package dev.aayush.os;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Aayush OS V3 — Cross-Device Intelligence Deck Builder (Phases 1 through 9 & 11).
 * Renders the Cross-Device Command Centre, Mac System Control, scrcpy Mirroring & Webcam,
 * Mac OTP Inbox, Advanced Clipboard, Bidirectional File Transfer, and Mac Notification Centre.
 */
public final class CrossDeviceDeckView {

    public static final int REQ_PICK_FILE_FOR_MAC = 8107;

    // UI References updated on live refresh
    private TextView crossStatusBadge;
    private TextView pixelPanelText;
    private TextView macPanelText;
    private TextView quickMirrorBtn;
    private TextView quickMacWifiBtn;
    private TextView quickMacBtBtn;
    private TextView quickMacCaffBtn;

    private TextView macConnPill;
    private TextView macSystemStateText;
    private TextView macDiagOutputText;
    private TextView wirelessAuditText;
    private EditText macHostEditInput;
    private EditText macTailscaleEditInput;

    private TextView scrcpyStatusText;
    private TextView webcamStatusText;
    private int selectedScrcpyRes = 1080;
    private String selectedScrcpyBitrate = "8M";
    private String selectedScrcpyOrient = "portrait";

    private TextView otpInboxStatusText;
    private TextView clipHistoryText;

    private String selectedMacDestFolder = "PixelDrop";
    private ProgressBar transferProgressBar;
    private TextView transferStatusText;
    private LinearLayout macOutboxListContainer;
    private TextView transferHistoryText;

    private LinearLayout macNotifListContainer;

    public View buildAll(final MainActivity act) {
        LinearLayout container = new LinearLayout(act);
        container.setOrientation(LinearLayout.VERTICAL);

        container.addView(buildCrossDeviceCommandCentreCard(act));
        container.addView(buildMacSystemControlCard(act));
        container.addView(buildPixelRemoteAndWebcamCard(act));
        container.addView(buildMacOtpInboxCard(act));
        container.addView(buildClipboardAndFileTransferCard(act));
        container.addView(buildMacNotificationCentreCard(act));

        return container;
    }

    // =========================================================================
    // PHASE 9: CROSS-DEVICE COMMAND CENTRE (DUAL DEVICE PANELS + QUICK ACTIONS)
    // =========================================================================

