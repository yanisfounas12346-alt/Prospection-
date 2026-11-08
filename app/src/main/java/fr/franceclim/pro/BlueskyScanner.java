package fr.franceclim.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Lecture de publications PUBLICQUES via l'API officielle Bluesky AppView.
 * Aucun login, aucun message, aucun accès à des comptes ou groupes privés.
 * La découverte est limitée à des mots clés liés au métier, puis vérifiée
 * par le classifieur local (besoin exprimé + commune des Bouches-du-Rhône).
 */
public final class BlueskyScanner {
  private BlueskyScanner() {}
  private static final String ENDPOINT =
      "https://api.bsky.app/xrpc/app.bsky.feed.searchPosts";
  private static final String[] QUERIES = {
    "climatisation", "chauffe-eau", "cumulus", "climatiseur",
    "clim Marseille", "chauffe eau Marseille", "clim Aix", "cumulus Aix",
    "clim Vitrolles", "chauffe eau Aubagne", "installateur clim 13",
    "plombier chauffe eau 13", "remplacer cumulus", "changer chauffe eau",
    "remplacer clim", "pose climatisation", "ballon eau chaude panne",
    "installation climatisation"
  };
  // Pages supplémentaires uniquement lorsque la page précédente est saturée.
  // Limite prudente pour éviter une rafale de requêtes sur l'API publique.
  private static final int PAGE_SIZE=100;
  private static final int MAX_PAGES=3;

  public static final class Report {
    public int searches, pages, posts, candidates, added, errors, truncated;
    public String failedQueries="";
    @Override public String toString(){
      return "Bluesky : "+searches+" recherches, "+pages+" pages, "+posts+
        " publications, "+candidates+" demandes potentielles dans le 13, "+
        added+" nouvelles, "+errors+" erreur(s), "+
        truncated+" recherche(s) possiblement tronquée(s)";
    }
  }

  public static Report scan(Context app){
    Report report=new Report();
    SharedPreferences prefs=app.getSharedPreferences("franceclim",Context.MODE_PRIVATE);
    if(!prefs.getBoolean("watch_bluesky",true))return report;
    boolean initialized=prefs.getBoolean("bluesky_initialized",false);
    // Déduplication par URL dans RadarStore; aucune information de compte n'est transférée.
    for(String query:QUERIES){
      report.searches++;
      try {
        Set<String> batch=new HashSet<>();
        Set<String> cursors=new HashSet<>();
        String cursor="";
        for(int page=0;page<MAX_PAGES;page++){
          String url=ENDPOINT+"?q="+Uri.encode(query)+
            "&sort=latest&lang=fr&limit="+PAGE_SIZE+
            (cursor.isEmpty()?"":"&cursor="+Uri.encode(cursor));
          JSONObject result=new JSONObject(readHttps(url));
          JSONArray posts=result.optJSONArray("posts");
          if(posts==null)throw new java.io.IOException("Réponse de recherche sans posts");
          report.pages++;
          for(int i=0;i<posts.length();i++){
          JSONObject post=posts.optJSONObject(i);
          if(post==null)continue;
          report.posts++;
          JSONObject record=post.optJSONObject("record");
          JSONObject author=post.optJSONObject("author");
          if(record==null||author==null)continue;
          String text=record.optString("text","").trim();
          if(text.length()<20||text.length()>1800)continue;
          LeadClassifier.Result qualified=LeadClassifier.classify(text,"");
          // Conserver aussi les demandes locales incertaines : un particulier
          // peut exprimer son besoin sans employer les formules habituelles.
          if(!qualified.eligible||!qualified.geoKnown)continue;
          report.candidates++;
          String handle=author.optString("handle","");
          String uri=post.optString("uri","");
          String rkey=uri.substring(uri.lastIndexOf('/')+1);
          if(!handle.matches("[A-Za-z0-9.-]{3,255}")||
             !rkey.matches("[A-Za-z0-9_-]{5,80}"))continue;
          String link="https://bsky.app/profile/"+handle+"/post/"+rkey;
          if(!batch.add(link))continue;
          RadarStore.AddResult added=RadarStore.add(app,text+" "+link,"","Bluesky (publication publique)");
          if(added.added){
            report.added++;
            if(initialized && added.classification!=null &&
               "PRIORITAIRE".equals(added.classification.status)){
              JSONObject alert=new JSONObject();
              alert.put("type",added.classification.type);
              alert.put("city",added.classification.city);
              alert.put("score",added.classification.score);
              RadarScanner.notifyLead(app,alert);
            }
          }else if(!"Déjà détectée".equals(added.reason)){
            // Storage errors must be visible; the next scan can retry the post.
            report.errors++;
          }
          }
          String next=result.optString("cursor","");
          if(posts.length()<PAGE_SIZE || next.isEmpty() || !cursors.add(next))break;
          if(page+1==MAX_PAGES){report.truncated++;break;}
          cursor=next;
        }
      }catch(Exception e){
        report.errors++;
        report.failedQueries+=query+" ("+e.getClass().getSimpleName()+") ; ";
      }
    }
    prefs.edit().putBoolean("bluesky_initialized",true)
      .putString("bluesky_scan_status",report.toString()+
      " • "+new java.util.Date().toString()).apply();
    return report;
  }

  private static String readHttps(String url) throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(11000);c.setReadTimeout(15000);
    c.setRequestProperty("User-Agent","FranceClimRadar13/2.2 (veille publique, pas de demarchage)");
    c.setRequestProperty("Accept","application/json");
    try{
      if(c.getResponseCode()!=200)throw new java.io.IOException("API publique indisponible");
      try(InputStream in=c.getInputStream()){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[4096];int n;
        while((n=in.read(buf))!=-1){
          if(out.size()+n>1200000)throw new java.io.IOException("Réponse trop volumineuse");
          out.write(buf,0,n);
        }
        return out.toString("UTF-8");
      }
    }finally{c.disconnect();}
  }
}
