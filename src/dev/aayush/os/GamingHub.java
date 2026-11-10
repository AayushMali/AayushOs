package dev.aayush.os;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Module 8: Gaming Hub for Pixel 7 ('panther').
 * Auto-discovers installed games, launches Steam / Xbox Cloud Gaming,
 * and logs gaming session thermal & battery telemetry.
 */
public final class GamingHub {

    public static final class GameEntry {
        public final String label;
        public final String packageName;
        public GameEntry(String l, String p) {
            this.label = l;
            this.packageName = p;
        }
    }

    private static final String PREF_NAME = "aayush_os_gaming";

    public static List<GameEntry> discoverGames(Context ctx) {
        List<GameEntry> out = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        if (pm == null) return out;

        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            boolean isGame = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ai.category == ApplicationInfo.CATEGORY_GAME) {
                isGame = true;
            }
            String pkg = ai.packageName;
            if (pkg.contains("riotgames") || pkg.contains("gamesoup") || pkg.contains("valvesoftware")
                    || pkg.contains("dream11") || pkg.contains("xbox") || pkg.contains("game")) {
                isGame = true;
            }
            if (isGame && pm.getLaunchIntentForPackage(pkg) != null) {
                CharSequence lbl = pm.getApplicationLabel(ai);
                out.add(new GameEntry(lbl != null ? lbl.toString() : pkg, pkg));
            }
        }
        return out;
    }

    public static void launchGameWithSession(Context ctx, String pkgName, String label, HardwareMonitor hw) {
        startSessionLog(ctx, label, hw);
        SmartAutomation.activateMode(ctx, SmartAutomation.Mode.GAMING);
        PackageManager pm = ctx.getPackageManager();
        if (pm != null) {
            Intent launch = pm.getLaunchIntentForPackage(pkgName);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(launch);
                return;
            }
        }
        Toast.makeText(ctx, "Could not launch " + label, Toast.LENGTH_SHORT).show();
    }

    public static void launchXboxCloud(Context ctx, HardwareMonitor hw) {
        startSessionLog(ctx, "Xbox Cloud Gaming", hw);
        SmartAutomation.activateMode(ctx, SmartAutomation.Mode.GAMING);
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.xbox.com/play"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    public static void launchSteam(Context ctx, HardwareMonitor hw) {
        PackageManager pm = ctx.getPackageManager();
        if (pm != null) {
            Intent steam = pm.getLaunchIntentForPackage("com.valvesoftware.android.steam.community");
            if (steam != null) {
                launchGameWithSession(ctx, "com.valvesoftware.android.steam.community", "Steam", hw);
                return;
            }
        }
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com"));
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(web);
    }

    public static void startSessionLog(Context ctx, String gameTitle, HardwareMonitor hw) {
        HardwareMonitor.Snapshot s = hw.sample();
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("session_active", true)
                .putString("session_game", gameTitle)
                .putLong("session_start_ms", System.currentTimeMillis())
                .putInt("session_start_batt", s.batteryPct)
                .putFloat("session_peak_temp", (float) s.batteryTempC)
                .apply();
    }

    public static String endOrFormatSessionSummary(Context ctx, HardwareMonitor hw, boolean finishSession) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean active = prefs.getBoolean("session_active", false);
        String game = prefs.getString("session_game", "None");
        long startMs = prefs.getLong("session_start_ms", 0L);
        int startBatt = prefs.getInt("session_start_batt", 0);
        float peakTemp = prefs.getFloat("session_peak_temp", 0f);

        HardwareMonitor.Snapshot cur = hw.sample();
        if (cur.batteryTempC > peakTemp) {
            peakTemp = (float) cur.batteryTempC;
            if (active) {
                prefs.edit().putFloat("session_peak_temp", peakTemp).apply();
            }
        }

        if (!active && startMs == 0L) {
            return "LAST SESSION: No recorded session yet • Tap any game or Start Session to log thermals.";
        }

        long durMin = Math.max(1L, (System.currentTimeMillis() - startMs) / 60000L);
        int battDrop = Math.max(0, startBatt - cur.batteryPct);

        if (finishSession && active) {
            prefs.edit().putBoolean("session_active", false).apply();
            SmartAutomation.activateMode(ctx, SmartAutomation.Mode.NONE);
        }

        return String.format(Locale.US,
                "%s SESSION [%s] : %d min | ΔBATT: -%d%% (%d%%→%d%%) | PEAK TEMP: %.1f°C",
                active && !finishSession ? "ACTIVE" : "LAST",
                game, durMin, battDrop, startBatt, cur.batteryPct, peakTemp
        );
    }
}
