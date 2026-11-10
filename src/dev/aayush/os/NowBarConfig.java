package dev.aayush.os;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.DisplayCutout;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * Dynamic Now Bar V2.3 Configuration & Hardware Cutout Geometry Store.
 * Engineered specifically around the Google Pixel 7 ('panther') DisplayCutout
 * (1080x2400 @ 420dpi / 2.625x density, camera center at X=540px, Y=68px / 25.9dp,
 * physical lens r=34px / 13.0dp, top cutout inset=136px / 51.8dp).
 */
public final class NowBarConfig {

    public enum DisplayMode {
        COMPACT_MODE("COMPACT ISLAND", "Ultra-compact Dynamic Island hugging the Pixel 7 camera cutout with minimal status-bar footprint"),
        CAMERA_MODE("EXPANDED WINGS", "Symmetrical Dynamic Island aligned to Pixel 7 camera center (Y=68px) with wider telemetry wings");

        public final String title;
        public final String description;
        DisplayMode(String t, String d) {
            this.title = t;
            this.description = d;
        }
    }

    /**
     * Real-time hardware geometry inspected from Android DisplayCutout & WindowMetrics.
     */
    public static final class HardwareGeometry {
        public final int screenWidthPx;
        public final int screenHeightPx;
        public final float density;
        public final int camCenterXPx;
        public final int camCenterYPx;
        public final int camLensRadiusPx;
        public final int cutoutBottomPx;
        public final int statusBarHeightPx;
        public final Rect cutoutBoundingRect;

        public HardwareGeometry(int sw, int sh, float dens, int cx, int cy, int lensR, int cutBot, int sbH, Rect rect) {
            this.screenWidthPx = sw;
            this.screenHeightPx = sh;
            this.density = dens;
            this.camCenterXPx = cx;
            this.camCenterYPx = cy;
            this.camLensRadiusPx = lensR;
            this.cutoutBottomPx = cutBot;
            this.statusBarHeightPx = sbH;
            this.cutoutBoundingRect = rect;
        }

        public float pxToDp(int px) {
            return px / Math.max(1f, density);
        }
    }

    /**
     * Queries Android's actual WindowMetrics / DisplayCutout for exact camera cutout coordinates.
     * On Pixel 7 ('panther'):
     * - Display: 1080 x 2400 px (density 2.625)
     * - DisplayCutout top bounding rect: Rect(480, 0 - 625, 136)
     * - Optical camera center: X = 540 px (205.7 dp), Y = 68 px (25.9 dp)
     * - Physical camera lens radius: 34 px (13.0 dp, diameter 68 px / 25.9 dp)
     */
    public static HardwareGeometry inspectHardwareGeometry(Context ctx) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int sw = dm.widthPixels;
        int sh = dm.heightPixels;
        float dens = dm.density;

        // Pixel 7 hardware defaults (used as fallback if WindowInsets hasn't attached a cutout yet)
        int camX = sw / 2;
        int camY = Math.round(25.9f * dens); // 68px on Pixel 7
        int lensR = Math.round(13.0f * dens); // 34px on Pixel 7
        int cutBot = Math.round(51.8f * dens); // 136px on Pixel 7
        int sbH = cutBot;
        Rect bound = new Rect(camX - lensR, 0, camX + lensR, cutBot);

        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowMetrics metrics = wm.getCurrentWindowMetrics();
                Rect b = metrics.getBounds();
                if (b.width() > 0 && b.height() > 0) {
                    sw = b.width();
                    sh = b.height();
                    camX = sw / 2;
                }
                WindowInsets insets = metrics.getWindowInsets();
                if (insets != null) {
                    Insets sbInsets = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars());
                    if (sbInsets.top > 0) {
                        sbH = sbInsets.top;
                    }
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        Rect topRect = cutout.getBoundingRectTop();
                        if (topRect != null && !topRect.isEmpty()) {
                            bound = new Rect(topRect);
                            cutBot = topRect.bottom;
                            // Exact vertical center of the cutout bounding box (68px on Pixel 7)
                            camY = (topRect.top + topRect.bottom) / 2;
                            // Physical lens diameter on punch-hole Pixels is half the top safe inset (68px -> r=34px)
                            lensR = Math.max(Math.round(11f * dens), Math.min(Math.round(15f * dens), cutBot / 4));
                            // Physical hardware profile matching for Pixel 7 ('panther') / 7 Pro ('cheetah'):
                            // Explicitly locks optical center to display midpoint and cutout vertical center regardless of display scaling (NB-GEO-02 / NB-GEO-03)
                            boolean isPixel7Series = "panther".equalsIgnoreCase(Build.DEVICE) || "cheetah".equalsIgnoreCase(Build.DEVICE);
                            if (isPixel7Series || Math.abs(topRect.centerX() - (sw / 2)) <= Math.round(24f * dens)) {
                                camX = sw / 2;
                            } else {
                                camX = topRect.centerX();
                            }
                        } else if (cutout.getSafeInsetTop() > 0) {
                            cutBot = cutout.getSafeInsetTop();
                            camY = cutBot / 2;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return new HardwareGeometry(sw, sh, dens, camX, camY, lensR, cutBot, Math.max(sbH, cutBot), bound);
    }

