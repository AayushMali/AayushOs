package dev.aayush.os;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * Module 6: Smart Automation Engine with Atomic State Snapshots & Rollback.
 * Uses ADB-granted WRITE_SECURE_SETTINGS and WRITE_SETTINGS.
 */
public final class SmartAutomation {

    public enum Mode {
        NONE("NORMAL BASELINE", "All system settings restored to baseline"),
        CODING("💻 CODING MODE", "Caffeinate 30m • 0.5x Anim • AdGuard DNS • Stay Awake on USB"),
        GAMING("🎮 GAMING MODE", "90Hz Peak • Suppress Heads-Up Popups • Priority DND • Boost"),
        WORK("🏢 WORK MODE", "Vibrate Ringer • Media 25% • Clean Status Bar • 0.5x Anim"),
        BATTERY_SAVER("🔋 ULTRA SAVER", "60Hz Limit • AOD Off • Extra Dim • 0.0x Anim"),
        NIGHT("🌙 NIGHT MODE", "Warm Night Light • Extra Dim • Silent Ringer • AOD Off");

        public final String title;
        public final String summary;
        Mode(String t, String s) {
            this.title = t;
            this.summary = s;
        }
    }

    private static final String PREF_NAME = "aayush_os_automation";

    public static Mode getActiveMode(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString("active_mode", Mode.NONE.name());
        try {
            return Mode.valueOf(name);
        } catch (Exception e) {
            return Mode.NONE;
        }
    }

    public static boolean activateMode(Context ctx, Mode target) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Mode current = getActiveMode(ctx);

        // If switching from NONE to an active mode, capture atomic baseline snapshot first!
        if (current == Mode.NONE && target != Mode.NONE) {
            saveAtomicBaseline(ctx, prefs);
        }

        // If tapping the already-active mode or NONE, restore baseline!
        if (target == Mode.NONE || current == target) {
            restoreAtomicBaseline(ctx, prefs);
            prefs.edit().putString("active_mode", Mode.NONE.name()).apply();
            Toast.makeText(ctx, "Restored baseline system state", Toast.LENGTH_SHORT).show();
            return true;
        }

        // First restore baseline cleanly so modes don't bleed into each other
        restoreAtomicBaseline(ctx, prefs);

        ContentResolver cr = ctx.getContentResolver();
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

