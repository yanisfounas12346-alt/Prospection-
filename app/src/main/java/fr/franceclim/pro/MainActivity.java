package fr.franceclim.pro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Tableau de bord du radar et CRM France Clim pour les particuliers. */
public class MainActivity extends Activity {
  private LinearLayout root, list;
  private EditText city, detail, contact;
  private Spinner type;
  private SharedPreferences prefs;
  private JSONArray leads=new JSONArray();
  private final int navy=Color.rgb(17,38,60), teal=Color.rgb(0,131,126);

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    prefs=getSharedPreferences("franceclim",MODE_PRIVATE);
    if(Build.VERSION.SDK_INT>=33 &&
       checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
    }
    RadarJobService.schedule(this);
    load();render();consumeShare(getIntent());
  }
  @Override protected void onNewIntent(Intent intent){
    super.onNewIntent(intent);setIntent(intent);consumeShare(intent);
  }
  @Override protected void onResume(){super.onResume();if(prefs!=null){load();if(root!=null)updateCount();}}
  private void consumeShare(Intent intent){
    if(Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())){
      String content=intent.getStringExtra(Intent.EXTRA_TEXT);
      if(content!=null){
        detail.setText(content);
        RadarClassifier.Result r=RadarClassifier.analyse(content);
        if(r!=null){
          city.setText(r.city);
          type.setSelection("Chauffe-eau".equals(r.category)?1:0);
        }
      }
    }
  }
  private void load(){
    try {leads=new JSONArray(prefs.getString("leads","[]"));}
    catch(Exception e){leads=new JSONArray();}
  }
  private TextView label(String value,int size,boolean bold){
    TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(navy);
    if(bold)t.setTypeface(null,1);t.setPadding(6,9,6,9);return t;
  }
  private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);return e;}
  private Button button(String value,Runnable action){
    Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextColor(Color.WHITE);
    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(teal));
    b.setOnClickListener(v->action.run());return b;
  }
  private void render(){
    ScrollView scroll=new ScrollView(this);
    root=new LinearLayout(this);root.setOrientation(1);root.setPadding(22,25,22,40);
    root.setBackgroundColor(Color.rgb(246,249,252));scroll.addView(root);setContentView(scroll);
    root.addView(label("FRANCE CLIM PRO • RADAR 13",23,true));
    root.addView(label("Demandes de particuliers • Climatisation • Chauffe-eau",14,false));
    root.addView(label("RADAR AUTOMATIQUE",19,true));
    String sources=prefs.getString("feeds","").trim();
    root.addView(label(sources.isEmpty()?
      "⚠️ Aucune source RSS configurée : le radar ne peut encore rien détecter.":
      "Sources configurées : "+countSources(sources)+" • Vérification périodique demandée (~15 min, variable selon Android)",13,false));
    root.addView(button("Configurer les sources d'annonces RSS",this::sourceDialog));
    root.addView(button("🔎 Scanner les sources maintenant",this::scanNow));
    root.addView(label("Dernier passage : "+prefs.getString("scan_status","Jamais"),12,false));
    root.addView(label("NOUVEAU DOSSIER",19,true));
    type=new Spinner(this);type.setAdapter(new ArrayAdapter<String>(this,
       android.R.layout.simple_spinner_dropdown_item,new String[]{"Climatisation","Chauffe-eau"}));
    root.addView(type);
    city=input("Ville du département 13");root.addView(city);
    contact=input("Contact / référence (facultatif)");root.addView(contact);
    detail=input("Texte de la demande ou lien partagé");detail.setMinLines(2);root.addView(detail);
    root.addView(button("Enregistrer le dossier",this::saveManual));
    root.addView(label("OPPORTUNITÉS À EXAMINER",19,true));
    list=new LinearLayout(this);list.setOrientation(1);root.addView(list);
    updateCount();
    root.addView(label("Les publications détectées ne constituent pas une autorisation de démarchage. Contact uniquement dans un cadre permis et sur demande explicite.",12,false));
  }
  private int countSources(String value){
    int count=0;for(String s:value.split("\\r?\\n"))if(s.trim().startsWith("https://"))count++;return count;
  }
  private void sourceDialog(){
    EditText editor=new EditText(this);
    editor.setHint("https://exemple.fr/demandes.rss");
    editor.setText(prefs.getString("feeds",""));
    editor.setMinLines(4);
    editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    new AlertDialog.Builder(this).setTitle("Sources RSS/Atom autorisées")
      .setMessage("Colle une URL HTTPS par ligne. Exemples : flux de demandes publiques ou Google Alerts RSS. N'ajoute pas une page web HTML ou un site qui interdit la collecte.")
      .setView(editor)
      .setPositiveButton("Enregistrer",(d,w)->{
        String value=editor.getText().toString().trim();
        for(String u:value.split("\\r?\\n")){
          if(!u.trim().isEmpty()&&!u.trim().startsWith("#")&&!u.trim().startsWith("https://")){
            Toast.makeText(this,"URL non HTTPS : "+u,Toast.LENGTH_LONG).show();return;
          }
        }
        prefs.edit().putString("feeds",value).putBoolean("radar_initialized",false).apply();
        RadarJobService.schedule(this);render();
      })
      .setNegativeButton("Annuler",null).show();
  }
  private void scanNow(){
    if(prefs.getString("feeds","").trim().isEmpty()){
      Toast.makeText(this,"Ajoute d'abord une source RSS.",Toast.LENGTH_LONG).show();return;
    }
    Toast.makeText(this,"Analyse en cours…",Toast.LENGTH_SHORT).show();
    new Thread(()->{
      RadarScanner.Report report=RadarScanner.scan(getApplicationContext());
      runOnUiThread(()->{load();render();new AlertDialog.Builder(this).setTitle("Radar : résultat")
        .setMessage(report.toString()).setPositiveButton("OK",null).show();});
    },"radar-scan-manuel").start();
  }
  private void saveManual(){
    String c=city.getText().toString().trim(),d=detail.getText().toString().trim();
    if(c.isEmpty()||d.isEmpty()){
      Toast.makeText(this,"Ville et description nécessaires.",Toast.LENGTH_SHORT).show();return;
    }
    String n=RadarClassifier.normal(d);
    if(n.contains(" je vends ")||n.contains(" a vendre ")||n.contains(" vente de ")){
      new AlertDialog.Builder(this).setMessage("Annonce de vente de matériel probable. Enregistrer quand même ?")
        .setPositiveButton("Oui",(x,y)->store(c,d)).setNegativeButton("Non",null).show();
    }else store(c,d);
  }
  private void store(String c,String d){
    load();
    for(int i=0;i<leads.length();i++){
      if(d.equalsIgnoreCase(leads.optJSONObject(i).optString("detail"))){
        Toast.makeText(this,"Demande déjà enregistrée",Toast.LENGTH_SHORT).show();return;
      }
    }
    try {
      JSONObject o=new JSONObject();
      o.put("city",c);o.put("detail",d);o.put("type",type.getSelectedItem().toString());
      o.put("contact",contact.getText().toString().trim());
      o.put("status","À qualifier");o.put("date",System.currentTimeMillis());o.put("score",0);
      o.put("link","");o.put("automatic",false);leads.put(o);
      persist();render();
    }catch(Exception e){Toast.makeText(this,"Échec d'enregistrement",Toast.LENGTH_LONG).show();}
  }
  private void persist(){prefs.edit().putString("leads",leads.toString()).apply();}
  private void updateCount(){
    if(list==null)return;
    list.removeAllViews();
    list.addView(label(leads.length()+" dossier(s) enregistré(s)",13,true));
    for(int j=leads.length()-1;j>=0;j--){
      final int idx=j;JSONObject o=leads.optJSONObject(j);if(o==null)continue;
      LinearLayout card=new LinearLayout(this);card.setOrientation(1);
      card.setPadding(12,12,12,14);card.setBackgroundColor(Color.WHITE);
      card.addView(label(o.optString("type")+" • "+o.optString("city"),17,true));
      card.addView(label(o.optString("status")+
        (o.optInt("score")>0?"  |  Priorité : "+o.optInt("score")+"/100":""),13,true));
      String desc=o.optString("detail");
      card.addView(label(desc.length()>230?desc.substring(0,230)+"…":desc,13,false));
      if(o.optString("link").startsWith("https://")){
        String link=o.optString("link");
        card.addView(button("Voir l'annonce originale",()->{
          try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}
          catch(Exception e){Toast.makeText(this,"Lien impossible à ouvrir",Toast.LENGTH_LONG).show();}
        }));
      }
      card.addView(button("Mettre à jour le dossier",()->edit(idx)));
      list.addView(card);
      View space=new View(this);space.setMinimumHeight(14);list.addView(space);
    }
  }
  private void edit(int index){
    String[] stages={"À vérifier","À qualifier","Contact autorisé","Visite gratuite proposée",
      "RDV confirmé","Visite effectuée","Devis après visite","Chantier signé","Classé"};
    new AlertDialog.Builder(this).setTitle("Étape commerciale").setItems(stages,(dialog,selected)->{
      load();
      try{leads.getJSONObject(index).put("status",stages[selected]);persist();updateCount();}
      catch(Exception ignored){}
    }).setNegativeButton("Annuler",null).show();
  }
}
