package dev.aayush.os;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * Module 11: Hidden Developer Easter Egg — "Panther #81 Engineering Vault".
 * Unlocked by triple-tapping the #81 McLaren telemetry crest.
 */
public final class VaultDialog {

    public static void show(final Context ctx) {
        final Dialog dialog = new Dialog(ctx, android.R.style.Theme_DeviceDefault_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG_OBSIDIAN);
        int pad = Theme.dp(ctx, 18);
        root.setPadding(pad, pad, pad, pad);

        // Top Header
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(ctx);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleCol.setLayoutParams(titleLp);

        TextView badge = new TextView(ctx);
        badge.setText("SECRET DIAGNOSTICS // PANTHER VAULT #81");
        badge.setTextColor(Theme.PAPAYA);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setTypeface(Theme.MONO_BOLD);
        titleCol.addView(badge);

        TextView title = new TextView(ctx);
        title.setText("Engineering & Kernel Telemetry");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Theme.SANS_BOLD);
        titleCol.addView(title);

        TextView closeBtn = Theme.createPill(ctx, "CLOSE [X]", Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.PAPAYA);
        header.addView(titleCol);
        header.addView(closeBtn);
        root.addView(header);
        root.addView(Theme.createDivider(ctx));

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. Live Sensor Oscilloscope Card
        LinearLayout sensorCard = Theme.createCard(ctx, true);
        sensorCard.addView(Theme.createSectionLabel(ctx, "01 // LIVE TENSOR G2 SENSOR OSCILLOSCOPE"));
        final TextView sensorReadout = new TextView(ctx);
        sensorReadout.setTypeface(Theme.MONO);
        sensorReadout.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sensorReadout.setTextColor(Theme.TELEMETRY_GREEN);
        sensorReadout.setText("Initializing hardware sensors...");
        sensorCard.addView(sensorReadout);
        content.addView(sensorCard);

        final SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        final float[] accel = new float[3];
        final float[] gyro = new float[3];
        final float[] mag = new float[3];
        final float[] luxBaro = new float[2];

        final SensorEventListener sensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                int t = event.sensor.getType();
                if (t == Sensor.TYPE_ACCELEROMETER) System.arraycopy(event.values, 0, accel, 0, 3);
                else if (t == Sensor.TYPE_GYROSCOPE) System.arraycopy(event.values, 0, gyro, 0, 3);
                else if (t == Sensor.TYPE_MAGNETIC_FIELD) System.arraycopy(event.values, 0, mag, 0, 3);
                else if (t == Sensor.TYPE_LIGHT) luxBaro[0] = event.values[0];
                else if (t == Sensor.TYPE_PRESSURE) luxBaro[1] = event.values[0];

                double gForce = Math.sqrt(accel[0]*accel[0] + accel[1]*accel[1] + accel[2]*accel[2]) / 9.81;
                sensorReadout.setText(String.format(Locale.US,
                        "ACCEL (m/s²) : X=%+6.2f  Y=%+6.2f  Z=%+6.2f  [%.2fG]\n" +
                        "GYRO (rad/s) : X=%+6.2f  Y=%+6.2f  Z=%+6.2f\n" +
                        "MAGNET (µT)  : X=%+6.1f  Y=%+6.1f  Z=%+6.1f\n" +
                        "OPTICAL/BARO : %.0f lux  |  %.1f hPa\n" +
                        "KERNEL       : %s (%s)",
                        accel[0], accel[1], accel[2], gForce,
                        gyro[0], gyro[1], gyro[2],
                        mag[0], mag[1], mag[2],
                        luxBaro[0], luxBaro[1],
                        System.getProperty("os.version", "unknown"), Build.HARDWARE
                ));
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        if (sm != null) {
            registerSensorSafe(sm, sensorListener, Sensor.TYPE_ACCELEROMETER);
            registerSensorSafe(sm, sensorListener, Sensor.TYPE_GYROSCOPE);
            registerSensorSafe(sm, sensorListener, Sensor.TYPE_MAGNETIC_FIELD);
            registerSensorSafe(sm, sensorListener, Sensor.TYPE_LIGHT);
            registerSensorSafe(sm, sensorListener, Sensor.TYPE_PRESSURE);
        }

