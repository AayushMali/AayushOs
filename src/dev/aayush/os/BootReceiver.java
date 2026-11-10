package dev.aayush.os;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

/**
 * Persists and auto-starts the Dynamic Now Bar V2 across device reboots and package updates.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        SharedPreferences prefs = context.getSharedPreferences("aayush_os_prefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("mod_nowbar", true);
        if (enabled && Settings.canDrawOverlays(context) && !NowBarOverlayService.isRunning) {
            Intent svc = new Intent(context, NowBarOverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        }
    }
}
