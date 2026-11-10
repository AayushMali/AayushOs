package dev.aayush.os;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Settings -> Now Bar -> Display & Calibration Studio UI Builder (V2.3).
 * Features:
 * - True-to-scale Interactive Pixel 7 Hardware Cutout & Screen Protector Live Preview Canvas
 * - Real-time DisplayCutout coordinate telemetry (X=540px, Y=68px camera center, r=34px lens)
 * - 3 Hardware-Calibrated Mode Presets (CAMERA MODE, FLOATING MODE, COMPACT MODE)
 * - 1dp fine steppers and live overlay synchronization
 */
public final class NowBarCalibrationCard {

    private interface IntConsumer {
        void accept(int val);
    }

    private interface BoolConsumer {
        void accept(boolean checked);
    }

    /**
     * True-to-scale 1:1 proportional schematic preview of the top 250px of the Google Pixel 7 screen.
     * Accurately renders the status bar clock/icons, physical camera lens (r=34px at 540,68),
     * circular screen protector border, and the live Now Bar geometry.
     */
    private static final class HardwarePreviewCanvasView extends View {
        private final NowBarConfig[] cfgRef;
        private final NowBarConfig.HardwareGeometry hw;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sbTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint islandFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint islandStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wingTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lensFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lensRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint protectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hudTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path islandPath = new Path();
        private final Path collarPath = new Path();
        private final Path holePath = new Path();
        private final RectF tmpRect = new RectF();

