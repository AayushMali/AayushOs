package dev.aayush.os;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Event-driven NotificationListenerService for Aayush OS.
 * Captures MediaSessions, active download progress, incoming notification conflicts,
 * routes OTPs exclusively to the Mac OTP Inbox (never Now Bar), and forwards permitted
 * non-OTP notifications to the Mac Notification Centre.
 */
public class NowBarNotificationListener extends NotificationListenerService {

    private static volatile NowBarNotificationListener activeInstance = null;
    private static final Pattern LINK_EXTRACT_PATTERN = Pattern.compile("(https?://[^\\s]+)", Pattern.CASE_INSENSITIVE);

    private MediaSessionManager mediaSessionManager;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            this::selectPrimaryMediaController;

    public static void dismissNotificationByKey(String key) {
        NowBarNotificationListener inst = activeInstance;
        if (inst != null && !TextUtils.isEmpty(key)) {
            try {
                inst.cancelNotification(key);
            } catch (Exception ignored) {}
        }
    }

    public static boolean toggleDnd() {
        NowBarNotificationListener inst = activeInstance;
        if (inst == null) return false;
        try {
            int current = inst.getCurrentInterruptionFilter();
            int next = (current == INTERRUPTION_FILTER_ALL || current == INTERRUPTION_FILTER_UNKNOWN)
                    ? INTERRUPTION_FILTER_PRIORITY : INTERRUPTION_FILTER_ALL;
            inst.requestInterruptionFilter(next);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isDndActive() {
        NowBarNotificationListener inst = activeInstance;
        if (inst == null) return false;
        try {
            int current = inst.getCurrentInterruptionFilter();
            return current != INTERRUPTION_FILTER_ALL && current != INTERRUPTION_FILTER_UNKNOWN;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        activeInstance = this;
        try {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (mediaSessionManager != null) {
                ComponentName cn = new ComponentName(this, NowBarNotificationListener.class);
                mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, cn);
                selectPrimaryMediaController(mediaSessionManager.getActiveSessions(cn));
            }
            refreshActiveNotifCount();
        } catch (Exception ignored) {}
    }

    private void refreshActiveNotifCount() {
        try {
            StatusBarNotification[] list = getActiveNotifications();
            int count = 0;
            if (list != null) {
                for (StatusBarNotification s : list) {
                    if (s.isClearable() && !("dev.aayush.os".equals(s.getPackageName()) && s.getId() == 8101)) {
                        count++;
                    }
                }
            }
            NowBarState.activeStatusBarNotifCount = count;
        } catch (Exception ignored) {}
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (activeInstance == this) {
            activeInstance = null;
        }
        try {
            if (mediaSessionManager != null) {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
            }
        } catch (Exception ignored) {}
    }

    private static volatile String lastActiveMediaPkg = null;

    private void selectPrimaryMediaController(List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) {
            NowBarState.updateFromMediaController(null);
            return;
        }

        // 1. Look for actively playing media controller (NB-MED-01)
        for (MediaController mc : controllers) {
            android.media.session.PlaybackState ps = mc.getPlaybackState();
            if (ps != null && ps.getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                lastActiveMediaPkg = mc.getPackageName();
                NowBarState.updateFromMediaController(mc);
                return;
            }
        }

        // 2. If nothing is actively playing, prefer the most recently active session package
        if (lastActiveMediaPkg != null) {
            for (MediaController mc : controllers) {
                if (lastActiveMediaPkg.equals(mc.getPackageName())) {
                    android.media.session.PlaybackState ps = mc.getPlaybackState();
                    if (ps != null && (ps.getState() == android.media.session.PlaybackState.STATE_PAUSED
                            || ps.getState() == android.media.session.PlaybackState.STATE_BUFFERING)) {
                        NowBarState.updateFromMediaController(mc);
                        return;
                    }
                }
            }
        }

        // 3. Fallback to any valid paused controller with real metadata (avoiding empty background stubs)
        for (MediaController mc : controllers) {
            android.media.session.PlaybackState ps = mc.getPlaybackState();
            if (ps != null && (ps.getState() == android.media.session.PlaybackState.STATE_PAUSED
                    || ps.getState() == android.media.session.PlaybackState.STATE_BUFFERING)) {
                if (mc.getMetadata() != null) {
                    lastActiveMediaPkg = mc.getPackageName();
                    NowBarState.updateFromMediaController(mc);
                    return;
                }
            }
        }

        NowBarState.updateFromMediaController(null);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        // Ignore our own persistent foreground service notification (ID 8101)
        if ("dev.aayush.os".equals(sbn.getPackageName()) && sbn.getId() == 8101) {
            return;
        }
        refreshActiveNotifCount();

        Notification n = sbn.getNotification();
        Bundle extras = n.extras;
        if (extras == null) return;

        // Refresh MediaSessions if a media notification arrives
        try {
            if (mediaSessionManager != null) {
                ComponentName cn = new ComponentName(this, NowBarNotificationListener.class);
                selectPrimaryMediaController(mediaSessionManager.getActiveSessions(cn));
            }
        } catch (Exception ignored) {}

        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String titleStr = titleCs != null ? titleCs.toString() : "";
        String bodyStr = bigTextCs != null ? bigTextCs.toString() : (textCs != null ? textCs.toString() : "");
        String combined = (titleStr + " " + bodyStr).trim();

        // Respect Android notification redaction / secret visibility restrictions
        boolean isRedacted = (n.visibility == Notification.VISIBILITY_SECRET)
                || combined.toLowerCase().contains("contents hidden")
                || combined.toLowerCase().contains("sensitive notification");

        String pkg = sbn.getPackageName();
        String appLabel = resolveAppLabel(pkg);

        // 1. Detect Phone Call Notifications (Google Dialer, Telecom, WhatsApp, VoIP Calls)
        boolean isCall = Notification.CATEGORY_CALL.equals(n.category)
                || extras.containsKey(Notification.EXTRA_CALL_PERSON)
                || extras.containsKey(Notification.EXTRA_CALL_TYPE)
                || extras.containsKey("android.callType")
                || (pkg != null && (pkg.contains("dialer") || pkg.contains("telecom") || pkg.contains("phone"))
                    && (n.actions != null && n.actions.length > 0));

        if (isCall) {
            handleCallNotification(sbn, n, extras, pkg, appLabel);
            return;
        }

        // 2. Phase 5 Strict Policy: Route OTPs EXCLUSIVELY to Mac OTP Inbox (NEVER to Now Bar!)
        if (!isRedacted) {
            String otp = SmartClipboard.extractOtpFromNotification(combined);
            if (!TextUtils.isEmpty(otp)) {
                MacCompanion.forwardOtpToMac(this, otp, appLabel, pkg, titleStr, null);
                // Still yield the Now Bar if a heads-up notification popup is shown by Android
                if (sbn.isClearable()) {
                    NowBarState.triggerIncomingNotificationYield(sbn.getKey(), titleStr, 5000L);
                }
                return;
            }
        }

        // 2. Check for Download Progress bar (NB-TMR-02)
        int progress = extras.getInt(Notification.EXTRA_PROGRESS, -1);
        int max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1);
        boolean indet = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false);
        if (!indet && max > 0 && progress >= 0 && progress <= max) {
            NowBarState.downloadPct = (progress * 100) / max;
            NowBarState.downloadPkg = sbn.getPackageName();
            NowBarState.downloadNotifKey = sbn.getKey();
            NowBarState.downloadLabel = !TextUtils.isEmpty(titleStr) ? titleStr : "Downloading";
            NowBarState.downloadUpdatedUptimeMs = SystemClock.uptimeMillis();
            NowBarState.notifyStateChanged();
            return;
        } else if (sbn.getPackageName().equals(NowBarState.downloadPkg)
                || sbn.getKey().equals(NowBarState.downloadNotifKey)) {
            // Updated notification from download package without progress means download completed/failed!
            NowBarState.clearDownload();
        }

