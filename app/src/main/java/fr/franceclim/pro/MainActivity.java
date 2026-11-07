package fr.franceclim.pro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
  private final int navy=Color.rgb(16,35,54), teal=Color.rgb(0,126,123), secondary=Color.rgb(91,109,124);
  private LinearLayout root;
  private EditText manualText, manualCity;
  private boolean firstResume=true;

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    render();
    handleShare(getIntent());
    if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
    }
  }
  @Override protected void onNewIntent(Intent i){
    super.onNewIntent(i);setIntent(i);
    render();handleShare(i);
  }
  @Override protected void onResume(){
    super.onResume();
    if(firstResume)firstResume=false;
    else if(root!=null)render();
  }
  private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
  private TextView text(String s,int size,boolean bold,int color){
    TextView t=new TextView(this);
    t.setText(s);t.setTextSize(size);t.setTextColor(color);
    if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    t.setPadding(0,dp(3),0,dp(3));return t;
  }
  private GradientDrawable surface(int color,int radius){
    GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
  }
  private LinearLayout section(){
    LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(15),dp(13),dp(15),dp(13));
    b.setBackground(surface(Color.WHITE,15));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));
    root.addView(b,p);return b;
  }
  private void gap(LinearLayout holder,int height){View v=new View(this);holder.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
  private Button button(String label,Runnable onClick){
    Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(14);
    b.setTextColor(Color.WHITE);
    b.setBackgroundTintList(ColorStateList.valueOf(teal));
    b.setOnClickListener(v->onClick.run());return b;
  }
  private EditText input(String hint,int minLines){
    EditText e=new EditText(this);e.setHint(hint);e.setTextSize(14);e.setTextColor(navy);
    if(minLines>1){e.setMinLines(minLines);e.setGravity(android.view.Gravity.TOP);}
    e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    return e;
  }
  private boolean listenerEnabled(){
    String v=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
    return v!=null&&v.contains(getPackageName()+"/");
  }
  private void handleShare(Intent i){
    if(i==null||!Intent.ACTION_SEND.equals(i.getAction())||!"text/plain".equals(i.getType()))return;
    CharSequence body=i.getCharSequenceExtra(Intent.EXTRA_TEXT);
    if(body!=null){manualText.setText(body.toString());manualText.requestFocus();}
    i.setAction(Intent.ACTION_MAIN); // Don't re-import when Android resumes.
  }
  private void render(){
    ScrollView scroller=new ScrollView(this);
    root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(17),dp(24),dp(17),dp(24));
    root.setBackgroundColor(Color.rgb(242,247,250));
    scroller.addView(root);setContentView(scroller);
    TextView logo=text("❄  FRANCE CLIM PRO",23,true,navy);
    root.addView(logo);
    root.addView(text("RADAR PARTICULIERS · BOUCHES-DU-RHÔNE (13)",12,true,teal));
    root.addView(text("Climatisation + chauffe-eau · Visite technique gratuite",13,false,secondary));
    dashboard();
    settings();
    manualCapture();
    leadInbox();
    root.addView(text("Veille basée sur les notifications reçues et les contenus que vous partagez. L'app ne scrape aucun site et n'envoie aucun message à des particuliers.",12,false,secondary));
  }
  private void dashboard(){
    JSONArray arr=RadarStore.leads(this);int priority=0,ambiguous=0,visits=0,real=0;
    for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o==null)continue;
      if(!"DÉMO".equals(o.optString("source")))real++;
      if("PRIORITAIRE".equals(o.optString("verdict")))priority++;
      else ambiguous++;
      if(o.optString("status").contains("RDV")||o.optString("status").contains("Visite"))visits++;
    }
    LinearLayout b=section();
    b.addView(text("Votre radar",20,true,navy));
    b.addView(text("Écoute des alertes : "+(listenerEnabled()?"ACTIVÉE ✓":"À ACTIVER")+"  ·  Communes : "+LeadClassifier.COMMUNES.length,13,true,listenerEnabled()?teal:secondary));
    b.addView(text("Opportunités : "+arr.length()+"   |   Prioritaires : "+priority+"   |   À vérifier : "+ambiguous,14,true,navy));
    b.addView(text("Dont "+real+" dossiers hors démonstration · Suivis RDV/visite : "+visits,12,false,secondary));
  }
  private void settings(){
    LinearLayout b=section();b.addView(text("1. Connecter les alertes du téléphone",18,true,navy));
    b.addView(text("Vous devez autoriser une fois l'accès aux notifications dans Android. L'application ne conserve que les textes qui concernent la climatisation ou les chauffe-eau.",13,false,secondary));
    b.addView(button("Activer l'accès aux notifications",()->{
      Intent i=new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
      try{startActivity(i);}catch(Exception ex){Toast.makeText(this,"Ouvrez Paramètres > Notifications > Accès aux notifications",Toast.LENGTH_LONG).show();}
    }));
    Switch lbc=new Switch(this);lbc.setText("Surveiller les alertes Leboncoin");lbc.setTextColor(navy);
    lbc.setChecked(getSharedPreferences("franceclim",MODE_PRIVATE).getBoolean("watch_lbc",true));
    lbc.setOnCheckedChangeListener((v,checked)->RadarStore.setEnabled(this,"watch_lbc",checked));
    b.addView(lbc);
    Switch fb=new Switch(this);fb.setText("Surveiller les notifications de groupes Facebook");fb.setTextColor(navy);
    fb.setChecked(getSharedPreferences("franceclim",MODE_PRIVATE).getBoolean("watch_fb",false));
    fb.setOnCheckedChangeListener((v,checked)->RadarStore.setEnabled(this,"watch_fb",checked));
    b.addView(fb);
    b.addView(text("Confidentialité : Android autorise l'accès global aux notifications, mais le radar ignore les autres applications. Facebook est désactivé par défaut et les messages privés ne sont pas traités.",12,false,secondary));
    b.addView(text("Sur Leboncoin, sauvegardez des recherches sur le 13 et activez leurs alertes. Les notifications de simples ventes sont souvent rejetées ou signalées « à vérifier ».",12,false,secondary));
    b.addView(button("Tester avec une demande fictive",()->{
      String demo="Bonjour, je cherche un plombier pour remplacer mon chauffe-eau HS à Marseille 13008, plus d'eau chaude depuis ce matin. Je souhaite une visite.";
      RadarStore.AddResult a=RadarStore.add(this,demo,"","DÉMO");
      String info=a.classification==null?a.reason:a.classification.status+" · "+a.classification.score+"/100 · "+a.reason;
      new AlertDialog.Builder(this).setTitle("Test du radar").setMessage(info).setPositiveButton("OK",(d,w)->render()).show();
    }));
  }
  private void manualCapture(){
    LinearLayout b=section();b.addView(text("2. Importer une annonce ou une publication",18,true,navy));
    b.addView(text("Depuis Leboncoin, Facebook ou Chrome : « Partager » → France Clim Pro. Ou collez le texte ci-dessous.",13,false,secondary));
    manualCity=input("Ville (facultatif : détectée depuis le texte)",1);b.addView(manualCity);
    manualText=input("Texte de la demande, avec lien de l'annonce si disponible",3);b.addView(manualText);
    b.addView(button("Analyser et ajouter au radar",()->{
      String raw=manualText.getText().toString().trim();
      if(raw.isEmpty()){Toast.makeText(this,"Collez une demande à analyser",Toast.LENGTH_LONG).show();return;}
      RadarStore.AddResult ar=RadarStore.add(this,raw,manualCity.getText().toString().trim(),"Partage manuel");
      String detail=ar.classification==null?ar.reason:
        ar.classification.status+" • Score "+ar.classification.score+"/100\n"+
        ar.classification.type+" • "+ar.classification.city+"\n"+ar.classification.reason+"\n\n"+ar.reason;
      new AlertDialog.Builder(this).setTitle(ar.added?"Demande enregistrée":"Demande non ajoutée")
         .setMessage(detail).setPositiveButton("OK",(d,w)->{if(ar.added)render();}).show();
    }));
  }
  private void leadInbox(){
    LinearLayout b=section();b.addView(text("3. Opportunités détectées",18,true,navy));
    JSONArray a=RadarStore.leads(this);
    if(a.length()==0){b.addView(text("Aucune demande reçue. Activez les alertes ou utilisez « Tester » pour vérifier le radar.",13,false,secondary));return;}
    List<Integer> ids=new ArrayList<>();
    for(int i=0;i<a.length();i++)ids.add(i);
    Collections.sort(ids,new Comparator<Integer>(){
      @Override public int compare(Integer x,Integer y){
        JSONObject xx=a.optJSONObject(x),yy=a.optJSONObject(y);
        int sx=xx==null?0:xx.optInt("score"),sy=yy==null?0:yy.optInt("score");
        if(sx!=sy)return sy-sx;
        long dx=xx==null?0:xx.optLong("date"),dy=yy==null?0:yy.optLong("date");
        return Long.compare(dy,dx);
      }
    });
    int shown=0;for(int pos:ids){
      if(shown>=75)break;shown++;
      JSONObject o=a.optJSONObject(pos);if(o==null)continue;
      LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(dp(10),dp(10),dp(10),dp(12));card.setBackground(surface(Color.rgb(245,250,251),10));
      LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(7),0,dp(5));b.addView(card,cp);
      int score=o.optInt("score",0);
      card.addView(text(o.optString("type")+"  ·  "+o.optString("city"),16,true,navy));
      card.addView(text(score+"/100 · "+o.optString("verdict")+" · "+o.optString("source"),12,true,score>=75?teal:secondary));
      card.addView(text(o.optString("status"),12,true,navy));
      String d=o.optString("detail");card.addView(text(d.length()>200?d.substring(0,200)+"…":d,13,false,navy));
      card.addView(button("Ouvrir le dossier et préparer le RDV",()->leadMenu(pos)));
    }
    b.addView(button("Effacer les données du radar",()->{
      new AlertDialog.Builder(this).setTitle("Effacer tous les dossiers ?")
        .setMessage("Cette opération est définitive et supprime les informations stockées sur ce téléphone.")
        .setPositiveButton("Effacer",(d,w)->{RadarStore.clear(this);render();})
        .setNegativeButton("Annuler",null).show();
    }));
  }
  private void leadMenu(int index){
    JSONObject o=RadarStore.leads(this).optJSONObject(index);if(o==null)return;
    String[] items={"Proposer une visite technique gratuite (copier un brouillon)",
      "Changer l'étape commerciale","Ouvrir le lien de l'annonce","Voir les détails"};
    new AlertDialog.Builder(this).setTitle(o.optString("type")+" · "+o.optString("city"))
      .setItems(items,(dialog,which)->{
        if(which==0){
          String type=o.optString("type").toLowerCase();
          String message="Bonjour, suite à votre demande concernant "+type+
            ", France Clim peut vous proposer une visite technique gratuite et sans engagement pour étudier votre installation à "+
            o.optString("city")+". Souhaitez-vous convenir d'un rendez-vous ?";
          ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
          cb.setPrimaryClip(ClipData.newPlainText("Brouillon France Clim",message));
          new AlertDialog.Builder(this).setMessage("Brouillon copié. À utiliser uniquement si vous êtes autorisé à répondre à cette demande. Aucun message n'a été envoyé.").setPositiveButton("OK",null).show();
        }else if(which==1)stage(index);
        else if(which==2){
          String url=o.optString("link");
          if(!url.startsWith("https://")&&!url.startsWith("http://")){
            new AlertDialog.Builder(this).setMessage("Aucun lien n'a été fourni dans cette notification. Ouvrez l'application d'origine pour retrouver la publication.").setPositiveButton("OK",null).show();return;
          }
          try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
          catch(Exception e){Toast.makeText(this,"Lien non pris en charge",Toast.LENGTH_SHORT).show();}
        }else {
          new AlertDialog.Builder(this).setTitle("Dossier")
            .setMessage(o.optString("detail")+"\n\n"+o.optString("reason"))
            .setPositiveButton("OK",null).show();
        }
      }).show();
  }
  private void stage(int index){
    String[] stages={"À examiner","Demande explicite confirmée","Visite gratuite proposée",
      "RDV confirmé","Visite effectuée","Devis après visite","Chantier signé","Perdu / non pertinent"};
    new AlertDialog.Builder(this).setTitle("Suivi commercial")
      .setItems(stages,(d,k)->{RadarStore.updateStage(this,index,stages[k]);render();})
      .setNegativeButton("Annuler",null).show();
  }
}
