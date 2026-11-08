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
    synchronized(LOCK){
      try{return new JSONArray(SecureLeadStore.read(c));}
      catch(org.json.JSONException e){throw new IllegalStateException("Dossiers clients invalides",e);}
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
    public final String fingerprint;
    AddResult(boolean added,String reason,LeadClassifier.Result classification){
      this(added,reason,classification,"");
    }
    AddResult(boolean added,String reason,LeadClassifier.Result classification,String fingerprint){
      this.added=added;this.reason=reason;this.classification=classification;
      this.fingerprint=fingerprint;
    }
  }
  public static AddResult add(Context ctx,String raw,String cityHint,String source) {
    return addWithIdentity(ctx,raw,cityHint,source,"");
  }
  /** The identity is hashed into the fingerprint, never added to the public map. */
  public static AddResult addWithIdentity(Context ctx,String raw,String cityHint,
    String source,String identity) {
    if(raw==null||raw.trim().isEmpty())return new AddResult(false,"Contenu vide",null);
    String clipped=raw.length()>1800?raw.substring(0,1800):raw;
    LeadClassifier.Result analysis=LeadClassifier.classify(clipped,cityHint);
    if(!analysis.eligible)return new AddResult(false,analysis.reason,analysis);
    synchronized(LOCK){
      JSONArray old=leads(ctx);
      String link=extractUrl(clipped);
      // Use URL when present, otherwise stable normalized content. No person name needed.
      String canonical=link.isEmpty()
        ?LeadClassifier.normalize(clipped)+"|"+LeadClassifier.normalize(cityHint)
        :link.trim().toLowerCase(java.util.Locale.ROOT);
      // Separate legitimate form submissions with identical descriptions.
      if(identity!=null&&!identity.trim().isEmpty())
        canonical+="|"+LeadClassifier.normalize(identity);
      String signature=hash(canonical);
      for(int k=0;k<old.length();k++){
        JSONObject item=old.optJSONObject(k);
        if(item==null)continue;
        if(signature.equals(item.optString("fingerprint")))
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
        SecureLeadStore.save(ctx,updated.toString());
        prefs(ctx).edit().putLong("last_match",System.currentTimeMillis()).apply();
        return new AddResult(true,"Ajoutée au radar",analysis,signature);
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
        SecureLeadStore.save(c,a.toString());
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
        SecureLeadStore.save(c,a.toString());
        return true;
      }catch(Exception e){return false;}
    }
  }

  /**
   * Les coordonnées sont enregistrées uniquement quand elles sont communiquées
   * à l'artisan. La demande de rappel et sa preuve sont distinctes du numéro.
   */
  public static boolean setContact(Context c,int index,String name,String phone,
    boolean requestedCallback,String evidence){
    String safeName=name==null?"":name.trim();
    String safePhone=phone==null?"":phone.trim();
    String safeEvidence=evidence==null?"":evidence.trim();
    if(safeName.length()>100||safePhone.length()>35||safeEvidence.length()>600)return false;
    if(!safePhone.isEmpty()){
      String digits=safePhone.replaceAll("[^0-9]","");
      if(digits.length()<9||digits.length()>15||
          !safePhone.matches("[+0-9 .()/-]+"))return false;
    }
    // Une publication publique ne constitue PAS une demande à être rappelé.
    if(requestedCallback && (safePhone.isEmpty()||safeEvidence.length()<8))return false;
    synchronized(LOCK){
      JSONArray leads=leads(c);
      JSONObject item=leads.optJSONObject(index);
      if(item==null)return false;
      try {
        item.put("personName",safeName);
        item.put("phone",safePhone);
        item.put("callbackRequested",requestedCallback);
        item.put("callbackEvidence",requestedCallback?safeEvidence:"");
        item.put("callbackAt",requestedCallback?System.currentTimeMillis():0);
        SecureLeadStore.save(c,leads.toString());
        return true;
      }catch(Exception ex){return false;}
    }
  }

  /** Attach a form contact to its exact record, even if another source adds a lead. */
  public static boolean setContactByFingerprint(Context c,String fingerprint,String name,
    String phone,boolean requestedCallback,String evidence){
    if(fingerprint==null||fingerprint.isEmpty())return false;
    synchronized(LOCK){
      JSONArray records=leads(c);
      for(int i=0;i<records.length();i++){
        JSONObject lead=records.optJSONObject(i);
        if(lead!=null&&fingerprint.equals(lead.optString("fingerprint")))
          return setContact(c,i,name,phone,requestedCallback,evidence);
      }
      return false;
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
      SecureLeadStore.save(c,replacement.toString());
    }
    return true;
  }

  /** Suppression locale d'un dossier et des coordonnées associées. */
  public static boolean delete(Context c,int index){
    synchronized(LOCK){
      JSONArray before=leads(c);
      if(index<0||index>=before.length())return false;
      JSONArray after=new JSONArray();
      for(int i=0;i<before.length();i++){
        if(i==index)continue;
        JSONObject item=before.optJSONObject(i);
        if(item!=null)after.put(item);
      }
      SecureLeadStore.save(c,after.toString());
      return true;
    }
  }

  public static void clear(Context c){
    synchronized(LOCK){SecureLeadStore.clear(c);prefs(c).edit().remove("last_match").apply();}
  }
  /** Notifications d'applications de demandes de travaux choisies explicitement. */
  public static boolean enabled(Context c,String pkg){
    if("fr.leboncoin".equals(pkg))return prefs(c).getBoolean("watch_lbc",true);
    if("com.facebook.katana".equals(pkg))return prefs(c).getBoolean("watch_fb",false);
    if(pkg==null || pkg.isEmpty())return false;
    // Les messageries personnelles, e-mails et applications système sont exclues.
    if(pkg.equals("com.whatsapp")||pkg.equals("com.whatsapp.w4b")||
       pkg.startsWith("com.google.android.gm")||pkg.contains("messaging")||
       pkg.contains("telegram")||pkg.contains("signal"))return false;
    return prefs(c).getStringSet("watched_marketplace_packages",
      java.util.Collections.<String>emptySet()).contains(pkg);
  }
  public static void setEnabled(Context c,String name,boolean value) {
    prefs(c).edit().putBoolean(name,value).apply();
  }
}
