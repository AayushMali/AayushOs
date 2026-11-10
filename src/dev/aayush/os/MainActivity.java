package dev.aayush.os;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Aayush OS — Personal Android Ecosystem Command Center for Pixel 7 ('panther').
 * Integrates all 11 personalized modules in a cohesive Monochrome + McLaren #81 Papaya UI.
 */
public class MainActivity extends Activity {

    private HardwareMonitor hwMonitor;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean isResumed = false;

    // Triple-tap #81 badge detector for Module 11 Easter Egg
    private final long[] tapTimestamps = new long[3];
    private int tapIndex = 0;

    // 4 Navigation Tabs
    private final TextView[] tabButtons = new TextView[4];
    private final LinearLayout[] tabPages = new LinearLayout[4];
    private int activeTab = 0;

    // Tab 0: Live Telemetry & Now Bar UI references
    private TextView wattsBigText;
    private TextView batteryMetaText;
    private ProgressBar batteryProgress;
    private TextView thermalBadge;
    private TextView cpuClusterText;
    private ProgressBar cpuLoadBar;
    private TextView ramStorageText;
    private ProgressBar ramLoadBar;
    private TextView netSpeedText;
    private TextView nowBarStatusPill;
    private TextView clipBadgeView;
    private TextView clipSummaryView;
    private TextView clipActionBtn;
    private SmartClipboard.Analysis currentClip = new SmartClipboard.Analysis();

    // Tab 1: F1 #81, Automation, Gaming Hub
    private F1CommandCenter.TelemetryData f1Data;
    private TextView f1CountdownText;
    private TextView f1RaceTitleText;
    private TextView f1ScheduleText;
    private TextView f1PiastriBadge;
    private TextView f1StandingsTable;
    private TextView activeModePill;
    private TextView gamingSessionSummary;

    // Tab 3: MacBook Air M4 Cross-Device Intelligence & French Press Utility UI references
    private CrossDeviceDeckView crossDeviceDeck;
    private TextView frenchPressMainText;

    private final Runnable telemetryTick = new Runnable() {
        @Override
        public void run() {
            if (!isResumed) return;
            updateTelemetryUI();
            uiHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hwMonitor = new HardwareMonitor(this);
        f1Data = F1CommandCenter.loadCached(this);

        SharedPreferences prefs = getSharedPreferences("aayush_os_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("mod_nowbar", true) && Settings.canDrawOverlays(this) && !NowBarOverlayService.isRunning) {
            startNowBarService();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG_OBSIDIAN);
        int pad = Theme.dp(this, 16);
        root.setPadding(pad, Theme.dp(this, 78), pad, 0);

        // 1. Top Command Header with #81 McLaren Crest
        root.addView(buildHeader());

        // 2. Modular 4-Tab Navigation Bar
        root.addView(buildTabBar());
        root.addView(Theme.createDivider(this));

        // 3. Main Scrollable Dashboard Pages
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout pagesContainer = new LinearLayout(this);
        pagesContainer.setOrientation(LinearLayout.VERTICAL);
        pagesContainer.setPadding(0, Theme.dp(this, 4), 0, Theme.dp(this, 32));

        // Page 0: Clean Home Dashboard (Core Hardware Telemetry, Smart Clipboard, Now Bar & Kill Switch)
        tabPages[0] = new LinearLayout(this);
        tabPages[0].setOrientation(LinearLayout.VERTICAL);
        tabPages[0].addView(buildPowerTelemetryCard());
        tabPages[0].addView(buildTensorCpuCard());
        tabPages[0].addView(buildMemoryAndNetworkCard());
        tabPages[0].addView(buildNowBarAndClipboardCard());
        tabPages[0].addView(buildPrivilegeAndPrivacyCard());
        tabPages[0].addView(buildMasterKillSwitchCard());

        // Page 1: McLaren #81 F1 Command Centre, Smart Automation & Gaming Hub
        tabPages[1] = new LinearLayout(this);
        tabPages[1].setOrientation(LinearLayout.VERTICAL);
        tabPages[1].setVisibility(View.GONE);
        tabPages[1].addView(buildMcLarenF1Card());
        tabPages[1].addView(buildSmartAutomationCard());
        tabPages[1].addView(buildGamingHubCard());

        // Page 2: Developer Toolbox & Personal AI Assistant
        tabPages[2] = new LinearLayout(this);
        tabPages[2].setOrientation(LinearLayout.VERTICAL);
        tabPages[2].setVisibility(View.GONE);
        tabPages[2].addView(buildPersonalAiCard());
        tabPages[2].addView(buildDeveloperToolboxCard());

        // Page 3: Cross-Device Command Centre, MacBook Air M4 Companion & Personal Utility Widgets
        crossDeviceDeck = new CrossDeviceDeckView();
        tabPages[3] = new LinearLayout(this);
        tabPages[3].setOrientation(LinearLayout.VERTICAL);
        tabPages[3].setVisibility(View.GONE);
        tabPages[3].addView(crossDeviceDeck.buildAll(this));
        tabPages[3].addView(buildUtilityWidgetsCard());

        for (LinearLayout page : tabPages) {
            pagesContainer.addView(page);
        }

        scroll.addView(pagesContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        if (getIntent() != null && getIntent().getBooleanExtra("reconnect", false)) {
            MacCompanion.setManuallyDisconnected(this, false);
        }
        PixelCompanionServer.ensureStarted(this);

        // Fetch live F1 telemetry & MacBook Air M4 status asynchronously
        F1CommandCenter.fetchLiveTelemetry(this, data -> {
            f1Data = data;
            refreshF1Views();
            AayushDeckWidget.updateAllWidgets(this);
        });
        refreshMacCompanionStatus();
    }

    private View buildTabBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(this, 10);
        hsv.setLayoutParams(lp);

        String[] labels = new String[]{
                "🏠 HOME",
                "🏎️ #81 F1 & MODES",
                "🛠️ DEV & AI",
                "💻 MAC M4 & WIDGETS"
        };

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView tab = Theme.createPill(this, labels[i],
                    i == 0 ? Theme.PAPAYA : Theme.BG_ELEVATED,
                    i == 0 ? Theme.BG_OBSIDIAN : Theme.TEXT_PRIMARY,
                    i == 0 ? Theme.PAPAYA : Theme.BORDER_GRAPHITE);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tLp.rightMargin = Theme.dp(this, 8);
            tab.setLayoutParams(tLp);
            tab.setOnClickListener(v -> selectTab(idx));
            tabButtons[i] = tab;
            row.addView(tab);
        }

        hsv.addView(row);
        return hsv;
    }

    public void selectTab(int index) {
        activeTab = index;
        for (int i = 0; i < tabPages.length; i++) {
            boolean sel = (i == index);
            tabPages[i].setVisibility(sel ? View.VISIBLE : View.GONE);
            tabButtons[i].setBackground(Theme.cardDrawable(
                    this,
                    sel ? Theme.PAPAYA : Theme.BG_ELEVATED,
                    sel ? Theme.PAPAYA : Theme.BORDER_GRAPHITE,
                    999f, 1f));
            tabButtons[i].setTextColor(sel ? Theme.BG_OBSIDIAN : Theme.TEXT_PRIMARY);
        }
        if (index == 3) {
            refreshMacCompanionStatus();
        }
    }

