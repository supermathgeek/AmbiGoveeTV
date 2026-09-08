package fr.ambigovee.tv;
import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;import android.os.Build;
public class BootReceiver extends BroadcastReceiver{public void onReceive(Context c,Intent i){ConfigStore.seedDefaults(c);if(!ConfigStore.isConfigured(c))return;Intent s=new Intent(c,AmbiService.class);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)c.startForegroundService(s);else c.startService(s);}}
