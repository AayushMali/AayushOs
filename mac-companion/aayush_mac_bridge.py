#!/usr/bin/env python3
"""
Aayush OS V3 — MacBook Air M4 Cross-Device Intelligence Daemon
HTTP Server (:8181) + UDP Discovery (:8182) + Mac Desktop Companion UI

Capabilities:
  - Phase 1 : Mac System Control (Wi-Fi ON/OFF, Bluetooth ON/OFF via IOBluetooth,
              Battery/Charging, CPU Usage, Uptime, Current Wi-Fi Network, BT Devices,
              Lock Screen, Caffeinate Toggle, Audio Mute Toggle, Dark Mode Toggle,
              Network Diagnostics, Play Notification Sound).
              * Strict Disconnect Policy: Turning OFF Mac Wi-Fi or Bluetooth executes
                immediately and NEVER auto-restores or overrides user choice.
  - Phase 2 : True Wireless Ecosystem (HTTP :8181 + UDP :8182 + Pixel HTTP :8183 push,
              zero USB/ADB dependency for daily companion operation).
  - Phase 3 : Pixel Desktop Companion via actual `scrcpy` (wireless & USB mirroring,
              resolution/bitrate/FPS/orientation controls, nav keys, text input).
  - Phase 4 : Pixel Camera as Mac Webcam (`scrcpy --video-source=camera` rear/front +
              native Android UVC webcam status inspection).
  - Phase 5 : Aayush OS Mac OTP Inbox (volatile RAM only, auto-expiry sweeper, zero
              disk/log persistence, 1-click copy, optional auto-scrub).
  - Phase 6 : Advanced Bidirectional Clipboard (SHA-256 duplicate prevention,
              sensitive/password/OTP filtering, configurable retention history).
  - Phase 7 : Bidirectional Wireless File Transfer (streaming binary upload/download,
              progress/speed metrics, destination selection, transfer history).
  - Phase 8 : Mac Notification Centre (filtered Pixel notifications, quick copy,
              open links on Mac, remote notification dismissal on Pixel).
  - Phase 11: Security (Local-subnet enforcement, X-Aayush-Token authentication,
              strict command allowlist, zero arbitrary shell execution).
"""
import atexit
import ctypes
import hashlib
import hmac
import ipaddress
import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HTTP_PORT = 8181
UDP_PORT = 8182
PIXEL_DEFAULT_IP = "192.168.0.108"
PIXEL_COMPANION_PORT = 8183
TAILSCALE_NET_V4 = ipaddress.ip_network("100.64.0.0/10")
TAILSCALE_NET_V6 = ipaddress.ip_network("fd7a:115c:a1e0::/48")

HOME_DIR = Path.home()
DROP_DIR = HOME_DIR / "Downloads" / "PixelDrop"
OUTBOX_DIR = DROP_DIR / "Outbox"
DROP_DIR.mkdir(parents=True, exist_ok=True)
OUTBOX_DIR.mkdir(parents=True, exist_ok=True)

SECRET_FILE = HOME_DIR / ".aayush_os_bridge_secret"
DEFAULT_TOKEN = "aayush81-panther-m4-bridge"


def load_or_create_token():
    try:
        if SECRET_FILE.exists():
            tok = SECRET_FILE.read_text(encoding="utf-8").strip()
            if len(tok) >= 12:
                return tok
        SECRET_FILE.write_text(DEFAULT_TOKEN + "\n", encoding="utf-8")
        os.chmod(SECRET_FILE, 0o600)
    except Exception:
        pass
    return DEFAULT_TOKEN


AUTH_TOKEN = load_or_create_token()

# Volatile in-memory state (never persisted to disk for privacy)
STATE_LOCK = threading.Lock()
OTP_COND = threading.Condition(STATE_LOCK)
OTP_SEQ = 0               # Monotonic event counter for instant push updates on /otp
OTP_INBOX = []            # List of dicts: {id, code, app, pkg, title, received_at, expires_at, ttl}
NOTIF_INBOX = []          # List of dicts: {id, key, app, pkg, title, body, url, timestamp}
PENDING_DISMISS_KEYS = [] # Notification keys queued for dismissal on Pixel
CLIP_HISTORY = []         # List of dicts: {text, kind, source, timestamp, expires_at, hash}
TRANSFER_HISTORY = []     # List of dicts: {id, direction, filename, size_bytes, speed_kbps, dest, status, timestamp}
LAST_CLIP_HASH = ""
CLIP_RETENTION_MIN = 30
PIXEL_LAST_SEEN_IP = PIXEL_DEFAULT_IP
PIXEL_TAILSCALE_IP = ""
LAST_ACTIVE_TRANSPORT = "DIRECT LAN"
MDNS_ADVERTISER_PROC = None

# Process handles for scrcpy mirroring & camera stream
SCRCPY_LOCK = threading.Lock()
SCRCPY_PROC = None
SCRCPY_META = {
    "running": False,
    "mode": "wireless",
    "target": f"{PIXEL_DEFAULT_IP}:5555",
    "max_size": 1080,
    "bitrate": "8M",
    "max_fps": 60,
    "orientation": "portrait",
    "started_at": 0,
}

WEBCAM_PROC = None
WEBCAM_META = {
    "running": False,
    "facing": "back",
    "size": "1920x1080",
    "fps": 30,
    "started_at": 0,
}

# Load IOBluetooth framework for instant, native Bluetooth power control on Apple Silicon
try:
    IOBT = ctypes.CDLL("/System/Library/Frameworks/IOBluetooth.framework/IOBluetooth")
    IOBT.IOBluetoothPreferenceGetControllerPowerState.restype = ctypes.c_int
    IOBT.IOBluetoothPreferenceSetControllerPowerState.argtypes = [ctypes.c_int]
except Exception:
    IOBT = None


def run_cmd(args, input_text=None, timeout=4.0):
    """Safe subprocess runner: shell=False always, explicit argv list only."""
    try:
        res = subprocess.run(
            args,
            input=input_text.encode("utf-8") if input_text is not None else None,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
            shell=False,
        )
        out = res.stdout.decode("utf-8", errors="replace").strip()
        err = res.stderr.decode("utf-8", errors="replace").strip()
        return out if out else err
    except Exception as e:
        return f"ERR: {e}"


def is_tailscale_ip(ip_str):
    """Returns True if ip_str belongs to Tailscale CGNAT (100.64.0.0/10) or ULA (fd7a:115c:a1e0::/48)."""
    try:
        ip = ipaddress.ip_address(ip_str)
        if getattr(ip, "ipv4_mapped", None):
            ip = ip.ipv4_mapped
        return (ip in TAILSCALE_NET_V4) or (ip in TAILSCALE_NET_V6)
    except Exception:
        return False


def is_allowed_lan_ip(ip_str):
    """
    Phase 11 & V3.1 Security: Allow loopback, RFC1918 private LAN, link-local,
    and authenticated Tailscale private mesh VPN (100.64.0.0/10). Reject public internet.
    """
    try:
        ip = ipaddress.ip_address(ip_str)
        if getattr(ip, "ipv4_mapped", None):
            ip = ip.ipv4_mapped
        return ip.is_loopback or ip.is_private or ip.is_link_local or is_tailscale_ip(str(ip))
    except Exception:
        return False


def classify_transport(ip_str):
    """Classifies active connection transport into DIRECT LAN, TAILSCALE VPN, or LOCALHOST."""
    try:
        ip = ipaddress.ip_address(ip_str)
        if getattr(ip, "ipv4_mapped", None):
            ip = ip.ipv4_mapped
        if ip.is_loopback:
            return "LOCALHOST"
        if is_tailscale_ip(str(ip)):
            return "TAILSCALE VPN"
        return "DIRECT LAN"
    except Exception:
        return "DIRECT LAN"


def get_all_local_mac_ips():
    """Returns a set of all IPv4 addresses currently assigned to this Mac (en*, utun*, lo0)."""
    ips = {"127.0.0.1", "::1"}
    raw = run_cmd(["/sbin/ifconfig"], timeout=2.0)
    for m in re.finditer(r"\binet\s+(\d+\.\d+\.\d+\.\d+)", raw):
        ips.add(m.group(1))
    return ips


def get_mac_mdns_hostname():
    local_host = run_cmd(["/usr/sbin/scutil", "--get", "LocalHostName"], timeout=1.5).strip()
    if not local_host or local_host.startswith("ERR"):
        local_host = socket.gethostname().split(".")[0]
    return f"{local_host}.local"


def get_tailscale_status():
    """Inspects local Tailscale installation, daemon state, Mac VPN IP, and Pixel VPN peer IP."""
    global PIXEL_TAILSCALE_IP
    ts_bin = shutil.which("tailscale") or "/Applications/Tailscale.app/Contents/MacOS/Tailscale"
    installed = os.path.exists(ts_bin)
    if not installed:
        return {
            "installed": False,
            "running": False,
            "state": "NOT_INSTALLED",
            "mac_ip": "",
            "dns_name": "",
            "pixel_ip": PIXEL_TAILSCALE_IP,
        }
    raw = run_cmd([ts_bin, "status", "--json"], timeout=1.8)
    try:
        obj = json.loads(raw)
        backend_state = obj.get("BackendState", "Stopped")
        running = (backend_state == "Running")
        self_node = obj.get("Self") or {}
        ts_ips = self_node.get("TailscaleIPs") or []
        mac_ts_ip = next((ip for ip in ts_ips if "." in ip), "")
        dns_name = (self_node.get("DNSName") or "").rstrip(".")
        peer_pixel_ip = PIXEL_TAILSCALE_IP
        peers = obj.get("Peer") or {}
        for _, p in peers.items():
            os_name = str(p.get("OS", "")).lower()
            host_name = str(p.get("HostName", "")).lower()
            dns_p = str(p.get("DNSName", "")).lower()
            if os_name == "android" or "pixel" in host_name or "panther" in host_name or "pixel" in dns_p:
                p_ips = p.get("TailscaleIPs") or []
                v4 = next((ip for ip in p_ips if "." in ip), "")
                if v4:
                    peer_pixel_ip = v4
                    PIXEL_TAILSCALE_IP = v4
                    break
        return {
            "installed": True,
            "running": running,
            "state": backend_state,
            "mac_ip": mac_ts_ip,
            "dns_name": dns_name,
            "pixel_ip": peer_pixel_ip,
        }
    except Exception:
        # Also check utun interfaces for 100.64.0.0/10 in case CLI output was non-JSON
        mac_ts_ip = ""
        for ip in get_all_local_mac_ips():
            if is_tailscale_ip(ip):
                mac_ts_ip = ip
                break
        return {
            "installed": True,
            "running": bool(mac_ts_ip),
            "state": "Running" if mac_ts_ip else "Stopped",
            "mac_ip": mac_ts_ip,
            "dns_name": "",
            "pixel_ip": PIXEL_TAILSCALE_IP,
        }


# =============================================================================
# V3.1 CRYPTO: HMAC-SHA256 SIGNATURES & AUTHENTICATED ENCRYPTION (ENC_V31)
# =============================================================================

