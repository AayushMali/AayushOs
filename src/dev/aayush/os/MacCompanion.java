package dev.aayush.os;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Module 2 (V3.1 — Network Independence): Cross-Device Intelligence Client for MacBook Air M4.
 * Supports Dynamic Multi-Layer Discovery (Signed UDP Subnet Broadcast, mDNS/Bonjour _aayush-os._tcp,
 * .local resolution, and Tailscale Private Mesh VPN 100.64.0.0/10 fallback), automatic IP/subnet
 * transition handling, Client Isolation / Port Block / UDP failure detection, ENC_V31 authenticated
 * encryption, and explicit separation of HTTP Companion connectivity from ADB sessions.
 */
public final class MacCompanion {

    public static final class OtpInboxItem {
        public String code = "";
        public String app = "";
        public int remainingSec = 0;
    }

    public static final class MacNotifItem {
        public String key = "";
        public String app = "";
        public String title = "";
        public String body = "";
        public String url = "";
        public long timestamp = 0L;
    }

    public static final class RemoteFileItem {
        public String name = "";
        public long sizeBytes = 0L;
    }

    public static final class TransferHistoryItem {
        public String direction = "";
        public String filename = "";
        public long sizeBytes = 0L;
        public double speedKbps = 0.0;
        public String dest = "";
        public String status = "";
    }

    public static final class MacStatus {
        public boolean online = false;
        public boolean manuallyDisconnected = false;
        public boolean wifiDisconnectedByUser = false;
        public String hostIp = "192.168.0.110";
        public int port = 8181;
        public String transportMode = "OFFLINE"; // "DIRECT LAN", "TAILSCALE VPN", "OFFLINE", "MAC WI-FI OFF", "DISCONNECTED"
        public String discoverySource = "CACHED";
        public String mdnsHost = "Aayushs-MacBook-Air.local";
        public String computerName = "Aayush's MacBook Air M4";
        public String chip = "Apple M4";
        public String batteryInfo = "Unknown";
        public int batteryPct = 100;
        public boolean batteryCharging = false;
        public String powerSource = "Battery Power";
        public String cpuUsage = "0.0%";
        public double cpuPct = 0.0;
        public String uptimeInfo = "";
        public boolean wifiPower = true;
        public boolean wifiConnected = false;
        public String wifiNetwork = "Unknown";
        public String macLanIp = "";
        public boolean btPower = true;
        public boolean btConnected = false;
        public String btStatus = "Unknown";
        public boolean caffeinated = false;
        public boolean audioMuted = false;
        public int audioVolume = 50;
        public boolean darkMode = true;
        public String macClipboardPreview = "";
        public long latencyMs = -1L;

        // V3.1: Tailscale VPN & ADB Separation State
        public boolean tailscaleInstalled = false;
        public boolean tailscaleRunning = false;
        public String tailscaleState = "Stopped";
        public String macTailscaleIp = "";
        public String pixelTailscaleIp = "";
        public boolean adbConnected = false;
        public String adbStatusLabel = "INACTIVE (Companion HTTP works 100% without ADB)";
        public String diagnosticHint = "";

        // Phase 3: scrcpy state
        public boolean scrcpyRunning = false;
        public String scrcpyTarget = "192.168.0.108:5555";
        public int scrcpyMaxSize = 1080;
        public String scrcpyBitrate = "8M";
        public String scrcpyOrientation = "portrait";

        // Phase 4: Webcam state
        public boolean webcamRunning = false;
        public String webcamFacing = "back";
        public String webcamSize = "1920x1080";
        public int webcamFps = 30;
        public boolean usbUvcDetected = false;

        // Phase 5 & 8: OTP Inbox & Notifications
        public final List<OtpInboxItem> otpInbox = new ArrayList<>();
        public int notifCount = 0;
    }

    public interface StatusCallback {
        void onStatus(MacStatus status);
    }

    public interface MessageCallback {
        void onMessage(boolean ok, String message);
    }

    public interface TransferProgressCallback {
        void onProgress(int pct, long transferredBytes, long totalBytes, double speedKbps);
    }

    public interface OutboxCallback {
        void onOutbox(List<RemoteFileItem> files, List<TransferHistoryItem> history);
    }

    public interface NotificationsCallback {
        void onNotifications(List<MacNotifItem> items);
    }

    public static final String PREF_NAME = "aayush_os_mac";
    public static final String DEFAULT_TOKEN = "aayush81-panther-m4-bridge";
    public static final String DEFAULT_MDNS_HOST = "Aayushs-MacBook-Air.local";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean cancelTransferFlag = new AtomicBoolean(false);
    private static final AtomicBoolean discoveryInFlight = new AtomicBoolean(false);
    public static volatile MacStatus lastKnownStatus = new MacStatus();

    // V3.1 Observed Pixel Network State (for automatic network-switch detection)
    private static volatile String lastObservedPixelLanIp = "0.0.0.0";
    private static volatile String lastObservedBroadcastIp = "255.255.255.255";
    private static volatile String lastObservedPixelTailscaleIp = "";
    private static volatile long lastAutoDiscoveryMs = 0L;