        // 2. Hidden Android System Intents Launcher
        LinearLayout intentsCard = Theme.createCard(ctx, false);
        intentsCard.addView(Theme.createSectionLabel(ctx, "02 // HIDDEN ANDROID SYSTEM ACTIVITIES"));

        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        addIntentButton(ctx, btnRow, "5G / Radio Info (*#*#4636#*#*)", () -> {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setComponent(new ComponentName("com.android.phone", "com.android.phone.settings.RadioInfo"));
                ctx.startActivity(i);
            } catch (Exception e) {
                Toast.makeText(ctx, "Fallback to Network Settings", Toast.LENGTH_SHORT).show();
                ctx.startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            }
        });

        addIntentButton(ctx, btnRow, "Notification History", () -> {
            try {
                Intent i = new Intent("android.settings.NOTIFICATION_HISTORY");
                ctx.startActivity(i);
            } catch (Exception e) {
                ctx.startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        addIntentButton(ctx, btnRow, "Developer Options", () -> {
            try {
                ctx.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Exception ignored) {}
        });

        addIntentButton(ctx, btnRow, "Battery Usage", () -> {
            try {
                ctx.startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
            } catch (Exception ignored) {}
        });

        hsv.addView(btnRow);
        intentsCard.addView(hsv);
        content.addView(intentsCard);

        // 3. Raw Settings.Secure / Settings.Global Live Mutator (WRITE_SECURE_SETTINGS)
        LinearLayout settingsCard = Theme.createCard(ctx, false);
        settingsCard.addView(Theme.createSectionLabel(ctx, "03 // WRITE_SECURE_SETTINGS LIVE INSPECTOR"));

        final TextView settingsDump = new TextView(ctx);
        settingsDump.setTypeface(Theme.MONO);
        settingsDump.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        settingsDump.setTextColor(Theme.TEXT_SECONDARY);
        refreshSettingsDump(ctx, settingsDump);
        settingsCard.addView(settingsDump);

        LinearLayout quickMutateRow = new LinearLayout(ctx);
        quickMutateRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams qmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qmLp.topMargin = Theme.dp(ctx, 8);
        quickMutateRow.setLayoutParams(qmLp);

        addIntentButton(ctx, quickMutateRow, "Anim 0.5x (Fast)", () -> {
            putGlobalSafe(ctx, Settings.Global.WINDOW_ANIMATION_SCALE, "0.5");
            putGlobalSafe(ctx, Settings.Global.TRANSITION_ANIMATION_SCALE, "0.5");
            putGlobalSafe(ctx, Settings.Global.ANIMATOR_DURATION_SCALE, "0.5");
            refreshSettingsDump(ctx, settingsDump);
        });
        addIntentButton(ctx, quickMutateRow, "Anim 1.0x (Stock)", () -> {
            putGlobalSafe(ctx, Settings.Global.WINDOW_ANIMATION_SCALE, "1.0");
            putGlobalSafe(ctx, Settings.Global.TRANSITION_ANIMATION_SCALE, "1.0");
            putGlobalSafe(ctx, Settings.Global.ANIMATOR_DURATION_SCALE, "1.0");
            refreshSettingsDump(ctx, settingsDump);
        });
        addIntentButton(ctx, quickMutateRow, "Toggle Heads-Up", () -> {
            String cur = Settings.Global.getString(ctx.getContentResolver(), "heads_up_notifications_enabled");
            String next = "0".equals(cur) ? "1" : "0";
            putGlobalSafe(ctx, "heads_up_notifications_enabled", next);
            refreshSettingsDump(ctx, settingsDump);
        });

        settingsCard.addView(quickMutateRow);
        content.addView(settingsCard);

        // 4. Live On-Device Logcat Reader (via ADB READ_LOGS grant)
        LinearLayout logCard = Theme.createCard(ctx, false);
        LinearLayout logHeader = new LinearLayout(ctx);
        logHeader.setOrientation(LinearLayout.HORIZONTAL);
        logHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView logTitle = Theme.createSectionLabel(ctx, "04 // LIVE ON-DEVICE LOGCAT (READ_LOGS)");
        LinearLayout.LayoutParams ltLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        logTitle.setLayoutParams(ltLp);
        logHeader.addView(logTitle);

        final TextView logView = new TextView(ctx);
        logView.setTypeface(Theme.MONO);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        logView.setTextColor(Theme.TEXT_PRIMARY);
        logView.setBackground(Theme.cardDrawable(ctx, 0xFF060606, Theme.BORDER_GRAPHITE, 10f, 1f));
        int lPad = Theme.dp(ctx, 10);
        logView.setPadding(lPad, lPad, lPad, lPad);

        TextView refreshLogBtn = Theme.createPill(ctx, "REFRESH LOGS", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.PAPAYA);
        refreshLogBtn.setOnClickListener(v -> loadLogcat(logView));
        logHeader.addView(refreshLogBtn);
        logCard.addView(logHeader);

        LinearLayout.LayoutParams lvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lvLp.topMargin = Theme.dp(ctx, 8);
        logView.setLayoutParams(lvLp);
        logCard.addView(logView);
        content.addView(logCard);

        loadLogcat(logView);

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            if (sm != null) sm.unregisterListener(sensorListener);
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private static void registerSensorSafe(SensorManager sm, SensorEventListener l, int type) {
        Sensor s = sm.getDefaultSensor(type);
        if (s != null) {
            sm.registerListener(l, s, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private static void addIntentButton(Context ctx, LinearLayout row, String label, final Runnable action) {
        TextView btn = Theme.createPill(ctx, label, Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.BORDER_GRAPHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(ctx, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> action.run());
        row.addView(btn);
    }

    private static void putGlobalSafe(Context ctx, String key, String val) {
        try {
            Settings.Global.putString(ctx.getContentResolver(), key, val);
            Toast.makeText(ctx, "Updated " + key + " = " + val, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(ctx, "WRITE_SECURE_SETTINGS required", Toast.LENGTH_SHORT).show();
        }
    }

    private static void refreshSettingsDump(Context ctx, TextView tv) {
        String animW = Settings.Global.getString(ctx.getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE);
        String animT = Settings.Global.getString(ctx.getContentResolver(), Settings.Global.TRANSITION_ANIMATION_SCALE);
        String headsUp = Settings.Global.getString(ctx.getContentResolver(), "heads_up_notifications_enabled");
        String dns = Settings.Global.getString(ctx.getContentResolver(), "private_dns_mode");
        String blacklist = Settings.Secure.getString(ctx.getContentResolver(), "icon_blacklist");

        tv.setText(String.format(Locale.US,
                "window_animation_scale       = %s\n" +
                "transition_animation_scale   = %s\n" +
                "heads_up_notifications       = %s\n" +
                "private_dns_mode             = %s\n" +
                "secure.icon_blacklist        = %s",
                animW != null ? animW : "1.0",
                animT != null ? animT : "1.0",
                headsUp != null ? headsUp : "1",
                dns != null ? dns : "opportunistic",
                !TextUtils.isEmpty(blacklist) ? blacklist : "(none)"
        ));
    }

    private static void loadLogcat(final TextView target) {
        target.setText("Reading kernel/system ring buffer...");
        new Thread(() -> {
            final StringBuilder sb = new StringBuilder();
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-t", "35", "-v", "time"});
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                br.close();
            } catch (Exception e) {
                sb.append("Error reading logcat: ").append(e.getMessage());
            }
            final String out = sb.length() > 0 ? sb.toString() : "No log entries returned (check READ_LOGS grant).";
            target.post(() -> target.setText(out));
        }).start();
    }
}