        HardwarePreviewCanvasView(Context ctx, NowBarConfig[] cfgRef) {
            super(ctx);
            this.cfgRef = cfgRef;
            this.hw = NowBarConfig.inspectHardwareGeometry(ctx);

            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(0xFF08080A);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(Theme.BORDER_GRAPHITE);
            borderPaint.setStrokeWidth(Theme.dp(ctx, 1));

            hairPaint.setStyle(Paint.Style.STROKE);
            hairPaint.setStrokeWidth(Theme.dp(ctx, 1));
            float d = Theme.dp(ctx, 3);
            hairPaint.setPathEffect(new DashPathEffect(new float[]{d, d}, 0f));

            sbTextPaint.setTypeface(Theme.SANS_BOLD);
            sbTextPaint.setColor(0xAAECECEC);

            islandFillPaint.setStyle(Paint.Style.FILL);
            islandStrokePaint.setStyle(Paint.Style.STROKE);

            wingTextPaint.setTypeface(Theme.MONO_BOLD);
            wingTextPaint.setTextAlign(Paint.Align.CENTER);

            lensFillPaint.setStyle(Paint.Style.FILL);
            lensFillPaint.setColor(0xFF030304);

            lensRimPaint.setStyle(Paint.Style.STROKE);
            lensRimPaint.setColor(0xFF00E5FF);

            protectorPaint.setStyle(Paint.Style.STROKE);
            protectorPaint.setColor(Theme.TELEMETRY_GREEN);
            protectorPaint.setPathEffect(new DashPathEffect(new float[]{d, d * 0.7f}, 0f));

            hudTextPaint.setTypeface(Theme.MONO);
            hudTextPaint.setColor(Theme.TEXT_SECONDARY);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            NowBarConfig c = cfgRef[0];
            if (c == null) return;

            int vw = getWidth();
            int vh = getHeight();
            if (vw <= 0 || vh <= 0) return;

            // Map full Pixel 7 screen width (1080px) to view width
            float scale = (float) vw / (float) Math.max(1, hw.screenWidthPx);
            float dens = hw.density;

            // Background & frame representing top of Pixel 7 screen
            float r = Theme.dp(getContext(), 12);
            tmpRect.set(1, 1, vw - 1, vh - 1);
            canvas.drawRoundRect(tmpRect, r, r, bgPaint);
            canvas.drawRoundRect(tmpRect, r, r, borderPaint);

            float camX = hw.camCenterXPx * scale;
            float camY = hw.camCenterYPx * scale;
            float sbBottomY = hw.statusBarHeightPx * scale;

            // 1. Draw Status Bar Safe Inset Boundary (Y = 136px on Pixel 7)
            hairPaint.setColor(0x44FFFFFF);
            canvas.drawLine(0, sbBottomY, vw, sbBottomY, hairPaint);

            // 2. Draw Camera Center Alignment Hairlines (X = 540px, Y = 68px)
            hairPaint.setColor(0x5500E676);
            canvas.drawLine(0, camY, vw, camY, hairPaint);
            canvas.drawLine(camX, 0, camX, sbBottomY + Theme.dp(getContext(), 28), hairPaint);

            // 3. Draw Simulated Pixel 7 Status Bar Elements at Y = camY (68px)
            sbTextPaint.setTextSize(34f * scale);
            Paint.FontMetrics fm = sbTextPaint.getFontMetrics();
            float sbBaseline = camY - (fm.ascent + fm.descent) / 2f;
            canvas.drawText("10:21", 52f * scale, sbBaseline, sbTextPaint);

            // Simulated left notification icons (X = 185px, 232px)
            sbTextPaint.setColor(0x77ECECEC);
            canvas.drawCircle(195f * scale, camY, 13f * scale, sbTextPaint);
            canvas.drawCircle(240f * scale, camY, 13f * scale, sbTextPaint);

            // Simulated right status bar icons (Signal, Wi-Fi, Battery at X = 825..1025px)
            sbTextPaint.setColor(0xAAECECEC);
            canvas.drawText("▲ ▰ 66⚡", 825f * scale, sbBaseline, sbTextPaint);

            // 4. Calculate & Draw Live Now Bar / Dynamic Island at Exact Scaled Coordinates
            float pillHeightPx = c.pillHeightDp * dens;
            float clearDiameterPx = c.cameraClearanceDp * dens;
            float minCapsuleHeightPx = clearDiameterPx + (4f * dens);
            boolean contoured = c.isCameraContouredMode();
            float boxHeightPx = contoured ? Math.max(pillHeightPx, minCapsuleHeightPx) : pillHeightPx;

            float targetCenterYPx = hw.camCenterYPx + (c.yOffsetDp * dens);
            float winTopYPx = Math.max(0f, targetCenterYPx - (boxHeightPx / 2f));
            float islandCenterYPx = winTopYPx + (boxHeightPx / 2f);
            float islandCenterXPx = hw.camCenterXPx + (c.xOffsetDp * dens);

            float cx = islandCenterXPx * scale;
            float cy = islandCenterYPx * scale;

            islandFillPaint.setColor(c.applyAlphaToColor(0x0B0B0B));
            islandStrokePaint.setColor(Theme.PAPAYA);
            islandStrokePaint.setStrokeWidth(Math.max(1.5f, 1.3f * dens * scale));

            int spanLeftPx;
            int spanRightPx;

            if (contoured) {
                float holeRadiusPx = clearDiameterPx / 2f;
                float pillHalfHPx = boxHeightPx / 2f;
                float leftWPx = c.showLeftWing ? (c.leftWingWidthDp * dens) : 0f;
                float rightWPx = c.showRightWing ? (c.rightWingWidthDp * dens) : 0f;
                float leftOffPx = c.leftWingOffsetDp * dens;
                float rightOffPx = c.rightWingOffsetDp * dens;

                float leftEdgePx = c.showLeftWing
                        ? (islandCenterXPx - holeRadiusPx - leftWPx + leftOffPx)
                        : (islandCenterXPx - holeRadiusPx - 4f * dens);
                float rightEdgePx = c.showRightWing
                        ? (islandCenterXPx + holeRadiusPx + rightWPx + rightOffPx)
                        : (islandCenterXPx + holeRadiusPx + 4f * dens);

                spanLeftPx = Math.round(leftEdgePx);
                spanRightPx = Math.round(rightEdgePx);

                float cornerRPx = Math.min(c.cornerRadiusDp * dens, pillHalfHPx);

                collarPath.reset();
                tmpRect.set(
                        leftEdgePx * scale,
                        (islandCenterYPx - pillHalfHPx) * scale,
                        rightEdgePx * scale,
                        (islandCenterYPx + pillHalfHPx) * scale
                );
                collarPath.addRoundRect(tmpRect, cornerRPx * scale, cornerRPx * scale, Path.Direction.CW);

                holePath.reset();
                holePath.addCircle(cx, cy, holeRadiusPx * scale, Path.Direction.CW);

                islandPath.reset();
                islandPath.op(collarPath, holePath, Path.Op.DIFFERENCE);

                canvas.drawPath(islandPath, islandFillPaint);
                canvas.drawPath(collarPath, islandStrokePaint);

                // Wing sample labels
                wingTextPaint.setTextSize(21f * scale);
                Paint.FontMetrics wfm = wingTextPaint.getFontMetrics();
                float wBase = cy - (wfm.ascent + wfm.descent) / 2f;
                boolean compact = (c.displayMode == NowBarConfig.DisplayMode.COMPACT_MODE);
                if (c.showLeftWing) {
                    wingTextPaint.setColor(Theme.PAPAYA);
                    float lx = ((leftEdgePx + (islandCenterXPx - holeRadiusPx)) * 0.5f) * scale;
                    canvas.drawText(compact ? "#81" : "#81 5.1W", lx, wBase, wingTextPaint);
                }
                if (c.showRightWing) {
                    wingTextPaint.setColor(Theme.TEXT_PRIMARY);
                    float rx = (((islandCenterXPx + holeRadiusPx) + rightEdgePx) * 0.5f) * scale;
                    canvas.drawText(compact ? "82%•37°" : "82%•37°C", rx, wBase, wingTextPaint);
                }
            } else {
                float totalWPx = c.pillWidthDp * dens;
                float halfWPx = totalWPx / 2f;
                float halfHPx = pillHeightPx / 2f;
                float cornerRPx = Math.min(c.cornerRadiusDp * dens, halfHPx);
                spanLeftPx = Math.round(islandCenterXPx - halfWPx);
                spanRightPx = Math.round(islandCenterXPx + halfWPx);

                tmpRect.set(
                        (islandCenterXPx - halfWPx) * scale,
                        (islandCenterYPx - halfHPx) * scale,
                        (islandCenterXPx + halfWPx) * scale,
                        (islandCenterYPx + halfHPx) * scale
                );
                canvas.drawRoundRect(tmpRect, cornerRPx * scale, cornerRPx * scale, islandFillPaint);
                canvas.drawRoundRect(tmpRect, cornerRPx * scale, cornerRPx * scale, islandStrokePaint);

                wingTextPaint.setTextSize(23f * scale);
                Paint.FontMetrics wfm = wingTextPaint.getFontMetrics();
                float wBase = cy - (wfm.ascent + wfm.descent) / 2f;
                wingTextPaint.setColor(Theme.PAPAYA);
                canvas.drawText("#81 5.1W  |  82% • 37°C", cx, wBase, wingTextPaint);
            }

            // 5. Draw Physical Pixel 7 Camera Lens (r=34px) & Screen Protector Clearance Ring at (camX, camY)
            float lensR = hw.camLensRadiusPx * scale;
            canvas.drawCircle(camX, camY, lensR, lensFillPaint);
            lensRimPaint.setStrokeWidth(Math.max(1.2f, 2f * scale));
            canvas.drawCircle(camX, camY, lensR, lensRimPaint);
            // Tiny optical glint inside camera lens
            lensRimPaint.setColor(0x8800E5FF);
            canvas.drawCircle(camX - lensR * 0.28f, camY - lensR * 0.28f, lensR * 0.18f, lensRimPaint);
            lensRimPaint.setColor(0xFF00E5FF);

            // Screen protector clearance ring (r = cameraClearanceDp / 2)
            float protR = (clearDiameterPx / 2f) * scale;
            protectorPaint.setStrokeWidth(Math.max(1.5f, 3.5f * scale));
            canvas.drawCircle(camX, camY, protR, protectorPaint);

            // 6. Bottom Telemetry Readout Bar inside Preview Canvas
            hudTextPaint.setTextSize(Theme.dp(getContext(), 8.5f));
            hudTextPaint.setColor(Theme.TELEMETRY_GREEN);
            String line1 = String.format(Locale.US,
                    "CUTOUT: (%d, %dpx / %.1fdp) • LENS Ø: %dpx • CLEAR Ø: %ddp (%dpx)",
                    hw.camCenterXPx, hw.camCenterYPx, hw.pxToDp(hw.camCenterYPx),
                    hw.camLensRadiusPx * 2, c.cameraClearanceDp, Math.round(clearDiameterPx));
            canvas.drawText(line1, Theme.dp(getContext(), 8), vh - Theme.dp(getContext(), 19), hudTextPaint);

            hudTextPaint.setColor(Theme.TEXT_SECONDARY);
            String line2 = String.format(Locale.US,
                    "ISLAND Y-CTR: %dpx (Δ%+ddp) • X-SPAN: %d..%dpx • %s",
                    Math.round(islandCenterYPx), c.yOffsetDp, spanLeftPx, spanRightPx, c.displayMode.title);
            canvas.drawText(line2, Theme.dp(getContext(), 8), vh - Theme.dp(getContext(), 6), hudTextPaint);
        }
    }

