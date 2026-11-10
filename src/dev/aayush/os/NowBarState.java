package dev.aayush.os;

import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.text.TextUtils;

/**
 * Shared live state & event bus for Dynamic Now Bar V2.
 * Tracks MediaSessions, OTPs, Downloads, Timers, F1 Race Session, and Incoming Notification Conflicts.
 */
public final class NowBarState {

    public interface StateListener {
        void onNowBarStateChanged();
    }

    private static volatile java.lang.ref.WeakReference<StateListener> listenerRef = null;

    public static void setListener(StateListener l) {
        listenerRef = (l != null) ? new java.lang.ref.WeakReference<>(l) : null;
    }

    public static StateListener getListener() {
        java.lang.ref.WeakReference<StateListener> ref = listenerRef;
        return (ref != null) ? ref.get() : null;
    }

    // Active / Incoming Phone Call State
    public static final int CALL_STATE_IDLE = 0;
    public static final int CALL_STATE_INCOMING = 1;
    public static final int CALL_STATE_ACTIVE = 2;

    public static volatile int callState = CALL_STATE_IDLE;
    public static volatile String callCaller = "";
    public static volatile String callSubtext = "";
    public static volatile String callAppLabel = "Phone";
    public static volatile String callNotificationKey = null;
    public static volatile long callStartWallMs = 0L;
    public static volatile android.app.PendingIntent callAnswerIntent = null;
    public static volatile android.app.PendingIntent callDeclineIntent = null;
    public static volatile android.app.PendingIntent callContentIntent = null;

    // Active Media Session
    public static volatile String mediaTitle = "";
    public static volatile String mediaArtist = "";
    public static volatile Bitmap mediaArtwork = null;
    public static volatile boolean mediaPlaying = false;
    public static volatile long lastMediaActiveUptimeMs = 0L;
    public static volatile MediaController activeController = null;

    // Active Download progress from notifications (NB-TMR-02)
    public static volatile String downloadLabel = null;
    public static volatile String downloadPkg = null;
    public static volatile String downloadNotifKey = null;
    public static volatile int downloadPct = -1;
    public static volatile long downloadUpdatedUptimeMs = 0L;

    // Incoming Notification Conflict Window (Heads-Up Yield)
    public static volatile long incomingNotificationUntilUptimeMs = 0L;
    public static volatile String lastConflictNotifKey = null;
    public static volatile String lastIncomingTitle = null;
    public static volatile int activeStatusBarNotifCount = 0;

    // Notification Shade / System Dialog open state
    public static volatile boolean notificationShadeOpen = false;

    // Interactive French Press / Custom Timer
    public static volatile boolean timerRunning = false;
    public static volatile long timerStartWallMs = 0L;
    public static volatile long timerDurationSec = 240L; // 4:00 French Press default
    public static volatile String timerLabel = "FRENCH PRESS";
    public static volatile long timerCompletedUptimeMs = 0L;

    // Race-Day F1 Ticker (populated by F1 module)
    public static volatile String f1Ticker = "#81 PIASTRI • MCLAREN F1";
    public static volatile boolean f1SessionLive = false;

    public static void notifyStateChanged() {
        StateListener l = getListener();
        if (l != null) {
            l.onNowBarStateChanged();
        }
    }

    public static void triggerIncomingNotificationYield(String key, String title, long durationMs) {
        lastConflictNotifKey = key;
        lastIncomingTitle = title;
        incomingNotificationUntilUptimeMs = SystemClock.uptimeMillis() + durationMs;
        notifyStateChanged();
    }

    public static void clearNotificationConflictIfMatches(String key) {
        if (key != null && key.equals(lastConflictNotifKey)) {
            incomingNotificationUntilUptimeMs = 0L;
            lastConflictNotifKey = null;
            notifyStateChanged();
        }
    }

    public static boolean isNotificationConflictActive() {
        return SystemClock.uptimeMillis() < incomingNotificationUntilUptimeMs;
    }

