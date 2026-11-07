package fr.franceclim.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Carte principale. Toutes les fiches contacts restent locales dans RadarStore.
 * Les centres de communes proviennent de l'API Découpage administratif.
 * Aucune latitude de domicile, numéro ou nom de client n'est transmis au HTML.
 */
public class MapActivity extends Activity {
  private static final String PREF="franceclim";
  private static final String GEO_JSON="geo_13_centres_v1";
  private static final String GEO_WHEN="geo_13_centres_updated";
  private WebView web;
  private boolean pageReady=false;
  private JSONObject pending;
  private final AtomicBoolean scanning=new AtomicBoolean(false);
  private final AtomicBoolean generating=new AtomicBoolean(false);
  private SharedPreferences prefs;

  private static final class City {
    final String name;
    final double lat,lon;
    City(String name,double lat,double lon){this.name=name;this.lat=lat;this.lon=lon;}
  }
  private static final class Group {
    final City city;
    final JSONArray entries=new JSONArray();
    int count;
    Group(City city){this.city=city;}
  }

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    prefs=getSharedPreferences(PREF,Context.MODE_PRIVATE);
    getWindow().setStatusBarColor(Color.rgb(11,40,59));
    getWindow().setNavigationBarColor(Color.rgb(11,40,59));
    RadarJobService.schedule(this);
    web=new WebView(this);
    WebSettings settings=web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(false);
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(false);
    settings.setAllowFileAccessFromFileURLs(false);
    settings.setAllowUniversalAccessFromFileURLs(false);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    if(android.os.Build.VERSION.SDK_INT>=26)settings.setSafeBrowsingEnabled(true);
    web.addJavascriptInterface(new MapBridge(),"RadarNative");
    web.setWebViewClient(new WebViewClient(){
      @Override public void onPageFinished(WebView v,String url){
        if(!url.startsWith("file:///android_asset/radar-map.html"))return;
        pageReady=true;
        sendToPage();
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
        if(!req.isForMainFrame())return false;
        Uri url=req.getUrl();
        if(url.toString().startsWith("file:///android_asset/radar-map.html"))return false;
        if("https".equalsIgnoreCase(url.getScheme())&&url.getHost()!=null){
          openExternal(url);
        }
        return true;
      }
    });
    setContentView(web);
    web.loadUrl("file:///android_asset/radar-map.html");
    receiveShare(getIntent());
    recalculate();
    long now=System.currentTimeMillis();
    long last=prefs.getLong("map_last_scan_at",0L);
    // Premier démarrage : recherche automatique, sans forcer l'utilisateur à tout configurer.
    if(last<=0||now-last>30L*60L*1000L)startScan();
  }

  @Override protected void onNewIntent(Intent intent){
    super.onNewIntent(intent);
    setIntent(intent);
    receiveShare(intent);
    recalculate();
  }
  private void receiveShare(Intent intent){
    if(intent==null||!Intent.ACTION_SEND.equals(intent.getAction()))return;
    if(!"text/plain".equals(intent.getType()))return;
    String raw=intent.getStringExtra(Intent.EXTRA_TEXT);
    if(raw!=null&&!raw.trim().isEmpty())
      RadarStore.add(this,raw,"","Partage volontaire");
  }
  @Override protected void onResume(){
    super.onResume();
    if(prefs!=null&&pageReady)recalculate();
  }

  private void eval(String method,String args){
    if(web!=null&&pageReady)
      web.evaluateJavascript("window."+method+"("+args+");",null);
  }
  private void sendToPage(){
    if(pending!=null)eval("renderRadar",pending.toString());
  }

  private void openExternal(Uri uri){
    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}
    catch(Exception e){Toast.makeText(this,"Lien non disponible.",Toast.LENGTH_SHORT).show();}
  }

  private final class MapBridge {
    @JavascriptInterface public void refresh(){runOnUiThread(()->startScan());}
    @JavascriptInterface public void openLead(int index){runOnUiThread(()->showLead(index));}
  }

  /** Fiche native privée : les coordonnées ne quittent jamais le stockage local.
   * Le composeur n'est proposé que pour répondre à une demande explicite documentée.
   */
  private void showLead(int index){
    JSONObject lead=RadarStore.leads(this).optJSONObject(index);
    if(lead==null){
      Toast.makeText(this,"Cette opportunité n'est plus disponible.",Toast.LENGTH_SHORT).show();
      recalculate();return;
    }
    String city=lead.optString("city","Ville à confirmer");
    String type=lead.optString("type","Demande");
    String name=lead.optString("personName","");
    String phone=lead.optString("phone","");
    String link=lead.optString("link","");
    StringBuilder body=new StringBuilder();
    body.append("Commune : ").append(city)
      .append("\nPriorité : ").append(lead.optInt("score",0)).append("/100")
      .append("\nSource : ").append(lead.optString("source","Non précisée"))
      .append("\nStatut : ").append(lead.optString("status","À examiner"))
      .append("\nNom : ").append(name.isEmpty()?"non communiqué":name)
      .append("\nTéléphone : ").append(phone.isEmpty()?"non communiqué":phone)
      .append("\n\n").append(lead.optString("detail","Demande à vérifier"));
    AlertDialog.Builder dlg=new AlertDialog.Builder(this)
      .setTitle(type+" — "+city).setMessage(body.toString())
      .setNegativeButton("Fermer",null);
    if(link.startsWith("https://")){
      dlg.setPositiveButton("Publication d'origine",
        (d,which)->openExternal(Uri.parse(link)));
    }
    if(!phone.isEmpty()&&lead.optBoolean("callbackRequested",false)&&
       lead.optString("callbackEvidence","").trim().length()>=8){
      dlg.setNeutralButton("Composer le numéro",(d,which)->{
        new AlertDialog.Builder(this).setTitle("Rappel demandé")
          .setMessage("Vérifier que le particulier a explicitement demandé ce rappel, son objet et le délai légal avant d'appeler.")
          .setPositiveButton("Ouvrir le téléphone",(confirm,w)->
            startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone,null))))
          .setNegativeButton("Annuler",null).show();
      });
    }
    dlg.show();
  }

  private void startScan(){
    if(!scanning.compareAndSet(false,true))return;
    prefs.edit().putLong("map_last_scan_at",System.currentTimeMillis()).apply();
    eval("setRadarBusy","true");
    new Thread(()->{
      String status;
      try {
        RadarScanner.Report rss=RadarScanner.scan(getApplicationContext());
        BlueskyScanner.Report sky=BlueskyScanner.scan(getApplicationContext());
        status="Analyse terminée • "+rss.added+" nouvelle(s) piste(s) RSS, "+
          sky.added+" Bluesky. Touchez une épingle pour les détails.";
      }catch(Exception e){status="Certaines sources sont indisponibles. Les dossiers déjà enregistrés restent visibles.";}
      final String note=status;
      scanning.set(false);
      runOnUiThread(()->{
        eval("setRadarStatus",JSONObject.quote(note));
        recalculate();
      });
    },"radar-map-scan").start();
  }

  private void recalculate(){
    if(!generating.compareAndSet(false,true))return;
    new Thread(()->{
      try {
        Map<String,City> centers=loadCenters();
        JSONArray leads=RadarStore.leads(getApplicationContext());
        LinkedHashMap<String,Group> groups=new LinkedHashMap<>();
        JSONArray unlocated=new JSONArray();
        for(int i=0;i<leads.length();i++){
          JSONObject lead=leads.optJSONObject(i);
          if(lead==null||lead.optBoolean("demo",false)||
             "Classé".equals(lead.optString("status")))continue;
          String rawCity=lead.optString("city","").trim();
          String key=LeadClassifier.normalize(rawCity);
          if(rawCity.matches("130(0[1-9]|1[0-6])"))key="marseille";
          JSONObject preview=new JSONObject();
          preview.put("idx",i);
          preview.put("type",lead.optString("type","Demande"));
          preview.put("score",Math.max(0,Math.min(100,lead.optInt("score",0))));
          preview.put("source",lead.optString("source",""));
          preview.put("city",rawCity);
          String original=lead.optString("detail","");
          String source=lead.optString("source","").toLowerCase(java.util.Locale.ROOT);
          // Les messages de formulaires directs peuvent contenir des données privées.
          String snippet=(source.contains("formulaire")||source.contains("csv")||
              source.contains("partage")||source.contains("saisie"))
              ?"Demande enregistrée. Ouvrez la fiche pour consulter le message complet."
              :sanitizePreview(original);
          preview.put("summary",snippet);
          String link=lead.optString("link","");
          if(link.startsWith("https://"))preview.put("link",link);
          City city=centers.get(key);
          if(city==null){
            unlocated.put(preview);
            continue;
          }
          Group group=groups.get(key);
          if(group==null){group=new Group(city);groups.put(key,group);}
          group.entries.put(preview);
          group.count++;
        }
        JSONArray pins=new JSONArray();
        for(Group g:groups.values()){
          JSONObject pin=new JSONObject();
          pin.put("city",g.city.name);
          pin.put("lat",g.city.lat);
          pin.put("lon",g.city.lon);
          pin.put("count",g.count);
          pin.put("opportunities",g.entries);
          pins.put(pin);
        }
        JSONObject payload=new JSONObject();
        payload.put("pins",pins);
        payload.put("unlocated",unlocated);
        runOnUiThread(()->{
          pending=payload;
          sendToPage();
        });
      }catch(Exception e){
        runOnUiThread(()->eval("renderRadarError",JSONObject.quote(
          "Impossible de mettre à jour la carte. Réessayez avec une connexion Internet.")));
      }finally{generating.set(false);}
    },"radar-map-pins").start();
  }

  private static String sanitizePreview(String raw){
    String result=raw.replaceAll("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b","[contact masqué]")
      .replaceAll("(?<!\\d)(?:\\+33|0)[ .-]?(?:\\d[ .-]?){8,10}(?!\\d)","[numéro masqué]")
      .replaceAll("\\s+"," ").trim();
    return result.length()>240?result.substring(0,240)+"…":result;
  }

  private Map<String,City> loadCenters(){
    Map<String,City> result=fallbackCenters();
    String saved=prefs.getString(GEO_JSON,"");
    if(!saved.isEmpty())mergeCenters(result,saved);
    long age=System.currentTimeMillis()-prefs.getLong(GEO_WHEN,0L);
    if(saved.isEmpty()||age>30L*24*60*60*1000L){
      try{
        String fresh=fetchOfficialCenters();
        mergeCenters(result,fresh);
        if(result.size()>=110){
          prefs.edit().putString(GEO_JSON,fresh)
            .putLong(GEO_WHEN,System.currentTimeMillis()).apply();
        }
      }catch(Exception ignored){
        // La carte reste utilisable depuis le cache ou les grandes communes.
      }
    }
    return result;
  }
  private static void mergeCenters(Map<String,City> map,String json){
    try {
      JSONArray communes=new JSONArray(json);
      for(int i=0;i<communes.length();i++){
        JSONObject c=communes.optJSONObject(i);if(c==null)continue;
        JSONObject centre=c.optJSONObject("centre");
        if(centre==null)continue;
        JSONArray xy=centre.optJSONArray("coordinates");
        if(xy==null||xy.length()<2)continue;
        double lon=xy.optDouble(0,0),lat=xy.optDouble(1,0);
        if(lat<42.9||lat>44.05||lon<4.15||lon>6.05)continue;
        String name=c.optString("nom","").trim();
        if(!name.isEmpty())map.put(LeadClassifier.normalize(name),new City(name,lat,lon));
      }
    }catch(Exception ignored){}
  }
  private static Map<String,City> fallbackCenters(){
    Map<String,City> result=new HashMap<>();
    // Centres approximatifs de grandes communes, utilisés uniquement hors réseau
    // en attendant le répertoire officiel (les plus petites sont non localisées).
    put(result,"Marseille",43.2965,5.3698);
    put(result,"Aix-en-Provence",43.5297,5.4474);
    put(result,"Arles",43.6766,4.6278);
    put(result,"Aubagne",43.2909,5.5707);
    put(result,"Martigues",43.4060,5.0470);
    put(result,"Salon-de-Provence",43.6400,5.0970);
    put(result,"Istres",43.5136,4.9880);
    put(result,"Vitrolles",43.4600,5.2500);
    put(result,"Marignane",43.4164,5.2140);
    put(result,"La Ciotat",43.1745,5.6040);
    put(result,"Tarascon",43.8050,4.6600);
    put(result,"Miramas",43.5850,5.0010);
    put(result,"Gardanne",43.4540,5.4690);
    put(result,"Fos-sur-Mer",43.4380,4.9470);
    put(result,"Cassis",43.2130,5.5380);
    put(result,"Châteaurenard",43.8820,4.8540);
    put(result,"Les Pennes-Mirabeau",43.4100,5.3100);
    put(result,"Berre-l'Étang",43.4760,5.1710);
    put(result,"Saint-Rémy-de-Provence",43.7890,4.8320);
    put(result,"Saint-Martin-de-Crau",43.6390,4.8150);
    put(result,"Auriol",43.3710,5.6360);
    put(result,"Trets",43.4480,5.6840);
    put(result,"Châteauneuf-les-Martigues",43.3830,5.1650);
    put(result,"Allauch",43.3350,5.4810);
    put(result,"Plan-de-Cuques",43.3460,5.4650);
    put(result,"La Penne-sur-Huveaune",43.2850,5.5170);
    put(result,"Port-de-Bouc",43.4050,4.9800);
    return result;
  }
  private static void put(Map<String,City> map,String name,double lat,double lon){
    map.put(LeadClassifier.normalize(name),new City(name,lat,lon));
  }

  private static String fetchOfficialCenters() throws Exception{
    HttpURLConnection connection=(HttpURLConnection)new URL(
      "https://geo.api.gouv.fr/departements/13/communes?fields=nom,centre&format=json").openConnection();
    connection.setConnectTimeout(9000);connection.setReadTimeout(12000);
    connection.setRequestProperty("Accept","application/json");
    connection.setRequestProperty("User-Agent","FranceClimRadar13/2.4 (carte communes publiques)");
    try{
      if(connection.getResponseCode()!=200)
        throw new java.io.IOException("Référentiel géographique indisponible");
      try(InputStream in=connection.getInputStream()){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){
          if(out.size()+n>1800000)throw new java.io.IOException("Réponse volumineuse");
          out.write(buf,0,n);
        }
        return out.toString("UTF-8");
      }
    }finally{connection.disconnect();}
  }

  @Override protected void onDestroy(){
    if(web!=null){
      web.removeJavascriptInterface("RadarNative");
      web.destroy();
      web=null;
    }
    super.onDestroy();
  }
}
