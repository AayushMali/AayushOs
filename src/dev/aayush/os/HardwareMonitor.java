package dev.aayush.os;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Module 5: Real-Time Hardware & Thermal Telemetry Engine for Pixel 7 ('panther').
 * Event-driven & zero background polling when screen is off.
 */
public final class HardwareMonitor {

    public static final class Snapshot {
        public int batteryPct;
        public boolean isCharging;
        public String chargePlugType;
        public double currentMa;
        public int voltageMv;
        public double powerWatts;
        public double batteryTempC;
        public int chargeCounterMah;
        public String batteryHealth;

        // Tensor G2 8-core frequencies in MHz
        public int[] coreCurMhz = new int[8];
        public int[] coreMaxMhz = new int[8];
        public int avgCpuLoadPct;
        public String thermalState;
        public int thermalStatusLevel;

        // Memory & Storage
        public double ramUsedGb;
        public double ramTotalGb;
        public int ramUsedPct;
        public double zramUsedMb;
        public double zramTotalMb;
        public double storageUsedGb;
        public double storageTotalGb;
        public int storageUsedPct;

        // Live Network Speed & Connectivity (V3.1 Network Independence)
        public double rxKbps;
        public double txKbps;
        public long uptimeMinutes;
        public boolean wifiEnabled;
        public String wifiIp = "0.0.0.0";
        public String subnetBroadcastIp = "255.255.255.255";
        public String tailscaleIp = "";
        public boolean vpnActive = false;
        public boolean tailscaleInstalled = false;
        public boolean bluetoothEnabled;
        public boolean screenInteractive = true;
    }

    private final Context ctx;
    private long lastNetTimestampMs = 0;
    private long lastRxBytes = 0;
    private long lastTxBytes = 0;

    // Static hardware cache: Max CPU frequencies never change at runtime
    private static final int[] CACHED_CORE_MAX_MHZ = new int[8];
    private static volatile boolean maxFreqsInitialized = false;
    private static volatile boolean cachedTailscaleInstalled = false;
    private static long lastTailscaleCheckMs = 0;
    private static String lastObservedLanIp = "";
    private static String lastObservedTailscaleIp = "";

    // Centralized snapshot cache: prevents uncoordinated redundant sampling between Activity & Service (NB-PERF-07)
    private static volatile Snapshot lastCachedSnapshot = null;
    private static volatile long lastSampleUptimeMs = 0L;

    // Network interface caching: prevents periodic socket ioctl & interface allocation churn (NB-PERF-04)
    private static volatile String cachedWifiIp = "0.0.0.0";
    private static volatile String cachedSubnetBroadcastIp = "255.255.255.255";
    private static volatile String cachedTailscaleIp = "";
    private static volatile boolean cachedVpnActive = false;
    private static volatile boolean cachedWifiEnabled = false;
    private static volatile long lastNetworkInspectWallMs = 0L;

    public static void invalidateNetworkCache() {
        lastNetworkInspectWallMs = 0L;
    }

    private static void ensureMaxFreqsCached() {
        if (maxFreqsInitialized) return;
        for (int i = 0; i < 8; i++) {
            int maxKhz = readSysInt("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (maxKhz > 0) {
                CACHED_CORE_MAX_MHZ[i] = maxKhz / 1000;
            } else {
                CACHED_CORE_MAX_MHZ[i] = (i < 4) ? 1803 : (i < 6 ? 2348 : 2850);
            }
        }
        maxFreqsInitialized = true;
    }

    public HardwareMonitor(Context context) {
        this.ctx = context.getApplicationContext();
    }

    public Snapshot sample() {
        long nowUptime = SystemClock.uptimeMillis();
        Snapshot cached = lastCachedSnapshot;
        if (cached != null && (nowUptime - lastSampleUptimeMs < 750L)) {
            return cached;
        }

        Snapshot s = new Snapshot();

        // 1. Battery & Charging Telemetry
        Intent battIntent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);

        if (battIntent != null) {
            int level = battIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            s.batteryPct = scale > 0 ? Math.round((level * 100f) / scale) : 0;

            int status = battIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            s.isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL);

            int plugged = battIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            if (plugged == BatteryManager.BATTERY_PLUGGED_AC) s.chargePlugType = "AC PD";
            else if (plugged == BatteryManager.BATTERY_PLUGGED_USB) s.chargePlugType = "USB";
            else if (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) s.chargePlugType = "QI";
            else s.chargePlugType = "BAT";

            s.voltageMv = battIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            int tempTenths = battIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            s.batteryTempC = tempTenths / 10.0;