    // Default OTP-permitted packages (Messages, Gmail, WhatsApp, Authenticator/Mail)
    private static final Set<String> DEFAULT_OTP_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging",
            "com.google.android.gm",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.microsoft.office.outlook",
            "dev.aayush.os"
    ));

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getMacHost(Context ctx) {
        return prefs(ctx).getString("mac_ip", "192.168.0.110");
    }

    public static void setMacHost(Context ctx, String ip) {
        if (ip != null && !ip.trim().isEmpty()) {
            prefs(ctx).edit().putString("mac_ip", ip.trim()).apply();
        }
    }

    public static String getMacTailscaleHost(Context ctx) {
        return prefs(ctx).getString("mac_tailscale_ip", "");
    }

    public static void setMacTailscaleHost(Context ctx, String ip) {
        if (ip != null) {
            prefs(ctx).edit().putString("mac_tailscale_ip", ip.trim()).apply();
        }
    }

    public static String getMacMdnsHost(Context ctx) {
        return prefs(ctx).getString("mac_mdns_host", DEFAULT_MDNS_HOST);
    }

    public static void setMacMdnsHost(Context ctx, String host) {
        if (host != null && !host.trim().isEmpty()) {
            prefs(ctx).edit().putString("mac_mdns_host", host.trim()).apply();
        }
    }

    public static String getAuthToken(Context ctx) {
        return prefs(ctx).getString("auth_token", DEFAULT_TOKEN);
    }

    public static void setAuthToken(Context ctx, String token) {
        if (token != null && !token.trim().isEmpty()) {
            prefs(ctx).edit().putString("auth_token", token.trim()).apply();
        }
    }

    public static boolean isManuallyDisconnected(Context ctx) {
        return prefs(ctx).getBoolean("manually_disconnected", false);
    }

    public static void setManuallyDisconnected(Context ctx, boolean disc) {
        prefs(ctx).edit().putBoolean("manually_disconnected", disc).apply();
        if (disc) {
            PixelCompanionServer.stopServer();
            lastKnownStatus.online = false;
            lastKnownStatus.manuallyDisconnected = true;
            lastKnownStatus.transportMode = "DISCONNECTED";
        } else {
            prefs(ctx).edit().putBoolean("wifi_off_by_user", false).apply();
            PixelCompanionServer.ensureStarted(ctx);
        }
    }

    /**
     * V3.1 Network Switch Observer: Called when HardwareMonitor samples active interfaces.
     * Detects when Pixel switches Wi-Fi networks/subnets or enables Tailscale VPN.
     * Never overrides manual disconnects or intentional Mac Wi-Fi OFF commands.
     */
    public static void onNetworkObserved(Context ctx, String wifiIp, String broadcastIp, String tailscaleIp) {
        if (broadcastIp != null && !broadcastIp.isEmpty()) {
            lastObservedBroadcastIp = broadcastIp;
        }
        if (tailscaleIp != null) {
            lastObservedPixelTailscaleIp = tailscaleIp;
        }
        if (wifiIp == null || wifiIp.isEmpty()) return;

        String prevIp = lastObservedPixelLanIp;
        lastObservedPixelLanIp = wifiIp;

        if (!"0.0.0.0".equals(prevIp) && !prevIp.equals(wifiIp) && !"0.0.0.0".equals(wifiIp)) {
            // Pixel switched to a new IP / network!
            if (isManuallyDisconnected(ctx) || prefs(ctx).getBoolean("wifi_off_by_user", false)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastAutoDiscoveryMs > 5000L) {
                lastAutoDiscoveryMs = now;
                final Context appCtx = ctx.getApplicationContext();
                PixelCompanionServer.stopServer();
                PixelCompanionServer.ensureStarted(appCtx);
                discoverMacOnLan(appCtx, (ok, msg) -> {});
            }
        }
    }

    // =========================================================================
    // V3.1 CRYPTO: HMAC-SHA256 & AUTHENTICATED STREAM ENCRYPTION (ENC_V31)
    // =========================================================================

    public static String computeHmacSha256(String keyStr, String messageStr) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyStr.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(messageStr.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(raw);
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] ctrKeystreamXor(byte[] data, byte[] encKey, byte[] iv) throws Exception {
        byte[] out = new byte[data.length];
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        int blockIdx = 0;
        int offset = 0;
        while (offset < data.length) {
            sha.reset();
            sha.update(encKey);
            sha.update(iv);
            sha.update(ByteBuffer.allocate(4).putInt(blockIdx).array());
            byte[] ks = sha.digest();
            int take = Math.min(32, data.length - offset);
            for (int i = 0; i < take; i++) {
                out[offset + i] = (byte) (data[offset + i] ^ ks[i]);
            }
            offset += take;
            blockIdx++;
        }
        return out;
    }

    public static String encryptV31Payload(Context ctx, String plaintext) {
        try {
            String tok = getAuthToken(ctx);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] encKey = sha.digest((tok + ":enc").getBytes(StandardCharsets.UTF_8));
            sha.reset();
            byte[] macKey = sha.digest((tok + ":mac").getBytes(StandardCharsets.UTF_8));

            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            byte[] ptBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ctBytes = ctrKeystreamXor(ptBytes, encKey, iv);

            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(macKey, "HmacSHA256"));
            hmac.update(iv);
            byte[] tag = hmac.doFinal(ctBytes);
            return bytesToHex(iv) + ":" + bytesToHex(ctBytes) + ":" + bytesToHex(tag);
        } catch (Exception e) {
            return "";
        }
    }

    public static String decryptV31Payload(Context ctx, String encV31) {
        try {
            String[] parts = encV31.split(":");
            if (parts.length != 3) return "";
            byte[] iv = hexToBytes(parts[0]);
            byte[] ctBytes = hexToBytes(parts[1]);
            String providedTag = parts[2].toLowerCase(Locale.US);

            String tok = getAuthToken(ctx);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] encKey = sha.digest((tok + ":enc").getBytes(StandardCharsets.UTF_8));
            sha.reset();
            byte[] macKey = sha.digest((tok + ":mac").getBytes(StandardCharsets.UTF_8));

            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(macKey, "HmacSHA256"));
            hmac.update(iv);
            String expectedTag = bytesToHex(hmac.doFinal(ctBytes)).toLowerCase(Locale.US);
            if (!MessageDigest.isEqual(
                    providedTag.getBytes(StandardCharsets.UTF_8),
                    expectedTag.getBytes(StandardCharsets.UTF_8))) {
                return "";
            }
            byte[] ptBytes = ctrKeystreamXor(ctBytes, encKey, iv);
            return new String(ptBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    // Phase 5: OTP Security Settings
    public static boolean isOtpMacDeliveryEnabled(Context ctx) {
        return prefs(ctx).getBoolean("otp_mac_opt_in", true);
    }

    public static void setOtpMacDeliveryEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean("otp_mac_opt_in", enabled).apply();
    }

    public static int getOtpTtlSec(Context ctx) {
        return prefs(ctx).getInt("otp_ttl_sec", 60);
    }

    public static void setOtpTtlSec(Context ctx, int sec) {
        prefs(ctx).edit().putInt("otp_ttl_sec", Math.max(15, Math.min(300, sec))).apply();
    }

    public static boolean isOtpAutoCopyOnMac(Context ctx) {
        return prefs(ctx).getBoolean("otp_auto_copy", true);
    }

    public static void setOtpAutoCopyOnMac(Context ctx, boolean autoCopy) {
        prefs(ctx).edit().putBoolean("otp_auto_copy", autoCopy).apply();
    }

    public static boolean isOtpPackageAllowed(Context ctx, String pkg) {
        if (TextUtils.isEmpty(pkg)) return false;
        boolean allowAllMessaging = prefs(ctx).getBoolean("otp_allow_all_apps", true);
        if (allowAllMessaging) return true;
        Set<String> custom = prefs(ctx).getStringSet("otp_allowed_pkgs", DEFAULT_OTP_PACKAGES);
        return custom != null && custom.contains(pkg);
    }

    // Phase 6: Clipboard Sync Settings
    public static boolean isAutoClipboardSyncEnabled(Context ctx) {
        return prefs(ctx).getBoolean("clip_auto_sync", false);
    }

    public static void setAutoClipboardSyncEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean("clip_auto_sync", enabled).apply();
    }

    public static boolean isSensitiveClipExcluded(Context ctx) {
        return prefs(ctx).getBoolean("clip_exclude_sensitive", true);
    }

    public static void setSensitiveClipExcluded(Context ctx, boolean exclude) {
        prefs(ctx).edit().putBoolean("clip_exclude_sensitive", exclude).apply();
    }

    // Phase 8: Mac Notification Centre Settings
    public static boolean isMacNotifForwardingEnabled(Context ctx) {
        return prefs(ctx).getBoolean("mac_notif_enabled", true);
    }

    public static void setMacNotifForwardingEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean("mac_notif_enabled", enabled).apply();
    }

    public static String getMacNotifFilterMode(Context ctx) {
        return prefs(ctx).getString("mac_notif_filter", "MESSAGING_AND_DEV");
    }

    public static void setMacNotifFilterMode(Context ctx, String mode) {
        prefs(ctx).edit().putString("mac_notif_filter", mode).apply();
    }

    public static boolean shouldForwardNotificationPackage(Context ctx, String pkg) {
        if (!isMacNotifForwardingEnabled(ctx) || TextUtils.isEmpty(pkg)) return false;
        if ("android".equals(pkg) || "com.android.systemui".equals(pkg)) return false;
        String mode = getMacNotifFilterMode(ctx);
        if ("ALL_CLEARABLE".equals(mode)) return true;
        String low = pkg.toLowerCase(Locale.US);
        return low.contains("messaging") || low.contains("whatsapp") || low.contains("telegram")
                || low.contains("slack") || low.contains("discord") || low.contains("gm")
                || low.contains("mail") || low.contains("github") || low.contains("dialer")
                || low.contains("aayush");
    }

    private static HttpURLConnection openAuthConn(Context ctx, String path, String method, int timeoutMs) throws Exception {
        return openAuthConnToHost(ctx, getMacHost(ctx), path, method, timeoutMs);
    }

    private static HttpURLConnection openAuthConnToHost(Context ctx, String host, String path, String method, int timeoutMs) throws Exception {
        URL u = new URL("http://" + host + ":8181" + path);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        String token = getAuthToken(ctx);
        String ts = String.valueOf(System.currentTimeMillis() / 1000L);
        String sig = computeHmacSha256(token, method + ":" + path + ":" + ts);
        conn.setRequestProperty("X-Aayush-Token", token);
        conn.setRequestProperty("X-Aayush-Timestamp", ts);
        conn.setRequestProperty("X-Aayush-Signature", sig);
        if (!"0.0.0.0".equals(lastObservedPixelLanIp)) {
            conn.setRequestProperty("X-Pixel-IP", lastObservedPixelLanIp);
        }
        if (!TextUtils.isEmpty(lastObservedPixelTailscaleIp)) {
            conn.setRequestProperty("X-Pixel-Tailscale-IP", lastObservedPixelTailscaleIp);
        }
        return conn;
    }

    public static String classifyHostTransport(String hostIp) {
        if (TextUtils.isEmpty(hostIp)) return "OFFLINE";
        if (HardwareMonitor.isTailscaleIpv4(hostIp) || hostIp.toLowerCase(Locale.US).startsWith("fd7a:115c:a1e0:")) {
            return "TAILSCALE VPN";
        }
        return "DIRECT LAN";
    }

    public static void fetchMacStatus(final Context ctx, final StatusCallback cb) {
        final String primaryHost = getMacHost(ctx);
        if (isManuallyDisconnected(ctx)) {
            MacStatus st = new MacStatus();
            st.hostIp = primaryHost;
            st.online = false;
            st.manuallyDisconnected = true;
            st.transportMode = "DISCONNECTED";
            lastKnownStatus = st;
            mainHandler.post(() -> cb.onStatus(st));
            return;
        }
        // CRITICAL DISCONNECT POLICY: If user explicitly turned OFF Mac Wi-Fi, NEVER auto-poll or reconnect!
        if (prefs(ctx).getBoolean("wifi_off_by_user", false)) {
            MacStatus st = new MacStatus();
            st.hostIp = primaryHost;
            st.online = false;
            st.wifiPower = false;
            st.wifiDisconnectedByUser = true;
            st.transportMode = "MAC WI-FI OFF";
            st.wifiNetwork = "OFF (Disabled on Mac by User)";
            lastKnownStatus = st;
            mainHandler.post(() -> cb.onStatus(st));
            return;
        }

        PixelCompanionServer.ensureStarted(ctx);

        new Thread(() -> {
            MacStatus st = new MacStatus();
            st.hostIp = primaryHost;

            // V3.1 Dynamic Failover Order:
            // 1. Primary configured/discovered host
            // 2. Tailscale VPN host (if primary LAN fails due to AP Client Isolation or network change)
            // 3. mDNS .local hostname resolution (if LAN IP changed via DHCP on new Wi-Fi)
            // 4. Signed UDP Subnet Broadcast discovery (:8182)
            List<String> candidateHosts = new ArrayList<>();
            candidateHosts.add(primaryHost);
            String tsHost = getMacTailscaleHost(ctx);
            if (!TextUtils.isEmpty(tsHost) && !candidateHosts.contains(tsHost)) {
                candidateHosts.add(tsHost);
            }
            String mdnsHost = getMacMdnsHost(ctx);
            if (!TextUtils.isEmpty(mdnsHost) && !candidateHosts.contains(mdnsHost)) {
                candidateHosts.add(mdnsHost);
            }

            boolean connected = false;
            for (int idx = 0; idx < candidateHosts.size(); idx++) {
                String candidate = candidateHosts.get(idx);
                if (tryFetchStatusFromHost(ctx, candidate, st, idx == 0 ? 1800 : 1400)) {
                    connected = true;
                    if (!candidate.equals(primaryHost)) {
                        setMacHost(ctx, st.hostIp);
                        if (HardwareMonitor.isTailscaleIpv4(st.hostIp)) {
                            st.discoverySource = "TAILSCALE VPN FAILOVER";
                            st.diagnosticHint = "Direct LAN (" + primaryHost + ") unreachable; routed via Tailscale VPN.";
                        } else {
                            st.discoverySource = "mDNS / DYNAMIC FAILOVER";
                        }
                    }
                    break;
                }
            }

            // If all known candidates failed, try a quick UDP broadcast beacon probe (:8182)
            if (!connected) {
                String beaconIp = quickSignedUdpDiscoveryProbe(ctx, 900);
                if (!TextUtils.isEmpty(beaconIp) && tryFetchStatusFromHost(ctx, beaconIp, st, 1500)) {
                    connected = true;
                    setMacHost(ctx, beaconIp);
                    st.discoverySource = "UDP BEACON (:8182)";
                }
            }

            if (!connected) {
                st.online = false;
                st.transportMode = "OFFLINE";
            }

            lastKnownStatus = st;
            final MacStatus res = st;
            mainHandler.post(() -> cb.onStatus(res));
        }).start();
    }

    private static boolean tryFetchStatusFromHost(Context ctx, String targetHost, MacStatus st, int timeoutMs) {
        long t0 = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            String resolvedIp = targetHost;
            if (targetHost.endsWith(".local")) {
                InetAddress addr = InetAddress.getByName(targetHost);
                if (addr != null && addr.getHostAddress() != null) {
                    resolvedIp = addr.getHostAddress();
                }
            }
            conn = openAuthConnToHost(ctx, resolvedIp, "/status", "GET", timeoutMs);
            if (conn.getResponseCode() == 200) {
                st.online = true;
                st.hostIp = resolvedIp;
                st.latencyMs = System.currentTimeMillis() - t0;
                st.transportMode = classifyHostTransport(resolvedIp);

                String json = readResp(conn);
                JSONObject obj = new JSONObject(json);
                st.computerName = obj.optString("hostname", st.computerName);
                st.mdnsHost = obj.optString("mdns_host", st.mdnsHost);
                if (!TextUtils.isEmpty(st.mdnsHost)) {
                    setMacMdnsHost(ctx, st.mdnsHost);
                }
                st.chip = obj.optString("chip", st.chip);
                st.batteryInfo = obj.optString("battery", "100%");
                st.batteryPct = obj.optInt("battery_pct", 100);
                st.batteryCharging = obj.optBoolean("battery_charging", false);
                st.powerSource = obj.optString("power_source", "Battery Power");
                st.cpuUsage = obj.optString("cpu_usage", "0.0%");
                st.cpuPct = obj.optDouble("cpu_pct", 0.0);
                st.uptimeInfo = obj.optString("uptime", "");
                st.wifiPower = obj.optBoolean("wifi_power", true);
                st.wifiConnected = obj.optBoolean("wifi_connected", true);
                st.wifiNetwork = obj.optString("wifi_network", resolvedIp);
                st.macLanIp = obj.optString("wifi_ip", resolvedIp);
                st.btPower = obj.optBoolean("bt_power", true);
                st.btConnected = obj.optBoolean("bt_connected", false);
                st.btStatus = obj.optString("bt_status", st.btPower ? "ON" : "OFF");
                st.caffeinated = obj.optBoolean("caffeinated", false);
                st.audioMuted = obj.optBoolean("audio_muted", false);
                st.audioVolume = obj.optInt("audio_volume", 50);
                st.darkMode = obj.optBoolean("dark_mode", true);
                st.macClipboardPreview = obj.optString("clipboard", "");
                st.notifCount = obj.optInt("notif_count", 0);

                JSONObject tsObj = obj.optJSONObject("tailscale");
                if (tsObj != null) {
                    st.tailscaleInstalled = tsObj.optBoolean("installed", false);
                    st.tailscaleRunning = tsObj.optBoolean("running", false);
                    st.tailscaleState = tsObj.optString("state", "Stopped");
                    st.macTailscaleIp = tsObj.optString("mac_ip", "");
                    st.pixelTailscaleIp = tsObj.optString("pixel_ip", lastObservedPixelTailscaleIp);
                    if (!TextUtils.isEmpty(st.macTailscaleIp)) {
                        setMacTailscaleHost(ctx, st.macTailscaleIp);
                    }
                }

                JSONObject adbObj = obj.optJSONObject("adb");
                if (adbObj != null) {
                    st.adbConnected = adbObj.optBoolean("connected", false);
                    st.adbStatusLabel = adbObj.optString("label", st.adbStatusLabel);
                }

                JSONObject sc = obj.optJSONObject("scrcpy");
                if (sc != null) {
                    st.scrcpyRunning = sc.optBoolean("running", false);
                    st.scrcpyTarget = sc.optString("target", st.scrcpyTarget);
                    st.scrcpyMaxSize = sc.optInt("max_size", 1080);
                    st.scrcpyBitrate = sc.optString("bitrate", "8M");
                    st.scrcpyOrientation = sc.optString("orientation", "portrait");
                }

                JSONObject wc = obj.optJSONObject("webcam");
                if (wc != null) {
                    st.webcamRunning = wc.optBoolean("running", false);
                    st.webcamFacing = wc.optString("facing", "back");
                    st.webcamSize = wc.optString("size", "1920x1080");
                    st.webcamFps = wc.optInt("fps", 30);
                    st.usbUvcDetected = wc.optBoolean("usb_uvc_detected_on_mac", false);
                }

                st.otpInbox.clear();
                JSONArray otps = obj.optJSONArray("otp_inbox");
                if (otps != null) {
                    for (int i = 0; i < otps.length(); i++) {
                        JSONObject o = otps.optJSONObject(i);
                        if (o != null) {
                            OtpInboxItem item = new OtpInboxItem();
                            item.code = o.optString("code", "");
                            item.app = o.optString("app", "App");
                            item.remainingSec = o.optInt("remaining_sec", 0);
                            st.otpInbox.add(item);
                        }
                    }
                }

                JSONArray dismissArr = obj.optJSONArray("pending_dismiss_keys");
                if (dismissArr != null) {
                    for (int i = 0; i < dismissArr.length(); i++) {
                        String k = dismissArr.optString(i, "");
                        if (!TextUtils.isEmpty(k)) {
                            NowBarNotificationListener.dismissNotificationByKey(k);
                        }
                    }
                }
                conn.disconnect();
                return true;
            }
            conn.disconnect();
        } catch (Exception ignored) {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored2) {}
            }
        }
        return false;
    }

    // =========================================================================
    // PHASE 1: MACBOOK SYSTEM CONTROL COMMANDS
    // =========================================================================

    public static void sendMacCommand(final Context ctx, final String actionId, final MessageCallback cb) {
        if ("WIFI_ON".equals(actionId)) {
            prefs(ctx).edit().putBoolean("wifi_off_by_user", false).apply();
        }
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/command", "POST", 3000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject body = new JSONObject().put("action", actionId);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                String resp = readResp(conn);
                conn.disconnect();

                if ("WIFI_OFF".equals(actionId)) {
                    // Record intentional Wi-Fi disconnect so we never auto-poll or override
                    prefs(ctx).edit().putBoolean("wifi_off_by_user", true).apply();
                    lastKnownStatus.wifiPower = false;
                    lastKnownStatus.wifiConnected = false;
                    lastKnownStatus.online = false;
                    lastKnownStatus.wifiDisconnectedByUser = true;
                } else if ("BT_OFF".equals(actionId)) {
                    lastKnownStatus.btPower = false;
                    lastKnownStatus.btConnected = false;
                    lastKnownStatus.btStatus = "OFF (Disabled)";
                } else if ("BT_ON".equals(actionId)) {
                    lastKnownStatus.btPower = true;
                }

                if (code == 200) {
                    JSONObject r = new JSONObject(resp);
                    final String msg = r.optString("output", "Executed " + actionId);
                    mainHandler.post(() -> cb.onMessage(true, msg));
                } else {
                    mainHandler.post(() -> cb.onMessage(false, "Command HTTP " + code));
                }
            } catch (Exception e) {
                if ("WIFI_OFF".equals(actionId)) {
                    // Socket naturally drops when Mac Wi-Fi powers down immediately!
                    prefs(ctx).edit().putBoolean("wifi_off_by_user", true).apply();
                    lastKnownStatus.wifiPower = false;
                    lastKnownStatus.online = false;
                    lastKnownStatus.wifiDisconnectedByUser = true;
                    mainHandler.post(() -> cb.onMessage(true,
                            "Mac Wi-Fi turned OFF immediately. Connection closed as expected."));
                    return;
                }
                mainHandler.post(() -> cb.onMessage(false, "Mac bridge unreachable: " + e.getMessage()));
            }
        }).start();
    }

    // =========================================================================
    // PHASE 3 & 4: SCRCPY MIRRORING & PIXEL WEBCAM
    // =========================================================================

    public static void controlScrcpy(
            final Context ctx,
            final String action,
            final int maxSize,
            final String bitrate,
            final String orientation,
            final MessageCallback cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/scrcpy", "POST", 4500);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject body = new JSONObject()
                        .put("action", action)
                        .put("mode", "wireless")
                        .put("max_size", maxSize)
                        .put("bitrate", bitrate)
                        .put("max_fps", 60)
                        .put("orientation", orientation);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                String resp = readResp(conn);
                conn.disconnect();
                if (code == 200) {
                    JSONObject r = new JSONObject(resp);
                    final boolean ok = r.optBoolean("ok", true);
                    final String msg = r.optString("output", "scrcpy " + action);
                    mainHandler.post(() -> cb.onMessage(ok, msg));
                } else {
                    mainHandler.post(() -> cb.onMessage(false, "scrcpy HTTP " + code));
                }
            } catch (Exception e) {
                mainHandler.post(() -> cb.onMessage(false, "scrcpy error: " + e.getMessage()));
            }
        }).start();
    }

    public static void sendAndroidNavFromMac(final Context ctx, final String navKey, final MessageCallback cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/scrcpy", "POST", 3000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject body = new JSONObject()
                        .put("action", "NAV_KEY")
                        .put("key", navKey);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                String resp = readResp(conn);
                conn.disconnect();
                if (code == 200) {
                    JSONObject r = new JSONObject(resp);
                    mainHandler.post(() -> cb.onMessage(true, r.optString("output", "Sent " + navKey)));
                } else {
                    mainHandler.post(() -> cb.onMessage(false, "Nav HTTP " + code));
                }
            } catch (Exception e) {
                mainHandler.post(() -> cb.onMessage(false, "Nav error: " + e.getMessage()));
            }
        }).start();
    }

    public static void controlWebcam(
            final Context ctx,
            final String action,
            final String facing,
            final String size,
            final int fps,
            final MessageCallback cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/webcam", "POST", 4500);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject body = new JSONObject()
                        .put("action", action)
                        .put("facing", facing)
                        .put("size", size)
                        .put("fps", fps);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                String resp = readResp(conn);
                conn.disconnect();
                if (code == 200) {
                    JSONObject r = new JSONObject(resp);
                    final boolean ok = r.optBoolean("ok", true);
                    final String msg = r.optString("output", "Webcam " + action);
                    mainHandler.post(() -> cb.onMessage(ok, msg));
                } else {
                    mainHandler.post(() -> cb.onMessage(false, "Webcam HTTP " + code));
                }
            } catch (Exception e) {
                mainHandler.post(() -> cb.onMessage(false, "Webcam error: " + e.getMessage()));
            }
        }).start();
    }

    // =========================================================================
    // PHASE 5: SECURE MAC OTP INBOX (NEVER IN NOW BAR • V3.1 ENC_V31 ENCRYPTED)
    // =========================================================================

    public static void forwardOtpToMac(
            final Context ctx,
            final String otpCode,
            final String appName,
            final String packageName,
            final String notifTitle,
            final MessageCallback cb) {
        if (!isOtpMacDeliveryEnabled(ctx) || isManuallyDisconnected(ctx)) {
            if (cb != null) mainHandler.post(() -> cb.onMessage(false, "Mac OTP Delivery is disabled"));
            return;
        }
        if (!isOtpPackageAllowed(ctx, packageName)) {
            if (cb != null) mainHandler.post(() -> cb.onMessage(false, "Package not in OTP allowlist"));
            return;
        }
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/otp", "POST", 2500);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject inner = new JSONObject()
                        .put("code", otpCode)
                        .put("app_name", appName)
                        .put("package_name", packageName)
                        .put("title", notifTitle)
                        .put("ttl_sec", getOtpTtlSec(ctx))
                        .put("auto_copy", isOtpAutoCopyOnMac(ctx));

                JSONObject body = new JSONObject()
                        .put("enc_v31", encryptV31Payload(ctx, inner.toString()))
                        .put("ttl_sec", getOtpTtlSec(ctx))
                        .put("auto_copy", isOtpAutoCopyOnMac(ctx));

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                conn.disconnect();
                final boolean ok = (code == 200);
                if (cb != null) {
                    mainHandler.post(() -> cb.onMessage(ok, ok
                            ? "🔐 Delivered encrypted OTP to Mac OTP Inbox (Auto-clears in " + getOtpTtlSec(ctx) + "s)"
                            : "Failed to deliver OTP (HTTP " + code + ")"));
                }
            } catch (Exception e) {
                if (cb != null) {
                    mainHandler.post(() -> cb.onMessage(false, "Mac OTP Inbox unreachable"));
                }
            }
        }).start();
    }

    public static void copyMacOtp(final Context ctx, final String otpCode, final MessageCallback cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/otp/copy", "POST", 2500);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject body = new JSONObject().put("code", otpCode != null ? otpCode : "");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                String resp = readResp(conn);
                conn.disconnect();
                JSONObject r = new JSONObject(resp);
                mainHandler.post(() -> cb.onMessage(code == 200, r.optString("output", "Copied OTP on Mac")));
            } catch (Exception e) {
                mainHandler.post(() -> cb.onMessage(false, "Error: " + e.getMessage()));
            }
        }).start();
    }

    public static void clearMacOtpInbox(final Context ctx, final MessageCallback cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/otp/clear", "POST", 2500);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write("{}".getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                conn.disconnect();
                mainHandler.post(() -> cb.onMessage(code == 200, "Purged Mac OTP Inbox"));
            } catch (Exception e) {
                mainHandler.post(() -> cb.onMessage(false, "Error: " + e.getMessage()));
            }
        }).start();
    }

    // =========================================================================
    // PHASE 6: ADVANCED BIDIRECTIONAL CLIPBOARD (V3.1 ENC_V31 ENCRYPTED)
    // =========================================================================

    public static void pushClipboardToMac(final Context ctx, final String text, final MessageCallback cb) {
        pushClipboardToMacInternal(ctx, text, false, false, cb);
    }

    public static void pushClipboardToMacInternal(
            final Context ctx,
            final String text,
            final boolean autoSync,
            final boolean forceOverride,
            final MessageCallback cb) {
        if (TextUtils.isEmpty(text)) {
            if (cb != null) mainHandler.post(() -> cb.onMessage(false, "Clipboard is empty"));
            return;
        }
        // Sensitive / OTP exclusion check
        if ((autoSync || isSensitiveClipExcluded(ctx)) && !forceOverride && SmartClipboard.isSensitiveOrOtp(text)) {
            if (cb != null && !autoSync) {
                mainHandler.post(() -> cb.onMessage(false,
                        "🛡️ Blocked sensitive/OTP content from clipboard sync (disable exclusion to override)"));
            }
            return;
        }
        // Duplicate sync prevention
        if (autoSync && SmartClipboard.isDuplicateSync(text)) {
            return;
        }

        final String host = getMacHost(ctx);
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/clipboard", "POST", 2500);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                JSONObject body = new JSONObject()
                        .put("enc_v31", encryptV31Payload(ctx, text))
                        .put("auto_sync", autoSync)
                        .put("exclude_sensitive", isSensitiveClipExcluded(ctx))
                        .put("force_override", forceOverride);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                String resp = readResp(conn);
                conn.disconnect();
                if (code == 200) {
                    JSONObject r = new JSONObject(resp);
                    if (r.optBoolean("blocked_sensitive", false)) {
                        if (cb != null && !autoSync) {
                            mainHandler.post(() -> cb.onMessage(false, r.optString("output", "Blocked sensitive clip")));
                        }
                        return;
                    }
                    SmartClipboard.markSynced(ctx, text, "PIXEL → MAC");
                    if (cb != null) {
                        mainHandler.post(() -> cb.onMessage(true,
                                "Pushed encrypted " + r.optString("kind", "clipboard")
                                        + " to Mac M4 (" + host + " • " + classifyHostTransport(host) + ")"));
                    }
                } else if (cb != null) {
                    mainHandler.post(() -> cb.onMessage(false, "Mac HTTP " + code));
                }
            } catch (Exception e) {
                if (cb != null && !autoSync) {
                    mainHandler.post(() -> cb.onMessage(false, "Mac bridge unreachable at " + host + ":8181"));
                }
            }
        }).start();
    }

    public static void pullClipboardFromMac(final Context ctx, final MessageCallback cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/clipboard", "GET", 2500);
                if (conn.getResponseCode() == 200) {
                    JSONObject obj = new JSONObject(readResp(conn));
                    String decoded = obj.optString("text", "");
                    String enc = obj.optString("enc_v31", "");
                    if (!TextUtils.isEmpty(enc)) {
                        String dec = decryptV31Payload(ctx, enc);
                        if (!TextUtils.isEmpty(dec)) {
                            decoded = dec;
                        }
                    }
                    final String clip = decoded;
                    final boolean sens = obj.optBoolean("sensitive", false);
                    conn.disconnect();
                    if (sens && isSensitiveClipExcluded(ctx)) {
                        mainHandler.post(() -> cb.onMessage(false,
                                "🛡️ Mac clipboard contains sensitive/OTP content (excluded by policy)"));
                        return;
                    }
                    mainHandler.post(() -> {
                        SmartClipboard.copyToClipboard(ctx, "MacClipboard", clip);
                        SmartClipboard.markSynced(ctx, clip, "MAC → PIXEL");
                        cb.onMessage(true, "Pulled from Mac M4: " + (clip.length() > 45 ? clip.substring(0, 45) + "…" : clip));
                    });
                } else {
                    conn.disconnect();
                    mainHandler.post(() -> cb.onMessage(false, "Failed to pull clipboard"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> cb.onMessage(false, "Mac bridge unreachable: " + e.getMessage()));
            }
        }).start();
    }

    // =========================================================================
    // PHASE 7: BIDIRECTIONAL WIRELESS FILE TRANSFER (STREAMING + PROGRESS)
    // =========================================================================

    public static void cancelActiveTransfer() {
        cancelTransferFlag.set(true);
    }

    public static void uploadFileToMac(
            final Context ctx,
            final Uri fileUri,
            final String filename,
            final byte[] fallbackBytes,
            final String destFolder,
            final TransferProgressCallback progressCb,
            final MessageCallback doneCb) {
        cancelTransferFlag.set(false);
        new Thread(() -> {
            long totalBytes = 0L;
            InputStream inputStream = null;
            try {
                if (fileUri != null) {
                    try ( android.content.res.AssetFileDescriptor afd =
                                  ctx.getContentResolver().openAssetFileDescriptor(fileUri, "r")) {
                        if (afd != null && afd.getLength() > 0) {
                            totalBytes = afd.getLength();
                        }
                    } catch (Exception ignored) {}
                    inputStream = ctx.getContentResolver().openInputStream(fileUri);
                } else if (fallbackBytes != null) {
                    totalBytes = fallbackBytes.length;
                    inputStream = new ByteArrayInputStream(fallbackBytes);
                }

                if (inputStream == null) {
                    mainHandler.post(() -> doneCb.onMessage(false, "Unable to open source file"));
                    return;
                }

                HttpURLConnection conn = openAuthConn(ctx, "/file/upload", "POST", 15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.setRequestProperty("X-Filename", URLEncoder.encode(filename, "UTF-8"));
                conn.setRequestProperty("X-Dest-Folder", destFolder != null ? destFolder : "PixelDrop");
                if (totalBytes > 0) {
                    conn.setFixedLengthStreamingMode(totalBytes);
                } else {
                    conn.setChunkedStreamingMode(32768);
                }

                long startMs = System.currentTimeMillis();
                long sent = 0L;
                byte[] buf = new byte[16384];
                int read;
                try (OutputStream os = conn.getOutputStream()) {
                    while ((read = inputStream.read(buf)) != -1) {
                        if (cancelTransferFlag.get()) {
                            conn.disconnect();
                            inputStream.close();
                            mainHandler.post(() -> doneCb.onMessage(false, "⏹ File transfer cancelled by user"));
                            return;
                        }
                        os.write(buf, 0, read);
                        sent += read;
                        long elapsedMs = Math.max(1L, System.currentTimeMillis() - startMs);
                        final double speedKbps = (sent / 1024.0) / (elapsedMs / 1000.0);
                        final int pct = totalBytes > 0 ? (int) ((sent * 100L) / totalBytes) : 50;
                        final long curSent = sent;
                        final long tot = totalBytes;
                        if (progressCb != null) {
                            mainHandler.post(() -> progressCb.onProgress(pct, curSent, tot, speedKbps));
                        }
                    }
                    os.flush();
                }
                inputStream.close();

                int code = conn.getResponseCode();
                String resp = readResp(conn);
                conn.disconnect();
                if (code == 200) {
                    JSONObject r = new JSONObject(resp);
                    final String savedTo = r.optString("saved_to", "~/Downloads/PixelDrop/" + filename);
                    final double kbps = r.optDouble("speed_kbps", 0.0);
                    final long finalSent = r.optLong("size_bytes", sent);
                    postTransferNotification(ctx, "Sent to MacBook Air M4",
                            filename + " (" + formatSize(sent) + " @ " + String.format(Locale.US, "%.1f KB/s", kbps) + ")");
                    mainHandler.post(() -> doneCb.onMessage(true,
                            "✓ Sent " + filename + " (" + formatSize(finalSent)
                                    + " @ " + String.format(Locale.US, "%.1f KB/s", kbps) + ") → " + savedTo));
                } else {
                    mainHandler.post(() -> doneCb.onMessage(false, "Upload failed: HTTP " + code));
                }
            } catch (Exception e) {
                mainHandler.post(() -> doneCb.onMessage(false, "Transfer error: " + e.getMessage()));
            }
        }).start();
    }

    public static void downloadFileFromMac(
            final Context ctx,
            final String filename,
            final TransferProgressCallback progressCb,
            final MessageCallback doneCb) {
        cancelTransferFlag.set(false);
        new Thread(() -> {
            try {
                String q = "/file/download?name=" + URLEncoder.encode(filename, "UTF-8");
                HttpURLConnection conn = openAuthConn(ctx, q, "GET", 15000);
                if (conn.getResponseCode() != 200) {
                    conn.disconnect();
                    mainHandler.post(() -> doneCb.onMessage(false, "File not found on Mac: " + filename));
                    return;
                }
                final long totalBytes = conn.getContentLengthLong();
                File dlDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "AayushOS");
                if (!dlDir.exists()) dlDir.mkdirs();
                final File outFile = new File(dlDir, filename);

                long startMs = System.currentTimeMillis();
                long received = 0L;
                byte[] buf = new byte[16384];
                int read;
                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    while ((read = is.read(buf)) != -1) {
                        if (cancelTransferFlag.get()) {
                            conn.disconnect();
                            outFile.delete();
                            mainHandler.post(() -> doneCb.onMessage(false, "⏹ Download cancelled by user"));
                            return;
                        }
                        fos.write(buf, 0, read);
                        received += read;
                        long elapsedMs = Math.max(1L, System.currentTimeMillis() - startMs);
                        final double speedKbps = (received / 1024.0) / (elapsedMs / 1000.0);
                        final int pct = totalBytes > 0 ? (int) ((received * 100L) / totalBytes) : 50;
                        final long curRec = received;
                        if (progressCb != null) {
                            mainHandler.post(() -> progressCb.onProgress(pct, curRec, totalBytes, speedKbps));
                        }
                    }
                }
                conn.disconnect();
                long elapsedMs = Math.max(1L, System.currentTimeMillis() - startMs);
                final double finalKbps = (received / 1024.0) / (elapsedMs / 1000.0);
                final long finalRec = received;
                postTransferNotification(ctx, "Received from MacBook Air M4",
                        filename + " (" + formatSize(finalRec) + ") saved to Downloads/AayushOS");
                mainHandler.post(() -> doneCb.onMessage(true,
                        "✓ Downloaded " + filename + " (" + formatSize(finalRec)
                                + " @ " + String.format(Locale.US, "%.1f KB/s", finalKbps)
                                + ") → Downloads/AayushOS/"));
            } catch (Exception e) {
                mainHandler.post(() -> doneCb.onMessage(false, "Download error: " + e.getMessage()));
            }
        }).start();
    }

    public static void fetchMacOutboxAndHistory(final Context ctx, final OutboxCallback cb) {
        new Thread(() -> {
            List<RemoteFileItem> files = new ArrayList<>();
            List<TransferHistoryItem> history = new ArrayList<>();
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/file/outbox", "GET", 2500);
                if (conn.getResponseCode() == 200) {
                    JSONObject obj = new JSONObject(readResp(conn));
                    JSONArray fArr = obj.optJSONArray("files");
                    if (fArr != null) {
                        for (int i = 0; i < fArr.length(); i++) {
                            JSONObject f = fArr.optJSONObject(i);
                            if (f != null) {
                                RemoteFileItem item = new RemoteFileItem();
                                item.name = f.optString("name", "");
                                item.sizeBytes = f.optLong("size", 0L);
                                files.add(item);
                            }
                        }
                    }
                    JSONArray hArr = obj.optJSONArray("history");
                    if (hArr != null) {
                        for (int i = 0; i < hArr.length(); i++) {
                            JSONObject h = hArr.optJSONObject(i);
                            if (h != null) {
                                TransferHistoryItem t = new TransferHistoryItem();
                                t.direction = h.optString("direction", "");
                                t.filename = h.optString("filename", "");
                                t.sizeBytes = h.optLong("size_bytes", 0L);
                                t.speedKbps = h.optDouble("speed_kbps", 0.0);
                                t.dest = h.optString("dest", "");
                                t.status = h.optString("status", "COMPLETED");
                                history.add(t);
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
            mainHandler.post(() -> cb.onOutbox(files, history));
        }).start();
    }

    public static void dropFileOrNoteToMac(final Context ctx, final String filename, final String content, final MessageCallback cb) {
        byte[] bytes = (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
        uploadFileToMac(ctx, null, filename, bytes, "PixelDrop", null, cb);
    }

    // =========================================================================
    // PHASE 8: MAC NOTIFICATION CENTRE
    // =========================================================================

    public static void forwardNotificationToMac(
            final Context ctx,
            final String key,
            final String appName,
            final String packageName,
            final String title,
            final String body,
            final String extractedUrl) {
        if (!shouldForwardNotificationPackage(ctx, packageName) || isManuallyDisconnected(ctx)) {
            return;
        }
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/notifications", "POST", 2200);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject inner = new JSONObject()
                        .put("key", key)
                        .put("app_name", appName)
                        .put("package_name", packageName)
                        .put("title", title)
                        .put("body", body)
                        .put("url", extractedUrl != null ? extractedUrl : "");
                String enc = encryptV31Payload(ctx, inner.toString());
                JSONObject payload = new JSONObject();
                if (!TextUtils.isEmpty(enc)) {
                    payload.put("enc_v31", enc);
                } else {
                    payload = inner;
                }
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    public static void fetchMacNotifications(final Context ctx, final NotificationsCallback cb) {
        new Thread(() -> {
            List<MacNotifItem> list = new ArrayList<>();
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/notifications", "GET", 2500);
                if (conn.getResponseCode() == 200) {
                    JSONObject obj = new JSONObject(readResp(conn));
                    JSONArray arr = obj.optJSONArray("notifications");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject n = arr.optJSONObject(i);
                            if (n != null) {
                                MacNotifItem item = new MacNotifItem();
                                item.key = n.optString("key", "");
                                item.app = n.optString("app", "");
                                item.title = n.optString("title", "");
                                item.body = n.optString("body", "");
                                item.url = n.optString("url", "");
                                item.timestamp = n.optLong("timestamp", 0L);
                                list.add(item);
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
            mainHandler.post(() -> cb.onNotifications(list));
        }).start();
    }

    public static void sendNotificationAction(
            final Context ctx,
            final String action,
            final String key,
            final String text,
            final String url,
            final MessageCallback cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = openAuthConn(ctx, "/notifications/action", "POST", 2500);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject body = new JSONObject()
                        .put("action", action)
                        .put("key", key != null ? key : "")
                        .put("text", text != null ? text : "")
                        .put("url", url != null ? url : "");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                String resp = readResp(conn);
                conn.disconnect();
                JSONObject r = new JSONObject(resp);
                mainHandler.post(() -> cb.onMessage(code == 200, r.optString("output", "Done")));
            } catch (Exception e) {
                mainHandler.post(() -> cb.onMessage(false, "Error: " + e.getMessage()));
            }
        }).start();
    }

    // =========================================================================
    // V3.1 DYNAMIC MULTI-LAYER DISCOVERY & CLIENT ISOLATION / PORT DIAGNOSTICS
    // =========================================================================

    private static String quickSignedUdpDiscoveryProbe(Context ctx, int timeoutMs) {
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setBroadcast(true);
            sock.setSoTimeout(timeoutMs);

            JSONObject meta = new JSONObject()
                    .put("pixel_ip", lastObservedPixelLanIp)
                    .put("pixel_tailscale_ip", lastObservedPixelTailscaleIp)
                    .put("pixel_port", PixelCompanionServer.PORT);
            byte[] probe = ("AAYUSH_OS_DISCOVER|" + meta.toString()).getBytes(StandardCharsets.UTF_8);

            // Send to both the Subnet Directed Broadcast (e.g. 192.168.1.255 / 10.x.y.255)
            // and 255.255.255.255 because many office routers filter 255.255.255.255
            if (!TextUtils.isEmpty(lastObservedBroadcastIp) && !"255.255.255.255".equals(lastObservedBroadcastIp)) {
                try {
                    DatagramPacket subPkt = new DatagramPacket(
                            probe, probe.length, InetAddress.getByName(lastObservedBroadcastIp), 8182);
                    sock.send(subPkt);
                } catch (Exception ignored) {}
            }
            DatagramPacket globalPkt = new DatagramPacket(
                    probe, probe.length, InetAddress.getByName("255.255.255.255"), 8182);
            sock.send(globalPkt);

            byte[] buf = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            sock.receive(reply);
            String rawReply = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8);
            String senderIp = reply.getAddress().getHostAddress();

            if (rawReply.contains("|")) {
                String jsonPart = rawReply.substring(rawReply.indexOf('|') + 1);
                JSONObject beacon = new JSONObject(jsonPart);
                String lanIp = beacon.optString("lan_ip", senderIp);
                int port = beacon.optInt("port", 8181);
                long ts = beacon.optLong("ts", 0L);
                String sig = beacon.optString("sig", "");
                String expectedSig = computeHmacSha256(getAuthToken(ctx), lanIp + ":" + port + ":" + ts);
                if (!TextUtils.isEmpty(sig) && !sig.equalsIgnoreCase(expectedSig)) {
                    // Reject forged UDP beacon whose HMAC-SHA256 does not match our shared token
                    return "";
                }
                String tsIp = beacon.optString("tailscale_ip", "");
                if (!TextUtils.isEmpty(tsIp)) {
                    setMacTailscaleHost(ctx, tsIp);
                }
                String mdns = beacon.optString("mdns_host", "");
                if (!TextUtils.isEmpty(mdns)) {
                    setMacMdnsHost(ctx, mdns);
                }
                return !TextUtils.isEmpty(lanIp) ? lanIp : senderIp;
            }
            return senderIp;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String discoverMacViaNsd(Context ctx, int timeoutMs) {
        final AtomicReference<String> foundHost = new AtomicReference<>("");
        final CountDownLatch latch = new CountDownLatch(1);
        NsdManager nsd = null;
        NsdManager.DiscoveryListener discListener = null;
        try {
            nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
            if (nsd != null) {
                final NsdManager finalNsd = nsd;
                discListener = new NsdManager.DiscoveryListener() {
                    @Override public void onStartDiscoveryFailed(String t, int e) { latch.countDown(); }
                    @Override public void onStopDiscoveryFailed(String t, int e) {}
                    @Override public void onDiscoveryStarted(String t) {}
                    @Override public void onDiscoveryStopped(String t) {}
                    @Override public void onServiceLost(NsdServiceInfo s) {}
                    @Override
                    public void onServiceFound(NsdServiceInfo service) {
                        if (service == null) return;
                        try {
                            finalNsd.resolveService(service, new NsdManager.ResolveListener() {
                                @Override public void onResolveFailed(NsdServiceInfo s, int e) {}
                                @Override
                                public void onServiceResolved(NsdServiceInfo resolved) {
                                    if (resolved != null && resolved.getHost() != null) {
                                        String ip = resolved.getHost().getHostAddress();
                                        if (!TextUtils.isEmpty(ip)) {
                                            foundHost.set(ip);
                                            latch.countDown();
                                        }
                                    }
                                }
                            });
                        } catch (Exception ignored) {}
                    }
                };
                nsd.discoverServices("_aayush-os._tcp.", NsdManager.PROTOCOL_DNS_SD, discListener);
                latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {
        } finally {
            if (nsd != null && discListener != null) {
                try { nsd.stopServiceDiscovery(discListener); } catch (Exception ignored) {}
            }
        }
        if (!TextUtils.isEmpty(foundHost.get())) {
            return foundHost.get();
        }
        // Fallback: Direct .local mDNS hostname resolution
        try {
            String mdnsName = getMacMdnsHost(ctx);
            InetAddress addr = InetAddress.getByName(mdnsName);
            if (addr != null && !TextUtils.isEmpty(addr.getHostAddress())) {
                return addr.getHostAddress();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String discoverMacViaArpTable(Context ctx) {
        try (BufferedReader br = new BufferedReader(new java.io.FileReader("/proc/net/arp"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4 && parts[0].contains(".") && !"00:00:00:00:00:00".equals(parts[3])) {
                    String candidateIp = parts[0];
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(candidateIp, 8181), 450);
                        return candidateIp;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static void discoverMacOnLan(final Context ctx, final MessageCallback cb) {
        if (!discoveryInFlight.compareAndSet(false, true)) {
            return;
        }
        setManuallyDisconnected(ctx, false);
        new Thread(() -> {
            try {
                // Stage 1: Signed UDP Broadcast (Subnet Directed + Global :8182)
                String foundIp = quickSignedUdpDiscoveryProbe(ctx, 1400);
                String sourceLabel = "Signed UDP Beacon (:8182)";

                // Stage 2: mDNS / Bonjour (_aayush-os._tcp & Aayushs-MacBook-Air.local)
                if (TextUtils.isEmpty(foundIp)) {
                    foundIp = discoverMacViaNsd(ctx, 1600);
                    sourceLabel = "mDNS Bonjour (_aayush-os._tcp)";
                }

                // Stage 2b: ARP / Hotspot Connected Peer Probe (:8181)
                if (TextUtils.isEmpty(foundIp)) {
                    foundIp = discoverMacViaArpTable(ctx);
                    if (!TextUtils.isEmpty(foundIp)) {
                        sourceLabel = "ARP / Hotspot Peer Table (:8181)";
                    }
                }

                // Stage 3: Verify candidate via authenticated HTTP /status or fallback to Tailscale VPN
                if (!TextUtils.isEmpty(foundIp)) {
                    MacStatus testSt = new MacStatus();
                    if (tryFetchStatusFromHost(ctx, foundIp, testSt, 1800)) {
                        setMacHost(ctx, testSt.hostIp);
                        testSt.discoverySource = sourceLabel;
                        lastKnownStatus = testSt;
                        final String msg = "✓ Discovered Mac M4 at " + testSt.hostIp + ":8181 via " + sourceLabel
                                + " (" + testSt.transportMode + " • " + testSt.latencyMs + "ms)";
                        mainHandler.post(() -> cb.onMessage(true, msg));
                        return;
                    }
                }

                // Stage 4: Fallback to fetchMacStatus (which probes Cached LAN IP -> Tailscale VPN -> mDNS)
                fetchMacStatus(ctx, st -> {
                    if (st.online) {
                        cb.onMessage(true, String.format(Locale.US,
                                "✓ Connected to %s at %s via %s (%d ms)",
                                st.computerName, st.hostIp, st.transportMode, st.latencyMs));
                    } else {
                        cb.onMessage(false,
                                "⚠️ Mac unreachable on Direct LAN & UDP :8182. Run Path Audit or enable Tailscale VPN.");
                    }
                });
            } finally {
                discoveryInFlight.set(false);
            }
        }).start();
    }

    /**
     * V3.1 Network Path, Client Isolation, Blocked Port & UDP Failure Diagnostic Engine.
     * Tests Pixel -> Mac Direct LAN, UDP Broadcast vs Unicast :8182, mDNS .local,
     * Tailscale VPN fallback, and ADB vs HTTP Companion separation.
     */
    public static void runNetworkPathAudit(final Context ctx, final MessageCallback cb) {
        new Thread(() -> {
            StringBuilder rep = new StringBuilder();
            HardwareMonitor.Snapshot px = new HardwareMonitor(ctx).sample();
            String configuredHost = getMacHost(ctx);
            String tsHost = getMacTailscaleHost(ctx);
            String mdnsHost = getMacMdnsHost(ctx);

            rep.append("=== AAYUSH OS V3.1 // CROSS-NETWORK PATH AUDIT ===\n");
            rep.append(String.format(Locale.US,
                    "PIXEL WI-FI IP : %s (Broadcast: %s)\n", px.wifiIp, px.subnetBroadcastIp));
            rep.append(String.format(Locale.US,
                    "PIXEL VPN/TS   : %s (Tailscale App: %s)\n",
                    !TextUtils.isEmpty(px.tailscaleIp) ? ("ACTIVE • " + px.tailscaleIp) : (px.vpnActive ? "VPN TUNNEL UP" : "INACTIVE"),
                    px.tailscaleInstalled ? "INSTALLED" : "NOT INSTALLED"));

            // 1. Test UDP Broadcast (:8182) vs Unicast (:8182)
            String bcastBeaconIp = quickSignedUdpDiscoveryProbe(ctx, 1100);
            boolean udpBroadcastOk = !TextUtils.isEmpty(bcastBeaconIp);
            boolean udpUnicastOk = false;
            if (!udpBroadcastOk && !TextUtils.isEmpty(configuredHost)) {
                try (DatagramSocket usock = new DatagramSocket()) {
                    usock.setSoTimeout(900);
                    byte[] p = "AAYUSH_OS_PING".getBytes(StandardCharsets.UTF_8);
                    usock.send(new DatagramPacket(p, p.length, InetAddress.getByName(configuredHost), 8182));
                    byte[] rbuf = new byte[512];
                    DatagramPacket rpkt = new DatagramPacket(rbuf, rbuf.length);
                    usock.receive(rpkt);
                    udpUnicastOk = rpkt.getLength() > 0;
                } catch (Exception ignored) {}
            }

            // 2. Test mDNS (.local) resolution
            String mdnsResolved = "";
            try {
                InetAddress ma = InetAddress.getByName(mdnsHost);
                if (ma != null && ma.getHostAddress() != null) {
                    mdnsResolved = ma.getHostAddress();
                }
            } catch (Exception ignored) {}

            // 3. Test Direct LAN TCP :8181
            String lanTarget = !TextUtils.isEmpty(bcastBeaconIp) ? bcastBeaconIp
                    : (!TextUtils.isEmpty(mdnsResolved) ? mdnsResolved : configuredHost);
            long lanStart = System.currentTimeMillis();
            boolean lanTcpOk = false;
            String lanTcpState;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(lanTarget, 8181), 1200);
                lanTcpOk = true;
                lanTcpState = "OPEN (" + (System.currentTimeMillis() - lanStart) + " ms)";
            } catch (ConnectException ce) {
                lanTcpState = "PORT :8181 REFUSED (Host reachable, bridge stopped or port blocked)";
            } catch (SocketTimeoutException ste) {
                lanTcpState = "TIMEOUT (Filtered / Client Isolation / Host Unreachable)";
            } catch (Exception e) {
                lanTcpState = "UNREACHABLE (" + e.getClass().getSimpleName() + ")";
            }

            // 4. Test Tailscale VPN TCP :8181 (if known)
            boolean vpnTcpOk = false;
            String vpnTcpState = TextUtils.isEmpty(tsHost) ? "NOT CONFIGURED (Auto-learns when Mac Tailscale is ON)" : "UNREACHABLE";
            if (!TextUtils.isEmpty(tsHost)) {
                long vpnStart = System.currentTimeMillis();
                try (Socket vs = new Socket()) {
                    vs.connect(new InetSocketAddress(tsHost, 8181), 1500);
                    vpnTcpOk = true;
                    vpnTcpState = "OPEN (" + (System.currentTimeMillis() - vpnStart) + " ms)";
                } catch (Exception e) {
                    vpnTcpState = "UNREACHABLE (" + e.getClass().getSimpleName() + ")";
                }
            }

            // 5. Diagnose UDP Broadcast Filtering, Port Blocking, and AP Client Isolation
            String udpVerdict;
            if (udpBroadcastOk) {
                udpVerdict = "OK • Signed Beacon from " + bcastBeaconIp + ":8181";
            } else if (udpUnicastOk || lanTcpOk) {
                udpVerdict = "⚠️ UDP BROADCAST FILTERED BY AP (Router drops broadcast :8182; Direct TCP :8181 works)";
            } else {
                udpVerdict = "❌ UDP :8182 NO REPLY (Blocked by AP Isolation, Firewall, or Different Subnet)";
            }

            String isolationVerdict;
            if (lanTcpOk) {
                isolationVerdict = "🟢 DIRECT LAN OK (No Client Isolation on current Wi-Fi)";
            } else if (vpnTcpOk && px.wifiEnabled) {
                isolationVerdict = "🟣 AP CLIENT ISOLATION DETECTED! Direct LAN blocked by Wi-Fi AP; Tailscale VPN active & working.";
            } else if (lanTcpState.startsWith("PORT :8181 REFUSED")) {
                isolationVerdict = "🟠 TCP PORT :8181 REFUSED (Mac is reachable on LAN, but port :8181 is closed/blocked)";
            } else if (!px.wifiEnabled || "0.0.0.0".equals(px.wifiIp)) {
                isolationVerdict = "⚪ PIXEL WI-FI DISCONNECTED (Use Tailscale over Mobile Data or enable Wi-Fi)";
            } else {
                isolationVerdict = "🔴 DIRECT LAN BLOCKED / ISOLATED (Possible Office AP Client Isolation or Different VLAN — Enable Tailscale VPN)";
            }

            String activeTransport = lanTcpOk ? "DIRECT LAN" : (vpnTcpOk ? "TAILSCALE VPN" : "OFFLINE");

            rep.append(String.format(Locale.US, "UDP :8182 DISC : %s\n", udpVerdict));
            rep.append(String.format(Locale.US, "mDNS BONJOUR   : %s -> %s\n",
                    mdnsHost, !TextUtils.isEmpty(mdnsResolved) ? mdnsResolved : "UNRESOLVED"));
            rep.append(String.format(Locale.US, "DIRECT LAN 8181: %s -> %s\n", lanTarget, lanTcpState));
            rep.append(String.format(Locale.US, "TAILSCALE 8181 : %s -> %s\n",
                    TextUtils.isEmpty(tsHost) ? "(none)" : tsHost, vpnTcpState));
            rep.append(String.format(Locale.US, "ISOLATION DIAG : %s\n", isolationVerdict));
            rep.append(String.format(Locale.US, "ACTIVE MODE    : %s (ENC_V31 + HMAC-SHA256 Auth)\n", activeTransport));
            rep.append("HTTP vs ADB    : HTTP Companion (Controls/OTP/Clip/Files) = ZERO ADB REQUIRED.\n");
            rep.append("                 scrcpy Mirroring & Wireless Camera = Requires Separate ADB (:5555/USB).\n");
            rep.append("OFFICE WI-FI   : Architecture ready • Live Office Wi-Fi verification PENDING.");

            final boolean anyOk = lanTcpOk || vpnTcpOk;
            final String out = rep.toString();
            mainHandler.post(() -> cb.onMessage(anyOk, out));
        }).start();
    }

    public static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private static void postTransferNotification(Context ctx, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String chId = "aayush_os_transfers";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                        chId, "Aayush OS File Transfers", NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(ch);
            }
            android.app.Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new android.app.Notification.Builder(ctx, chId)
                    : new android.app.Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_aayush_os)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true);
            nm.notify((int) (System.currentTimeMillis() % 10000) + 9000, b.build());
        } catch (Exception ignored) {}
    }

    private static String readResp(HttpURLConnection conn) throws Exception {
        InputStream is = (conn.getResponseCode() >= 400) ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return "{}";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}