    public static View build(final Context ctx) {
        final NowBarConfig[] cfgRef = new NowBarConfig[]{NowBarConfig.load(ctx)};

        final LinearLayout card = Theme.createCard(ctx, true);

        // Header
        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = Theme.createSectionLabel(ctx, "SETTINGS → NOW BAR → PIXEL 7 CALIBRATION STUDIO");
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(title);

        final TextView modeBadge = Theme.createPill(
                ctx, cfgRef[0].displayMode.title, Theme.BG_ELEVATED, Theme.TELEMETRY_GREEN, Theme.PAPAYA);
        topRow.addView(modeBadge);
        card.addView(topRow);

        final TextView modeDesc = new TextView(ctx);
        modeDesc.setText(cfgRef[0].displayMode.description);
        modeDesc.setTextColor(Theme.TEXT_SECONDARY);
        modeDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        card.addView(modeDesc);

        // Live 1:1 Hardware Cutout & Screen Protector Schematic Preview Canvas
        final HardwarePreviewCanvasView previewCanvas = new HardwarePreviewCanvasView(ctx, cfgRef);
        LinearLayout.LayoutParams pvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(ctx, 118));
        pvLp.topMargin = Theme.dp(ctx, 10);
        previewCanvas.setLayoutParams(pvLp);
        card.addView(previewCanvas);

