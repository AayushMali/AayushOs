package dev.aayush.os;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 2, 6, 7, 8 & 11 + V3.1 Network Independence:
 * Lightweight Authenticated Wireless Receiver on Pixel 7 (:8183).
 * Advertises _aayush-pixel._tcp via Android NsdManager (mDNS/Bonjour) and accepts
 * authenticated Direct LAN and Tailscale VPN (100.64.0.0/10) requests with ENC_V31 decryption.
 */
public final class PixelCompanionServer {

    public static final int PORT = 8183;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static ServerSocket serverSocket = null;
    private static NsdManager nsdManager = null;
    private static NsdManager.RegistrationListener nsdRegListener = null;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService workerPool = Executors.newFixedThreadPool(6);
    private static volatile boolean isTorchOn = false;
    private static android.media.Ringtone activeAlarmRingtone = null;

    public static synchronized void ensureStarted(final Context ctx) {
        android.util.Log.i("PixelCompanionServer", "ensureStarted called. running=" + running.get() + ", manuallyDisconnected=" + MacCompanion.isManuallyDisconnected(ctx));
        if (running.get()) return;
        if (MacCompanion.isManuallyDisconnected(ctx)) return;
        final Context appCtx = ctx.getApplicationContext();
        running.set(true);
        new Thread(() -> {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    try { serverSocket.close(); } catch (Exception ignored) {}
                }
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new java.net.InetSocketAddress(PORT));
                registerMdnsService(appCtx);
                while (running.get()) {
                    final Socket client = serverSocket.accept();
                    if (!running.get()) {
                        try { client.close(); } catch (Exception ignored) {}
                        break;
                    }
                    workerPool.execute(() -> handleClient(appCtx, client));
                }
            } catch (Exception e) {
                android.util.Log.e("PixelCompanionServer", "Server error", e);
            } finally {
                running.set(false);
            }
        }, "PixelCompanionServer").start();
    }

    public static synchronized void stopServer() {
        running.set(false);
        unregisterMdnsService();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
            serverSocket = null;
        }
    }

    private static void registerMdnsService(Context ctx) {
        try {
            if (nsdRegListener != null) return;
            nsdManager = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
            if (nsdManager == null) return;
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName("AayushOS-Pixel7-Panther");
            info.setServiceType("_aayush-pixel._tcp.");
            info.setPort(PORT);
            nsdRegListener = new NsdManager.RegistrationListener() {
                @Override public void onServiceRegistered(NsdServiceInfo s) {}
                @Override public void onRegistrationFailed(NsdServiceInfo s, int e) {}
                @Override public void onServiceUnregistered(NsdServiceInfo s) {}
                @Override public void onUnregistrationFailed(NsdServiceInfo s, int e) {}
            };
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdRegListener);
        } catch (Exception ignored) {}
    }

    private static void unregisterMdnsService() {
        try {
            if (nsdManager != null && nsdRegListener != null) {
                nsdManager.unregisterService(nsdRegListener);
            }
        } catch (Exception ignored) {
        } finally {
            nsdRegListener = null;
        }
    }

    public static boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    private static boolean isAllowedLanAddress(InetAddress addr) {
        if (addr == null) return false;
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
            return true;
        }
        String host = addr.getHostAddress();
        if (host == null) return false;
        // V3.1: Allow Tailscale CGNAT (100.64.0.0/10) and Tailscale IPv6 ULA (fd7a:115c:a1e0::/48)
        return HardwareMonitor.isTailscaleIpv4(host) || host.toLowerCase(Locale.US).startsWith("fd7a:115c:a1e0:");
    }

    private static void handleClient(final Context ctx, Socket sock) {
        try {
            sock.setSoTimeout(4000);
            if (!isAllowedLanAddress(sock.getInetAddress())) {
                sock.close();
                return;
            }
            InputStream is = sock.getInputStream();
            OutputStream os = sock.getOutputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            String reqLine = br.readLine();
            if (reqLine == null || reqLine.isEmpty()) {
                sock.close();
                return;
            }
            String[] parts = reqLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String path = parts.length > 1 ? parts[1] : "/";

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = br.readLine()) != null && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    headers.put(line.substring(0, idx).trim().toLowerCase(Locale.US),
                            line.substring(idx + 1).trim());
                }
            }

            String expectedToken = MacCompanion.getAuthToken(ctx);
            String providedToken = headers.get("x-aayush-token");
            String providedSig = headers.get("x-aayush-signature");
            String providedTs = headers.get("x-aayush-timestamp");

            boolean authOk = false;
            // Cryptographic HMAC-SHA256 signature verification with 120s anti-replay window (VULN-02)
            if (!TextUtils.isEmpty(providedSig) && !TextUtils.isEmpty(providedTs)) {
                try {
                    long ts = Long.parseLong(providedTs);
                    long nowSec = System.currentTimeMillis() / 1000L;
                    if (Math.abs(nowSec - ts) <= 120) {
                        String expectedSig = MacCompanion.computeHmacSha256(expectedToken, method + ":" + path + ":" + providedTs);
                        if (providedSig.equalsIgnoreCase(expectedSig)) {
                            authOk = true;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (!authOk && !TextUtils.isEmpty(expectedToken) && expectedToken.equals(providedToken)) {
                authOk = true;
            }

            if (!authOk) {
                sendJson(os, 401, new JSONObject().put("ok", false).put("error", "Unauthorized"));
                sock.close();
                return;
            }

            // V3.1 Dynamic Auto-Pairing: When an authenticated Mac peer connects to :8183,
            // automatically update the Pixel's known Mac IP (unless manually disconnected).
            InetAddress remoteInet = sock.getInetAddress();
            if (remoteInet != null && !remoteInet.isLoopbackAddress()) {
                String peerIp = remoteInet.getHostAddress();
                if (!TextUtils.isEmpty(peerIp)) {
                    if (HardwareMonitor.isTailscaleIpv4(peerIp)) {
                        MacCompanion.setMacTailscaleHost(ctx, peerIp);
                    } else if (!MacCompanion.isManuallyDisconnected(ctx)) {
                        MacCompanion.setMacHost(ctx, peerIp);
                    }
                }
            }

            int contentLen = 0;
            try {
                String cl = headers.get("content-length");
                if (cl != null) contentLen = Integer.parseInt(cl);
            } catch (Exception ignored) {}

            char[] bodyChars = new char[Math.max(0, Math.min(contentLen, 512 * 1024))];
            int readTotal = 0;
            while (readTotal < bodyChars.length) {
                int r = br.read(bodyChars, readTotal, bodyChars.length - readTotal);
                if (r < 0) break;
                readTotal += r;
            }
            String bodyStr = new String(bodyChars, 0, readTotal);

            if ("GET".equals(method) && "/status".equals(path)) {
                HardwareMonitor.Snapshot s = new HardwareMonitor(ctx).sample();
                String clientIp = sock.getInetAddress() != null ? sock.getInetAddress().getHostAddress() : "";
                boolean viaVpn = HardwareMonitor.isTailscaleIpv4(clientIp);
                JSONObject resp = new JSONObject()
                        .put("ok", true)
                        .put("version", "3.1")
                        .put("device", "Google Pixel 7 (panther)")
                        .put("transport", viaVpn ? "TAILSCALE VPN" : "DIRECT LAN")
                        .put("battery_pct", s.batteryPct)
                        .put("power_watts", s.powerWatts)
                        .put("temp_c", s.batteryTempC)
                        .put("wifi_ip", s.wifiIp)
                        .put("tailscale_ip", s.tailscaleIp)
                        .put("vpn_active", s.vpnActive)
                        .put("bt_enabled", s.bluetoothEnabled)
                        .put("screen_on", s.screenInteractive);
                sendJson(os, 200, resp);

            } else if ("POST".equals(method) && "/clipboard".equals(path)) {
                JSONObject obj = new JSONObject(bodyStr.isEmpty() ? "{}" : bodyStr);
                String decodedText = obj.optString("text", "");
                String encV31 = obj.optString("enc_v31", "");
                if (!TextUtils.isEmpty(encV31)) {
                    String dec = MacCompanion.decryptV31Payload(ctx, encV31);
                    if (!TextUtils.isEmpty(dec)) {
                        decodedText = dec;
                    }
                }
                final String text = decodedText;
                if (!TextUtils.isEmpty(text)) {
                    mainHandler.post(() -> {
                        SmartClipboard.copyToClipboard(ctx, "MacBridge", text);
                        SmartClipboard.markSynced(ctx, text, "MAC → PIXEL");
                        Toast.makeText(ctx, "📋 Clipboard received from Mac M4", Toast.LENGTH_SHORT).show();
                    });
                }
                sendJson(os, 200, new JSONObject().put("ok", true));

            } else if ("POST".equals(method) && "/dismiss-notif".equals(path)) {
                JSONObject obj = new JSONObject(bodyStr.isEmpty() ? "{}" : bodyStr);
                String key = obj.optString("key", "");
                NowBarNotificationListener.dismissNotificationByKey(key);
                sendJson(os, 200, new JSONObject().put("ok", true));

            } else if ("POST".equals(method) && "/adb-toggle".equals(path)) {
                JSONObject obj = new JSONObject(bodyStr.isEmpty() ? "{}" : bodyStr);
                int enable = obj.optBoolean("enable", true) ? 1 : 0;
                android.provider.Settings.Global.putInt(
                        ctx.getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, enable);
                sendJson(os, 200, new JSONObject().put("ok", true).put("adb_enabled", enable == 1));

            } else if ("POST".equals(method) && "/ring-phone".equals(path)) {
                mainHandler.post(() -> {
                    try {
                        if (activeAlarmRingtone != null && activeAlarmRingtone.isPlaying()) {
                            activeAlarmRingtone.stop();
                            activeAlarmRingtone = null;
                            Toast.makeText(ctx, "🔕 Siren Stopped from Mac", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM);
                            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, max, 0);
                        }
                        android.net.Uri alert = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
                        if (alert == null) alert = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE);
                        activeAlarmRingtone = android.media.RingtoneManager.getRingtone(ctx, alert);
                        if (activeAlarmRingtone != null) {
                            activeAlarmRingtone.play();
                            mainHandler.postDelayed(() -> {
                                if (activeAlarmRingtone != null && activeAlarmRingtone.isPlaying()) {
                                    activeAlarmRingtone.stop();
                                    activeAlarmRingtone = null;
                                }
                            }, 12000L);
                        }
                        Toast.makeText(ctx, "🚨 Find My Phone Siren Triggered from Mac!", Toast.LENGTH_LONG).show();
                    } catch (Exception ignored) {}
                });
                sendJson(os, 200, new JSONObject().put("ok", true).put("output", "Siren toggled on Pixel 7"));

            } else if ("POST".equals(method) && "/flashlight-toggle".equals(path)) {
                mainHandler.post(() -> {
                    try {
                        android.hardware.camera2.CameraManager cm = (android.hardware.camera2.CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
                        if (cm != null) {
                            String[] ids = cm.getCameraIdList();
                            if (ids != null && ids.length > 0) {
                                isTorchOn = !isTorchOn;
                                cm.setTorchMode(ids[0], isTorchOn);
                                Toast.makeText(ctx, isTorchOn ? "🔦 Torch Turned ON from Mac" : "🔦 Torch Turned OFF", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (Exception ignored) {}
                });
                sendJson(os, 200, new JSONObject().put("ok", true).put("output", "Toggled Pixel 7 Flashlight"));

            } else if ("POST".equals(method) && "/dnd-toggle".equals(path)) {
                final boolean toggled = NowBarNotificationListener.toggleDnd();
                final boolean active = NowBarNotificationListener.isDndActive();
                mainHandler.post(() -> {
                    Toast.makeText(ctx, active ? "🌙 Do Not Disturb Enabled from Mac" : "🔔 Normal Sound Restored from Mac", Toast.LENGTH_SHORT).show();
                });
                sendJson(os, 200, new JSONObject().put("ok", toggled).put("dnd_active", active));

            } else if ("POST".equals(method) && "/lock-phone".equals(path)) {
                final boolean locked = NowBarAccessibilityService.lockScreen();
                mainHandler.post(() -> {
                    Toast.makeText(ctx, locked ? "🔒 Screen Locked Remotely from Mac" : "⚠️ Accessibility not active to lock", Toast.LENGTH_SHORT).show();
                });
                sendJson(os, 200, new JSONObject().put("ok", locked).put("locked", locked));

            } else if ("POST".equals(method) && "/file".equals(path)) {
                JSONObject obj = new JSONObject(bodyStr.isEmpty() ? "{}" : bodyStr);
                final String fname = new File(obj.optString("filename", "mac_drop.txt")).getName();
                String content = obj.optString("content", "");
                File dlDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "AayushOS");
                if (!dlDir.exists()) dlDir.mkdirs();
                final File out = new File(dlDir, fname);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(content.getBytes(StandardCharsets.UTF_8));
                }
                mainHandler.post(() -> Toast.makeText(ctx,
                        "📥 Received from Mac: " + out.getName(), Toast.LENGTH_LONG).show());
                sendJson(os, 200, new JSONObject().put("ok", true).put("saved_to", out.getAbsolutePath()));

            } else {
                sendJson(os, 404, new JSONObject().put("ok", false).put("error", "Not found"));
            }
        } catch (Exception ignored) {
        } finally {
            try {
                sock.close();
            } catch (Exception ignored) {}
        }
    }

    private static void sendJson(OutputStream os, int code, JSONObject payload) throws Exception {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        String status = (code == 200) ? "200 OK" : (code == 401 ? "401 Unauthorized" : "404 Not Found");
        String head = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        os.write(head.getBytes(StandardCharsets.UTF_8));
        os.write(body);
        os.flush();
    }
}
