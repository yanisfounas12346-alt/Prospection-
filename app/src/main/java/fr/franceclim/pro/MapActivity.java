package fr.franceclim.pro;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vue de carte publique : uniquement ville + décompte, jamais coordonnées de clients.
 * Centres géographiques officiels des communes du 13 via geo.api.gouv.fr.
 */
public class MapActivity extends Activity {
  WebView map;
  TextView info;
  boolean pageReady=false;
  JSONArray pending=null;
  String error=null;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout frame=new LinearLayout(this);
    frame.setOrientation(LinearLayout.VERTICAL);
    info=new TextView(this);
    info.setText("FRANCE CLIM RADAR 13 • Carte des opportunités");
    RadarJobService.schedule(this);
    info.setPadding(14,12,14,12);
    frame.addView(info);
    map=new WebView(this);
    map.getSettings().setJavaScriptEnabled(true);
    map.getSettings().setDomStorageEnabled(false);
    map.getSettings().setAllowFileAccess(true);
    map.getSettings().setAllowContentAccess(false);
    map.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
    map.setWebViewClient(new WebViewClient(){
      @Override public void onPageFinished(WebView view,String url){
        pageReady=true;
        updatePage();
      }
    });
    frame.addView(map,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
    Button refresh=new Button(this);
    refresh.setText("🔎 Actualiser les opportunités");
    refresh.setOnClickListener(v->scanAndRefresh(refresh));
    frame.addView(refresh);
    setContentView(frame);
    map.loadUrl("file:///android_asset/radar-map.html");
    new Thread(()->calculatePins(),"radar-communes").start();
  }

  private void scanAndRefresh(Button button){
    button.setEnabled(false);
    info.setText("Recherche des opportunités publiques en cours…");
    new Thread(()->{
      RadarScanner.Report rss=RadarScanner.scan(getApplicationContext());
      BlueskyScanner.Report bsky=BlueskyScanner.scan(getApplicationContext());
      runOnUiThread(()->{
        button.setEnabled(true);
        info.setText("Sources analysées • "+rss.added+" RSS et "+bsky.added+
          " Bluesky ajoutée(s). Mise à jour de la carte…");
        new Thread(()->calculatePins(),"radar-refresh-map").start();
      });
    },"radar-scan-map").start();
  }

  @Override protected void onDestroy(){
    if(map!=null)map.destroy();
    super.onDestroy();
  }

  private void updatePage(){
    if(!pageReady)return;
    if(error!=null){
      map.evaluateJavascript("window.renderRadarError("+JSONObject.quote(error)+")",null);
    }else if(pending!=null){
      map.evaluateJavascript("window.renderRadar("+pending.toString()+")",null);
    }
  }

  private void calculatePins(){
    try {
      String json=fetchCommunes();
      JSONArray communes=new JSONArray(json);
      Map<String,double[]> centres=new HashMap<>();
      for(int i=0;i<communes.length();i++){
        JSONObject c=communes.optJSONObject(i);
        if(c==null)continue;
        JSONObject centre=c.optJSONObject("centre");
        if(centre==null)centre=c.optJSONObject("geometry");
        JSONArray xy=centre!=null?centre.optJSONArray("coordinates"):null;
        if(xy==null||xy.length()<2)continue;
        centres.put(LeadClassifier.normalize(c.optString("nom")),
          new double[]{xy.optDouble(1),xy.optDouble(0)});
      }
      JSONArray leads=RadarStore.leads(this);
      LinkedHashMap<String,int[]> groups=new LinkedHashMap<>();
      LinkedHashMap<String,JSONArray> opportunities=new LinkedHashMap<>();
      int noCity=0;
      for(int i=0;i<leads.length();i++){
        JSONObject lead=leads.optJSONObject(i);if(lead==null||lead.optBoolean("demo",false))continue;
        String city=lead.optString("city","").trim();
        String key=LeadClassifier.normalize(city);
        // Arrondissements 13001-13016 correspondent à Marseille.
        if(city.matches("130(0[1-9]|1[0-6])"))key="marseille";
        if(!centres.containsKey(key)){noCity++;continue;}
        int[] count=groups.get(key);
        if(count==null){count=new int[3];groups.put(key,count);}
        JSONArray entries=opportunities.get(key);
        if(entries==null){entries=new JSONArray();opportunities.put(key,entries);}
        JSONObject preview=new JSONObject();
        preview.put("type",lead.optString("type","Demande"));
        preview.put("score",lead.optInt("score",0));
        preview.put("source",lead.optString("source",""));
        preview.put("summary",lead.optString("detail","").substring(0,
          Math.min(180,lead.optString("detail","").length())));
        String link=lead.optString("link","");
        if(link.startsWith("https://"))preview.put("link",link);
        entries.put(preview);
        count[0]++;
        String type=lead.optString("type","").toLowerCase(java.util.Locale.ROOT);
        if(type.contains("clim"))count[1]++;
        if(type.contains("eau")||type.contains("cumulus"))count[2]++;
      }
      JSONArray data=new JSONArray();
      for(Map.Entry<String,int[]> e:groups.entrySet()){
        double[] ll=centres.get(e.getKey());
        if(ll==null)continue;
        JSONObject pin=new JSONObject();
        pin.put("city",e.getKey());pin.put("lat",ll[0]);pin.put("lon",ll[1]);
        pin.put("count",e.getValue()[0]);
        pin.put("opportunities",opportunities.get(e.getKey()));
        pin.put("types",e.getValue()[1]+" clim • "+e.getValue()[2]+" chauffe-eau");
        data.put(pin);
      }
      final int excluded=noCity;
      runOnUiThread(()->{
        pending=data;
        info.setText(data.length()+" commune(s) avec opportunités • "+excluded+
          " dossier(s) non localisés. Touchez une épingle pour les détails.");
        updatePage();
      });
    }catch(Exception ex){
      runOnUiThread(()->{
        error="Impossible de charger la géographie officielle (réseau indisponible)";
        info.setText(error+" ; les dossiers restent disponibles dans l'application.");
        updatePage();
      });
    }
  }

  private String fetchCommunes() throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(
      "https://geo.api.gouv.fr/departements/13/communes?fields=nom,centre").openConnection();
    c.setConnectTimeout(12000);
    c.setReadTimeout(16000);
    c.setRequestProperty("User-Agent","FranceClimRadar13/2.1 (carte communes 13)");
    try{
      if(c.getResponseCode()!=200)throw new java.io.IOException("Géographie non disponible");
      try(InputStream in=c.getInputStream()){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[4096];int n;
        while((n=in.read(buf))>=0){
          if(out.size()+n>2000000)throw new java.io.IOException("Taille excessive");
          out.write(buf,0,n);
        }
        return out.toString("UTF-8");
      }
    }finally{c.disconnect();}
  }
}