        // 1. Three Display Modes Selector Row
        HorizontalScrollView modeScroll = new HorizontalScrollView(ctx);
        modeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout modeRow = new LinearLayout(ctx);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mrLp.topMargin = Theme.dp(ctx, 10);
        modeScroll.setLayoutParams(mrLp);

        final LinearLayout slidersContainer = new LinearLayout(ctx);
        slidersContainer.setOrientation(LinearLayout.VERTICAL);

        final Runnable rebuildSliders = new Runnable() {
            @Override
            public void run() {
                NowBarConfig c = cfgRef[0];
                modeBadge.setText(c.displayMode.title);
                modeDesc.setText(c.displayMode.description);
                previewCanvas.invalidate();
                slidersContainer.removeAllViews();
                populateSlidersAndToggles(ctx, slidersContainer, cfgRef, previewCanvas, this);
            }
        };

        for (final NowBarConfig.DisplayMode m : NowBarConfig.DisplayMode.values()) {
            TextView btn = Theme.createPill(ctx, m.title, Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.PAPAYA);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bLp.rightMargin = Theme.dp(ctx, 8);
            btn.setLayoutParams(bLp);
            btn.setOnClickListener(v -> {
                cfgRef[0].applyPreset(m);
                cfgRef[0].saveAndBroadcast(ctx);
                rebuildSliders.run();
                Toast.makeText(ctx, "Applied preset: " + m.title, Toast.LENGTH_SHORT).show();
            });
            modeRow.addView(btn);
        }

