package fr.franceclim.pro;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Découvre uniquement les applications de travaux installées et visibles.
 * La lecture des alertes requiert l'autorisation explicite Android.
 * Ni messagerie personnelle, ni e-mail, ni réseau social généraliste ne
 * sont automatiquement ajoutés.
 */
public final class PlatformSources {
  private PlatformSources(){}
  public static boolean recognized(String label) {
    String n=LeadClassifier.normalize(label);
    return n.contains("allovoisins")||n.contains("allo voisins")||
      n.contains("travaux com")||n.equals("travaux")||
      n.contains("needhelp")||n.contains("need help")||
      n.contains("frizbiz")||n.contains("mesdepanneurs")||
      n.contains("starofservice")||n.contains("star of service")||
      n.contains("bricoleur");
  }
  public static int activateInstalled(Context context) {
    SharedPreferences prefs=context.getSharedPreferences("franceclim",Context.MODE_PRIVATE);
    Set<String> enabled=new LinkedHashSet<>(prefs.getStringSet(
      "watched_marketplace_packages",Collections.<String>emptySet()));
    PackageManager pm=context.getPackageManager();
    Intent launcher=new Intent(Intent.ACTION_MAIN);
    launcher.addCategory(Intent.CATEGORY_LAUNCHER);
    boolean leboncoin=false;
    int found=0;
    try{
      for(ResolveInfo entry:pm.queryIntentActivities(launcher,0)){
        if(entry.activityInfo==null)continue;
        String pkg=entry.activityInfo.packageName;
        if("fr.leboncoin".equals(pkg)){leboncoin=true;found++;continue;}
        if(pkg.equals(context.getPackageName()))continue;
        CharSequence title=entry.loadLabel(pm);
        if(title!=null&&recognized(title.toString())){
          enabled.add(pkg);
          found++;
        }
      }
    }catch(RuntimeException ignored){}
    // Leboncoin uniquement si son application est effectivement détectée.
    // Facebook et les conversations privées ne sont jamais activés implicitement.
    prefs.edit()
      .putStringSet("watched_marketplace_packages",enabled)
      .putBoolean("watch_lbc",leboncoin)
      .putBoolean("watch_fb",false)
      .putInt("auto_platforms_found",found)
      .putLong("auto_platforms_last_check",System.currentTimeMillis())
      .apply();
    return found;
  }
}
