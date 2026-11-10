package dev.aayush.os;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Module 7: McLaren #81 F1 Command Centre.
 * Live Jolpica/Ergast F1 API client with IST (+05:30) race schedule conversion,
 * Oscar Piastri #81 WDC/WCC telemetry, offline caching, and Race-Day notifications.
 */
public final class F1CommandCenter {

    public static final class TelemetryData {
        public String raceName = "São Paulo Grand Prix";
        public String circuitName = "Autódromo José Carlos Pace (Interlagos)";
        public String roundStr = "ROUND 21";
        public String raceIstTimeStr = "Sun • 22:30 IST";
        public String qualiIstTimeStr = "Sat • 23:30 IST";
        public long raceEpochMs = 0L;
        public String countdownStr = "LIVE SEASON TELEMETRY";
        public String piastriStanding = "#81 Oscar Piastri (McLaren) — P4 • 262 PTS • 2 WINS";
        public String mclarenStanding = "McLaren Formula 1 Team — P1 WCC • 593 PTS";
        public String topDriversTable =
                "P1  VER  Red Bull     362 PTS\n" +
                "P2  NOR  McLaren      315 PTS\n" +
                "P3  LEC  Ferrari      291 PTS\n" +
                "P4  PIA  McLaren #81  262 PTS  [★]\n" +
                "P5  SAI  Ferrari      240 PTS";
        public boolean fromLiveApi = false;
    }

    public interface Callback {
        void onUpdated(TelemetryData data);
    }

    private static final String PREF_NAME = "aayush_os_f1_cache";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static TelemetryData loadCached(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        TelemetryData d = new TelemetryData();
        d.raceName = prefs.getString("raceName", d.raceName);
        d.circuitName = prefs.getString("circuitName", d.circuitName);
        d.roundStr = prefs.getString("roundStr", d.roundStr);
        d.raceIstTimeStr = prefs.getString("raceIstTimeStr", d.raceIstTimeStr);
        d.qualiIstTimeStr = prefs.getString("qualiIstTimeStr", d.qualiIstTimeStr);
        d.raceEpochMs = prefs.getLong("raceEpochMs", 0L);
        d.piastriStanding = prefs.getString("piastriStanding", d.piastriStanding);
        d.mclarenStanding = prefs.getString("mclarenStanding", d.mclarenStanding);
        d.topDriversTable = prefs.getString("topDriversTable", d.topDriversTable);
        d.fromLiveApi = prefs.getBoolean("fromLiveApi", false);
        updateCountdownString(d);
        NowBarState.f1Ticker = "#81 PIASTRI • " + d.raceName + " (" + d.countdownStr + ")";
        return d;
    }

    public static void updateCountdownString(TelemetryData d) {
        if (d.raceEpochMs > System.currentTimeMillis()) {
            long diffSec = (d.raceEpochMs - System.currentTimeMillis()) / 1000L;
            long days = diffSec / 86400L;
            long hours = (diffSec % 86400L) / 3600L;
            long mins = (diffSec % 3600L) / 60L;
            d.countdownStr = String.format(Locale.US, "LIGHTS OUT IN %02dd %02dh %02dm", days, hours, mins);
        } else {
            d.countdownStr = "RACE WEEKEND // IST TELEMETRY READY";
        }
    }