    private View buildCrossDeviceCommandCentreCard(final MainActivity act) {
        LinearLayout card = Theme.createCard(act, true);

        LinearLayout headerRow = new LinearLayout(act);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = Theme.createSectionLabel(act, "PHASE 09 // CROSS-DEVICE COMMAND CENTRE (PIXEL 7 × MAC M4)");
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.rightMargin = Theme.dp(act, 8);
        title.setLayoutParams(tLp);
        headerRow.addView(title);

        crossStatusBadge = Theme.createPill(act, "SYNCING...", Theme.BG_ELEVATED, Theme.PAPAYA, Theme.PAPAYA);
        headerRow.addView(crossStatusBadge);
        card.addView(headerRow);

        // Two-Column Side-by-Side Device Telemetry Panels
        LinearLayout dualRow = new LinearLayout(act);
        dualRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams drLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        drLp.topMargin = Theme.dp(act, 6);
        drLp.bottomMargin = Theme.dp(act, 10);
        dualRow.setLayoutParams(drLp);

        int panelPad = Theme.dp(act, 10);

        // Left Panel: PIXEL 7 ('panther')
        LinearLayout pixelPanel = new LinearLayout(act);
        pixelPanel.setOrientation(LinearLayout.VERTICAL);
        pixelPanel.setPadding(panelPad, panelPad, panelPad, panelPad);
        pixelPanel.setBackground(Theme.cardDrawable(act, Theme.BG_ELEVATED, Theme.BORDER_GRAPHITE, 12f, 1f));
        LinearLayout.LayoutParams ppLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ppLp.rightMargin = Theme.dp(act, 6);
        pixelPanel.setLayoutParams(ppLp);

        TextView pxHeader = new TextView(act);
        pxHeader.setText("📱 PIXEL 7 PANTHER");
        pxHeader.setTypeface(Theme.MONO_BOLD);
        pxHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        pxHeader.setTextColor(Theme.PAPAYA);
        pixelPanel.addView(pxHeader);

        pixelPanelText = new TextView(act);
        pixelPanelText.setTypeface(Theme.MONO);
        pixelPanelText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        pixelPanelText.setTextColor(Theme.TEXT_PRIMARY);
        pixelPanelText.setLineSpacing(Theme.dp(act, 2), 1.0f);
        pixelPanelText.setText("Sampling Pixel 7 registers...");
        pixelPanel.addView(pixelPanelText);

        // Right Panel: MACBOOK AIR M4
        LinearLayout macPanel = new LinearLayout(act);
        macPanel.setOrientation(LinearLayout.VERTICAL);
        macPanel.setPadding(panelPad, panelPad, panelPad, panelPad);
        macPanel.setBackground(Theme.cardDrawable(act, Theme.BG_ELEVATED, Theme.BORDER_GRAPHITE, 12f, 1f));
        LinearLayout.LayoutParams mpLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mpLp.leftMargin = Theme.dp(act, 6);
        macPanel.setLayoutParams(mpLp);

        TextView mcHeader = new TextView(act);
        mcHeader.setText("💻 MACBOOK AIR M4");
        mcHeader.setTypeface(Theme.MONO_BOLD);
        mcHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mcHeader.setTextColor(Theme.TELEMETRY_GREEN);
        macPanel.addView(mcHeader);

        macPanelText = new TextView(act);
        macPanelText.setTypeface(Theme.MONO);
        macPanelText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        macPanelText.setTextColor(Theme.TEXT_PRIMARY);
        macPanelText.setLineSpacing(Theme.dp(act, 2), 1.0f);
        macPanelText.setText("Probing Mac M4 bridge...");
        macPanel.addView(macPanelText);

        dualRow.addView(pixelPanel);
        dualRow.addView(macPanel);
        card.addView(dualRow);

        card.addView(Theme.createSectionLabel(act, "10 LIVE CROSS-DEVICE QUICK ACTIONS"));

        // Quick Actions Row 1
        HorizontalScrollView qaScroll1 = new HorizontalScrollView(act);
        qaScroll1.setHorizontalScrollBarEnabled(false);
        LinearLayout qaRow1 = new LinearLayout(act);
        qaRow1.setOrientation(LinearLayout.HORIZONTAL);

        quickMirrorBtn = addPill(act, qaRow1, "🖥️ MIRROR PIXEL", true, () -> {
            boolean running = MacCompanion.lastKnownStatus.scrcpyRunning;
            MacCompanion.controlScrcpy(act, running ? "STOP" : "START",
                    selectedScrcpyRes, selectedScrcpyBitrate, selectedScrcpyOrient, (ok, msg) -> {
                        Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
                        refreshAll(act);
                    });
        });

        quickMacWifiBtn = addPill(act, qaRow1, "📶 MAC WI-FI: ON", false, () -> {
            boolean curOn = MacCompanion.lastKnownStatus.wifiPower;
            String cmd = curOn ? "WIFI_OFF" : "WIFI_ON";
            MacCompanion.sendMacCommand(act, cmd, (ok, msg) -> {
                Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
                refreshAll(act);
            });
        });

        quickMacBtBtn = addPill(act, qaRow1, "🦷 MAC BT: ON", false, () -> {
            boolean curOn = MacCompanion.lastKnownStatus.btPower;
            String cmd = curOn ? "BT_OFF" : "BT_ON";
            MacCompanion.sendMacCommand(act, cmd, (ok, msg) -> {
                Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
                refreshAll(act);
            });
        });

        addPill(act, qaRow1, "📤 PUSH CLIPBOARD", false, () -> {
            SmartClipboard.Analysis a = SmartClipboard.inspectClipboard(act);
            MacCompanion.pushClipboardToMac(act, a.rawText, (ok, msg) -> {
                Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
                refreshAll(act);
            });
        });

        addPill(act, qaRow1, "📥 PULL CLIPBOARD", false, () ->
                MacCompanion.pullClipboardFromMac(act, (ok, msg) -> {
                    Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));

        qaScroll1.addView(qaRow1);
        card.addView(qaScroll1);

        // Quick Actions Row 2
        HorizontalScrollView qaScroll2 = new HorizontalScrollView(act);
        qaScroll2.setHorizontalScrollBarEnabled(false);
        LinearLayout qaRow2 = new LinearLayout(act);
        qaRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams qr2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qr2Lp.topMargin = Theme.dp(act, 8);
        qaScroll2.setLayoutParams(qr2Lp);

        addPill(act, qaRow2, "📤 SEND FILE", true, () -> launchFilePicker(act));

        addPill(act, qaRow2, "📥 RECEIVE FILE", false, () ->
                MacCompanion.fetchMacOutboxAndHistory(act, (files, history) -> {
                    if (files.isEmpty()) {
                        Toast.makeText(act, "Mac Outbox is empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String fname = files.get(0).name;
                    triggerMacFileDownload(act, fname);
                }));

        addPill(act, qaRow2, "🔒 LOCK MAC", false, () ->
                MacCompanion.sendMacCommand(act, "LOCK_SCREEN", (ok, msg) ->
                        Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()));

        quickMacCaffBtn = addPill(act, qaRow2, "☕ CAFFEINATE MAC", false, () ->
                MacCompanion.sendMacCommand(act, "CAFFEINATE_TOGGLE", (ok, msg) -> {
                    Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));

        addPill(act, qaRow2, "🔐 MAC OTP INBOX", false, () -> {
            MacCompanion.sendMacCommand(act, "OPEN_DASHBOARD", (ok, msg) ->
                    Toast.makeText(act, "Opened Mac OTP Inbox on MacBook Air M4", Toast.LENGTH_SHORT).show());
            refreshAll(act);
        });

        qaScroll2.addView(qaRow2);
        card.addView(qaScroll2);

        return card;
    }

    // =========================================================================
    // PHASE 1, 2 & 11: MAC SYSTEM CONTROL & TRUE WIRELESS ECOSYSTEM
    // =========================================================================

    private View buildMacSystemControlCard(final MainActivity act) {
        LinearLayout card = Theme.createCard(act, false);

        LinearLayout topRow = new LinearLayout(act);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = Theme.createSectionLabel(act, "PHASE 01 // MACBOOK AIR M4 SYSTEM CONTROL");
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.rightMargin = Theme.dp(act, 8);
        title.setLayoutParams(tLp);
        topRow.addView(title);

        macConnPill = Theme.createPill(act, "PROBING...", Theme.BG_ELEVATED, Theme.TELEMETRY_AMBER, Theme.PAPAYA);
        topRow.addView(macConnPill);
        card.addView(topRow);

        macSystemStateText = new TextView(act);
        macSystemStateText.setTypeface(Theme.MONO);
        macSystemStateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        macSystemStateText.setTextColor(Theme.TEXT_PRIMARY);
        macSystemStateText.setLineSpacing(Theme.dp(act, 2), 1.0f);
        int pad = Theme.dp(act, 10);
        macSystemStateText.setPadding(pad, pad, pad, pad);
        macSystemStateText.setBackground(Theme.cardDrawable(act, 0xFF090909, Theme.BORDER_GRAPHITE, 10f, 1f));
        macSystemStateText.setText("Querying MacBook Air M4 hardware registers...");
        card.addView(macSystemStateText);

        // Row 1: Radio Controls (Wi-Fi ON/OFF & Bluetooth ON/OFF — Strict Disconnect Policy)
        TextView radioNote = new TextView(act);
        radioNote.setText("Strict Radio Policy: Turning OFF Mac Wi-Fi or Bluetooth executes immediately and stays OFF permanently until manually re-enabled. Pixel radios are never affected.");
        radioNote.setTextColor(Theme.TEXT_SECONDARY);
        radioNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        LinearLayout.LayoutParams rnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rnLp.topMargin = Theme.dp(act, 8);
        rnLp.bottomMargin = Theme.dp(act, 6);
        radioNote.setLayoutParams(rnLp);
        card.addView(radioNote);

        HorizontalScrollView sysScroll1 = new HorizontalScrollView(act);
        sysScroll1.setHorizontalScrollBarEnabled(false);
        LinearLayout sysRow1 = new LinearLayout(act);
        sysRow1.setOrientation(LinearLayout.HORIZONTAL);

        addPill(act, sysRow1, "📶 MAC WI-FI ON", true, () ->
                MacCompanion.sendMacCommand(act, "WIFI_ON", (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));
        addPill(act, sysRow1, "📴 MAC WI-FI OFF (IMMEDIATE)", false, () ->
                MacCompanion.sendMacCommand(act, "WIFI_OFF", (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_LONG).show();
                    refreshAll(act);
                }));
        addPill(act, sysRow1, "🦷 MAC BLUETOOTH ON", true, () ->
                MacCompanion.sendMacCommand(act, "BT_ON", (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));
        addPill(act, sysRow1, "📴 MAC BLUETOOTH OFF", false, () ->
                MacCompanion.sendMacCommand(act, "BT_OFF", (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));
        sysScroll1.addView(sysRow1);
        card.addView(sysScroll1);

        // Row 2: Power, Audio, Appearance & Diagnostics
        HorizontalScrollView sysScroll2 = new HorizontalScrollView(act);
        sysScroll2.setHorizontalScrollBarEnabled(false);
        LinearLayout sysRow2 = new LinearLayout(act);
        sysRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sr2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sr2Lp.topMargin = Theme.dp(act, 8);
        sysScroll2.setLayoutParams(sr2Lp);

        addPill(act, sysRow2, "🔒 LOCK MAC SCREEN", false, () ->
                MacCompanion.sendMacCommand(act, "LOCK_SCREEN", (ok, m) ->
                        Toast.makeText(act, m, Toast.LENGTH_SHORT).show()));
        addPill(act, sysRow2, "☕ CAFFEINATE TOGGLE", false, () ->
                MacCompanion.sendMacCommand(act, "CAFFEINATE_TOGGLE", (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));
        addPill(act, sysRow2, "🔇 MUTE / UNMUTE AUDIO", false, () ->
                MacCompanion.sendMacCommand(act, "AUDIO_MUTE_TOGGLE", (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));
        addPill(act, sysRow2, "🌗 DARK MODE TOGGLE", false, () ->
                MacCompanion.sendMacCommand(act, "DARK_MODE_TOGGLE", (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));
        addPill(act, sysRow2, "🔔 PLAY MAC SOUND", false, () ->
                MacCompanion.sendMacCommand(act, "PLAY_SOUND", (ok, m) ->
                        Toast.makeText(act, m, Toast.LENGTH_SHORT).show()));
        addPill(act, sysRow2, "🌐 MAC NET DIAGNOSTICS", true, () -> {
            macDiagOutputText.setVisibility(View.VISIBLE);
            macDiagOutputText.setText("Running Mac network diagnostics...");
            MacCompanion.sendMacCommand(act, "NET_DIAGNOSTICS", (ok, m) -> macDiagOutputText.setText(m));
        });

        sysScroll2.addView(sysRow2);
        card.addView(sysScroll2);

        macDiagOutputText = new TextView(act);
        macDiagOutputText.setTypeface(Theme.MONO);
        macDiagOutputText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        macDiagOutputText.setTextColor(Theme.TELEMETRY_GREEN);
        macDiagOutputText.setPadding(pad, pad, pad, pad);
        macDiagOutputText.setBackground(Theme.cardDrawable(act, 0xFF080808, Theme.BORDER_GRAPHITE, 8f, 1f));
        macDiagOutputText.setVisibility(View.GONE);
        LinearLayout.LayoutParams mdLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mdLp.topMargin = Theme.dp(act, 8);
        macDiagOutputText.setLayoutParams(mdLp);
        card.addView(macDiagOutputText);

        card.addView(Theme.createDivider(act));
        card.addView(Theme.createSectionLabel(act, "PHASE 02 & V3.1 // NETWORK INDEPENDENCE, TAILSCALE VPN & PAIRING"));

        wirelessAuditText = new TextView(act);
        wirelessAuditText.setTypeface(Theme.MONO);
        wirelessAuditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        wirelessAuditText.setTextColor(Theme.TEXT_SECONDARY);
        card.addView(wirelessAuditText);

        final EditText ipInput = createInput(act, "Mac Active Host / mDNS (e.g. Aayushs-MacBook-Air.local)", MacCompanion.getMacHost(act));
        final EditText tsInput = createInput(act, "Mac Tailscale VPN Fallback IP (100.x.y.z — Optional)", MacCompanion.getMacTailscaleHost(act));
        final EditText tokenInput = createInput(act, "Shared Auth Token (X-Aayush-Token + ENC_V31 Key)", MacCompanion.getAuthToken(act));
        macHostEditInput = ipInput;
        macTailscaleEditInput = tsInput;
        card.addView(ipInput);
        card.addView(tsInput);
        card.addView(tokenInput);

        HorizontalScrollView pairScroll = new HorizontalScrollView(act);
        pairScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout pairRow = new LinearLayout(act);
        pairRow.setOrientation(LinearLayout.HORIZONTAL);

        addPill(act, pairRow, "📡 DYNAMIC DISCOVER (UDP + mDNS + VPN)", true, () -> {
            MacCompanion.setMacHost(act, ipInput.getText().toString());
            MacCompanion.setMacTailscaleHost(act, tsInput.getText().toString());
            MacCompanion.setAuthToken(act, tokenInput.getText().toString());
            MacCompanion.discoverMacOnLan(act, (ok, msg) -> {
                ipInput.setText(MacCompanion.getMacHost(act));
                tsInput.setText(MacCompanion.getMacTailscaleHost(act));
                Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
                refreshAll(act);
            });
        });

        addPill(act, pairRow, "🛡️ PATH & ISOLATION AUDIT", true, () -> {
            macDiagOutputText.setVisibility(View.VISIBLE);
            macDiagOutputText.setText("Running V3.1 Cross-Network Path & Client Isolation Audit...");
            MacCompanion.runNetworkPathAudit(act, (ok, rep) -> macDiagOutputText.setText(rep));
        });

        addPill(act, pairRow, "🟣 LAUNCH TAILSCALE VPN", false, () -> {
            MacCompanion.sendMacCommand(act, "OPEN_TAILSCALE", (ok, m) -> {});
            try {
                Intent tsIntent = act.getPackageManager().getLaunchIntentForPackage("com.tailscale.ipn");
                if (tsIntent != null) {
                    act.startActivity(tsIntent);
                    Toast.makeText(act, "Launching Tailscale VPN on Pixel 7 & Mac M4", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(act, "Launched Tailscale.app on Mac M4 (Install Tailscale on Pixel for VPN fallback)", Toast.LENGTH_LONG).show();
                }
            } catch (Exception ignored) {}
        });

        addPill(act, pairRow, "💾 SAVE & RECONNECT", false, () -> {
            MacCompanion.setMacHost(act, ipInput.getText().toString());
            MacCompanion.setMacTailscaleHost(act, tsInput.getText().toString());
            MacCompanion.setAuthToken(act, tokenInput.getText().toString());
            MacCompanion.setManuallyDisconnected(act, false);
            refreshAll(act);
            Toast.makeText(act, "Saved V3.1 network config & reconnected", Toast.LENGTH_SHORT).show();
        });

        addPill(act, pairRow, "🔌 EXPLICIT DISCONNECT", false, () -> {
            boolean disc = !MacCompanion.isManuallyDisconnected(act);
            MacCompanion.setManuallyDisconnected(act, disc);
            Toast.makeText(act, disc
                    ? "Disconnected from Mac Bridge (zero background traffic)"
                    : "Reconnected to Mac Bridge", Toast.LENGTH_SHORT).show();
            refreshAll(act);
        });

        addPill(act, pairRow, "🛠️ TOGGLE ADB DEBUGGING", false, () -> {
            try {
                int cur = Settings.Global.getInt(act.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
                int next = (cur == 0) ? 1 : 0;
                Settings.Global.putInt(act.getContentResolver(), Settings.Global.ADB_ENABLED, next);
                Toast.makeText(act, next == 1
                        ? "USB/ADB Debugging ENABLED (Maintenance/scrcpy mode)"
                        : "USB/ADB Debugging DISABLED (Daily Wireless mode)", Toast.LENGTH_SHORT).show();
                refreshAll(act);
            } catch (Exception e) {
                Toast.makeText(act, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        pairScroll.addView(pairRow);
        card.addView(pairScroll);

        return card;
    }

    // =========================================================================
    // PHASE 3 & 4: CONTROL PIXEL FROM MAC (SCRCPY) & PIXEL CAMERA WEBCAM
    // =========================================================================

    private View buildPixelRemoteAndWebcamCard(final MainActivity act) {
        LinearLayout card = Theme.createCard(act, false);
        card.addView(Theme.createSectionLabel(act, "PHASE 03 // CONTROL PIXEL FROM MAC (REAL SCRCPY 4.1 ENGINE)"));

        scrcpyStatusText = new TextView(act);
        scrcpyStatusText.setTypeface(Theme.MONO);
        scrcpyStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        scrcpyStatusText.setTextColor(Theme.TEXT_PRIMARY);
        scrcpyStatusText.setText("Checking scrcpy session on MacBook Air M4...");
        card.addView(scrcpyStatusText);

        // Resolution, Bitrate & Orientation Presets
        HorizontalScrollView presetScroll = new HorizontalScrollView(act);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(act);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prLp.topMargin = Theme.dp(act, 8);
        presetScroll.setLayoutParams(prLp);

        addPill(act, presetRow, "RES: 1080P", false, () -> {
            selectedScrcpyRes = 1080;
            Toast.makeText(act, "scrcpy max-size: 1080p", Toast.LENGTH_SHORT).show();
        });
        addPill(act, presetRow, "RES: 1440P", false, () -> {
            selectedScrcpyRes = 1440;
            Toast.makeText(act, "scrcpy max-size: 1440p", Toast.LENGTH_SHORT).show();
        });
        addPill(act, presetRow, "RES: 720P", false, () -> {
            selectedScrcpyRes = 720;
            Toast.makeText(act, "scrcpy max-size: 720p", Toast.LENGTH_SHORT).show();
        });
        addPill(act, presetRow, "BITRATE: 8M", false, () -> {
            selectedScrcpyBitrate = "8M";
            Toast.makeText(act, "scrcpy bitrate: 8 Mbps", Toast.LENGTH_SHORT).show();
        });
        addPill(act, presetRow, "BITRATE: 16M", false, () -> {
            selectedScrcpyBitrate = "16M";
            Toast.makeText(act, "scrcpy bitrate: 16 Mbps", Toast.LENGTH_SHORT).show();
        });
        addPill(act, presetRow, "PORTRAIT / LANDSCAPE", false, () -> {
            selectedScrcpyOrient = "portrait".equals(selectedScrcpyOrient) ? "landscape" : "portrait";
            Toast.makeText(act, "Orientation: " + selectedScrcpyOrient.toUpperCase(Locale.US), Toast.LENGTH_SHORT).show();
        });
        presetScroll.addView(presetRow);
        card.addView(presetScroll);

        // Start / Stop / Wireless Bootstrap / Reconnect / Disconnect
        HorizontalScrollView ctrlScroll = new HorizontalScrollView(act);
        ctrlScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout ctrlRow = new LinearLayout(act);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crLp.topMargin = Theme.dp(act, 8);
        ctrlScroll.setLayoutParams(crLp);

        addPill(act, ctrlRow, "▶ START MIRRORING", true, () ->
                MacCompanion.controlScrcpy(act, "START", selectedScrcpyRes, selectedScrcpyBitrate, selectedScrcpyOrient,
                        (ok, m) -> {
                            Toast.makeText(act, m, Toast.LENGTH_LONG).show();
                            refreshAll(act);
                        }));
        addPill(act, ctrlRow, "⏹ STOP MIRRORING", false, () ->
                MacCompanion.controlScrcpy(act, "STOP", selectedScrcpyRes, selectedScrcpyBitrate, selectedScrcpyOrient,
                        (ok, m) -> {
                            Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                            refreshAll(act);
                        }));
        addPill(act, ctrlRow, "📡 ENABLE WIRELESS (5555)", false, () ->
                MacCompanion.controlScrcpy(act, "ENABLE_WIRELESS", selectedScrcpyRes, selectedScrcpyBitrate, selectedScrcpyOrient,
                        (ok, m) -> {
                            Toast.makeText(act, m, Toast.LENGTH_LONG).show();
                            refreshAll(act);
                        }));
        addPill(act, ctrlRow, "🔄 RECONNECT", false, () ->
                MacCompanion.controlScrcpy(act, "RECONNECT", selectedScrcpyRes, selectedScrcpyBitrate, selectedScrcpyOrient,
                        (ok, m) -> {
                            Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                            refreshAll(act);
                        }));
        addPill(act, ctrlRow, "🔌 DISCONNECT", false, () ->
                MacCompanion.controlScrcpy(act, "DISCONNECT", selectedScrcpyRes, selectedScrcpyBitrate, selectedScrcpyOrient,
                        (ok, m) -> {
                            Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                            refreshAll(act);
                        }));
        addPill(act, ctrlRow, "🖥️ OPEN MAC DESKTOP UI", false, () ->
                MacCompanion.sendMacCommand(act, "OPEN_DASHBOARD", (ok, m) ->
                        Toast.makeText(act, m, Toast.LENGTH_SHORT).show()));

        ctrlScroll.addView(ctrlRow);
        card.addView(ctrlScroll);

        card.addView(Theme.createDivider(act));
        card.addView(Theme.createSectionLabel(act, "PHASE 04 // PIXEL 7 CAMERA AS MAC WEBCAM (UVC + SCRCPY CAMERA)"));

        webcamStatusText = new TextView(act);
        webcamStatusText.setTypeface(Theme.MONO);
        webcamStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        webcamStatusText.setTextColor(Theme.TEXT_PRIMARY);
        webcamStatusText.setText("Pixel 7 Hardware Cameras: Rear (4080×3072 @ 15/30/60fps) • Front (3440×2448 @ 15/24/30fps)");
        card.addView(webcamStatusText);

        HorizontalScrollView camScroll = new HorizontalScrollView(act);
        camScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout camRow = new LinearLayout(act);
        camRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cmLp.topMargin = Theme.dp(act, 8);
        camScroll.setLayoutParams(cmLp);

        addPill(act, camRow, "📷 STREAM REAR CAM (1080P 30FPS)", true, () ->
                MacCompanion.controlWebcam(act, "START_CAMERA", "back", "1920x1080", 30, (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_LONG).show();
                    refreshAll(act);
                }));
        addPill(act, camRow, "📷 REAR CAM (1080P 60FPS)", false, () ->
                MacCompanion.controlWebcam(act, "START_CAMERA", "back", "1920x1080", 60, (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_LONG).show();
                    refreshAll(act);
                }));
        addPill(act, camRow, "🤳 FRONT CAM (1080P 30FPS)", false, () ->
                MacCompanion.controlWebcam(act, "START_CAMERA", "front", "1920x1080", 30, (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_LONG).show();
                    refreshAll(act);
                }));
        addPill(act, camRow, "⏹ STOP CAMERA STREAM", false, () ->
                MacCompanion.controlWebcam(act, "STOP_CAMERA", "back", "1920x1080", 30, (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));
        addPill(act, camRow, "🔌 NATIVE USB UVC WEBCAM MODE", false, () -> {
            try {
                Intent usbIntent = new Intent("android.settings.USB_SETTINGS");
                usbIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(usbIntent);
            } catch (Exception e) {
                try {
                    Intent devIntent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    act.startActivity(devIntent);
                } catch (Exception ignored) {}
            }
            Toast.makeText(act, "Select 'Webcam' under Use USB for to expose native UVC camera to FaceTime/Zoom",
                    Toast.LENGTH_LONG).show();
        });

        camScroll.addView(camRow);
        card.addView(camScroll);

        return card;
    }

    // =========================================================================
    // PHASE 5: AAYUSH OS – MAC OTP INBOX (STRICTLY NEVER IN NOW BAR)
    // =========================================================================

    private View buildMacOtpInboxCard(final MainActivity act) {
        LinearLayout card = Theme.createCard(act, true);
        card.addView(Theme.createSectionLabel(act, "PHASE 05 // AAYUSH OS — MAC OTP INBOX (NEVER IN NOW BAR)"));

        TextView policyText = new TextView(act);
        policyText.setText("Strict Privacy Architecture: OTP notifications are blocked from the Dynamic Now Bar, camera cutout, and phone telemetry pill. Permitted OTPs are forwarded over authenticated LAN to volatile Mac RAM and auto-purged after expiry.");
        policyText.setTextColor(Theme.TEXT_SECONDARY);
        policyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        card.addView(policyText);

        card.addView(buildSwitchRow(act, "Explicit Opt-In: Forward OTPs to Mac Inbox",
                MacCompanion.isOtpMacDeliveryEnabled(act),
                checked -> MacCompanion.setOtpMacDeliveryEnabled(act, checked)));

        card.addView(buildSwitchRow(act, "Auto-Copy OTP to Mac Clipboard on Arrival",
                MacCompanion.isOtpAutoCopyOnMac(act),
                checked -> MacCompanion.setOtpAutoCopyOnMac(act, checked)));

        card.addView(buildSwitchRow(act, "Per-App Allowlist: Permit All Messaging & Mail Apps",
                MacCompanion.prefs(act).getBoolean("otp_allow_all_apps", true),
                checked -> MacCompanion.prefs(act).edit().putBoolean("otp_allow_all_apps", checked).apply()));

        otpInboxStatusText = new TextView(act);
        otpInboxStatusText.setTypeface(Theme.MONO_BOLD);
        otpInboxStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        otpInboxStatusText.setTextColor(Theme.TELEMETRY_GREEN);
        int pad = Theme.dp(act, 10);
        otpInboxStatusText.setPadding(pad, pad, pad, pad);
        otpInboxStatusText.setBackground(Theme.cardDrawable(act, 0xFF0A1910, Theme.TELEMETRY_GREEN, 10f, 1f));
        LinearLayout.LayoutParams oiLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        oiLp.topMargin = Theme.dp(act, 8);
        oiLp.bottomMargin = Theme.dp(act, 8);
        otpInboxStatusText.setLayoutParams(oiLp);
        otpInboxStatusText.setText("🔐 MAC OTP INBOX: Empty (TTL: " + MacCompanion.getOtpTtlSec(act) + "s)");
        card.addView(otpInboxStatusText);

        HorizontalScrollView otpScroll = new HorizontalScrollView(act);
        otpScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout otpRow = new LinearLayout(act);
        otpRow.setOrientation(LinearLayout.HORIZONTAL);

        addPill(act, otpRow, "🧪 TEST OTP TO MAC (814920)", true, () ->
                MacCompanion.forwardOtpToMac(act, "814920", "Aayush OS Auth", "dev.aayush.os",
                        "Verification Code", (ok, m) -> {
                            Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                            refreshAll(act);
                        }));
        addPill(act, otpRow, "📋 1-CLICK COPY ON MAC", false, () ->
                MacCompanion.copyMacOtp(act, "", (ok, m) ->
                        Toast.makeText(act, m, Toast.LENGTH_SHORT).show()));
        addPill(act, otpRow, "🧹 CLEAR MAC OTP INBOX", false, () ->
                MacCompanion.clearMacOtpInbox(act, (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));
        addPill(act, otpRow, "⏱️ TTL: 30S", false, () -> {
            MacCompanion.setOtpTtlSec(act, 30);
            refreshAll(act);
        });
        addPill(act, otpRow, "⏱️ TTL: 60S", false, () -> {
            MacCompanion.setOtpTtlSec(act, 60);
            refreshAll(act);
        });
        addPill(act, otpRow, "⏱️ TTL: 120S", false, () -> {
            MacCompanion.setOtpTtlSec(act, 120);
            refreshAll(act);
        });

        otpScroll.addView(otpRow);
        card.addView(otpScroll);

        return card;
    }

    // =========================================================================
    // PHASE 6 & 7: ADVANCED CLIPBOARD & BIDIRECTIONAL FILE TRANSFER
    // =========================================================================

    private View buildClipboardAndFileTransferCard(final MainActivity act) {
        LinearLayout card = Theme.createCard(act, false);
        card.addView(Theme.createSectionLabel(act, "PHASE 06 // ADVANCED BIDIRECTIONAL CLIPBOARD SYNC"));

        card.addView(buildSwitchRow(act, "Automatic Clipboard Sync on App Focus",
                MacCompanion.isAutoClipboardSyncEnabled(act),
                checked -> MacCompanion.setAutoClipboardSyncEnabled(act, checked)));

        card.addView(buildSwitchRow(act, "Exclude Sensitive Passwords, API Keys & OTPs",
                MacCompanion.isSensitiveClipExcluded(act),
                checked -> MacCompanion.setSensitiveClipExcluded(act, checked)));

        HorizontalScrollView clipScroll = new HorizontalScrollView(act);
        clipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout clipRow = new LinearLayout(act);
        clipRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crLp.topMargin = Theme.dp(act, 6);
        clipScroll.setLayoutParams(crLp);

        addPill(act, clipRow, "📤 PUSH PIXEL → MAC", true, () -> {
            SmartClipboard.Analysis a = SmartClipboard.inspectClipboard(act);
            MacCompanion.pushClipboardToMac(act, a.rawText, (ok, m) -> {
                Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                refreshAll(act);
            });
        });
        addPill(act, clipRow, "📥 PULL MAC → PIXEL", false, () ->
                MacCompanion.pullClipboardFromMac(act, (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));
        addPill(act, clipRow, "🧹 CLEAR HISTORY", false, () -> {
            SmartClipboard.clearHistory();
            refreshAll(act);
            Toast.makeText(act, "Cleared clipboard sync history", Toast.LENGTH_SHORT).show();
        });
        addPill(act, clipRow, "RETENTION: 15M", false, () -> {
            MacCompanion.prefs(act).edit().putInt("clip_retention_min", 15).apply();
            Toast.makeText(act, "Clipboard history retention: 15 min", Toast.LENGTH_SHORT).show();
        });
        addPill(act, clipRow, "RETENTION: 60M", false, () -> {
            MacCompanion.prefs(act).edit().putInt("clip_retention_min", 60).apply();
            Toast.makeText(act, "Clipboard history retention: 60 min", Toast.LENGTH_SHORT).show();
        });

        clipScroll.addView(clipRow);
        card.addView(clipScroll);

        clipHistoryText = new TextView(act);
        clipHistoryText.setTypeface(Theme.MONO);
        clipHistoryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        clipHistoryText.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chLp.topMargin = Theme.dp(act, 6);
        clipHistoryText.setLayoutParams(chLp);
        clipHistoryText.setText("Clipboard Sync History: (no items yet)");
        card.addView(clipHistoryText);

        card.addView(Theme.createDivider(act));
        card.addView(Theme.createSectionLabel(act, "PHASE 07 // BIDIRECTIONAL WIRELESS FILE TRANSFER"));

        transferStatusText = new TextView(act);
        transferStatusText.setTypeface(Theme.MONO_BOLD);
        transferStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        transferStatusText.setTextColor(Theme.PAPAYA);
        transferStatusText.setText("DEST: ~/Downloads/PixelDrop • Ready for binary streaming transfer");
        card.addView(transferStatusText);

        transferProgressBar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        transferProgressBar.setMax(100);
        transferProgressBar.setProgress(0);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(act, 6));
        pbLp.topMargin = Theme.dp(act, 6);
        pbLp.bottomMargin = Theme.dp(act, 8);
        transferProgressBar.setLayoutParams(pbLp);
        card.addView(transferProgressBar);

        // Destination Selector & Send / Cancel Buttons
        HorizontalScrollView ftScroll = new HorizontalScrollView(act);
        ftScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout ftRow = new LinearLayout(act);
        ftRow.setOrientation(LinearLayout.HORIZONTAL);

        addPill(act, ftRow, "📤 PICK & SEND FILE TO MAC", true, () -> launchFilePicker(act));

        addPill(act, ftRow, "📝 SEND CLIPBOARD AS FILE", false, () -> {
            SmartClipboard.Analysis a = SmartClipboard.inspectClipboard(act);
            String content = !TextUtils.isEmpty(a.rawText)
                    ? a.rawText
                    : "Aayush OS V3 Wireless File Transfer\nDevice: Pixel 7 Panther (#81)\nTime: " + System.currentTimeMillis();
            String ext = (a.type == SmartClipboard.ContentType.JSON) ? ".json" : ".txt";
            String fname = "pixel7_payload_" + (System.currentTimeMillis() / 1000L) + ext;
            byte[] raw = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            MacCompanion.uploadFileToMac(act, null, fname, raw, selectedMacDestFolder,
                    (pct, sent, tot, kbps) -> {
                        transferProgressBar.setProgress(pct);
                        transferStatusText.setText(String.format(Locale.US,
                                "UPLOADING %s: %d%% (%s @ %.1f KB/s)",
                                fname, pct, MacCompanion.formatSize(sent), kbps));
                    },
                    (ok, msg) -> {
                        transferProgressBar.setProgress(ok ? 100 : 0);
                        transferStatusText.setText(msg);
                        refreshAll(act);
                    });
        });

        addPill(act, ftRow, "⏹ CANCEL TRANSFER", false, () -> {
            MacCompanion.cancelActiveTransfer();
            transferStatusText.setText("⏹ Cancel signal sent to active stream");
        });

        addPill(act, ftRow, "📂 DEST: PIXELDROP", false, () -> {
            selectedMacDestFolder = "PixelDrop";
            transferStatusText.setText("Mac Destination: ~/Downloads/PixelDrop/");
        });
        addPill(act, ftRow, "📂 DEST: DESKTOP", false, () -> {
            selectedMacDestFolder = "Desktop";
            transferStatusText.setText("Mac Destination: ~/Desktop/");
        });
        addPill(act, ftRow, "📂 DEST: DOCUMENTS", false, () -> {
            selectedMacDestFolder = "Documents";
            transferStatusText.setText("Mac Destination: ~/Documents/");
        });

        ftScroll.addView(ftRow);
        card.addView(ftScroll);

        TextView outboxHeader = Theme.createSectionLabel(act, "MAC → PIXEL OUTBOX (TAP ANY FILE TO RECEIVE ON PIXEL)");
        LinearLayout.LayoutParams ohLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ohLp.topMargin = Theme.dp(act, 10);
        outboxHeader.setLayoutParams(ohLp);
        card.addView(outboxHeader);

        macOutboxListContainer = new LinearLayout(act);
        macOutboxListContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(macOutboxListContainer);

        transferHistoryText = new TextView(act);
        transferHistoryText.setTypeface(Theme.MONO);
        transferHistoryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        transferHistoryText.setTextColor(Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams thLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        thLp.topMargin = Theme.dp(act, 6);
        transferHistoryText.setLayoutParams(thLp);
        card.addView(transferHistoryText);

        return card;
    }

    // =========================================================================
    // PHASE 8: MAC NOTIFICATION CENTRE
    // =========================================================================

    private View buildMacNotificationCentreCard(final MainActivity act) {
        LinearLayout card = Theme.createCard(act, false);
        card.addView(Theme.createSectionLabel(act, "PHASE 08 // MAC NOTIFICATION CENTRE (NON-OTP MIRRORING)"));

        card.addView(buildSwitchRow(act, "Forward Permitted Pixel Notifications to Mac",
                MacCompanion.isMacNotifForwardingEnabled(act),
                checked -> MacCompanion.setMacNotifForwardingEnabled(act, checked)));

        HorizontalScrollView nfScroll = new HorizontalScrollView(act);
        nfScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nfRow = new LinearLayout(act);
        nfRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams nfLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nfLp.topMargin = Theme.dp(act, 6);
        nfLp.bottomMargin = Theme.dp(act, 8);
        nfScroll.setLayoutParams(nfLp);

        addPill(act, nfRow, "FILTER: MESSAGING & DEV", false, () -> {
            MacCompanion.setMacNotifFilterMode(act, "MESSAGING_AND_DEV");
            Toast.makeText(act, "Filter: Messaging, Mail & Dev apps", Toast.LENGTH_SHORT).show();
        });
        addPill(act, nfRow, "FILTER: ALL CLEARABLE APPS", false, () -> {
            MacCompanion.setMacNotifFilterMode(act, "ALL_CLEARABLE");
            Toast.makeText(act, "Filter: All clearable non-OTP notifications", Toast.LENGTH_SHORT).show();
        });
        addPill(act, nfRow, "🧪 SEND TEST NOTIFICATION TO MAC", true, () -> {
            MacCompanion.forwardNotificationToMac(
                    act,
                    "test_" + System.currentTimeMillis(),
                    "GitHub Mobile",
                    "com.github.android",
                    "PR #81 Merged: Cross-Device Intelligence",
                    "Oscar Piastri #81 telemetry & scrcpy bridge verified on https://github.com",
                    "https://github.com"
            );
            uiRefreshDelayed(act, 400L);
        });
        addPill(act, nfRow, "🧹 CLEAR MAC NOTIFICATIONS", false, () ->
                MacCompanion.sendNotificationAction(act, "CLEAR_ALL", "", "", "", (ok, m) -> {
                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
                    refreshAll(act);
                }));

        nfScroll.addView(nfRow);
        card.addView(nfScroll);

        macNotifListContainer = new LinearLayout(act);
        macNotifListContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(macNotifListContainer);

        return card;
    }

    // =========================================================================
    // FILE PICKER & LIVE STATE REFRESH
    // =========================================================================

    public void launchFilePicker(MainActivity act) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            act.startActivityForResult(intent, REQ_PICK_FILE_FOR_MAC);
        } catch (Exception e) {
            Toast.makeText(act, "File picker unavailable: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void handlePickedFileResult(final MainActivity act, Uri uri) {
        if (uri == null) return;
        String fname = "pixel7_file_" + (System.currentTimeMillis() / 1000L);
        try (Cursor c = act.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && !TextUtils.isEmpty(c.getString(idx))) {
                    fname = c.getString(idx);
                }
            }
        } catch (Exception ignored) {}

        final String finalName = fname;
        transferProgressBar.setProgress(5);
        transferStatusText.setText("Streaming " + finalName + " to MacBook Air M4 (" + selectedMacDestFolder + ")...");
        MacCompanion.uploadFileToMac(act, uri, finalName, null, selectedMacDestFolder,
                (pct, sent, tot, kbps) -> {
                    transferProgressBar.setProgress(pct);
                    transferStatusText.setText(String.format(Locale.US,
                            "SENDING %s: %d%% (%s / %s @ %.1f KB/s)",
                            finalName, pct, MacCompanion.formatSize(sent),
                            MacCompanion.formatSize(tot), kbps));
                },
                (ok, msg) -> {
                    transferProgressBar.setProgress(ok ? 100 : 0);
                    transferStatusText.setText(msg);
                    Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
                    refreshAll(act);
                });
    }

    private void triggerMacFileDownload(final MainActivity act, final String filename) {
        transferProgressBar.setProgress(5);
        transferStatusText.setText("Receiving " + filename + " from MacBook Air M4...");
        MacCompanion.downloadFileFromMac(act, filename,
                (pct, rec, tot, kbps) -> {
                    transferProgressBar.setProgress(pct);
                    transferStatusText.setText(String.format(Locale.US,
                            "RECEIVING %s: %d%% (%s @ %.1f KB/s)",
                            filename, pct, MacCompanion.formatSize(rec), kbps));
                },
                (ok, msg) -> {
                    transferProgressBar.setProgress(ok ? 100 : 0);
                    transferStatusText.setText(msg);
                    Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
                    refreshAll(act);
                });
    }

    public void updatePixelLiveTelemetry(MainActivity act, HardwareMonitor.Snapshot px) {
        if (pixelPanelText == null) return;
        MacCompanion.MacStatus st = MacCompanion.lastKnownStatus;
        String bridgeState = st.manuallyDisconnected ? "PAUSED"
                : (st.wifiDisconnectedByUser ? "MAC WIFI OFF"
                : (st.online ? (st.transportMode + " (" + st.latencyMs + "ms)") : "OFFLINE"));
        String vpnStr = !TextUtils.isEmpty(px.tailscaleIp) ? ("ON (" + px.tailscaleIp + ")")
                : (px.vpnActive ? "VPN UP" : "OFF");

        pixelPanelText.setText(String.format(Locale.US,
                "BATT   : %d%% (%s%.1fW)\n" +
                "TEMP   : %.1f°C (%s)\n" +
                "LAN IP : %s\n" +
                "VPN/TS : %s\n" +
                "SPEED  : ↓%s ↑%s\n" +
                "SCREEN : %s (90Hz)\n" +
                "RADIOS : Wi-Fi %s • BT %s\n" +
                "BRIDGE : %s",
                px.batteryPct, px.isCharging ? "⚡" : "", px.powerWatts,
                px.batteryTempC, px.thermalState,
                px.wifiIp,
                vpnStr,
                HardwareMonitor.formatSpeed(px.rxKbps), HardwareMonitor.formatSpeed(px.txKbps),
                px.screenInteractive ? "ON" : "OFF",
                px.wifiEnabled ? "ON" : "OFF",
                px.bluetoothEnabled ? "ON" : "OFF",
                bridgeState
        ));
    }

    public void refreshAll(final MainActivity act) {
        // 1. Update Wireless Audit & V3.1 Network / ADB Separation Line
        if (wirelessAuditText != null) {
            int adbEnabled = Settings.Global.getInt(act.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            boolean hasWriteSec = act.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean hasOverlay = Settings.canDrawOverlays(act);
            MacCompanion.MacStatus curSt = MacCompanion.lastKnownStatus;
            wirelessAuditText.setText(String.format(Locale.US,
                    "COMPANION LINK    : %s (HTTP :8181 ↔ :%d • ENC_V31 + HMAC Auth)\n" +
                    "DISCOVERY ENGINE  : Signed UDP :8182 + mDNS (_aayush-os._tcp) + Tailscale VPN\n" +
                    "ADB SEPARATION    : %s (HTTP Companion needs ZERO ADB)\n" +
                    "PRIVILEGES KEPT   : WRITE_SECURE=%s • OVERLAY=%s • OFFICE TEST=PENDING",
                    curSt.online ? (curSt.transportMode + " @ " + curSt.hostIp) : curSt.transportMode,
                    PixelCompanionServer.PORT,
                    adbEnabled == 0 ? "ADB OFF (Daily Wireless Mode)" : "ADB ON (scrcpy/Webcam Ready)",
                    hasWriteSec ? "YES" : "NO",
                    hasOverlay ? "YES" : "NO"
            ));
        }

        // 2. Update Local Clipboard Sync History
        if (clipHistoryText != null) {
            List<SmartClipboard.HistoryItem> hist = SmartClipboard.getHistory(act);
            if (hist.isEmpty()) {
                clipHistoryText.setText("Clipboard Sync History: (empty)");
            } else {
                StringBuilder sb = new StringBuilder("Recent Synced Clipboards:\n");
                for (int i = 0; i < Math.min(4, hist.size()); i++) {
                    SmartClipboard.HistoryItem h = hist.get(i);
                    sb.append("• [").append(h.direction).append(" • ").append(h.badgeLabel).append("] ")
                            .append(h.text).append("\n");
                }
                clipHistoryText.setText(sb.toString().trim());
            }
        }

        // 3. Query Mac Companion Status
        MacCompanion.fetchMacStatus(act, st -> {
            if (macHostEditInput != null && !macHostEditInput.hasFocus() && st.online) {
                macHostEditInput.setText(st.hostIp);
            }
            if (macTailscaleEditInput != null && !macTailscaleEditInput.hasFocus() && !TextUtils.isEmpty(st.macTailscaleIp)) {
                macTailscaleEditInput.setText(st.macTailscaleIp);
            }

            if (crossStatusBadge != null) {
                if (st.online) {
                    crossStatusBadge.setText(st.transportMode + " • " + st.latencyMs + "ms");
                    crossStatusBadge.setTextColor("TAILSCALE VPN".equals(st.transportMode)
                            ? Theme.PAPAYA : Theme.TELEMETRY_GREEN);
                } else if (st.wifiDisconnectedByUser) {
                    crossStatusBadge.setText("MAC WI-FI OFF");
                    crossStatusBadge.setTextColor(Theme.TELEMETRY_AMBER);
                } else if (st.manuallyDisconnected) {
                    crossStatusBadge.setText("DISCONNECTED");
                    crossStatusBadge.setTextColor(Theme.TEXT_SECONDARY);
                } else {
                    crossStatusBadge.setText("OFFLINE");
                    crossStatusBadge.setTextColor(Theme.TELEMETRY_RED);
                }
            }

            if (macConnPill != null) {
                macConnPill.setText(st.online ? (st.transportMode + " • " + st.latencyMs + "ms")
                        : (st.wifiDisconnectedByUser ? "WI-FI OFF BY USER" : "OFFLINE"));
                macConnPill.setTextColor(st.online ? Theme.TELEMETRY_GREEN : Theme.TELEMETRY_RED);
            }

            if (macPanelText != null) {
                if (st.online) {
                    macPanelText.setText(String.format(Locale.US,
                            "BATT   : %d%% (%s)\n" +
                            "SOURCE : %s\n" +
                            "CPU    : %.1f%% Load\n" +
                            "WI-FI  : %s\n" +
                            "VPN/TS : %s\n" +
                            "BT     : %s\n" +
                            "UPTIME : %s\n" +
                            "LINK   : %s (:8181)",
                            st.batteryPct, st.batteryCharging ? "CHARGING" : "DISCHARGING",
                            st.powerSource,
                            st.cpuPct,
                            st.wifiPower ? st.wifiNetwork : "OFF (Disabled)",
                            st.tailscaleRunning ? ("ON (" + st.macTailscaleIp + ")") : st.tailscaleState,
                            st.btPower ? (st.btConnected ? "CONNECTED" : "ON (Standby)") : "OFF",
                            st.uptimeInfo,
                            st.transportMode
                    ));
                } else {
                    macPanelText.setText(st.wifiDisconnectedByUser
                            ? "Mac Wi-Fi was turned OFF from Pixel.\nRemains OFF until re-enabled."
                            : "Mac Bridge Offline at\n" + st.hostIp + ":8181\n(Tap Dynamic Discover or Audit)");
                }
            }

            if (quickMirrorBtn != null) {
                quickMirrorBtn.setText(st.scrcpyRunning ? "⏹ STOP MIRROR" : "🖥️ MIRROR PIXEL");
            }
            if (quickMacWifiBtn != null) {
                quickMacWifiBtn.setText(st.wifiPower ? "📶 MAC WI-FI: ON" : "📴 MAC WI-FI: OFF");
            }
            if (quickMacBtBtn != null) {
                quickMacBtBtn.setText(st.btPower ? "🦷 MAC BT: ON" : "📴 MAC BT: OFF");
            }
            if (quickMacCaffBtn != null) {
                quickMacCaffBtn.setText(st.caffeinated ? "☕ MAC: AWAKE" : "☕ CAFFEINATE MAC");
            }

            if (macSystemStateText != null) {
                if (st.online) {
                    macSystemStateText.setText(String.format(Locale.US,
                            "HOST & CHIP : %s (%s • %s)\n" +
                            "TRANSPORT   : %s via %s (%d ms)\n" +
                            "TAILSCALE   : %s\n" +
                            "BATTERY     : %s\n" +
                            "CPU USAGE   : %s\n" +
                            "UPTIME      : %s\n" +
                            "WI-FI STATE : %s\n" +
                            "BLUETOOTH   : %s\n" +
                            "CAFFEINATE  : %s  |  DARK MODE: %s\n" +
                            "AUDIO STATE : %s (Vol %d%%)",
                            st.computerName, st.chip, st.mdnsHost,
                            st.transportMode, st.hostIp, st.latencyMs,
                            st.tailscaleRunning ? ("ACTIVE • Mac VPN " + st.macTailscaleIp)
                                    : (st.tailscaleInstalled ? ("Installed (" + st.tailscaleState + ")") : "Not Installed"),
                            st.batteryInfo,
                            st.cpuUsage,
                            st.uptimeInfo,
                            st.wifiPower ? ("ON • " + st.wifiNetwork) : "OFF (Disabled)",
                            st.btStatus,
                            st.caffeinated ? "ACTIVE (Stay Awake)" : "OFF (Normal Sleep)",
                            st.darkMode ? "DARK" : "LIGHT",
                            st.audioMuted ? "MUTED" : "UNMUTED", st.audioVolume
                    ));
                } else {
                    macSystemStateText.setText(st.wifiDisconnectedByUser
                            ? "MAC WI-FI IS OFF (Disabled by user command — zero auto-reconnect)."
                            : "Mac companion unreachable at " + st.hostIp + ":8181 (Transport: OFFLINE).");
                }
            }

            if (scrcpyStatusText != null) {
                scrcpyStatusText.setText(String.format(Locale.US,
                        "SCRCPY ENGINE : %s\n" +
                        "ADB SESSION   : %s\n" +
                        "TARGET LINK   : %s\n" +
                        "CONFIG        : %dp • %s • %s (Requires Separate ADB Link)",
                        st.scrcpyRunning ? "🟢 ACTIVE MIRRORING ON MAC" : "⚪ STANDBY (Ready)",
                        st.adbStatusLabel,
                        st.scrcpyTarget,
                        selectedScrcpyRes, selectedScrcpyBitrate, selectedScrcpyOrient.toUpperCase(Locale.US)
                ));
            }

            if (webcamStatusText != null) {
                webcamStatusText.setText(String.format(Locale.US,
                        "SCRCPY CAMERA : %s\n" +
                        "USB UVC CAM   : %s (Android 17 DeviceAsWebcam)\n" +
                        "HARDWARE      : Rear 4080×3072 (60fps) • Front 3440×2448 (30fps)",
                        st.webcamRunning ? ("🟢 STREAMING " + st.webcamFacing.toUpperCase(Locale.US) + " (" + st.webcamSize + ")") : "⚪ STANDBY",
                        st.usbUvcDetected ? "🟢 DETECTED IN MACOS VIDEO APPS" : "⚪ SELECT 'WEBCAM' IN USB SETTINGS WHEN PLUGGED"
                ));
            }

            if (otpInboxStatusText != null) {
                if (st.otpInbox.isEmpty()) {
                    otpInboxStatusText.setText("🔐 MAC OTP INBOX: No active OTPs in Mac RAM (Auto-clear TTL: "
                            + MacCompanion.getOtpTtlSec(act) + "s)");
                } else {
                    StringBuilder sb = new StringBuilder("🔐 ACTIVE OTPS ON MACBOOK AIR M4:\n");
                    for (MacCompanion.OtpInboxItem o : st.otpInbox) {
                        sb.append("• CODE: ").append(o.code)
                                .append("  (").append(o.app)
                                .append(" • clears in ").append(o.remainingSec).append("s)\n");
                    }
                    otpInboxStatusText.setText(sb.toString().trim());
                }
            }
        });

        // 4. Refresh Mac Outbox & Transfer History
        MacCompanion.fetchMacOutboxAndHistory(act, (files, history) -> {
            if (macOutboxListContainer != null) {
                macOutboxListContainer.removeAllViews();
                if (files.isEmpty()) {
                    TextView empty = new TextView(act);
                    empty.setTypeface(Theme.MONO);
                    empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                    empty.setTextColor(Theme.TEXT_MUTED);
                    empty.setText("(No files staged in ~/Downloads/PixelDrop)");
                    macOutboxListContainer.addView(empty);
                } else {
                    for (int i = 0; i < Math.min(5, files.size()); i++) {
                        final MacCompanion.RemoteFileItem f = files.get(i);
                        LinearLayout row = new LinearLayout(act);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(0, Theme.dp(act, 4), 0, Theme.dp(act, 4));

                        TextView tv = new TextView(act);
                        tv.setTypeface(Theme.MONO);
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                        tv.setTextColor(Theme.TEXT_PRIMARY);
                        tv.setText("📄 " + f.name + " (" + MacCompanion.formatSize(f.sizeBytes) + ")");
                        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                        TextView dlBtn = Theme.createPill(act, "📥 RECEIVE",
                                Theme.BG_ELEVATED, Theme.PAPAYA, Theme.BORDER_GRAPHITE);
                        dlBtn.setOnClickListener(v -> triggerMacFileDownload(act, f.name));

                        row.addView(tv);
                        row.addView(dlBtn);
                        macOutboxListContainer.addView(row);
                    }
                }
            }

            if (transferHistoryText != null) {
                if (history.isEmpty()) {
                    transferHistoryText.setText("Transfer History: (no transfers yet)");
                } else {
                    StringBuilder sb = new StringBuilder("Recent Transfers:\n");
                    for (int i = 0; i < Math.min(4, history.size()); i++) {
                        MacCompanion.TransferHistoryItem h = history.get(i);
                        sb.append(String.format(Locale.US, "• [%s] %s (%s @ %.1f KB/s) — %s\n",
                                h.direction, h.filename, MacCompanion.formatSize(h.sizeBytes), h.speedKbps, h.status));
                    }
                    transferHistoryText.setText(sb.toString().trim());
                }
            }
        });

        // 5. Refresh Mac Notification Centre
        MacCompanion.fetchMacNotifications(act, items -> {
            if (macNotifListContainer == null) return;
            macNotifListContainer.removeAllViews();
            if (items.isEmpty()) {
                TextView empty = new TextView(act);
                empty.setTypeface(Theme.MONO);
                empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                empty.setTextColor(Theme.TEXT_MUTED);
                empty.setText("(No Pixel notifications in Mac Notification Centre)");
                macNotifListContainer.addView(empty);
                return;
            }
            for (int i = 0; i < Math.min(5, items.size()); i++) {
                final MacCompanion.MacNotifItem n = items.get(i);
                LinearLayout box = new LinearLayout(act);
                box.setOrientation(LinearLayout.VERTICAL);
                int p = Theme.dp(act, 10);
                box.setPadding(p, p, p, p);
                box.setBackground(Theme.cardDrawable(act, Theme.BG_ELEVATED, Theme.BORDER_GRAPHITE, 10f, 1f));
                LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bLp.bottomMargin = Theme.dp(act, 6);
                box.setLayoutParams(bLp);

                TextView head = new TextView(act);
                head.setTypeface(Theme.MONO_BOLD);
                head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                head.setTextColor(Theme.PAPAYA);
                head.setText(n.app + " // " + n.title);
                box.addView(head);

                TextView body = new TextView(act);
                body.setTypeface(Theme.MONO);
                body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                body.setTextColor(Theme.TEXT_PRIMARY);
                body.setText(n.body);
                box.addView(body);

                LinearLayout actRow = new LinearLayout(act);
                actRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams arLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                arLp.topMargin = Theme.dp(act, 6);
                actRow.setLayoutParams(arLp);

                addPill(act, actRow, "📋 COPY ON MAC", false, () ->
                        MacCompanion.sendNotificationAction(act, "COPY", n.key, n.body, "", (ok, m) ->
                                Toast.makeText(act, m, Toast.LENGTH_SHORT).show()));

                if (!TextUtils.isEmpty(n.url)) {
                    addPill(act, actRow, "🌐 OPEN LINK ON MAC", true, () ->
                            MacCompanion.sendNotificationAction(act, "OPEN_URL", n.key, "", n.url, (ok, m) ->
                                    Toast.makeText(act, m, Toast.LENGTH_SHORT).show()));
                }

                addPill(act, actRow, "✖ DISMISS", false, () ->
                        MacCompanion.sendNotificationAction(act, "DISMISS", n.key, "", "", (ok, m) -> {
                            NowBarNotificationListener.dismissNotificationByKey(n.key);
                            refreshAll(act);
                        }));

                box.addView(actRow);
                macNotifListContainer.addView(box);
            }
        });
    }

    // =========================================================================
    // UI HELPERS
    // =========================================================================

    private interface BoolCallback {
        void onChanged(boolean checked);
    }

    private View buildSwitchRow(Context ctx, String title, boolean initial, final BoolCallback cb) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(ctx, 4), 0, Theme.dp(ctx, 4));

        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextColor(Theme.TEXT_PRIMARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Theme.SANS_MEDIUM);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = Theme.dp(ctx, 8);
        tv.setLayoutParams(lp);

        Switch sw = new Switch(ctx);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((b, isChecked) -> cb.onChanged(isChecked));

        row.addView(tv);
        row.addView(sw);
        return row;
    }

    private EditText createInput(Context ctx, String hint, String text) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setHintTextColor(Theme.TEXT_MUTED);
        et.setText(text);
        et.setTextColor(Theme.TEXT_PRIMARY);
        et.setTypeface(Theme.MONO);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        et.setSingleLine(true);
        int p = Theme.dp(ctx, 10);
        et.setPadding(p, p, p, p);
        et.setBackground(Theme.cardDrawable(ctx, 0xFF090909, Theme.BORDER_GRAPHITE, 10f, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(ctx, 6);
        lp.bottomMargin = Theme.dp(ctx, 6);
        et.setLayoutParams(lp);
        return et;
    }

    private TextView addPill(Context ctx, LinearLayout row, String label, boolean papaya, Runnable onClick) {
        TextView btn = Theme.createPill(
                ctx,
                label,
                papaya ? Theme.PAPAYA : Theme.BG_ELEVATED,
                papaya ? Theme.BG_OBSIDIAN : Theme.TEXT_PRIMARY,
                papaya ? Theme.PAPAYA : Theme.BORDER_GRAPHITE
        );
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(ctx, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> onClick.run());
        row.addView(btn);
        return btn;
    }

    private void uiRefreshDelayed(final MainActivity act, long delayMs) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> refreshAll(act), delayMs);
    }
}
