package fr.franceclim.pro;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public final class RadarStore {
  private RadarStore(){}
  private static final Object LOCK=new Object();
  private static final String PREF="franceclim";
  private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
  public static JSONArray leads(Context c){
    synchronized(LOCK) {
      try{return new JSONArray(prefs(c).getString("leads","[]"));}catch(Exception e){return new JSONArray();}
    }
  }
  private static String hash(String input){
    try{
      byte[] digest=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format("%02x",b&0xff));
      return out.toString();
    }catch(Exception e){return Integer.toHexString(input.hashCode());}
  }
  public static String extractUrl(String raw){
    if(raw==null)return "";
    Matcher m=Pattern.compile("https?://[^\\s<>\\\"']+").matcher(raw);
    if(!m.find())return "";
    return m.group().replaceAll("[)\\],;.!]+$","");
  }
  public static final class AddResult {
    public final boolean added;
    public final String reason;
    public final LeadClassifier.Result classification;
    AddResult(boolean added,String reason,LeadClassifier.Result classification){
      this.added=added;this.reason=reason;this.classification=classification;
    }
  }
  public static AddResult add(Context ctx,String raw,String cityHint,String source) {
    if(raw==null||raw.trim().isEmpty())return new AddResult(false,"Contenu vide",null);
    String clipped=raw.length()>1800?raw.substring(0,1800):raw;
    LeadClassifier.Result analysis=LeadClassifier.classify(clipped,cityHint);
    if(!analysis.eligible)return new AddResult(false,analysis.reason,analysis);
    synchronized(LOCK){
      JSONArray old=leads(ctx);
      String link=extractUrl(clipped);
      // Use URL when present, otherwise stable normalized content. No person name needed.
      String signature=hash(link.isEmpty()?LeadClassifier.normalize(clipped):link.trim().toLowerCase());
      for(int k=0;k<old.length();k++){
        JSONObject item=old.optJSONObject(k);
        if(item==null)continue;
        if(signature.equals(item.optString("fingerprint")) ||
           clipped.equalsIgnoreCase(item.optString("detail")))
          return new AddResult(false,"Déjà détectée",analysis);
      }
      try {
        JSONObject item=new JSONObject();
        item.put("fingerprint",signature);
        item.put("city",analysis.city);
        item.put("detail",clipped);
        item.put("type",analysis.type);
        item.put("score",analysis.score);
        item.put("verdict",analysis.status);
        item.put("reason",analysis.reason);
        item.put("urgent",analysis.urgent);
        item.put("source",source==null?"Partage":source);
        item.put("link",link);
        item.put("contact","");
        item.put("status","À examiner");
        item.put("visited",false);
        item.put("demo",source!=null && source.startsWith("DÉMO"));
        item.put("appointment_at","");
        item.put("date",System.currentTimeMillis());
        JSONArray updated=new JSONArray();
        int start=Math.max(0,old.length()-399);
        for(int i=start;i<old.length();i++)updated.put(old.get(i));
        updated.put(item);
        prefs(ctx).edit().putString("leads",updated.toString())
          .putLong("last_match",System.currentTimeMillis()).apply();
        return new AddResult(true,"Ajoutée au radar",analysis);
      }catch(Exception e){return new AddResult(false,"Erreur de stockage",analysis);}
    }
  }
  /**
   * L'engagement commercial commence par une visite technique gratuite.
   * Un devis ne peut être émis que si la visite est effectivement terminée.
   * Retourne false si la transition est interdite ou le dossier n'existe pas.
   */
  public static boolean updateStage(Context c,int index,String stage){
    synchronized(LOCK){
      JSONArray a=leads(c);
      JSONObject item=a.optJSONObject(index);
      if(item==null)return false;
      boolean visited=item.optBoolean("visited",false) ||
        "Visite effectuée".equals(item.optString("status"));
      if(("Devis après visite".equals(stage)||"Chantier signé".equals(stage))&&!visited)
        return false;
      try {
        if("Visite effectuée".equals(stage))item.put("visited",true);
        item.put("status",stage);
        prefs(c).edit().putString("leads",a.toString()).apply();
        return true;
      }catch(Exception e){return false;}
    }
  }

  public static boolean setVisit(Context c,int index,String when){
    synchronized(LOCK){
      JSONArray a=leads(c);
      JSONObject item=a.optJSONObject(index);
      if(item==null||when==null||when.trim().isEmpty())return false;
      try{
        item.put("appointment_at",when.trim());
        item.put("status","RDV confirmé");
        prefs(c).edit().putString("leads",a.toString()).apply();
        return true;
      }catch(Exception e){return false;}
    }
  }

  public static boolean replaceFromBackup(Context c,JSONArray replacement){
    if(replacement==null || replacement.length()>500)return false;
    // Validation basique et limite du volume de données de clients importées.
    for(int i=0;i<replacement.length();i++){
      JSONObject lead=replacement.optJSONObject(i);
      if(lead==null || lead.optString("detail").length()>4000 ||
         lead.optString("city").length()>160 || lead.optString("contact").length()>250)
        return false;
    }
    synchronized(LOCK){
      prefs(c).edit().putString("leads",replacement.toString()).apply();
    }
    return true;
  }

  public static void clear(Context c){
    synchronized(LOCK){prefs(c).edit().remove("leads").remove("last_match").apply();}
  }
  public static boolean enabled(Context c,String pkg){
    if("fr.leboncoin".equals(pkg))return prefs(c).getBoolean("watch_lbc",true);
    if("com.facebook.katana".equals(pkg))return prefs(c).getBoolean("watch_fb",false);
    return false;
  }
  public static void setEnabled(Context c,String name,boolean value) {
    prefs(c).edit().putBoolean(name,value).apply();
  }
}