    public static final String PREF_NAME = "aayush_os_prefs";

    // Pixel 7 ('panther') Hardware-Relative Defaults:
    // - yOffsetDp is relative to the hardware camera vertical center (camCenterYPx = 68px / 25.9dp)!
    //   * 0 dp = Vertical center of the Dynamic Island is locked to camCenterYPx (68px)!
    //   * +40 dp = Floating Mode vertical center at 65.9dp (top edge at 50dp / 131px, 6dp below protector ring).
    // - cameraClearanceDp = Diameter of the transparent circular cutout hole around (camCenterX, camCenterY).
    //   * Physical lens is 25.9dp (68px). Screen protector circular ring is ~32dp (84px).
    //   * Default 34dp (89px diameter, r=44.5px) clears both the lens and circular protector border cleanly!
    public static final int DEF_CAMERA_CLEARANCE_DP = 34;
    public static final int DEF_CAMERA_Y_OFFSET_DP  = 0;   // Locked to camera vertical center (Y=68px)
    public static final int DEF_COMPACT_Y_OFFSET_DP = 0;   // Locked to camera vertical center (Y=68px)
    public static final int DEF_PILL_WIDTH_DP       = 236;
    public static final int DEF_CAMERA_HEIGHT_DP    = 38;  // Symmetrical island enclosing 34dp hole + top/bottom bridge
    public static final int DEF_COMPACT_HEIGHT_DP   = 36;
    public static final int DEF_LEFT_WING_WIDTH_DP  = 62;  // Symmetrical 62dp left + 34dp hole + 62dp right = 158dp (X=333..747px)
    public static final int DEF_RIGHT_WING_WIDTH_DP = 62;
    public static final int DEF_COMPACT_WING_W_DP   = 46;  // Symmetrical 46dp left + 34dp hole + 46dp right = 126dp (X=375..705px)
    public static final int DEF_EXPANDED_WIDTH_DP   = 376;
    public static final int DEF_PADDING_DP          = 7;
    public static final int DEF_CORNER_RADIUS_DP    = 19;
    public static final int DEF_OPACITY_PCT         = 96;

    public DisplayMode displayMode = DisplayMode.COMPACT_MODE;
    public boolean anchorTop = true;
    public int xOffsetDp = 0;
    public int yOffsetDp = DEF_COMPACT_Y_OFFSET_DP;
    public int cameraClearanceDp = DEF_CAMERA_CLEARANCE_DP;
    public int pillWidthDp = DEF_PILL_WIDTH_DP;
    public int pillHeightDp = DEF_COMPACT_HEIGHT_DP;
    public int leftWingWidthDp = DEF_COMPACT_WING_W_DP;
    public int rightWingWidthDp = DEF_COMPACT_WING_W_DP;
    public int leftWingOffsetDp = 0;
    public int rightWingOffsetDp = 0;
    public int expandedWidthDp = DEF_EXPANDED_WIDTH_DP;
    public int internalPaddingDp = DEF_PADDING_DP;
    public int cornerRadiusDp = DEF_CORNER_RADIUS_DP;
    public int opacityPct = DEF_OPACITY_PCT;

    public boolean showLeftWing = true;
    public boolean showRightWing = true;
    public boolean notificationAware = true;
    public boolean hideOnLockScreen = true;
    public boolean showProtectorGuide = false;

    public static NowBarConfig load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        NowBarConfig c = new NowBarConfig();
        // Auto-migrate to V2.4 DisplayCutout-centered flat-capsule geometry
        if (!p.getBoolean("nb2_calibrated_v24", false)) {
            c.applyPreset(DisplayMode.COMPACT_MODE);
            p.edit().putBoolean("nb2_calibrated_v24", true).apply();
            c.saveAndBroadcast(ctx);
            return c;
        }
        String modeStr = p.getString("nb2_mode", DisplayMode.COMPACT_MODE.name());
        try {
            if ("FLOATING_MODE".equalsIgnoreCase(modeStr)) {
                c.displayMode = DisplayMode.COMPACT_MODE;
            } else {
                c.displayMode = DisplayMode.valueOf(modeStr);
            }
        } catch (Exception e) {
            c.displayMode = DisplayMode.COMPACT_MODE;
        }
        c.anchorTop = p.getBoolean("nowbar_top_camera", true);
        c.xOffsetDp = p.getInt("nb2_x_offset", 0);
        c.yOffsetDp = p.getInt("nb2_y_offset", DEF_COMPACT_Y_OFFSET_DP);
        c.cameraClearanceDp = p.getInt("nb2_cam_clearance", DEF_CAMERA_CLEARANCE_DP);
        c.pillWidthDp = p.getInt("nb2_pill_width", DEF_PILL_WIDTH_DP);
        c.pillHeightDp = p.getInt("nb2_pill_height", DEF_COMPACT_HEIGHT_DP);
        c.leftWingWidthDp = p.getInt("nb2_left_wing_w", DEF_COMPACT_WING_W_DP);
        c.rightWingWidthDp = p.getInt("nb2_right_wing_w", DEF_COMPACT_WING_W_DP);
        c.leftWingOffsetDp = p.getInt("nb2_left_wing_off", 0);
        c.rightWingOffsetDp = p.getInt("nb2_right_wing_off", 0);
        c.expandedWidthDp = p.getInt("nb2_exp_width", DEF_EXPANDED_WIDTH_DP);
        c.internalPaddingDp = p.getInt("nb2_padding", DEF_PADDING_DP);
        c.cornerRadiusDp = p.getInt("nb2_corner_radius", DEF_CORNER_RADIUS_DP);
        c.opacityPct = p.getInt("nb2_opacity_pct", DEF_OPACITY_PCT);

