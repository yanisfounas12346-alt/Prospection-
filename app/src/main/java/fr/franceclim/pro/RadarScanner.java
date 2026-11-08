package fr.franceclim.pro;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Scanner autonome de flux RSS/Atom dont la consultation est autorisée. */
public final class RadarScanner {
  private RadarScanner(){}
  public static final class Report {
    public int feeds, checked, added, errors;
    public String failedSources="";
    @Override public String toString() {return feeds+" source(s), "+checked+" publication(s), "+added+
      " opportunité(s), "+errors+" erreur(s)";}
  }
  private static final class Entry {
    String title="", description="", link="", id="";
  }

  public static synchronized Report scan(Context ctx) {
    Report report=new Report();
    SharedPreferences prefs=ctx.getSharedPreferences("franceclim",Context.MODE_PRIVATE);
    // Flux RSS PUBLICS fournis nativement par Mastodon pour ses hashtags.
    // Ils ne donnent accès qu'aux publications publiques du réseau indiqué.
    StringBuilder all=new StringBuilder();
    if(prefs.getBoolean("watch_mastodon",true)){
      all.append("https://mastodon.social/tags/climatisation.rss\n");
      all.append("https://mastodon.social/tags/chauffeeau.rss\n");
    }
    all.append(prefs.getString("feeds","").trim());
    String configured=all.toString().trim();
    if(configured.isEmpty()){
      prefs.edit().putString("scan_status","Aucun flux RSS actif").apply();
      return report;
    }
    Set<String> seen=new LinkedHashSet<>(prefs.getStringSet("seen",Collections.<String>emptySet()));
    boolean first=prefs.getBoolean("radar_initialized",false);
    int attempted=0;
    for(String raw:configured.split("\\r?\\n")){
      String feed=raw.trim();
      if(feed.isEmpty()||feed.startsWith("#"))continue;
      if(++attempted>20)break;
      report.feeds++;
      if(!feed.startsWith("https://")){report.errors++;report.failedSources+="Adresse RSS invalide ; ";continue;}
      try {
        String xml=download(feed);
        XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();
        p.setInput(new StringReader(xml));
        Entry entry=null;
        int count=0;
        for(int event=p.getEventType();event!=XmlPullParser.END_DOCUMENT;event=p.next()){
          String tag=p.getName();
          if(event==XmlPullParser.START_TAG && ("item".equalsIgnoreCase(tag)||"entry".equalsIgnoreCase(tag))){
            entry=new Entry(); continue;
          }
          if(entry==null)continue;
          if(event==XmlPullParser.START_TAG){
            if("title".equalsIgnoreCase(tag))entry.title=p.nextText();
            else if("description".equalsIgnoreCase(tag)||"summary".equalsIgnoreCase(tag))entry.description=p.nextText();
            else if("link".equalsIgnoreCase(tag)){
              String href=p.getAttributeValue(null,"href");
              if(href!=null)entry.link=href;
              else entry.link=p.nextText();
            } else if("guid".equalsIgnoreCase(tag)||"id".equalsIgnoreCase(tag))entry.id=p.nextText();
          } else if(event==XmlPullParser.END_TAG && ("item".equalsIgnoreCase(tag)||"entry".equalsIgnoreCase(tag))){
            report.checked++;
            if(count++<100) handle(ctx,seen,entry,first,report);
            entry=null;
          }
        }
      } catch(Exception e){
        report.errors++;
        // Ne jamais inclure de jetons, chemins ou paramètres de flux privés.
        String host="Source RSS";
        try{String parsed=new URL(feed).getHost();if(parsed!=null&&!parsed.isEmpty())host=parsed;}
        catch(Exception ignored){}
        report.failedSources+=host+" ("+e.getClass().getSimpleName()+") ; ";
      }
    }
    // Pas de données personnelles publiées sur GitHub ; stockage uniquement sur ce téléphone.
    if(seen.size()>3000){
      Set<String> reduced=new LinkedHashSet<>();
      int skip=seen.size()-2500;
      for(String h:seen)if(skip-->0){}else reduced.add(h);
      seen=reduced;
    }
    prefs.edit().putStringSet("seen",new LinkedHashSet<>(seen))
      .putBoolean("radar_initialized",true)
      .putString("scan_status",report.toString()+" • "+new java.util.Date().toString()).apply();
    return report;
  }