        // 3. Incoming Notification Conflict Detection (for Heads-Up Popups) & Phase 8 Mac Notification Centre
        boolean isMediaStyle = extras.containsKey(Notification.EXTRA_MEDIA_SESSION);
        boolean isHeadsUp = (n.fullScreenIntent != null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Ranking ranking = new Ranking();
            if (getCurrentRanking() != null && getCurrentRanking().getRanking(sbn.getKey(), ranking)) {
                isHeadsUp = isHeadsUp || (ranking.getImportance() >= android.app.NotificationManager.IMPORTANCE_HIGH);
            }
        } else {
            isHeadsUp = isHeadsUp || (n.priority >= Notification.PRIORITY_HIGH);
        }

        if (!isMediaStyle && isHeadsUp && sbn.isClearable()) {
            NowBarState.triggerIncomingNotificationYield(sbn.getKey(), titleStr, 4000L);
        }

        if (!isMediaStyle && !isRedacted && !TextUtils.isEmpty(bodyStr) && !"dev.aayush.os".equals(pkg)) {
            String url = "";
            Matcher m = LINK_EXTRACT_PATTERN.matcher(bodyStr);
            if (m.find()) {
                url = m.group(1);
            }
            MacCompanion.forwardNotificationToMac(
                    this, sbn.getKey(), appLabel, pkg, titleStr, bodyStr, url);
        }
    }

    private void handleCallNotification(StatusBarNotification sbn, Notification n, Bundle extras, String pkg, String appLabel) {
        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (TextUtils.isEmpty(titleCs) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                android.app.Person person = extras.getParcelable(Notification.EXTRA_CALL_PERSON);
                if (person != null && person.getName() != null) {
                    titleCs = person.getName();
                }
            } catch (Exception ignored) {}
        }
        if (TextUtils.isEmpty(titleCs)) {
            titleCs = extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
        }
        String caller = (titleCs != null && !TextUtils.isEmpty(titleCs.toString().trim()))
                ? titleCs.toString().trim() : "Incoming Call";

        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(textCs)) textCs = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        if (TextUtils.isEmpty(textCs)) textCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String subtext = (textCs != null) ? textCs.toString().trim() : "";

        boolean isIncoming = false;
        int callType = extras.getInt(Notification.EXTRA_CALL_TYPE, -1);
        if (callType == 1 || callType == 3) { // 1 = CALL_TYPE_INCOMING, 3 = CALL_TYPE_SCREENING
            isIncoming = true;
        } else if (callType == 2) { // 2 = CALL_TYPE_ONGOING
            isIncoming = false;
        } else {
            String combinedLower = (caller + " " + subtext).toLowerCase(java.util.Locale.US);
            if (combinedLower.contains("incoming") || combinedLower.contains("ringing") || combinedLower.contains("calling")) {
                isIncoming = true;
            }
            if (n.actions != null) {
                for (Notification.Action a : n.actions) {
                    if (a != null && a.title != null) {
                        String at = a.title.toString().toLowerCase(java.util.Locale.US);
                        if (at.contains("answer") || at.contains("accept")) {
                            isIncoming = true;
                            break;
                        }
                    }
                }
            }
        }

        android.app.PendingIntent answerIntent = null;
        android.app.PendingIntent declineIntent = null;
        if (n.actions != null) {
            for (Notification.Action a : n.actions) {
                if (a == null) continue;
                String at = (a.title != null) ? a.title.toString().toLowerCase(java.util.Locale.US) : "";
                if (at.contains("answer") || at.contains("accept")) {
                    answerIntent = a.actionIntent;
                } else if (at.contains("decline") || at.contains("reject") || at.contains("dismiss")
                        || at.contains("hang up") || at.contains("end")) {
                    declineIntent = a.actionIntent;
                }
            }
            if (answerIntent == null && n.actions.length >= 2) {
                for (Notification.Action a : n.actions) {
                    if (a != null && a.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_CALL) {
                        answerIntent = a.actionIntent;
                    } else if (a != null && a.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_MUTE) {
                        declineIntent = a.actionIntent;
                    }
                }
            }
            if (declineIntent == null && n.actions.length > 0) {
                for (Notification.Action a : n.actions) {
                    if (a != null && a.actionIntent != null && a.actionIntent != answerIntent) {
                        declineIntent = a.actionIntent;
                        break;
                    }
                }
            }
        }

        android.app.PendingIntent contentIntent = (n.contentIntent != null) ? n.contentIntent : n.fullScreenIntent;

        NowBarState.updateCall(sbn.getKey(), caller, subtext, appLabel, isIncoming, answerIntent, declineIntent, contentIntent);

        // Forward call alert to Mac Companion
        if (!"dev.aayush.os".equals(pkg)) {
            String macTitle = (isIncoming ? "📞 Incoming Call: " : "📞 Active Call: ") + caller;
            String macBody = !TextUtils.isEmpty(subtext) ? subtext : (isIncoming ? "Ringing on Pixel 7" : "Call in progress on Pixel 7");
            MacCompanion.forwardNotificationToMac(this, sbn.getKey(), appLabel, pkg, macTitle, macBody, "");
        }
    }

    private String resolveAppLabel(String pkg) {
        if (TextUtils.isEmpty(pkg)) return "Android";
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence cs = pm.getApplicationLabel(ai);
            if (cs != null) return cs.toString();
        } catch (Exception ignored) {}
        return pkg;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        refreshActiveNotifCount();
        if (sbn.getKey().equals(NowBarState.downloadNotifKey)
                || sbn.getPackageName().equals(NowBarState.downloadPkg)) {
            NowBarState.clearDownload();
        }
        NowBarState.clearNotificationConflictIfMatches(sbn.getKey());
        NowBarState.clearCallIfMatches(sbn.getKey());
    }
}