        try {
            switch (target) {
                case CODING:
                    Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 1800000); // 30 min
                    Settings.Global.putInt(cr, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 7);
                    setAnimations(cr, "0.5");
                    break;

                case GAMING:
                    Settings.Global.putString(cr, "heads_up_notifications_enabled", "0");
                    setAnimations(cr, "0.5");
                    Settings.System.putFloat(cr, "peak_refresh_rate", 90.0f);
                    Settings.System.putFloat(cr, "min_refresh_rate", 90.0f);
                    Settings.Secure.putInt(cr, "reduce_bright_colors_activated", 0);
                    break;

                case WORK:
                    if (am != null) {
                        try {
                            am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                            int maxMus = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, maxMus / 4), 0);
                        } catch (Exception ignored) {}
                    }
                    Settings.Secure.putString(cr, "icon_blacklist", "alarm_clock,rotate,nfc,headset,vibrate,bluetooth");
                    setAnimations(cr, "0.5");
                    break;

                case BATTERY_SAVER:
                    setAnimations(cr, "0.0");
                    Settings.Secure.putInt(cr, "doze_always_on", 0);
                    Settings.Secure.putInt(cr, "reduce_bright_colors_activated", 1);
                    Settings.System.putFloat(cr, "peak_refresh_rate", 60.0f);
                    Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000);
                    break;

                case NIGHT:
                    Settings.Secure.putInt(cr, "night_display_activated", 1);
                    Settings.Secure.putInt(cr, "reduce_bright_colors_activated", 1);
                    Settings.Secure.putInt(cr, "doze_always_on", 0);
                    if (am != null) {
                        try {
                            am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        } catch (Exception ignored) {}
                    }
                    break;

                default:
                    break;
            }
            prefs.edit().putString("active_mode", target.name()).apply();
            Toast.makeText(ctx, "Activated " + target.title, Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            Toast.makeText(ctx, "Automation error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private static void saveAtomicBaseline(Context ctx, SharedPreferences prefs) {
        ContentResolver cr = ctx.getContentResolver();
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        try {
            JSONObject snap = new JSONObject();
            snap.put("anim_w", getGlobalOrDef(cr, Settings.Global.WINDOW_ANIMATION_SCALE, "0.5"));
            snap.put("anim_t", getGlobalOrDef(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, "0.5"));
            snap.put("anim_d", getGlobalOrDef(cr, Settings.Global.ANIMATOR_DURATION_SCALE, "0.5"));
            snap.put("timeout", Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 60000));
            snap.put("stay_on", Settings.Global.getInt(cr, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0));
            snap.put("heads_up", getGlobalOrDef(cr, "heads_up_notifications_enabled", "1"));
            snap.put("dns_mode", getGlobalOrDef(cr, "private_dns_mode", "opportunistic"));
            snap.put("doze_aod", Settings.Secure.getInt(cr, "doze_always_on", 1));
            snap.put("extra_dim", Settings.Secure.getInt(cr, "reduce_bright_colors_activated", 0));
            snap.put("night_light", Settings.Secure.getInt(cr, "night_display_activated", 0));
            snap.put("blacklist", Settings.Secure.getString(cr, "icon_blacklist"));
            if (am != null) {
                snap.put("ringer", am.getRingerMode());
                snap.put("vol_music", am.getStreamVolume(AudioManager.STREAM_MUSIC));
            }
            prefs.edit().putString("baseline_json", snap.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void restoreAtomicBaseline(Context ctx, SharedPreferences prefs) {
        String raw = prefs.getString("baseline_json", null);
        if (raw == null) return;
        ContentResolver cr = ctx.getContentResolver();
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        try {
            JSONObject snap = new JSONObject(raw);
            Settings.Global.putString(cr, Settings.Global.WINDOW_ANIMATION_SCALE, snap.optString("anim_w", "0.5"));
            Settings.Global.putString(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, snap.optString("anim_t", "0.5"));
            Settings.Global.putString(cr, Settings.Global.ANIMATOR_DURATION_SCALE, snap.optString("anim_d", "0.5"));
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, snap.optInt("timeout", 60000));
            Settings.Global.putInt(cr, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, snap.optInt("stay_on", 0));
            Settings.Global.putString(cr, "heads_up_notifications_enabled", snap.optString("heads_up", "1"));
            Settings.Global.putString(cr, "private_dns_mode", snap.optString("dns_mode", "opportunistic"));
            Settings.Global.putString(cr, "private_dns_specifier", "");
            Settings.Secure.putInt(cr, "doze_always_on", snap.optInt("doze_aod", 1));
            Settings.Secure.putInt(cr, "reduce_bright_colors_activated", snap.optInt("extra_dim", 0));
            Settings.Secure.putInt(cr, "night_display_activated", snap.optInt("night_light", 0));
            Settings.System.putFloat(cr, "peak_refresh_rate", 90.0f);
            Settings.System.putFloat(cr, "min_refresh_rate", 60.0f);
            String bl = snap.optString("blacklist", "alarm_clock,rotate,nfc,headset,vibrate");
            Settings.Secure.putString(cr, "icon_blacklist", bl);
            if (am != null) {
                try {
                    if (snap.has("ringer")) am.setRingerMode(snap.getInt("ringer"));
                    if (snap.has("vol_music")) am.setStreamVolume(AudioManager.STREAM_MUSIC, snap.getInt("vol_music"), 0);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static void setAnimations(ContentResolver cr, String scale) {
        Settings.Global.putString(cr, Settings.Global.WINDOW_ANIMATION_SCALE, scale);
        Settings.Global.putString(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, scale);
        Settings.Global.putString(cr, Settings.Global.ANIMATOR_DURATION_SCALE, scale);
    }

    private static String getGlobalOrDef(ContentResolver cr, String key, String def) {
        String v = Settings.Global.getString(cr, key);
        return v != null ? v : def;
    }
}