    public static void fetchLiveTelemetry(final Context ctx, final Callback cb) {
        new Thread(() -> {
            TelemetryData d = loadCached(ctx);
            try {
                // 1. Fetch Next Race Schedule
                String nextJson = httpGet("https://api.jolpi.ca/ergast/f1/current/next.json");
                if (nextJson != null) {
                    JSONObject root = new JSONObject(nextJson);
                    JSONArray races = root.getJSONObject("MRData")
                            .getJSONObject("RaceTable").getJSONArray("Races");
                    if (races.length() > 0) {
                        JSONObject race = races.getJSONObject(0);
                        d.raceName = race.optString("raceName", d.raceName);
                        d.roundStr = "ROUND " + race.optString("round", "1");
                        JSONObject circuit = race.optJSONObject("Circuit");
                        if (circuit != null) {
                            d.circuitName = circuit.optString("circuitName", d.circuitName);
                        }
                        String dateStr = race.optString("date", "");
                        String timeStr = race.optString("time", "14:00:00Z");
                        long epoch = parseIsoUtc(dateStr, timeStr);
                        if (epoch > 0) {
                            d.raceEpochMs = epoch;
                            d.raceIstTimeStr = "Race: " + formatIst(epoch);
                        }
                        JSONObject quali = race.optJSONObject("Qualifying");
                        if (quali != null) {
                            long qEpoch = parseIsoUtc(quali.optString("date", ""), quali.optString("time", "14:00:00Z"));
                            if (qEpoch > 0) {
                                d.qualiIstTimeStr = "Quali: " + formatIst(qEpoch);
                            }
                        }
                        d.fromLiveApi = true;
                    }
                }

                // 2. Fetch Driver Standings (Highlighting #81 Oscar Piastri)
                String drvJson = httpGet("https://api.jolpi.ca/ergast/f1/current/driverStandings.json");
                if (drvJson != null) {
                    JSONObject root = new JSONObject(drvJson);
                    JSONArray lists = root.getJSONObject("MRData")
                            .getJSONObject("StandingsTable").getJSONArray("StandingsLists");
                    if (lists.length() > 0) {
                        JSONArray standings = lists.getJSONObject(0).getJSONArray("DriverStandings");
                        StringBuilder table = new StringBuilder();
                        for (int i = 0; i < Math.min(6, standings.length()); i++) {
                            JSONObject row = standings.getJSONObject(i);
                            String pos = row.optString("position", String.valueOf(i + 1));
                            String pts = row.optString("points", "0");
                            String wins = row.optString("wins", "0");
                            JSONObject driver = row.getJSONObject("Driver");
                            String code = driver.optString("code", "DRV");
                            String famName = driver.optString("familyName", "");
                            JSONArray constructors = row.getJSONArray("Constructors");
                            String team = constructors.length() > 0
                                    ? constructors.getJSONObject(0).optString("name", "") : "";

                            boolean isPiastri = "PIA".equalsIgnoreCase(code)
                                    || famName.toLowerCase(Locale.US).contains("piastri");
                            if (isPiastri) {
                                d.piastriStanding = String.format(Locale.US,
                                        "#81 Oscar Piastri (%s) — P%s • %s PTS • %s WINS",
                                        team, pos, pts, wins);
                            }
                            table.append(String.format(Locale.US, "P%-2s %-4s %-12s %4s PTS%s\n",
                                    pos, code, ellipsize(team, 12), pts, isPiastri ? "  [#81 ★]" : ""));
                        }
                        // Also scan beyond top 6 if Piastri wasn't in top 6
                        for (int i = 0; i < standings.length(); i++) {
                            JSONObject row = standings.getJSONObject(i);
                            JSONObject driver = row.getJSONObject("Driver");
                            if ("PIA".equalsIgnoreCase(driver.optString("code", ""))) {
                                d.piastriStanding = String.format(Locale.US,
                                        "#81 Oscar Piastri (McLaren) — P%s • %s PTS • %s WINS",
                                        row.optString("position", "?"),
                                        row.optString("points", "0"),
                                        row.optString("wins", "0"));
                                break;
                            }
                        }
                        if (table.length() > 0) {
                            d.topDriversTable = table.toString().trim();
                        }
                        d.fromLiveApi = true;
                    }
                }

                // 3. Fetch Constructor Standings (Highlighting McLaren)
                String conJson = httpGet("https://api.jolpi.ca/ergast/f1/current/constructorStandings.json");
                if (conJson != null) {
                    JSONObject root = new JSONObject(conJson);
                    JSONArray lists = root.getJSONObject("MRData")
                            .getJSONObject("StandingsTable").getJSONArray("StandingsLists");
                    if (lists.length() > 0) {
                        JSONArray standings = lists.getJSONObject(0).getJSONArray("ConstructorStandings");
                        for (int i = 0; i < standings.length(); i++) {
                            JSONObject row = standings.getJSONObject(i);
                            String name = row.getJSONObject("Constructor").optString("name", "");
                            if (name.toLowerCase(Locale.US).contains("mclaren")) {
                                d.mclarenStanding = String.format(Locale.US,
                                        "McLaren F1 Team — P%s WCC • %s PTS • %s WINS",
                                        row.optString("position", "1"),
                                        row.optString("points", "0"),
                                        row.optString("wins", "0"));
                                break;
                            }
                        }
                    }
                }

                updateCountdownString(d);
                saveCache(ctx, d);
            } catch (Exception ignored) {
                updateCountdownString(d);
            }

            NowBarState.f1Ticker = "#81 PIASTRI • " + d.raceName;
            final TelemetryData result = d;
            mainHandler.post(() -> cb.onUpdated(result));
        }).start();
    }

    public static void triggerRaceDayNotification(Context ctx, TelemetryData d) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String chId = "aayush_f1_81";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    chId, "McLaren #81 Race Control", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(ctx, chId)
                .setSmallIcon(R.drawable.ic_aayush_os)
                .setContentTitle("🏎️ #81 McLaren Race Control: " + d.raceName)
                .setContentText(d.countdownStr + " | " + d.raceIstTimeStr)
                .setStyle(new Notification.BigTextStyle().bigText(
                        d.countdownStr + "\n" +
                        d.circuitName + " (" + d.roundStr + ")\n" +
                        d.qualiIstTimeStr + "  •  " + d.raceIstTimeStr + "\n" +
                        d.piastriStanding))
                .build();
        nm.notify(8181, n);
    }

    private static void saveCache(Context ctx, TelemetryData d) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString("raceName", d.raceName)
                .putString("circuitName", d.circuitName)
                .putString("roundStr", d.roundStr)
                .putString("raceIstTimeStr", d.raceIstTimeStr)
                .putString("qualiIstTimeStr", d.qualiIstTimeStr)
                .putLong("raceEpochMs", d.raceEpochMs)
                .putString("piastriStanding", d.piastriStanding)
                .putString("mclarenStanding", d.mclarenStanding)
                .putString("topDriversTable", d.topDriversTable)
                .putBoolean("fromLiveApi", d.fromLiveApi)
                .apply();
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(urlStr);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static long parseIsoUtc(String date, String time) {
        try {
            String cleanTime = time.endsWith("Z") ? time.substring(0, time.length() - 1) : time;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date dt = sdf.parse(date + " " + cleanTime);
            return dt != null ? dt.getTime() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String formatIst(long epochMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE dd MMM • HH:mm 'IST'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        return sdf.format(new Date(epochMs));
    }

    private static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