    public static boolean isCallActive() {
        return callState != CALL_STATE_IDLE;
    }

    public static boolean isCallIncoming() {
        return callState == CALL_STATE_INCOMING;
    }

    public static int getCallDurationSec() {
        if (callState != CALL_STATE_ACTIVE || callStartWallMs == 0L) return 0;
        return (int) Math.max(0, (System.currentTimeMillis() - callStartWallMs) / 1000L);
    }

    public static void updateCall(String notifKey, String caller, String subtext, String appLabel,
                                  boolean incoming,
                                  android.app.PendingIntent answer,
                                  android.app.PendingIntent decline,
                                  android.app.PendingIntent content) {
        callNotificationKey = notifKey;
        callCaller = (caller != null && !caller.trim().isEmpty()) ? caller.trim() : "Incoming Call";
        callSubtext = (subtext != null) ? subtext.trim() : "";
        callAppLabel = (appLabel != null && !appLabel.trim().isEmpty()) ? appLabel.trim() : "Phone";
        callAnswerIntent = answer;
        callDeclineIntent = decline;
        callContentIntent = content;

        int newState = incoming ? CALL_STATE_INCOMING : CALL_STATE_ACTIVE;
        if (callState != CALL_STATE_ACTIVE && newState == CALL_STATE_ACTIVE) {
            callStartWallMs = System.currentTimeMillis();
        } else if (newState == CALL_STATE_INCOMING) {
            callStartWallMs = 0L;
        }
        callState = newState;
        notifyStateChanged();
    }

    public static void clearCallIfMatches(String key) {
        if (key != null && key.equals(callNotificationKey)) {
            clearCall();
        }
    }

    public static void clearCall() {
        if (callState != CALL_STATE_IDLE) {
            callState = CALL_STATE_IDLE;
            callCaller = "";
            callSubtext = "";
            callNotificationKey = null;
            callStartWallMs = 0L;
            callAnswerIntent = null;
            callDeclineIntent = null;
            callContentIntent = null;
            notifyStateChanged();
        }
    }

    public static volatile boolean isStopwatch = false;

    public static void startTimer(String label, long durationSec) {
        timerLabel = label;
        timerDurationSec = durationSec;
        timerStartWallMs = System.currentTimeMillis();
        isStopwatch = false;
        timerRunning = true;
        notifyStateChanged();
    }

    public static void startStopwatch(String label) {
        timerLabel = (label != null && !label.isEmpty()) ? label : "STOPWATCH";
        timerDurationSec = 0L;
        timerStartWallMs = System.currentTimeMillis();
        isStopwatch = true;
        timerRunning = true;
        notifyStateChanged();
    }

    public static int getStopwatchElapsedSec() {
        if (!timerRunning || !isStopwatch) return 0;
        return (int) Math.max(0, (System.currentTimeMillis() - timerStartWallMs) / 1000L);
    }

    public static void startFrenchPressTimer() {
        startTimer("FRENCH PRESS", 240L);
    }

    public static void stopTimer() {
        timerRunning = false;
        isStopwatch = false;
        notifyStateChanged();
    }

    public static int getTimerRemainingSec() {
        if (!timerRunning) return 0;
        long elapsedSec = (System.currentTimeMillis() - timerStartWallMs) / 1000L;
        long rem = timerDurationSec - elapsedSec;
        if (rem <= 0) {
            timerRunning = false;
            timerCompletedUptimeMs = SystemClock.uptimeMillis();
            notifyStateChanged();
            return 0;
        }
        return (int) rem;
    }

    public static boolean isTimerJustCompleted() {
        return timerCompletedUptimeMs > 0 && (SystemClock.uptimeMillis() - timerCompletedUptimeMs < 12000L);
    }

