package fr.franceclim.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Locale;

/**
 * Opt-in watch of selected app notifications only.
 * Never scrapes, opens pages, contacts a person, or transmits data to a server.
 */
public class RadarNotificationListener extends NotificationListenerService {
  private static final String CHANNEL="radar_opportunities";
  @Override public void onNotificationPosted(StatusBarNotification sbn){
    if(sbn==null||!RadarStore.enabled(this,sbn.getPackageName()))return;
    Notification original=sbn.getNotification();
    if(original==null||original.extras==null)return;
    Bundle e=original.extras;
    String title=string(e.getCharSequence(Notification.EXTRA_TITLE));
    String summary=string(e.getCharSequence(Notification.EXTRA_TEXT));
    String big=string(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
    CharSequence[] lines=e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
    StringBuilder text=new StringBuilder(title).append(" ").append(summary).append(" ").append(big);
    if(lines!=null)for(CharSequence line:lines)text.append(" ").append(string(line));
    String full=text.toString().trim();
    if(full.length()<12)return;

    // Never scan Facebook private DMs, contacts, comments or other unrelated notices.
    if("com.facebook.katana".equals(sbn.getPackageName())){
      String n=LeadClassifier.normalize(full);
      if(!(n.contains(" publie dans ")||n.contains(" publication dans ")||n.contains(" posted in ")||
            n.contains(" dans le groupe ")||n.contains(" group ")||n.contains(" groupe ")))return;
    }
    String pkg=sbn.getPackageName();
    if(!"fr.leboncoin".equals(pkg)&&!"com.facebook.katana".equals(pkg)){
      // Les demandes publiques ou avis de nouvelles annonces uniquement ; jamais les
      // notifications de conversation privée, paiement, messagerie ou compte.
      String n=LeadClassifier.normalize(title+" "+summary+" "+big);
      if(!(n.contains(" demande ")||n.contains(" recherche ")||
           n.contains(" annonce ")||n.contains(" chantier ")||
           n.contains(" un voisin ")||n.contains(" besoin ")||
           n.contains(" nouvelle mission ")||n.contains(" nouveau projet ")||
           n.contains(" sollicitation ")||n.contains(" intervention ")||
           n.contains(" votre secteur ")||n.contains(" votre zone ")))return;
      if(n.contains(" message prive ")||n.contains(" votre conversation ")||
         n.contains(" paiement ")||n.contains(" connexion a votre compte ")||
         n.contains(" verification de compte ")||n.contains(" nouveau commentaire ")||
         n.contains(" code de connexion ")||n.contains(" renouvellement abonnement "))return;
    }
    String source;
    if("fr.leboncoin".equals(pkg))source="Alerte Leboncoin";
    else if("com.facebook.katana".equals(pkg))source="Publication de groupe Facebook";
    else {
      String appLabel="Plateforme travaux";
      try{
        CharSequence label=getPackageManager().getApplicationLabel(
          getPackageManager().getApplicationInfo(pkg,0));
        if(label!=null)appLabel=label.toString();
      }catch(Exception ignored){}
      source="Alerte "+appLabel.substring(0,Math.min(65,appLabel.length()));
    }
    RadarStore.AddResult added=RadarStore.add(this,full,"",source);
    // Journal de diagnostic minimal : aucun texte, contact ou nom de personne.
    android.content.SharedPreferences prefs=getSharedPreferences("franceclim",MODE_PRIVATE);
    int total=prefs.getInt("notification_checked",0);
    // Métriques agrégées, aucun texte ni identifiant de personne.
    String family="fr.leboncoin".equals(pkg)?"leboncoin":
      "com.facebook.katana".equals(pkg)?"facebook":"autres";
    String prefix="source_metrics_"+family+"_";
    int examined=prefs.getInt(prefix+"examined",0);
    int accepted=prefs.getInt(prefix+"accepted",0);
    int priority=prefs.getInt(prefix+"priority",0);
    boolean isPriority=added.added&&added.classification!=null&&
      "PRIORITAIRE".equals(added.classification.status);
    prefs.edit()
      .putInt("notification_checked",Math.min(100000,total+1))
      .putLong("notification_last_at",System.currentTimeMillis())
      .putString("notification_last_source",source)
      .putString("notification_last_result",added.added?"Nouveau dossier":added.reason)
      .putInt(prefix+"examined",Math.min(100000,examined+1))
      .putInt(prefix+"accepted",Math.min(100000,accepted+(added.added?1:0)))
      .putInt(prefix+"priority",Math.min(100000,priority+(isPriority?1:0)))
      .apply();
    if(!added.added||added.classification==null)return;
    // Alerts only for explicit requests in a known location; ambiguous matches stay in the inbox.
    if(!"PRIORITAIRE".equals(added.classification.status))return;
    publishAlert(added.classification);
  }
  private static String string(CharSequence c){return c==null?"":c.toString();}
  private void publishAlert(LeadClassifier.Result r){
    try {
      if(Build.VERSION.SDK_INT>=33 &&
         checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
      NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
      if(manager==null)return;
      if(Build.VERSION.SDK_INT>=26){
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Opportunités France Clim",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Demandes explicites de particuliers, zone du 13");
        manager.createNotificationChannel(channel);
      }
      Intent open=new Intent(this,MainActivity.class);
      open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
      PendingIntent intent=PendingIntent.getActivity(this,500,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
      Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
      builder.setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("France Clim • "+r.type+" • "+r.score+"/100")
        .setContentText(r.city+" · Demande à examiner")
        .setStyle(new Notification.BigTextStyle().bigText(r.city+" · "+r.reason))
        .setAutoCancel(true).setContentIntent(intent)
        .setPriority(Notification.PRIORITY_HIGH);
      manager.notify((int)(System.currentTimeMillis()%2000000000L),builder.build());
    }catch(Exception ignored){ /* The inbox remains available even when alerts are denied. */ }
  }
}