def compute_hmac_sha256(key_str, message_str):
    return hmac.new(
        key_str.encode("utf-8"),
        message_str.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def _ctr_keystream_xor(data_bytes, enc_key_bytes, iv_bytes):
    out = bytearray(len(data_bytes))
    block_idx = 0
    offset = 0
    while offset < len(data_bytes):
        ks = hashlib.sha256(enc_key_bytes + iv_bytes + block_idx.to_bytes(4, "big")).digest()
        take = min(32, len(data_bytes) - offset)
        for i in range(take):
            out[offset + i] = data_bytes[offset + i] ^ ks[i]
        offset += take
        block_idx += 1
    return bytes(out)


def encrypt_v31_payload(plaintext_str, token=None):
    tok = token or AUTH_TOKEN
    enc_key = hashlib.sha256((tok + ":enc").encode("utf-8")).digest()
    mac_key = hashlib.sha256((tok + ":mac").encode("utf-8")).digest()
    iv = secrets.token_bytes(16)
    pt_bytes = plaintext_str.encode("utf-8")
    ct_bytes = _ctr_keystream_xor(pt_bytes, enc_key, iv)
    tag = hmac.new(mac_key, iv + ct_bytes, hashlib.sha256).hexdigest()
    return f"{iv.hex()}:{ct_bytes.hex()}:{tag}"


def decrypt_v31_payload(enc_v31_str, token=None):
    tok = token or AUTH_TOKEN
    parts = str(enc_v31_str or "").split(":")
    if len(parts) != 3:
        raise ValueError("Invalid enc_v31 envelope format")
    iv = bytes.fromhex(parts[0])
    ct_bytes = bytes.fromhex(parts[1])
    provided_tag = parts[2].lower()
    enc_key = hashlib.sha256((tok + ":enc").encode("utf-8")).digest()
    mac_key = hashlib.sha256((tok + ":mac").encode("utf-8")).digest()
    expected_tag = hmac.new(mac_key, iv + ct_bytes, hashlib.sha256).hexdigest().lower()
    if not hmac.compare_digest(provided_tag, expected_tag):
        raise ValueError("enc_v31 HMAC verification failed")
    pt_bytes = _ctr_keystream_xor(ct_bytes, enc_key, iv)
    return pt_bytes.decode("utf-8", errors="replace")


def cleanup_mdns_advertiser():
    """Kills any active dns-sd subprocesses and stale background processes (ISSUE-SRV-02)."""
    global MDNS_ADVERTISER_PROC
    if MDNS_ADVERTISER_PROC is not None:
        try:
            MDNS_ADVERTISER_PROC.terminate()
            MDNS_ADVERTISER_PROC.wait(timeout=1.0)
        except Exception:
            try:
                MDNS_ADVERTISER_PROC.kill()
            except Exception:
                pass
        MDNS_ADVERTISER_PROC = None
    try:
        subprocess.run(["/usr/bin/pkill", "-f", "dns-sd -R AayushOS"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        pass


def start_mdns_advertiser():
    """Advertises _aayush-os._tcp on port 8181 via macOS native Bonjour dns-sd."""
    global MDNS_ADVERTISER_PROC
    try:
        cleanup_mdns_advertiser()
        mdns_host = get_mac_mdns_hostname()
        MDNS_ADVERTISER_PROC = subprocess.Popen(
            [
                "/usr/bin/dns-sd",
                "-R",
                "AayushOS-Mac-M4",
                "_aayush-os._tcp",
                "local",
                str(HTTP_PORT),
                "version=3.1",
                f"mdns={mdns_host}",
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            shell=False,
        )
    except Exception:
        pass


atexit.register(cleanup_mdns_advertiser)


def notify_mac(title, subtitle, sound=False):
    safe_t = str(title).replace("\\", "\\\\").replace('"', '\\"')[:80]
    safe_s = str(subtitle).replace("\\", "\\\\").replace('"', '\\"')[:160]
    script = f'display notification "{safe_s}" with title "{safe_t}"'
    if sound:
        script += ' sound name "Glass"'
    subprocess.Popen(["/usr/bin/osascript", "-e", script], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


# =============================================================================
# PHASE 1: MACBOOK SYSTEM TELEMETRY & CONTROLS
# =============================================================================

def get_wifi_state():
    pwr_raw = run_cmd(["/usr/sbin/networksetup", "-getairportpower", "en0"])
    power_on = "On" in pwr_raw
    if not power_on:
        return {
            "power": False,
            "connected": False,
            "network": "OFF (Disabled)",
            "ip": "0.0.0.0",
            "router": "",
        }

    info_raw = run_cmd(["/usr/sbin/networksetup", "-getinfo", "Wi-Fi"])
    ip_addr = ""
    router = ""
    for line in info_raw.splitlines():
        if line.startswith("IP address:"):
            ip_addr = line.split(":", 1)[1].strip()
        elif line.startswith("Router:"):
            router = line.split(":", 1)[1].strip()

    summary_raw = run_cmd(["/usr/sbin/ipconfig", "getsummary", "en0"])
    link_active = "LinkStatusActive : TRUE" in summary_raw or bool(ip_addr and ip_addr != "none")
    ssid = ""
    for line in summary_raw.splitlines():
        s = line.strip()
        if s.startswith("SSID :"):
            candidate = s.split(":", 1)[1].strip()
            if candidate and "<redacted>" not in candidate:
                ssid = candidate
                break

    if not ssid and link_active:
        pref_raw = run_cmd(["/usr/sbin/networksetup", "-listpreferredwirelessnetworks", "en0"])
        pref_lines = [l.strip() for l in pref_raw.splitlines()[1:] if l.strip()]
        if pref_lines:
            ssid = pref_lines[0]
        else:
            ssid = "Connected LAN"

    net_label = f"{ssid} ({ip_addr})" if (link_active and ip_addr) else ("Disconnected" if not link_active else ssid)
    return {
        "power": True,
        "connected": link_active,
        "network": net_label,
        "ssid": ssid,
        "ip": ip_addr or "192.168.0.110",
        "router": router,
    }


def set_wifi_power(turn_on):
    """
    Sets Mac Wi-Fi power explicitly.
    CRITICAL: If turned OFF, never schedules any auto-restore or reconnect.
    """
    target = "on" if turn_on else "off"
    run_cmd(["/usr/sbin/networksetup", "-setairportpower", "en0", target])
    time.sleep(0.25)
    return get_wifi_state()


def get_bluetooth_state():
    bt_on = True
    if IOBT is not None:
        try:
            bt_on = bool(IOBT.IOBluetoothPreferenceGetControllerPowerState() == 1)
        except Exception:
            pass

    if not bt_on:
        return {
            "power": False,
            "connected": False,
            "devices": [],
            "status": "OFF (Disabled)",
        }

    sp_raw = run_cmd(["/usr/sbin/system_profiler", "SPBluetoothDataType"], timeout=3.5)
    if "State: Off" in sp_raw and IOBT is None:
        return {
            "power": False,
            "connected": False,
            "devices": [],
            "status": "OFF (Disabled)",
        }

    devices = []
    in_connected = False
    for line in sp_raw.splitlines():
        stripped = line.strip()
        if stripped == "Connected:":
            in_connected = True
            continue
        elif stripped in ("Not Connected:", "Bluetooth Controller:"):
            if in_connected:
                break
        if in_connected and stripped.endswith(":") and not stripped.startswith("Address"):
            # Device name header has 10 spaces indent in system_profiler SPBluetoothDataType
            indent = len(line) - len(line.lstrip(" "))
            if indent <= 12:
                devices.append(stripped[:-1])

    status_str = f"ON • {', '.join(devices)}" if devices else "ON • Standby (No active audio/HID)"
    return {
        "power": True,
        "connected": len(devices) > 0,
        "devices": devices,
        "status": status_str,
    }


def set_bluetooth_power(turn_on):
    """
    Sets Mac Bluetooth power explicitly via IOBluetooth.framework.
    CRITICAL: If turned OFF, keeps it disabled with zero background re-enabling.
    """
    if IOBT is not None:
        try:
            IOBT.IOBluetoothPreferenceSetControllerPowerState(1 if turn_on else 0)
            time.sleep(0.35)
        except Exception:
            pass
    return get_bluetooth_state()


def get_mac_battery():
    raw = run_cmd(["/usr/bin/pmset", "-g", "batt"])
    lines = [l.strip() for l in raw.splitlines() if l.strip()]
    pct = 100
    charging = False
    source = "Battery Power"
    summary = raw
    if lines:
        if "AC Power" in lines[0]:
            source = "AC Power"
        if len(lines) >= 2:
            parts = lines[1].split("\t")
            summary = parts[-1] if parts else lines[1]
            m = re.search(r"(\d+)%", summary)
            if m:
                pct = int(m.group(1))
            low = summary.lower()
            charging = ("charging" in low and "discharging" not in low) or ("charged" in low and source == "AC Power")
    return {
        "summary": summary,
        "pct": pct,
        "charging": charging,
        "source": source,
    }


def get_mac_cpu_usage():
    top_raw = run_cmd(["/usr/bin/top", "-l", "1", "-n", "0"], timeout=3.0)
    user_pct = 0.0
    sys_pct = 0.0
    idle_pct = 100.0
    for line in top_raw.splitlines():
        if "CPU usage:" in line:
            m_u = re.search(r"([\d.]+)%\s*user", line)
            m_s = re.search(r"([\d.]+)%\s*sys", line)
            m_i = re.search(r"([\d.]+)%\s*idle", line)
            if m_u:
                user_pct = float(m_u.group(1))
            if m_s:
                sys_pct = float(m_s.group(1))
            if m_i:
                idle_pct = float(m_i.group(1))
            break
    total_pct = round(max(0.0, min(100.0, user_pct + sys_pct)), 1)
    load_raw = run_cmd(["/usr/sbin/sysctl", "-n", "vm.loadavg"]).strip("{} ")
    load_1m = load_raw.split()[0] if load_raw else "0.00"
    return {
        "total_pct": total_pct,
        "user_pct": round(user_pct, 1),
        "sys_pct": round(sys_pct, 1),
        "idle_pct": round(idle_pct, 1),
        "load_1m": load_1m,
        "formatted": f"{total_pct}% ({user_pct:.1f}% usr, {sys_pct:.1f}% sys) • Load {load_1m}",
    }


def get_mac_uptime():
    raw = run_cmd(["/usr/bin/uptime"])
    # e.g. 23:05  up 2 days,  4:12, 2 users, load averages: 1.42 1.55 1.60
    if " up " in raw:
        after_up = raw.split(" up ", 1)[1]
        parts = after_up.split(",")
        if len(parts) >= 2 and ("day" in parts[0] or "hr" in parts[0] or "min" in parts[0]):
            return f"up {parts[0].strip()}, {parts[1].strip()}"
        return f"up {parts[0].strip()}"
    return raw


def get_caffeinate_state():
    pid = run_cmd(["/usr/bin/pgrep", "-x", "caffeinate"])
    return bool(pid and not pid.startswith("ERR"))


def set_caffeinate_state(enable):
    if enable:
        if not get_caffeinate_state():
            subprocess.Popen(["/usr/bin/caffeinate", "-d", "-i", "-u", "-t", "3600"])
            time.sleep(0.15)
    else:
        run_cmd(["/usr/bin/pkill", "-x", "caffeinate"])
        time.sleep(0.15)
    return get_caffeinate_state()


def get_audio_state():
    raw = run_cmd(["/usr/bin/osascript", "-e", "get volume settings"])
    muted = "output muted:true" in raw
    vol = 50
    m = re.search(r"output volume:(\d+)", raw)
    if m:
        vol = int(m.group(1))
    return {"muted": muted, "volume": vol}


def set_audio_mute(mute):
    val = "true" if mute else "false"
    run_cmd(["/usr/bin/osascript", "-e", f"set volume output muted {val}"])
    return get_audio_state()


def get_dark_mode():
    raw = run_cmd([
        "/usr/bin/osascript",
        "-e",
        'tell application "System Events" to tell appearance preferences to get dark mode',
    ])
    return raw.strip().lower() == "true"


def set_dark_mode(enable):
    val = "true" if enable else "false"
    run_cmd([
        "/usr/bin/osascript",
        "-e",
        f'tell application "System Events" to tell appearance preferences to set dark mode to {val}',
    ])
    return get_dark_mode()


def get_adb_status():
    """
    V3.1: Explicitly distinguishes ADB session state from HTTP companion connectivity.
    Normal companion operations (Mac control, OTP inbox, clipboard, file transfer)
    NEVER require ADB. Only scrcpy screen mirroring and wireless camera streaming require ADB.
    """
    adb_bin = shutil.which("adb") or "/opt/homebrew/bin/adb"
    raw = run_cmd([adb_bin, "devices"], timeout=1.5)
    usb_attached = False
    wireless_lan = False
    wireless_vpn = False
    active_serial = "NONE"

    for line in raw.splitlines()[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            serial = parts[0]
            if ":" in serial:
                host_part = serial.split(":", 1)[0]
                if is_tailscale_ip(host_part):
                    wireless_vpn = True
                    active_serial = serial
                else:
                    wireless_lan = True
                    active_serial = serial
            else:
                usb_attached = True
                if active_serial == "NONE":
                    active_serial = serial

    connected = usb_attached or wireless_lan or wireless_vpn
    if wireless_vpn:
        mode_label = f"WIRELESS ADB (TAILSCALE VPN • {active_serial})"
    elif wireless_lan:
        mode_label = f"WIRELESS ADB (DIRECT LAN • {active_serial})"
    elif usb_attached:
        mode_label = f"USB CABLE ADB ({active_serial})"
    else:
        mode_label = "INACTIVE (Companion HTTP works 100% without ADB)"

    return {
        "connected": connected,
        "usb_attached": usb_attached,
        "wireless_lan": wireless_lan,
        "wireless_vpn": wireless_vpn,
        "serial": active_serial,
        "label": mode_label,
    }


def probe_tcp_port(host, port, timeout=1.2):
    if not host or host == "0.0.0.0":
        return False, "NO_HOST", -1
    t0 = time.time()
    try:
        with socket.create_connection((host, port), timeout=timeout):
            ms = round((time.time() - t0) * 1000.0, 1)
            return True, "OPEN", ms
    except ConnectionRefusedError:
        ms = round((time.time() - t0) * 1000.0, 1)
        return False, "REFUSED (Host reachable, port closed)", ms
    except socket.timeout:
        return False, "TIMEOUT (Filtered / Client Isolation / Offline)", -1
    except OSError as e:
        return False, f"UNREACHABLE ({e.strerror or e})", -1


def probe_pixel_http_status(host, timeout=1.0):
    """Probes http://<host>:8183/status with X-Aayush-Token to verify if <host> is our Pixel 7."""
    if not host or host in ("0.0.0.0", "127.0.0.1", "255.255.255.255"):
        return False
    try:
        req = urllib.request.Request(
            f"http://{host}:{PIXEL_COMPANION_PORT}/status",
            headers={"X-Aayush-Token": AUTH_TOKEN},
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            if resp.status == 200:
                data = json.loads(resp.read().decode("utf-8", errors="replace"))
                return bool(data.get("ok"))
    except Exception:
        pass
    return False


def resolve_pixel_lan_ip(wifi=None):
    """
    V3.1 Dynamic Pixel Resolver on Mac:
    1. If PIXEL_LAST_SEEN_IP is on the current subnet and responds on :8183, keep it.
    2. If the Mac switched networks (e.g. from 192.168.0.x to 10.234.233.x Pixel Hotspot or Office Wi-Fi),
       probe the Gateway IP (which IS the Pixel 7 when tethered to Pixel Hotspot!) and active ARP peers on :8183.
    """
    global PIXEL_LAST_SEEN_IP
    if wifi is None:
        wifi = get_wifi_state()
    mac_ip = wifi.get("ip") or ""
    gw_ip = wifi.get("router") or ""

    mac_prefix = ".".join(mac_ip.split(".")[:3]) if "." in mac_ip else ""
    cur_prefix = ".".join(PIXEL_LAST_SEEN_IP.split(".")[:3]) if "." in PIXEL_LAST_SEEN_IP else ""

    if mac_prefix and cur_prefix == mac_prefix:
        ok, _, _ = probe_tcp_port(PIXEL_LAST_SEEN_IP, PIXEL_COMPANION_PORT, timeout=0.6)
        if ok:
            return PIXEL_LAST_SEEN_IP

    # Candidate 1: Current Gateway IP (When Mac is on Pixel Hotspot e.g. Pixel_8972, Router IS the Pixel 7!)
    if gw_ip and gw_ip != mac_ip and probe_pixel_http_status(gw_ip, timeout=0.9):
        PIXEL_LAST_SEEN_IP = gw_ip
        return PIXEL_LAST_SEEN_IP

    # Candidate 2: Active ARP table entries on en0 matching the current subnet
    arp_raw = run_cmd(["/usr/sbin/arp", "-a"], timeout=1.2)
    for m in re.finditer(r"\((\d+\.\d+\.\d+\.\d+)\)\s+at\s+([0-9a-f:]+)\s+on\s+en0", arp_raw, re.I):
        cand_ip = m.group(1)
        mac_hw = m.group(2).lower()
        if cand_ip in (mac_ip, gw_ip) or cand_ip.endswith(".255") or mac_hw == "ff:ff:ff:ff:ff:ff":
            continue
        if probe_pixel_http_status(cand_ip, timeout=0.7):
            PIXEL_LAST_SEEN_IP = cand_ip
            return PIXEL_LAST_SEEN_IP

    return PIXEL_LAST_SEEN_IP


def build_network_audit():
    wifi = get_wifi_state()
    ts = get_tailscale_status()
    adb_st = get_adb_status()
    mdns_host = get_mac_mdns_hostname()

    gw = wifi.get("router") or "192.168.0.1"
    gw_ping = run_cmd(["/sbin/ping", "-c", "1", "-W", "900", gw], timeout=1.5)
    gw_ok = False
    gw_ms = "timeout"
    m = re.search(r"time=([\d.]+)\s*ms", gw_ping)
    if m:
        gw_ok = True
        gw_ms = f"{m.group(1)} ms"

    px_lan_ip = resolve_pixel_lan_ip(wifi)
    px_lan_tcp_ok, px_lan_tcp_state, px_lan_ms = probe_tcp_port(px_lan_ip, PIXEL_COMPANION_PORT, timeout=1.2)

    px_ts_ip = ts.get("pixel_ip") or ""
    px_vpn_tcp_ok, px_vpn_tcp_state, px_vpn_ms = (
        probe_tcp_port(px_ts_ip, PIXEL_COMPANION_PORT, timeout=1.5)
        if px_ts_ip else (False, "NO_PEER_IP", -1)
    )

    # Detect AP Client Isolation vs Port Block vs Normal Direct LAN
    isolation_verdict = "NORMAL (Direct LAN Bidirectional OK)"
    if not wifi.get("connected"):
        isolation_verdict = "MAC WI-FI DISCONNECTED"
    elif not px_lan_tcp_ok and px_vpn_tcp_ok and gw_ok:
        isolation_verdict = "⚠️ AP CLIENT ISOLATION DETECTED (Direct LAN blocked by Wi-Fi AP; routed via Tailscale VPN)"
    elif not px_lan_tcp_ok and "REFUSED" in px_lan_tcp_state:
        isolation_verdict = "⚠️ PIXEL REACHABLE ON LAN, BUT PORT :8183 CLOSED (Open Aayush OS on Pixel)"
    elif not px_lan_tcp_ok and gw_ok:
        # Check if Pixel responds to ICMP ping on LAN
        px_ping = run_cmd(["/sbin/ping", "-c", "1", "-W", "800", px_lan_ip], timeout=1.2)
        if "time=" in px_ping:
            isolation_verdict = f"⚠️ TCP PORT :{PIXEL_COMPANION_PORT} BLOCKED BY FIREWALL (ICMP ping succeeds to {px_lan_ip})"
        else:
            isolation_verdict = (
                f"⚠️ DIRECT LAN UNREACHABLE TO {px_lan_ip} (Possible AP Client Isolation, "
                "Different Subnet/VLAN, or Pixel Wi-Fi Off — Use Tailscale VPN Fallback)"
            )

    active_transport = "TAILSCALE VPN" if (px_vpn_tcp_ok and not px_lan_tcp_ok) else (
        "DIRECT LAN" if px_lan_tcp_ok else LAST_ACTIVE_TRANSPORT
    )

    return {
        "ok": True,
        "transport": active_transport,
        "mac_wifi_network": wifi.get("network"),
        "mac_lan_ip": wifi.get("ip"),
        "mac_mdns_host": mdns_host,
        "gateway_ip": gw,
        "gateway_reachable": gw_ok,
        "gateway_latency": gw_ms,
        "pixel_lan_ip": px_lan_ip,
        "pixel_lan_8183_ok": px_lan_tcp_ok,
        "pixel_lan_8183_state": px_lan_tcp_state,
        "pixel_lan_latency_ms": px_lan_ms,
        "tailscale": ts,
        "pixel_vpn_8183_ok": px_vpn_tcp_ok,
        "pixel_vpn_8183_state": px_vpn_tcp_state,
        "pixel_vpn_latency_ms": px_vpn_ms,
        "isolation_verdict": isolation_verdict,
        "adb": adb_st,
        "office_wifi_tested": False,
    }


def run_mac_network_diagnostics():
    audit = build_network_audit()
    ts = audit["tailscale"]
    adb_st = audit["adb"]

    dns_t0 = time.time()
    try:
        resolved = socket.gethostbyname("github.com")
        dns_ms = f"{(time.time() - dns_t0) * 1000:.1f} ms ({resolved})"
    except Exception as e:
        dns_ms = f"FAILED ({e})"

    ts_line = (
        f"RUNNING (Mac: {ts['mac_ip']} • Pixel: {ts['pixel_ip'] or ' auto'})"
        if ts["running"]
        else (f"INSTALLED ({ts['state']} — Launch Tailscale for VPN fallback)" if ts["installed"] else "NOT INSTALLED")
    )

    return (
        f"TRANSPORT     : {audit['transport']} (HTTP :8181 ↔ :8183 • Encrypted ENC_V31)\n"
        f"MAC WI-FI     : {audit['mac_wifi_network']} • mDNS: {audit['mac_mdns_host']}\n"
        f"GATEWAY       : {audit['gateway_ip']} ({audit['gateway_latency']})\n"
        f"PIXEL LAN :8183: {audit['pixel_lan_ip']} -> {audit['pixel_lan_8183_state']}"
        + (f" ({audit['pixel_lan_latency_ms']} ms)" if audit["pixel_lan_latency_ms"] >= 0 else "") + "\n"
        f"TAILSCALE VPN : {ts_line}\n"
        f"ISOLATION CHK : {audit['isolation_verdict']}\n"
        f"ADB CHANNEL   : {adb_st['label']}\n"
        f"DNS LOOKUP    : github.com -> {dns_ms}"
    )


# =============================================================================
# PHASE 3 & 4: SCRCPY PIXEL MIRRORING & CAMERA WEBCAM ENGINE
# =============================================================================

def is_proc_running(proc):
    return proc is not None and proc.poll() is None


def get_scrcpy_status():
    with SCRCPY_LOCK:
        running = is_proc_running(SCRCPY_PROC)
        SCRCPY_META["running"] = running
        if not running and SCRCPY_META["started_at"] > 0:
            SCRCPY_META["started_at"] = 0
        res = dict(SCRCPY_META)
        res["adb"] = get_adb_status()
        return res


def get_webcam_status():
    with SCRCPY_LOCK:
        running = is_proc_running(WEBCAM_PROC)
        WEBCAM_META["running"] = running
        # Also inspect macOS SPCameraDataType for native USB UVC camera presence
        cam_raw = run_cmd(["/usr/sbin/system_profiler", "SPCameraDataType"], timeout=2.5)
        uvc_detected = ("Android" in cam_raw) or ("Pixel" in cam_raw) or ("Webcam" in cam_raw)
        res = dict(WEBCAM_META)
        res["usb_uvc_detected_on_mac"] = uvc_detected
        return res


def start_scrcpy_mirror(data):
    global SCRCPY_PROC
    with SCRCPY_LOCK:
        if is_proc_running(SCRCPY_PROC):
            SCRCPY_PROC.terminate()
            try:
                SCRCPY_PROC.wait(timeout=2)
            except Exception:
                SCRCPY_PROC.kill()

        mode = data.get("mode", SCRCPY_META["mode"])
        max_size = int(data.get("max_size", SCRCPY_META["max_size"]))
        bitrate = str(data.get("bitrate", SCRCPY_META["bitrate"]))
        max_fps = int(data.get("max_fps", SCRCPY_META["max_fps"]))
        orientation = str(data.get("orientation", SCRCPY_META["orientation"]))
        target_ip = data.get("pixel_ip", PIXEL_LAST_SEEN_IP) or PIXEL_DEFAULT_IP

        scrcpy_bin = shutil.which("scrcpy") or "/opt/homebrew/bin/scrcpy"
        adb_bin = shutil.which("adb") or "/opt/homebrew/bin/adb"

        # Determine ADB target (wireless LAN vs Tailscale VPN vs USB)
        devices_out = run_cmd([adb_bin, "devices"])
        wireless_serial = f"{target_ip}:5555"
        ts_serial = f"{PIXEL_TAILSCALE_IP}:5555" if PIXEL_TAILSCALE_IP else ""
        has_wireless = wireless_serial in devices_out
        has_ts_wireless = bool(ts_serial and ts_serial in devices_out)
        has_usb = "2A311FDH200A4A" in devices_out

        if mode == "wireless" and not has_wireless and not has_ts_wireless:
            if has_usb:
                run_cmd([adb_bin, "-s", "2A311FDH200A4A", "tcpip", "5555"])
                time.sleep(1.0)
            conn_out = run_cmd([adb_bin, "connect", wireless_serial])
            has_wireless = "connected to" in conn_out.lower()
            if not has_wireless and ts_serial:
                conn_ts = run_cmd([adb_bin, "connect", ts_serial])
                has_ts_wireless = "connected to" in conn_ts.lower()

        args = [
            scrcpy_bin,
            "--window-title", "Aayush OS // Pixel 7 Panther (#81)",
            "--video-bit-rate", bitrate,
            "--max-fps", str(max_fps),
        ]
        if max_size > 0:
            args.extend(["--max-size", str(max_size)])

        if orientation == "landscape":
            args.extend(["--capture-orientation", "90"])
        elif orientation == "portrait":
            args.extend(["--capture-orientation", "0"])

        if mode == "wireless" and has_wireless:
            args.extend(["-s", wireless_serial])
            SCRCPY_META["target"] = f"{wireless_serial} (Direct LAN)"
            SCRCPY_META["mode"] = "wireless"
        elif mode == "wireless" and has_ts_wireless:
            args.extend(["-s", ts_serial])
            SCRCPY_META["target"] = f"{ts_serial} (Tailscale VPN)"
            SCRCPY_META["mode"] = "wireless_vpn"
        elif has_usb:
            args.extend(["-s", "2A311FDH200A4A"])
            SCRCPY_META["target"] = "2A311FDH200A4A (USB)"
            SCRCPY_META["mode"] = "usb"
        elif has_wireless:
            args.extend(["-s", wireless_serial])
            SCRCPY_META["target"] = f"{wireless_serial} (Direct LAN)"
            SCRCPY_META["mode"] = "wireless"
        elif has_ts_wireless:
            args.extend(["-s", ts_serial])
            SCRCPY_META["target"] = f"{ts_serial} (Tailscale VPN)"
            SCRCPY_META["mode"] = "wireless_vpn"
        else:
            return (
                False,
                "scrcpy requires an active ADB session (separate from HTTP companion). "
                "Toggle ADB Debugging ON in Aayush OS and tap Enable Wireless (5555) or connect USB.",
            )

        SCRCPY_PROC = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        time.sleep(0.6)
        if SCRCPY_PROC.poll() is not None:
            err = SCRCPY_PROC.stderr.read().decode("utf-8", errors="replace")[:200] if SCRCPY_PROC.stderr else "Exited"
            SCRCPY_META["running"] = False
            return False, f"scrcpy failed: {err}"

        SCRCPY_META.update({
            "running": True,
            "max_size": max_size,
            "bitrate": bitrate,
            "max_fps": max_fps,
            "orientation": orientation,
            "started_at": int(time.time()),
        })
        return True, f"scrcpy mirroring active on Mac ({SCRCPY_META['target']} • {max_size or 'Native'}p @ {bitrate})"


def stop_scrcpy_mirror():
    global SCRCPY_PROC
    with SCRCPY_LOCK:
        if is_proc_running(SCRCPY_PROC):
            SCRCPY_PROC.terminate()
            try:
                SCRCPY_PROC.wait(timeout=2)
            except Exception:
                SCRCPY_PROC.kill()
        SCRCPY_PROC = None
        SCRCPY_META["running"] = False
        SCRCPY_META["started_at"] = 0
    return True, "Stopped Pixel mirroring on Mac"


def start_pixel_webcam(data):
    global WEBCAM_PROC
    with SCRCPY_LOCK:
        if is_proc_running(WEBCAM_PROC):
            WEBCAM_PROC.terminate()
            try:
                WEBCAM_PROC.wait(timeout=2)
            except Exception:
                WEBCAM_PROC.kill()

        facing = data.get("facing", "back")
        cam_id = "0" if facing == "back" else "1"
        size = data.get("size", "1920x1080")
        fps = int(data.get("fps", 30))

        scrcpy_bin = shutil.which("scrcpy") or "/opt/homebrew/bin/scrcpy"
        args = [
            scrcpy_bin,
            "--video-source=camera",
            f"--camera-id={cam_id}",
            f"--camera-size={size}",
            f"--camera-fps={fps}",
            "--no-audio",
            "--window-title", f"Aayush OS // Pixel 7 Camera ({facing.upper()} {size} @{fps}fps)",
        ]
        WEBCAM_PROC = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        time.sleep(0.8)
        if WEBCAM_PROC.poll() is not None:
            err = WEBCAM_PROC.stderr.read().decode("utf-8", errors="replace")[:200] if WEBCAM_PROC.stderr else "Exited"
            WEBCAM_META["running"] = False
            return False, f"Camera stream requires an active ADB session (separate from HTTP companion): {err}"

        WEBCAM_META.update({
            "running": True,
            "facing": facing,
            "size": size,
            "fps": fps,
            "started_at": int(time.time()),
        })
        return True, f"Streaming Pixel 7 {facing.upper()} camera ({size} @ {fps}fps) to Mac"


def stop_pixel_webcam():
    global WEBCAM_PROC
    with SCRCPY_LOCK:
        if is_proc_running(WEBCAM_PROC):
            WEBCAM_PROC.terminate()
            try:
                WEBCAM_PROC.wait(timeout=2)
            except Exception:
                WEBCAM_PROC.kill()
        WEBCAM_PROC = None
        WEBCAM_META["running"] = False
        WEBCAM_META["started_at"] = 0
    return True, "Stopped Pixel camera stream on Mac"


# =============================================================================
# PHASE 5 & 6: OTP EXPIRY SWEEPER & SENSITIVE CLIPBOARD DETECTOR
# =============================================================================

SENSITIVE_PATTERNS = [
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"\b(?:sk-[A-Za-z0-9]{20,}|ghp_[A-Za-z0-9]{30,}|AIza[0-9A-Za-z-_]{35})\b"),
    re.compile(r"(?i)\b(?:password|passwd|secret_key|api_key|access_token)\s*[:=]\s*\S+"),
]
OTP_ONLY_REGEX = re.compile(r"^\d{4,8}$")


def is_sensitive_clipboard(text):
    if not text:
        return False, ""
    stripped = text.strip()
    if OTP_ONLY_REGEX.match(stripped):
        return True, "OTP_CODE"
    for pat in SENSITIVE_PATTERNS:
        if pat.search(stripped):
            return True, "SECRET_OR_PASSWORD"
    return False, ""


def classify_clip_kind(text):
    t = (text or "").strip()
    if not t:
        return "EMPTY"
    if (t.startswith("{") and t.endswith("}")) or (t.startswith("[") and t.endswith("]")):
        return "JSON"
    if re.match(r"^https?://\S+$", t, re.I):
        return "URL"
    if any(k in t for k in ("public class ", "def ", "function ", "const ", "import ", "#!/")):
        return "CODE"
    return "TEXT"


def record_clip_history(text, source):
    global LAST_CLIP_HASH
    if not text or not text.strip():
        return
    sens, _ = is_sensitive_clipboard(text)
    if sens:
        return
    h = hashlib.sha256(text.strip().encode("utf-8")).hexdigest()
    now = time.time()
    with STATE_LOCK:
        LAST_CLIP_HASH = h
        CLIP_HISTORY[:] = [c for c in CLIP_HISTORY if c["hash"] != h and c["expires_at"] > now]
        CLIP_HISTORY.insert(0, {
            "text": text[:600],
            "kind": classify_clip_kind(text),
            "source": source,
            "timestamp": int(now),
            "expires_at": now + (CLIP_RETENTION_MIN * 60),
            "hash": h,
        })
        del CLIP_HISTORY[20:]


def volatile_sweeper_loop():
    """Purges expired OTPs and expired clipboard history items every second."""
    global OTP_SEQ
    while True:
        time.sleep(1.0)
        now = time.time()
        with OTP_COND:
            before_len = len(OTP_INBOX)
            OTP_INBOX[:] = [o for o in OTP_INBOX if o["expires_at"] > now]
            if len(OTP_INBOX) != before_len:
                OTP_SEQ += 1
                OTP_COND.notify_all()
            CLIP_HISTORY[:] = [c for c in CLIP_HISTORY if c["expires_at"] > now]


def push_to_pixel_direct(endpoint, payload_dict):
    """
    V3.1: Pushes data over HTTP to PixelCompanionServer (:8183) without ADB.
    Tries Direct LAN IP first; if unreachable (e.g. AP Client Isolation or subnet switch),
    automatically falls back to Tailscale VPN IP (100.x.y.z:8183).
    Encrypts sensitive /clipboard payloads with ENC_V31.
    """
    send_dict = dict(payload_dict)
    if endpoint == "/clipboard" and "text" in send_dict:
        send_dict["enc_v31"] = encrypt_v31_payload(str(send_dict["text"]))

    resolved_lan = resolve_pixel_lan_ip()
    candidates = []
    if resolved_lan:
        candidates.append(resolved_lan)
    if PIXEL_LAST_SEEN_IP and PIXEL_LAST_SEEN_IP not in candidates:
        candidates.append(PIXEL_LAST_SEEN_IP)
    ts_info = get_tailscale_status()
    ts_px = ts_info.get("pixel_ip") or PIXEL_TAILSCALE_IP
    if ts_px and ts_px not in candidates:
        candidates.append(ts_px)

    body = json.dumps(send_dict).encode("utf-8")
    ts_hdr = str(int(time.time()))
    sig_hdr = compute_hmac_sha256(AUTH_TOKEN, f"POST:{endpoint}:{ts_hdr}")
    last_err = "No Pixel IP known"

    for host in candidates:
        url = f"http://{host}:{PIXEL_COMPANION_PORT}{endpoint}"
        req = urllib.request.Request(
            url,
            data=body,
            headers={
                "Content-Type": "application/json; charset=utf-8",
                "X-Aayush-Token": AUTH_TOKEN,
                "X-Aayush-Timestamp": ts_hdr,
                "X-Aayush-Signature": sig_hdr,
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=2.5) as resp:
                return resp.status == 200, resp.read().decode("utf-8", errors="replace")
        except Exception as e:
            last_err = f"{host}:{PIXEL_COMPANION_PORT} ({e})"
    raise RuntimeError(last_err)


# =============================================================================
# MAC DESKTOP COMPANION WEB DASHBOARD (SERVED ON http://127.0.0.1:8181/)
# =============================================================================

DASHBOARD_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>Aayush OS V3 — Mac Desktop Companion (#81)</title>
<style>
  :root {
    --obsidian: #0A0A0A; --carbon: #141414; --elevated: #1C1C1C;
    --border: #282828; --papaya: #FF8000; --green: #00E676; --red: #FF5252;
    --text: #F5F5F5; --muted: #9E9E9E;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 24px; background: var(--obsidian); color: var(--text);
    font-family: -apple-system, BlinkMacSystemFont, "SF Mono", Menlo, monospace;
  }
  .header {
    display: flex; justify-content: space-between; align-items: center;
    border-bottom: 1px solid var(--border); padding-bottom: 16px; margin-bottom: 20px;
  }
  .badge81 {
    background: var(--papaya); color: var(--obsidian); font-weight: 800;
    padding: 8px 14px; border-radius: 10px; font-size: 14px;
  }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 16px; }
  .card {
    background: var(--carbon); border: 1px solid var(--border);
    border-radius: 16px; padding: 18px;
  }
  .card.papaya { border-color: var(--papaya); }
  .sec-title { color: var(--papaya); font-size: 11px; font-weight: 700; letter-spacing: 1.4px; margin-bottom: 12px; }
  .btn-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
  button {
    background: var(--elevated); color: var(--text); border: 1px solid var(--border);
    padding: 8px 12px; border-radius: 999px; font-family: inherit; font-size: 11px;
    font-weight: 700; cursor: pointer;
  }
  button.primary { background: var(--papaya); color: var(--obsidian); border-color: var(--papaya); }
  button:hover { opacity: 0.9; }
  pre, .mono { font-size: 12px; line-height: 1.5; color: var(--text); white-space: pre-wrap; margin: 6px 0; }
  .otp-box {
    background: #102418; border: 1px solid var(--green); border-radius: 12px;
    padding: 12px; margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center;
  }
  .otp-code { font-size: 22px; font-weight: 800; color: var(--green); letter-spacing: 3px; }
  input, select, textarea {
    width: 100%; background: #090909; color: var(--text); border: 1px solid var(--border);
    border-radius: 8px; padding: 8px; font-family: inherit; font-size: 12px; margin-top: 6px;
  }
</style>
</head>
<body>
<div class="header">
  <div>
    <div style="color:var(--papaya);font-size:11px;font-weight:700;letter-spacing:1.5px;">AAYUSH OS V3 // CROSS-DEVICE INTELLIGENCE</div>
    <h1 style="margin:4px 0;font-size:24px;">MacBook Air M4 × Pixel 7 Panther</h1>
    <div id="connLine" style="color:var(--muted);font-size:12px;">Probing bridge state...</div>
  </div>
  <div class="badge81">#81 PIASTRI</div>
</div>

<div class="grid">
  <div class="card papaya">
    <div class="sec-title">PHASE 5 // MAC OTP INBOX (VOLATILE RAM ONLY • NEVER IN NOW BAR)</div>
    <div id="otpContainer"><div class="mono" style="color:var(--muted)">No active OTPs in memory.</div></div>
    <div class="btn-row">
      <button onclick="apiPost('/otp/clear', {})">PURGE OTP INBOX</button>
    </div>
  </div>

  <div class="card papaya">
    <div class="sec-title">PHASE 3 & 4 // CONTROL PIXEL FROM MAC (SCRCPY & WEBCAM)</div>
    <pre id="scrcpyStatus">Checking scrcpy state...</pre>
    <div style="display:flex;gap:8px;">
      <select id="scrcpyRes"><option value="1080">1080p</option><option value="1440">1440p</option><option value="720">720p</option><option value="0">Native 2400p</option></select>
      <select id="scrcpyBitrate"><option value="8M">8 Mbps</option><option value="4M">4 Mbps</option><option value="12M">12 Mbps</option><option value="16M">16 Mbps</option></select>
      <select id="scrcpyOrient"><option value="portrait">Portrait</option><option value="landscape">Landscape</option></select>
    </div>
    <div class="btn-row">
      <button class="primary" onclick="startScrcpy()">▶ START MIRRORING</button>
      <button onclick="apiPost('/scrcpy', {action:'STOP'})">⏹ STOP</button>
      <button onclick="apiPost('/scrcpy', {action:'ENABLE_WIRELESS'})">📡 ENABLE WIRELESS ADB</button>
      <button onclick="apiPost('/scrcpy', {action:'RECONNECT'})">🔄 RECONNECT</button>
    </div>
    <div class="sec-title" style="margin-top:14px;">ANDROID REMOTE NAVIGATION & CAMERA WEBCAM</div>
    <div class="btn-row">
      <button onclick="apiPost('/scrcpy', {action:'NAV_KEY', key:'HOME'})">⚪ HOME</button>
      <button onclick="apiPost('/scrcpy', {action:'NAV_KEY', key:'BACK'})">◀ BACK</button>
      <button onclick="apiPost('/scrcpy', {action:'NAV_KEY', key:'RECENTS'})">⬛ RECENTS</button>
      <button onclick="apiPost('/scrcpy', {action:'NAV_KEY', key:'LOCK'})">🔒 LOCK PIXEL</button>
      <button class="primary" onclick="apiPost('/webcam', {action:'START_CAMERA', facing:'back', size:'1920x1080', fps:30})">📷 REAR CAM 1080P</button>
      <button onclick="apiPost('/webcam', {action:'START_CAMERA', facing:'front', size:'1920x1080', fps:30})">🤳 FRONT CAM</button>
      <button onclick="apiPost('/webcam', {action:'STOP_CAMERA'})">⏹ STOP CAM</button>
    </div>
  </div>

  <div class="card">
    <div class="sec-title">PHASE 8 // PIXEL NOTIFICATION CENTRE ON MAC</div>
    <div id="notifContainer"><div class="mono" style="color:var(--muted)">No notifications forwarded yet.</div></div>
    <div class="btn-row">
      <button onclick="apiPost('/notifications/action', {action:'CLEAR_ALL'})">CLEAR ALL</button>
    </div>
  </div>

  <div class="card">
    <div class="sec-title">PHASE 6 & 7 // CLIPBOARD & WIRELESS FILE TRANSFER</div>
    <textarea id="clipBox" rows="3" placeholder="Type or paste text/code/JSON to push to Pixel 7..."></textarea>
    <div class="btn-row">
      <button class="primary" onclick="pushClipToPixel()">📤 PUSH TO PIXEL CLIPBOARD</button>
      <button onclick="pullMacClip()">📋 LOAD MAC CLIPBOARD</button>
    </div>
    <div class="sec-title" style="margin-top:14px;">TRANSFER & OUTBOX HISTORY (~/Downloads/PixelDrop)</div>
    <pre id="transferBox" style="color:var(--green)">Loading...</pre>
  </div>
</div>

<script>
const BRIDGE_TOKEN = "__AAYUSH_TOKEN__";
const revealedOtpIds = new Set();
async function apiGet(path) {
  const r = await fetch(path, {headers:{'X-Aayush-Token': BRIDGE_TOKEN}});
  return await r.json();
}
async function apiPost(path, body) {
  const r = await fetch(path, {
    method:'POST',
    headers:{'Content-Type':'application/json', 'X-Aayush-Token': BRIDGE_TOKEN},
    body:JSON.stringify(body)
  });
  const j = await r.json();
  refreshAll();
  return j;
}
function toggleRevealOtp(id) {
  if (revealedOtpIds.has(id)) revealedOtpIds.delete(id);
  else revealedOtpIds.add(id);
  refreshAll();
}
function startScrcpy() {
  apiPost('/scrcpy', {
    action: 'START',
    max_size: parseInt(document.getElementById('scrcpyRes').value),
    bitrate: document.getElementById('scrcpyBitrate').value,
    orientation: document.getElementById('scrcpyOrient').value
  });
}
async function pushClipToPixel() {
  const txt = document.getElementById('clipBox').value;
  const r = await apiPost('/clipboard/push-to-pixel', {text: txt});
  alert(r.output || (r.ok ? 'Pushed to Pixel 7!' : 'Failed'));
}
async function pullMacClip() {
  const j = await apiGet('/clipboard');
  document.getElementById('clipBox').value = j.text || '';
}
async function refreshAll() {
  try {
    const st = await apiGet('/status');
    const tsInfo = st.tailscale && st.tailscale.running ? ` • VPN: ${st.tailscale.mac_ip}` : '';
    document.getElementById('connLine').textContent =
      `TRANSPORT: ${st.transport} • MAC: ${st.hostname} (${st.wifi_network} • ${st.mdns_host}${tsInfo}) • PIXEL IP: ${st.pixel_ip}`;
    document.getElementById('scrcpyStatus').textContent =
      `HTTP BRIDGE : ACTIVE (${st.transport} • Zero ADB Required for Controls/OTP/Clipboard/Files)\n` +
      `ADB SESSION : ${st.adb ? st.adb.label : 'INACTIVE'}\n` +
      `MIRRORING   : ${st.scrcpy.running ? 'ACTIVE (' + st.scrcpy.target + ')' : 'IDLE'}\n` +
      `WEBCAM      : ${st.webcam.running ? 'STREAMING (' + st.webcam.facing.toUpperCase() + ' ' + st.webcam.size + ')' : 'IDLE'} • USB UVC: ${st.webcam.usb_uvc_detected_on_mac ? 'DETECTED' : 'NOT PLUGGED IN'}`;

    const revealQuery = revealedOtpIds.size > 0 ? '?reveal=1' : '';
    const otpRes = await apiGet('/otp' + revealQuery);
    const otpDiv = document.getElementById('otpContainer');
    if (otpRes.otps && otpRes.otps.length > 0) {
      otpDiv.innerHTML = otpRes.otps.map(o => {
        const isRev = revealedOtpIds.has(o.id);
        const disp = isRev ? o.code : o.masked_code;
        return `
        <div class="otp-box">
          <div>
            <div class="otp-code">${disp}</div>
            <div style="font-size:11px;color:var(--muted)">${o.app} • rcvd ${o.received_time} • expires in ${o.remaining_sec}s</div>
          </div>
          <div class="btn-row" style="margin-top:0">
            <button onclick="toggleRevealOtp(${o.id})">${isRev ? '🙈 HIDE' : '👁 REVEAL'}</button>
            <button class="primary" onclick="apiPost('/otp/copy',{id:${o.id}})">📋 COPY OTP</button>
            <button onclick="apiPost('/otp/clear',{id:${o.id}})">✕</button>
          </div>
        </div>
      `}).join('');
    } else {
      otpDiv.innerHTML = '<div class="mono" style="color:var(--muted)">No active OTPs in memory.</div>';
    }

    const nRes = await apiGet('/notifications');
    const nDiv = document.getElementById('notifContainer');
    if (nRes.notifications && nRes.notifications.length > 0) {
      nDiv.innerHTML = nRes.notifications.slice(0, 6).map(n => `
        <div style="border-bottom:1px solid var(--border);padding:8px 0;">
          <div style="color:var(--papaya);font-size:11px;font-weight:700;">${n.app} • ${new Date(n.timestamp*1000).toLocaleTimeString()}</div>
          <div style="font-weight:700;font-size:13px;">${n.title}</div>
          <div style="color:var(--muted);font-size:12px;">${n.body}</div>
          <div class="btn-row">
            <button onclick="apiPost('/notifications/action', {action:'COPY', text: ${JSON.stringify(n.body)}})">COPY</button>
            ${n.url ? `<button onclick="apiPost('/notifications/action', {action:'OPEN_URL', url: '${n.url}'})">OPEN LINK</button>` : ''}
            <button onclick="apiPost('/notifications/action', {action:'DISMISS', key: '${n.key}'})">DISMISS ON PIXEL</button>
          </div>
        </div>
      `).join('');
    } else {
      nDiv.innerHTML = '<div class="mono" style="color:var(--muted)">No notifications forwarded yet.</div>';
    }

    const fRes = await apiGet('/file/outbox');
    const hist = (fRes.history || []).slice(0, 5).map(h => `[${h.direction}] ${h.filename} (${(h.size_bytes/1024).toFixed(1)} KB @ ${h.speed_kbps} KB/s)`).join('\\n');
    const outbox = (fRes.files || []).slice(0, 5).map(f => `• ${f.name} (${(f.size/1024).toFixed(1)} KB)`).join('\\n');
    document.getElementById('transferBox').textContent =
      `FILES IN ~/Downloads/PixelDrop:\\n${outbox || '(empty)'}\\n\\nRECENT TRANSFERS:\\n${hist || '(none)'}`;
  } catch (e) {}
}
setInterval(refreshAll, 2000);
refreshAll();
</script>
</body>
</html>
"""


# =============================================================================
# HTTP REQUEST HANDLER
# =============================================================================

class BridgeHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def _check_access(self, require_token=True):
        global PIXEL_LAST_SEEN_IP, PIXEL_TAILSCALE_IP, LAST_ACTIVE_TRANSPORT
        client_ip = self.client_address[0]
        if not is_allowed_lan_ip(client_ip):
            self._send_json(403, {"ok": False, "error": "Forbidden: Local LAN or Tailscale VPN access only"})
            return False

        local_mac_ips = get_all_local_mac_ips()
        if client_ip not in local_mac_ips:
            if is_tailscale_ip(client_ip):
                PIXEL_TAILSCALE_IP = client_ip
                LAST_ACTIVE_TRANSPORT = "TAILSCALE VPN"
            else:
                PIXEL_LAST_SEEN_IP = client_ip
                LAST_ACTIVE_TRANSPORT = "DIRECT LAN"

        hdr_px_lan = (self.headers.get("X-Pixel-IP") or "").strip()
        if hdr_px_lan and hdr_px_lan != "0.0.0.0" and is_allowed_lan_ip(hdr_px_lan) and not is_tailscale_ip(hdr_px_lan):
            PIXEL_LAST_SEEN_IP = hdr_px_lan

        hdr_px_ts = (self.headers.get("X-Pixel-Tailscale-IP") or "").strip()
        if hdr_px_ts and is_tailscale_ip(hdr_px_ts):
            PIXEL_TAILSCALE_IP = hdr_px_ts

        if not require_token:
            return True

        tok = self.headers.get("X-Aayush-Token", "")
        if not tok or tok != AUTH_TOKEN:
            self._send_json(401, {"ok": False, "error": "Invalid or missing X-Aayush-Token"})
            return False
        return True

    def _send_json(self, code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_html(self, html_str):
        body = html_str.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length).decode("utf-8", errors="replace") if length > 0 else "{}"
        return json.loads(raw)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        qs = urllib.parse.parse_qs(parsed.query)

        if path in ("/", "/dashboard"):
            if self.client_address[0] not in ("127.0.0.1", "::1") and not self._check_access(require_token=True):
                return
            self._send_html(DASHBOARD_HTML.replace("__AAYUSH_TOKEN__", AUTH_TOKEN))
            return

        if not self._check_access(require_token=True):
            return

        if path == "/status":
            hostname = run_cmd(["/usr/sbin/scutil", "--get", "ComputerName"]) or socket.gethostname()
            mdns_host = get_mac_mdns_hostname()
            chip = run_cmd(["/usr/sbin/sysctl", "-n", "machdep.cpu.brand_string"]) or "Apple M4"
            batt = get_mac_battery()
            cpu = get_mac_cpu_usage()
            up = get_mac_uptime()
            wifi = get_wifi_state()
            bt = get_bluetooth_state()
            caff = get_caffeinate_state()
            aud = get_audio_state()
            dark = get_dark_mode()
            clip_raw = run_cmd(["/usr/bin/pbpaste"])
            sens, _ = is_sensitive_clipboard(clip_raw)
            clip_preview = "[REDACTED SENSITIVE/OTP]" if sens else clip_raw[:160]
            scrcpy_st = get_scrcpy_status()
            webcam_st = get_webcam_status()
            ts_st = get_tailscale_status()
            adb_st = scrcpy_st.get("adb") or get_adb_status()

            client_ip = self.client_address[0]
            transport = classify_transport(client_ip)
            if transport == "LOCALHOST":
                transport = LAST_ACTIVE_TRANSPORT

            now = time.time()
            reveal_req = qs.get("reveal", ["0"])[0] == "1"
            with STATE_LOCK:
                active_otps = [
                    {
                        "id": o["id"],
                        "code": o["code"] if reveal_req else ("•" * len(o["code"])),
                        "masked_code": "•" * len(o["code"]),
                        "masked": not reveal_req,
                        "code_len": len(o["code"]),
                        "app": o["app"],
                        "pkg": o["pkg"],
                        "title": o["title"],
                        "received_at": int(o["received_at"]),
                        "received_time": time.strftime("%H:%M:%S", time.localtime(o["received_at"])),
                        "remaining_sec": max(0, int(o["expires_at"] - now)),
                        "ttl": o["ttl"],
                    }
                    for o in OTP_INBOX if o["expires_at"] > now
                ]
                dismiss_queue = list(PENDING_DISMISS_KEYS)
                PENDING_DISMISS_KEYS.clear()
                notif_count = len(NOTIF_INBOX)

            self._send_json(200, {
                "ok": True,
                "version": "3.1",
                "hostname": hostname,
                "mdns_host": mdns_host,
                "chip": chip,
                "transport": transport,
                "battery": batt["summary"],
                "battery_pct": batt["pct"],
                "battery_charging": batt["charging"],
                "power_source": batt["source"],
                "cpu_usage": cpu["formatted"],
                "cpu_pct": cpu["total_pct"],
                "uptime": up,
                "wifi_power": wifi["power"],
                "wifi_connected": wifi["connected"],
                "wifi_network": wifi["network"],
                "wifi_ip": wifi["ip"],
                "bt_power": bt["power"],
                "bt_connected": bt["connected"],
                "bt_status": bt["status"],
                "caffeinated": caff,
                "audio_muted": aud["muted"],
                "audio_volume": aud["volume"],
                "dark_mode": dark,
                "clipboard": clip_preview,
                "scrcpy": scrcpy_st,
                "webcam": webcam_st,
                "adb": adb_st,
                "tailscale": ts_st,
                "otp_inbox": active_otps,
                "notif_count": notif_count,
                "pending_dismiss_keys": dismiss_queue,
                "pixel_ip": PIXEL_LAST_SEEN_IP,
                "pixel_tailscale_ip": ts_st.get("pixel_ip") or PIXEL_TAILSCALE_IP,
            })

        elif path == "/network/audit":
            self._send_json(200, build_network_audit())

        elif path == "/clipboard":
            clip = run_cmd(["/usr/bin/pbpaste"])
            sens, reason = is_sensitive_clipboard(clip)
            auto_mode = qs.get("auto", ["0"])[0] == "1"
            if auto_mode and sens:
                self._send_json(200, {
                    "ok": False,
                    "blocked_sensitive": True,
                    "reason": reason,
                    "text": "",
                })
                return
            h = hashlib.sha256(clip.strip().encode("utf-8")).hexdigest() if clip.strip() else ""
            with STATE_LOCK:
                history_copy = list(CLIP_HISTORY[:10])
            self._send_json(200, {
                "ok": True,
                "text": clip,
                "enc_v31": encrypt_v31_payload(clip) if clip else "",
                "kind": classify_clip_kind(clip),
                "hash": h,
                "sensitive": sens,
                "history": history_copy,
            })

        elif path == "/otp":
            wait_seq_str = qs.get("wait_seq", [""])[0]
            timeout_sec = 0.0
            try:
                timeout_sec = max(0.0, min(20.0, float(qs.get("timeout", ["0"])[0])))
            except Exception:
                timeout_sec = 0.0
            reveal_all = qs.get("reveal", ["0"])[0] == "1"
            reveal_ids = set(x.strip() for x in qs.get("reveal_ids", [""])[0].split(",") if x.strip())
            reveal_single = qs.get("reveal_id", [""])[0].strip()
            if reveal_single:
                reveal_ids.add(reveal_single)

            with OTP_COND:
                if wait_seq_str and timeout_sec > 0:
                    try:
                        req_seq = int(wait_seq_str)
                        if req_seq == OTP_SEQ:
                            OTP_COND.wait(timeout=timeout_sec)
                    except Exception:
                        pass
                now = time.time()
                cur_seq = OTP_SEQ
                items = []
                for o in OTP_INBOX:
                    if o["expires_at"] <= now:
                        continue
                    oid_str = str(o["id"])
                    is_rev = reveal_all or (oid_str in reveal_ids)
                    masked_str = "•" * len(o["code"])
                    items.append({
                        "id": o["id"],
                        "code": o["code"] if is_rev else masked_str,
                        "masked_code": masked_str,
                        "masked": not is_rev,
                        "code_len": len(o["code"]),
                        "app": o["app"],
                        "pkg": o["pkg"],
                        "title": o["title"],
                        "received_at": int(o["received_at"]),
                        "received_time": time.strftime("%H:%M:%S", time.localtime(o["received_at"])),
                        "expires_at": int(o["expires_at"]),
                        "remaining_sec": max(0, int(o["expires_at"] - now)),
                        "ttl": o["ttl"],
                    })
            wifi_st = get_wifi_state()
            eff_transport = LAST_ACTIVE_TRANSPORT if wifi_st["power"] else "OFFLINE (MAC WI-FI OFF)"
            self._send_json(200, {
                "ok": True,
                "seq": cur_seq,
                "transport": eff_transport,
                "pixel_ip": PIXEL_LAST_SEEN_IP,
                "wifi_network": wifi_st["network"],
                "otps": items,
            })

        elif path == "/notifications":
            with STATE_LOCK:
                items = list(NOTIF_INBOX[:30])
            self._send_json(200, {"ok": True, "notifications": items})

        elif path == "/file/outbox":
            files = []
            for folder in (DROP_DIR, OUTBOX_DIR):
                if folder.exists():
                    for f in sorted(folder.iterdir(), key=lambda p: p.stat().st_mtime, reverse=True):
                        if f.is_file() and not f.name.startswith("."):
                            st = f.stat()
                            files.append({
                                "name": f.name,
                                "size": st.st_size,
                                "mtime": int(st.st_mtime),
                            })
            # Deduplicate by name
            seen = set()
            unique_files = []
            for item in files:
                if item["name"] not in seen:
                    seen.add(item["name"])
                    unique_files.append(item)
            with STATE_LOCK:
                hist = list(TRANSFER_HISTORY[:15])
            self._send_json(200, {"ok": True, "files": unique_files[:25], "history": hist})

        elif path == "/file/download":
            fname = os.path.basename(qs.get("name", [""])[0])
            target = OUTBOX_DIR / fname
            if not target.exists():
                target = DROP_DIR / fname
            if not fname or not target.exists() or not target.is_file():
                self._send_json(404, {"ok": False, "error": f"File not found on Mac: {fname}"})
                return
            size = target.stat().st_size
            t0 = time.time()
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(size))
            self.send_header("X-Filename", fname)
            self.end_headers()
            with open(target, "rb") as fp:
                shutil.copyfileobj(fp, self.wfile, length=65536)
            dt = max(0.001, time.time() - t0)
            kbps = round((size / 1024.0) / dt, 1)
            with STATE_LOCK:
                TRANSFER_HISTORY.insert(0, {
                    "id": int(time.time() * 1000),
                    "direction": "MAC → PIXEL",
                    "filename": fname,
                    "size_bytes": size,
                    "speed_kbps": kbps,
                    "dest": "Pixel Downloads/AayushOS",
                    "status": "COMPLETED",
                    "timestamp": int(time.time()),
                })
                del TRANSFER_HISTORY[25:]
        else:
            self._send_json(404, {"ok": False, "error": "Not found"})

    def do_POST(self):
        global OTP_SEQ
        if not self._check_access(require_token=True):
            return

        # Binary Streaming File Upload (Phase 7: Pixel -> Mac)
        if self.path == "/file/upload":
            try:
                length = int(self.headers.get("Content-Length", "0"))
                raw_name = urllib.parse.unquote(self.headers.get("X-Filename", f"pixel_file_{int(time.time())}.bin"))
                fname = os.path.basename(raw_name) or f"pixel_file_{int(time.time())}.bin"
                dest_choice = self.headers.get("X-Dest-Folder", "PixelDrop")
                if dest_choice == "Desktop":
                    dest_dir = HOME_DIR / "Desktop"
                elif dest_choice == "Documents":
                    dest_dir = HOME_DIR / "Documents"
                else:
                    dest_dir = DROP_DIR
                dest_dir.mkdir(parents=True, exist_ok=True)
                target = dest_dir / fname

                t0 = time.time()
                remaining = length
                with open(target, "wb") as fp:
                    while remaining > 0:
                        chunk = self.rfile.read(min(65536, remaining))
                        if not chunk:
                            break
                        fp.write(chunk)
                        remaining -= len(chunk)
                dt = max(0.001, time.time() - t0)
                kbps = round((length / 1024.0) / dt, 1)

                with STATE_LOCK:
                    TRANSFER_HISTORY.insert(0, {
                        "id": int(time.time() * 1000),
                        "direction": "PIXEL → MAC",
                        "filename": fname,
                        "size_bytes": length,
                        "speed_kbps": kbps,
                        "dest": str(target),
                        "status": "COMPLETED",
                        "timestamp": int(time.time()),
                    })
                    del TRANSFER_HISTORY[25:]

                notify_mac("Aayush OS // File Received", f"{fname} ({length / 1024:.1f} KB @ {kbps} KB/s)", sound=True)
                self._send_json(200, {
                    "ok": True,
                    "saved_to": str(target),
                    "size_bytes": length,
                    "speed_kbps": kbps,
                })
            except Exception as e:
                self._send_json(500, {"ok": False, "error": str(e)})
            return

        try:
            data = self._read_json()
        except Exception as e:
            self._send_json(400, {"ok": False, "error": str(e)})
            return

        # Phase 6: Advanced Clipboard Sync (Supports V3.1 ENC_V31 Encrypted Envelopes)
        if self.path == "/clipboard":
            text = data.get("text", "")
            if data.get("enc_v31"):
                try:
                    text = decrypt_v31_payload(data.get("enc_v31"))
                except Exception as e:
                    self._send_json(400, {"ok": False, "error": f"ENC_V31 decryption failed: {e}"})
                    return
            auto_sync = bool(data.get("auto_sync", False))
            exclude_sensitive = bool(data.get("exclude_sensitive", True))

            sens, reason = is_sensitive_clipboard(text)
            if (auto_sync or exclude_sensitive) and sens and not data.get("force_override", False):
                self._send_json(200, {
                    "ok": False,
                    "blocked_sensitive": True,
                    "output": f"Blocked sensitive clipboard ({reason}) from syncing.",
                })
                return

            h = hashlib.sha256(text.strip().encode("utf-8")).hexdigest() if text.strip() else ""
            with STATE_LOCK:
                if auto_sync and h and h == LAST_CLIP_HASH:
                    self._send_json(200, {"ok": True, "duplicate_skipped": True, "output": "Duplicate clipboard skipped"})
                    return

            run_cmd(["/usr/bin/pbcopy"], input_text=text)
            record_clip_history(text, "PIXEL → MAC")
            if not auto_sync:
                notify_mac("Aayush OS // Pixel 7", f"Clipboard synced ({len(text)} chars • {classify_clip_kind(text)})")
            self._send_json(200, {"ok": True, "hash": h, "kind": classify_clip_kind(text), "encrypted": bool(data.get("enc_v31"))})

        elif self.path == "/clipboard/push-to-pixel":
            text = data.get("text")
            if text is None:
                text = run_cmd(["/usr/bin/pbpaste"])
            sens, reason = is_sensitive_clipboard(text)
            if sens and not data.get("force_override", False):
                self._send_json(200, {"ok": False, "output": f"Blocked sensitive content ({reason})"})
                return
            try:
                ok, resp = push_to_pixel_direct("/clipboard", {"text": text, "source": "MacBook Air M4"})
                record_clip_history(text, "MAC → PIXEL")
                self._send_json(200, {"ok": ok, "output": "Pushed clipboard to Pixel 7 over Wi-Fi!" if ok else resp})
            except Exception as e:
                self._send_json(200, {"ok": False, "output": f"Pixel receiver unreachable: {e}"})

        elif self.path == "/clipboard/clear":
            with STATE_LOCK:
                CLIP_HISTORY.clear()
            self._send_json(200, {"ok": True, "output": "Cleared Mac clipboard history"})

        # Phase 7: Legacy & Text File Drop
        elif self.path == "/drop":
            fname = os.path.basename(data.get("filename", f"pixel_drop_{int(time.time())}.txt"))
            content = data.get("content", "")
            dest_choice = data.get("dest_folder", "PixelDrop")
            dest_dir = (HOME_DIR / "Desktop") if dest_choice == "Desktop" else (
                (HOME_DIR / "Documents") if dest_choice == "Documents" else DROP_DIR
            )
            dest_dir.mkdir(parents=True, exist_ok=True)
            target = dest_dir / fname
            raw_bytes = content.encode("utf-8")
            target.write_bytes(raw_bytes)
            with STATE_LOCK:
                TRANSFER_HISTORY.insert(0, {
                    "id": int(time.time() * 1000),
                    "direction": "PIXEL → MAC",
                    "filename": fname,
                    "size_bytes": len(raw_bytes),
                    "speed_kbps": 1250.0,
                    "dest": str(target),
                    "status": "COMPLETED",
                    "timestamp": int(time.time()),
                })
            notify_mac("Aayush OS // PixelDrop", f"Saved {fname} ({len(raw_bytes)} B) to {dest_dir.name}/")
            self._send_json(200, {"ok": True, "saved_to": str(target)})

        # Phase 5: Mac OTP Inbox (Volatile RAM Only • Supports V3.1 ENC_V31 Encrypted Envelopes)
        elif self.path == "/otp":
            raw_code = data.get("code", "")
            if data.get("enc_v31"):
                try:
                    decrypted = json.loads(decrypt_v31_payload(data.get("enc_v31")))
                    raw_code = decrypted.get("code", raw_code)
                    data.update(decrypted)
                except Exception as e:
                    self._send_json(400, {"ok": False, "error": f"ENC_V31 OTP decryption failed: {e}"})
                    return
            code = re.sub(r"\D", "", str(raw_code))
            if not (4 <= len(code) <= 8):
                self._send_json(400, {"ok": False, "error": "Invalid OTP format"})
                return
            app_name = str(data.get("app_name", "Android App"))[:40]
            pkg_name = str(data.get("package_name", ""))[:80]
            title_str = str(data.get("title", "Verification Code"))[:60]
            ttl_sec = max(15, min(300, int(data.get("ttl_sec", 60))))
            auto_copy = bool(data.get("auto_copy", False))
            now = time.time()

            with OTP_COND:
                # Deduplicate identical OTP arriving within 15s
                for existing in OTP_INBOX:
                    if existing["code"] == code and (now - existing["received_at"]) < 15:
                        self._send_json(200, {"ok": True, "dedup": True, "seq": OTP_SEQ})
                        return
                new_id = int(now * 1000)
                OTP_INBOX.insert(0, {
                    "id": new_id,
                    "code": code,
                    "app": app_name,
                    "pkg": pkg_name,
                    "title": title_str,
                    "received_at": now,
                    "expires_at": now + ttl_sec,
                    "ttl": ttl_sec,
                })
                del OTP_INBOX[10:]
                OTP_SEQ += 1
                cur_seq = OTP_SEQ
                OTP_COND.notify_all()

            if auto_copy:
                run_cmd(["/usr/bin/pbcopy"], input_text=code)

            # Lock-screen privacy: NEVER include raw OTP digits in osascript notification text!
            # The native AayushOSMenuBar.app handles interactive notifications & lock-screen privacy.
            self._send_json(200, {
                "ok": True,
                "id": new_id,
                "seq": cur_seq,
                "expires_in_sec": ttl_sec,
                "encrypted": bool(data.get("enc_v31")),
            })

        elif self.path == "/otp/copy":
            req_id = data.get("id")
            code = re.sub(r"\D", "", str(data.get("code", "")))
            matched_app = "Android App"
            matched_id = 0
            now = time.time()
            with STATE_LOCK:
                if req_id is not None:
                    for o in OTP_INBOX:
                        if str(o["id"]) == str(req_id) and o["expires_at"] > now:
                            code = o["code"]
                            matched_app = o["app"]
                            matched_id = o["id"]
                            break
                elif code:
                    for o in OTP_INBOX:
                        if o["code"] == code and o["expires_at"] > now:
                            matched_app = o["app"]
                            matched_id = o["id"]
                            break
                elif OTP_INBOX:
                    for o in OTP_INBOX:
                        if o["expires_at"] > now:
                            code = o["code"]
                            matched_app = o["app"]
                            matched_id = o["id"]
                            break
            if code:
                run_cmd(["/usr/bin/pbcopy"], input_text=code)
                self._send_json(200, {
                    "ok": True,
                    "id": matched_id,
                    "app": matched_app,
                    "output": f"Copied OTP from {matched_app} to Mac clipboard",
                })
            else:
                self._send_json(404, {"ok": False, "output": "No active OTP in Mac Inbox"})

        elif self.path == "/otp/clear":
            target_id = data.get("id")
            with OTP_COND:
                if target_id is not None and str(target_id).strip() != "":
                    OTP_INBOX[:] = [o for o in OTP_INBOX if str(o["id"]) != str(target_id)]
                    msg = f"Cleared OTP #{target_id}"
                else:
                    OTP_INBOX.clear()
                    msg = "Cleared all OTPs from Mac OTP Inbox"
                OTP_SEQ += 1
                cur_seq = OTP_SEQ
                OTP_COND.notify_all()
            self._send_json(200, {"ok": True, "seq": cur_seq, "output": msg})

        # Phase 8: Mac Notification Centre
        elif self.path == "/notifications":
            if data.get("enc_v31"):
                try:
                    decrypted_raw = decrypt_v31_payload(data.get("enc_v31"))
                    decrypted_obj = json.loads(decrypted_raw)
                    data.update(decrypted_obj)
                except Exception as e:
                    self._send_json(400, {"ok": False, "error": f"ENC_V31 decryption failed: {e}"})
                    return

            key = str(data.get("key", f"notif_{int(time.time()*1000)}"))
            app_name = str(data.get("app_name", "Pixel 7"))[:40]
            pkg_name = str(data.get("package_name", ""))[:80]
            title_str = str(data.get("title", ""))[:100]
            body_str = str(data.get("body", ""))[:240]
            url_str = str(data.get("url", ""))[:300]
            now = int(time.time())

            with STATE_LOCK:
                NOTIF_INBOX[:] = [n for n in NOTIF_INBOX if n["key"] != key]
                NOTIF_INBOX.insert(0, {
                    "id": int(time.time() * 1000),
                    "key": key,
                    "app": app_name,
                    "pkg": pkg_name,
                    "title": title_str,
                    "body": body_str,
                    "url": url_str,
                    "timestamp": now,
                })
                del NOTIF_INBOX[50:]

            notify_mac(f"📱 {app_name} — {title_str}", body_str)
            self._send_json(200, {"ok": True})

        elif self.path == "/notifications/action":
            act = data.get("action", "")
            if act == "COPY":
                txt = str(data.get("text", ""))
                run_cmd(["/usr/bin/pbcopy"], input_text=txt)
                self._send_json(200, {"ok": True, "output": "Copied notification text to Mac clipboard"})
            elif act == "OPEN_URL":
                url = str(data.get("url", ""))
                if url.startswith("http://") or url.startswith("https://"):
                    subprocess.Popen(["/usr/bin/open", url])
                    self._send_json(200, {"ok": True, "output": f"Opened {url} on Mac"})
                else:
                    self._send_json(400, {"ok": False, "output": "Invalid URL"})
            elif act == "DISMISS":
                key = str(data.get("key", ""))
                with STATE_LOCK:
                    NOTIF_INBOX[:] = [n for n in NOTIF_INBOX if n["key"] != key]
                    if key:
                        PENDING_DISMISS_KEYS.append(key)
                try:
                    push_to_pixel_direct("/dismiss-notif", {"key": key})
                except Exception:
                    pass
                self._send_json(200, {"ok": True, "output": "Dismissed notification"})
            elif act == "CLEAR_ALL":
                with STATE_LOCK:
                    NOTIF_INBOX.clear()
                self._send_json(200, {"ok": True, "output": "Cleared Mac Notification Centre"})
            else:
                self._send_json(400, {"ok": False, "output": f"Unknown action: {act}"})

        # Phase 3: scrcpy Control Endpoint
        elif self.path == "/scrcpy":
            act = data.get("action", "STATUS")
            adb_bin = shutil.which("adb") or "/opt/homebrew/bin/adb"
            if act == "START":
                ok, msg = start_scrcpy_mirror(data)
                self._send_json(200, {"ok": ok, "output": msg, "scrcpy": get_scrcpy_status()})
            elif act == "STOP":
                ok, msg = stop_scrcpy_mirror()
                self._send_json(200, {"ok": ok, "output": msg, "scrcpy": get_scrcpy_status()})
            elif act == "ENABLE_WIRELESS":
                run_cmd([adb_bin, "tcpip", "5555"])
                time.sleep(1.0)
                out = run_cmd([adb_bin, "connect", f"{PIXEL_LAST_SEEN_IP}:5555"])
                ok = "connected" in out.lower()
                self._send_json(200, {"ok": ok, "output": out or "Wireless ADB command sent", "scrcpy": get_scrcpy_status()})
            elif act == "RECONNECT":
                stop_scrcpy_mirror()
                run_cmd([adb_bin, "connect", f"{PIXEL_LAST_SEEN_IP}:5555"])
                ok, msg = start_scrcpy_mirror(data)
                self._send_json(200, {"ok": ok, "output": msg, "scrcpy": get_scrcpy_status()})
            elif act == "DISCONNECT":
                stop_scrcpy_mirror()
                out = run_cmd([adb_bin, "disconnect", f"{PIXEL_LAST_SEEN_IP}:5555"])
                self._send_json(200, {"ok": True, "output": out or "Disconnected wireless scrcpy", "scrcpy": get_scrcpy_status()})
            elif act == "NAV_KEY":
                key_map = {
                    "HOME": "3", "BACK": "4", "RECENTS": "187", "LOCK": "26",
                    "VOL_UP": "24", "VOL_DOWN": "25", "PLAY_PAUSE": "85",
                }
                kc = key_map.get(data.get("key", "HOME"), "3")
                run_cmd([adb_bin, "shell", "input", "keyevent", kc])
                self._send_json(200, {"ok": True, "output": f"Sent Android keyevent {kc}"})
            elif act == "INPUT_TEXT":
                raw_txt = str(data.get("text", ""))
                safe_txt = raw_txt.replace(" ", "%s")
                safe_txt = re.sub(r"[^A-Za-z0-9%._,@/-]", "", safe_txt)
                if safe_txt:
                    run_cmd([adb_bin, "shell", "input", "text", safe_txt])
                self._send_json(200, {"ok": True, "output": "Typed text on Pixel 7"})
            elif act == "LAUNCH_APP":
                pkg = re.sub(r"[^A-Za-z0-9._]", "", str(data.get("package", "")))
                if pkg:
                    run_cmd([adb_bin, "shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1"])
                self._send_json(200, {"ok": True, "output": f"Launched {pkg} on Pixel 7"})
            else:
                self._send_json(200, {"ok": True, "scrcpy": get_scrcpy_status()})

        # Phase 4: Pixel Camera as Mac Webcam Endpoint
        elif self.path == "/webcam":
            act = data.get("action", "STATUS")
            if act == "START_CAMERA":
                ok, msg = start_pixel_webcam(data)
                self._send_json(200, {"ok": ok, "output": msg, "webcam": get_webcam_status()})
            elif act == "STOP_CAMERA":
                ok, msg = stop_pixel_webcam()
                self._send_json(200, {"ok": ok, "output": msg, "webcam": get_webcam_status()})
            else:
                self._send_json(200, {"ok": True, "webcam": get_webcam_status()})

        # Phase 1: Mac System Control Endpoint (Strict Allowlist)
        elif self.path == "/command":
            action = data.get("action", "")
            if action in ("PING_BEEP", "PLAY_SOUND"):
                subprocess.Popen(["/usr/bin/afplay", "/System/Library/Sounds/Glass.aiff"])
                notify_mac("Aayush OS // Pixel 7", "Notification chime triggered from Pixel 7 (#81)")
                self._send_json(200, {"ok": True, "output": "Played Glass.aiff chime & notification on Mac"})

            elif action == "WIFI_ON":
                st = set_wifi_power(True)
                self._send_json(200, {
                    "ok": True,
                    "wifi_power": st["power"],
                    "output": f"Mac Wi-Fi turned ON ({st['network']})",
                })

            elif action == "WIFI_OFF":
                # Critical disconnect behaviour: Flush HTTP 200 immediately, then cut Wi-Fi power
                # within 80ms and NEVER automatically restore it.
                self._send_json(200, {
                    "ok": True,
                    "wifi_power": False,
                    "disconnected_by_user": True,
                    "output": "Mac Wi-Fi turned OFF immediately (remains OFF until manually re-enabled)",
                })
                try:
                    self.wfile.flush()
                except Exception:
                    pass
                threading.Timer(0.08, lambda: subprocess.run(
                    ["/usr/sbin/networksetup", "-setairportpower", "en0", "off"],
                    shell=False,
                )).start()

            elif action == "BT_ON":
                st = set_bluetooth_power(True)
                self._send_json(200, {
                    "ok": True,
                    "bt_power": st["power"],
                    "output": f"Mac Bluetooth turned ON ({st['status']})",
                })

            elif action == "BT_OFF":
                st = set_bluetooth_power(False)
                self._send_json(200, {
                    "ok": True,
                    "bt_power": st["power"],
                    "output": "Mac Bluetooth turned OFF immediately (remains OFF)",
                })

            elif action in ("CAFFEINATE_MAC", "CAFFEINATE_ON"):
                active = set_caffeinate_state(True)
                notify_mac("Aayush OS // Caffeinate", "Mac will stay awake (60m)")
                self._send_json(200, {"ok": True, "caffeinated": active, "output": "Mac Caffeinate ON (60m)"})

            elif action == "CAFFEINATE_OFF":
                active = set_caffeinate_state(False)
                notify_mac("Aayush OS // Caffeinate", "Mac normal sleep restored")
                self._send_json(200, {"ok": True, "caffeinated": active, "output": "Mac Caffeinate OFF (normal sleep)"})

            elif action == "CAFFEINATE_TOGGLE":
                target = not get_caffeinate_state()
                active = set_caffeinate_state(target)
                self._send_json(200, {
                    "ok": True,
                    "caffeinated": active,
                    "output": "Mac Caffeinate ON (60m)" if active else "Mac Caffeinate OFF",
                })

            elif action == "LOCK_SCREEN":
                subprocess.Popen(["/usr/bin/pmset", "displaysleepnow"])
                self._send_json(200, {"ok": True, "output": "Mac display locked / sleeping now"})

            elif action == "AUDIO_MUTE":
                st = set_audio_mute(True)
                self._send_json(200, {"ok": True, "audio_muted": st["muted"], "output": "Mac audio MUTED"})

            elif action == "AUDIO_UNMUTE":
                st = set_audio_mute(False)
                self._send_json(200, {
                    "ok": True,
                    "audio_muted": st["muted"],
                    "output": f"Mac audio UNMUTED (Vol {st['volume']}%)",
                })

            elif action == "AUDIO_MUTE_TOGGLE":
                cur = get_audio_state()["muted"]
                st = set_audio_mute(not cur)
                self._send_json(200, {
                    "ok": True,
                    "audio_muted": st["muted"],
                    "output": "Mac audio MUTED" if st["muted"] else f"Mac audio UNMUTED ({st['volume']}%)",
                })

            elif action == "DARK_MODE_TOGGLE":
                cur = get_dark_mode()
                new_st = set_dark_mode(not cur)
                self._send_json(200, {
                    "ok": True,
                    "dark_mode": new_st,
                    "output": f"Mac appearance set to {'DARK MODE' if new_st else 'LIGHT MODE'}",
                })

            elif action == "DARK_MODE_ON":
                new_st = set_dark_mode(True)
                self._send_json(200, {"ok": True, "dark_mode": new_st, "output": "Mac Dark Mode ON"})

            elif action == "DARK_MODE_OFF":
                new_st = set_dark_mode(False)
                self._send_json(200, {"ok": True, "dark_mode": new_st, "output": "Mac Light Mode ON"})

            elif action == "NET_DIAGNOSTICS":
                rep = run_mac_network_diagnostics()
                self._send_json(200, {"ok": True, "output": rep})

            elif action in ("TAILSCALE_UP", "CONNECT_TAILSCALE"):
                ts_bin = shutil.which("tailscale") or "/Applications/Tailscale.app/Contents/MacOS/Tailscale"
                # Prevent MagicDNS hijacking system DNS (ISSUE-NET-01)
                out = run_cmd([ts_bin, "up", "--accept-dns=false"])
                self._send_json(200, {"ok": True, "output": f"Tailscale connected without DNS hijacking: {out}"})

            elif action == "OPEN_TAILSCALE":
                subprocess.Popen(["/usr/bin/open", "-a", "Tailscale"])
                self._send_json(200, {"ok": True, "output": "Launched Tailscale VPN app on MacBook Air M4"})

            elif action in ("CLOSE_TAILSCALE", "TAILSCALE_DOWN", "KILL_TAILSCALE"):
                ts_bin = shutil.which("tailscale") or "/Applications/Tailscale.app/Contents/MacOS/Tailscale"
                out = run_cmd([ts_bin, "down"])
                self._send_json(200, {"ok": True, "output": f"Tailscale VPN disconnected: {out}"})

            elif action in ("DISCONNECT_ALL", "KILL_ALL"):
                ts_bin = shutil.which("tailscale") or "/Applications/Tailscale.app/Contents/MacOS/Tailscale"
                if os.path.exists(ts_bin):
                    run_cmd([ts_bin, "down"])
                stop_scrcpy()
                stop_webcam_stream()
                self._send_json(200, {"ok": True, "output": "All bridges and VPN tunnels terminated on Mac M4"})

            elif action == "OPEN_DASHBOARD":
                subprocess.Popen(["/usr/bin/open", f"http://127.0.0.1:{HTTP_PORT}/dashboard"])
                self._send_json(200, {"ok": True, "output": "Opened Aayush OS Desktop Companion on Mac"})

            elif action == "PIXEL_LOCK":
                try:
                    ok, resp = push_to_pixel_direct("/lock-phone", {})
                    self._send_json(200, {"ok": ok, "output": "Locked Pixel 7 Screen remotely"})
                except Exception as e:
                    self._send_json(500, {"ok": False, "error": str(e)})

            elif action == "PIXEL_RING":
                try:
                    ok, resp = push_to_pixel_direct("/ring-phone", {})
                    self._send_json(200, {"ok": ok, "output": "Toggled Pixel 7 Siren"})
                except Exception as e:
                    self._send_json(500, {"ok": False, "error": str(e)})

            elif action == "PIXEL_TORCH":
                try:
                    ok, resp = push_to_pixel_direct("/flashlight-toggle", {})
                    self._send_json(200, {"ok": ok, "output": "Toggled Pixel 7 Torch"})
                except Exception as e:
                    self._send_json(500, {"ok": False, "error": str(e)})

            elif action == "PIXEL_DND":
                try:
                    ok, resp = push_to_pixel_direct("/dnd-toggle", {})
                    self._send_json(200, {"ok": ok, "output": "Toggled Pixel 7 DND"})
                except Exception as e:
                    self._send_json(500, {"ok": False, "error": str(e)})

            elif action == "OPEN_GITHUB":
                subprocess.Popen(["/usr/bin/open", "https://github.com"])
                self._send_json(200, {"ok": True, "output": "Opened GitHub on MacBook"})
            else:
                self._send_json(400, {"ok": False, "output": f"Disallowed command: {action}"})
        elif self.path == "/pixel/control":
            action = data.get("action", "").upper()
            routes = {
                "LOCK": "/lock-phone",
                "RING": "/ring-phone",
                "TORCH": "/flashlight-toggle",
                "DND": "/dnd-toggle",
            }
            if action in routes:
                try:
                    ok, resp = push_to_pixel_direct(routes[action], {})
                    self._send_json(200, {"ok": ok, "action": action, "output": resp})
                except Exception as e:
                    self._send_json(500, {"ok": False, "error": str(e)})
            else:
                self._send_json(400, {"ok": False, "error": f"Invalid action: {action}"})
        else:
            self._send_json(404, {"ok": False, "error": "Not found"})


def udp_discovery_loop():
    """
    V3.1 Dynamic Authenticated UDP Discovery Responder (:8182).
    Learns Pixel's current IP from incoming broadcast/unicast packets across any subnet
    and replies with an HMAC-SHA256 signed beacon containing Mac LAN IP, Tailscale IP, and mDNS host.
    """
    global PIXEL_LAST_SEEN_IP, PIXEL_TAILSCALE_IP
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("", UDP_PORT))
    while True:
        try:
            data, addr = sock.recvfrom(1024)
            sender_ip = addr[0]
            if not is_allowed_lan_ip(sender_ip):
                continue

            local_ips = get_all_local_mac_ips()
            if sender_ip not in local_ips:
                if is_tailscale_ip(sender_ip):
                    PIXEL_TAILSCALE_IP = sender_ip
                else:
                    PIXEL_LAST_SEEN_IP = sender_ip

            raw_str = data.decode("utf-8", errors="replace")
            if "|" in raw_str:
                try:
                    meta = json.loads(raw_str.split("|", 1)[1])
                    px_ip = str(meta.get("pixel_ip", "")).strip()
                    if px_ip and px_ip != "0.0.0.0" and is_allowed_lan_ip(px_ip) and not is_tailscale_ip(px_ip):
                        PIXEL_LAST_SEEN_IP = px_ip
                    px_ts = str(meta.get("pixel_tailscale_ip", "")).strip()
                    if px_ts and is_tailscale_ip(px_ts):
                        PIXEL_TAILSCALE_IP = px_ts
                except Exception:
                    pass

            if b"AAYUSH_OS_DISCOVER" in data or b"AAYUSH_OS_PING" in data:
                wifi = get_wifi_state()
                ts_info = get_tailscale_status()
                lan_ip = wifi.get("ip") or sender_ip
                ts_now = int(time.time())
                sig = compute_hmac_sha256(AUTH_TOKEN, f"{lan_ip}:{HTTP_PORT}:{ts_now}")
                beacon = {
                    "ok": True,
                    "version": "3.1",
                    "lan_ip": lan_ip,
                    "tailscale_ip": ts_info.get("mac_ip", ""),
                    "mdns_host": get_mac_mdns_hostname(),
                    "port": HTTP_PORT,
                    "ts": ts_now,
                    "sig": sig,
                }
                reply_bytes = f"AAYUSH_OS_MAC_M4:{HTTP_PORT}|{json.dumps(beacon)}".encode("utf-8")
                sock.sendto(reply_bytes, addr)
        except Exception:
            pass


if __name__ == "__main__":
    start_mdns_advertiser()
    threading.Thread(target=udp_discovery_loop, daemon=True).start()
    threading.Thread(target=volatile_sweeper_loop, daemon=True).start()
    # Stage a sample welcome file in Outbox so Mac -> Pixel file transfer can be tested immediately
    sample_outbox = OUTBOX_DIR / "mclaren_81_mac_telemetry.json"
    if not sample_outbox.exists():
        sample_outbox.write_text(json.dumps({
            "ecosystem": "Aayush OS V3.1",
            "host": f"MacBook Air M4 ({get_mac_mdns_hostname()})",
            "target": "Pixel 7 Panther",
            "driver": "Oscar Piastri #81",
            "timestamp": int(time.time()),
        }, indent=2), encoding="utf-8")

    print(f"Aayush OS V3.1 Mac Bridge listening on 0.0.0.0:{HTTP_PORT} (UDP :{UDP_PORT} • mDNS _aayush-os._tcp)")
    server = ThreadingHTTPServer(("0.0.0.0", HTTP_PORT), BridgeHandler)
    server.serve_forever()