            int health = battIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD);
            s.batteryHealth = (health == BatteryManager.BATTERY_HEALTH_GOOD) ? "GOOD"
                    : (health == BatteryManager.BATTERY_HEALTH_OVERHEAT) ? "OVERHEAT" : "DEGRADED";
        }

        if (bm != null) {
            long currentUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            // On Pixel 7 ('panther'), current_now is in microamperes (uA)
            if (Math.abs(currentUa) > 50000) {
                s.currentMa = currentUa / 1000.0;
            } else {
                s.currentMa = currentUa;
            }
            s.powerWatts = (Math.abs(s.currentMa) * s.voltageMv) / 1_000_000.0;

            long chargeCounterUaH = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (chargeCounterUaH > 0) {
                s.chargeCounterMah = (int) (chargeCounterUaH / 1000);
            }
        }

        // 2. Tensor G2 8-Core CPU Frequencies (4x A510, 2x A78, 2x X1)
        ensureMaxFreqsCached();
        int validCores = 0;
        int totalRatioPct = 0;
        for (int i = 0; i < 8; i++) {
            s.coreMaxMhz[i] = CACHED_CORE_MAX_MHZ[i];
            int curKhz = readSysInt("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (curKhz > 0) {
                s.coreCurMhz[i] = curKhz / 1000;
                totalRatioPct += Math.min(100, (curKhz * 100) / Math.max(1, s.coreMaxMhz[i] * 1000));
                validCores++;
            } else {
                s.coreCurMhz[i] = (i < 4) ? 600 : (i < 6 ? 800 : 900);
            }
        }
        s.avgCpuLoadPct = validCores > 0 ? (totalRatioPct / validCores) : 18;

        // 3. Thermal Status via PowerManager
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            s.thermalStatusLevel = pm.getCurrentThermalStatus();
            switch (s.thermalStatusLevel) {
                case PowerManager.THERMAL_STATUS_NONE: s.thermalState = "NOMINAL"; break;
                case PowerManager.THERMAL_STATUS_LIGHT: s.thermalState = "WARM (LIGHT)"; break;
                case PowerManager.THERMAL_STATUS_MODERATE: s.thermalState = "MODERATE"; break;
                case PowerManager.THERMAL_STATUS_SEVERE: s.thermalState = "THROTTLING"; break;
                default: s.thermalState = "CRITICAL"; break;
            }
        } else {
            s.thermalState = "NOMINAL";
        }

        // 4. RAM & ZRAM Swap (/proc/meminfo + ActivityManager)
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            s.ramTotalGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0);
            double availGb = mi.availMem / (1024.0 * 1024.0 * 1024.0);
            s.ramUsedGb = Math.max(0, s.ramTotalGb - availGb);
            s.ramUsedPct = s.ramTotalGb > 0 ? (int) Math.round((s.ramUsedGb * 100.0) / s.ramTotalGb) : 0;
        }
        readZramFromMeminfo(s);

        // 5. Internal Storage (/data)
        try {
            File dataDir = Environment.getDataDirectory();
            StatFs stat = new StatFs(dataDir.getPath());
            long totalBytes = stat.getTotalBytes();
            long availBytes = stat.getAvailableBytes();
            s.storageTotalGb = totalBytes / (1024.0 * 1024.0 * 1024.0);
            s.storageUsedGb = (totalBytes - availBytes) / (1024.0 * 1024.0 * 1024.0);
            s.storageUsedPct = totalBytes > 0 ? (int) Math.round(((totalBytes - availBytes) * 100.0) / totalBytes) : 0;
        } catch (Exception ignored) {}

        // 6. Live Network Throughput via TrafficStats
        long nowMs = SystemClock.elapsedRealtime();
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (lastNetTimestampMs > 0 && nowMs > lastNetTimestampMs && rx >= lastRxBytes && tx >= lastTxBytes) {
            double dtSec = (nowMs - lastNetTimestampMs) / 1000.0;
            s.rxKbps = ((rx - lastRxBytes) / 1024.0) / dtSec;
            s.txKbps = ((tx - lastTxBytes) / 1024.0) / dtSec;
        }
        lastNetTimestampMs = nowMs;
        lastRxBytes = rx;
        lastTxBytes = tx;

        s.uptimeMinutes = SystemClock.elapsedRealtime() / 60000L;

        // 7. Wi-Fi, VPN/Tailscale, Bluetooth & Screen State (for Cross-Device Command Centre)
        if (pm != null) {
            s.screenInteractive = pm.isInteractive();
        }
        long nowWallMs = System.currentTimeMillis();
        if (nowWallMs - lastNetworkInspectWallMs > 15000L || "0.0.0.0".equals(cachedWifiIp)) {
            lastNetworkInspectWallMs = nowWallMs;
            try {
                WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    cachedWifiEnabled = wm.isWifiEnabled();
                    WifiInfo wi = wm.getConnectionInfo();
                    if (wi != null && wi.getIpAddress() != 0) {
                        int ip = wi.getIpAddress();
                        cachedWifiIp = String.format(Locale.US, "%d.%d.%d.%d",
                                (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
                    }
                }
                inspectNetworkInterfaces(s);
                cachedWifiIp = s.wifiIp;
                cachedTailscaleIp = s.tailscaleIp;
                cachedSubnetBroadcastIp = s.subnetBroadcastIp;
                cachedVpnActive = s.vpnActive;
                cachedWifiEnabled = s.wifiEnabled;
            } catch (Exception ignored) {}
        } else {
            s.wifiIp = cachedWifiIp;
            s.tailscaleIp = cachedTailscaleIp;
            s.subnetBroadcastIp = cachedSubnetBroadcastIp;
            s.vpnActive = cachedVpnActive;
            s.wifiEnabled = cachedWifiEnabled;
        }

        if (nowWallMs - lastTailscaleCheckMs > 60000L) {
            lastTailscaleCheckMs = nowWallMs;
            try {
                ctx.getPackageManager().getPackageInfo("com.tailscale.ipn", 0);
                cachedTailscaleInstalled = true;
            } catch (Exception ignored) {
                cachedTailscaleInstalled = false;
            }
        }
        s.tailscaleInstalled = cachedTailscaleInstalled;

        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            s.bluetoothEnabled = (ba != null && ba.isEnabled());
        } catch (Exception ignored) {}

        if (!s.wifiIp.equals(lastObservedLanIp) || !s.tailscaleIp.equals(lastObservedTailscaleIp)) {
            lastObservedLanIp = s.wifiIp;
            lastObservedTailscaleIp = s.tailscaleIp;
            MacCompanion.onNetworkObserved(ctx, s.wifiIp, s.subnetBroadcastIp, s.tailscaleIp);
        }
        lastCachedSnapshot = s;
        lastSampleUptimeMs = nowUptime;
        return s;
    }

    public static boolean isTailscaleIpv4(String ip) {
        if (ip == null || !ip.startsWith("100.")) return false;
        try {
            String[] p = ip.split("\\.");
            if (p.length != 4) return false;
            int second = Integer.parseInt(p[1]);
            return second >= 64 && second <= 127;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void inspectNetworkInterfaces(Snapshot s) {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                String name = nif.getName() != null ? nif.getName().toLowerCase(Locale.US) : "";
                boolean isTun = name.startsWith("tun") || name.startsWith("wg") || name.startsWith("utun");
                boolean isCellular = name.startsWith("rmnet") || name.startsWith("ccmni")
                        || name.startsWith("pdp") || name.startsWith("dummy") || name.startsWith("clat");
                boolean isLanOrHotspot = name.startsWith("wlan") || name.startsWith("ap")
                        || name.startsWith("swlan") || name.startsWith("softap")
                        || name.startsWith("rndis") || name.startsWith("eth");
                if (isTun) {
                    s.vpnActive = true;
                }
                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) continue;
                    String hostAddr = addr.getHostAddress();
                    if (hostAddr == null) continue;
                    if (isTailscaleIpv4(hostAddr)) {
                        s.tailscaleIp = hostAddr;
                        s.vpnActive = true;
                    } else if (!isTun && !isCellular) {
                        if ("0.0.0.0".equals(s.wifiIp) || isLanOrHotspot) {
                            s.wifiIp = hostAddr;
                            if (isLanOrHotspot) {
                                s.wifiEnabled = true;
                            }
                        }
                        if (hostAddr.equals(s.wifiIp) && ia.getBroadcast() != null) {
                            String bcast = ia.getBroadcast().getHostAddress();
                            if (bcast != null && !bcast.isEmpty()) {
                                s.subnetBroadcastIp = bcast;
                            }
                        }
                    }
                }
            }
            if ("255.255.255.255".equals(s.subnetBroadcastIp) && !"0.0.0.0".equals(s.wifiIp)) {
                int lastDot = s.wifiIp.lastIndexOf('.');
                if (lastDot > 0) {
                    s.subnetBroadcastIp = s.wifiIp.substring(0, lastDot) + ".255";
                }
            }
        } catch (Exception ignored) {}
    }

    private static int readSysInt(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            if (line != null) return Integer.parseInt(line.trim());
        } catch (Exception ignored) {}
        return -1;
    }

    private static void readZramFromMeminfo(Snapshot s) {
        long swapTotalKb = 0;
        long swapFreeKb = 0;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) {
                    swapTotalKb = parseMeminfoKb(line);
                } else if (line.startsWith("SwapFree:")) {
                    swapFreeKb = parseMeminfoKb(line);
                }
                if (swapTotalKb > 0 && swapFreeKb > 0) {
                    break;
                }
            }
        } catch (Exception ignored) {}
        s.zramTotalMb = swapTotalKb / 1024.0;
        s.zramUsedMb = Math.max(0, swapTotalKb - swapFreeKb) / 1024.0;
    }

    private static long parseMeminfoKb(String line) {
        if (line == null) return 0;
        int len = line.length();
        int i = 0;
        while (i < len && line.charAt(i) != ':') i++;
        while (i < len && (line.charAt(i) <= ' ' || line.charAt(i) == ':')) i++;
        long val = 0;
        while (i < len && line.charAt(i) >= '0' && line.charAt(i) <= '9') {
            val = val * 10 + (line.charAt(i) - '0');
            i++;
        }
        return val;
    }

    public static String formatSpeed(double kbps) {
        if (kbps >= 1024.0) {
            return String.format(Locale.US, "%.2f MB/s", kbps / 1024.0);
        }
        return String.format(Locale.US, "%.1f KB/s", kbps);
    }
}
