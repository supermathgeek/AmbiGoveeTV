package fr.ambigovee.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AmbiService extends Service {
    public static final String ACTION_AUTO="fr.ambigovee.tv.AUTO";
    public static final String ACTION_BASE="fr.ambigovee.tv.BASE";
    public static final String ACTION_STATUS="fr.ambigovee.tv.STATUS";
    public static final String ACTION_RELOAD="fr.ambigovee.tv.RELOAD";
    public static final String EXTRA_STATUS="status";
    public static final String EXTRA_SYNC="sync";
    public static final String EXTRA_GOVEE="govee";
    public static final String EXTRA_TV="tv";
    public static final String EXTRA_LIGHTS_TOTAL="lights_total";
    public static final String EXTRA_LIGHTS_ON="lights_on";
    public static final String EXTRA_LIGHTS_SYNC="lights_sync";
    public static final String EXTRA_R="r", EXTRA_G="g", EXTRA_B="b", EXTRA_BRIGHTNESS="brightness";

    private static final String CHANNEL_ID="ambigovee_service";
    private static final int NOTIFICATION_ID=2208;
    private static final long GOVEE_STATUS_PERIOD_MS=650;
    private static final long SYNC_PERIOD_MS=50;
    private static final long GOVEE_STATUS_MAX_AGE_MS=1800;

    private static final class LightRuntime {
        final GoveeConfig config;
        BaseState baseState;
        boolean baseCapturedForSession=false;
        boolean pendingRestore=false;
        boolean syncActive=false;
        boolean on=false;
        boolean reachable=false;
        boolean firstFrame=true;
        long lastStatusAt=0;
        double smoothR=0,smoothG=0,smoothB=0,smoothBrightness=1;
        int lastR=-999,lastG=-999,lastB=-999,lastBrightness=-999;
        long lastBrightnessNs=0;

        LightRuntime(GoveeConfig config){this.config=config;}
    }

    private final Object stateLock=new Object();
    private ScheduledExecutorService monitorExecutor,syncExecutor;
    private GoveeLan govee;
    private PhilipsClient philips;
    private PowerManager powerManager;
    private SharedPreferences prefs;
    private final List<LightRuntime> lights = new ArrayList<>();
    private volatile boolean running=false,autoEnabled=true,tvInteractive=true;
    private long lastSyncTickNs=0,lastUiBroadcastNs=0;
    private BroadcastReceiver screenReceiver;

    @Override public void onCreate(){
        super.onCreate();
        ConfigStore.seedDefaults(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID,buildNotification("Démarrage…"));
        prefs=getSharedPreferences("ambigovee",MODE_PRIVATE);
        autoEnabled=ConfigStore.autoEnabled(this);
        powerManager=(PowerManager)getSystemService(POWER_SERVICE);
        tvInteractive=powerManager==null||powerManager.isInteractive();
        if(!ConfigStore.isConfigured(this)){publishStatus("Configuration requise");return;}
        try{
            govee=new GoveeLan(this);
            philips=new PhilipsClient(this);
            loadLights();
        }catch(Exception e){publishStatus("Configuration réseau incomplète");return;}
        registerScreenReceiver();
        startWorkers();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_AUTO.equals(intent.getAction())){
            autoEnabled=true; ConfigStore.setAutoEnabled(this,true);
            publishStatus("AUTO activé — allume les lampes voulues");
        } else if(intent!=null&&ACTION_BASE.equals(intent.getAction())){
            autoEnabled=false; ConfigStore.setAutoEnabled(this,false);
            if(monitorExecutor!=null)monitorExecutor.execute(this::switchToBaseMode);
        } else if(intent!=null&&ACTION_RELOAD.equals(intent.getAction())){
            if(monitorExecutor!=null)monitorExecutor.execute(this::restartClients);
            else restartClients();
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent){return null;}

    private void loadLights(){
        synchronized(stateLock){
            lights.clear();
            for(GoveeConfig cfg:ConfigStore.goveeLights(this)){
                if(!cfg.enabled)continue;
                LightRuntime n=new LightRuntime(cfg);
                loadNodeState(n, lights.isEmpty());
                lights.add(n);
            }
        }
    }

    private void restartClients(){
        try{
            restoreAllOnLights();
            if(govee!=null)govee.close();
            govee=new GoveeLan(this);
            philips=new PhilipsClient(this);
            loadLights();
            publishStatus("Appareils mis à jour");
        }catch(Exception e){publishStatus("Impossible d'appliquer les réglages");}
    }

    private void startWorkers(){
        if(running)return;
        running=true;
        monitorExecutor=Executors.newSingleThreadScheduledExecutor();
        syncExecutor=Executors.newSingleThreadScheduledExecutor();
        monitorExecutor.scheduleWithFixedDelay(this::monitorTick,0,GOVEE_STATUS_PERIOD_MS,TimeUnit.MILLISECONDS);
        syncExecutor.scheduleWithFixedDelay(this::syncTick,100,SYNC_PERIOD_MS,TimeUnit.MILLISECONDS);
    }

    private void monitorTick(){
        if(!running||govee==null)return;
        boolean interactive=powerManager==null||powerManager.isInteractive();
        if(tvInteractive&&!interactive){tvInteractive=false;handleTvOff();}
        else if(!tvInteractive&&interactive){
            tvInteractive=true;
            synchronized(stateLock){for(LightRuntime n:lights)n.baseCapturedForSession=false;}
            publishStatus(autoEnabled?"TV réveillée — en attente des lampes":"Lumière normale");
        } else tvInteractive=interactive;

        int total=0,onCount=0,syncCount=0,reachableCount=0;
        synchronized(stateLock){
            for(LightRuntime n:lights){
                total++;
                GoveeLan.GoveeState status=govee.queryStatus(n.config);
                n.reachable=status!=null;
                if(status==null){n.on=false;continue;}
                reachableCount++;
                n.lastStatusAt=System.currentTimeMillis();
                n.on=status.on;
                if(status.on)onCount++;

                if(!tvInteractive){
                    if(status.on && n.baseState!=null && (n.pendingRestore||n.syncActive||wasSyncActive(n))){ restoreNode(n,status); }
                    else if(!status.on && n.baseState!=null && (n.syncActive||wasSyncActive(n))){n.pendingRestore=true;n.syncActive=false;n.baseCapturedForSession=false;persistNode(n);}
                    continue;
                }

                if(!autoEnabled){
                    if(status.on && n.baseState!=null && (n.pendingRestore||n.syncActive)) restoreNode(n,status);
                    n.syncActive=false;
                    continue;
                }

                if(!status.on){
                    if(n.syncActive){n.syncActive=false;n.firstFrame=true;setWasSyncActive(n,false);}
                    continue;
                }

                if(n.pendingRestore&&n.baseState!=null){
                    restoreNode(n,status);
                    try{Thread.sleep(25);}catch(InterruptedException ignored){}
                    GoveeLan.GoveeState refreshed=govee.queryStatus(n.config);
                    if(refreshed!=null)status=refreshed;
                }

                if(!n.syncActive){
                    if(!n.baseCapturedForSession){
                        n.baseState=BaseState.fromGovee(status);
                        n.baseCapturedForSession=true;
                        n.pendingRestore=false;
                        persistNode(n);
                    }
                    n.syncActive=true;n.firstFrame=true;setWasSyncActive(n,true);
                }
                if(n.syncActive)syncCount++;
            }
        }

        if(!tvInteractive)publishStatus("TV en veille — lumière normale");
        else if(!autoEnabled)publishStatus(onCount>0?"Lumière normale":"Lumière normale — lampes éteintes");
        else if(total==0)publishStatus("Ajoute une lampe Govee");
        else if(reachableCount==0)publishStatus("Govee introuvable — vérifie Contrôle LAN");
        else if(syncCount>0)publishStatus("SYNCHRO EN DIRECT — "+syncCount+" lampe"+(syncCount>1?"s":""));
        else publishStatus("Prêt — allume une lampe configurée");
    }

    private void syncTick(){
        if(!running||!autoEnabled||!tvInteractive||philips==null||govee==null)return;
        boolean any=false;
        synchronized(stateLock){for(LightRuntime n:lights)if(n.syncActive&&n.on&&System.currentTimeMillis()-n.lastStatusAt<GOVEE_STATUS_MAX_AGE_MS){any=true;break;}}
        if(!any)return;

        try{
            JSONObject measured=philips.getMeasured();
            String profile=ConfigStore.profile(this);
            double tauSmall,tauNormal,tauCut,tauBrightness;
            if(ConfigStore.PROFILE_CINEMA.equals(profile)){tauSmall=.150;tauNormal=.080;tauCut=.035;tauBrightness=.110;}
            else if(ConfigStore.PROFILE_DOUX.equals(profile)){tauSmall=.240;tauNormal=.130;tauCut=.060;tauBrightness=.180;}
            else{tauSmall=.075;tauNormal=.035;tauCut=.012;tauBrightness=.045;}

            long now=System.nanoTime();
            double dt=lastSyncTickNs==0?.05:(now-lastSyncTickNs)/1_000_000_000.0;
            lastSyncTickNs=now;

            int previewR=0,previewG=0,previewB=0,previewBrightness=1;
            boolean previewSet=false;

            synchronized(stateLock){
                for(LightRuntime n:lights){
                    if(!n.syncActive||!n.on||System.currentTimeMillis()-n.lastStatusAt>=GOVEE_STATUS_MAX_AGE_MS)continue;
                    ColorEngine.Target target=ColorEngine.fromMeasured(this,measured,n.config.position);
                    if(n.firstFrame){n.smoothR=target.r;n.smoothG=target.g;n.smoothB=target.b;n.smoothBrightness=target.brightness;n.firstFrame=false;}
                    else{
                        double delta=ColorEngine.rgbDelta(n.smoothR,n.smoothG,n.smoothB,target.r,target.g,target.b);
                        double tau=delta>=100?tauCut:(delta>=32?tauNormal:tauSmall);
                        double a=ColorEngine.alpha(dt,tau);
                        n.smoothR+=(target.r-n.smoothR)*a;n.smoothG+=(target.g-n.smoothG)*a;n.smoothB+=(target.b-n.smoothB)*a;
                        double ab=ColorEngine.alpha(dt,tauBrightness);n.smoothBrightness+=(target.brightness-n.smoothBrightness)*ab;
                    }
                    int r=clamp((int)Math.round(n.smoothR),0,255),gg=clamp((int)Math.round(n.smoothG),0,255),b=clamp((int)Math.round(n.smoothB),0,255);
                    int brightness=clamp((int)Math.round(n.smoothBrightness),1,ConfigStore.maxBrightness(this));
                    int colorDelta=Math.abs(r-n.lastR)+Math.abs(gg-n.lastG)+Math.abs(b-n.lastB);
                    if(colorDelta>=2){govee.setColor(n.config,r,gg,b);n.lastR=r;n.lastG=gg;n.lastB=b;}
                    if(Math.abs(brightness-n.lastBrightness)>=1&&now-n.lastBrightnessNs>=250_000_000L){govee.setBrightness(n.config,brightness);n.lastBrightness=brightness;n.lastBrightnessNs=now;}
                    if(!previewSet||GoveeConfig.POSITION_ROOM.equals(n.config.position)){previewR=r;previewG=gg;previewB=b;previewBrightness=brightness;previewSet=true;}
                }
            }
            if(previewSet&&now-lastUiBroadcastNs>=250_000_000L){publishColorFrame(previewR,previewG,previewB,previewBrightness);lastUiBroadcastNs=now;}
        }catch(Exception ignored){}
    }

    private void handleTvOff(){
        PowerManager.WakeLock wl=null;
        try{
            if(powerManager!=null){wl=powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AmbiGovee:restore");wl.acquire(5000);}
            synchronized(stateLock){
                for(LightRuntime n:lights){
                    n.baseCapturedForSession=false;n.firstFrame=true;
                    GoveeLan.GoveeState status=govee!=null?govee.queryStatus(n.config):null;
                    if(status!=null&&status.on&&n.baseState!=null)restoreNode(n,status);
                    else if(n.baseState!=null){n.pendingRestore=true;n.syncActive=false;persistNode(n);}
                }
            }
            publishStatus("TV en veille — lumières normales");
        } finally {if(wl!=null&&wl.isHeld())wl.release();}
    }

    private void switchToBaseMode(){
        synchronized(stateLock){
            for(LightRuntime n:lights){
                GoveeLan.GoveeState status=govee!=null?govee.queryStatus(n.config):null;
                if(n.baseState!=null&&status!=null&&status.on)restoreNode(n,status);
                else if(n.baseState!=null){n.pendingRestore=true;persistNode(n);}
                n.syncActive=false;n.firstFrame=true;n.baseCapturedForSession=false;setWasSyncActive(n,false);
            }
        }
        publishStatus("Lumière normale");
    }

    private void restoreAllOnLights(){
        synchronized(stateLock){
            for(LightRuntime n:lights){
                try{
                    GoveeLan.GoveeState s=govee!=null?govee.queryStatus(n.config):null;
                    if(s!=null&&s.on&&n.baseState!=null)restoreNode(n,s);
                }catch(Exception ignored){}
            }
        }
    }

    private void restoreNode(LightRuntime n,GoveeLan.GoveeState current){
        if(n.baseState==null||current==null||!current.on||govee==null)return;
        govee.restore(n.config,n.baseState);
        n.pendingRestore=false;n.syncActive=false;n.firstFrame=true;
        setWasSyncActive(n,false);persistNode(n);
    }

    private String prefKey(LightRuntime n,String suffix){return "l_"+Integer.toHexString(n.config.identity().hashCode())+"_"+suffix;}
    private boolean wasSyncActive(LightRuntime n){return prefs.getBoolean(prefKey(n,"sync"),false);}
    private void setWasSyncActive(LightRuntime n,boolean v){prefs.edit().putBoolean(prefKey(n,"sync"),v).apply();}

    private void loadNodeState(LightRuntime n, boolean firstLight){
        n.pendingRestore=prefs.getBoolean(prefKey(n,"pending"),false);
        if(prefs.contains(prefKey(n,"brightness"))){
            n.baseState=new BaseState(
                    prefs.getInt(prefKey(n,"brightness"),100),prefs.getInt(prefKey(n,"r"),255),prefs.getInt(prefKey(n,"g"),255),prefs.getInt(prefKey(n,"b"),255),prefs.getInt(prefKey(n,"kelvin"),0));
        } else if(firstLight && prefs.contains("base_brightness")) {
            // Migration v1.3 -> v1.4 : conserve la lumière normale de l'ancien mode mono-lampe.
            n.baseState=new BaseState(prefs.getInt("base_brightness",100),prefs.getInt("base_r",255),prefs.getInt("base_g",255),prefs.getInt("base_b",255),prefs.getInt("base_kelvin",0));
            n.pendingRestore = n.pendingRestore || prefs.getBoolean("pending_restore",false) || prefs.getBoolean("sync_was_active",false);
            persistNode(n);
        }
        if(wasSyncActive(n)&&n.baseState!=null)n.pendingRestore=true;
    }

    private void persistNode(LightRuntime n){
        SharedPreferences.Editor e=prefs.edit().putBoolean(prefKey(n,"pending"),n.pendingRestore);
        if(n.baseState!=null)e.putInt(prefKey(n,"brightness"),n.baseState.brightness).putInt(prefKey(n,"r"),n.baseState.r).putInt(prefKey(n,"g"),n.baseState.g).putInt(prefKey(n,"b"),n.baseState.b).putInt(prefKey(n,"kelvin"),n.baseState.kelvin);
        e.apply();
    }

    private void registerScreenReceiver(){
        screenReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){String a=i.getAction();if(Intent.ACTION_SCREEN_OFF.equals(a)){tvInteractive=false;if(monitorExecutor!=null)monitorExecutor.execute(AmbiService.this::handleTvOff);}else if(Intent.ACTION_SCREEN_ON.equals(a))tvInteractive=true;}};
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);registerReceiver(screenReceiver,f);
    }

    private int countOn(){int c=0;for(LightRuntime n:lights)if(n.on)c++;return c;}
    private int countSync(){int c=0;for(LightRuntime n:lights)if(n.syncActive&&n.on)c++;return c;}
    private boolean anyGovee(){for(LightRuntime n:lights)if(n.reachable)return true;return false;}

    private void publishStatus(String text){
        NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(m!=null)m.notify(NOTIFICATION_ID,buildNotification(text));
        Intent u=new Intent(ACTION_STATUS);u.setPackage(getPackageName());u.putExtra(EXTRA_STATUS,text);u.putExtra(EXTRA_SYNC,countSync()>0);u.putExtra(EXTRA_GOVEE,anyGovee());u.putExtra(EXTRA_TV,tvInteractive);u.putExtra(EXTRA_LIGHTS_TOTAL,lights.size());u.putExtra(EXTRA_LIGHTS_ON,countOn());u.putExtra(EXTRA_LIGHTS_SYNC,countSync());sendBroadcast(u);
    }

    private void publishColorFrame(int r,int g,int b,int brightness){
        Intent u=new Intent(ACTION_STATUS);u.setPackage(getPackageName());u.putExtra(EXTRA_STATUS,countSync()>0?"SYNCHRO EN DIRECT":"AmbiGovee actif");u.putExtra(EXTRA_SYNC,countSync()>0);u.putExtra(EXTRA_GOVEE,anyGovee());u.putExtra(EXTRA_TV,tvInteractive);u.putExtra(EXTRA_LIGHTS_TOTAL,lights.size());u.putExtra(EXTRA_LIGHTS_ON,countOn());u.putExtra(EXTRA_LIGHTS_SYNC,countSync());u.putExtra(EXTRA_R,r);u.putExtra(EXTRA_G,g);u.putExtra(EXTRA_B,b);u.putExtra(EXTRA_BRIGHTNESS,brightness);sendBroadcast(u);
    }

    private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel c=new NotificationChannel(CHANNEL_ID,"AmbiGovee",NotificationManager.IMPORTANCE_LOW);c.setDescription("Synchronisation locale Philips Ambilight vers Govee");NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(m!=null)m.createNotificationChannel(c);}}
    private Notification buildNotification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent p=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);return b.setContentTitle("AmbiGovee").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_slideshow).setOngoing(true).setContentIntent(p).build();}

    @Override public void onDestroy(){
        running=false;
        try{restoreAllOnLights();}catch(Exception ignored){}
        try{if(screenReceiver!=null)unregisterReceiver(screenReceiver);}catch(Exception ignored){}
        if(monitorExecutor!=null)monitorExecutor.shutdownNow();if(syncExecutor!=null)syncExecutor.shutdownNow();if(govee!=null)govee.close();super.onDestroy();
    }

    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