    public static String getFrenchPressStage() {
        if (isTimerJustCompleted()) return "TIMER READY!";
        if (!timerRunning) return "IDLE";
        if (!"FRENCH PRESS".equalsIgnoreCase(timerLabel)) {
            return timerLabel;
        }
        long elapsedSec = (System.currentTimeMillis() - timerStartWallMs) / 1000L;
        if (elapsedSec < 30) return "BLOOM (POUR 1/3)";
        if (elapsedSec < 35) return "STIR CRUST";
        if (elapsedSec < 210) return "STEEP";
        return "PLUNGE & POUR!";
    }

    public static boolean isMediaActiveOrRecent() {
        if (TextUtils.isEmpty(mediaTitle)) return false;
        if (mediaPlaying) return true;
        // Retain recently paused media in Now Bar for 3 minutes (180s)
        return lastMediaActiveUptimeMs > 0 && (SystemClock.uptimeMillis() - lastMediaActiveUptimeMs < 180000L);
    }

    public static int getActiveDownloadPct() {
        if (downloadPct >= 0 && (SystemClock.uptimeMillis() - downloadUpdatedUptimeMs) < 15000L) {
            return downloadPct;
        }
        downloadPct = -1;
        downloadPkg = null;
        downloadNotifKey = null;
        return -1;
    }

    public static void clearDownload() {
        downloadPct = -1;
        downloadLabel = null;
        downloadPkg = null;
        downloadNotifKey = null;
        notifyStateChanged();
    }

    private static final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            refreshMediaFields(activeController);
            notifyStateChanged();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            refreshMediaFields(activeController);
            notifyStateChanged();
        }
    };

    public static void updateFromMediaController(MediaController mc) {
        if (activeController != null && activeController != mc) {
            try {
                activeController.unregisterCallback(mediaCallback);
            } catch (Exception ignored) {}
        }
        activeController = mc;
        if (mc == null) {
            mediaPlaying = false;
            mediaTitle = "";
            mediaArtist = "";
            mediaArtwork = null;
            notifyStateChanged();
            return;
        }
        try {
            mc.registerCallback(mediaCallback);
        } catch (Exception ignored) {}
        refreshMediaFields(mc);
        notifyStateChanged();
    }

    private static void refreshMediaFields(MediaController mc) {
        if (mc == null) return;
        PlaybackState ps = mc.getPlaybackState();
        mediaPlaying = (ps != null && ps.getState() == PlaybackState.STATE_PLAYING);
        if (mediaPlaying) {
            lastMediaActiveUptimeMs = SystemClock.uptimeMillis();
        }
        MediaMetadata meta = mc.getMetadata();
        if (meta != null) {
            CharSequence t = meta.getText(MediaMetadata.METADATA_KEY_TITLE);
            CharSequence a = meta.getText(MediaMetadata.METADATA_KEY_ARTIST);
            mediaTitle = t != null ? t.toString() : "";
            mediaArtist = a != null ? a.toString() : "";
            mediaArtwork = extractAlbumArt(meta);
        } else {
            mediaArtwork = null;
        }
    }

    private static Bitmap extractAlbumArt(MediaMetadata meta) {
        if (meta == null) return null;
        try {
            Bitmap bm = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (bm == null) {
                bm = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            }
            if (bm != null && (bm.getWidth() > 128 || bm.getHeight() > 128)) {
                return Bitmap.createScaledBitmap(bm, 96, 96, true);
            }
            return bm;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void togglePlayPause() {
        MediaController mc = activeController;
        if (mc == null) return;
        PlaybackState ps = mc.getPlaybackState();
        if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
            mc.getTransportControls().pause();
        } else {
            mc.getTransportControls().play();
        }
    }

    public static void skipNext() {
        MediaController mc = activeController;
        if (mc != null) mc.getTransportControls().skipToNext();
    }

    public static void skipPrev() {
        MediaController mc = activeController;
        if (mc != null) mc.getTransportControls().skipToPrevious();
    }
}