        c.showLeftWing = p.getBoolean("nb2_show_left", true);
        c.showRightWing = p.getBoolean("nb2_show_right", true);
        c.notificationAware = p.getBoolean("nb2_notif_aware", true);
        c.hideOnLockScreen = p.getBoolean("nb2_hide_lock", true);
        c.showProtectorGuide = p.getBoolean("nb2_protector_guide", false);
        return c;
    }

    public void saveAndBroadcast(Context ctx) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean("nb2_calibrated_v24", true)
                .putString("nb2_mode", displayMode.name())
                .putBoolean("nowbar_top_camera", anchorTop)
                .putInt("nb2_x_offset", xOffsetDp)
                .putInt("nb2_y_offset", yOffsetDp)
                .putInt("nb2_cam_clearance", cameraClearanceDp)
                .putInt("nb2_pill_width", pillWidthDp)
                .putInt("nb2_pill_height", pillHeightDp)
                .putInt("nb2_left_wing_w", leftWingWidthDp)
                .putInt("nb2_right_wing_w", rightWingWidthDp)
                .putInt("nb2_left_wing_off", leftWingOffsetDp)
                .putInt("nb2_right_wing_off", rightWingOffsetDp)
                .putInt("nb2_exp_width", expandedWidthDp)
                .putInt("nb2_padding", internalPaddingDp)
                .putInt("nb2_corner_radius", cornerRadiusDp)
                .putInt("nb2_opacity_pct", opacityPct)
                .putBoolean("nb2_show_left", showLeftWing)
                .putBoolean("nb2_show_right", showRightWing)
                .putBoolean("nb2_notif_aware", notificationAware)
                .putBoolean("nb2_hide_lock", hideOnLockScreen)
                .putBoolean("nb2_protector_guide", showProtectorGuide)
                .apply();

        notifyLiveOverlay(ctx);
    }

    public void applyPreset(DisplayMode mode) {
        this.displayMode = mode;
        this.anchorTop = true;
        this.xOffsetDp = 0;
        this.leftWingOffsetDp = 0;
        this.rightWingOffsetDp = 0;
        switch (mode) {
            case CAMERA_MODE:
                this.yOffsetDp = DEF_CAMERA_Y_OFFSET_DP;
                this.cameraClearanceDp = DEF_CAMERA_CLEARANCE_DP;
                this.pillHeightDp = DEF_CAMERA_HEIGHT_DP;
                this.leftWingWidthDp = DEF_LEFT_WING_WIDTH_DP;
                this.rightWingWidthDp = DEF_RIGHT_WING_WIDTH_DP;
                this.internalPaddingDp = 7;
                this.cornerRadiusDp = 19;
                this.showLeftWing = true;
                this.showRightWing = true;
                break;
            case COMPACT_MODE:
            default:
                this.displayMode = DisplayMode.COMPACT_MODE;
                this.yOffsetDp = DEF_COMPACT_Y_OFFSET_DP;
                this.cameraClearanceDp = DEF_CAMERA_CLEARANCE_DP;
                this.pillHeightDp = DEF_COMPACT_HEIGHT_DP;
                this.leftWingWidthDp = DEF_COMPACT_WING_W_DP;
                this.rightWingWidthDp = DEF_COMPACT_WING_W_DP;
                this.internalPaddingDp = 5;
                this.cornerRadiusDp = 18;
                this.showLeftWing = true;
                this.showRightWing = true;
                break;
        }
    }

    public boolean isCameraContouredMode() {
        return anchorTop;
    }

    public static NowBarConfig resetToDefaults(Context ctx) {
        NowBarConfig c = new NowBarConfig();
        c.applyPreset(DisplayMode.COMPACT_MODE);
        c.saveAndBroadcast(ctx);
        return c;
    }

    public static void notifyLiveOverlay(Context ctx) {
        if (NowBarOverlayService.isRunning) {
            Intent i = new Intent(ctx, NowBarOverlayService.class);
            i.setAction(NowBarOverlayService.ACTION_REFRESH);
            ctx.startService(i);
        }
    }

    public int applyAlphaToColor(int baseRgb) {
        int alpha = Math.max(80, Math.min(255, (opacityPct * 255) / 100));
        return (alpha << 24) | (baseRgb & 0x00FFFFFF);
    }
}
