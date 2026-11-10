package dev.aayush.os;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.Locale;

/**
 * Module 10: Native Home Screen AppWidget (#81 McLaren F1 + Live Telemetry + French Press + Quick Deck).
 */
public class AayushDeckWidget extends AppWidgetProvider {

    public static final String ACTION_COFFEE = "dev.aayush.os.WIDGET_COFFEE";
    public static final String ACTION_NOWBAR = "dev.aayush.os.WIDGET_NOWBAR";
    public static final String ACTION_CODING = "dev.aayush.os.WIDGET_CODING";
    public static final String ACTION_REFRESH = "dev.aayush.os.WIDGET_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAllWidgets(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || intent.getAction() == null) return;
        String act = intent.getAction();

        if (ACTION_COFFEE.equals(act)) {
            if (NowBarState.timerRunning) {
                NowBarState.stopTimer();
                Toast.makeText(context, "☕ French Press timer stopped", Toast.LENGTH_SHORT).show();
            } else {
                NowBarState.startFrenchPressTimer();
                ensureNowBarRunning(context);
                Toast.makeText(context, "☕ 4:00 French Press started in Top Camera Now Bar!", Toast.LENGTH_SHORT).show();
            }
            updateAllWidgets(context);
        } else if (ACTION_NOWBAR.equals(act)) {
            Intent svc = new Intent(context, NowBarOverlayService.class);
            if (NowBarOverlayService.isRunning) {
                context.stopService(svc);
            } else {
                ensureNowBarRunning(context);
            }
            updateAllWidgets(context);
        } else if (ACTION_CODING.equals(act)) {
            SmartAutomation.activateMode(context, SmartAutomation.Mode.CODING);
            updateAllWidgets(context);
        } else if (ACTION_REFRESH.equals(act)) {
            updateAllWidgets(context);
            Toast.makeText(context, "Aayush OS Widget Telemetry Synced", Toast.LENGTH_SHORT).show();
        }
    }

    private static void ensureNowBarRunning(Context ctx) {
        if (!NowBarOverlayService.isRunning) {
            Intent svc = new Intent(ctx, NowBarOverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startService(svc);
            } else {
                ctx.startService(svc);
            }
        }
    }

    public static void updateAllWidgets(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        if (mgr == null) return;
        ComponentName cn = new ComponentName(ctx, AayushDeckWidget.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids == null || ids.length == 0) return;

        HardwareMonitor hw = new HardwareMonitor(ctx);
        HardwareMonitor.Snapshot s = hw.sample();
        F1CommandCenter.TelemetryData f1 = F1CommandCenter.loadCached(ctx);
        SmartAutomation.Mode mode = SmartAutomation.getActiveMode(ctx);

        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_aayush_deck);

            rv.setTextViewText(R.id.widget_f1_badge, "#81 PIASTRI // " + f1.raceName.toUpperCase(Locale.US));
            rv.setTextViewText(R.id.widget_mode_status, mode == SmartAutomation.Mode.NONE ? "90HZ NOMINAL" : mode.name());
            rv.setTextViewText(R.id.widget_telemetry_main, String.format(Locale.US,
                    "%s %.2fW (%d%%) • %.1f°C • RAM %d%%",
                    s.isCharging ? "⚡" : "🔋", s.powerWatts, s.batteryPct, s.batteryTempC, s.ramUsedPct));

            int remSec = NowBarState.getTimerRemainingSec();
            if (NowBarState.timerRunning && remSec > 0) {
                rv.setTextViewText(R.id.widget_sub_line, String.format(Locale.US,
                        "☕ %s (%02d:%02d) • %s", NowBarState.getFrenchPressStage(), remSec / 60, remSec % 60, f1.countdownStr));
                rv.setTextViewText(R.id.widget_btn_coffee, "⏹ STOP");
            } else {
                rv.setTextViewText(R.id.widget_sub_line, "🏎️ " + f1.countdownStr + " • " + f1.raceIstTimeStr);
                rv.setTextViewText(R.id.widget_btn_coffee, "☕ 4:00");
            }

            // Wire PendingIntents
            Intent openApp = new Intent(ctx, MainActivity.class);
            PendingIntent piOpen = PendingIntent.getActivity(ctx, 100, openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.widget_root, piOpen);

            rv.setOnClickPendingIntent(R.id.widget_btn_coffee, buildBroadcastPi(ctx, ACTION_COFFEE, 101));
            rv.setOnClickPendingIntent(R.id.widget_btn_nowbar, buildBroadcastPi(ctx, ACTION_NOWBAR, 102));
            rv.setOnClickPendingIntent(R.id.widget_btn_coding, buildBroadcastPi(ctx, ACTION_CODING, 103));
            rv.setOnClickPendingIntent(R.id.widget_btn_refresh, buildBroadcastPi(ctx, ACTION_REFRESH, 104));

            mgr.updateAppWidget(id, rv);
        }
    }

    private static PendingIntent buildBroadcastPi(Context ctx, String action, int reqCode) {
        Intent i = new Intent(ctx, AayushDeckWidget.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(ctx, reqCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
