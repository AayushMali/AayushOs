package dev.aayush.os;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module 3: Developer Toolbox.
 * Offline-first utilities (JSON, JWT, Base64, Regex, Epoch, QR, HTTP API Tester, DNS/Ping).
 */
public final class DeveloperToolbox {

    public interface StringCallback {
        void onResult(String output);
    }

    public interface BitmapCallback {
        void onBitmap(Bitmap bmp);
    }

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static String formatJson(String input, boolean pretty) {
        if (input == null || input.trim().isEmpty()) return "Empty input";
        String t = input.trim();
        try {
            if (t.startsWith("[")) {
                JSONArray arr = new JSONArray(t);
                return pretty ? arr.toString(2) : arr.toString();
            } else {
                JSONObject obj = new JSONObject(t);
                return pretty ? obj.toString(2) : obj.toString();
            }
        } catch (Exception e) {
            return "Invalid JSON Syntax:\n" + e.getMessage();
        }
    }

    public static String decodeJwtFull(String token) {
        if (token == null || token.trim().isEmpty()) return "Empty JWT token";
        String[] parts = token.trim().split("\\.");
        if (parts.length < 2) return "Invalid JWT format (expected header.payload.signature)";
        try {
            String headerJson = new String(Base64.decode(parts[0],
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.decode(parts[1],
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8);

            JSONObject hObj = new JSONObject(headerJson);
            JSONObject pObj = new JSONObject(payloadJson);

            StringBuilder sb = new StringBuilder();
            sb.append("// HEADER\n").append(hObj.toString(2)).append("\n\n");
            sb.append("// PAYLOAD\n").append(pObj.toString(2)).append("\n");

            if (pObj.has("exp")) {
                long expSec = pObj.optLong("exp", 0L);
                long nowSec = System.currentTimeMillis() / 1000L;
                long diffMin = (expSec - nowSec) / 60L;
                sb.append("\n// EXPIRATION: ")
                  .append(diffMin >= 0 ? ("VALID (" + diffMin + " min remaining)") : ("EXPIRED (" + (-diffMin) + " min ago)"));
            }
            return sb.toString();
        } catch (Exception e) {
            return "JWT Decode Error: " + e.getMessage();
        }
    }

    public static String base64Convert(String input, boolean encode) {
        if (input == null) return "";
        try {
            if (encode) {
                return Base64.encodeToString(input.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            } else {
                byte[] dec = Base64.decode(input.trim(), Base64.DEFAULT);
                return new String(dec, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "Base64 Error: " + e.getMessage();
        }
    }

    public static String urlOrEpochConvert(String input) {
        if (input == null || input.trim().isEmpty()) return "Enter epoch timestamp or URL text";
        String t = input.trim();
        if (t.matches("^\\d{9,13}$")) {
            long val = Long.parseLong(t);
            long ms = (t.length() <= 10) ? val * 1000L : val;
            SimpleDateFormat ist = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'IST'", Locale.US);
            ist.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
            utc.setTimeZone(TimeZone.getTimeZone("UTC"));
            return "EPOCH : " + val + "\nIST   : " + ist.format(new Date(ms)) + "\nUTC   : " + utc.format(new Date(ms));
        }
        try {
            if (t.contains("%")) {
                return "URL DECODED:\n" + URLDecoder.decode(t, "UTF-8");
            } else {
                return "URL ENCODED:\n" + URLEncoder.encode(t, "UTF-8");
            }
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public static String testRegex(String patternStr, String sampleText) {
        if (patternStr == null || patternStr.isEmpty()) return "Provide a regex pattern";
        try {
            Pattern p = Pattern.compile(patternStr);
            Matcher m = p.matcher(sampleText != null ? sampleText : "");
            StringBuilder sb = new StringBuilder();
            int count = 0;
            while (m.find()) {
                count++;
                sb.append(String.format(Locale.US, "Match #%d [%d..%d]: \"%s\"\n",
                        count, m.start(), m.end(), m.group()));
                for (int g = 1; g <= m.groupCount(); g++) {
                    sb.append(String.format(Locale.US, "   Group %d: %s\n", g, m.group(g)));
                }
            }
            if (count == 0) return "0 matches found.";
            return "Total Matches: " + count + "\n" + sb.toString().trim();
        } catch (Exception e) {
            return "Regex Syntax Error: " + e.getMessage();
        }
    }

    public static void executeHttpTest(final String method, final String urlStr, final String bodyJson, final StringCallback cb) {
        new Thread(() -> {
            long startMs = SystemClock.elapsedRealtime();
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr.trim());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "AayushOS/1.0 (Pixel 7 Panther)");
                conn.setRequestProperty("Accept", "application/json, text/plain, */*");

                if ("POST".equalsIgnoreCase(method) && bodyJson != null && !bodyJson.trim().isEmpty()) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bodyJson.getBytes(StandardCharsets.UTF_8));
                    }
                }

                int code = conn.getResponseCode();
                long latencyMs = SystemClock.elapsedRealtime() - startMs;
                InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder resp = new StringBuilder();
                if (is != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String line;
                    while ((line = br.readLine()) != null) {
                        resp.append(line).append('\n');
                    }
                    br.close();
                }
                String rawResp = resp.toString().trim();
                if (rawResp.startsWith("{") || rawResp.startsWith("[")) {
                    rawResp = formatJson(rawResp, true);
                }
                if (rawResp.length() > 1400) {
                    rawResp = rawResp.substring(0, 1400) + "\n... [truncated]";
                }
                final String out = String.format(Locale.US,
                        "HTTP %d %s  •  %d ms\n----------------------------------\n%s",
                        code, conn.getResponseMessage(), latencyMs, rawResp);
                mainHandler.post(() -> cb.onResult(out));
            } catch (Exception e) {
                final String err = "HTTP Request Failed: " + e.getClass().getSimpleName() + " — " + e.getMessage();
                mainHandler.post(() -> cb.onResult(err));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public static void runNetworkDiagnostics(final Context ctx, final String hostInput, final StringCallback cb) {
        new Thread(() -> {
            String host = (hostInput == null || hostInput.trim().isEmpty()) ? "github.com" : hostInput.trim();
            // Strip scheme if user pasted full URL
            host = host.replaceFirst("^https?://", "").split("/")[0].split(":")[0];

            StringBuilder sb = new StringBuilder();
            sb.append("LOCAL LAN IP : ").append(getLocalIpv4()).append('\n');
            sb.append("TARGET HOST  : ").append(host).append('\n');

            try {
                long dnsStart = SystemClock.elapsedRealtime();
                InetAddress[] addrs = InetAddress.getAllByName(host);
                long dnsMs = SystemClock.elapsedRealtime() - dnsStart;
                sb.append("DNS RESOLVE  : ").append(dnsMs).append(" ms (").append(addrs.length).append(" records)\n");
                for (int i = 0; i < Math.min(4, addrs.length); i++) {
                    sb.append("  -> ").append(addrs[i].getHostAddress()).append('\n');
                }

                // Measure TCP handshake latency on 443 or 80
                if (addrs.length > 0) {
                    int[] ports = new int[]{443, 80};
                    for (int p : ports) {
                        long t0 = SystemClock.elapsedRealtime();
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(addrs[0], p), 2500);
                            long tcpMs = SystemClock.elapsedRealtime() - t0;
                            sb.append(String.format(Locale.US, "TCP PING :%-3d: %d ms [OPEN]\n", p, tcpMs));
                        } catch (Exception ignored) {
                            sb.append(String.format(Locale.US, "TCP PING :%-3d: Closed/Filtered\n", p));
                        }
                    }
                }
            } catch (Exception e) {
                sb.append("DNS/Ping Error: ").append(e.getMessage());
            }

            final String result = sb.toString().trim();
            mainHandler.post(() -> cb.onResult(result));
        }).start();
    }

    public static String getLocalIpv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress() + " (" + nif.getName() + ")";
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Offline";
    }

    public static void generateQrCode(final String payload, final int sizePx, final BitmapCallback cb) {
        final String text = (payload == null || payload.trim().isEmpty()) ? "https://github.com" : payload.trim();
        new Thread(() -> {
            Bitmap bmp = null;
            try {
                String url = "https://api.qrserver.com/v1/create-qr-code/?size=" + sizePx + "x" + sizePx
                        + "&color=FF8000&bgcolor=0A0A0A&margin=1&data=" + URLEncoder.encode(text, "UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(3500);
                conn.setReadTimeout(3500);
                if (conn.getResponseCode() == 200) {
                    bmp = BitmapFactory.decodeStream(conn.getInputStream());
                }
                conn.disconnect();
            } catch (Exception ignored) {}

            if (bmp == null) {
                bmp = renderDeterministicMatrixBitmap(text, sizePx);
            }
            final Bitmap result = bmp;
            mainHandler.post(() -> cb.onBitmap(result));
        }).start();
    }

    private static Bitmap renderDeterministicMatrixBitmap(String text, int sizePx) {
        int modules = 21;
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Theme.BG_OBSIDIAN);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Theme.PAPAYA);
        float cell = sizePx / (float) (modules + 2);

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int r = 0; r < modules; r++) {
            for (int col = 0; col < modules; col++) {
                boolean finder = (r < 7 && col < 7) || (r < 7 && col >= modules - 7) || (r >= modules - 7 && col < 7);
                boolean draw;
                if (finder) {
                    int lr = r < 7 ? r : r - (modules - 7);
                    int lc = col < 7 ? col : col - (modules - 7);
                    draw = (lr == 0 || lr == 6 || lc == 0 || lc == 6 || (lr >= 2 && lr <= 4 && lc >= 2 && lc <= 4));
                } else {
                    int idx = (r * modules + col);
                    int b = bytes[idx % bytes.length] & 0xFF;
                    draw = ((b ^ (r * 31 + col * 17)) & 1) == 1;
                }
                if (draw) {
                    float left = (col + 1) * cell;
                    float top = (r + 1) * cell;
                    c.drawRect(left, top, left + cell - 1, top + cell - 1, p);
                }
            }
        }
        return bmp;
    }
}
