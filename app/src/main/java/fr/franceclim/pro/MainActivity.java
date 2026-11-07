package fr.franceclim.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
  LinearLayout root, list;
  EditText city, detail, contact;
  Spinner type, status;
  SharedPreferences prefs;
  final int navy=Color.rgb(15,32,52), teal=Color.rgb(0,128,124);
  JSONArray leads=new JSONArray();

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    prefs=getSharedPreferences("franceclim",MODE_PRIVATE);
    try { leads=new JSONArray(prefs.getString("leads","[]")); } catch(Exception ignored){}
    render();
    Intent i=getIntent();
    if(Intent.ACTION_SEND.equals(i.getAction()) && "text/plain".equals(i.getType())) {
      String shared=i.getStringExtra(Intent.EXTRA_TEXT);
      if(shared!=null) detail.setText(shared);
    }
  }
  TextView text(String s,int size,boolean bold) {
    TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(navy);
    if(bold)t.setTypeface(null,1);t.setPadding(4,10,4,10);return t;
  }
  EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(false);return e;}
  Button button(String label,Runnable click) {
    Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(teal));
    b.setOnClickListener(v->click.run());return b;
  }
  void render() {
    ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,28,24,28);
    root.setBackgroundColor(Color.rgb(246,249,251));scroll.addView(root);setContentView(scroll);
    root.addView(text("FRANCE CLIM PRO",25,true));
    root.addView(text("Particuliers • Climatisation & chauffe-eau • Bouches-du-Rhône (13)",14,false));
    root.addView(text("Nouvelle opportunité",20,true));
    type=new Spinner(this);type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Climatisation","Chauffe-eau"}));root.addView(type);
    city=input("Ville (ex. Marseille, Istres, Aix-en-Provence)");root.addView(city);
    contact=input("Contact / référence (facultatif)");root.addView(contact);
    detail=input("Texte de la demande ou lien de l'annonce");detail.setMinLines(3);root.addView(detail);
    root.addView(button("Analyser et enregistrer",()->save()));
    root.addView(text("Dossiers enregistrés : "+leads.length(),20,true));
    list=new LinearLayout(this);list.setOrientation(1);root.addView(list);showLeads();
    root.addView(text("Les contacts sont traités uniquement lorsque le particulier a demandé à être contacté ou qu'un canal autorisé le permet. Aucune prospection automatique.",12,false));
  }
  boolean contains(String s,String... words){for(String w:words)if(s.contains(w))return true;return false;}
  void save() {
    String c=city.getText().toString().trim(),d=detail.getText().toString().trim(),ct=contact.getText().toString().trim();
    if(c.isEmpty()||d.isEmpty()){Toast.makeText(this,"Indique la ville et la demande.",Toast.LENGTH_LONG).show();return;}
    String normalized=d.toLowerCase(java.util.Locale.ROOT);
    if(contains(normalized,"je vends","à vendre","a vendre","vend mon","vente de")) {
      new AlertDialog.Builder(this).setMessage("Cette annonce ressemble à une vente de matériel, pas à une demande d'installation. Enregistrer quand même ?")
        .setPositiveButton("Enregistrer",(x,y)->insert(c,d,ct)).setNegativeButton("Annuler",null).show();return;
    }
    insert(c,d,ct);
  }
  void insert(String c,String d,String ct) {
    try {
      for(int j=0;j<leads.length();j++){JSONObject old=leads.getJSONObject(j);
        if(d.equalsIgnoreCase(old.optString("detail"))){Toast.makeText(this,"Doublon déjà enregistré",Toast.LENGTH_SHORT).show();return;}
      }
      JSONObject item=new JSONObject();item.put("city",c);item.put("detail",d);item.put("contact",ct);
      item.put("type",type.getSelectedItem().toString());item.put("status","À qualifier");item.put("date",System.currentTimeMillis());
      leads.put(item);persist();render();
    }catch(Exception e){Toast.makeText(this,"Erreur d'enregistrement",Toast.LENGTH_LONG).show();}
  }
  void persist(){prefs.edit().putString("leads",leads.toString()).apply();}
  void showLeads() {
    list.removeAllViews();
    for(int j=leads.length()-1;j>=0;j--){
      final int idx=j;
      JSONObject o=leads.optJSONObject(j);if(o==null)continue;
      LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(14,12,14,18);
      card.setBackgroundColor(Color.WHITE);
      card.addView(text(o.optString("type")+" • "+o.optString("city"),18,true));
      card.addView(text(o.optString("status"),13,true));
      String s=o.optString("detail");card.addView(text(s.length()>180?s.substring(0,180)+"…":s,14,false));
      card.addView(button("Mettre à jour le suivi",()->edit(idx)));
      list.addView(card);
      View space=new View(this);space.setMinimumHeight(12);list.addView(space);
    }
  }
  void edit(int idx){
    String[] stages={"À qualifier","Contact autorisé","Visite gratuite proposée","RDV confirmé","Visite effectuée","Devis après visite","Chantier signé","Classé"};
    new AlertDialog.Builder(this).setTitle("Étape commerciale").setItems(stages,(dialog,which)->{
      try {leads.getJSONObject(idx).put("status",stages[which]);persist();render();}catch(Exception ignored){}
    }).setNegativeButton("Annuler",null).show();
  }
}