  private static void handle(Context ctx,Set<String> seen,Entry entry,
    boolean initialized,Report report) {
    String content=clean(entry.title+" "+entry.description);
    RadarClassifier.Result result=RadarClassifier.analyse(content);
    if(result==null)return;
    String key=sha((entry.link.isEmpty()?entry.id:entry.link).trim());
    if(key.equals(sha("")))key=sha(content);
    if(seen.contains(key))return;
    // Mark an item seen ONLY after storage succeeds or the CRM confirms a duplicate.
    // A full or unavailable store must not silently lose a customer request.
    String body=content+(entry.link.isEmpty()?"":" "+entry.link);
    RadarStore.AddResult added=RadarStore.add(ctx,body,"","Flux RSS/Atom");
    if(!added.added){
      if("Déjà détectée".equals(added.reason))seen.add(key);
      else report.errors++;
      return;
    }
    seen.add(key);
    report.added++;
    if(initialized && added.classification!=null &&
      "PRIORITAIRE".equals(added.classification.status)) {
      try {
        JSONObject lead=new JSONObject();
        lead.put("type",added.classification.type);
        lead.put("city",added.classification.city);
        lead.put("score",added.classification.score);
        notifyLead(ctx,lead);
      }catch(Exception ignored){}
    }
  }

  private static String clean(String text){
    return text.replaceAll("<[^>]*>"," ").replace("&nbsp;"," ").replace("&amp;","&")
      .replace("&quot;","\\\"").replaceAll("\\s+"," ").trim();
  }
  private static String sha(String s){
    try {
      byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder b=new StringBuilder();
      for(int i=0;i<12;i++)b.append(String.format(java.util.Locale.ROOT,"%02x",bytes[i]&255));
      return b.toString();
    }catch(Exception e){return Integer.toHexString(s.hashCode());}
  }
  private static String download(String url) throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(12000);c.setReadTimeout(12000);
    c.setRequestProperty("User-Agent","FranceClimPro-Radar/2.0 (RSS/Atom)");
    try {
      if(c.getResponseCode()!=200)throw new java.io.IOException("HTTP "+c.getResponseCode());
      ByteArrayOutputStream out=new ByteArrayOutputStream();
      try(InputStream input=c.getInputStream()){
        byte[] buffer=new byte[4096];int n;
        while((n=input.read(buffer))!=-1){
          if(out.size()+n>1024*1024)throw new java.io.IOException("Flux trop volumineux");
          out.write(buffer,0,n);
        }
      }
      return out.toString("UTF-8");
    }finally{c.disconnect();}
  }
  static void notifyLead(Context ctx,JSONObject lead){
    if(Build.VERSION.SDK_INT>=33 &&
       ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
    NotificationManager manager=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if(manager==null)return;
    String channel="radar13";
    if(Build.VERSION.SDK_INT>=26){
      NotificationChannel nc=new NotificationChannel(channel,"Nouvelles demandes particuliers",NotificationManager.IMPORTANCE_HIGH);
      nc.setDescription("Demandes potentielles à vérifier dans les Bouches-du-Rhône");
      manager.createNotificationChannel(nc);
    }
    Intent open=new Intent(ctx,MainActivity.class);
    PendingIntent pi=PendingIntent.getActivity(ctx,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    String title=lead.optString("type")+" - "+lead.optString("city");
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(ctx,channel):new Notification.Builder(ctx);
    b.setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("France Clim : nouvelle demande à vérifier")
      .setContentText(title+" • score "+lead.optInt("score"))
      .setContentIntent(pi).setAutoCancel(true);
    manager.notify((int)(System.currentTimeMillis()%Integer.MAX_VALUE),b.build());
  }
}
