package dev.aayush.os;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module 9: Deterministic Smart Clipboard, Sensitive Content Detector & History Store.
 * Detects OTPs, Passwords/Secrets, JSON, JWTs, Tracking URLs, Shell commands, and Code.
 */
public final class SmartClipboard {

    public enum ContentType {
        EMPTY,
        OTP,
        SENSITIVE_SECRET,
        JWT,
        JSON,
        URL,
        SHELL_CMD,
        CODE,
        TEXT
    }

    public static final class Analysis {
        public ContentType type = ContentType.EMPTY;
        public String rawText = "";
        public String badgeLabel = "EMPTY";
        public String summary = "Clipboard is empty";
        public String primaryActionLabel = "";
        public String transformedText = "";
        public boolean isSensitive = false;
        public String sha256Hash = "";
    }

    public static final class HistoryItem {
        public final String text;
        public final String badgeLabel;
        public final String direction; // "LOCAL", "PIXEL → MAC", "MAC → PIXEL"
        public final long timestampMs;
        public final long expiresAtMs;
        public final String hash;

        public HistoryItem(String text, String badgeLabel, String direction, long timestampMs, long expiresAtMs, String hash) {
            this.text = text;
            this.badgeLabel = badgeLabel;
            this.direction = direction;
            this.timestampMs = timestampMs;
            this.expiresAtMs = expiresAtMs;
            this.hash = hash;
        }
    }

    private static final List<HistoryItem> CLIP_HISTORY = new ArrayList<>();
    private static String lastSyncedHash = "";

