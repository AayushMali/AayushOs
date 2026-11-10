import Cocoa
import CryptoKit
import UserNotifications

// =============================================================================
// AAYUSH OS V3.1 — NATIVE macOS MENU BAR OTP INBOX & FLOATING DESKTOP WIDGET
// =============================================================================
// • Persistent macOS Menu Bar Icon with live connection & OTP arrival badge
// • Click-to-open Popover with App Name, Received Time, Live Expiry Countdown
// • Privacy-First: OTP values hidden (••••••) by default until explicitly revealed
// • 1-Click Copy (POST /otp/copy), Individual Clear & Clear All (POST /otp/clear)
// • Optional Floating Desktop OTP Panel (auto-shows on OTP arrival, auto-hides when empty)
// • Native macOS Notifications with Copy action & strict Lock-Screen Privacy
// • Push-based long-polling (/otp?wait_seq=...) + exponential backoff, zero disk storage
// =============================================================================

struct OtpEntry: Equatable {
    let id: Int64
    let code: String
    let maskedCode: String
    let masked: Bool
    let codeLen: Int
    let app: String
    let pkg: String
    let title: String
    let receivedAt: Int64
    let receivedTime: String
    let expiresAt: Int64
    var remainingSec: Int
    let ttl: Int
}

enum ThemeColor {
    static let obsidian = NSColor(calibratedRed: 0.04, green: 0.04, blue: 0.04, alpha: 1.0) // #0A0A0A
    static let carbon   = NSColor(calibratedRed: 0.08, green: 0.08, blue: 0.08, alpha: 1.0) // #141414
    static let elevated = NSColor(calibratedRed: 0.12, green: 0.12, blue: 0.12, alpha: 1.0) // #1F1F1F
    static let border   = NSColor(calibratedRed: 0.18, green: 0.18, blue: 0.18, alpha: 1.0) // #2E2E2E
    static let papaya   = NSColor(calibratedRed: 1.00, green: 0.50, blue: 0.00, alpha: 1.0) // #FF8000
    static let green    = NSColor(calibratedRed: 0.00, green: 0.90, blue: 0.46, alpha: 1.0) // #00E676
    static let red      = NSColor(calibratedRed: 1.00, green: 0.32, blue: 0.32, alpha: 1.0) // #FF5252
    static let textPri  = NSColor(calibratedRed: 0.96, green: 0.96, blue: 0.96, alpha: 1.0)
    static let textSec  = NSColor(calibratedRed: 0.62, green: 0.62, blue: 0.62, alpha: 1.0)
}

final class FlippedView: NSView {
    override var isFlipped: Bool { true }
}

final class AppDelegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {
    private var statusItem: NSStatusItem!
    private let popover = NSPopover()
    private var floatingPanel: NSPanel!
    private var toastPanel: NSPanel!
    private var toastHideWorkItem: DispatchWorkItem?

    private var popoverContentView: NSView!
    private var panelContentView: NSView!

    // Volatile RAM state only (never persisted to disk)
    private var otps: [OtpEntry] = []
    private var revealedIds: Set<Int64> = []
    private var notifiedIds: Set<Int64> = []
    private var copiedFeedbackIds: Set<Int64> = []
    private var currentSeq: Int64 = -1
    private var transportMode: String = "PROBING..."
    private var pixelIp: String = ""
    private var bridgeOnline: Bool = false

    private var floatingPanelEnabled: Bool = true {
        didSet {
            UserDefaults.standard.set(floatingPanelEnabled, forKey: "floating_otp_panel_enabled")
            updateFloatingPanelVisibility()
            rebuildUI()
        }
    }

