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
  private EditText city, detail, contact, personName, personPhone, callbackEvidence;
  private CheckBox callbackRequested;
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
  @Override protected void onResume(){super.onResume();if(prefs!=null){load();if(root!=null)render();}}
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
    root.addView(button("🗺 Carte des opportunités dans le 13",()->
      startActivity(new Intent(this,MapActivity.class))));
    root.addView(label("SOURCES DE DEMANDES",19,true));
    root.addView(button("Choisir et connecter les sources compatibles",this::sourceHub));
    root.addView(label("État : Leboncoin "+(prefs.getBoolean("watch_lbc",true)?"activé":"désactivé")+
      " • Facebook "+(prefs.getBoolean("watch_fb",false)?"activé":"désactivé")+
      " • flux RSS "+countSources(prefs.getString("feeds",""))+
      " • formulaires CSV à importer depuis ce téléphone",12,false));
    String lastSource=prefs.getString("notification_last_source","");
    long lastNotif=prefs.getLong("notification_last_at",0);
    root.addView(label("Notifications examinées : "+prefs.getInt("notification_checked",0)
      +(lastNotif>0?" | Dernière : "+new java.util.Date(lastNotif).toString()+
       " ("+lastSource+")":" | Aucune alerte reçue"),12,false));
    root.addView(label("RADAR AUTOMATIQUE",19,true));
    root.addView(label("ALERTES LEBONCOIN / FACEBOOK (avec autorisation)",16,true));
    root.addView(label(notificationAccess()
      ? "Accès aux notifications actif. Les alertes de recherche sauvegardées peuvent être analysées."
      : "Accès non activé : aucune alerte Leboncoin/Facebook ne sera lue.",13,false));
    root.addView(button("Activer ou vérifier l'accès aux notifications",this::openNotificationSettings));
    Switch lbc=new Switch(this);
    lbc.setText("Surveiller les alertes Leboncoin");
    lbc.setChecked(prefs.getBoolean("watch_lbc",true));
    lbc.setOnCheckedChangeListener((control,checked)->
      prefs.edit().putBoolean("watch_lbc",checked).apply());
    root.addView(lbc);
    Switch fb=new Switch(this);
    fb.setText("Surveiller les notifications de groupes Facebook (facultatif)");
    fb.setChecked(prefs.getBoolean("watch_fb",false));
    fb.setOnCheckedChangeListener((control,checked)->
      prefs.edit().putBoolean("watch_fb",checked).apply());
    root.addView(fb);
    root.addView(label("L'autorisation d'accès aux notifications est large. Le code ne traite que les applications activées ci-dessus ; désactive-la dans les paramètres Android à tout moment.",12,false));
    root.addView(label("SOURCES PUBLIQUES SANS COMPTE",16,true));
    Switch bluesky=new Switch(this);
    bluesky.setText("Bluesky : rechercher les demandes publiques (API officielle)");
    bluesky.setChecked(prefs.getBoolean("watch_bluesky",true));
    bluesky.setOnCheckedChangeListener((v,checked)->{
      prefs.edit().putBoolean("watch_bluesky",checked).apply();
      RadarJobService.schedule(this);
    });
    root.addView(bluesky);
    root.addView(label(prefs.getString("bluesky_scan_status","Bluesky : pas encore vérifié"),12,false));
    Switch mastodon=new Switch(this);
    mastodon.setText("Mastodon : hashtags publics climatisation / chauffe-eau");
    mastodon.setChecked(prefs.getBoolean("watch_mastodon",true));
    mastodon.setOnCheckedChangeListener((v,checked)->{
      prefs.edit().putBoolean("watch_mastodon",checked).apply();
      RadarJobService.schedule(this);
    });
    root.addView(mastodon);
    root.addView(label("Ces flux peuvent ne contenir aucune demande dans le 13 ou être indisponibles ; aucun prospect fictif n'est créé.",12,false));
    root.addView(label("FLUX RSS/ATOM (autres sources autorisées)",16,true));
    String sources=prefs.getString("feeds","").trim();
    root.addView(label(sources.isEmpty()?
      "Aucun flux RSS configuré. Les alertes officielles restent utilisables si leur accès est activé.":
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
    root.addView(label("COORDONNÉES COMMUNIQUÉES PAR LE PARTICULIER",14,true));
    personName=input("Nom / prénom (si la personne l'a communiqué)");root.addView(personName);
    personPhone=input("Téléphone communiqué (facultatif)");root.addView(personPhone);
    callbackRequested=new CheckBox(this);
    callbackRequested.setText("La personne a explicitement demandé à être rappelée");
    root.addView(callbackRequested);
    callbackEvidence=input("Preuve de la demande de rappel : formulaire, date, référence...");
    root.addView(callbackEvidence);
    root.addView(label("Un numéro publié sur Internet n'autorise pas un appel commercial. En rénovation énergétique, une réponse à une demande explicite de rappel est strictement encadrée.",12,false));
    root.addView(button("Analyser et ajouter la demande",this::saveManual));
    root.addView(button("Importer un CSV de formulaires reçus",this::chooseCsv));
    root.addView(button("Sauvegarder mes dossiers et contacts (JSON)",this::exportBackup));
    root.addView(button("Restaurer une sauvegarde de dossiers",this::importBackup));
    root.addView(label("La sauvegarde contient des données personnelles. Ne la publie jamais sur GitHub et conserve-la dans un emplacement privé.",12,false));
    root.addView(label("OPPORTUNITÉS À EXAMINER",19,true));
    list=new LinearLayout(this);list.setOrientation(1);root.addView(list);
    updateCount();
    root.addView(label("Les publications détectées ne constituent pas une autorisation de démarchage. Contact uniquement dans un cadre permis et sur demande explicite.",12,false));
  }
  private void sourceHub(){
    String[] options={
      "Bluesky (API publique sans compte)",
      "Mastodon (hashtags RSS publics)",
      "Alertes Google (RSS ou e-mail)",
      "Leboncoin (recherches sauvegardées / alertes officielles)",
      "Facebook (notifications de groupes avec accord)",
      "Flux RSS / Atom HTTPS autorisés",
      "Formulaires entrants (import CSV avec nom / téléphone)",
      "WhatsApp Business (notifications au professionnel)",
      "Aide : sources, permission et confidentialité"
    };
    new AlertDialog.Builder(this).setTitle("Connecteurs compatibles")
      .setMessage("Sélectionne une source. Un site qui ne fournit pas de flux ou d'API autorisée ne peut pas être raccordé automatiquement.")
      .setItems(options,(dialog,which)->{
        switch(which){
          case 0:openPublicUrl("https://bsky.app/search?q=climatisation");break;
          case 1:openPublicUrl("https://mastodon.social/tags/climatisation");break;
          case 2:openPublicUrl("https://www.google.com/alerts");break;
          case 3:openPublicUrl("https://www.leboncoin.fr/");break;
          case 4:openPublicUrl("https://www.facebook.com/groups/");break;
          case 5:sourceDialog();break;
          case 6:chooseCsv();break;
          case 7:
          case 8:openPublicUrl("https://github.com/yanisfounas12346-alt/Prospection-/blob/main/docs/MISE_EN_SERVICE_RADAR.md");break;
        }
      }).setNegativeButton("Fermer",null).show();
  }
  private void openPublicUrl(String url){
    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
    catch(Exception e){Toast.makeText(this,"Ouvre le site dans un navigateur.",Toast.LENGTH_SHORT).show();}
  }

  private boolean notificationAccess(){
    try {
      String value=android.provider.Settings.Secure.getString(
        getContentResolver(),"enabled_notification_listeners");
      return value!=null && value.contains(getPackageName()+"/");
    }catch(Exception e){return false;}
  }
  private void openNotificationSettings(){
    new AlertDialog.Builder(this)
      .setTitle("Autorisation Android")
      .setMessage("Android donnera à France Clim Pro l'accès aux notifications affichées sur le téléphone. L'application ne retient que les alertes pertinentes des applications sélectionnées. N'active cet accès que si tu acceptes cette permission large.")
      .setPositiveButton("Ouvrir les paramètres",(dialog,which)->{
        try {startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}
        catch(Exception e){Toast.makeText(this,"Paramètres indisponibles",Toast.LENGTH_LONG).show();}
      }).setNegativeButton("Annuler",null).show();
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
    if(prefs.getString("feeds","").trim().isEmpty() &&
      !prefs.getBoolean("watch_mastodon",true)&&
      !prefs.getBoolean("watch_bluesky",true)){
      Toast.makeText(this,"Active une source pour commencer le radar.",Toast.LENGTH_LONG).show();return;
    }
    Toast.makeText(this,"Scan des sources publiques en cours…",Toast.LENGTH_SHORT).show();
    new Thread(()->{
      RadarScanner.Report rss=RadarScanner.scan(getApplicationContext());
      BlueskyScanner.Report bsky=BlueskyScanner.scan(getApplicationContext());
      runOnUiThread(()->{load();render();new AlertDialog.Builder(this).setTitle("Radar : résultat")
        .setMessage(rss.toString()+"\\n"+bsky.toString()+
          "\\nLes résultats sont des pistes à vérifier, pas des contacts autorisés.")
        .setPositiveButton("OK",null).show();});
    },"radar-scan-manuel").start();
  }
  private void saveManual(){
    String c=city.getText().toString().trim();
    String d=detail.getText().toString().trim();
    if(d.isEmpty()){
      Toast.makeText(this,"Ajoute le texte d'une demande.",Toast.LENGTH_SHORT).show();
      return;
    }
    RadarStore.AddResult result=RadarStore.add(this,d,c,"Partage / saisie");
    if(result.added){
      int idx=RadarStore.leads(this).length()-1;
      if(!RadarStore.setContact(this,idx,personName.getText().toString(),
         personPhone.getText().toString(),callbackRequested.isChecked(),
         callbackEvidence.getText().toString())){
        Toast.makeText(this,"Dossier ajouté, mais coordonnées invalides ou preuve de rappel absente.",Toast.LENGTH_LONG).show();
      }
      load();render();
      Toast.makeText(this,"Opportunité enregistrée pour vérification",Toast.LENGTH_LONG).show();
      return;
    }
    if(result.classification!=null && result.classification.eligible){
      Toast.makeText(this,result.reason,Toast.LENGTH_LONG).show();
      return;
    }
    if(c.isEmpty()){
      Toast.makeText(this,"Ville à confirmer avant la sauvegarde manuelle.",Toast.LENGTH_LONG).show();
      return;
    }
    new AlertDialog.Builder(this).setTitle("Demande non confirmée")
      .setMessage("Le texte ne ressemble pas clairement à une demande de particulier. L'enregistrer quand même comme dossier à vérifier ?")
      .setPositiveButton("Enregistrer",(dialog,which)->store(c,d))
      .setNegativeButton("Annuler",null).show();
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
      persist();
      RadarStore.setContact(this,leads.length()-1,personName.getText().toString(),
         personPhone.getText().toString(),callbackRequested.isChecked(),
         callbackEvidence.getText().toString());
      render();
    }catch(Exception e){Toast.makeText(this,"Échec d'enregistrement",Toast.LENGTH_LONG).show();}
  }
  private void persist(){prefs.edit().putString("leads",leads.toString()).apply();}
  private void updateCount(){
    if(list==null)return;
    list.removeAllViews();
    int contactCount=0,priorityCount=0,callbackCount=0;
    java.util.List<Integer> order=new java.util.ArrayList<>();
    for(int i=0;i<leads.length();i++){
      JSONObject p=leads.optJSONObject(i);if(p==null)continue;
      order.add(i);
      if(!p.optString("personName").isEmpty()||!p.optString("phone").isEmpty())contactCount++;
      if(p.optInt("score")>=75)priorityCount++;
      if(p.optBoolean("callbackRequested",false))callbackCount++;
    }
    java.util.Collections.sort(order,(left,right)->{
      JSONObject a=leads.optJSONObject(left),b=leads.optJSONObject(right);
      int sa=a==null?0:a.optInt("score"),sb=b==null?0:b.optInt("score");
      if(sb!=sa)return Integer.compare(sb,sa);
      long da=a==null?0:a.optLong("date"),db=b==null?0:b.optLong("date");
      return Long.compare(db,da);
    });
    list.addView(label(leads.length()+" dossier(s) • "+priorityCount+" prioritaires • "+
      contactCount+" avec nom ou téléphone • "+callbackCount+" demandes de rappel documentées",13,true));
    for(int j=0;j<order.size();j++){
      final int idx=order.get(j);JSONObject o=leads.optJSONObject(idx);if(o==null)continue;
      LinearLayout card=new LinearLayout(this);card.setOrientation(1);
      card.setPadding(12,12,12,14);card.setBackgroundColor(Color.WHITE);
      card.addView(label(o.optString("type")+" • "+o.optString("city"),17,true));
      card.addView(label(o.optString("status")+
        (o.optInt("score")>0?"  |  Priorité : "+o.optInt("score")+"/100":""),13,true));
      String desc=o.optString("detail");
      card.addView(label(desc.length()>230?desc.substring(0,230)+"…":desc,13,false));
      String name=o.optString("personName",""),phone=o.optString("phone","");
      if(!name.isEmpty())card.addView(label("Nom communiqué : "+name,13,true));
      if(!phone.isEmpty())card.addView(label("Téléphone communiqué : "+phone,13,false));
      if(o.optBoolean("callbackRequested",false) && !phone.isEmpty()){
        String number=phone;
        card.addView(button("Appel en réponse à sa demande (vérifier le délai)",()->
          dialCallback(number)));
      }else if(!phone.isEmpty()){
        card.addView(label("Pas de preuve de rappel : ne pas appeler à des fins commerciales.",12,false));
      }
      card.addView(button("Renseigner le nom / téléphone",()->editContact(idx)));
      String appt=o.optString("appointment_at","");
      if(!appt.isEmpty())card.addView(label("Visite gratuite : "+appt,13,true));
      card.addView(button("Planifier une visite gratuite",()->scheduleVisit(idx)));
      if(o.optString("link").startsWith("https://")){
        String link=o.optString("link");
        card.addView(button("Voir l'annonce originale",()->{
          try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}
          catch(Exception e){Toast.makeText(this,"Lien impossible à ouvrir",Toast.LENGTH_LONG).show();}
        }));
      }
      card.addView(button("Partager l'alerte sur WhatsApp",()->shareWhatsapp(o)));
      card.addView(button("Mettre à jour le dossier",()->edit(idx)));
      card.addView(button("Supprimer le dossier et les coordonnées",()->{
        new AlertDialog.Builder(this).setTitle("Supprimer ce dossier ?")
          .setMessage("Suppression locale définitive du dossier et de ses coordonnées. Sauvegarde d'abord si nécessaire.")
          .setPositiveButton("Supprimer",(dialog,which)->{
            RadarStore.delete(this,idx);load();render();
          }).setNegativeButton("Annuler",null).show();
      }));
      list.addView(card);
      View space=new View(this);space.setMinimumHeight(14);list.addView(space);
    }
  }
  /** Partage manuel d'un résumé sans coordonnées, vers WhatsApp uniquement. */
  private void shareWhatsapp(JSONObject item){
    String value="France Clim Radar 13\\n"+
      "Demande à vérifier : "+item.optString("type","Travaux")+"\\n"+
      "Commune : "+item.optString("city","À vérifier")+"\\n"+
      "Priorité : "+item.optInt("score",0)+"/100\\n"+
      "Statut : "+item.optString("status","À examiner");
    String original=item.optString("link","");
    if(original.startsWith("https://")){
      try {
        Uri uri=Uri.parse(original);
        if(uri.getHost()!=null) {
          value+="\\nPublication : "+uri.buildUpon().clearQuery().fragment(null).build().toString();
        }
      }catch(Exception ignored){}
    }
    Intent send=new Intent(Intent.ACTION_SEND);
    send.setType("text/plain");
    send.putExtra(Intent.EXTRA_TEXT,value);
    try {
      send.setPackage("com.whatsapp");
      startActivity(send);
    }catch(Exception personalMissing){
      try {
        send.setPackage("com.whatsapp.w4b");
        startActivity(send);
      }catch(Exception businessMissing){
        send.setPackage(null);
        new AlertDialog.Builder(this)
          .setTitle("WhatsApp non détecté")
          .setMessage("Installe WhatsApp ou WhatsApp Business pour partager l'alerte. Tu peux utiliser le menu Partager en attendant.")
          .setPositiveButton("Ouvrir le menu Partager",(dialog,which)->
            startActivity(Intent.createChooser(send,"Partager l'alerte")))
          .setNegativeButton("Annuler",null).show();
      }
    }
  }

  private void dialCallback(String phone) {
    new AlertDialog.Builder(this).setTitle("Vérifier le droit de rappeler")
      .setMessage("Ne rappeler que si la personne a demandé explicitement ce rappel pour ce chantier, dans les délais légaux (notamment 5 jours ouvrables pour certaines demandes liées à la rénovation énergétique). Ne pas utiliser cette fiche pour un démarchage.")
      .setPositiveButton("Ouvrir le composeur",(dialog,which)->{
        try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone,null)));}
        catch(Exception e){Toast.makeText(this,"Impossible d'ouvrir le téléphone",Toast.LENGTH_LONG).show();}
      }).setNegativeButton("Annuler",null).show();
  }

  private void editContact(int index){
    JSONArray arr=RadarStore.leads(this);
    JSONObject item=arr.optJSONObject(index);
    if(item==null)return;
    LinearLayout fields=new LinearLayout(this);
    fields.setOrientation(LinearLayout.VERTICAL);
    fields.setPadding(24,8,24,8);
    EditText name=input("Nom / prénom communiqué");
    name.setText(item.optString("personName",""));
    fields.addView(name);
    EditText tel=input("Numéro communiqué");
    tel.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
    tel.setText(item.optString("phone",""));
    fields.addView(tel);
    CheckBox callback=new CheckBox(this);
    callback.setText("Demande explicite de rappel reçue");
    callback.setChecked(item.optBoolean("callbackRequested",false));
    fields.addView(callback);
    EditText proof=input("Preuve / date / canal de la demande explicite");
    proof.setText(item.optString("callbackEvidence",""));
    fields.addView(proof);
    new AlertDialog.Builder(this).setTitle("Coordonnées communiquées")
      .setMessage("Ne saisir que les informations reçues volontairement dans le cadre de la demande.")
      .setView(fields).setPositiveButton("Enregistrer",(dialog,which)->{
        boolean ok=RadarStore.setContact(this,index,name.getText().toString(),
          tel.getText().toString(),callback.isChecked(),proof.getText().toString());
        if(!ok)Toast.makeText(this,"Coordonnées invalides ou preuve de demande de rappel manquante.",Toast.LENGTH_LONG).show();
        load();render();
      }).setNegativeButton("Annuler",null).show();
  }

  private void scheduleVisit(int index){
    EditText appointment=input("Date et heure de visite (ex. 2026-11-12 14:30)");
    new AlertDialog.Builder(this).setTitle("Visite technique gratuite")
      .setMessage("Visite sur place, gratuite et sans engagement, avant devis.")
      .setView(appointment).setPositiveButton("Confirmer",(dialog,which)->{
        if(!RadarStore.setVisit(this,index,appointment.getText().toString()))
          Toast.makeText(this,"Indiquer une date et une heure.",Toast.LENGTH_LONG).show();
        load();render();
      }).setNegativeButton("Annuler",null).show();
  }

  private static final int PICK_BACKUP_EXPORT=6402;
  private static final int PICK_BACKUP_IMPORT=6403;

  private void exportBackup(){
    new AlertDialog.Builder(this).setTitle("Sauvegarde privée")
      .setMessage("Le fichier JSON contiendra les noms, numéros, textes et demandes de rappel enregistrés. Conserve-le dans un espace privé et ne le publie pas.")
      .setPositiveButton("Choisir où enregistrer",(dialog,which)->{
        Intent create=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("application/json");
        create.putExtra(Intent.EXTRA_TITLE,"FranceClimRadar13-sauvegarde.json");
        startActivityForResult(create,PICK_BACKUP_EXPORT);
      }).setNegativeButton("Annuler",null).show();
  }
  private void importBackup(){
    Intent open=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    open.addCategory(Intent.CATEGORY_OPENABLE);
    open.setType("application/json");
    startActivityForResult(open,PICK_BACKUP_IMPORT);
  }
  private void saveBackup(Uri uri){
    try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
      if(out==null)throw new java.io.IOException("Destination inaccessible");
      JSONObject backup=new JSONObject();
      backup.put("format","FranceClimRadar13-backup");
      backup.put("version",1);
      backup.put("exportedAt",System.currentTimeMillis());
      backup.put("leads",RadarStore.leads(this));
      out.write(backup.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      Toast.makeText(this,"Sauvegarde créée. Garde ce fichier privé.",Toast.LENGTH_LONG).show();
    }catch(Exception e){Toast.makeText(this,"Sauvegarde impossible.",Toast.LENGTH_LONG).show();}
  }
  private void restoreBackup(Uri uri){
    try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
      if(in==null)throw new java.io.IOException("Fichier inaccessible");
      java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
      byte[] buf=new byte[4096];int n;
      while((n=in.read(buf))!=-1){
        if(out.size()+n>1400000)throw new java.io.IOException("Fichier trop grand");
        out.write(buf,0,n);
      }
      JSONObject backup=new JSONObject(out.toString("UTF-8"));
      if(!"FranceClimRadar13-backup".equals(backup.optString("format")))
        throw new java.io.IOException("Format de sauvegarde non reconnu");
      JSONArray data=backup.getJSONArray("leads");
      new AlertDialog.Builder(this).setTitle("Restaurer la sauvegarde")
        .setMessage(data.length()+" dossiers dans le fichier. La restauration remplacera les dossiers actuellement enregistrés sur le téléphone. Continuer ?")
        .setPositiveButton("Remplacer les dossiers",(dialog,which)->{
          if(RadarStore.replaceFromBackup(this,data)){
            load();render();
            Toast.makeText(this,"Sauvegarde restaurée.",Toast.LENGTH_LONG).show();
          }else Toast.makeText(this,"Sauvegarde invalide ou trop volumineuse.",Toast.LENGTH_LONG).show();
        }).setNegativeButton("Annuler",null).show();
    }catch(Exception e){Toast.makeText(this,"Import impossible : JSON invalide.",Toast.LENGTH_LONG).show();}
  }

  private static final int PICK_CSV=6401;
  private void chooseCsv(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("*/*");
    i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/comma-separated-values","text/plain",
      "application/vnd.ms-excel"});
    startActivityForResult(i,PICK_CSV);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri uri=data.getData();
    if(request==PICK_BACKUP_EXPORT){saveBackup(uri);return;}
    if(request==PICK_BACKUP_IMPORT){restoreBackup(uri);return;}
    if(request!=PICK_CSV)return;
    new Thread(()->{
      try(java.io.InputStream stream=getContentResolver().openInputStream(uri)){
        CsvIntake.Report r=CsvIntake.importFile(this,stream);
        runOnUiThread(()->{load();render();
          new AlertDialog.Builder(this).setTitle("Import CSV de demandes directes")
            .setMessage(r.toString()).setPositiveButton("OK",null).show();
        });
      }catch(Exception ex){
        runOnUiThread(()->Toast.makeText(this,"Import impossible : vérifier le CSV.",Toast.LENGTH_LONG).show());
      }
    },"radar-import-csv").start();
  }

  private void edit(int index){
    String[] stages={"À vérifier","À qualifier","Contact autorisé","Visite gratuite proposée",
      "RDV confirmé","Visite effectuée","Devis après visite","Chantier signé","Classé"};
    new AlertDialog.Builder(this).setTitle("Étape commerciale").setItems(stages,(dialog,selected)->{
      boolean ok=RadarStore.updateStage(this,index,stages[selected]);
      if(!ok)Toast.makeText(this,"Visite technique obligatoire avant le devis ou la signature.",Toast.LENGTH_LONG).show();
      load();render();
    }).setNegativeButton("Annuler",null).show();
  }
}