    private static final Pattern OTP_PATTERN = Pattern.compile(
            "(?i)(?:otp|code|verification|passcode|pin|auth|login|\\b)\\D{0,15}\\b(\\d{4,8})\\b|\\b(\\d{6})\\b");
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "^[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}$");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://[^\\s]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(-----BEGIN [A-Z ]*PRIVATE KEY-----|\\b(?:sk-[A-Za-z0-9]{20,}|ghp_[A-Za-z0-9]{30,}|AIza[0-9A-Za-z\\-_]{35})\\b|\\b(?:password|passwd|secret_key|api_key|access_token|private_key)\\s*[:=]\\s*\\S+)");

    public static Analysis inspectClipboard(Context ctx) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return new Analysis();
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return new Analysis();
        CharSequence cs = clip.getItemAt(0).coerceToText(ctx);
        return classify(cs != null ? cs.toString() : "");
    }

    public static String getRawClipboardText(Context ctx) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return "";
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return "";
        CharSequence cs = clip.getItemAt(0).coerceToText(ctx);
        return cs != null ? cs.toString() : "";
    }

    public static void copyToClipboard(Context ctx, String label, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
        }
    }

    public static Analysis classify(String raw) {
        Analysis a = new Analysis();
        if (raw == null || TextUtils.isEmpty(raw.trim())) {
            return a;
        }
        String trimmed = raw.trim();
        a.rawText = trimmed;
        a.sha256Hash = sha256(trimmed);

        // 0. Check Sensitive Secret / API Key / Password
        if (SECRET_PATTERN.matcher(trimmed).find()) {
            a.type = ContentType.SENSITIVE_SECRET;
            a.badgeLabel = "SENSITIVE SECRET";
            a.isSensitive = true;
            a.transformedText = trimmed;
            a.summary = "Sensitive key/password detected (excluded from silent Mac sync)";
            a.primaryActionLabel = "COPY LOCALLY ONLY";
            return a;
        }

        // 1. Check JWT Token
        if (JWT_PATTERN.matcher(trimmed).matches()) {
            a.type = ContentType.JWT;
            a.badgeLabel = "JWT TOKEN";
            a.transformedText = decodeJwtPayload(trimmed);
            a.summary = "Decoded JWT Payload:\n" + a.transformedText;
            a.primaryActionLabel = "COPY DECODED JSON";
            return a;
        }

        // 2. Check JSON Object / Array
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                if (trimmed.startsWith("{")) {
                    JSONObject obj = new JSONObject(trimmed);
                    a.transformedText = obj.toString(2);
                    a.summary = "Valid JSON Object (" + obj.length() + " keys)";
                } else {
                    JSONArray arr = new JSONArray(trimmed);
                    a.transformedText = arr.toString(2);
                    a.summary = "Valid JSON Array (" + arr.length() + " items)";
                }
                a.type = ContentType.JSON;
                a.badgeLabel = "JSON PAYLOAD";
                a.primaryActionLabel = "FORMAT & COPY PRETTY JSON";
                return a;
            } catch (Exception ignored) {}
        }

        // 3. Check URL & Tracking Parameters
        if (URL_PATTERN.matcher(trimmed).matches()) {
            a.type = ContentType.URL;
            a.badgeLabel = "URL LINK";
            a.transformedText = stripTrackingParams(trimmed);
            boolean hadTrackers = !a.transformedText.equals(trimmed);
            a.summary = hadTrackers
                    ? "Trackers detected! Clean URL:\n" + a.transformedText
                    : "Clean URL: " + trimmed;
            a.primaryActionLabel = hadTrackers ? "SANITIZE & COPY URL" : "OPEN IN BROWSER";
            return a;
        }

        // 4. Check OTP (if short message or direct 4-8 digit code)
        if (trimmed.length() <= 180) {
            Matcher m = OTP_PATTERN.matcher(trimmed);
            if (m.find()) {
                String code = m.group(1) != null ? m.group(1) : m.group(2);
                if (code != null) {
                    a.type = ContentType.OTP;
                    a.badgeLabel = "OTP CODE: " + code;
                    a.isSensitive = true;
                    a.transformedText = code;
                    a.summary = "Verification Code: " + code + " (Excluded from auto clipboard sync)";
                    a.primaryActionLabel = "COPY OTP (" + code + ")";
                    return a;
                }
            }
        }

        // 5. Check Shell Command
        if (trimmed.startsWith("adb ") || trimmed.startsWith("git ") || trimmed.startsWith("curl ")
                || trimmed.startsWith("brew ") || trimmed.startsWith("docker ") || trimmed.startsWith("npm ")
                || trimmed.startsWith("ssh ") || trimmed.startsWith("python")) {
            a.type = ContentType.SHELL_CMD;
            a.badgeLabel = "SHELL CMD";
            a.transformedText = trimmed;
            a.summary = "CLI Command: " + ellipsize(trimmed, 80);
            a.primaryActionLabel = "SEND TO MACBOOK";
            return a;
        }

        // 6. Check Source Code / Stack Trace
        if (trimmed.contains("function ") || trimmed.contains("public class ") || trimmed.contains("import ")
                || trimmed.contains("const ") || trimmed.contains("def ") || trimmed.contains("Exception")
                || (trimmed.contains("{") && trimmed.contains(";"))) {
            a.type = ContentType.CODE;
            a.badgeLabel = "SOURCE CODE";
            a.transformedText = trimmed;
            a.summary = String.format(Locale.US, "Code Snippet (%d chars, %d lines)",
                    trimmed.length(), trimmed.split("\n").length);
            a.primaryActionLabel = "EXPLAIN WITH AI";
            return a;
        }

        // 7. Ordinary Text
        a.type = ContentType.TEXT;
        a.badgeLabel = "TEXT (" + trimmed.length() + "c)";
        a.transformedText = trimmed;
        a.summary = ellipsize(trimmed, 100);
        a.primaryActionLabel = "SUMMARIZE / SEND TO MAC";
        return a;
    }

    public static String extractOtpFromNotification(String text) {
        if (TextUtils.isEmpty(text)) return null;
        String lower = text.toLowerCase(Locale.US);
        if (!lower.contains("otp") && !lower.contains("code") && !lower.contains("verification")
                && !lower.contains("passcode") && !lower.contains("pin")) {
            return null;
        }
        Matcher m = Pattern.compile("\\b(\\d{4,8})\\b").matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static String decodeJwtPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return "Invalid JWT";
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return new JSONObject(json).toString(2);
        } catch (Exception e) {
            return "JWT Decode Error: " + e.getMessage();
        }
    }

    public static String stripTrackingParams(String urlStr) {
        try {
            Uri uri = Uri.parse(urlStr);
            if (uri.getQuery() == null) return urlStr;
            Uri.Builder b = uri.buildUpon().clearQuery();
            for (String name : uri.getQueryParameterNames()) {
                String low = name.toLowerCase(Locale.US);
                if (low.startsWith("utm_") || low.equals("fbclid") || low.equals("gclid")
                        || low.equals("igsh") || low.equals("igshid") || low.equals("si")
                        || low.equals("ref") || low.equals("mc_cid")) {
                    continue;
                }
                for (String val : uri.getQueryParameters(name)) {
                    b.appendQueryParameter(name, val);
                }
            }
            return b.build().toString();
        } catch (Exception e) {
            return urlStr;
        }
    }

    private static String ellipsize(String s, int max) {
        String oneLine = s.replace('\n', ' ').trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 3) + "...";
    }

    public static String sha256(String text) {
        if (TextUtils.isEmpty(text)) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.trim().hashCode());
        }
    }

    public static boolean isSensitiveOrOtp(String text) {
        if (TextUtils.isEmpty(text)) return false;
        Analysis a = classify(text);
        return a.isSensitive || a.type == ContentType.OTP || a.type == ContentType.SENSITIVE_SECRET;
    }

    public static synchronized boolean isDuplicateSync(String text) {
        String h = sha256(text);
        return !TextUtils.isEmpty(h) && h.equals(lastSyncedHash);
    }

    public static synchronized void markSynced(Context ctx, String text, String direction) {
        if (TextUtils.isEmpty(text)) return;
        Analysis a = classify(text);
        lastSyncedHash = a.sha256Hash;
        if (a.isSensitive) return; // Never store sensitive or OTP content in clipboard history

        int retentionMin = ctx.getSharedPreferences("aayush_os_mac", Context.MODE_PRIVATE)
                .getInt("clip_retention_min", 30);
        if (retentionMin <= 0) return;

        long now = System.currentTimeMillis();
        long exp = now + (retentionMin * 60_000L);
        pruneExpiredHistoryLocked(now);
        Iterator<HistoryItem> it = CLIP_HISTORY.iterator();
        while (it.hasNext()) {
            if (it.next().hash.equals(a.sha256Hash)) {
                it.remove();
            }
        }
        CLIP_HISTORY.add(0, new HistoryItem(
                ellipsize(a.rawText, 120),
                a.badgeLabel,
                direction,
                now,
                exp,
                a.sha256Hash
        ));
        while (CLIP_HISTORY.size() > 15) {
            CLIP_HISTORY.remove(CLIP_HISTORY.size() - 1);
        }
    }

    public static synchronized List<HistoryItem> getHistory(Context ctx) {
        pruneExpiredHistoryLocked(System.currentTimeMillis());
        return new ArrayList<>(CLIP_HISTORY);
    }

    public static synchronized void clearHistory() {
        CLIP_HISTORY.clear();
        lastSyncedHash = "";
    }

    private static void pruneExpiredHistoryLocked(long nowMs) {
        Iterator<HistoryItem> it = CLIP_HISTORY.iterator();
        while (it.hasNext()) {
            if (it.next().expiresAtMs <= nowMs) {
                it.remove();
            }
        }
    }
}