    private EditText createStyledInput(String hint, String initialText, int maxLines) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Theme.TEXT_MUTED);
        et.setText(initialText);
        et.setTextColor(Theme.TEXT_PRIMARY);
        et.setTypeface(Theme.MONO);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        et.setMaxLines(maxLines);
        int pad = Theme.dp(this, 10);
        et.setPadding(pad, pad, pad, pad);
        et.setBackground(Theme.cardDrawable(this, 0xFF090909, Theme.BORDER_GRAPHITE, 10f, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(this, 6);
        lp.bottomMargin = Theme.dp(this, 6);
        et.setLayoutParams(lp);
        return et;
    }

    // =========================================================================
    // TAB 2: PERSONAL AI ASSISTANT (MODULE 04) & DEVELOPER TOOLBOX (MODULE 03)
    // =========================================================================

    private View buildPersonalAiCard() {
        LinearLayout card = Theme.createCard(this, true);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = Theme.createSectionLabel(this, "MODULE 04 // PERSONAL AI ASSISTANT (GEMINI • CHATGPT • LOCAL M4)");
        LinearLayout.LayoutParams aiLblLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        aiLblLp.rightMargin = Theme.dp(this, 8);
        label.setLayoutParams(aiLblLp);
        topRow.addView(label);

        final TextView providerBadge = Theme.createPill(
                this,
                PersonalAiAssistant.getSelectedProvider(this).label,
                Theme.BG_ELEVATED,
                Theme.PAPAYA,
                Theme.PAPAYA
        );
        topRow.addView(providerBadge);
        card.addView(topRow);

        // Provider Switcher Row
        HorizontalScrollView provScroll = new HorizontalScrollView(this);
        provScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout provRow = new LinearLayout(this);
        provRow.setOrientation(LinearLayout.HORIZONTAL);

        final EditText endpointInput = createStyledInput(
                "Endpoint URL",
                PersonalAiAssistant.getCustomEndpoint(this, PersonalAiAssistant.getSelectedProvider(this)),
                2
        );
        final EditText modelInput = createStyledInput(
                "Model Name (e.g. gemini-2.5-flash / gpt-4o-mini / llama3.2)",
                PersonalAiAssistant.getCustomModel(this, PersonalAiAssistant.getSelectedProvider(this)),
                1
        );
        final EditText apiKeyInput = createStyledInput(
                "API Key (optional for Local Mac Ollama / Offline Engine)",
                PersonalAiAssistant.getApiKey(this, PersonalAiAssistant.getSelectedProvider(this)),
                1
        );

        for (final PersonalAiAssistant.Provider p : PersonalAiAssistant.Provider.values()) {
            addActionPill(provRow, p.label, false, () -> {
                PersonalAiAssistant.setSelectedProvider(this, p);
                providerBadge.setText(p.label);
                endpointInput.setText(PersonalAiAssistant.getCustomEndpoint(this, p));
                modelInput.setText(PersonalAiAssistant.getCustomModel(this, p));
                apiKeyInput.setText(PersonalAiAssistant.getApiKey(this, p));
            });
        }
        provScroll.addView(provRow);
        card.addView(provScroll);

        card.addView(endpointInput);
        card.addView(modelInput);
        card.addView(apiKeyInput);

        TextView saveCfgBtn = Theme.createPill(this, "SAVE AI PROVIDER CONFIG", Theme.BG_ELEVATED, Theme.TELEMETRY_GREEN, Theme.BORDER_GRAPHITE);
        saveCfgBtn.setOnClickListener(v -> {
            PersonalAiAssistant.Provider cur = PersonalAiAssistant.getSelectedProvider(this);
            PersonalAiAssistant.saveConfig(
                    this,
                    cur,
                    apiKeyInput.getText().toString(),
                    endpointInput.getText().toString(),
                    modelInput.getText().toString()
            );
            Toast.makeText(this, "Saved config for " + cur.label, Toast.LENGTH_SHORT).show();
        });
        card.addView(saveCfgBtn);

        card.addView(Theme.createDivider(this));

        final EditText promptInput = createStyledInput(
                "Paste code, stack trace, or natural language command prompt...",
                "find all processes listening on port 8080 and kill them on macOS",
                5
        );
        card.addView(promptInput);

        final TextView aiOutput = new TextView(this);
        aiOutput.setTypeface(Theme.MONO);
        aiOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        aiOutput.setTextColor(Theme.TEXT_PRIMARY);
        int pad = Theme.dp(this, 10);
        aiOutput.setPadding(pad, pad, pad, pad);
        aiOutput.setBackground(Theme.cardDrawable(this, 0xFF080808, Theme.BORDER_GRAPHITE, 10f, 1f));
        aiOutput.setText("// Tip: You can also highlight text in ANY app on your Pixel 7 and tap '⚡ Aayush AI' in the popup menu.");

        HorizontalScrollView taskScroll = new HorizontalScrollView(this);
        taskScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout taskRow = new LinearLayout(this);
        taskRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.bottomMargin = Theme.dp(this, 8);
        taskScroll.setLayoutParams(trLp);

        addActionPill(taskRow, "USE CLIPBOARD", false, () -> {
            SmartClipboard.Analysis c = SmartClipboard.inspectClipboard(this);
            if (!TextUtils.isEmpty(c.rawText)) promptInput.setText(c.rawText);
        });
        addActionPill(taskRow, "⚡ CLI COMMAND", true, () -> {
            aiOutput.setText("Generating CLI command...");
            PersonalAiAssistant.runTask(this, PersonalAiAssistant.TaskType.GEN_COMMAND,
                    promptInput.getText().toString(), aiOutput::setText);
        });
        addActionPill(taskRow, "EXPLAIN CODE", false, () -> {
            aiOutput.setText("Analyzing code...");
            PersonalAiAssistant.runTask(this, PersonalAiAssistant.TaskType.EXPLAIN_CODE,
                    promptInput.getText().toString(), aiOutput::setText);
        });
        addActionPill(taskRow, "SUMMARIZE", false, () -> {
            aiOutput.setText("Summarizing...");
            PersonalAiAssistant.runTask(this, PersonalAiAssistant.TaskType.SUMMARIZE,
                    promptInput.getText().toString(), aiOutput::setText);
        });
        addActionPill(taskRow, "REFACTOR", false, () -> {
            aiOutput.setText("Refactoring...");
            PersonalAiAssistant.runTask(this, PersonalAiAssistant.TaskType.REFACTOR,
                    promptInput.getText().toString(), aiOutput::setText);
        });

        taskScroll.addView(taskRow);
        card.addView(taskScroll);
        card.addView(aiOutput);

        return card;
    }

    private View buildDeveloperToolboxCard() {
        LinearLayout card = Theme.createCard(this, false);
        card.addView(Theme.createSectionLabel(this, "MODULE 03 // DEVELOPER TOOLBOX (JSON • JWT • REGEX • API • DNS • QR)"));

        final EditText primaryInput = createStyledInput(
                "Input payload (JSON, JWT, Base64, URL, Hostname, or API URL)...",
                "{\"team\":\"McLaren\",\"driver\":\"Oscar Piastri\",\"car\":81,\"device\":\"Pixel 7\"}",
                5
        );
        final EditText secondaryInput = createStyledInput(
                "Secondary input (Regex pattern or POST JSON body when needed)...",
                "([A-Za-z]+)\\s+(\\d+)",
                2
        );
        card.addView(primaryInput);
        card.addView(secondaryInput);

        final TextView toolOutput = new TextView(this);
        toolOutput.setTypeface(Theme.MONO);
        toolOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        toolOutput.setTextColor(Theme.TELEMETRY_GREEN);
        int pad = Theme.dp(this, 10);
        toolOutput.setPadding(pad, pad, pad, pad);
        toolOutput.setBackground(Theme.cardDrawable(this, 0xFF080808, Theme.BORDER_GRAPHITE, 10f, 1f));
        toolOutput.setText("Select any developer tool below to execute on the input above.");

        final ImageView qrImageView = new ImageView(this);
        qrImageView.setVisibility(View.GONE);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(
                Theme.dp(this, 180), Theme.dp(this, 180));
        qrLp.gravity = Gravity.CENTER_HORIZONTAL;
        qrLp.topMargin = Theme.dp(this, 10);
        qrImageView.setLayoutParams(qrLp);

        HorizontalScrollView toolScroll1 = new HorizontalScrollView(this);
        toolScroll1.setHorizontalScrollBarEnabled(false);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        addActionPill(row1, "PRETTY JSON", true, () -> {
            qrImageView.setVisibility(View.GONE);
            toolOutput.setText(DeveloperToolbox.formatJson(primaryInput.getText().toString(), true));
        });
        addActionPill(row1, "MINIFY JSON", false, () -> {
            qrImageView.setVisibility(View.GONE);
            toolOutput.setText(DeveloperToolbox.formatJson(primaryInput.getText().toString(), false));
        });
        addActionPill(row1, "DECODE JWT", false, () -> {
            qrImageView.setVisibility(View.GONE);
            toolOutput.setText(DeveloperToolbox.decodeJwtFull(primaryInput.getText().toString()));
        });
        addActionPill(row1, "B64 ENCODE", false, () -> {
            qrImageView.setVisibility(View.GONE);
            toolOutput.setText(DeveloperToolbox.base64Convert(primaryInput.getText().toString(), true));
        });
        addActionPill(row1, "B64 DECODE", false, () -> {
            qrImageView.setVisibility(View.GONE);
            toolOutput.setText(DeveloperToolbox.base64Convert(primaryInput.getText().toString(), false));
        });
        addActionPill(row1, "EPOCH / URL", false, () -> {
            qrImageView.setVisibility(View.GONE);
            toolOutput.setText(DeveloperToolbox.urlOrEpochConvert(primaryInput.getText().toString()));
        });
        toolScroll1.addView(row1);
        card.addView(toolScroll1);

        HorizontalScrollView toolScroll2 = new HorizontalScrollView(this);
        toolScroll2.setHorizontalScrollBarEnabled(false);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2Lp.topMargin = Theme.dp(this, 8);
        r2Lp.bottomMargin = Theme.dp(this, 8);
        toolScroll2.setLayoutParams(r2Lp);

        addActionPill(row2, "TEST REGEX", false, () -> {
            qrImageView.setVisibility(View.GONE);
            toolOutput.setText(DeveloperToolbox.testRegex(
                    secondaryInput.getText().toString(),
                    primaryInput.getText().toString()));
        });
        addActionPill(row2, "HTTP GET TEST", true, () -> {
            qrImageView.setVisibility(View.GONE);
            String u = primaryInput.getText().toString().trim();
            if (!u.startsWith("http")) {
                u = "https://api.github.com/zen";
                primaryInput.setText(u);
            }
            toolOutput.setText("Executing HTTP GET " + u + " ...");
            DeveloperToolbox.executeHttpTest("GET", u, null, toolOutput::setText);
        });
        addActionPill(row2, "DNS & TCP PING", false, () -> {
            qrImageView.setVisibility(View.GONE);
            toolOutput.setText("Running DNS & TCP latency diagnostics...");
            DeveloperToolbox.runNetworkDiagnostics(this, primaryInput.getText().toString(), toolOutput::setText);
        });
        addActionPill(row2, "GENERATE QR", false, () -> {
            toolOutput.setText("Rendering QR Matrix for payload...");
            DeveloperToolbox.generateQrCode(primaryInput.getText().toString(), Theme.dp(this, 200), bmp -> {
                qrImageView.setImageBitmap(bmp);
                qrImageView.setVisibility(View.VISIBLE);
                toolOutput.setText("QR Matrix generated (" + primaryInput.getText().length() + " chars)");
            });
        });
        addActionPill(row2, "COPY OUTPUT", false, () -> {
            SmartClipboard.copyToClipboard(this, "DevTool", toolOutput.getText().toString());
            Toast.makeText(this, "Copied output to clipboard", Toast.LENGTH_SHORT).show();
        });

        toolScroll2.addView(row2);
        card.addView(toolScroll2);
        card.addView(toolOutput);
        card.addView(qrImageView);

        return card;
    }

    // =========================================================================
    // TAB 3: MACBOOK AIR M4 COMPANION (MODULE 02) & UTILITY WIDGETS (MODULE 10)
    // =========================================================================

    private void refreshMacCompanionStatus() {
        if (crossDeviceDeck != null) {
            crossDeviceDeck.refreshAll(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CrossDeviceDeckView.REQ_PICK_FILE_FOR_MAC
                && resultCode == Activity.RESULT_OK
                && data != null
                && data.getData() != null
                && crossDeviceDeck != null) {
            crossDeviceDeck.handlePickedFileResult(this, data.getData());
        }
    }

    private View buildUtilityWidgetsCard() {
        LinearLayout card = Theme.createCard(this, false);
        card.addView(Theme.createSectionLabel(this, "MODULE 10 // FRENCH PRESS BREW TIMER & HOME SCREEN WIDGETS"));

        frenchPressMainText = new TextView(this);
        frenchPressMainText.setTypeface(Theme.MONO_BOLD);
        frenchPressMainText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        frenchPressMainText.setTextColor(Theme.PAPAYA);
        frenchPressMainText.setText("☕ FRENCH PRESS: READY (0:30 BLOOM → 3:30 STEEP → 4:00 PLUNGE)");
        card.addView(frenchPressMainText);

        HorizontalScrollView fpScroll = new HorizontalScrollView(this);
        fpScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout fpRow = new LinearLayout(this);
        fpRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams fpLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fpLp.topMargin = Theme.dp(this, 8);
        fpScroll.setLayoutParams(fpLp);

        addActionPill(fpRow, "☕ START 4:00 BREW", true, () -> {
            if (!NowBarOverlayService.isRunning) startNowBarService();
            NowBarState.startFrenchPressTimer();
            AayushDeckWidget.updateAllWidgets(this);
            updateTelemetryUI();
        });
        addActionPill(fpRow, "⏹ STOP TIMER", false, () -> {
            NowBarState.stopTimer();
            AayushDeckWidget.updateAllWidgets(this);
            updateTelemetryUI();
        });
        addActionPill(fpRow, "📌 PIN #81 WIDGET TO HOME SCREEN", false, () -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AppWidgetManager mgr = getSystemService(AppWidgetManager.class);
                ComponentName provider = new ComponentName(this, AayushDeckWidget.class);
                if (mgr != null && mgr.isRequestPinAppWidgetSupported()) {
                    mgr.requestPinAppWidget(provider, null, null);
                    return;
                }
            }
            Toast.makeText(this, "Long-press Home Screen → Widgets → Aayush OS", Toast.LENGTH_LONG).show();
        });

        fpScroll.addView(fpRow);
        card.addView(fpScroll);

        return card;
    }

    // =========================================================================
    // TAB 0 & TAB 1 CARDS
    // =========================================================================

    private void startNowBarService() {
        Intent i = new Intent(this, NowBarOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void stopNowBarService() {
        stopService(new Intent(this, NowBarOverlayService.class));
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView overline = new TextView(this);
        overline.setText("AAYUSH OS // PIXEL 7 PANTHER");
        overline.setTextColor(Theme.PAPAYA);
        overline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        overline.setTypeface(Theme.MONO_BOLD);
        overline.setLetterSpacing(0.12f);
        leftCol.addView(overline);

        TextView mainTitle = new TextView(this);
        mainTitle.setText("Command Center");
        mainTitle.setTextColor(Theme.TEXT_PRIMARY);
        mainTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mainTitle.setTypeface(Theme.SANS_BOLD);
        leftCol.addView(mainTitle);

        TextView subTitle = new TextView(this);
        subTitle.setText("ANDROID 17 • 90HZ • MCLAREN #81 EDITION");
        subTitle.setTextColor(Theme.TEXT_SECONDARY);
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        subTitle.setTypeface(Theme.MONO);
        leftCol.addView(subTitle);

        // #81 McLaren Racing Badge (Triple-tap triggers Secret Engineering Vault)
        TextView badge81 = new TextView(this);
        badge81.setText("#81 PIASTRI");
        badge81.setTextColor(Theme.BG_OBSIDIAN);
        badge81.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        badge81.setTypeface(Theme.MONO_BOLD);
        int hPad = Theme.dp(this, 14);
        int vPad = Theme.dp(this, 8);
        badge81.setPadding(hPad, vPad, hPad, vPad);
        badge81.setBackground(Theme.cardDrawable(this, Theme.PAPAYA, Theme.PAPAYA, 12f, 0f));
        badge81.setOnClickListener(v -> {
            long now = SystemClock.uptimeMillis();
            tapTimestamps[tapIndex % 3] = now;
            tapIndex++;
            if (tapIndex >= 3) {
                long oldest = tapTimestamps[tapIndex % 3];
                if (now - oldest < 900L) {
                    tapIndex = 0;
                    Toast.makeText(this, "Unlocked Panther #81 Engineering Vault", Toast.LENGTH_SHORT).show();
                    VaultDialog.show(this);
                }
            }
        });

        header.addView(leftCol);
        header.addView(badge81);
        return header;
    }

    private View buildMcLarenF1Card() {
        LinearLayout card = Theme.createCard(this, true);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = Theme.createSectionLabel(this, "MODULE 07 // MCLAREN #81 F1 COMMAND CENTRE");
        LinearLayout.LayoutParams f1LblLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        f1LblLp.rightMargin = Theme.dp(this, 8);
        label.setLayoutParams(f1LblLp);
        topRow.addView(label);

        TextView syncBtn = Theme.createPill(this, "SYNC LIVE API", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.PAPAYA);
        syncBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Fetching live Jolpica/Ergast F1 telemetry...", Toast.LENGTH_SHORT).show();
            F1CommandCenter.fetchLiveTelemetry(this, data -> {
                f1Data = data;
                refreshF1Views();
            });
        });
        topRow.addView(syncBtn);
        card.addView(topRow);

        f1CountdownText = new TextView(this);
        f1CountdownText.setTypeface(Theme.MONO_BOLD);
        f1CountdownText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        f1CountdownText.setTextColor(Theme.PAPAYA);
        card.addView(f1CountdownText);

        f1RaceTitleText = new TextView(this);
        f1RaceTitleText.setTypeface(Theme.SANS_BOLD);
        f1RaceTitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        f1RaceTitleText.setTextColor(Theme.TEXT_PRIMARY);
        card.addView(f1RaceTitleText);

        f1ScheduleText = new TextView(this);
        f1ScheduleText.setTypeface(Theme.MONO);
        f1ScheduleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        f1ScheduleText.setTextColor(Theme.TEXT_SECONDARY);
        card.addView(f1ScheduleText);

        card.addView(Theme.createDivider(this));

        f1PiastriBadge = new TextView(this);
        f1PiastriBadge.setTypeface(Theme.MONO_BOLD);
        f1PiastriBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        f1PiastriBadge.setTextColor(Theme.BG_OBSIDIAN);
        int pad = Theme.dp(this, 10);
        f1PiastriBadge.setPadding(pad, pad, pad, pad);
        f1PiastriBadge.setBackground(Theme.cardDrawable(this, Theme.PAPAYA, Theme.PAPAYA, 10f, 0f));
        card.addView(f1PiastriBadge);

        f1StandingsTable = new TextView(this);
        f1StandingsTable.setTypeface(Theme.MONO);
        f1StandingsTable.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        f1StandingsTable.setTextColor(Theme.TEXT_PRIMARY);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = Theme.dp(this, 10);
        f1StandingsTable.setLayoutParams(stLp);
        card.addView(f1StandingsTable);

        TextView raceAlertBtn = Theme.createPill(
                this,
                "PUSH #81 RACE CONTROL ALERT TO NOW BAR & NOTIFICATIONS",
                Theme.BG_ELEVATED,
                Theme.PAPAYA,
                Theme.BORDER_GRAPHITE
        );
        LinearLayout.LayoutParams raLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        raLp.topMargin = Theme.dp(this, 10);
        raceAlertBtn.setLayoutParams(raLp);
        raceAlertBtn.setOnClickListener(v -> {
            F1CommandCenter.triggerRaceDayNotification(this, f1Data);
            Toast.makeText(this, "Dispatched #81 McLaren Race Control alert!", Toast.LENGTH_SHORT).show();
        });
        card.addView(raceAlertBtn);

        refreshF1Views();
        return card;
    }

    private void refreshF1Views() {
        if (f1Data == null || f1CountdownText == null) return;
        F1CommandCenter.updateCountdownString(f1Data);
        f1CountdownText.setText(f1Data.roundStr + " // " + f1Data.countdownStr);
        f1RaceTitleText.setText(f1Data.raceName);
        f1ScheduleText.setText(f1Data.circuitName + "\n" + f1Data.qualiIstTimeStr + "  |  " + f1Data.raceIstTimeStr);
        f1PiastriBadge.setText(f1Data.piastriStanding + "\n" + f1Data.mclarenStanding);
        f1StandingsTable.setText(f1Data.topDriversTable);
    }

    private View buildSmartAutomationCard() {
        LinearLayout card = Theme.createCard(this, false);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = Theme.createSectionLabel(this, "MODULE 06 // SMART AUTOMATION (ATOMIC ROLLBACK)");
        LinearLayout.LayoutParams saLblLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saLblLp.rightMargin = Theme.dp(this, 8);
        label.setLayoutParams(saLblLp);
        headerRow.addView(label);

        SmartAutomation.Mode cur = SmartAutomation.getActiveMode(this);
        activeModePill = Theme.createPill(this, cur.title, Theme.BG_ELEVATED, Theme.TELEMETRY_GREEN, Theme.PAPAYA);
        headerRow.addView(activeModePill);
        card.addView(headerRow);

        TextView note = new TextView(this);
        note.setText("Every mode snapshots your baseline settings (refresh rate, DND, timeout, DNS, animations) to JSON before applying and restores them atomically when turned off.");
        note.setTextColor(Theme.TEXT_SECONDARY);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        card.addView(note);

        SmartAutomation.Mode[] modes = new SmartAutomation.Mode[]{
                SmartAutomation.Mode.CODING,
                SmartAutomation.Mode.GAMING,
                SmartAutomation.Mode.WORK,
                SmartAutomation.Mode.BATTERY_SAVER,
                SmartAutomation.Mode.NIGHT,
                SmartAutomation.Mode.NONE
        };

        for (final SmartAutomation.Mode m : modes) {
            LinearLayout modeRow = new LinearLayout(this);
            modeRow.setOrientation(LinearLayout.HORIZONTAL);
            modeRow.setGravity(Gravity.CENTER_VERTICAL);
            int p = Theme.dp(this, 10);
            modeRow.setPadding(p, p, p, p);
            modeRow.setBackground(Theme.cardDrawable(this, Theme.BG_ELEVATED, Theme.BORDER_GRAPHITE, 12f, 1f));
            LinearLayout.LayoutParams mrLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mrLp.topMargin = Theme.dp(this, 8);
            modeRow.setLayoutParams(mrLp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView mTitle = new TextView(this);
            mTitle.setText(m == SmartAutomation.Mode.NONE ? "↺ RESTORE BASELINE STATE" : m.title);
            mTitle.setTextColor(m == SmartAutomation.Mode.NONE ? Theme.PAPAYA : Theme.TEXT_PRIMARY);
            mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            mTitle.setTypeface(Theme.SANS_BOLD);
            textCol.addView(mTitle);

            TextView mSub = new TextView(this);
            mSub.setText(m.summary);
            mSub.setTextColor(Theme.TEXT_SECONDARY);
            mSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            textCol.addView(mSub);

            TextView actBtn = Theme.createPill(this, m == SmartAutomation.Mode.NONE ? "RESET" : "APPLY",
                    Theme.BG_CARBON, Theme.PAPAYA, Theme.PAPAYA);
            actBtn.setOnClickListener(v -> {
                SmartAutomation.activateMode(this, m);
                SmartAutomation.Mode nowActive = SmartAutomation.getActiveMode(this);
                activeModePill.setText(nowActive.title);
                AayushDeckWidget.updateAllWidgets(this);
            });

            modeRow.addView(textCol);
            modeRow.addView(actBtn);
            card.addView(modeRow);
        }

        return card;
    }

    private View buildGamingHubCard() {
        LinearLayout card = Theme.createCard(this, false);
        card.addView(Theme.createSectionLabel(this, "MODULE 08 // GAMING HUB & THERMAL SESSION LOGGER"));

        gamingSessionSummary = new TextView(this);
        gamingSessionSummary.setTypeface(Theme.MONO);
        gamingSessionSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        gamingSessionSummary.setTextColor(Theme.TELEMETRY_GREEN);
        gamingSessionSummary.setText(GamingHub.endOrFormatSessionSummary(this, hwMonitor, false));
        card.addView(gamingSessionSummary);

        HorizontalScrollView cloudScroll = new HorizontalScrollView(this);
        cloudScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout cloudRow = new LinearLayout(this);
        cloudRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crLp.topMargin = Theme.dp(this, 10);
        cloudScroll.setLayoutParams(crLp);

        addActionPill(cloudRow, "🎮 LAUNCH STEAM", true, () -> {
            GamingHub.launchSteam(this, hwMonitor);
            gamingSessionSummary.setText(GamingHub.endOrFormatSessionSummary(this, hwMonitor, false));
        });

        addActionPill(cloudRow, "☁️ XBOX CLOUD GAMING", false, () -> {
            GamingHub.launchXboxCloud(this, hwMonitor);
            gamingSessionSummary.setText(GamingHub.endOrFormatSessionSummary(this, hwMonitor, false));
        });

        addActionPill(cloudRow, "START SESSION BENCHMARK", false, () -> {
            GamingHub.startSessionLog(this, "Custom Benchmark", hwMonitor);
            SmartAutomation.activateMode(this, SmartAutomation.Mode.GAMING);
            activeModePill.setText(SmartAutomation.getActiveMode(this).title);
            gamingSessionSummary.setText(GamingHub.endOrFormatSessionSummary(this, hwMonitor, false));
        });

        addActionPill(cloudRow, "END SESSION & RESTORE", false, () -> {
            gamingSessionSummary.setText(GamingHub.endOrFormatSessionSummary(this, hwMonitor, true));
            activeModePill.setText(SmartAutomation.getActiveMode(this).title);
        });

        cloudScroll.addView(cloudRow);
        card.addView(cloudScroll);

        card.addView(Theme.createDivider(this));
        card.addView(Theme.createSectionLabel(this, "DISCOVERED GAMES ON PIXEL 7"));

        List<GamingHub.GameEntry> games = GamingHub.discoverGames(this);
        for (final GamingHub.GameEntry g : games) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 6));

            TextView gTitle = new TextView(this);
            gTitle.setText("🕹️ " + g.label + " (" + g.packageName + ")");
            gTitle.setTextColor(Theme.TEXT_PRIMARY);
            gTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            gTitle.setTypeface(Theme.MONO);
            gTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView launchBtn = Theme.createPill(this, "BOOST & PLAY", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.BORDER_GRAPHITE);
            launchBtn.setOnClickListener(v ->
                    GamingHub.launchGameWithSession(this, g.packageName, g.label, hwMonitor));

            row.addView(gTitle);
            row.addView(launchBtn);
            card.addView(row);
        }

        return card;
    }

    private View buildNowBarAndClipboardCard() {
        LinearLayout card = Theme.createCard(this, true);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = Theme.createSectionLabel(this, "MODULES 01 & 09 // CAMERA-CUTOUT NOW BAR & SMART CLIPBOARD");
        LinearLayout.LayoutParams nbTitleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nbTitleLp.rightMargin = Theme.dp(this, 10);
        title.setLayoutParams(nbTitleLp);
        headerRow.addView(title);

        nowBarStatusPill = Theme.createPill(this, "ACTIVE", Theme.BG_ELEVATED, Theme.TELEMETRY_GREEN, Theme.PAPAYA);
        headerRow.addView(nowBarStatusPill);
        card.addView(headerRow);

        TextView desc = new TextView(this);
        desc.setText("Top camera punch-hole island wraps your lens with live wattage, media, downloads, F1 ticker, and French Press timer. OTPs are strictly routed exclusively to the Mac OTP Inbox.");
        desc.setTextColor(Theme.TEXT_SECONDARY);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        card.addView(desc);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = Theme.dp(this, 10);
        hsv.setLayoutParams(brLp);

        addActionPill(btnRow, "TOGGLE NOW BAR", true, () -> {
            if (NowBarOverlayService.isRunning) {
                stopNowBarService();
            } else {
                startNowBarService();
            }
            uiHandler.postDelayed(this::updateTelemetryUI, 200L);
        });

        addActionPill(btnRow, "☕ START 4:00 FRENCH PRESS", false, () -> {
            if (!NowBarOverlayService.isRunning) startNowBarService();
            NowBarState.startFrenchPressTimer();
            AayushDeckWidget.updateAllWidgets(this);
            Toast.makeText(this, "French Press Brew Timer started in Top Now Bar!", Toast.LENGTH_SHORT).show();
        });

        addActionPill(btnRow, "🔐 TEST MAC OTP INBOX (814920)", false, () ->
                MacCompanion.forwardOtpToMac(this, "814920", "Aayush OS Auth", "dev.aayush.os",
                        "Verification Code", (ok, msg) -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()));

        addActionPill(btnRow, "💻 CROSS-DEVICE COMMAND CENTRE", false, () -> selectTab(3));

        final View calibCard = NowBarCalibrationCard.build(this);
        calibCard.setVisibility(View.GONE);

        addActionPill(btnRow, "📐 NOW BAR CALIBRATION", false, () -> {
            boolean show = (calibCard.getVisibility() != View.VISIBLE);
            calibCard.setVisibility(show ? View.VISIBLE : View.GONE);
            Toast.makeText(this, show ? "Now Bar Calibration Studio opened below" : "Calibration Studio hidden", Toast.LENGTH_SHORT).show();
        });

        hsv.addView(btnRow);
        card.addView(hsv);

        card.addView(Theme.createDivider(this));

        LinearLayout clipTop = new LinearLayout(this);
        clipTop.setOrientation(LinearLayout.HORIZONTAL);
        clipTop.setGravity(Gravity.CENTER_VERTICAL);

        TextView clipLabel = Theme.createSectionLabel(this, "SMART CLIPBOARD CLASSIFIER");
        clipLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clipTop.addView(clipLabel);

        clipBadgeView = Theme.createPill(this, "EMPTY", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.BORDER_GRAPHITE);
        clipTop.addView(clipBadgeView);
        card.addView(clipTop);

        clipSummaryView = new TextView(this);
        clipSummaryView.setTypeface(Theme.MONO);
        clipSummaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        clipSummaryView.setTextColor(Theme.TEXT_PRIMARY);
        clipSummaryView.setText("Copy any JSON, JWT, Tracking URL, OTP, or Code to auto-classify.");
        card.addView(clipSummaryView);

        clipActionBtn = Theme.createPill(this, "PROCESS CLIPBOARD", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.PAPAYA);
        LinearLayout.LayoutParams caLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        caLp.topMargin = Theme.dp(this, 8);
        clipActionBtn.setLayoutParams(caLp);
        clipActionBtn.setOnClickListener(v -> {
            refreshClipboardAnalysis();
            if (!TextUtils.isEmpty(currentClip.transformedText)) {
                SmartClipboard.copyToClipboard(this, "AayushOS", currentClip.transformedText);
                Toast.makeText(this, "Copied: " + currentClip.badgeLabel, Toast.LENGTH_SHORT).show();
                refreshClipboardAnalysis();
            }
        });
        card.addView(clipActionBtn);

        HorizontalScrollView testScroll = new HorizontalScrollView(this);
        testScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout testRow = new LinearLayout(this);
        testRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.topMargin = Theme.dp(this, 8);
        testScroll.setLayoutParams(trLp);

        addActionPill(testRow, "Test Dirty URL", false, () -> {
            SmartClipboard.copyToClipboard(this, "TestURL",
                    "https://mclaren.com/racing/f1/driver/oscar-piastri?utm_source=instagram&utm_medium=social&igsh=MzRlODBiNWFlZA==");
            refreshClipboardAnalysis();
        });
        addActionPill(testRow, "Test Minified JSON", false, () -> {
            SmartClipboard.copyToClipboard(this, "TestJSON",
                    "{\"driver\":\"Oscar Piastri\",\"number\":81,\"team\":\"McLaren\",\"device\":\"Pixel 7 Panther\"}");
            refreshClipboardAnalysis();
        });
        addActionPill(testRow, "Test JWT Token", false, () -> {
            SmartClipboard.copyToClipboard(this, "TestJWT",
                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhYXl1c2giLCJkZXZpY2UiOiJQaXhlbCA3Iiwicm9sZSI6ImRldiIsImNhciI6ODF9.signature");
            refreshClipboardAnalysis();
        });

        testScroll.addView(testRow);
        card.addView(testScroll);
        card.addView(calibCard);

        return card;
    }

    private void addActionPill(LinearLayout row, String text, boolean papayaFill, Runnable onClick) {
        TextView btn = Theme.createPill(
                this,
                text,
                papayaFill ? Theme.PAPAYA : Theme.BG_ELEVATED,
                papayaFill ? Theme.BG_OBSIDIAN : Theme.TEXT_PRIMARY,
                papayaFill ? Theme.PAPAYA : Theme.BORDER_GRAPHITE
        );
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(this, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> onClick.run());
        row.addView(btn);
    }

    private View buildPowerTelemetryCard() {
        LinearLayout card = Theme.createCard(this, false);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = Theme.createSectionLabel(this, "MODULE 05 // LIVE BATTERY & CHARGING REGISTER");
        LinearLayout.LayoutParams pwLblLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pwLblLp.rightMargin = Theme.dp(this, 8);
        label.setLayoutParams(pwLblLp);
        topRow.addView(label);

        thermalBadge = Theme.createPill(this, "NOMINAL", Theme.BG_ELEVATED, Theme.TELEMETRY_GREEN, Theme.BORDER_GRAPHITE);
        topRow.addView(thermalBadge);
        card.addView(topRow);

        wattsBigText = new TextView(this);
        wattsBigText.setText("--.- W");
        wattsBigText.setTextColor(Theme.TEXT_PRIMARY);
        wattsBigText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        wattsBigText.setTypeface(Theme.MONO_BOLD);
        card.addView(wattsBigText);

        batteryMetaText = new TextView(this);
        batteryMetaText.setText("Sampling BatteryManager registers...");
        batteryMetaText.setTextColor(Theme.TEXT_SECONDARY);
        batteryMetaText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        batteryMetaText.setTypeface(Theme.MONO);
        card.addView(batteryMetaText);

        batteryProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        batteryProgress.setMax(100);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 8));
        pbLp.topMargin = Theme.dp(this, 10);
        batteryProgress.setLayoutParams(pbLp);
        card.addView(batteryProgress);

        return card;
    }

    private View buildTensorCpuCard() {
        LinearLayout card = Theme.createCard(this, false);
        card.addView(Theme.createSectionLabel(this, "TENSOR G2 // 8-CORE CLUSTER CLOCKS (4×A510 • 2×A78 • 2×X1)"));

        cpuClusterText = new TextView(this);
        cpuClusterText.setTypeface(Theme.MONO);
        cpuClusterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cpuClusterText.setTextColor(Theme.TEXT_PRIMARY);
        cpuClusterText.setText("Reading /sys/devices/system/cpu/cpufreq...");
        card.addView(cpuClusterText);

        cpuLoadBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        cpuLoadBar.setMax(100);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 6));
        lp.topMargin = Theme.dp(this, 10);
        cpuLoadBar.setLayoutParams(lp);
        card.addView(cpuLoadBar);

        return card;
    }

    private View buildMemoryAndNetworkCard() {
        LinearLayout card = Theme.createCard(this, false);
        card.addView(Theme.createSectionLabel(this, "MEMORY (/PROC/MEMINFO) • STORAGE (/DATA) • LIVE TRAFFIC"));

        ramStorageText = new TextView(this);
        ramStorageText.setTypeface(Theme.MONO);
        ramStorageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ramStorageText.setTextColor(Theme.TEXT_PRIMARY);
        card.addView(ramStorageText);

        ramLoadBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        ramLoadBar.setMax(100);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 6));
        lp.topMargin = Theme.dp(this, 8);
        lp.bottomMargin = Theme.dp(this, 8);
        ramLoadBar.setLayoutParams(lp);
        card.addView(ramLoadBar);

        netSpeedText = new TextView(this);
        netSpeedText.setTypeface(Theme.MONO_BOLD);
        netSpeedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        netSpeedText.setTextColor(Theme.PAPAYA);
        card.addView(netSpeedText);

        return card;
    }

    private View buildPrivilegeAndPrivacyCard() {
        LinearLayout card = Theme.createCard(this, false);
        card.addView(Theme.createSectionLabel(this, "PRIVILEGE MATRIX & MODULE PRIVACY SWITCHBOARD"));

        boolean hasWriteSecure = checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
        boolean hasReadLogs = checkCallingOrSelfPermission("android.permission.READ_LOGS")
                == PackageManager.PERMISSION_GRANTED;
        boolean hasOverlay = Settings.canDrawOverlays(this);
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean hasNotifListener = enabledListeners != null && enabledListeners.contains(getPackageName());

        TextView permStatus = new TextView(this);
        permStatus.setTypeface(Theme.MONO);
        permStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        permStatus.setTextColor(Theme.TEXT_SECONDARY);
        permStatus.setText(String.format(Locale.US,
                "[%s] ADB WRITE_SECURE_SETTINGS (Automation & System UI)\n" +
                "[%s] ADB SYSTEM_ALERT_WINDOW   (Camera Cutout Now Bar)\n" +
                "[%s] ADB NOTIFICATION_LISTENER (Media, OTP & Downloads)\n" +
                "[%s] ADB READ_LOGS             (Live Kernel Logcat Vault)",
                hasWriteSecure ? "GRANTED" : "MISSING",
                hasOverlay ? "GRANTED" : "MISSING",
                hasNotifListener ? "GRANTED" : "MISSING",
                hasReadLogs ? "GRANTED" : "MISSING"
        ));
        card.addView(permStatus);

        card.addView(Theme.createDivider(this));

        final SharedPreferences prefs = getSharedPreferences("aayush_os_prefs", Context.MODE_PRIVATE);
        card.addView(buildModuleSwitchRow(prefs, "mod_telemetry", "Hardware & Thermal Telemetry", true));
        card.addView(buildModuleSwitchRow(prefs, "mod_nowbar", "Top Camera-Cutout Dynamic Now Bar", true));
        card.addView(buildModuleSwitchRow(prefs, "mod_mac_sync", "MacBook Air M4 LAN Companion (192.168.0.110)", true));
        card.addView(buildModuleSwitchRow(prefs, "mod_smart_clip", "Smart Clipboard & OTP Classifier", true));

        TextView openVaultBtn = Theme.createPill(
                this,
                "OPEN PANTHER #81 ENGINEERING VAULT (OR TRIPLE-TAP #81)",
                Theme.BG_ELEVATED,
                Theme.PAPAYA,
                Theme.PAPAYA
        );
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = Theme.dp(this, 12);
        openVaultBtn.setLayoutParams(btnLp);
        openVaultBtn.setOnClickListener(v -> VaultDialog.show(this));
        card.addView(openVaultBtn);

        return card;
    }

    private View buildMasterKillSwitchCard() {
        LinearLayout card = Theme.createCard(this, false);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = Theme.createSectionLabel(this, "🚨 EMERGENCY KILL SWITCH // ZERO-TRUST ISOLATION");
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.rightMargin = Theme.dp(this, 10);
        title.setLayoutParams(tLp);
        headerRow.addView(title);

        final boolean isDisc = MacCompanion.isManuallyDisconnected(this);
        final TextView statusPill = Theme.createPill(
                this,
                isDisc ? "ISOLATED" : "CONNECTED",
                Theme.BG_ELEVATED,
                isDisc ? Theme.TELEMETRY_RED : Theme.TELEMETRY_GREEN,
                isDisc ? Theme.TELEMETRY_RED : Theme.BORDER_GRAPHITE
        );
        headerRow.addView(statusPill);
        card.addView(headerRow);

        TextView desc = new TextView(this);
        desc.setText("1-Tap Emergency Cutoff: Immediately halts background companion receiver (:8183), closes active sockets, forces MacBook M4 offline (kills Tailscale & tunnels), and isolates the device.");
        desc.setTextColor(Theme.TEXT_SECONDARY);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = Theme.dp(this, 4);
        dLp.bottomMargin = Theme.dp(this, 10);
        desc.setLayoutParams(dLp);
        card.addView(desc);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView killBtn = Theme.createPill(
                this,
                "🔴 ISOLATE & KILL ALL CONNECTIONS",
                Theme.BG_ELEVATED,
                Theme.TELEMETRY_RED,
                Theme.TELEMETRY_RED
        );
        LinearLayout.LayoutParams kLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        kLp.rightMargin = Theme.dp(this, 6);
        killBtn.setLayoutParams(kLp);
        killBtn.setOnClickListener(v -> {
            MacCompanion.setManuallyDisconnected(this, true);
            PixelCompanionServer.stopServer();
            MacCompanion.sendMacCommand(this, "DISCONNECT_ALL", (ok, msg) -> {});
            statusPill.setText("ISOLATED");
            statusPill.setTextColor(Theme.TELEMETRY_RED);
            Toast.makeText(this, "🔒 Emergency Kill Switch Engaged: All connections terminated.", Toast.LENGTH_LONG).show();
            updateTelemetryUI();
            refreshMacCompanionStatus();
        });
        btnRow.addView(killBtn);

        TextView restoreBtn = Theme.createPill(
                this,
                "🟢 RESTORE / RECONNECT",
                Theme.BG_ELEVATED,
                Theme.TELEMETRY_GREEN,
                Theme.BORDER_GRAPHITE
        );
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rLp.leftMargin = Theme.dp(this, 6);
        restoreBtn.setLayoutParams(rLp);
        restoreBtn.setOnClickListener(v -> {
            MacCompanion.setManuallyDisconnected(this, false);
            PixelCompanionServer.ensureStarted(this);
            statusPill.setText("CONNECTING...");
            statusPill.setTextColor(Theme.PAPAYA);
            MacCompanion.discoverMacOnLan(this, (ok, msg) -> {
                uiHandler.post(() -> {
                    statusPill.setText(ok ? "CONNECTED" : "LAN SEARCH");
                    statusPill.setTextColor(ok ? Theme.TELEMETRY_GREEN : Theme.PAPAYA);
                    Toast.makeText(this, "Restoring connections: " + msg, Toast.LENGTH_SHORT).show();
                    refreshMacCompanionStatus();
                });
            });
        });
        btnRow.addView(restoreBtn);

        card.addView(btnRow);
        return card;
    }

    private View buildModuleSwitchRow(final SharedPreferences prefs, final String key, String label, boolean defVal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(this, 4), 0, Theme.dp(this, 4));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Theme.TEXT_PRIMARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Theme.SANS_MEDIUM);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, defVal));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).apply();
            if ("mod_nowbar".equals(key)) {
                if (isChecked) startNowBarService();
                else stopNowBarService();
            }
        });

        row.addView(tv);
        row.addView(sw);
        return row;
    }

    private void refreshClipboardAnalysis() {
        currentClip = SmartClipboard.inspectClipboard(this);
        if (clipBadgeView != null) {
            clipBadgeView.setText(currentClip.badgeLabel);
            clipSummaryView.setText(currentClip.summary);
            clipActionBtn.setText(!TextUtils.isEmpty(currentClip.primaryActionLabel)
                    ? currentClip.primaryActionLabel : "REFRESH CLIPBOARD");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            refreshClipboardAnalysis();
            if (MacCompanion.isAutoClipboardSyncEnabled(this)
                    && currentClip != null
                    && !TextUtils.isEmpty(currentClip.rawText)
                    && !currentClip.isSensitive) {
                MacCompanion.pushClipboardToMacInternal(this, currentClip.rawText, true, false, null);
            }
        }
    }

    private void updateTelemetryUI() {
        HardwareMonitor.Snapshot s = hwMonitor.sample();
        if (crossDeviceDeck != null) {
            crossDeviceDeck.updatePixelLiveTelemetry(this, s);
        }

        if (nowBarStatusPill != null) {
            boolean nb = NowBarOverlayService.isRunning;
            nowBarStatusPill.setText(nb ? "CAMERA ISLAND ON" : "STOPPED");
            nowBarStatusPill.setTextColor(nb ? Theme.TELEMETRY_GREEN : Theme.TEXT_SECONDARY);
        }

        if (gamingSessionSummary != null && activeTab == 1) {
            gamingSessionSummary.setText(GamingHub.endOrFormatSessionSummary(this, hwMonitor, false));
        }

        if (frenchPressMainText != null) {
            int remSec = NowBarState.getTimerRemainingSec();
            if (NowBarState.timerRunning && remSec > 0) {
                frenchPressMainText.setText(String.format(Locale.US,
                        "☕ %s — %02d:%02d REMAINING",
                        NowBarState.getFrenchPressStage(), remSec / 60, remSec % 60));
            } else {
                frenchPressMainText.setText("☕ FRENCH PRESS: READY (0:30 BLOOM → 3:30 STEEP → 4:00 PLUNGE)");
            }
        }

        String flowPrefix = s.isCharging ? "⚡ CHARGING " : "DISCHARGING ";
        wattsBigText.setText(String.format(Locale.US, "%s %.2f W  (%d%%)",
                flowPrefix, s.powerWatts, s.batteryPct));
        wattsBigText.setTextColor(s.isCharging ? Theme.TELEMETRY_GREEN : Theme.TEXT_PRIMARY);

        batteryMetaText.setText(String.format(Locale.US,
                "SRC: %-5s | I: %+.0f mA | V: %d mV | TEMP: %.1f°C | CAP: %d mAh",
                s.chargePlugType, s.currentMa, s.voltageMv, s.batteryTempC, s.chargeCounterMah
        ));
        batteryProgress.setProgress(s.batteryPct);

        thermalBadge.setText(String.format(Locale.US, "%s • %.1f°C", s.thermalState, s.batteryTempC));
        int tColor = (s.batteryTempC >= 40.0 || s.thermalStatusLevel >= 2)
                ? Theme.TELEMETRY_RED
                : (s.batteryTempC >= 37.0 ? Theme.TELEMETRY_AMBER : Theme.TELEMETRY_GREEN);
        thermalBadge.setTextColor(tColor);

        cpuClusterText.setText(String.format(Locale.US,
                "LITTLE (A510 cpu0-3): %4d / %4d / %4d / %4d MHz\n" +
                "MID    (A78  cpu4-5): %4d / %4d MHz\n" +
                "PRIME  (X1   cpu6-7): %4d / %4d MHz  [Scaling: %d%%]",
                s.coreCurMhz[0], s.coreCurMhz[1], s.coreCurMhz[2], s.coreCurMhz[3],
                s.coreCurMhz[4], s.coreCurMhz[5],
                s.coreCurMhz[6], s.coreCurMhz[7],
                s.avgCpuLoadPct
        ));
        cpuLoadBar.setProgress(s.avgCpuLoadPct);

        ramStorageText.setText(String.format(Locale.US,
                "LPDDR5 RAM : %.2f / %.2f GB (%d%%)  |  ZRAM: %.0f / %.0f MB\n" +
                "UFS 3.1 /data: %.1f / %.1f GB (%d%%)  |  UPTIME: %d min",
                s.ramUsedGb, s.ramTotalGb, s.ramUsedPct, s.zramUsedMb, s.zramTotalMb,
                s.storageUsedGb, s.storageTotalGb, s.storageUsedPct, s.uptimeMinutes
        ));
        ramLoadBar.setProgress(s.ramUsedPct);

        netSpeedText.setText(String.format(Locale.US,
                "NET THROUGHPUT  ↓ %s   ↑ %s",
                HardwareMonitor.formatSpeed(s.rxKbps),
                HardwareMonitor.formatSpeed(s.txKbps)
        ));
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        uiHandler.removeCallbacks(telemetryTick);
        uiHandler.post(telemetryTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        uiHandler.removeCallbacks(telemetryTick);
    }
}
