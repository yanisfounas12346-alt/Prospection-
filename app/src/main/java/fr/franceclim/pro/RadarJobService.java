package fr.franceclim.pro;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Android effectue la veille périodique lorsqu'une connexion est disponible.
 * La périodicité de 15 min est une préférence, pas une garantie de temps réel.
 */
public class RadarJobService extends JobService {
  public static final int JOB_ID=13013;
  public static void schedule(Context ctx) {
    JobScheduler scheduler=(JobScheduler)ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if(scheduler==null)return;
    String feeds=ctx.getSharedPreferences("franceclim",MODE_PRIVATE).getString("feeds","");
    boolean enabled=ctx.getSharedPreferences("franceclim",MODE_PRIVATE)
      .getBoolean("watch_bluesky",true) ||
      ctx.getSharedPreferences("franceclim",MODE_PRIVATE)
      .getBoolean("watch_mastodon",true) ||
      (feeds!=null&&!feeds.trim().isEmpty());
    if(!enabled){scheduler.cancel(JOB_ID);return;}
    JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(ctx,RadarJobService.class))
      .setPeriodic(15L*60L*1000L)
      .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
      .setPersisted(true)
      .build();
    scheduler.schedule(info);
  }
  @Override public boolean onStartJob(JobParameters params){
    new Thread(()->{
      boolean retry=false;
      try {
        RadarScanner.Report rss=RadarScanner.scan(getApplicationContext());
        BlueskyScanner.Report sky=BlueskyScanner.scan(getApplicationContext());
        // Horodatage et diagnostic local : aucune donnée de prospect enregistrée ici.
        getSharedPreferences("franceclim",MODE_PRIVATE).edit()
          .putLong("background_scan_completed_at",System.currentTimeMillis())
          .putString("background_scan_summary",rss.toString()+" • "+sky.toString())
          .apply();
      }catch(Exception e){
        retry=true;
        getSharedPreferences("franceclim",MODE_PRIVATE).edit()
          .putLong("background_scan_failed_at",System.currentTimeMillis())
          .putString("background_scan_error",e.getClass().getSimpleName())
          .apply();
      }finally{
        jobFinished(params,retry);
      }
    },"franceclim-radar").start();
    return true;
  }
  @Override public boolean onStopJob(JobParameters params){return true;}
}