    private var countdownTimer: Timer?
    private let pollQueue = DispatchQueue(label: "dev.aayush.os.otp.longpoll", qos: .userInitiated)
    private var isShuttingDown = false

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)

        setupNotifications()
        setupStatusItem()
        setupPopover()
        setupFloatingPanel()
        setupToastBanner()
        setupDistributedControlListener()

        if UserDefaults.standard.object(forKey: "floating_otp_panel_enabled") != nil {
            floatingPanelEnabled = UserDefaults.standard.bool(forKey: "floating_otp_panel_enabled")
        } else {
            floatingPanelEnabled = true
        }

        rebuildUI()
        startCountdownTimer()
        startPushLongPollLoop()
    }

    func applicationWillTerminate(_ notification: Notification) {
        isShuttingDown = true
        countdownTimer?.invalidate()
    }

    // MARK: - Auth Token & HMAC-SHA256 Signing

    private func loadAuthToken() -> String {
        let secretPath = (NSHomeDirectory() as NSString).appendingPathComponent(".aayush_os_bridge_secret")
        if let raw = try? String(contentsOfFile: secretPath, encoding: .utf8) {
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.count >= 12 {
                return trimmed
            }
        }
        return "aayush81-panther-m4-bridge"
    }

    private func makeSignedRequest(path: String, method: String = "GET", jsonBody: [String: Any]? = nil, timeout: TimeInterval = 5.0) -> URLRequest? {
        guard let url = URL(string: "http://127.0.0.1:8181\(path)") else { return nil }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalAndRemoteCacheData, timeoutInterval: timeout)
        req.httpMethod = method
        let token = loadAuthToken()
        let ts = String(Int(Date().timeIntervalSince1970))
        let msg = "\(method):\(path):\(ts)"
        let key = SymmetricKey(data: Data(token.utf8))
        let mac = HMAC<SHA256>.authenticationCode(for: Data(msg.utf8), using: key)
        let sig = mac.map { String(format: "%02x", $0) }.joined()

        req.setValue(token, forHTTPHeaderField: "X-Aayush-Token")
        req.setValue(ts, forHTTPHeaderField: "X-Aayush-Timestamp")
        req.setValue(sig, forHTTPHeaderField: "X-Aayush-Signature")

        if let body = jsonBody {
            req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            req.httpBody = try? JSONSerialization.data(withJSONObject: body, options: [])
        }
        return req
    }

    // MARK: - Screen Lock Privacy Check

    private func isMacScreenLocked() -> Bool {
        guard let dict = CGSessionCopyCurrentDictionary() as? [String: Any] else {
            return false
        }
        if let locked = dict["CGSSessionScreenIsLocked"] as? Bool, locked {
            return true
        }
        return false
    }

    // MARK: - Native macOS Notification Center + Copy Action

    private func setupNotifications() {
        let bundleUrl = Bundle.main.bundleURL
        guard bundleUrl.pathExtension == "app" else { return }
        let center = UNUserNotificationCenter.current()
        center.delegate = self

        let copyAction = UNNotificationAction(
            identifier: "COPY_OTP_ACTION",
            title: "📋 Copy OTP",
            options: []
        )
        let clearAction = UNNotificationAction(
            identifier: "CLEAR_OTP_ACTION",
            title: "Clear",
            options: [.destructive]
        )
        let category = UNNotificationCategory(
            identifier: "AAYUSH_OTP_ARRIVAL",
            actions: [copyAction, clearAction],
            intentIdentifiers: [],
            options: [.hiddenPreviewsShowTitle]
        )
        center.setNotificationCategories([category])
        center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    private func postArrivalNotification(for entry: OtpEntry) {
        NSSound(named: NSSound.Name("Glass"))?.play()

        // 1. Standard UNUserNotificationCenter notification (never includes raw OTP code for lock-screen privacy)
        if Bundle.main.bundleURL.pathExtension == "app" {
            let content = UNMutableNotificationContent()
            content.title = "🔐 OTP Received • \(entry.app)"
            content.subtitle = "Received \(entry.receivedTime) • Expires in \(entry.remainingSec)s"
            content.body = "Verification code hidden by default for lock-screen privacy. Click Copy OTP or open Menu Bar Inbox."
            content.categoryIdentifier = "AAYUSH_OTP_ARRIVAL"
            content.userInfo = ["otp_id": entry.id, "app": entry.app]

            let req = UNNotificationRequest(
                identifier: "aayush_otp_\(entry.id)",
                content: content,
                trigger: nil
            )
            UNUserNotificationCenter.current().add(req, withCompletionHandler: nil)
        }

        // 2. Native macOS Top-Right Interactive Notification Banner (with 1-Click Copy, suppressed when screen locked)
        if !isMacScreenLocked() {
            showToastBanner(for: entry)
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
         willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if isMacScreenLocked() {
            completionHandler([])
        } else {
            completionHandler([.banner, .sound])
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        let otpId = (info["otp_id"] as? Int64) ?? Int64(info["otp_id"] as? Int ?? 0)
        if response.actionIdentifier == "COPY_OTP_ACTION" || response.actionIdentifier == UNNotificationDefaultActionIdentifier {
            copyOtp(id: otpId)
        } else if response.actionIdentifier == "CLEAR_OTP_ACTION" {
            clearOtp(id: otpId)
        }
        completionHandler()
    }

    // MARK: - Distributed Control Listener (for CLI / Dashboard / Automation)

    private func setupDistributedControlListener() {
        DistributedNotificationCenter.default().addObserver(
            forName: NSNotification.Name("dev.aayush.os.menubar.command"),
            object: nil,
            queue: .main
        ) { [weak self] note in
            guard let self = self else { return }
            let cmd = (note.userInfo?["cmd"] as? String) ?? (note.object as? String) ?? ""
            switch cmd {
            case "OPEN_POPOVER":
                self.openPopover()
            case "CLOSE_POPOVER":
                self.popover.performClose(nil)
            case "REVEAL_LATEST":
                if let first = self.otps.first {
                    self.revealedIds.insert(first.id)
                    self.fetchOtpSnapshotImmediate()
                }
            case "HIDE_ALL":
                self.revealedIds.removeAll()
                self.fetchOtpSnapshotImmediate()
            case "SHOW_PANEL":
                self.floatingPanelEnabled = true
            case "HIDE_PANEL":
                self.floatingPanelEnabled = false
            default:
                break
            }
        }
    }

    // MARK: - Menu Bar Status Item & Popover Setup

    private func setupStatusItem() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        if let btn = statusItem.button {
            btn.target = self
            btn.action = #selector(togglePopover(_:))
            btn.sendAction(on: [.leftMouseUp, .rightMouseUp])
        }
        updateMenuBarTitle()
    }

    private func updateMenuBarTitle() {
        guard let btn = statusItem.button else { return }
        let font = NSFont.monospacedSystemFont(ofSize: 12, weight: .bold)

        let attr = NSMutableAttributedString()
        if !otps.isEmpty {
            let first = otps[0]
            let badgeText = "🔐 #81 [\(otps.count) OTP • \(first.remainingSec)s]"
            attr.append(NSAttributedString(string: badgeText, attributes: [
                .font: font,
                .foregroundColor: ThemeColor.papaya
            ]))
        } else if bridgeOnline {
            let dotColor = transportMode.contains("VPN") ? NSColor.systemPurple : ThemeColor.green
            attr.append(NSAttributedString(string: "🔐 #81 ", attributes: [
                .font: font,
                .foregroundColor: ThemeColor.textPri
            ]))
            attr.append(NSAttributedString(string: "●", attributes: [
                .font: font,
                .foregroundColor: dotColor
            ]))
        } else {
            attr.append(NSAttributedString(string: "🔐 #81 ○", attributes: [
                .font: font,
                .foregroundColor: ThemeColor.textSec
            ]))
        }
        btn.attributedTitle = attr
        btn.toolTip = bridgeOnline
            ? "Aayush OS V3.1 OTP Inbox (\(transportMode) • \(otps.count) active)"
            : "Aayush OS V3.1 OTP Inbox (Offline)"
    }

    private func setupPopover() {
        popover.behavior = .transient
        popover.animates = true
        let vc = NSViewController()
        popoverContentView = FlippedView(frame: NSRect(x: 0, y: 0, width: 380, height: 320))
        popoverContentView.wantsLayer = true
        popoverContentView.layer?.backgroundColor = ThemeColor.obsidian.cgColor
        vc.view = popoverContentView
        popover.contentViewController = vc
        popover.contentSize = NSSize(width: 380, height: 320)
    }

    @objc private func togglePopover(_ sender: AnyObject?) {
        if popover.isShown {
            popover.performClose(sender)
        } else {
            openPopover()
        }
    }

    private func openPopover() {
        guard let btn = statusItem.button else { return }
        rebuildUI()
        popover.show(relativeTo: btn.bounds, of: btn, preferredEdge: .minY)
        NSApp.activate(ignoringOtherApps: true)
    }

    // MARK: - Optional Floating Desktop OTP Panel

    private func setupFloatingPanel() {
        let screenFrame = NSScreen.main?.visibleFrame ?? NSRect(x: 0, y: 0, width: 1440, height: 900)
        let width: CGFloat = 350
        let height: CGFloat = 210
        let rect = NSRect(
            x: screenFrame.maxX - width - 20,
            y: screenFrame.maxY - height - 20,
            width: width,
            height: height
        )

        floatingPanel = NSPanel(
            contentRect: rect,
            styleMask: [.titled, .nonactivatingPanel, .utilityWindow, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        floatingPanel.isFloatingPanel = true
        floatingPanel.level = .floating
        floatingPanel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        floatingPanel.titleVisibility = .hidden
        floatingPanel.titlebarAppearsTransparent = true
        floatingPanel.isMovableByWindowBackground = true
        floatingPanel.backgroundColor = ThemeColor.obsidian
        floatingPanel.hasShadow = true
        floatingPanel.isOpaque = false

        panelContentView = FlippedView(frame: NSRect(x: 0, y: 0, width: width, height: height))
        panelContentView.wantsLayer = true
        panelContentView.layer?.backgroundColor = ThemeColor.obsidian.cgColor
        panelContentView.layer?.cornerRadius = 14
        panelContentView.layer?.borderWidth = 1.5
        panelContentView.layer?.borderColor = ThemeColor.papaya.cgColor
        floatingPanel.contentView = panelContentView
    }

    private func updateFloatingPanelVisibility() {
        guard let panel = floatingPanel, panelContentView != nil else { return }
        // Auto-hide when empty or when screen is locked or when disabled by user
        if !floatingPanelEnabled || otps.isEmpty || isMacScreenLocked() {
            if panel.isVisible {
                panel.orderOut(nil)
            }
            return
        }

        let desiredHeight: CGFloat = min(380, CGFloat(84 + otps.count * 108))
        var frame = panel.frame
        if abs(frame.height - desiredHeight) > 2 {
            frame.origin.y += (frame.height - desiredHeight)
            frame.size.height = desiredHeight
            panel.setFrame(frame, display: true, animate: true)
            panelContentView.frame = NSRect(x: 0, y: 0, width: frame.width, height: desiredHeight)
        }

        if !panel.isVisible {
            panel.orderFrontRegardless()
        }
    }

    // MARK: - Top-Right Interactive Notification Toast Banner

    private func setupToastBanner() {
        let screenFrame = NSScreen.main?.visibleFrame ?? NSRect(x: 0, y: 0, width: 1440, height: 900)
        let width: CGFloat = 360
        let height: CGFloat = 86
        let rect = NSRect(
            x: screenFrame.maxX - width - 18,
            y: screenFrame.maxY - height - 10,
            width: width,
            height: height
        )
        toastPanel = NSPanel(
            contentRect: rect,
            styleMask: [.borderless, .nonactivatingPanel],
            backing: .buffered,
            defer: true
        )
        toastPanel.isFloatingPanel = true
        toastPanel.level = .statusBar
        toastPanel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        toastPanel.backgroundColor = .clear
        toastPanel.isOpaque = false
        toastPanel.hasShadow = true
    }

    private func showToastBanner(for entry: OtpEntry) {
        toastHideWorkItem?.cancel()
        let width: CGFloat = 360
        let height: CGFloat = 86
        let container = FlippedView(frame: NSRect(x: 0, y: 0, width: width, height: height))
        container.wantsLayer = true
        container.layer?.backgroundColor = ThemeColor.carbon.cgColor
        container.layer?.cornerRadius = 12
        container.layer?.borderWidth = 1.5
        container.layer?.borderColor = ThemeColor.green.cgColor

        let titleLbl = makeLabel(
            "🔐 OTP ARRIVED // \(entry.app.uppercased())",
            size: 11,
            weight: .bold,
            color: ThemeColor.green,
            frame: NSRect(x: 14, y: 12, width: 220, height: 16)
        )
        container.addSubview(titleLbl)

        let subLbl = makeLabel(
            "Code hidden (••••••) • Rcvd \(entry.receivedTime) • TTL \(entry.remainingSec)s",
            size: 11,
            weight: .regular,
            color: ThemeColor.textPri,
            frame: NSRect(x: 14, y: 32, width: 230, height: 16)
        )
        container.addSubview(subLbl)

        let hintLbl = makeLabel(
            "Lock-screen safe • Click Copy or Menu Bar #81",
            size: 10,
            weight: .regular,
            color: ThemeColor.textSec,
            frame: NSRect(x: 14, y: 52, width: 230, height: 14)
        )
        container.addSubview(hintLbl)

        let copyBtn = makePillButton(
            title: "📋 COPY OTP",
            bg: ThemeColor.papaya,
            fg: ThemeColor.obsidian,
            frame: NSRect(x: 248, y: 18, width: 98, height: 26),
            tag: Int(entry.id & 0x7FFFFFFF),
            action: #selector(onToastCopyClicked(_:))
        )
        container.addSubview(copyBtn)

        let openBtn = makePillButton(
            title: "OPEN INBOX",
            bg: ThemeColor.elevated,
            fg: ThemeColor.textPri,
            frame: NSRect(x: 248, y: 48, width: 98, height: 22),
            tag: Int(entry.id & 0x7FFFFFFF),
            action: #selector(onToastOpenClicked(_:))
        )
        container.addSubview(openBtn)

        toastPanel.contentView = container
        toastPanel.orderFrontRegardless()

        let work = DispatchWorkItem { [weak self] in
            self?.toastPanel.orderOut(nil)
        }
        toastHideWorkItem = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.5, execute: work)
    }

    @objc private func onToastCopyClicked(_ sender: NSButton) {
        if let match = otps.first(where: { Int($0.id & 0x7FFFFFFF) == sender.tag }) ?? otps.first {
            copyOtp(id: match.id)
        }
        toastPanel.orderOut(nil)
    }

    @objc private func onToastOpenClicked(_ sender: NSButton) {
        toastPanel.orderOut(nil)
        openPopover()
    }

    // MARK: - UI Construction (Menu Bar Popover & Floating Desktop Panel)

    private func rebuildUI() {
        guard statusItem != nil, popoverContentView != nil, panelContentView != nil else { return }
        updateMenuBarTitle()
        rebuildPopoverView()
        rebuildFloatingPanelView()
        updateFloatingPanelVisibility()
    }

    private func rebuildPopoverView() {
        guard let root = popoverContentView else { return }
        root.subviews.forEach { $0.removeFromSuperview() }

        let width: CGFloat = 380
        let cardHeight: CGFloat = 114
        let listHeight = otps.isEmpty ? 95 : CGFloat(otps.count) * (cardHeight + 8)
        let totalHeight = max(255, min(520, 74 + listHeight + 76))

        popover.contentSize = NSSize(width: width, height: totalHeight)
        root.frame = NSRect(x: 0, y: 0, width: width, height: totalHeight)

        // Header
        let headerTitle = makeLabel(
            "AAYUSH OS V3.1 // MAC OTP INBOX",
            size: 11,
            weight: .bold,
            color: ThemeColor.papaya,
            frame: NSRect(x: 16, y: 14, width: 240, height: 16)
        )
        root.addSubview(headerTitle)

        let connColor = bridgeOnline
            ? (transportMode.contains("VPN") ? NSColor.systemPurple : ThemeColor.green)
            : ThemeColor.red
        let connString = bridgeOnline
            ? "● \(transportMode)" + (pixelIp.isEmpty ? "" : " • Pixel \(pixelIp)")
            : "○ BRIDGE OFFLINE (Retrying...)"
        let connSub = makeLabel(
            connString,
            size: 10.5,
            weight: .medium,
            color: connColor,
            frame: NSRect(x: 16, y: 32, width: 260, height: 15)
        )
        root.addSubview(connSub)

        // #81 Badge
        let badge = makeLabel(
            " #81 OTP ",
            size: 10,
            weight: .bold,
            color: ThemeColor.obsidian,
            frame: NSRect(x: width - 78, y: 16, width: 62, height: 22)
        )
        badge.alignment = .center
        badge.wantsLayer = true
        badge.layer?.backgroundColor = ThemeColor.papaya.cgColor
        badge.layer?.cornerRadius = 6
        root.addSubview(badge)

        let sepTop = NSView(frame: NSRect(x: 16, y: 56, width: width - 32, height: 1))
        sepTop.wantsLayer = true
        sepTop.layer?.backgroundColor = ThemeColor.border.cgColor
        root.addSubview(sepTop)

        var yOffset: CGFloat = 66
        if otps.isEmpty {
            let emptyBox = FlippedView(frame: NSRect(x: 16, y: yOffset, width: width - 32, height: 82))
            emptyBox.wantsLayer = true
            emptyBox.layer?.backgroundColor = ThemeColor.carbon.cgColor
            emptyBox.layer?.cornerRadius = 12
            emptyBox.layer?.borderWidth = 1
            emptyBox.layer?.borderColor = ThemeColor.border.cgColor

            let l1 = makeLabel(
                "No Active OTPs in Volatile Memory",
                size: 12,
                weight: .bold,
                color: ThemeColor.textPri,
                frame: NSRect(x: 14, y: 18, width: width - 60, height: 18)
            )
            let l2 = makeLabel(
                "Permitted Pixel 7 OTPs appear here & auto-purge on TTL expiry.\nZero disk storage • Strictly excluded from Dynamic Now Bar.",
                size: 10,
                weight: .regular,
                color: ThemeColor.textSec,
                frame: NSRect(x: 14, y: 40, width: width - 60, height: 32)
            )
            emptyBox.addSubview(l1)
            emptyBox.addSubview(l2)
            root.addSubview(emptyBox)
            yOffset += 92
        } else {
            for entry in otps {
                let card = buildOtpCardView(entry: entry, width: width - 32, compact: false)
                card.frame.origin = NSPoint(x: 16, y: yOffset)
                root.addSubview(card)
                yOffset += card.frame.height + 8
            }
        }

        // Footer Controls
        let footerY = totalHeight - 66
        let sepBot = NSView(frame: NSRect(x: 16, y: footerY - 8, width: width - 32, height: 1))
        sepBot.wantsLayer = true
        sepBot.layer?.backgroundColor = ThemeColor.border.cgColor
        root.addSubview(sepBot)

        let panelToggleTitle = floatingPanelEnabled ? "🪟 FLOATING PANEL: ON" : "🪟 FLOATING PANEL: OFF"
        let panelBtn = makePillButton(
            title: panelToggleTitle,
            bg: floatingPanelEnabled ? ThemeColor.green.withAlphaComponent(0.2) : ThemeColor.elevated,
            fg: floatingPanelEnabled ? ThemeColor.green : ThemeColor.textSec,
            frame: NSRect(x: 16, y: footerY, width: 158, height: 26),
            tag: 0,
            action: #selector(onToggleFloatingPanel(_:))
        )
        root.addSubview(panelBtn)

        let clearAllBtn = makePillButton(
            title: "🧹 CLEAR ALL",
            bg: ThemeColor.elevated,
            fg: otps.isEmpty ? ThemeColor.textSec : ThemeColor.red,
            frame: NSRect(x: 182, y: footerY, width: 108, height: 26),
            tag: 0,
            action: #selector(onClearAllClicked(_:))
        )
        root.addSubview(clearAllBtn)

        let quitBtn = makePillButton(
            title: "QUIT",
            bg: ThemeColor.elevated,
            fg: ThemeColor.textSec,
            frame: NSRect(x: 298, y: footerY, width: 66, height: 26),
            tag: 0,
            action: #selector(onQuitClicked(_:))
        )
        root.addSubview(quitBtn)

        let secHint = makeLabel(
            "🔒 RAM-Only • Hidden by default • Never shown in Pixel Now Bar",
            size: 9.5,
            weight: .regular,
            color: ThemeColor.textSec,
            frame: NSRect(x: 16, y: footerY + 32, width: width - 32, height: 14)
        )
        root.addSubview(secHint)
    }

    private func rebuildFloatingPanelView() {
        guard let root = panelContentView else { return }
        root.subviews.forEach { $0.removeFromSuperview() }
        if otps.isEmpty { return }

        let width: CGFloat = 350
        let headerLbl = makeLabel(
            "🔐 AAYUSH OS // FLOATING OTP PANEL (\(otps.count))",
            size: 10.5,
            weight: .bold,
            color: ThemeColor.papaya,
            frame: NSRect(x: 14, y: 12, width: 225, height: 16)
        )
        root.addSubview(headerLbl)

        let clearAllBtn = makePillButton(
            title: "CLEAR ALL",
            bg: ThemeColor.elevated,
            fg: ThemeColor.red,
            frame: NSRect(x: width - 102, y: 9, width: 88, height: 22),
            tag: 0,
            action: #selector(onClearAllClicked(_:))
        )
        root.addSubview(clearAllBtn)

        let subLbl = makeLabel(
            "Auto-hides when empty • Drag anywhere",
            size: 9.5,
            weight: .regular,
            color: ThemeColor.textSec,
            frame: NSRect(x: 14, y: 28, width: 220, height: 13)
        )
        root.addSubview(subLbl)

        var yOffset: CGFloat = 48
        for entry in otps.prefix(3) {
            let card = buildOtpCardView(entry: entry, width: width - 24, compact: true)
            card.frame.origin = NSPoint(x: 12, y: yOffset)
            root.addSubview(card)
            yOffset += card.frame.height + 6
        }
    }

    private func buildOtpCardView(entry: OtpEntry, width: CGFloat, compact: Bool) -> NSView {
        let height: CGFloat = compact ? 98 : 112
        let card = FlippedView(frame: NSRect(x: 0, y: 0, width: width, height: height))
        card.wantsLayer = true
        card.layer?.backgroundColor = ThemeColor.carbon.cgColor
        card.layer?.cornerRadius = 12
        card.layer?.borderWidth = 1.2

        let ratio = max(0.0, min(1.0, Double(entry.remainingSec) / Double(max(1, entry.ttl))))
        let urgencyColor: NSColor = entry.remainingSec <= 10
            ? ThemeColor.red
            : (entry.remainingSec <= 20 ? ThemeColor.papaya : ThemeColor.green)
        card.layer?.borderColor = urgencyColor.withAlphaComponent(0.65).cgColor

        // Top Row: App Name + Received Time + Countdown Badge
        let appLbl = makeLabel(
            "📱 \(entry.app)",
            size: 11.5,
            weight: .bold,
            color: ThemeColor.papaya,
            frame: NSRect(x: 12, y: 10, width: width - 145, height: 16)
        )
        card.addSubview(appLbl)

        let timeText = "Rcvd \(entry.receivedTime) • ⏱ \(entry.remainingSec)s"
        let timeLbl = makeLabel(
            timeText,
            size: 10.5,
            weight: .bold,
            color: urgencyColor,
            frame: NSRect(x: width - 140, y: 10, width: 128, height: 16)
        )
        timeLbl.alignment = .right
        card.addSubview(timeLbl)

        // Middle Row: Masked or Explicitly Revealed OTP Code
        let isRevealed = revealedIds.contains(entry.id) && !entry.masked
        let displayedCode = isRevealed
            ? entry.code.map { String($0) }.joined(separator: " ")
            : "• • • • • •"
        let codeLbl = makeLabel(
            displayedCode,
            size: compact ? 19 : 21,
            weight: .bold,
            color: isRevealed ? ThemeColor.green : ThemeColor.textPri,
            frame: NSRect(x: 12, y: 31, width: 175, height: 26)
        )
        card.addSubview(codeLbl)

        let privacyBadgeText = isRevealed ? "REVEALED IN RAM" : "HIDDEN BY DEFAULT"
        let privLbl = makeLabel(
            privacyBadgeText,
            size: 9.0,
            weight: .medium,
            color: ThemeColor.textSec,
            frame: NSRect(x: 12, y: 58, width: 150, height: 13)
        )
        card.addSubview(privLbl)

        // Action Buttons: COPY, REVEAL/HIDE, CLEAR
        let itemTag = Int(entry.id & 0x7FFFFFFF)
        let isCopied = copiedFeedbackIds.contains(entry.id)

        let btnY: CGFloat = compact ? 64 : 74
        let copyBtn = makePillButton(
            title: isCopied ? "✓ COPIED!" : "📋 COPY OTP",
            bg: isCopied ? ThemeColor.green : ThemeColor.papaya,
            fg: ThemeColor.obsidian,
            frame: NSRect(x: 12, y: btnY, width: 106, height: 25),
            tag: itemTag,
            action: #selector(onCopyOtpClicked(_:))
        )
        card.addSubview(copyBtn)

        let revealBtn = makePillButton(
            title: isRevealed ? "🙈 HIDE" : "👁 REVEAL",
            bg: ThemeColor.elevated,
            fg: ThemeColor.textPri,
            frame: NSRect(x: 124, y: btnY, width: 88, height: 25),
            tag: itemTag,
            action: #selector(onRevealOtpClicked(_:))
        )
        card.addSubview(revealBtn)

        let clearBtn = makePillButton(
            title: "✕ CLEAR",
            bg: ThemeColor.elevated,
            fg: ThemeColor.red,
            frame: NSRect(x: 218, y: btnY, width: 76, height: 25),
            tag: itemTag,
            action: #selector(onClearSingleOtpClicked(_:))
        )
        card.addSubview(clearBtn)

        // Expiry Countdown Progress Bar along bottom edge
        let barBg = NSView(frame: NSRect(x: 12, y: height - 6, width: width - 24, height: 3))
        barBg.wantsLayer = true
        barBg.layer?.backgroundColor = ThemeColor.border.cgColor
        barBg.layer?.cornerRadius = 1.5
        card.addSubview(barBg)

        let barFillWidth = max(4.0, (width - 24) * CGFloat(ratio))
        let barFill = NSView(frame: NSRect(x: 12, y: height - 6, width: barFillWidth, height: 3))
        barFill.wantsLayer = true
        barFill.layer?.backgroundColor = urgencyColor.cgColor
        barFill.layer?.cornerRadius = 1.5
        card.addSubview(barFill)

        return card
    }

    private func makeLabel(_ text: String, size: CGFloat, weight: NSFont.Weight, color: NSColor, frame: NSRect) -> NSTextField {
        let lbl = NSTextField(frame: frame)
        lbl.stringValue = text
        lbl.isEditable = false
        lbl.isBordered = false
        lbl.drawsBackground = false
        lbl.font = NSFont.monospacedSystemFont(ofSize: size, weight: weight)
        lbl.textColor = color
        lbl.lineBreakMode = .byTruncatingTail
        return lbl
    }

    private func makePillButton(title: String, bg: NSColor, fg: NSColor, frame: NSRect, tag: Int, action: Selector) -> NSButton {
        let btn = NSButton(frame: frame)
        btn.title = title
        btn.bezelStyle = .regularSquare
        btn.isBordered = false
        btn.wantsLayer = true
        btn.layer?.backgroundColor = bg.cgColor
        btn.layer?.cornerRadius = frame.height / 2.0
        btn.layer?.borderWidth = 1
        btn.layer?.borderColor = ThemeColor.border.cgColor
        btn.tag = tag
        btn.target = self
        btn.action = action

        let attr = NSAttributedString(string: title, attributes: [
            .font: NSFont.monospacedSystemFont(ofSize: 10.5, weight: .bold),
            .foregroundColor: fg
        ])
        btn.attributedTitle = attr
        return btn
    }

    // MARK: - Button Actions (Copy, Reveal/Hide, Clear Single, Clear All)

    private func entryForTag(_ tag: Int) -> OtpEntry? {
        return otps.first(where: { Int($0.id & 0x7FFFFFFF) == tag })
    }

    @objc private func onCopyOtpClicked(_ sender: NSButton) {
        guard let entry = entryForTag(sender.tag) else { return }
        copyOtp(id: entry.id)
    }

    @objc private func onRevealOtpClicked(_ sender: NSButton) {
        guard let entry = entryForTag(sender.tag) else { return }
        if revealedIds.contains(entry.id) {
            revealedIds.remove(entry.id)
        } else {
            revealedIds.insert(entry.id)
        }
        fetchOtpSnapshotImmediate()
    }

    @objc private func onClearSingleOtpClicked(_ sender: NSButton) {
        guard let entry = entryForTag(sender.tag) else { return }
        clearOtp(id: entry.id)
    }

    @objc private func onClearAllClicked(_ sender: NSButton) {
        clearOtp(id: nil)
    }

    @objc private func onToggleFloatingPanel(_ sender: NSButton) {
        floatingPanelEnabled.toggle()
    }

    @objc private func onQuitClicked(_ sender: NSButton) {
        NSApp.terminate(nil)
    }

    // MARK: - Backend API Calls (POST /otp/copy, POST /otp/clear, GET /otp)

    private func copyOtp(id: Int64) {
        guard let req = makeSignedRequest(path: "/otp/copy", method: "POST", jsonBody: ["id": id], timeout: 3.0) else { return }
        URLSession.shared.dataTask(with: req) { [weak self] _, resp, _ in
            guard let self = self else { return }
            if let http = resp as? HTTPURLResponse, http.statusCode == 200 {
                DispatchQueue.main.async {
                    self.copiedFeedbackIds.insert(id)
                    self.rebuildUI()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
                        self.copiedFeedbackIds.remove(id)
                        self.rebuildUI()
                    }
                }
            }
        }.resume()
    }

    private func clearOtp(id: Int64?) {
        var body: [String: Any] = [:]
        if let specificId = id {
            body["id"] = specificId
            revealedIds.remove(specificId)
        } else {
            revealedIds.removeAll()
        }
        guard let req = makeSignedRequest(path: "/otp/clear", method: "POST", jsonBody: body, timeout: 3.0) else { return }
        URLSession.shared.dataTask(with: req) { [weak self] _, _, _ in
            self?.fetchOtpSnapshotImmediate()
        }.resume()
    }

    // MARK: - 1-Second Local Expiry Countdown Timer

    private func startCountdownTimer() {
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            guard !self.otps.isEmpty else { return }
            let nowSec = Int64(Date().timeIntervalSince1970)
            var changed = false
            var updated: [OtpEntry] = []
            for var item in self.otps {
                let rem = Int(item.expiresAt - nowSec)
                if rem > 0 {
                    if item.remainingSec != rem {
                        item.remainingSec = rem
                        changed = true
                    }
                    updated.append(item)
                } else {
                    self.revealedIds.remove(item.id)
                    changed = true
                }
            }
            if changed {
                self.otps = updated
                self.rebuildUI()
            }
        }
    }

    // MARK: - Push-Based Long-Polling with Exponential Backoff

    private func buildOtpQueryPath(waitSeq: Int64?, timeoutSec: Int) -> String {
        var parts: [String] = []
        if let seq = waitSeq, seq >= 0 {
            parts.append("wait_seq=\(seq)")
            parts.append("timeout=\(timeoutSec)")
        }
        if !revealedIds.isEmpty {
            let idsStr = revealedIds.map { String($0) }.joined(separator: ",")
            parts.append("reveal_ids=\(idsStr)")
        }
        if parts.isEmpty {
            return "/otp"
        }
        return "/otp?" + parts.joined(separator: "&")
    }

    private func fetchOtpSnapshotImmediate() {
        let path = buildOtpQueryPath(waitSeq: nil, timeoutSec: 0)
        guard let req = makeSignedRequest(path: path, method: "GET", timeout: 3.0) else { return }
        URLSession.shared.dataTask(with: req) { [weak self] data, resp, _ in
            guard let self = self, let data = data,
                  let http = resp as? HTTPURLResponse, http.statusCode == 200 else { return }
            self.handleOtpResponseData(data)
        }.resume()
    }

    private func startPushLongPollLoop() {
        pollQueue.async { [weak self] in
            var backoffSec: Double = 1.0
            while let self = self, !self.isShuttingDown {
                let sema = DispatchSemaphore(value: 0)
                let waitSeq = self.currentSeq >= 0 ? self.currentSeq : nil
                let path = self.buildOtpQueryPath(waitSeq: waitSeq, timeoutSec: 12)

                guard let req = self.makeSignedRequest(path: path, method: "GET", timeout: 16.0) else {
                    Thread.sleep(forTimeInterval: 2.0)
                    continue
                }

                var requestSucceeded = false
                URLSession.shared.dataTask(with: req) { data, resp, err in
                    defer { sema.signal() }
                    if err == nil, let http = resp as? HTTPURLResponse, http.statusCode == 200, let data = data {
                        requestSucceeded = true
                        self.handleOtpResponseData(data)
                    }
                }.resume()

                sema.wait()

                if requestSucceeded {
                    backoffSec = 1.0
                } else {
                    DispatchQueue.main.async {
                        if self.bridgeOnline {
                            self.bridgeOnline = false
                            self.transportMode = "OFFLINE"
                            self.otps.removeAll()
                            self.rebuildUI()
                        }
                    }
                    Thread.sleep(forTimeInterval: backoffSec)
                    backoffSec = min(10.0, backoffSec * 2.0)
                }
            }
        }
    }

    private func handleOtpResponseData(_ data: Data) {
        guard let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
              let ok = json["ok"] as? Bool, ok else { return }

        let newSeq = (json["seq"] as? Int64) ?? Int64(json["seq"] as? Int ?? 0)
        let transport = (json["transport"] as? String) ?? "DIRECT LAN"
        let pxIp = (json["pixel_ip"] as? String) ?? ""
        let rawList = (json["otps"] as? [[String: Any]]) ?? []

        var parsed: [OtpEntry] = []
        for item in rawList {
            let id = (item["id"] as? Int64) ?? Int64(item["id"] as? Int ?? 0)
            let code = (item["code"] as? String) ?? "••••••"
            let maskedCode = (item["masked_code"] as? String) ?? "••••••"
            let masked = (item["masked"] as? Bool) ?? true
            let codeLen = (item["code_len"] as? Int) ?? 6
            let app = (item["app"] as? String) ?? "Android App"
            let pkg = (item["pkg"] as? String) ?? ""
            let title = (item["title"] as? String) ?? "Verification Code"
            let rcvdAt = (item["received_at"] as? Int64) ?? Int64(item["received_at"] as? Int ?? 0)
            let rcvdTime = (item["received_time"] as? String) ?? ""
            let expAt = (item["expires_at"] as? Int64) ?? Int64(item["expires_at"] as? Int ?? 0)
            let remSec = (item["remaining_sec"] as? Int) ?? 0
            let ttl = (item["ttl"] as? Int) ?? 60

            parsed.append(OtpEntry(
                id: id,
                code: code,
                maskedCode: maskedCode,
                masked: masked,
                codeLen: codeLen,
                app: app,
                pkg: pkg,
                title: title,
                receivedAt: rcvdAt,
                receivedTime: rcvdTime,
                expiresAt: expAt,
                remainingSec: remSec,
                ttl: ttl
            ))
        }

        DispatchQueue.main.async {
            let hadRevealNeedingRefetch = parsed.contains { self.revealedIds.contains($0.id) && $0.masked }
            self.currentSeq = newSeq
            self.bridgeOnline = true
            self.transportMode = transport
            self.pixelIp = pxIp
            self.otps = parsed

            // Trigger arrival notification for any brand-new OTP ID
            for entry in parsed {
                if !self.notifiedIds.contains(entry.id) {
                    self.notifiedIds.insert(entry.id)
                    self.postArrivalNotification(for: entry)
                }
            }

            self.rebuildUI()

            if hadRevealNeedingRefetch {
                self.fetchOtpSnapshotImmediate()
            }
        }
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