        modeScroll.addView(modeRow);
        card.addView(modeScroll);
        card.addView(Theme.createDivider(ctx));

        rebuildSliders.run();
        card.addView(slidersContainer);

        return card;
    }

    private static void populateSlidersAndToggles(
            final Context ctx,
            final LinearLayout container,
            final NowBarConfig[] cfgRef,
            final View previewCanvas,
            final Runnable rebuildCallback) {

        final NowBarConfig c = cfgRef[0];

        // Screen Protector Clearance Slider (Priority #1)
        container.addView(Theme.createSectionLabel(ctx, "01 // PIXEL 7 SCREEN PROTECTOR & CAMERA CLEARANCE"));
        addCalibSlider(ctx, container, "Circular Protector Hole Diameter (Ø)", "dp",
                28, 64, c.cameraClearanceDp, val -> {
                    c.cameraClearanceDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addToggleRow(ctx, container, "Show On-Screen Camera & Protector Alignment Reticle", c.showProtectorGuide, checked -> {
            c.showProtectorGuide = checked;
            c.saveAndBroadcast(ctx);
            previewCanvas.invalidate();
        });

        container.addView(Theme.createDivider(ctx));
        container.addView(Theme.createSectionLabel(ctx, "02 // HARDWARE-RELATIVE POSITIONING (0dp = CAMERA CENTER Y=68px)"));

        addCalibSlider(ctx, container, "Vertical Offset from Camera Center (ΔY)", "dp",
                -18, 90, c.yOffsetDp, val -> {
                    c.yOffsetDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addCalibSlider(ctx, container, "Horizontal Offset from Camera Center (ΔX)", "dp",
                -80, 80, c.xOffsetDp, val -> {
                    c.xOffsetDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addCalibSlider(ctx, container, "Left Wing Fine Trim", "dp",
                -24, 24, c.leftWingOffsetDp, val -> {
                    c.leftWingOffsetDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addCalibSlider(ctx, container, "Right Wing Fine Trim", "dp",
                -24, 24, c.rightWingOffsetDp, val -> {
                    c.rightWingOffsetDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        container.addView(Theme.createDivider(ctx));
        container.addView(Theme.createSectionLabel(ctx, "03 // SYMMETRICAL TELEMETRY WINGS & DIMENSIONS"));

        addCalibSlider(ctx, container, "Left Telemetry Wing Width", "dp",
                40, 120, c.leftWingWidthDp, val -> {
                    c.leftWingWidthDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addCalibSlider(ctx, container, "Right Telemetry Wing Width", "dp",
                40, 120, c.rightWingWidthDp, val -> {
                    c.rightWingWidthDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addCalibSlider(ctx, container, "Island / Wing Height", "dp",
                22, 48, c.pillHeightDp, val -> {
                    c.pillHeightDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addCalibSlider(ctx, container, "Expanded Control Panel Width", "dp",
                300, 396, c.expandedWidthDp, val -> {
                    c.expandedWidthDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addCalibSlider(ctx, container, "Internal Horizontal Padding", "dp",
                4, 18, c.internalPaddingDp, val -> {
                    c.internalPaddingDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addCalibSlider(ctx, container, "Corner Radius", "dp",
                8, 28, c.cornerRadiusDp, val -> {
                    c.cornerRadiusDp = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        addCalibSlider(ctx, container, "Surface Opacity", "%",
                50, 100, c.opacityPct, val -> {
                    c.opacityPct = val;
                    c.saveAndBroadcast(ctx);
                    previewCanvas.invalidate();
                });

        container.addView(Theme.createDivider(ctx));
        container.addView(Theme.createSectionLabel(ctx, "04 // INTELLIGENT NOTIFICATION YIELD & WING TOGGLES"));

        addToggleRow(ctx, container, "Intelligent Notification-Aware Auto-Yield", c.notificationAware, checked -> {
            c.notificationAware = checked;
            c.saveAndBroadcast(ctx);
        });

        addToggleRow(ctx, container, "Show Left Telemetry Wing", c.showLeftWing, checked -> {
            c.showLeftWing = checked;
            c.saveAndBroadcast(ctx);
            previewCanvas.invalidate();
        });

        addToggleRow(ctx, container, "Show Right Telemetry Wing", c.showRightWing, checked -> {
            c.showRightWing = checked;
            c.saveAndBroadcast(ctx);
            previewCanvas.invalidate();
        });

        addToggleRow(ctx, container, "Hide Overlay on Lock Screen", c.hideOnLockScreen, checked -> {
            c.hideOnLockScreen = checked;
            c.saveAndBroadcast(ctx);
        });

        addToggleRow(ctx, container, "Anchor at Top of Screen (Off = Bottom Now Bar)", c.anchorTop, checked -> {
            c.anchorTop = checked;
            c.saveAndBroadcast(ctx);
            previewCanvas.invalidate();
        });

        // Action Buttons: Test Notification Conflict & Reset Defaults
        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams arLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        arLp.topMargin = Theme.dp(ctx, 12);
        actionRow.setLayoutParams(arLp);

        TextView testNotifBtn = Theme.createPill(
                ctx, "🔔 TEST NOTIF YIELD (5S)", Theme.BG_ELEVATED, Theme.TELEMETRY_GREEN, Theme.BORDER_GRAPHITE);
        LinearLayout.LayoutParams tnLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tnLp.rightMargin = Theme.dp(ctx, 8);
        testNotifBtn.setLayoutParams(tnLp);
        testNotifBtn.setOnClickListener(v -> {
            NowBarState.triggerIncomingNotificationYield("test_conflict", "Incoming Heads-Up Test", 5000L);
            Toast.makeText(ctx, "Simulating incoming notification: Now Bar yielding for 5s...", Toast.LENGTH_SHORT).show();
        });

        TextView resetBtn = Theme.createPill(
                ctx, "↺ RESET PIXEL 7 DEFAULTS", Theme.PAPAYA, Theme.BG_OBSIDIAN, Theme.PAPAYA);
        resetBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        resetBtn.setOnClickListener(v -> {
            cfgRef[0] = NowBarConfig.resetToDefaults(ctx);
            rebuildCallback.run();
            Toast.makeText(ctx, "Restored Pixel 7 DisplayCutout-centered defaults (Y=68px)", Toast.LENGTH_SHORT).show();
        });

        actionRow.addView(testNotifBtn);
        actionRow.addView(resetBtn);
        container.addView(actionRow);

        LinearLayout testCallRow = new LinearLayout(ctx);
        testCallRow.setOrientation(LinearLayout.HORIZONTAL);
        testCallRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tcrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tcrLp.topMargin = Theme.dp(ctx, 8);
        testCallRow.setLayoutParams(tcrLp);

        TextView testCallBtn = Theme.createPill(
                ctx, "📞 SIMULATE INCOMING CALL", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.PAPAYA);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tcLp.rightMargin = Theme.dp(ctx, 8);
        testCallBtn.setLayoutParams(tcLp);
        testCallBtn.setOnClickListener(v -> {
            if (NowBarState.isCallActive()) {
                NowBarState.clearCall();
                Toast.makeText(ctx, "Call cleared.", Toast.LENGTH_SHORT).show();
            } else {
                NowBarState.updateCall("test_call_" + System.currentTimeMillis(), "Aayush Mali", "Incoming Call • Mobile", "Phone", true, null, null, null);
                Toast.makeText(ctx, "Simulating incoming call: Dynamic Island Call Pop-up active!", Toast.LENGTH_SHORT).show();
            }
        });

        TextView testActiveCallBtn = Theme.createPill(
                ctx, "📞 ACTIVE CALL (TIMER)", Theme.BG_ELEVATED, Theme.TELEMETRY_GREEN, Theme.BORDER_GRAPHITE);
        testActiveCallBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        testActiveCallBtn.setOnClickListener(v -> {
            if (NowBarState.isCallActive()) {
                NowBarState.clearCall();
                Toast.makeText(ctx, "Call cleared.", Toast.LENGTH_SHORT).show();
            } else {
                NowBarState.updateCall("test_active_" + System.currentTimeMillis(), "McLaren #81 Pit Wall", "Call in Progress", "Phone", false, null, null, null);
                Toast.makeText(ctx, "Active call simulated with live duration timer!", Toast.LENGTH_SHORT).show();
            }
        });

        testCallRow.addView(testCallBtn);
        testCallRow.addView(testActiveCallBtn);
        container.addView(testCallRow);
    }

    private static void addCalibSlider(
            final Context ctx,
            LinearLayout parent,
            final String label,
            final String unit,
            final int minVal,
            final int maxVal,
            int initialVal,
            final IntConsumer onChanged) {

        LinearLayout block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, Theme.dp(ctx, 4), 0, Theme.dp(ctx, 4));

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView lbl = new TextView(ctx);
        lbl.setText(label);
        lbl.setTextColor(Theme.TEXT_PRIMARY);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lbl.setTypeface(Theme.SANS_MEDIUM);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView valText = new TextView(ctx);
        valText.setText(String.format(Locale.US, "%d %s", initialVal, unit));
        valText.setTextColor(Theme.PAPAYA);
        valText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valText.setTypeface(Theme.MONO_BOLD);

        final SeekBar sb = new SeekBar(ctx);
        sb.setMax(maxVal - minVal);
        int clamped = Math.max(minVal, Math.min(maxVal, initialVal));
        sb.setProgress(clamped - minVal);

        // -1 and +1 fine calibration buttons
        TextView minusBtn = Theme.createPill(ctx, " - ", Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.BORDER_GRAPHITE);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mLp.leftMargin = Theme.dp(ctx, 8);
        mLp.rightMargin = Theme.dp(ctx, 4);
        minusBtn.setLayoutParams(mLp);
        minusBtn.setOnClickListener(v -> {
            int p = sb.getProgress();
            if (p > 0) sb.setProgress(p - 1);
        });

        TextView plusBtn = Theme.createPill(ctx, " + ", Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.BORDER_GRAPHITE);
        plusBtn.setOnClickListener(v -> {
            int p = sb.getProgress();
            if (p < sb.getMax()) sb.setProgress(p + 1);
        });

        top.addView(lbl);
        top.addView(valText);
        top.addView(minusBtn);
        top.addView(plusBtn);
        block.addView(top);

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int actual = minVal + progress;
                valText.setText(String.format(Locale.US, "%d %s", actual, unit));
                onChanged.accept(actual);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        block.addView(sb);
        parent.addView(block);
    }

    private static void addToggleRow(
            Context ctx,
            LinearLayout parent,
            String label,
            boolean initial,
            final BoolConsumer onChanged) {

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(ctx, 4), 0, Theme.dp(ctx, 4));

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Theme.TEXT_PRIMARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Theme.SANS_MEDIUM);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(ctx);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> onChanged.accept(isChecked));

        row.addView(tv);
        row.addView(sw);
        parent.addView(row);
    }
}
