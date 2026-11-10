package dev.aayush.os;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Module 1: Dynamic Now Bar V2.3 Overlay Service for Google Pixel 7 ('panther').
 * Engineered specifically around Android's DisplayCutout & WindowMetrics:
 * - Exact camera center alignment: X = 540px (205.7dp), Y = 68px (25.9dp)
 * - Custom boolean Path Dynamic Island renderer (wing capsule ∪ camera collar − screen protector hole)
 *   so telemetry wings visually connect to the camera cutout while leaving the lens + circular
 *   screen protector border 100% transparent and unobstructed.
 * - Camera-anchored Dynamic Island architecture: COMPACT_MODE (default) and CAMERA_MODE (expanded wings)
 * - Intelligent Notification-Aware auto-yield & symmetrical wing compaction
 * - Swipe-down gesture pass-through to open Android Notification Shade
 */
public class NowBarOverlayService extends Service implements NowBarState.StateListener {

    public static final String ACTION_TOGGLE = "dev.aayush.os.NOWBAR_TOGGLE";
    public static final String ACTION_REFRESH = "dev.aayush.os.NOWBAR_REFRESH";
    private static final String CHANNEL_ID = "aayush_os_nowbar";

    public static volatile boolean isRunning = false;
    public static volatile NowBarOverlayService activeInstance = null;

    private WindowManager wm;
    private WindowManager.LayoutParams wmParams;
    private KeyguardManager keyguardManager;
    private NowBarConfig cfg;
    private NowBarConfig.HardwareGeometry hwGeo;

    private LinearLayout rootOverlay;
    private DynamicIslandPillView compactPill;
    private LinearLayout leftWingSlot;
    private LinearLayout leftWingBox;
    private View cameraCutoutSpacer;
    private LinearLayout rightWingSlot;
    private LinearLayout rightWingBox;
    private LinearLayout expandedDeck;

    // Compact Pill TextViews
    private TextView leftWingText;
    private TextView rightWingText;

    // Expanded Deck Views
    private TextView expTelemetryText;
    private LinearLayout expMediaSection;
    private ImageView expMediaArtView;
    private TextView expMediaText;
    private TextView expMediaArtistText;
    private TextView expPlayPauseBtn;
    private TextView expClipBadge;
    private TextView expClipSummary;
    private TextView expClipActionBtn;
    private TextView expTimerStatus;
    private TextView expTimerBtn;

    // Expanded Deck Call Views (Dynamic Island Call Pop-up)
    private LinearLayout callDeckSection;
    private LinearLayout normalDeckSections;
    private TextView expCallCallerText;
    private TextView expCallStatusText;
    private TextView expCallDeclineBtn;
    private TextView expCallAnswerBtn;

    private boolean isExpanded = false;
    private boolean isExpandedForCall = false;
    private boolean isScreenOn = true;
    private boolean isYieldingForNotification = false;
    private boolean lastWasCharging = false;
    private long chargingBannerStartUptimeMs = 0L;
    private SeekBar expVolSeekBar;
    private TextView expVolValText;
    private boolean isVolTracking = false;
    private int lastAppliedWingWidthDp = -1;

    private static final int HAPTIC_CLICK = 1;
    private static final int HAPTIC_TICK = 2;
    private static final int HAPTIC_HEAVY = 3;

    private TextView expTimerCancelBtn;
    private HardwareMonitor hwMonitor;
    private SmartClipboard.Analysis lastClipAnalysis = new SmartClipboard.Analysis();

    /**
     * Custom Canvas Dynamic Island Pill View.
     * In CAMERA_MODE and COMPACT_MODE, uses Path.Op boolean geometry to unite the left/right
     * telemetry wings with a sleek circular collar around the camera cutout while subtracting
     * the exact circular screen protector hole (100% transparent inside cameraClearanceDp).
     */
    private final class DynamicIslandPillView extends LinearLayout {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guideRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guideLensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path outerCapsulePath = new Path();
        private final Path islandPath = new Path();
        private final Path holePath = new Path();
        private final RectF tmpRect = new RectF();

        private float downX, downY;
        private boolean swipeTriggered;
        private boolean longPressTriggered;

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!swipeTriggered) {
                    longPressTriggered = true;
                    triggerHaptic(HAPTIC_HEAVY);
                    Toast.makeText(getContext(), "⚡ AayushOS Hub", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(getContext(), MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(i);
                }
            }
        };

