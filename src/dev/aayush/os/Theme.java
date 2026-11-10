package dev.aayush.os;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Monochrome + McLaren #81 Papaya Design System for Aayush OS.
 */
public final class Theme {
    public static final int BG_OBSIDIAN      = 0xFF0A0A0A;
    public static final int BG_CARBON        = 0xFF141414;
    public static final int BG_ELEVATED      = 0xFF1C1C1C;
    public static final int BORDER_GRAPHITE  = 0xFF333333;
    public static final int PAPAYA           = 0xFFFF8000;
    public static final int PAPAYA_DIM       = 0x33FF8000;
    public static final int TEXT_PRIMARY     = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY   = 0xFFB3B3B3;
    public static final int TEXT_MUTED       = 0xFF666666;
    public static final int TELEMETRY_GREEN  = 0xFF00E676;
    public static final int TELEMETRY_AMBER  = 0xFFFFC400;
    public static final int TELEMETRY_RED    = 0xFFFF5252;

    public static final Typeface MONO = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);
    public static final Typeface MONO_BOLD = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
    public static final Typeface SANS_BOLD = Typeface.create("sans-serif-medium", Typeface.BOLD);
    public static final Typeface SANS_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface PILL_TYPO = Typeface.create("sans-serif-condensed-medium", Typeface.BOLD);

    public static int dp(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable cardDrawable(Context ctx, int fillColor, int strokeColor, float radiusDp, float strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(dp(ctx, radiusDp));
        if (strokeDp > 0) {
            gd.setStroke(Math.max(1, dp(ctx, strokeDp)), strokeColor);
        }
        return gd;
    }

    public static LinearLayout createCard(Context ctx, boolean papayaHighlight) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 16);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(cardDrawable(
                ctx,
                BG_CARBON,
                papayaHighlight ? PAPAYA : BORDER_GRAPHITE,
                16f,
                papayaHighlight ? 1.5f : 1f
        ));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(ctx, 12);
        card.setLayoutParams(lp);
        return card;
    }

    public static TextView createSectionLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text.toUpperCase());
        tv.setTextColor(PAPAYA);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(MONO_BOLD);
        tv.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(ctx, 6);
        tv.setLayoutParams(lp);
        return tv;
    }

    public static TextView createPill(Context ctx, String label, int bgColor, int textColor, int borderColor) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(textColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(MONO_BOLD);
        tv.setGravity(Gravity.CENTER);
        int hPad = dp(ctx, 10);
        int vPad = dp(ctx, 5);
        tv.setPadding(hPad, vPad, hPad, vPad);
        tv.setBackground(cardDrawable(ctx, bgColor, borderColor, 999f, 1f));
        return tv;
    }

    public static View createDivider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(BORDER_GRAPHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(ctx, 1))
        );
        lp.topMargin = dp(ctx, 10);
        lp.bottomMargin = dp(ctx, 10);
        v.setLayoutParams(lp);
        return v;
    }
}
