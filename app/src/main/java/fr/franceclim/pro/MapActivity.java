package fr.franceclim.pro;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.CheckBox;
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
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
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
  private final AtomicBoolean geoFetching=new AtomicBoolean(false);
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
    if(android.os.Build.VERSION.SDK_INT>=33 &&
       checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
         !=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3301);
    }
    PlatformSources.activateInstalled(this);
    RadarJobService.schedule(this);
    web=new WebView(this);
    WebSettings settings=web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setUserAgentString(settings.getUserAgentString()+
      " FranceClimRadar13/2.4 (+https://github.com/yanisfounas12346-alt/Prospection-)");
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
        if(scanning.get())eval("setRadarBusy","true");
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
    refreshGeographyIfNecessary();
    // Chaque ouverture à froid lance la veille des sources activées, sans action manuelle.
    // Les notifications des plateformes nécessitent toujours leur autorisation Android.
    startScan();
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
    if(raw==null||raw.trim().isEmpty())return;
    FormEmailIntake.Request request=FormEmailIntake.parse(raw);
    if(request!=null){
      // Courriel explicitement partagé : pas d'accès automatique aux e-mails.
      String description=request.detail+" "+request.service;
      RadarStore.AddResult added=RadarStore.addWithIdentity(this,description,
        request.city,"Formulaire direct / courriel partagé",request.id);
      if(added.added){
        boolean saved=RadarStore.setContactByFingerprint(this,added.fingerprint,
          request.name,request.phone,true,request.evidence);
        Toast.makeText(this,saved
          ?"Demande importée avec téléphone. Vérifie la provenance de l'e-mail."
          :"Demande importée, coordonnées à vérifier.",Toast.LENGTH_LONG).show();
      }else{
        Toast.makeText(this,"Demande déjà présente ou non éligible : "+added.reason,
          Toast.LENGTH_LONG).show();
      }
      return;
    }
    RadarStore.add(this,raw,"","Partage volontaire");
  }
  @Override protected void onResume(){
    super.onResume();
    PlatformSources.activateInstalled(this);
    // Android exige une permission spéciale : une seule invitation explicative.
    if(prefs!=null&&!notificationAccess()&&
       !prefs.getBoolean("source_listener_permission_prompted",false)){
      prefs.edit().putBoolean("source_listener_permission_prompted",true).apply();
      requestNotificationAccess();
    }
    if(prefs!=null&&pageReady)recalculate();
    // Au retour d'une autre application, actualiser si la dernière tentative date
    // de plus de cinq minutes. Le verrou scanning évite les scans concurrents.
    if(prefs!=null&&System.currentTimeMillis()-prefs.getLong("map_last_scan_at",0L)
        >5L*60L*1000L)startScan();
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
    @JavascriptInterface public void sources(){runOnUiThread(()->showSources());}
  }

  /** Les réglages sont secondaires : la carte demeure l'écran principal. */
  private void showSources(){
    String[] choices={
      "Activer l'ingestion automatique des alertes",
      "Choisir les plateformes à surveiller automatiquement",
      "Importer les demandes reçues (CSV avec contacts)",
      "Ajouter des flux RSS/Atom autorisés",
      "Diagnostic des sources actives",
      "AlloVoisins — vraies demandes chauffage / climatisation (Marseille)",
      "AlloVoisins — activer les alertes professionnelles",
      "Leboncoin — créer les recherches sauvegardées",
      "Travaux.com — appels d'offres locaux",
      "NeedHelp — missions et coordonnées autorisées",
      "StarOfService — demandes de devis clients"
    };
    new AlertDialog.Builder(this).setTitle("Sources des opportunités")
      .setMessage("Après activation, les nouvelles alertes des plateformes choisies entrent automatiquement dans tes dossiers. La collecte dépend des notifications réellement envoyées par chaque plateforme et ne lit pas les messages privés.")
      .setItems(choices,(dlg,selection)->{
        switch(selection){
          case 0:requestNotificationAccess();break;
          case 1:chooseMarketplaces();break;
          case 2:chooseCsv();break;
          case 3:editFeeds();break;
          case 4:showSourcesStatus();break;
          case 5:openExternal(Uri.parse("https://www.allovoisins.com/r/0/130/82171/0/Marseille/service/Chauffage-climatisation"));break;
          case 6:openExternal(Uri.parse("https://support-pro.allovoisins.com/hc/fr/articles/16199176771730--Comment-proposer-mes-services"));break;
          case 7:openExternal(Uri.parse("https://www.leboncoin.fr/"));break;
          case 8:openExternal(Uri.parse("https://www.travaux.com/professionnel/inscription/appel-d-offres"));break;
          case 9:openExternal(Uri.parse("https://www.needhelp.com/content/subscription"));break;
          case 10:openExternal(Uri.parse("https://www.starofservice.com/pro/how-it-works"));break;
        }
      }).setNegativeButton("Fermer",null).show();
  }

  private boolean notificationAccess(){
    String value=Settings.Secure.getString(getContentResolver(),
      "enabled_notification_listeners");
    return value!=null && value.contains(getPackageName()+"/");
  }
  private void requestNotificationAccess(){
    new AlertDialog.Builder(this).setTitle("Permission Android")
      .setMessage("Pour recevoir automatiquement les annonces des plateformes de travaux détectées, Android demande ton autorisation une fois. Le radar filtre les applications reconnues ; il exclut les conversations WhatsApp, e-mails et messages privés.")
      .setPositiveButton("Ouvrir les paramètres",(dlg,w)->{
        try{startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}
        catch(Exception ex){Toast.makeText(this,"Paramètres indisponibles",Toast.LENGTH_LONG).show();}
      }).setNegativeButton("Annuler",null).show();
  }

  /** Liste issue des applications effectivement installées, pas d'identifiants inventés. */
  private void chooseMarketplaces(){
    LinkedHashMap<String,String> apps=new LinkedHashMap<>();
    PackageManager pm=getPackageManager();
    Intent launcher=new Intent(Intent.ACTION_MAIN);
    launcher.addCategory(Intent.CATEGORY_LAUNCHER);
    for(ResolveInfo info:pm.queryIntentActivities(launcher,0)){
      if(info.activityInfo==null)continue;
      String pkg=info.activityInfo.packageName;
      CharSequence title=info.loadLabel(pm);
      if(title==null)continue;
      String label=title.toString();
      String n=LeadClassifier.normalize(label);
      if(n.contains("allovoisins")||n.contains("allo voisins")||
         n.contains("travaux com")||n.contains("needhelp")||
         n.contains("frizbiz")||n.contains("mesdepanneurs")||
         n.contains("need help")||n.contains("bricoleur")||
         n.contains("starofservice"))apps.put(pkg,label);
    }
    ArrayList<String> ids=new ArrayList<>();ArrayList<String> names=new ArrayList<>();
    ids.add("fr.leboncoin");names.add("Leboncoin — alertes officielles");
    ids.add("com.facebook.katana");names.add("Facebook — notifications de groupes");
    for(Map.Entry<String,String> app:apps.entrySet()){
      if(ids.contains(app.getKey()))continue;
      ids.add(app.getKey());names.add(app.getValue()+" — demandes de travaux");
    }
    boolean[] checked=new boolean[ids.size()];
    Set<String> selected=prefs.getStringSet("watched_marketplace_packages",
      Collections.<String>emptySet());
    for(int i=0;i<ids.size();i++){
      String id=ids.get(i);
      if("fr.leboncoin".equals(id))checked[i]=prefs.getBoolean("watch_lbc",false);
      else if("com.facebook.katana".equals(id))checked[i]=prefs.getBoolean("watch_fb",false);
      else checked[i]=selected.contains(id);
    }
    new AlertDialog.Builder(this).setTitle("Applications de demandes")
      .setMessage("Sélectionne les applications de travaux installées puis active leurs alertes officielles par métier et par commune. Le radar enregistre automatiquement leurs nouvelles demandes lorsque les notifications arrivent. Aucune messagerie privée n'est lue.")
      .setMultiChoiceItems(names.toArray(new String[0]),checked,(d,index,on)->checked[index]=on)
      .setPositiveButton("Enregistrer",(d,w)->{
        Set<String> picked=new LinkedHashSet<>();
        for(int i=0;i<ids.size();i++){
          if("fr.leboncoin".equals(ids.get(i)))prefs.edit().putBoolean("watch_lbc",checked[i]).apply();
          else if("com.facebook.katana".equals(ids.get(i)))prefs.edit().putBoolean("watch_fb",checked[i]).apply();
          else if(checked[i])picked.add(ids.get(i));
        }
        prefs.edit().putStringSet("watched_marketplace_packages",picked).apply();
        if(!notificationAccess())requestNotificationAccess();
        else Toast.makeText(this,"Sources enregistrées : veille des nouvelles alertes active.",
          Toast.LENGTH_LONG).show();
      }).setNegativeButton("Annuler",null).show();
  }

  private void editFeeds(){
    EditText input=new EditText(this);input.setMinLines(3);
    input.setHint("https://site-autorisant-les-flux.example/feed.rss");
    input.setText(prefs.getString("feeds",""));
    new AlertDialog.Builder(this).setTitle("Flux RSS ou Atom publics")
      .setMessage("Une URL HTTPS par ligne. Utiliser uniquement des flux officiels ou autorisés. Les demandes des plateformes sont suivies via leurs alertes professionnelles.")
      .setView(input).setPositiveButton("Enregistrer",(d,w)->{
        String value=input.getText().toString().trim();
        for(String line:value.split("\\r?\\n")){
          if(!line.trim().isEmpty()&&!line.trim().startsWith("https://")){
            Toast.makeText(this,"Chaque flux doit commencer par https://",Toast.LENGTH_LONG).show();
            return;
          }
        }
        prefs.edit().putString("feeds",value).apply();
        RadarJobService.schedule(this);
        Toast.makeText(this,"Flux enregistrés.",Toast.LENGTH_SHORT).show();
      }).setNegativeButton("Annuler",null).show();
  }

  private static String formatScanDate(long timestamp){
    return timestamp>0?new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",
      java.util.Locale.FRANCE).format(new java.util.Date(timestamp)):"Jamais";
  }

  private String sourceYield(String family){
    String key="source_metrics_"+family+"_";
    return prefs.getInt(key+"examined",0)+" / "+
      prefs.getInt(key+"accepted",0)+" / "+
      prefs.getInt(key+"priority",0);
  }

  private void showSourcesStatus(){
    int checked=prefs.getInt("notification_checked",0);
    String result=prefs.getString("notification_last_result","Aucune alerte encore examinée");
    String source=prefs.getString("notification_last_source","—");
    Set<String> platforms=prefs.getStringSet("watched_marketplace_packages",
      Collections.<String>emptySet());
    String status="Applications de travaux détectées automatiquement : "+prefs.getInt("auto_platforms_found",0)
      +"\\nPermission de notifications : "+(notificationAccess()?"ACTIVE":"NON ACTIVÉE")
      +"\\nLeboncoin : "+(prefs.getBoolean("watch_lbc",false)?"choisi":"non détecté")
      +"\\nFacebook : "+(prefs.getBoolean("watch_fb",false)?"choisi":"désactivé")
      +"\\nAutres plateformes installées : "+platforms.size()
      +"\\nMastodon : "+(prefs.getBoolean("watch_mastodon",false)?"actif":"désactivé")
      +"\\nRendement Leboncoin (alertes / dossiers / prioritaires) : "+
        sourceYield("leboncoin")
      +"\\nRendement Facebook (alertes / dossiers / prioritaires) : "+
        sourceYield("facebook")
      +"\\nRendement plateformes travaux (alertes / dossiers / prioritaires) : "+
        sourceYield("autres")
      +"\\nNotifications examinées : "+checked
      +"\\nDernière source : "+source
      +"\\nDernier résultat : "+result
      +"\\nFlux RSS : "+prefs.getString("scan_status","Jamais analysés")
      +"\\nVeille Android en arrière-plan : "+prefs.getString("background_scan_summary","Pas encore de rapport")
      +"\\nDernière veille Android terminée : "+formatScanDate(prefs.getLong("background_scan_completed_at",0))
      +"\\nDernière erreur Android : "+prefs.getString("background_scan_error","Aucune enregistrée")
      +"\\nSources/recherches échouées : "+prefs.getString("last_scan_failures","Aucune erreur détaillée");
    new AlertDialog.Builder(this).setTitle("Diagnostic du radar")
      .setMessage(status)
      .setPositiveButton("OK",null).show();
  }

  private static final int PICK_CSV=7403;
  private void chooseCsv(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("*/*");
    i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv",
      "text/comma-separated-values","text/plain","application/vnd.ms-excel"});
    startActivityForResult(i,PICK_CSV);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=PICK_CSV||result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri uri=data.getData();
    new Thread(()->{
      String report;
      try(InputStream input=getContentResolver().openInputStream(uri)){
        CsvIntake.Report imported=CsvIntake.importFile(this,input);
        report=imported.toString();
      }catch(Exception ex){report="Import impossible : fichier CSV invalide.";}
      final String text=report;
      runOnUiThread(()->{
        recalculate();
        new AlertDialog.Builder(this).setTitle("Demandes directes importées")
          .setMessage(text).setPositiveButton("OK",null).show();
      });
    },"radar-import-formulaires").start();
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
    }else{
      String sourcePackage=lead.optString("sourcePackage","");
      if(sourcePackage.matches("[a-zA-Z0-9_.]{3,160}")){
        Intent sourceIntent=getPackageManager().getLaunchIntentForPackage(sourcePackage);
        if(sourceIntent!=null){
          dlg.setPositiveButton("Ouvrir la plateforme",
            (d,which)->startActivity(sourceIntent));
        }
      }
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
    // L'heure est conservée pour éviter une boucle au retour de l'application.
    prefs.edit().putLong("map_last_scan_at",System.currentTimeMillis()).apply();
    eval("setRadarStatus",JSONObject.quote("Recherche automatique des nouvelles demandes en cours…"));
    eval("setRadarBusy","true");
    new Thread(()->{
      String status;
      try {
        RadarScanner.Report rss=RadarScanner.scan(getApplicationContext());
        prefs.edit().putString("last_scan_failures",rss.failedSources).apply();
        Set<String> apps=prefs.getStringSet("watched_marketplace_packages",
          Collections.<String>emptySet());
        int chosen=apps.size()+
          (prefs.getBoolean("watch_lbc",false)?1:0)+
          (prefs.getBoolean("watch_fb",false)?1:0);
        boolean permitted=notificationAccess();
        if(chosen==0&&rss.feeds==0){
          status="AUCUNE SOURCE ACTIVE : sélectionne les applications de travaux "+
            "dans Sources, puis active leurs alertes officielles.";
        }else if(chosen>0&&!permitted&&rss.feeds==0){
          status="INGESTION BLOQUÉE : "+chosen+
            " application(s) choisie(s), mais accès aux notifications Android "+
            "non autorisé. Ouvre Sources pour l'activer.";
        }else{
          status=(rss.errors>0?"COUVERTURE PARTIELLE : une source RSS a échoué. ":
            "Surveillance configurée. ")+
            chosen+" application(s) de travaux choisie(s)"+
            (permitted?" ; accès aux notifications autorisé. ":
              " ; accès aux notifications non autorisé. ")+
            rss.feeds+" flux RSS ; "+rss.checked+" publication(s) lue(s), "+
            rss.errors+" erreur(s). "+
            "Les plateformes ne transmettent que leurs propres alertes : "+
            "aucune garantie de couverture exhaustive.";
        }
      }catch(Exception e){status="Certaines sources sont indisponibles. Les dossiers déjà enregistrés restent visibles.";}
      final String note=status;
      scanning.set(false);
      runOnUiThread(()->{
        eval("setRadarBusy","false");
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
        int callable=0;
        for(int k=0;k<leads.length();k++){
          JSONObject item=leads.optJSONObject(k);
          if(item==null||item.optBoolean("demo",false)||
             "Classé".equals(item.optString("status")))continue;
          if(!item.optString("phone","").trim().isEmpty() &&
             item.optBoolean("callbackRequested",false) &&
             item.optString("callbackEvidence","").trim().length()>=8)callable++;
        }
        final int callableCount=callable;
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
          String original=lead.optString("detail","");
          String source=lead.optString("source","").toLowerCase(java.util.Locale.ROOT);
          boolean callback=!lead.optString("phone","").trim().isEmpty() &&
            lead.optBoolean("callbackRequested",false) &&
            lead.optString("callbackEvidence","").trim().length()>=8;
          // Requalifier les anciennes pistes à chaque affichage sans toucher aux dossiers.
          // Seules les demandes explicites sont présentées en premier.
          LeadClassifier.Result fresh=LeadClassifier.classify(original,rawCity);
          boolean explicit=callback || (fresh.eligible && "PRIORITAIRE".equals(fresh.status));
          preview.put("explicit",explicit);
          preview.put("callback",callback);
          preview.put("score",Math.max(0,Math.min(100,fresh.eligible?fresh.score:lead.optInt("score",0))));
          preview.put("source",lead.optString("source",""));
          preview.put("date",lead.optLong("date",0));
          preview.put("city",rawCity);
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
        // Agrégat seulement : aucune donnée nominative ou téléphone dans la page WebView.
        payload.put("callableCount",callableCount);
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
    // Affichage immédiat : aucune requête réseau ne bloque l'écran.
    Map<String,City> result=fallbackCenters();
    String cached=prefs.getString(GEO_JSON,"");
    if(!cached.isEmpty())mergeCenters(result,cached);
    return result;
  }
  private void refreshGeographyIfNecessary(){
    long age=System.currentTimeMillis()-prefs.getLong(GEO_WHEN,0L);
    boolean stale=age>30L*24L*60L*60L*1000L;
    if(!stale || !geoFetching.compareAndSet(false,true))return;
    new Thread(()->{
      try{
        String json=fetchOfficialCenters();
        Map<String,City> check=new HashMap<>();
        mergeCenters(check,json);
        if(check.size()>=110){
          prefs.edit().putString(GEO_JSON,json)
            .putLong(GEO_WHEN,System.currentTimeMillis()).apply();
          runOnUiThread(()->recalculate());
        }
      }catch(Exception ignored){
        // Hors réseau, la carte se base sur le cache et les centres de secours.
      }finally{geoFetching.set(false);}
    },"radar-map-geography").start();
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