        DynamicIslandPillView(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
            setClickable(true);
            setFocusable(false);
            fillPaint.setStyle(Paint.Style.FILL);
            strokePaint.setStyle(Paint.Style.STROKE);
            guideRingPaint.setStyle(Paint.Style.STROKE);
            guideLensPaint.setStyle(Paint.Style.STROKE);
            float dash = Theme.dp(ctx, 3);
            guideLensPaint.setPathEffect(new DashPathEffect(new float[]{dash, dash}, 0f));
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            // Dynamically intercept touches on compact pill so child views don't drop or race events
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            android.util.Log.i("NowBarTouch", "pill onTouchEvent action=" + ev.getActionMasked() + " rawX=" + ev.getRawX() + " rawY=" + ev.getRawY());
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    swipeTriggered = false;
                    longPressTriggered = false;
                    postDelayed(longPressRunnable, 480L);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getRawX() - downX;
                    float dy = ev.getRawY() - downY;
                    if (Math.abs(dx) > Theme.dp(getContext(), 14) || Math.abs(dy) > Theme.dp(getContext(), 14)) {
                        removeCallbacks(longPressRunnable);
                    }
                    if (!swipeTriggered && !longPressTriggered) {
                        // Downward swipe (>30dp) triggers Android notification shade
                        if (dy > Theme.dp(getContext(), 30) && dy > Math.abs(dx) * 1.2f) {
                            swipeTriggered = true;
                            removeCallbacks(longPressRunnable);
                            triggerHaptic(HAPTIC_TICK);
                            if (isExpanded) setExpandedState(false);
                            expandSystemNotificationShade();
                            NowBarState.triggerIncomingNotificationYield("shade_pull", "Notification Shade", 4000L);
                        } else if (Math.abs(dx) > Theme.dp(getContext(), 34) && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                            swipeTriggered = true;
                            removeCallbacks(longPressRunnable);
                            triggerHaptic(HAPTIC_CLICK);
                            if (dx < 0) {
                                // Swipe left: next track
                                NowBarState.skipNext();
                                Toast.makeText(getContext(), "⏭ Next Track", Toast.LENGTH_SHORT).show();
                            } else {
                                // Swipe right: previous track
                                NowBarState.skipPrev();
                                Toast.makeText(getContext(), "⏮ Prev Track", Toast.LENGTH_SHORT).show();
                            }
                            handler.postDelayed(NowBarOverlayService.this::updateOverlayContent, 200L);
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    removeCallbacks(longPressRunnable);
                    if (!swipeTriggered && !longPressTriggered) {
                        float distDx = Math.abs(ev.getRawX() - downX);
                        float distDy = Math.abs(ev.getRawY() - downY);
                        float maxTapSlop = Theme.dp(getContext(), 28);
                        if (distDx < maxTapSlop && distDy < maxTapSlop) {
                            triggerHaptic(HAPTIC_CLICK);
                            onPillTapped();
                            performClick();
                        }
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(longPressRunnable);
                    swipeTriggered = false;
                    longPressTriggered = false;
                    return true;
            }
            return super.onTouchEvent(ev);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (cfg != null && cfg.isCameraContouredMode()) {
                // Touches outside the drawn pill capsule pass through transparently to Android status bar (NB-TCH-01 & NB-TCH-02)
                float x = ev.getX();
                float y = ev.getY();
                if (!tmpRect.isEmpty()) {
                    float slop = Theme.dp(getContext(), 4);
                    if (x < (tmpRect.left - slop) || x > (tmpRect.right + slop)
                            || y < (tmpRect.top - slop) || y > (tmpRect.bottom + slop)) {
                        return false;
                    }
                }
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (cfg == null) return;

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float cx = w / 2f;
            float cy = h / 2f;
            float strokeW = Theme.dp(getContext(), 1.25f);
            fillPaint.setColor(cfg.applyAlphaToColor(0x0A0A0A));
            strokePaint.setColor(Theme.PAPAYA);
            strokePaint.setStrokeWidth(strokeW);

            float holeRadius = Theme.dp(getContext(), cfg.cameraClearanceDp) / 2f;
            // Flat-topped Dynamic Island capsule: ensure outer height encloses holeRadius + 2dp bridge
            float minHalfH = holeRadius + Theme.dp(getContext(), 2.0f);
            float pillHalfH = Math.min((h - strokeW) / 2f,
                    Math.max(Theme.dp(getContext(), cfg.pillHeightDp) / 2f, minHalfH));

            int effLeftDp = getEffectiveLeftWingWidthDp();
            int effRightDp = getEffectiveRightWingWidthDp();
            float leftW = cfg.showLeftWing ? Theme.dp(getContext(), effLeftDp) : 0f;
            float rightW = cfg.showRightWing ? Theme.dp(getContext(), effRightDp) : 0f;

            float leftOff = Theme.dp(getContext(), cfg.leftWingOffsetDp);
            float rightOff = Theme.dp(getContext(), cfg.rightWingOffsetDp);

            float leftEdge = cfg.showLeftWing
                    ? Math.max(strokeW, cx - holeRadius - leftW + leftOff)
                    : (cx - holeRadius - Theme.dp(getContext(), 4));
            float rightEdge = cfg.showRightWing
                    ? Math.min(w - strokeW, cx + holeRadius + rightW + rightOff)
                    : (cx + holeRadius + Theme.dp(getContext(), 4));

            float pillTop = cy - pillHalfH;
            float pillBottom = cy + pillHalfH;
            float cornerR = Math.min(Theme.dp(getContext(), cfg.cornerRadiusDp), pillHalfH);

            outerCapsulePath.reset();
            tmpRect.set(leftEdge, pillTop, rightEdge, pillBottom);
            outerCapsulePath.addRoundRect(tmpRect, cornerR, cornerR, Path.Direction.CW);

            // Subtract 100% of the circular camera + screen protector clearance zone at (cx, cy)
            holePath.reset();
            holePath.addCircle(cx, cy, Math.max(4f, holeRadius), Path.Direction.CW);

            islandPath.reset();
            islandPath.op(outerCapsulePath, holePath, Path.Op.DIFFERENCE);

            canvas.drawPath(islandPath, fillPaint);
            canvas.drawPath(outerCapsulePath, strokePaint);

            // Screen protector hole remains 100% pure OLED black seamlessly blending with punch-hole (NB-POL-05)

            // Optional Screen Protector & Lens Alignment Guide Reticle
            if (cfg.showProtectorGuide) {
                float lensR = (hwGeo != null) ? hwGeo.camLensRadiusPx : Theme.dp(getContext(), 13);
                guideLensPaint.setColor(0xCC00E5FF);
                guideLensPaint.setStrokeWidth(Theme.dp(getContext(), 1.2f));
                canvas.drawCircle(cx, cy, lensR, guideLensPaint);

                guideRingPaint.setColor(Theme.TELEMETRY_GREEN);
                guideRingPaint.setStrokeWidth(Theme.dp(getContext(), 1.8f));
                canvas.drawCircle(cx, cy, holeRadius, guideRingPaint);

                // Crosshair ticks at N/S/E/W of the protector ring
                float tick = Theme.dp(getContext(), 4);
                canvas.drawLine(cx - holeRadius - tick, cy, cx - holeRadius + tick, cy, guideRingPaint);
                canvas.drawLine(cx + holeRadius - tick, cy, cx + holeRadius + tick, cy, guideRingPaint);
                canvas.drawLine(cx, cy - holeRadius - tick, cx, cy - holeRadius + tick, guideRingPaint);
                canvas.drawLine(cx, cy + holeRadius - tick, cx, cy + holeRadius + tick, guideRingPaint);
            }
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning || !isScreenOn) return;
            try {
                updateOverlayContent();
            } catch (Exception e) {
                android.util.Log.e("NowBarOverlay", "Safe recovery: error updating overlay content", e);
            }
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isPowerSave = (pm != null && pm.isPowerSaveMode());
            long delay = (NowBarState.mediaPlaying)
                    ? 320L
                    : ((isExpanded || NowBarState.timerRunning || NowBarState.isCallActive() || isYieldingForNotification)
                    ? 1000L
                    : (isPowerSave ? 5000L : 3000L));
            handler.postDelayed(this, delay);
        }
    };

    private final BroadcastReceiver systemEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String act = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(act)) {
                isScreenOn = false;
                handler.removeCallbacks(tickRunnable);
                if (rootOverlay != null) rootOverlay.setVisibility(View.GONE);
            } else if (Intent.ACTION_SCREEN_ON.equals(act) || Intent.ACTION_USER_PRESENT.equals(act)) {
                isScreenOn = true;
                handler.removeCallbacks(tickRunnable);
                handler.post(tickRunnable);
            } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(act)) {
                if (isExpanded) {
                    setExpandedState(false);
                }
            } else if ("dev.aayush.os.TEST_CALL".equals(act)) {
                String caller = intent.getStringExtra("caller");
                if (TextUtils.isEmpty(caller)) caller = "Aayush Mali";
                boolean incoming = intent.getBooleanExtra("incoming", true);
                NowBarState.updateCall("test_intent_" + System.currentTimeMillis(),
                        caller, incoming ? "Incoming Call • Mobile" : "Call in progress", "Phone", incoming, null, null, null);
            } else if ("dev.aayush.os.TEST_CALL_CLEAR".equals(act)) {
                NowBarState.clearCall();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Self-Healing Recovery Loop & Uncaught Exception Handler (NB-REL-01)
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            android.util.Log.e("NowBarRecovery", "Self-healing catch: " + throwable.getMessage(), throwable);
            try {
                if (wm != null && rootOverlay != null && rootOverlay.isAttachedToWindow()) {
                    wm.removeViewImmediate(rootOverlay);
                }
            } catch (Exception ignored) {}
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });

        hwMonitor = new HardwareMonitor(this);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        hwGeo = NowBarConfig.inspectHardwareGeometry(this);
        cfg = NowBarConfig.load(this);

        startForegroundMinimal();
        if (Settings.canDrawOverlays(this)) {
            createOverlayView();
            isRunning = true;
            activeInstance = this;
            NowBarState.setListener(this);
            getSharedPreferences("aayush_os_prefs", MODE_PRIVATE)
                    .edit().putBoolean("mod_nowbar_active", true).apply();

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            filter.addAction("dev.aayush.os.TEST_CALL");
            filter.addAction("dev.aayush.os.TEST_CALL_CLEAR");
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(systemEventReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(systemEventReceiver, filter);
            }

            handler.post(tickRunnable);
        } else {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String act = intent.getAction();
            if (ACTION_TOGGLE.equals(act)) {
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_REFRESH.equals(act)) {
                if (NowBarAccessibilityService.instance != null && wmParams != null
                        && wmParams.type != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
                    try {
                        if (wm != null && rootOverlay != null && rootOverlay.isAttachedToWindow()) {
                            wm.removeViewImmediate(rootOverlay);
                        }
                    } catch (Exception ignored) {}
                    createOverlayView();
                } else {
                    hwGeo = NowBarConfig.inspectHardwareGeometry(this);
                    cfg = NowBarConfig.load(this);
                    applyConfigToViews();
                    updateOverlayContent();
                }
            }
        }
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
        return START_STICKY;
    }

    @Override
    public void onNowBarStateChanged() {
        handler.post(() -> {
            if (isRunning && isScreenOn) {
                if (NowBarState.isCallIncoming() && !isExpanded) {
                    isExpandedForCall = true;
                    setExpandedState(true);
                } else if (!NowBarState.isCallActive() && isExpandedForCall) {
                    isExpandedForCall = false;
                    setExpandedState(false);
                }
                updateOverlayContent();
                if (NowBarState.isNotificationConflictActive()) {
                    long rem = NowBarState.incomingNotificationUntilUptimeMs - SystemClock.uptimeMillis();
                    if (rem > 0) {
                        handler.postDelayed(tickRunnable, rem + 150L);
                    }
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handler.post(() -> {
            // Auto-hide immediately in landscape orientation to prevent mid-screen punch-hole misplacement (NB-GEO-04)
            if (newConfig != null && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (rootOverlay != null) rootOverlay.setVisibility(View.GONE);
                return;
            }
            hwGeo = NowBarConfig.inspectHardwareGeometry(NowBarOverlayService.this);
            applyConfigToViews();
            updateOverlayContent();
        });
    }

    private boolean hasTriggeredTimerHaptic = false;

    private void triggerHaptic(int type) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 29) {
                    int effectId;
                    switch (type) {
                        case HAPTIC_TICK:
                            effectId = VibrationEffect.EFFECT_TICK;
                            break;
                        case HAPTIC_HEAVY:
                            effectId = VibrationEffect.EFFECT_HEAVY_CLICK;
                            break;
                        case HAPTIC_CLICK:
                        default:
                            effectId = VibrationEffect.EFFECT_CLICK;
                            break;
                    }
                    v.vibrate(VibrationEffect.createPredefined(effectId));
                    return;
                }
                v.vibrate(25L);
            }
        } catch (Exception ignored) {}
    }



    private void triggerHapticAlert() {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 100, 100, 200}, -1));
                } else {
                    v.vibrate(250L);
                }
            }
        } catch (Exception ignored) {}
    }

    private void startForegroundMinimal() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel chan = new NotificationChannel(
                        CHANNEL_ID, "Aayush OS Now Bar", NotificationManager.IMPORTANCE_MIN);
                chan.setDescription("Dynamic Now Bar camera cutout overlay");
                chan.setShowBadge(false);
                chan.enableLights(false);
                chan.enableVibration(false);
                nm.createNotificationChannel(chan);
            }
            Notification n;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                n = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("Aayush OS // Now Bar")
                        .setContentText("Dynamic Camera Island Active")
                        .setSmallIcon(R.drawable.ic_aayush_os)
                        .setOngoing(true)
                        .build();
            } else {
                n = new Notification.Builder(this)
                        .setContentTitle("Aayush OS // Now Bar")
                        .setSmallIcon(R.drawable.ic_aayush_os)
                        .build();
            }
            startForeground(8101, n);
        } catch (Exception ignored) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createOverlayView() {
        rootOverlay = new LinearLayout(this) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                android.util.Log.i("NowBarTouch", "rootOverlay dispatchTouchEvent action=" + ev.getActionMasked() + " rawX=" + ev.getRawX() + " rawY=" + ev.getRawY());
                if (ev.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    if (isExpanded) {
                        setExpandedState(false);
                        return true;
                    }
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        rootOverlay.setOrientation(LinearLayout.VERTICAL);
        rootOverlay.setGravity(Gravity.CENTER_HORIZONTAL);

        // 1. Dynamic Island Pill Container
        compactPill = new DynamicIslandPillView(this);
        compactPill.setOrientation(LinearLayout.HORIZONTAL);
        compactPill.setGravity(Gravity.CENTER_VERTICAL);

        leftWingSlot = new LinearLayout(this);
        leftWingSlot.setOrientation(LinearLayout.HORIZONTAL);
        leftWingSlot.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        leftWingBox = new LinearLayout(this);
        leftWingBox.setOrientation(LinearLayout.HORIZONTAL);
        leftWingBox.setGravity(Gravity.CENTER);

        leftWingText = new TextView(this);
        leftWingText.setText("#81");
        leftWingText.setTextColor(Theme.PAPAYA);
        leftWingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        leftWingText.setTypeface(Theme.PILL_TYPO);
        leftWingText.setFontFeatureSettings("'tnum' 1");
        leftWingText.setLetterSpacing(0.015f);
        leftWingText.setIncludeFontPadding(false);
        leftWingText.setSingleLine(true);
        leftWingText.setEllipsize(TextUtils.TruncateAt.END);
        leftWingBox.addView(leftWingText);
        leftWingSlot.addView(leftWingBox);

        // Center spacer for Pixel 7 camera cutout + circular screen protector clearance
        cameraCutoutSpacer = new View(this);

        rightWingSlot = new LinearLayout(this);
        rightWingSlot.setOrientation(LinearLayout.HORIZONTAL);
        rightWingSlot.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        rightWingBox = new LinearLayout(this);
        rightWingBox.setOrientation(LinearLayout.HORIZONTAL);
        rightWingBox.setGravity(Gravity.CENTER);

        rightWingText = new TextView(this);
        rightWingText.setText("66% • 35°C");
        rightWingText.setTextColor(Theme.TEXT_PRIMARY);
        rightWingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        rightWingText.setTypeface(Theme.PILL_TYPO);
        rightWingText.setFontFeatureSettings("'tnum' 1");
        rightWingText.setLetterSpacing(0.015f);
        rightWingText.setIncludeFontPadding(false);
        rightWingText.setSingleLine(true);
        rightWingText.setEllipsize(TextUtils.TruncateAt.END);
        rightWingBox.addView(rightWingText);
        rightWingSlot.addView(rightWingBox);

        compactPill.addView(leftWingSlot);
        compactPill.addView(cameraCutoutSpacer);
        compactPill.addView(rightWingSlot);

        // 2. Expanded Interactive Control Deck
        expandedDeck = buildExpandedDeck();
        expandedDeck.setVisibility(View.GONE);

        rootOverlay.addView(compactPill);
        rootOverlay.addView(expandedDeck);

        int overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        boolean hasAccess = (NowBarAccessibilityService.instance != null);
        int windowType = hasAccess
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        WindowManager targetWm = hasAccess
                ? (WindowManager) NowBarAccessibilityService.instance.getSystemService(Context.WINDOW_SERVICE)
                : (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        this.wm = targetWm;

        wmParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                overlayFlags,
                PixelFormat.TRANSLUCENT
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            wmParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        applyConfigToViews();

        rootOverlay.setVisibility(View.VISIBLE);
        wm.addView(rootOverlay, wmParams);
    }

    private void onPillTapped() {
        android.util.Log.i("NowBarTouch", "onPillTapped! current isExpanded=" + isExpanded);
        setExpandedState(!isExpanded);
    }

    @SuppressLint("WrongConstant")
    private void expandSystemNotificationShade() {
        try {
            Object sbService = getSystemService("statusbar");
            if (sbService != null) {
                Method expand = sbService.getClass().getMethod("expandNotificationsPanel");
                expand.invoke(sbService);
                return;
            }
        } catch (Exception ignored) {}
        try {
            Runtime.getRuntime().exec(new String[]{"cmd", "statusbar", "expand-notifications"});
        } catch (Exception ignored) {}
    }

    public void rebindAccessibilityOverlay() {
        handler.post(() -> {
            if (NowBarAccessibilityService.instance != null && wmParams != null
                    && wmParams.type != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
                try {
                    if (wm != null && rootOverlay != null && rootOverlay.isAttachedToWindow()) {
                        wm.removeViewImmediate(rootOverlay);
                    }
                } catch (Exception ignored) {}
                createOverlayView();
            }
        });
    }

    private void applyConfigToViews() {
        if (rootOverlay == null || cfg == null) return;
        if (hwGeo == null) hwGeo = NowBarConfig.inspectHardwareGeometry(this);

        int padH = Theme.dp(this, cfg.internalPaddingDp);
        int pillHeightPx = Theme.dp(this, cfg.pillHeightDp);
        boolean cameraContoured = cfg.isCameraContouredMode();
        
        // Dynamic Font Scaling Protection (NB-ACC-02):
        // Clamps effective SP size when system font scale exceeds 1.15x to avoid vertical text clipping
        float fontScale = getResources().getConfiguration().fontScale;
        float baseSp = (cfg.displayMode == NowBarConfig.DisplayMode.COMPACT_MODE) ? 10.0f : 10.5f;
        float scaledSp = (fontScale > 1.15f) ? (baseSp / (fontScale / 1.05f)) : baseSp;
        leftWingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSp);
        rightWingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSp);
        leftWingText.setIncludeFontPadding(false);
        rightWingText.setIncludeFontPadding(false);

        // Camera-Contoured Dynamic Island:
        // Dynamic Island Capsule Enclosing clearDiameterPx + 8dp collar (4dp top, 4dp bottom - NB-GEO-01)
        int clearDiameterPx = Theme.dp(this, cfg.cameraClearanceDp);
        int minCapsuleHeightPx = clearDiameterPx + Theme.dp(this, 8);
        int boxHeightPx = Math.max(pillHeightPx, minCapsuleHeightPx);

        int effLeftW = getEffectiveLeftWingWidthDp();
        int effRightW = getEffectiveRightWingWidthDp();
        lastAppliedWingWidthDp = effLeftW;

        int leftWingPx = cfg.showLeftWing ? Theme.dp(this, effLeftW) : 0;
        int rightWingPx = cfg.showRightWing ? Theme.dp(this, effRightW) : 0;
        int maxSlotPx = Math.max(leftWingPx, rightWingPx);

        compactPill.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, boxHeightPx));
        compactPill.setPadding(0, 0, 0, 0);

        // Equal-width left/right slots guarantee cameraCutoutSpacer stays dead-center at X=540px!
        leftWingSlot.setLayoutParams(new LinearLayout.LayoutParams(maxSlotPx, boxHeightPx));
        leftWingBox.setVisibility(cfg.showLeftWing ? View.VISIBLE : View.INVISIBLE);
        leftWingBox.setBackgroundColor(Color.TRANSPARENT);
        leftWingBox.setPadding(padH, 0, Math.max(Theme.dp(this, 4), padH - Theme.dp(this, 2)), 0);
        leftWingBox.setLayoutParams(new LinearLayout.LayoutParams(leftWingPx, boxHeightPx));
        leftWingBox.setTranslationX(Theme.dp(this, cfg.leftWingOffsetDp));

        cameraCutoutSpacer.setVisibility(View.VISIBLE);
        cameraCutoutSpacer.setBackgroundColor(Color.TRANSPARENT);
        cameraCutoutSpacer.setLayoutParams(new LinearLayout.LayoutParams(clearDiameterPx, boxHeightPx));

        rightWingSlot.setLayoutParams(new LinearLayout.LayoutParams(maxSlotPx, boxHeightPx));
        rightWingBox.setVisibility(cfg.showRightWing ? View.VISIBLE : View.INVISIBLE);
        rightWingBox.setBackgroundColor(Color.TRANSPARENT);
        rightWingBox.setPadding(Math.max(Theme.dp(this, 4), padH - Theme.dp(this, 2)), 0, padH, 0);
        rightWingBox.setLayoutParams(new LinearLayout.LayoutParams(rightWingPx, boxHeightPx));
        rightWingBox.setTranslationX(Theme.dp(this, cfg.rightWingOffsetDp));

        compactPill.invalidate();

        // Expanded Control Deck Sizing (clamped within screen bounds)
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxAllowedWidthPx = dm.widthPixels - Theme.dp(this, 20);
        int desiredExpWidthPx = Math.min(maxAllowedWidthPx, Theme.dp(this, cfg.expandedWidthDp));
        LinearLayout.LayoutParams deckLp = new LinearLayout.LayoutParams(
                desiredExpWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        deckLp.topMargin = Theme.dp(this, 8);
        expandedDeck.setLayoutParams(deckLp);
        expandedDeck.setBackground(Theme.cardDrawable(
                this, cfg.applyAlphaToColor(0x101010), Theme.PAPAYA, 22f, 1.5f));

        // Hardware-Relative Window Positioning:
        // In Top Anchor mode, targetCenterYPx = hwGeo.camCenterYPx (68px on Pixel 7) + dp(cfg.yOffsetDp).
        // Subtracting (boxHeightPx / 2) places the exact vertical center of the Dynamic Island
        // directly on targetCenterYPx!
        wmParams.gravity = (cfg.anchorTop ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        int centerShiftX = (hwGeo.camCenterXPx - (hwGeo.screenWidthPx / 2)) + Theme.dp(this, cfg.xOffsetDp);
        wmParams.x = centerShiftX;

        if (cfg.anchorTop) {
            int targetCenterYPx = hwGeo.camCenterYPx + Theme.dp(this, cfg.yOffsetDp);
            wmParams.y = Math.max(0, Math.round(targetCenterYPx - (boxHeightPx / 2f)));
        } else {
            wmParams.y = Math.max(0, Theme.dp(this, Math.max(12, cfg.yOffsetDp)));
        }

        updateWindowLayoutSafe();
    }

    private int getEffectiveLeftWingWidthDp() {
        // On Pixel 7, Clock + up to 3 status bar notification icons span X = 58..326px,
        // while 62dp wing + 34dp hole starts at X = 333px (zero overlap!).
        // If 4+ notification icons are active (extending to X=380px), symmetrically compact to 44dp.
        if (cfg.notificationAware && cfg.displayMode == NowBarConfig.DisplayMode.CAMERA_MODE
                && cfg.yOffsetDp <= 12 && NowBarState.activeStatusBarNotifCount >= 4) {
            return Math.min(cfg.leftWingWidthDp, 44);
        }
        return cfg.leftWingWidthDp;
    }

    private int getEffectiveRightWingWidthDp() {
        if (cfg.notificationAware && cfg.displayMode == NowBarConfig.DisplayMode.CAMERA_MODE) {
            // Clamps right wing to 46dp when VPN / Tailscale key is active to prevent overlapping status bar icons (NB-SYS-01)
            try {
                if (hwMonitor != null && hwMonitor.sample().vpnActive) {
                    return Math.min(cfg.rightWingWidthDp, 46);
                }
            } catch (Exception ignored) {}
            if (cfg.yOffsetDp <= 12 && NowBarState.activeStatusBarNotifCount >= 4) {
                return Math.min(cfg.rightWingWidthDp, 44);
            }
        }
        return cfg.rightWingWidthDp;
    }

    private LinearLayout buildExpandedDeck() {
        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 16);
        deck.setPadding(pad, pad, pad, pad);

        // 1. Header Row
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("AAYUSH OS // DYNAMIC ISLAND");
        title.setTextColor(Theme.PAPAYA);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        title.setTypeface(Theme.MONO_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(title);

        TextView collapseBtn = Theme.createPill(this, "HIDE ▲", Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.PAPAYA);
        collapseBtn.setContentDescription("Collapse Now Bar Deck");
        collapseBtn.setOnClickListener(v -> setExpandedState(false));
        topRow.addView(collapseBtn);
        deck.addView(topRow);

        // 2. Live Telemetry Line
        expTelemetryText = new TextView(this);
        expTelemetryText.setTypeface(Theme.MONO);
        expTelemetryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        expTelemetryText.setTextColor(Theme.TELEMETRY_GREEN);
        LinearLayout.LayoutParams telLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        telLp.topMargin = Theme.dp(this, 6);
        expTelemetryText.setLayoutParams(telLp);
        deck.addView(expTelemetryText);

        deck.addView(Theme.createDivider(this));

        // Call Controller Section (Dynamic Island Call Pop-up)
        callDeckSection = new LinearLayout(this);
        callDeckSection.setOrientation(LinearLayout.VERTICAL);
        callDeckSection.setVisibility(View.GONE);

        LinearLayout callInfoBox = new LinearLayout(this);
        callInfoBox.setOrientation(LinearLayout.VERTICAL);
        callInfoBox.setGravity(Gravity.CENTER_HORIZONTAL);
        callInfoBox.setClickable(true);
        callInfoBox.setOnClickListener(v -> openFullCallScreen());

        expCallCallerText = new TextView(this);
        expCallCallerText.setTypeface(Theme.SANS_BOLD);
        expCallCallerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        expCallCallerText.setTextColor(Theme.TEXT_PRIMARY);
        expCallCallerText.setGravity(Gravity.CENTER);
        expCallCallerText.setSingleLine(true);
        expCallCallerText.setEllipsize(TextUtils.TruncateAt.END);
        callInfoBox.addView(expCallCallerText);

        expCallStatusText = new TextView(this);
        expCallStatusText.setTypeface(Theme.MONO_BOLD);
        expCallStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        expCallStatusText.setTextColor(Theme.TELEMETRY_GREEN);
        expCallStatusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        csLp.topMargin = Theme.dp(this, 3);
        expCallStatusText.setLayoutParams(csLp);
        callInfoBox.addView(expCallStatusText);

        callDeckSection.addView(callInfoBox);

        LinearLayout callActionsRow = new LinearLayout(this);
        callActionsRow.setOrientation(LinearLayout.HORIZONTAL);
        callActionsRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams caRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        caRowLp.topMargin = Theme.dp(this, 12);
        callActionsRow.setLayoutParams(caRowLp);

        expCallDeclineBtn = Theme.createPill(this, "☎ DECLINE", 0xFF2A0808, 0xFFFF4444, 0xFFFF4444);
        expCallDeclineBtn.setContentDescription("Decline Phone Call");
        LinearLayout.LayoutParams decLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        decLp.rightMargin = Theme.dp(this, 8);
        expCallDeclineBtn.setLayoutParams(decLp);
        expCallDeclineBtn.setOnClickListener(v -> handleDeclineCall());

        expCallAnswerBtn = Theme.createPill(this, "📞 ANSWER", 0xFF082A08, 0xFF00E676, 0xFF00E676);
        expCallAnswerBtn.setContentDescription("Answer Phone Call");
        LinearLayout.LayoutParams ansLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ansLp.leftMargin = Theme.dp(this, 8);
        expCallAnswerBtn.setLayoutParams(ansLp);
        expCallAnswerBtn.setOnClickListener(v -> handleAnswerCall());

        callActionsRow.addView(expCallDeclineBtn);
        callActionsRow.addView(expCallAnswerBtn);
        callDeckSection.addView(callActionsRow);

        deck.addView(callDeckSection);

        // Container for non-call sections (Media, Clipboard, French Press)
        normalDeckSections = new LinearLayout(this);
        normalDeckSections.setOrientation(LinearLayout.VERTICAL);

        // 3. Media Controller Section (Adaptive Smart Card - NB-POL-07 & NB-MED-02)
        expMediaSection = new LinearLayout(this);
        expMediaSection.setOrientation(LinearLayout.VERTICAL);
        expMediaSection.setVisibility(View.GONE);

        LinearLayout mediaHeaderRow = new LinearLayout(this);
        mediaHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        expMediaArtView = new ImageView(this);
        expMediaArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int artSize = Theme.dp(this, 36);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(artSize, artSize);
        artLp.rightMargin = Theme.dp(this, 10);
        expMediaArtView.setLayoutParams(artLp);
        expMediaArtView.setBackground(Theme.cardDrawable(this, 0xFF1E1E1E, Theme.BORDER_GRAPHITE, 8f, 1f));
        expMediaArtView.setClipToOutline(true);

        LinearLayout mediaTextBox = new LinearLayout(this);
        mediaTextBox.setOrientation(LinearLayout.VERTICAL);
        mediaTextBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        expMediaText = new TextView(this);
        expMediaText.setTypeface(Theme.SANS_BOLD);
        expMediaText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        expMediaText.setTextColor(Theme.TEXT_PRIMARY);
        expMediaText.setSingleLine(true);
        expMediaText.setEllipsize(TextUtils.TruncateAt.END);

        expMediaArtistText = new TextView(this);
        expMediaArtistText.setTypeface(Theme.MONO);
        expMediaArtistText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        expMediaArtistText.setTextColor(Theme.TEXT_SECONDARY);
        expMediaArtistText.setSingleLine(true);
        expMediaArtistText.setEllipsize(TextUtils.TruncateAt.END);

        mediaTextBox.addView(expMediaText);
        mediaTextBox.addView(expMediaArtistText);

        mediaHeaderRow.addView(expMediaArtView);
        mediaHeaderRow.addView(mediaTextBox);
        expMediaSection.addView(mediaHeaderRow);

        LinearLayout mediaBtns = new LinearLayout(this);
        mediaBtns.setOrientation(LinearLayout.HORIZONTAL);
        mediaBtns.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mRowLp.topMargin = Theme.dp(this, 8);
        mediaBtns.setLayoutParams(mRowLp);

        TextView prevBtn = Theme.createPill(this, "⏮ PREV", Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.BORDER_GRAPHITE);
        prevBtn.setContentDescription("Previous Track");
        prevBtn.setOnClickListener(v -> {
            NowBarState.skipPrev();
            handler.postDelayed(this::updateOverlayContent, 200L);
        });

        expPlayPauseBtn = Theme.createPill(this, "⏯ PLAY / PAUSE", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.PAPAYA);
        expPlayPauseBtn.setContentDescription("Play or Pause Media");
        LinearLayout.LayoutParams ppLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ppLp.leftMargin = Theme.dp(this, 10);
        ppLp.rightMargin = Theme.dp(this, 10);
        expPlayPauseBtn.setLayoutParams(ppLp);
        expPlayPauseBtn.setOnClickListener(v -> {
            NowBarState.togglePlayPause();
            handler.postDelayed(this::updateOverlayContent, 200L);
        });

        TextView nextBtn = Theme.createPill(this, "NEXT ⏭", Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.BORDER_GRAPHITE);
        nextBtn.setContentDescription("Next Track");
        nextBtn.setOnClickListener(v -> {
            NowBarState.skipNext();
            handler.postDelayed(this::updateOverlayContent, 200L);
        });

        mediaBtns.addView(prevBtn);
        mediaBtns.addView(expPlayPauseBtn);
        mediaBtns.addView(nextBtn);
        expMediaSection.addView(mediaBtns);

        // Media Volume Slider Row directly inside Media Card (like Apple Dynamic Island & Samsung Now Bar)
        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams vrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vrLp.topMargin = Theme.dp(this, 6);
        volRow.setLayoutParams(vrLp);

        TextView volIcon = new TextView(this);
        volIcon.setText("🔈");
        volIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        volRow.addView(volIcon);

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVol = (am != null) ? am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 15;
        int curVol = (am != null) ? am.getStreamVolume(AudioManager.STREAM_MUSIC) : 10;

        expVolValText = new TextView(this);
        expVolValText.setTypeface(Theme.MONO_BOLD);
        expVolValText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        expVolValText.setTextColor(Theme.TEXT_SECONDARY);
        expVolValText.setText(String.format(Locale.US, "%d%%", (curVol * 100) / Math.max(1, maxVol)));
        expVolValText.setGravity(Gravity.END);
        expVolValText.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(this, 36), ViewGroup.LayoutParams.WRAP_CONTENT));

        expVolSeekBar = new SeekBar(this);
        expVolSeekBar.setMax(Math.max(1, maxVol));
        expVolSeekBar.setProgress(curVol);
        expVolSeekBar.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(Theme.PAPAYA, PorterDuff.Mode.SRC_IN));
        if (expVolSeekBar.getThumb() != null) {
            expVolSeekBar.getThumb().setColorFilter(new PorterDuffColorFilter(Theme.PAPAYA, PorterDuff.Mode.SRC_IN));
        }
        expVolSeekBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        expVolSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    AudioManager aManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (aManager != null) {
                        int mVol = aManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        aManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                        if (expVolValText != null) {
                            expVolValText.setText(String.format(Locale.US, "%d%%", (progress * 100) / Math.max(1, mVol)));
                        }
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { isVolTracking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { isVolTracking = false; }
        });

        volRow.addView(expVolSeekBar);
        volRow.addView(expVolValText);
        expMediaSection.addView(volRow);
        expMediaSection.addView(Theme.createDivider(this));

        normalDeckSections.addView(expMediaSection);

        // 4. Smart Clipboard Section
        LinearLayout clipRow = new LinearLayout(this);
        clipRow.setOrientation(LinearLayout.HORIZONTAL);
        clipRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView clipHeader = Theme.createSectionLabel(this, "SMART CLIPBOARD");
        clipHeader.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clipRow.addView(clipHeader);

        expClipBadge = Theme.createPill(this, "READY", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.BORDER_GRAPHITE);
        clipRow.addView(expClipBadge);
        normalDeckSections.addView(clipRow);

        expClipSummary = new TextView(this);
        expClipSummary.setTypeface(Theme.MONO);
        expClipSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        expClipSummary.setTextColor(Theme.TEXT_SECONDARY);
        expClipSummary.setMaxLines(3);
        expClipSummary.setEllipsize(TextUtils.TruncateAt.END);
        normalDeckSections.addView(expClipSummary);

        LinearLayout clipBtnsRow = new LinearLayout(this);
        clipBtnsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cbRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbRowLp.topMargin = Theme.dp(this, 6);
        clipBtnsRow.setLayoutParams(cbRowLp);

        TextView expAiRewriteBtn = Theme.createPill(this, "⚡ AI WRITING ASSIST", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.BORDER_GRAPHITE);
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        aiLp.rightMargin = Theme.dp(this, 6);
        expAiRewriteBtn.setLayoutParams(aiLp);
        expAiRewriteBtn.setOnClickListener(v -> launchAiWritingAssist());
        clipBtnsRow.addView(expAiRewriteBtn);

        expClipActionBtn = Theme.createPill(this, "INSPECT CLIP", Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.BORDER_GRAPHITE);
        expClipActionBtn.setContentDescription("Inspect or Execute Clipboard Action");
        LinearLayout.LayoutParams caLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        expClipActionBtn.setLayoutParams(caLp);
        expClipActionBtn.setOnClickListener(v -> executeSmartClipboardAction());
        clipBtnsRow.addView(expClipActionBtn);

        normalDeckSections.addView(clipBtnsRow);

        normalDeckSections.addView(Theme.createDivider(this));

        // 5. Interactive French Press Brew Timer Row
        LinearLayout timerRow = new LinearLayout(this);
        timerRow.setOrientation(LinearLayout.HORIZONTAL);
        timerRow.setGravity(Gravity.CENTER_VERTICAL);

        expTimerStatus = new TextView(this);
        expTimerStatus.setTypeface(Theme.MONO_BOLD);
        expTimerStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        expTimerStatus.setTextColor(Theme.TEXT_PRIMARY);
        expTimerStatus.setText("☕ FRENCH PRESS (4:00)");
        expTimerStatus.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        expTimerBtn = Theme.createPill(this, "START BREW", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.BORDER_GRAPHITE);
        expTimerBtn.setContentDescription("Start or Stop French Press Timer");
        expTimerBtn.setOnClickListener(v -> {
            if (NowBarState.timerRunning) {
                NowBarState.stopTimer();
            } else {
                NowBarState.startFrenchPressTimer();
            }
            updateOverlayContent();
        });

        expTimerCancelBtn = Theme.createPill(this, "✕ STOP", Theme.BG_ELEVATED, 0xFFFF4444, 0xFFFF4444);
        expTimerCancelBtn.setVisibility(View.GONE);
        expTimerCancelBtn.setOnClickListener(v -> {
            NowBarState.stopTimer();
            triggerHaptic(HAPTIC_CLICK);
            updateOverlayContent();
        });

        timerRow.addView(expTimerStatus);
        timerRow.addView(expTimerCancelBtn);
        timerRow.addView(expTimerBtn);
        normalDeckSections.addView(timerRow);

        // Quick Preset Timer Chips Row (Coffee 4m & Timer 5m - Tea & Pomo removed)
        LinearLayout timerChipsRow = new LinearLayout(this);
        timerChipsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tcLp.topMargin = Theme.dp(this, 6);
        timerChipsRow.setLayoutParams(tcLp);

        String[] presetLabels = {"☕ 4m COFFEE", "⏱ 5m TIMER"};
        long[] presetSecs = {240L, 300L};
        for (int pIdx = 0; pIdx < presetLabels.length; pIdx++) {
            final String pLabel = presetLabels[pIdx];
            final long pSec = presetSecs[pIdx];
            TextView chip = Theme.createPill(this, pLabel, Theme.BG_ELEVATED, Theme.TEXT_SECONDARY, Theme.BORDER_GRAPHITE);
            LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (pIdx > 0) chLp.leftMargin = Theme.dp(this, 6);
            chip.setLayoutParams(chLp);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(v -> {
                NowBarState.startTimer(pLabel, pSec);
                triggerHaptic(HAPTIC_CLICK);
                updateOverlayContent();
            });
            timerChipsRow.addView(chip);
        }
        normalDeckSections.addView(timerChipsRow);

        deck.addView(normalDeckSections);

        // 6. Mode Preset & Hub Footer Row
        LinearLayout footerRow = new LinearLayout(this);
        footerRow.setOrientation(LinearLayout.HORIZONTAL);
        footerRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams frLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        frLp.topMargin = Theme.dp(this, 12);
        footerRow.setLayoutParams(frLp);

        TextView openHubBtn = Theme.createPill(this, "CALIBRATE ⚡", Theme.PAPAYA, Theme.BG_OBSIDIAN, Theme.PAPAYA);
        openHubBtn.setOnClickListener(v -> {
            setExpandedState(false);
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

        footerRow.addView(openHubBtn);
        deck.addView(footerRow);

        return deck;
    }

    private void cycleDisplayMode() {
        NowBarConfig.DisplayMode next = (cfg.displayMode == NowBarConfig.DisplayMode.COMPACT_MODE)
                ? NowBarConfig.DisplayMode.CAMERA_MODE
                : NowBarConfig.DisplayMode.COMPACT_MODE;
        cfg.applyPreset(next);
        cfg.saveAndBroadcast(this);
        applyConfigToViews();
        updateOverlayContent();
        Toast.makeText(this, "Switched to " + next.title, Toast.LENGTH_SHORT).show();
    }

    private void setExpandedState(boolean expand) {
        android.util.Log.i("NowBarTouch", "setExpandedState expand=" + expand + " previous=" + this.isExpanded, new Throwable("stack"));
        if (this.isExpanded == expand) return;
        this.isExpanded = expand;

        triggerHaptic(HAPTIC_TICK);

        if (expand) {
            wmParams.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            updateWindowLayoutSafe();

            float pivotX = expandedDeck.getWidth() > 0 ? expandedDeck.getWidth() / 2f : Theme.dp(this, cfg.expandedWidthDp) / 2f;
            expandedDeck.setPivotX(pivotX);
            expandedDeck.setPivotY(0f);
            expandedDeck.setAlpha(0f);
            expandedDeck.setScaleX(0.72f);
            expandedDeck.setScaleY(0.65f);
            expandedDeck.setTranslationY(-Theme.dp(this, 20));
            expandedDeck.setVisibility(View.VISIBLE);
            expandedDeck.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(260L)
                    .setInterpolator(new OvershootInterpolator(1.15f))
                    .start();
        } else {
            isExpandedForCall = false;
            wmParams.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            updateWindowLayoutSafe();

            float pivotX = expandedDeck.getWidth() > 0 ? expandedDeck.getWidth() / 2f : Theme.dp(this, cfg.expandedWidthDp) / 2f;
            expandedDeck.setPivotX(pivotX);
            expandedDeck.setPivotY(0f);
            expandedDeck.animate()
                    .alpha(0f)
                    .scaleX(0.72f)
                    .scaleY(0.65f)
                    .translationY(-Theme.dp(this, 20))
                    .setDuration(170L)
                    .setInterpolator(new AccelerateInterpolator(1.5f))
                    .withEndAction(() -> {
                        expandedDeck.setVisibility(View.GONE);
                        expandedDeck.setTranslationY(0f);
                    })
                    .start();
            updateOverlayContent();
        }
    }

    private void updateWindowLayoutSafe() {
        if (wm != null && rootOverlay != null && rootOverlay.isAttachedToWindow()) {
            try {
                wm.updateViewLayout(rootOverlay, wmParams);
            } catch (Exception ignored) {}
        }
    }

    private void executeSmartClipboardAction() {
        if (lastClipAnalysis.type == SmartClipboard.ContentType.EMPTY) {
            lastClipAnalysis = SmartClipboard.inspectClipboard(this);
        }
        if (!TextUtils.isEmpty(lastClipAnalysis.transformedText)) {
            SmartClipboard.copyToClipboard(this, "AayushOS", lastClipAnalysis.transformedText);
            Toast.makeText(this, "Copied processed " + lastClipAnalysis.badgeLabel + "!", Toast.LENGTH_SHORT).show();
            lastClipAnalysis = SmartClipboard.inspectClipboard(this);
            updateOverlayContent();
        }
    }

    private void launchAiWritingAssist() {
        setExpandedState(false);
        Intent intent = new Intent(this, AiProcessTextActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void handleAnswerCall() {
        if (NowBarState.isCallIncoming() && NowBarState.callAnswerIntent != null) {
            sendSafePendingIntent(this, NowBarState.callAnswerIntent);
            NowBarState.callState = NowBarState.CALL_STATE_ACTIVE;
            NowBarState.callStartWallMs = System.currentTimeMillis();
        } else if (NowBarState.callContentIntent != null) {
            sendSafePendingIntent(this, NowBarState.callContentIntent);
        }
        updateOverlayContent();
    }

    private void handleDeclineCall() {
        if (NowBarState.callDeclineIntent != null) {
            sendSafePendingIntent(this, NowBarState.callDeclineIntent);
        }
        NowBarState.clearCall();
        setExpandedState(false);
    }

    private void openFullCallScreen() {
        if (NowBarState.callContentIntent != null) {
            sendSafePendingIntent(this, NowBarState.callContentIntent);
            setExpandedState(false);
        }
    }

    public static void sendSafePendingIntent(Context ctx, PendingIntent pi) {
        if (pi == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pi.send(ctx, 0, null, null, null, null, opts.toBundle());
            } else {
                pi.send();
            }
        } catch (Exception e) {
            try {
                pi.send();
            } catch (Exception ignored) {}
        }
    }

    private void updateOverlayContent() {
        if (rootOverlay == null || cfg == null) return;

        // 1. Check Lock Screen & Landscape Orientation (NB-SEC-03)
        boolean isLocked = (keyguardManager != null && keyguardManager.isKeyguardLocked());
        boolean isLandscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        if (isLocked) {
            if (isExpanded) {
                setExpandedState(false);
            }
            if (cfg.hideOnLockScreen) {
                if (rootOverlay.getVisibility() != View.GONE) {
                    rootOverlay.setVisibility(View.GONE);
                }
                return;
            }
        }

        if (!isScreenOn || isLandscape) {
            if (rootOverlay.getVisibility() != View.GONE) {
                rootOverlay.setVisibility(View.GONE);
            }
            return;
        }

        // 2. Check Intelligent Notification-Aware Conflict (Heads-Up Notification Yield)
        boolean notifConflict = cfg.notificationAware && NowBarState.isNotificationConflictActive() && !NowBarState.isCallActive() && !isExpanded;
        if (notifConflict != isYieldingForNotification) {
            isYieldingForNotification = notifConflict;
            if (notifConflict) {
                if (isExpanded) setExpandedState(false);
                wmParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                updateWindowLayoutSafe();
                rootOverlay.animate().alpha(0.0f).setDuration(180L).start();
                return;
            } else {
                wmParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                updateWindowLayoutSafe();
                rootOverlay.setVisibility(View.VISIBLE);
                rootOverlay.animate().alpha(1.0f).setDuration(220L).start();
            }
        }

        if (isYieldingForNotification) {
            return;
        }

        if (rootOverlay.getVisibility() != View.VISIBLE) {
            rootOverlay.setAlpha(1.0f);
            rootOverlay.setVisibility(View.VISIBLE);
        }

        // Symmetrically adjust wing widths if status bar notification icon count changed
        if (cfg.isCameraContouredMode()) {
            int effW = getEffectiveLeftWingWidthDp();
            if (effW != lastAppliedWingWidthDp) {
                applyConfigToViews();
            }
        }

        // 3. Populate Telemetry & Activity Priority Stack
        HardwareMonitor.Snapshot hw = hwMonitor.sample();
        int timerRemSec = NowBarState.getTimerRemainingSec();
        int dlPct = NowBarState.getActiveDownloadPct();
        boolean isCompact = (cfg.displayMode == NowBarConfig.DisplayMode.COMPACT_MODE);
        boolean narrowWing = isCompact || (lastAppliedWingWidthDp > 0 && lastAppliedWingWidthDp <= 56);

        int maxRightChars = narrowWing ? 10 : 16;

        boolean isStopwatch = NowBarState.isStopwatch && NowBarState.timerRunning;
        int swElapsedSec = isStopwatch ? NowBarState.getStopwatchElapsedSec() : 0;
        String timerPillStr = isStopwatch
                ? String.format(Locale.US, "%s%02d:%02d", narrowWing ? "⏱" : "⏱ ", swElapsedSec / 60, swElapsedSec % 60)
                : String.format(Locale.US, "%s%02d:%02d", narrowWing ? "" : "☕ ", timerRemSec / 60, timerRemSec % 60);

        boolean isCall = !isLocked && NowBarState.isCallActive();
        boolean isTimerDone = NowBarState.isTimerJustCompleted();
        boolean isTimerRunning = (NowBarState.timerRunning && (timerRemSec > 0 || isStopwatch));
        boolean isDl = !isLocked && (dlPct >= 0);
        boolean isMedia = !isLocked && (NowBarState.isMediaActiveOrRecent() && !TextUtils.isEmpty(NowBarState.mediaTitle));
        boolean isCharging = hw.isCharging;

        if (isCall) {
            boolean isIncoming = NowBarState.isCallIncoming();
            if (isIncoming) {
                leftWingText.setText(narrowWing ? "📞" : "📞 " + ellipsize(NowBarState.callCaller, 9));
                leftWingText.setTextColor(Theme.PAPAYA);
                rightWingText.setText(narrowWing ? "ANS" : "ANSWER");
                rightWingText.setTextColor(Theme.TELEMETRY_GREEN);
            } else {
                int callSec = NowBarState.getCallDurationSec();
                leftWingText.setText(narrowWing ? "📞" : "📞 " + ellipsize(NowBarState.callCaller, 9));
                leftWingText.setTextColor(Theme.TELEMETRY_GREEN);
                rightWingText.setText(String.format(Locale.US, "%02d:%02d", callSec / 60, callSec % 60));
                rightWingText.setTextColor(Theme.TEXT_PRIMARY);
            }
        } else if (isTimerDone) {
            if (!hasTriggeredTimerHaptic) {
                triggerHapticAlert();
                hasTriggeredTimerHaptic = true;
            }
            leftWingText.setText("☕ READY");
            leftWingText.setTextColor(Theme.TELEMETRY_GREEN);
            rightWingText.setText("PLUNGE NOW!");
            rightWingText.setTextColor(Theme.TELEMETRY_GREEN);
        } else if (isTimerRunning && isMedia) {
            // Dual Activity: Timer (Left) + Media Live Equalizer (Right) - NB-PRI-02
            hasTriggeredTimerHaptic = false;
            leftWingText.setText(timerPillStr);
            leftWingText.setTextColor(Theme.PAPAYA);

            String[] EQ_FRAMES = {" ılı", " ı|ı", " |ı|", " ı||"};
            int frame = (int) ((SystemClock.uptimeMillis() / 320L) % EQ_FRAMES.length);
            String eqGlyph = NowBarState.mediaPlaying ? EQ_FRAMES[frame] : " ⏸";
            rightWingText.setText(ellipsize("🎵" + eqGlyph + " " + NowBarState.mediaTitle, maxRightChars));
            rightWingText.setTextColor(NowBarState.mediaPlaying ? Theme.PAPAYA : Theme.TEXT_SECONDARY);
        } else if (isTimerRunning && isCharging) {
            // Dual Activity: Timer (Left) + Charging Telemetry (Right) - NB-PRI-02
            hasTriggeredTimerHaptic = false;
            leftWingText.setText(timerPillStr);
            leftWingText.setTextColor(Theme.PAPAYA);
            rightWingText.setText(narrowWing
                    ? String.format(Locale.US, "⚡%.0fW", hw.powerWatts)
                    : String.format(Locale.US, "⚡%.0fW•%d%%", hw.powerWatts, hw.batteryPct));
            rightWingText.setTextColor(Theme.TELEMETRY_GREEN);
        } else if (isCharging && isMedia) {
            // Dual Activity: Charging (Left) + Media Live Equalizer (Right) - NB-PRI-02
            leftWingText.setText(narrowWing
                    ? String.format(Locale.US, "⚡%.0fW", hw.powerWatts)
                    : String.format(Locale.US, "⚡ %.1fW", hw.powerWatts));
            leftWingText.setTextColor(Theme.TELEMETRY_GREEN);

            String[] EQ_FRAMES = {" ılı", " ı|ı", " |ı|", " ı||"};
            int frame = (int) ((SystemClock.uptimeMillis() / 320L) % EQ_FRAMES.length);
            String eqGlyph = NowBarState.mediaPlaying ? EQ_FRAMES[frame] : " ⏸";
            rightWingText.setText(ellipsize("🎵" + eqGlyph + " " + NowBarState.mediaTitle, maxRightChars));
            rightWingText.setTextColor(NowBarState.mediaPlaying ? Theme.PAPAYA : Theme.TEXT_SECONDARY);
        } else if (isTimerRunning) {
            hasTriggeredTimerHaptic = false;
            leftWingText.setText(timerPillStr);
            leftWingText.setTextColor(Theme.PAPAYA);
            rightWingText.setText(ellipsize(isStopwatch ? NowBarState.timerLabel : NowBarState.getFrenchPressStage(), maxRightChars));
            rightWingText.setTextColor(Theme.TEXT_PRIMARY);
        } else if (isDl) {
            leftWingText.setText("⬇ " + dlPct + "%");
            leftWingText.setTextColor(Theme.PAPAYA);
            rightWingText.setText(ellipsize(NowBarState.downloadLabel, maxRightChars));
            rightWingText.setTextColor(Theme.TEXT_PRIMARY);
        } else if (isMedia) {
            boolean isPlaying = NowBarState.mediaPlaying;
            if (isPlaying) {
                String[] EQ_FRAMES = {" ılı", " ı|ı", " |ı|", " ı||"};
                int frame = (int) ((SystemClock.uptimeMillis() / 320L) % EQ_FRAMES.length);
                leftWingText.setText(narrowWing ? EQ_FRAMES[frame] : "🎵" + EQ_FRAMES[frame]);
                leftWingText.setTextColor(Theme.PAPAYA);
            } else {
                leftWingText.setText(narrowWing ? "⏸" : "🎵 ⏸");
                leftWingText.setTextColor(Theme.TEXT_SECONDARY);
            }
            String fullTrack = NowBarState.mediaTitle
                    + (!TextUtils.isEmpty(NowBarState.mediaArtist) ? " • " + NowBarState.mediaArtist : "");
            rightWingText.setText(ellipsize(fullTrack, maxRightChars));
            rightWingText.setTextColor(Theme.TEXT_PRIMARY);
        } else if (isCharging) {
            if (!lastWasCharging) {
                chargingBannerStartUptimeMs = SystemClock.uptimeMillis();
                triggerHaptic(HAPTIC_CLICK);
            }
            boolean recentPlug = (SystemClock.uptimeMillis() - chargingBannerStartUptimeMs < 3500L);
            if (recentPlug) {
                leftWingText.setText("⚡ RAPID");
                leftWingText.setTextColor(Theme.TELEMETRY_GREEN);
                rightWingText.setText(String.format(Locale.US, "%.0fW • %d%%", hw.powerWatts, hw.batteryPct));
                rightWingText.setTextColor(Theme.TELEMETRY_GREEN);
            } else {
                leftWingText.setText(narrowWing
                        ? String.format(Locale.US, "⚡%.0fW", hw.powerWatts)
                        : String.format(Locale.US, "⚡ %.1fW", hw.powerWatts));
                leftWingText.setTextColor(Theme.TELEMETRY_GREEN);
                rightWingText.setText(narrowWing
                        ? String.format(Locale.US, "%d%%", hw.batteryPct)
                        : String.format(Locale.US, "%d%%•%.0f°C", hw.batteryPct, hw.batteryTempC));
                rightWingText.setTextColor(Theme.TEXT_PRIMARY);
            }
        } else {
            leftWingText.setText(narrowWing
                    ? "#81"
                    : String.format(Locale.US, "#81 %.1fW", hw.powerWatts));
            leftWingText.setTextColor(Theme.PAPAYA);
            rightWingText.setText(narrowWing
                    ? String.format(Locale.US, "%d%%•%.0f°", hw.batteryPct, hw.batteryTempC)
                    : String.format(Locale.US, "%d%% • %.0f°C", hw.batteryPct, hw.batteryTempC));
            rightWingText.setTextColor(Theme.TEXT_PRIMARY);
        }
        lastWasCharging = isCharging;

        compactPill.setContentDescription("Now Bar: " + leftWingText.getText() + ", " + rightWingText.getText());

        // 4. Populate Expanded Control Panel if Open
        if (isExpanded) {
            if (NowBarState.isCallActive()) {
                if (callDeckSection != null) callDeckSection.setVisibility(View.VISIBLE);
                if (normalDeckSections != null) normalDeckSections.setVisibility(View.GONE);

                boolean isIncoming = NowBarState.isCallIncoming();
                expCallCallerText.setText("📞 " + NowBarState.callCaller);
                if (isIncoming) {
                    expCallStatusText.setText("INCOMING " + NowBarState.callAppLabel.toUpperCase(Locale.US) + " CALL");
                    expCallStatusText.setTextColor(Theme.PAPAYA);
                    expCallDeclineBtn.setText("☎ DECLINE");
                    expCallDeclineBtn.setVisibility(View.VISIBLE);
                    expCallAnswerBtn.setText("📞 ANSWER");
                    expCallAnswerBtn.setVisibility(View.VISIBLE);
                } else {
                    int sec = NowBarState.getCallDurationSec();
                    expCallStatusText.setText(String.format(Locale.US, "ACTIVE CALL • %02d:%02d", sec / 60, sec % 60));
                    expCallStatusText.setTextColor(Theme.TELEMETRY_GREEN);
                    expCallDeclineBtn.setText("☎ END CALL");
                    expCallDeclineBtn.setVisibility(View.VISIBLE);
                    expCallAnswerBtn.setText("↗ OPEN APP");
                    expCallAnswerBtn.setVisibility(View.VISIBLE);
                }
            } else {
                if (callDeckSection != null) callDeckSection.setVisibility(View.GONE);
                if (normalDeckSections != null) normalDeckSections.setVisibility(View.VISIBLE);
            }

            String netStatus = (hw.wifiIp != null && !hw.wifiIp.equals("0.0.0.0"))
                    ? "IP: " + hw.wifiIp + (hw.vpnActive ? " 🛡" : "")
                    : (hw.vpnActive ? "🛡 VPN" : "OFFLINE");
            String therm = (!TextUtils.isEmpty(hw.thermalState) && !"NONE".equalsIgnoreCase(hw.thermalState))
                    ? " • " + hw.thermalState
                    : "";
            String battStatus = hw.isCharging
                    ? String.format(Locale.US, "⚡ %.1fW (CHARGING)", hw.powerWatts)
                    : ("🔋 " + hw.batteryPct + "%");

            expTelemetryText.setText(String.format(Locale.US,
                    "%s | %s%s | RAM %d%%",
                    battStatus, netStatus, therm, hw.ramUsedPct
            ));

            if (NowBarState.isMediaActiveOrRecent() && !TextUtils.isEmpty(NowBarState.mediaTitle)) {
                if (expMediaSection != null) expMediaSection.setVisibility(View.VISIBLE);
                expMediaText.setText(NowBarState.mediaTitle);
                if (expMediaArtistText != null) {
                    expMediaArtistText.setText(!TextUtils.isEmpty(NowBarState.mediaArtist)
                            ? (NowBarState.mediaPlaying ? "▶ " : "⏸ ") + NowBarState.mediaArtist
                            : (NowBarState.mediaPlaying ? "▶ Playing" : "⏸ Paused"));
                }
                if (expMediaArtView != null) {
                    if (NowBarState.mediaArtwork != null) {
                        expMediaArtView.setVisibility(View.VISIBLE);
                        expMediaArtView.setImageBitmap(NowBarState.mediaArtwork);
                    } else {
                        expMediaArtView.setVisibility(View.GONE);
                    }
                }
                expPlayPauseBtn.setText(NowBarState.mediaPlaying ? "⏸ PAUSE" : "▶ PLAY");
            } else {
                if (expMediaSection != null) expMediaSection.setVisibility(View.GONE);
            }

            expClipBadge.setText(lastClipAnalysis.badgeLabel);
            expClipSummary.setText(lastClipAnalysis.summary);
            expClipActionBtn.setText(!TextUtils.isEmpty(lastClipAnalysis.primaryActionLabel)
                    ? lastClipAnalysis.primaryActionLabel : "REFRESH CLIPBOARD");

            if (NowBarState.timerRunning && timerRemSec > 0) {
                expTimerStatus.setText(String.format(Locale.US, "⏱ %s (%02d:%02d)",
                        NowBarState.getFrenchPressStage(), timerRemSec / 60, timerRemSec % 60));
                expTimerBtn.setText("PAUSE");
                expTimerBtn.setTextColor(Theme.PAPAYA);
                if (expTimerCancelBtn != null) expTimerCancelBtn.setVisibility(View.VISIBLE);
            } else {
                expTimerStatus.setText("⏱ QUICK TIMERS (SELECT CHIP BELOW)");
                expTimerBtn.setText("START 4m");
                expTimerBtn.setTextColor(Theme.TEXT_PRIMARY);
                if (expTimerCancelBtn != null) expTimerCancelBtn.setVisibility(View.GONE);
            }

            
            if (expVolSeekBar != null && !isVolTracking && !expVolSeekBar.isPressed()) {
                AudioManager aManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (aManager != null) {
                    int mVol = aManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int cVol = aManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    expVolSeekBar.setMax(Math.max(1, mVol));
                    expVolSeekBar.setProgress(cVol);
                    if (expVolValText != null) {
                        expVolValText.setText(String.format(Locale.US, "%d%%", (cVol * 100) / Math.max(1, mVol)));
                    }
                }
            }
        }
    }

    private static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        activeInstance = null;
        NowBarState.setListener(null);
        getSharedPreferences("aayush_os_prefs", MODE_PRIVATE)
                .edit().putBoolean("mod_nowbar_active", false).apply();
        handler.removeCallbacks(tickRunnable);
        try {
            unregisterReceiver(systemEventReceiver);
        } catch (Exception ignored) {}
        if (wm != null && rootOverlay != null) {
            try {
                if (rootOverlay.isAttachedToWindow()) {
                    wm.removeViewImmediate(rootOverlay);
                }
            } catch (Exception ignored) {}
            rootOverlay = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
