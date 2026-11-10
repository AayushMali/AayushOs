package dev.aayush.os;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * System-wide ACTION_PROCESS_TEXT & ACTION_SEND overlay activity.
 * Samsung Galaxy AI-Style Writing Assist & Tone Switcher.
 * Appears as "⚡ Aayush AI" in Android's native text-selection menu across all apps.
 */
public class AiProcessTextActivity extends Activity {

    private String selectedText = "";
    private boolean isReadOnly = false;
    private TextView inputPreviewView;
    private TextView outputView;
    private ProgressBar progressBar;
    private TextView replaceBtn;
    private String currentOutput = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null) {
            isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false);
            CharSequence proc = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (proc != null) {
                selectedText = proc.toString();
            } else {
                CharSequence send = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if (send != null) selectedText = send.toString();
            }
        }

        if (TextUtils.isEmpty(selectedText)) {
            selectedText = SmartClipboard.getRawClipboardText(this);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG_OBSIDIAN);
        int pad = Theme.dp(this, 18);
        root.setPadding(pad, Theme.dp(this, 32), pad, pad);

        // Top Row: Title + Provider Badge + Close
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("⚡ AAYUSH AI // WRITING ASSIST");
        title.setTextColor(Theme.PAPAYA);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(Theme.MONO_BOLD);
        titleCol.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Galaxy AI-Style Tone & Grammar Rewriter (Ollama/M4)");
        sub.setTextColor(Theme.TEXT_SECONDARY);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        sub.setTypeface(Theme.SANS_MEDIUM);
        titleCol.addView(sub);

        topRow.addView(titleCol);

        TextView closeBtn = Theme.createPill(this, "✕", Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.PAPAYA);
        closeBtn.setOnClickListener(v -> finish());
        topRow.addView(closeBtn);
        root.addView(topRow);

        root.addView(Theme.createDivider(this));

        // Horizontal Tone Selector Chips
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        addToneButton(actions, "👔 PROFESSIONAL", PersonalAiAssistant.TaskType.TONE_PROFESSIONAL);
        addToneButton(actions, "😎 CASUAL", PersonalAiAssistant.TaskType.TONE_CASUAL);
        addToneButton(actions, "✍️ FIX GRAMMAR", PersonalAiAssistant.TaskType.FIX_GRAMMAR);
        addToneButton(actions, "✂️ CONCISE", PersonalAiAssistant.TaskType.TONE_CONCISE);
        addToneButton(actions, "📝 SUMMARIZE", PersonalAiAssistant.TaskType.SUMMARIZE);
        addToneButton(actions, "💻 EXPLAIN", PersonalAiAssistant.TaskType.EXPLAIN_CODE);

        hsv.addView(actions);
        root.addView(hsv);

        // Input Preview Box
        LinearLayout inputHeaderRow = new LinearLayout(this);
        inputHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ihLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ihLp.topMargin = Theme.dp(this, 10);
        inputHeaderRow.setLayoutParams(ihLp);

        TextView inputLabel = Theme.createSectionLabel(this, "ORIGINAL TEXT");
        inputHeaderRow.addView(inputLabel);
        root.addView(inputHeaderRow);

        inputPreviewView = new TextView(this);
        inputPreviewView.setTypeface(Theme.SANS_MEDIUM);
        inputPreviewView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        inputPreviewView.setTextColor(Theme.TEXT_SECONDARY);
        inputPreviewView.setMaxLines(3);
        inputPreviewView.setEllipsize(TextUtils.TruncateAt.END);
        int inPad = Theme.dp(this, 10);
        inputPreviewView.setPadding(inPad, inPad, inPad, inPad);
        inputPreviewView.setBackground(Theme.cardDrawable(this, Theme.BG_CARBON, Theme.BORDER_GRAPHITE, 10f, 1f));
        inputPreviewView.setText(!TextUtils.isEmpty(selectedText) ? selectedText : "[No text selected or in clipboard]");
        root.addView(inputPreviewView);

        // Progress Bar (Indeterminate)
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 4));
        pbLp.topMargin = Theme.dp(this, 8);
        progressBar.setLayoutParams(pbLp);
        root.addView(progressBar);

        // Output Result Card
        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sLp.topMargin = Theme.dp(this, 8);
        scroll.setLayoutParams(sLp);

        outputView = new TextView(this);
        outputView.setTypeface(Theme.SANS_MEDIUM);
        outputView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        outputView.setTextColor(Theme.TEXT_PRIMARY);
        outputView.setLineSpacing(0f, 1.25f);
        outputView.setTextIsSelectable(true);
        int cPad = Theme.dp(this, 14);
        outputView.setPadding(cPad, cPad, cPad, cPad);
        outputView.setBackground(Theme.cardDrawable(this, Theme.BG_CARBON, Theme.PAPAYA, 14f, 1.2f));
        scroll.addView(outputView);
        root.addView(scroll);

        // Bottom Action Row
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = Theme.dp(this, 12);
        bottomRow.setLayoutParams(brLp);

        if (!isReadOnly) {
            replaceBtn = Theme.createPill(this, "⚡ REPLACE TEXT", Theme.PAPAYA, Theme.BG_OBSIDIAN, Theme.PAPAYA);
            LinearLayout.LayoutParams rpLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
            rpLp.rightMargin = Theme.dp(this, 8);
            replaceBtn.setLayoutParams(rpLp);
            replaceBtn.setOnClickListener(v -> handleReplaceText());
            bottomRow.addView(replaceBtn);
        }

        TextView copyBtn = Theme.createPill(this, "📋 COPY", Theme.BG_ELEVATED, Theme.TEXT_PRIMARY, Theme.BORDER_GRAPHITE);
        LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyBtn.setLayoutParams(cpLp);
        copyBtn.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(currentOutput)) {
                SmartClipboard.copyToClipboard(this, "AayushAI", currentOutput);
                Toast.makeText(this, "Copied rewritten text!", Toast.LENGTH_SHORT).show();
            }
        });
        bottomRow.addView(copyBtn);

        root.addView(bottomRow);

        setContentView(root);

        if (!TextUtils.isEmpty(selectedText)) {
            triggerTask(PersonalAiAssistant.TaskType.TONE_PROFESSIONAL);
        } else {
            outputView.setText("Please select text in any app and tap '⚡ Aayush AI', or copy text to your clipboard first.");
        }
    }

    private void addToneButton(LinearLayout row, String label, final PersonalAiAssistant.TaskType t) {
        TextView btn = Theme.createPill(this, label, Theme.BG_ELEVATED, Theme.PAPAYA, Theme.BORDER_GRAPHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(this, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> triggerTask(t));
        row.addView(btn);
    }

    private void triggerTask(PersonalAiAssistant.TaskType t) {
        if (TextUtils.isEmpty(selectedText)) return;
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        outputView.setText("Rewriting tone via " + PersonalAiAssistant.getSelectedProvider(this).label + "...\n");

        PersonalAiAssistant.runTask(this, t, selectedText, result -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            currentOutput = (result != null) ? result.trim() : "";
            outputView.setText(currentOutput);
        });
    }

    private void handleReplaceText() {
        if (TextUtils.isEmpty(currentOutput)) return;
        Intent resultIntent = new Intent();
        resultIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, currentOutput);
        setResult(RESULT_OK, resultIntent);
        Toast.makeText(this, "Replaced with AI rewrite!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
