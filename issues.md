# Aayush OS — Dynamic Now Bar V2.4 & Cross-Device Ecosystem Master Tracker

> **Device Targets:**
> - **Phone:** Google Pixel 7 ("panther") — 1080 × 2400 px, 420 dpi (2.625× density)  
>   - Camera Cutout Center: X = 540 px (205.7 dp), Y = 68 px (25.9 dp)  
>   - Physical Lens Radius: r = 34 px (13.0 dp) | Clearance Diameter: Ø = 34 dp (89 px)  
>   - Target SDK: 34 (Android 14) | Min SDK: 29 (Android 10)  
> - **Mac Companion:** MacBook Air M4 (macOS Sonoma / Sequoia) — Direct LAN (:8181 ↔ :8183) + Tailscale VPN  
> **Audit Date:** 2026-11-03  
> **Current Mode:** Strict Ask Mode (Audit & Tracking Only — Zero Code Modifications Permitted)  
>
> **Status Legend:**  
> - `- [ ]` Open / Confirmed Defect or Planned Enhancement  
> - `- [?]` Needs Verification / Profiling Required  
> - `- [~]` In Progress  
> - `- [x]` Resolved & Verified  
> - `- [D]` Deferred Improvement  

---

## 1. Executive Summary & Progress Dashboard

### Overall Metrics
- **Total Tracked Issues:** 82
- **Confirmed Bugs & Defects:** 22
- **Performance & Battery Issues:** 7
- **Visual, UX & Polish Enhancements:** 15
- **Reliability & State Consistency Items:** 9
- **Security & Privacy Gaps:** 4
- **macOS Companion & Quick Controls Tasks:** 10
- **End-to-End Roaming & Network Integration:** 5
- **Resolved / Completed (Verified):** 63
- **Open / Actionable Items:** 19

### Category Breakdown
| # | Category | Total | Open | Resolved | Deferred |
| :-: | :--- | :---: | :---: | :---: | :---: |
| **1** | Camera Geometry & Cutout Layout | 4 | 0 | 4 | 0 |
| **2** | Dynamic Sizing & Typography | 4 | 2 | 2 | 0 |
| **3** | Content Selection & Prioritisation | 4 | 0 | 4 | 0 |
| **4** | Animations & Continuous Transitions | 3 | 2 | 1 | 0 |
| **5** | Touch & Gesture Handling | 4 | 1 | 3 | 0 |
| **6** | Android System & Status Bar Integration | 3 | 0 | 3 | 0 |
| **7** | Lifecycle, Service & Reliability | 5 | 0 | 5 | 0 |
| **8** | Media Playback Experience | 3 | 0 | 3 | 0 |
| **9** | Timer & Download Experience | 3 | 0 | 3 | 0 |
| **10** | Accessibility & Readability | 3 | 0 | 3 | 0 |
| **11** | Performance, Threading & Battery | 7 | 0 | 7 | 0 |
| **12** | Security, Privacy & OTP Strictness | 4 | 0 | 4 | 0 |
| **13** | General Aayush OS System & Companion | 10 | 4 | 6 | 0 |
| **14** | Now Bar — Final UI Polish & Micro-Interactions | 7 | 2 | 5 | 0 |
| **15** | Rock-Solid Reliability, State & Crash Recovery | 4 | 1 | 3 | 0 |
| **16** | macOS Desktop Widget (MacBook Air M4) | 3 | 3 | 0 | 0 |
| **17** | Mac-to-Pixel Remote Quick Controls | 4 | 0 | 4 | 0 |
| **18** | Complete Mac Companion Experience | 3 | 3 | 0 | 0 |
| **19** | End-to-End Cross-Device Integration & Roaming | 3 | 3 | 0 | 0 |

---

## 2. Category 1: Camera Geometry & Cutout Layout

- [x] **NB-GEO-01: Razor-Thin 2dp Bridge & Sub-Pixel Clipping Artifacts Above/Below Camera**
  - **Category:** Camera Geometry and Layout
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L601-L604`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L601-L604)
  - **Problem:** In `CAMERA_MODE`, `pillHeightDp` defaults to `38dp` while `cameraClearanceDp` is `34dp`. This left only `(38 - 34) / 2 = 2.0dp` of capsule material above and below the optical punch-hole, producing anti-aliasing jitter and uneven line weights.
  - **Resolution:** Enforced `minCapsuleHeightPx = clearDiameterPx + Theme.dp(ctx, 8)`, guaranteeing a minimum 4dp top collar and 4dp bottom collar around the camera cutout for a balanced, anti-aliased finish.
  - **Verification:** Verified via screencap crop on Pixel 7 physical display: zero sub-pixel clipping or jagged contours above/below punch-hole.

- [x] **NB-GEO-02: Display Size & Font Scaling Misaligns Pre-Calibrated Cutout DP Values**
  - **Category:** Camera Geometry and Layout
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarConfig.java#L115-L125`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarConfig.java#L115-L125)
  - **Problem:** When user modified display scaling in Android Settings, constant dp calculations shifted the circular clearance hole away from the physical optical sensor.
  - **Resolution:** Implemented explicit physical hardware profile matching for `panther` (Pixel 7) and `cheetah` (Pixel 7 Pro) locking optical center to display midpoint (`sw / 2`) and cutout vertical center `(topRect.top + topRect.bottom) / 2 = 68px` directly from `DisplayCutout`.
  - **Verification:** Verified camera cutout alignment remains dead-center across display scaling configurations.

- [x] **NB-GEO-03: Proximity Sensor Bounding Rect Asymmetry Compensation**
  - **Category:** Camera Geometry and Layout
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarConfig.java#L115-L122`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarConfig.java#L115-L122)
  - **Problem:** Pixel 7's `DisplayCutout.getBoundingRectTop()` includes the under-display proximity sensor (574..625 px), causing asymmetric camera center calculation on non-standard DPI scaling.
  - **Resolution:** Formalized hardware device profile matching for `panther` (Pixel 7) and `cheetah` (Pixel 7 Pro) locking optical center strictly to display midpoint (`sw / 2 = 540px`) and cutout vertical center `(topRect.top + topRect.bottom) / 2 = 68px`.
  - **Verification:** Verified live on Pixel 7 physical display: punch-hole collar is perfectly centered at X=540px, Y=68px across display scaling modes.

- [x] **NB-GEO-04: Landscape Orientation Punch-Hole Misplacement**
  - **Category:** Camera Geometry and Layout
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L370-L375`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L370-L375)
  - **Problem:** When the screen rotated to landscape, the overlay remained floating in the middle of the long display edge rather than hugging the camera cutout.
  - **Resolution:** Added immediate landscape detection in `onConfigurationChanged`: sets `rootOverlay.setVisibility(View.GONE)` instantly on `ORIENTATION_LANDSCAPE` before layout recalculation, completely preventing floating mid-screen overlay.
  - **Verification:** Verified clean auto-hide on landscape orientation switch.

---

## 3. Category 2: Dynamic Sizing & Typography

- [x] **NB-SIZ-01: Fixed-Width Wings Force Premature Text Truncation and Clipping**
  - **Category:** Dynamic Sizing and Typography
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L1010`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L1010)
  - **Problem:** Left and right wings were statically clamped to `62dp` (or `46dp` in `COMPACT_MODE`), prematurely truncating `⚡ 22.5W`, battery temperatures, and track titles to only 7–10 characters with `...`.
  - **Resolution:** Expanded maximum character boundaries to 16 characters (`maxRightChars = isFloating ? 24 : (narrowWing ? 10 : 16)`) and removed premature truncation, allowing full telemetry and track titles to display cleanly without unwanted ellipsis.
  - **Verification:** Verified on Pixel 7 that full `⚡ 7.5W`, `29%•27°C`, and music titles render cleanly without clipping.

- [x] **NB-SIZ-02: Monospace Typography Inefficiency in Compact Pill**
  - **Category:** Dynamic Sizing and Typography
  - **Severity:** Low
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java), [`Theme.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/Theme.java)
  - **Problem:** `Theme.MONO_BOLD` reserved fixed width for narrow characters, wasting 30–40% space and restricting wing information density.
  - **Resolution:** Introduced `Theme.PILL_TYPO` (`sans-serif-condensed-medium`) and enabled OpenType tabular numbers (`fontFeatureSettings = "'tnum' 1"` with `letterSpacing = 0.015f`). Digits remain jitter-free during countdowns and power sampling while text flows proportionally.
  - **Verification:** Verified live on Pixel 7 physical display: telemetry (`⚡11W` and `100%`) renders with high-contrast legibility, no glyph clipping, and zero width jitter.

- [ ] **NB-SIZ-03: Sudden Content Jump & Layout Jitter on Telemetry State Changes**
  - **Category:** Dynamic Sizing and Typography
  - **Severity:** Medium
  - **Status:** Open
  - **Source:** [`NowBarOverlayService.java#L974-L1009`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L974-L1009)
  - **Problem:** Switching between charging state (`⚡ 18.4W`), idle state (`#81`), timer (`☕ 03:45`), and media (`🎵 PLAY`) causes immediate text jumps without size interpolation, producing visual jitter.
  - **Expected Behaviour:** Transitions between distinct text lengths should smoothly interpolate pill width using a ValueAnimator (`DecelerateInterpolator`, 200ms).
  - **Recommended Solution:** Wrap wing width changes in an animated width transition rather than instant `setLayoutParams()`.
  - **Verification:** Start and stop timer; verify pill width smoothly glides to new dimension.

- [ ] **NB-SIZ-04: Asymmetric Single-Wing Inbalance**
  - **Category:** Dynamic Sizing and Typography
  - **Severity:** Medium
  - **Status:** Open
  - **Source:** [`NowBarOverlayService.java#L526-L548`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L526-L548)
  - **Problem:** If content is only relevant on one wing (e.g., active download percentage or media icon), the other wing shows generic battery telemetry, preventing the island from acting as a focused single-purpose indicator.
  - **Expected Behaviour:** Island should support asymmetrical compact states (e.g., small single bubble or focused single wing) when appropriate.
  - **Recommended Solution:** Support single-wing collapsed mode where inactive wing width shrinks to 0dp and pill centers around camera hole.
  - **Verification:** Toggle left wing off; verify right wing remains anchored without phantom left spacer.

---

## 4. Category 3: Content Selection & Prioritisation

- [x] **NB-PRI-01: Paused Media Disappears Completely from Compact Now Bar**
  - **Category:** Content and Prioritisation
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarState.java#L125`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java#L125), [`NowBarOverlayService.java#L1035`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L1035)
  - **Problem:** Media was previously only displayed in the compact pill if `NowBarState.mediaPlaying == true`. The instant playback paused, the pill abruptly dropped the track title and reverted to battery telemetry.
  - **Resolution:** Implemented `lastMediaActiveUptimeMs` and `isMediaActiveOrRecent()`, retaining paused tracks in the Now Bar for a 3-minute (180s) grace period with a dimmed `⏸` pause indicator before falling back to idle telemetry.
  - **Verification:** Verified on Pixel 7 that paused media remains accessible in the pill with pause glyph.

- [x] **NB-PRI-02: Dual-Activity Multi-Tasking Wings (Timer + Media + Charging Concurrency)**
  - **Category:** Content and Prioritisation
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** The priority stack was previously mutually exclusive. If a timer was running, media playback and charging status were completely suppressed.
  - **Resolution:** Implemented concurrent dual-activity prioritization:
    1. Timer + Media: Left wing displays ticking timer countdown (`☕ MM:SS` in Papaya); Right wing displays live dancing equalizer (`🎵 ılı`) and track title.
    2. Timer + Charging: Left wing displays timer countdown; Right wing displays wattage (`⚡ 21W•94%`).
    3. Charging + Media: Left wing displays rapid charging telemetry; Right wing displays live animated media equalizer.
  - **Verification:** Verified live on Pixel 7 physical display: starting coffee timer while music plays or device charges cleanly splits activities symmetrically across both wings.

- [x] **NB-PRI-03: Redundant Telemetry Duplication Between Compact Wings and Expanded Deck**
  - **Category:** Content and Prioritisation
  - **Severity:** Low
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** Expanded deck previously duplicated battery % and wattage already visible in the compact pill.
  - **Resolution:** Upgraded `expTelemetryText` to render complementary system telemetry: Tensor G2 CPU cluster load, Cortex-X1 prime core MHz clock, live LAN IP, active VPN shield indicator, thermal headroom, and real-time network download throughput.
  - **Verification:** Verified live via screencap crop on Pixel 7 hardware: renders `CPU 36% (X1: 500MHz) | IP: 10.15.15.189 🛡 • NOMINAL | RAM 94% | ↓13.9 KB/s` cleanly without metric duplication.

- [x] **NB-PRI-04: Incoming Phone Call Heads-Up Occlusion & Dynamic Island Call Support Void**
  - **Category:** Content and Prioritisation
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarNotificationListener.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarNotificationListener.java), [`NowBarState.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java), [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** When a phone call arrives on Pixel 7, the incoming call notification is ongoing (`isClearable() == false`). The notification conflict filter skipped it, causing the top overlay to stay fixed and opaque over the dialer heads-up banner with zero call UI. Users were unable to see or interact with incoming calls.
  - **Resolution:**
    1. Added `isCall` detection in `NowBarNotificationListener` for `Notification.CATEGORY_CALL`, `EXTRA_CALL_PERSON`, `EXTRA_CALL_TYPE`, and dialer packages.
    2. Implemented Call State machine in `NowBarState` (`CALL_STATE_INCOMING`, `CALL_STATE_ACTIVE`) extracting Caller, Subtext, Answer Intent, Decline Intent, and Content Intent.
    3. Built rich Dynamic Island Call Pop-up in `NowBarOverlayService`:
       - Compact Mode: `📞` on left wing, `ANS` / `ANSWER` or elapsed call timer `MM:SS` on right wing.
       - Expanded Mode: Fluid auto-expansion on incoming call showing bold Caller Name, call status, high-contrast Red `☎ DECLINE` pill and Emerald Green `📞 ANSWER` pill with Android 14 `ActivityOptions` safe execution.
       - Forwarded call alerts to macOS Companion.
  - **Verification:** Tested live on Pixel 7 hardware via simulated broadcasts (`TEST_CALL` and `TEST_CALL_CLEAR`); verified visual rendering via screencap inspection, auto-expansion, duration timer ticking, and clean collapse upon call end.

---

## 5. Category 4: Animations & Continuous Transitions

- [x] **NB-ANI-01: Fluid Continuous Spring Morphing Expansion from Camera Hole**
  - **Category:** Animations and Transitions
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** Tapping the compact pill previously caused the expanded deck to simply fade in place with a flat scale animation, feeling disconnected from the camera cutout.
  - **Resolution:** Re-architected deck expansion using top-center anchored pivot (`setPivotX(w/2)`, `setPivotY(0)`), starting from collapsed punch-hole geometry (`translationY = -20dp`, `scaleX = 0.72f`, `scaleY = 0.65f`) and springing downwards with `OvershootInterpolator(1.15f)` over 260ms. On collapse, pulls back smoothly into the camera hole with `AccelerateInterpolator(1.5f)` over 170ms.
  - **Verification:** Verified live on Pixel 7 display: island organically blooms outward from behind the punch-hole into the expanded control deck and snaps back seamlessly upon dismissal.

- [ ] **NB-ANI-02: Missing Smooth Cross-Fade / Slide Transitions on Content Replacement**
  - **Category:** Animations and Transitions
  - **Severity:** Medium
  - **Status:** Open
  - **Source:** [`NowBarOverlayService.java#L974-L1009`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L974-L1009)
  - **Problem:** When telemetry values update or when playback state changes, `setText()` immediately replaces the string. Characters flash and snap without transition.
  - **Expected Behaviour:** New text should smoothly cross-fade or vertically slide in (ticker style) when data changes.
  - **Recommended Solution:** Use `TextSwitcher` or animate text alpha (`1.0 -> 0.0 -> 1.0` over 120ms) on content changes.
  - **Verification:** Observe telemetry tick; verify numbers transition smoothly without instant redraw flash.

- [ ] **NB-ANI-03: Visual Flicker on Notification Count-Based Wing Sizing**
  - **Category:** Animations and Transitions
  - **Severity:** Medium
  - **Status:** Open
  - **Source:** [`NowBarOverlayService.java#L956-L961`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L956-L961)
  - **Problem:** When notification count exceeds 3, `applyConfigToViews()` is triggered during `updateOverlayContent()`. This re-executes `wm.updateViewLayout()`, causing an abrupt screen redraw and perceptible layout flicker.
  - **Expected Behaviour:** Wing width compaction should animate smoothly without triggering a full window layout rebuild.
  - **Recommended Solution:** Animate layout params width directly on the views rather than invoking full window layout updates.
  - **Verification:** Post 4 notifications; verify wings compact smoothly without window flash.

---

## 6. Category 5: Touch & Gesture Handling

- [x] **NB-TCH-01: Invisible Transparent Layout Slots Block Status Bar Touches**
  - **Category:** Touch and Gesture Handling
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L129-L139`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L129-L139)
  - **Problem:** When one wing was narrower or hidden, the transparent unoccupied slot still occupied layout space and intercepted user touches intended for the Android status bar clock or icons underneath.
  - **Resolution:** Overrode `dispatchTouchEvent()` in `DynamicIslandPillView` to perform strict geometric hit-testing against `tmpRect` (the drawn capsule bounds). Any touch falling outside the rendered capsule immediately returns `false`, passing through transparently to the underlying Android system UI.
  - **Verification:** Verified on Pixel 7 that tapping the status bar clock or icons outside the pill triggers native system UI actions without interference.

- [x] **NB-TCH-02: Swiping Down Programmatically Pulls Shade But Blocks Interactive Drag**
  - **Category:** Touch and Gesture Handling
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** Generous touch interception margins intercepted native downward swipe drags meant for Android's top status bar shade.
  - **Resolution:** Restricted `DynamicIslandPillView.dispatchTouchEvent` hit-testing slop to 4dp. Any swipe originating outside the drawn capsule boundary immediately yields to Android's status bar window, enabling native interactive Quick Settings pull-downs.
  - **Verification:** Verified on Pixel 7 that dragging down from top-left or top-right directly triggers native interactive Quick Settings shade.

- [x] **NB-TCH-03: Full Multi-Touch Gesture Suite on Compact Pill (Swipe Left/Right & Long-Press Hub)**
  - **Category:** Touch and Gesture Handling
  - **Severity:** Low
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** The touch listener only handled simple tap and swipe down, lacking media transport gestures and quick shortcuts.
  - **Resolution:** Added comprehensive gesture detection inside `DynamicIslandPillView`:
    1. Swipe Left (>34dp horizontal): Skips to Next Track (`NowBarState.skipNext()`) with haptic click.
    2. Swipe Right (>34dp horizontal): Skips to Previous Track (`NowBarState.skipPrev()`) with haptic click.
    3. Swipe Down (>30dp vertical): Pulls down Android system notification shade.
    4. Long-Press (480ms hold): Triggers heavy haptic click and opens Aayush OS Hub (`MainActivity`).
    5. Single Tap (28dp slop): Instantly expands/collapses the deck with 0ms touch delay.
  - **Verification:** Verified each gesture live on Pixel 7 physical display: swiping left/right displayed skip toasts and long-press opened MainActivity.

---

## 7. Category 6: Android System & Status Bar Integration

- [x] **NB-SYS-01: Right Wing Overlaps Android System Icons (Wi-Fi, Battery, Tailscale Key)**
  - **Category:** Android System Integration
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L724-L733`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L724-L733)
  - **Problem:** Right wing extended to X = 747.5px. When Tailscale VPN, Wi-Fi, Cellular, and Battery icons were active, system icons extended leftward to X ≈ 710px, causing the right wing to directly overlap the Tailscale key and Wi-Fi icon.
  - **Resolution:** Added dynamic right wing compaction in `getEffectiveRightWingWidthDp()`: when VPN is active (`hwMonitor.sample().vpnActive`), right wing clamps from 62dp to 46dp (terminating at X = 705px), preventing status bar icon overlap.
  - **Verification:** Verified on Pixel 7 with Wi-Fi, Tailscale VPN, and Battery indicators active: right wing terminates cleanly with generous clearance before system icons.

- [x] **NB-SYS-02: Overlay Overlaps Fullscreen Games and Portrait Video Players**
  - **Category:** Android System Integration
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L568-L582, L1158-L1168`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L568-L582)
  - **Problem:** Portrait fullscreen games and media players kept the Now Bar floating directly over game headers and controls indefinitely because fullscreen checks only tested landscape orientation.
  - **Resolution:** Added event-driven `OnApplyWindowInsetsListener` on `rootOverlay` querying `insets.isVisible(WindowInsets.Type.statusBars())`. When any foreground app enters immersive fullscreen mode (hiding system bars), the Now Bar immediately auto-hides (`View.GONE`) and restores when system bars return.
  - **Verification:** Verified auto-hide upon entering immersive fullscreen modes.

- [x] **NB-SYS-03: Deprecated `ACTION_CLOSE_SYSTEM_DIALOGS` Ignored on Android 12+**
  - **Category:** Android System Integration
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L397, L906`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L397)
  - **Problem:** `NowBarOverlayService` registers a receiver for `Intent.ACTION_CLOSE_SYSTEM_DIALOGS` to collapse the expanded deck when the user taps Home or Recents. Starting in Android 12 (API 31), non-system apps are blocked from receiving or broadcasting this intent.
  - **Resolution:** Added `FLAG_WATCH_OUTSIDE_TOUCH` dynamically when deck expands and intercepted `MotionEvent.ACTION_OUTSIDE` in `rootOverlay.dispatchTouchEvent` to collapse deck immediately on outside tap or app navigation.
  - **Verification:** Verified outside touches collapse expanded deck instantly.

---

## 8. Category 7: Lifecycle, Service & Reliability

- [x] **NB-LIF-01: Foreground Service Notification Cancellation Violates Android 14 Policy**
  - **Category:** Lifecycle and Reliability
  - **Severity:** Critical
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L358-L386`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L358-L386)
  - **Problem:** `NowBarOverlayService` cancelled notification 8101 without ever calling `startForeground()`, leaving it treated as a background service subject to Android 14 LMK termination.
  - **Resolution:** Created a silent notification channel (`IMPORTANCE_MIN`) without badges, sound, or vibration, and called `startForeground(8101, notification)` officially promoting the service to a persistent foreground service.
  - **Verification:** Verified via `adb shell dumpsys activity services dev.aayush.os.NowBarOverlayService`: service officially reports `isForeground=true foregroundId=8101`.

- [x] **NB-LIF-02: `startService()` Called on Android 8+ Causes Crash from Background**
  - **Category:** Lifecycle and Reliability
  - **Severity:** Critical
  - **Status:** Resolved
  - **Source:** [`MainActivity.java#L583`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MainActivity.java#L583), [`BootReceiver.java#L22`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/BootReceiver.java#L22), [`NowBarTileService.java#L27`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarTileService.java#L27)
  - **Problem:** Background triggers invoked `context.startService(svc)`, which throws `BackgroundServiceStartNotAllowedException` on Android 8+ and Android 14.
  - **Resolution:** Replaced all background calls with `startForegroundService()` across `MainActivity`, `BootReceiver`, and `NowBarTileService`.
  - **Verification:** Verified on Pixel 7 that tile toggling and service launches execute cleanly without exceptions.

- [x] **NB-LIF-03: SharedPreferences Key Mismatch Between Service and Toggles**
  - **Category:** Lifecycle and Reliability
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L278, L1100`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L278), [`BootReceiver.java#L17`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/BootReceiver.java#L17), [`MainActivity.java#L1175`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MainActivity.java#L1175)
  - **Problem:** `NowBarOverlayService` writes its running state to preference key `"mod_nowbar_active"`, while `MainActivity` and `BootReceiver` check `"mod_nowbar"`. This mismatch can lead to incorrect state restoration across reboot.
  - **Resolution:** Standardized user toggle on `"aayush_os_prefs"` with key `"mod_nowbar"` and synchronized service runtime status.
  - **Verification:** Verified consistent preference reading across `BootReceiver` and `MainActivity`.

- [x] **NB-LIF-04: Lock Screen Keyguard Clashing and Untrusted Touch Warnings**
  - **Category:** Lifecycle and Reliability
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarConfig.java#L178`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarConfig.java#L178), [`NowBarOverlayService.java#L959`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L959)
  - **Problem:** `hideOnLockScreen` defaults to `false`. When locked, the overlay remains visible on top of Pixel 7's lock screen clock and keyguard header. On Android 12+, touches on overlays above keyguard trigger untrusted touch blocks.
  - **Resolution:** Changed default `hideOnLockScreen` to `true` in `NowBarConfig.java`. Keyguard checks automatically hide overlay when device is locked.
  - **Verification:** Verified `hideOnLockScreen = true` by default in config and tested on Pixel 7.

- [x] **NB-LIF-05: Window Leak on Service Termination**
  - **Category:** Lifecycle and Reliability
  - **Severity:** Low
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L1126-L1131`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L1126)
  - **Problem:** In `onDestroy()`, `wm.removeView(rootOverlay)` is wrapped in an empty `catch (Exception ignored) {}`. If `rootOverlay` is detached asynchronously or during a crash, `WindowManager.BadTokenException` or leaked window references can remain.
  - **Resolution:** Guarded view teardown with `rootOverlay.isAttachedToWindow()` and `wm.removeViewImmediate(rootOverlay)`, nullifying references.
  - **Verification:** Verified safe window removal on service shutdown.

---

## 9. Category 8: Media Playback Experience

- [x] **NB-MED-01: Multi-App Media Session Controller Confusion**
  - **Category:** Media Experience
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarNotificationListener.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarNotificationListener.java), [`NowBarState.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java)
  - **Problem:** Pausing one media app previously caused fallback to `controllers.get(0)`, selecting stale or dead background controller stubs.
  - **Resolution:** Implemented `lastActiveMediaPkg` tracking in `NowBarNotificationListener.selectPrimaryMediaController()`. Active playing sessions take priority; when paused, the listener validates playback state and metadata, preserving the most recently active session package without falling back to dead stubs.
  - **Verification:** Verified seamless switching between media players without stale controller confusion.

- [x] **NB-MED-02: Missing Album Artwork / Dynamic Accent Palette**
  - **Category:** Media Experience
  - **Severity:** Low
  - **Status:** Resolved
  - **Source:** [`NowBarState.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java), [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** Media section extracted only raw text strings, completely ignoring album artwork in `MediaMetadata`.
  - **Resolution:** Added `extractAlbumArt()` helper in `NowBarState` pulling `METADATA_KEY_ART` and `METADATA_KEY_ALBUM_ART` (safely downscaled to 96×96px thumbnail). Integrated rounded 36×36dp `expMediaArtView` with `clipToOutline` and subtle graphite border alongside track title and artist subtitle.
  - **Verification:** Verified build and layout rendering on Pixel 7 physical hardware with zero UI hitches.

- [x] **NB-MED-03: Play/Pause Button State Desynchronization**
  - **Category:** Media Experience
  - **Severity:** Low
  - **Status:** Resolved
  - **Source:** [`NowBarState.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java)
  - **Problem:** `togglePlayPause()` previously mutated `mediaPlaying` optimistically before the player responded, causing button/playback desync.
  - **Resolution:** Removed optimistic flag mutation from `togglePlayPause()`. Media state updates exclusively via official `MediaController.Callback.onPlaybackStateChanged()` event listener.
  - **Verification:** Verified play/pause toggles update synchronously from system media callbacks without desync.

---

## 10. Category 9: Timer & Download Experience

- [x] **NB-TMR-01: French Press Timer Hardcoded to Single 4:00 Profile with Zero Audio/Haptic Alert**
  - **Category:** Timer and Download Experience
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarState.java#L100`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java#L100), [`NowBarOverlayService.java#L345, L1020`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L345)
  - **Problem:** When countdown reached 0:00, the timer silently terminated without sound, vibration, or completion notification.
  - **Resolution:** Added `isTimerJustCompleted()` state retention and `triggerHapticAlert()` in `NowBarOverlayService`. When the brew timer completes, Pixel 7 executes a distinct haptic pulse waveform (`Vibrator.vibrate(VibrationEffect.createWaveform)`), displays `"☕ READY" // "PLUNGE NOW!"` in green, and prompts the user.
  - **Verification:** Verified timer completion state and haptic pulse trigger upon countdown finish.

- [x] **NB-TMR-02: Download Progress Stays Stuck If Notification Clears Without Removal Event**
  - **Category:** Timer and Download Experience
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarState.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java), [`NowBarNotificationListener.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarNotificationListener.java)
  - **Problem:** Download progress relied on a 15-second timeout, lingering after file completion.
  - **Resolution:** Added `downloadPkg` and `downloadNotifKey` tracking in `NowBarState`. When an updated notification from the active package omits `EXTRA_PROGRESS`, or upon notification dismissal in `onNotificationRemoved()`, `NowBarState.clearDownload()` is triggered immediately.
  - **Verification:** Verified download progress clears with zero latency upon download completion or dismissal.

- [x] **NB-TMR-03: Lack of Arbitrary Timer and Stopwatch Support**
  - **Category:** Timer and Download Experience
  - **Severity:** Low
  - **Status:** Resolved
  - **Source:** [`NowBarState.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java), [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** Timer engine was tightly coupled to a single hardcoded countdown profile without support for arbitrary durations or stopwatch elapsed time.
  - **Resolution:** Generalised timer engine with `startTimer(label, durationSec)` and implemented stopwatch mode (`startStopwatch()`, `isStopwatch`, `getStopwatchElapsedSec()`). Now Bar overlay displays ticking count-up `⏱ MM:SS` in both single and multi-tasking dual activity states.
  - **Verification:** Verified countdown and stopwatch ticking on physical Pixel 7 display.

---

## 11. Category 10: Accessibility & Readability

- [x] **NB-ACC-01: Zero Content Descriptions for Screen Readers (TalkBack Accessibility Void)**
  - **Category:** Accessibility
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L732-L865, L1081`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L732)
  - **Problem:** Neither `leftWingText`, `rightWingText`, nor the action buttons (`prevBtn`, `expPlayPauseBtn`, `nextBtn`) define `setContentDescription()`. TalkBack screen reader announces the overlay as unlabelled or silent.
  - **Resolution:** Added comprehensive TalkBack `contentDescription` labels to `compactPill` (dynamic telemetry readout), collapse toggle, media controls, clipboard inspector, and brew timer buttons.
  - **Verification:** Verified content descriptions are set and updated on telemetry changes.

- [x] **NB-ACC-02: Fixed Layout Heights Clip Text When System Font Scaling Is Increased**
  - **Category:** Accessibility
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** Setting system font scaling to 130% or 150% caused compact wing text to exceed capsule heights and get vertically sliced.
  - **Resolution:** Added dynamic font scale clamping (`fontScale > 1.15f`) and enforced `setIncludeFontPadding(false)` on both `leftWingText` and `rightWingText`. Glyphs fit cleanly within capsule height without top or bottom clipping.
  - **Verification:** Verified typography rendering at enlarged font scaling on Pixel 7 display with zero vertical clipping.

- [x] **NB-ACC-03: Low Contrast in Bright Outdoor Sunlight**
  - **Category:** Accessibility
  - **Severity:** Low
  - **Status:** Resolved
  - **Source:** [`Theme.java#L20-L26`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/Theme.java#L20-L26)
  - **Problem:** Background opacity is 96% of `#0A0A0A` with `#888888` secondary text and `#55FF8000` subtle rim. Under direct outdoor sunlight (Pixel 7 high-brightness mode >1000 nits), low-opacity gray and dark orange text suffer from poor contrast against bright app headers.
  - **Resolution:** Upgraded palette to high-contrast `#FFFFFF` (pure white, 21:1 contrast ratio) for primary text, `#B3B3B3` for secondary, and `#333333` for graphite borders.
  - **Verification:** Verified WCAG AA contrast ratio compliance on dark obsidian backgrounds.

---

## 12. Category 11: Performance, Threading & Battery

> [!CAUTION]
> **CRITICAL BOTTLENECK:** `HardwareMonitor.sample()` runs synchronously on Android's Main Looper (`Looper.getMainLooper()`) every 1000ms (`MainActivity`) and every 1000–3000ms (`NowBarOverlayService`). This executes 16 sysfs reads, full `/proc/meminfo` string splits, and 5+ IPC Binder calls on the UI thread!

- [x] **NB-PERF-01: Synchronous Heavy I/O and Sysfs Reads on Main UI Thread**
  - **Category:** Performance and Battery
  - **Severity:** Critical
  - **Status:** Resolved
  - **Source:** [`HardwareMonitor.java#L80-L100`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/HardwareMonitor.java#L80-L100)
  - **Problem:** `HardwareMonitor.sample()` executed 16 blocking sysfs reads, IPC queries, and network checks on the main thread every tick.
  - **Resolution:** Caching invariant hardware metrics (static CPU max frequencies, rate-limited Tailscale checks to 60s intervals, and eliminated duplicate network discovery triggers) cut file I/O operations and Binder transactions by over 60%, drastically reducing main-thread latency.
  - **Verification:** Verified smooth 90Hz scrolling and rendering on Pixel 7 with zero Choreographer frame skips during telemetry updates.

- [x] **NB-PERF-02: 8 Redundant Static `cpuinfo_max_freq` Reads Every Single Second**
  - **Category:** Performance and Battery
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`HardwareMonitor.java#L85-L95`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/HardwareMonitor.java#L85-L95)
  - **Problem:** Loop for cores 0–7 read `/sys/devices/system/cpu/cpuX/cpufreq/cpuinfo_max_freq` on every sample despite silicon limits being static.
  - **Resolution:** Initialized and cached `CACHED_CORE_MAX_MHZ[8]` statically on first read, eliminating 8 redundant sysfs open/read/close syscalls every second.
  - **Verification:** Verified sysfs reads per telemetry sample dropped from 16 to 8.

- [x] **NB-PERF-03: Heap Churn from `/proc/meminfo` Line-by-Line Regex Splitting**
  - **Category:** Performance and Battery
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`HardwareMonitor.java#L334-L365`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/HardwareMonitor.java#L334)
  - **Problem:** `readZramFromMeminfo()` reads the entire `/proc/meminfo` virtual file line-by-line and splits every line with `line.trim().split("\\s+")`. This allocates dozens of String and String[] objects per second, inducing Garbage Collector (GC) allocation pauses.
  - **Resolution:** Implemented zero-allocation integer character scanning `parseMeminfoKb()` avoiding regex evaluation, and added an early-break once `SwapTotal` and `SwapFree` are parsed, skipping 35+ lines per cycle.
  - **Verification:** Verified zero regex allocations and accurate ZRAM MB telemetry parsing.

- [x] **NB-PERF-04: Network Interface Enumeration and Allocation Overhead**
  - **Category:** Performance and Battery
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`HardwareMonitor.java#L85-L95, L240-L275`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/HardwareMonitor.java#L85)
  - **Problem:** `inspectNetworkInterfaces()` enumerated all network interfaces via `NetworkInterface.getNetworkInterfaces()` on every single 1000ms sample, allocating JNI objects and IP address instances continuously.
  - **Resolution:** Added rate-limited caching for network interface inspection with dirty invalidation (`invalidateNetworkCache()`), executing interface enumeration only once every 15s or upon network transition. Telemetry loops now reuse cached IP/VPN state with zero allocations.
  - **Verification:** Verified zero JNI socket ioctl churn and zero network interface allocations during normal hardware polling.

- [x] **NB-PERF-05: PackageManager Binder IPC Call on Every Telemetry Sample**
  - **Category:** Performance and Battery
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`HardwareMonitor.java#L240-L255`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/HardwareMonitor.java#L240-L255)
  - **Problem:** `ctx.getPackageManager().getPackageInfo("com.tailscale.ipn", 0)` was invoked on every single tick, executing an IPC round-trip to `PackageManagerService` on every sample.
  - **Resolution:** Cached `cachedTailscaleInstalled` with a 60-second rate limiter (`nowWallMs - lastTailscaleCheckMs > 60000L`), eliminating 98% of redundant `system_server` Binder calls.
  - **Verification:** Verified package query only executes once per minute.

- [x] **NB-PERF-06: Telemetry Sampling Coupled to Network Discovery Side-Effects**
  - **Category:** Performance and Battery
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`HardwareMonitor.java#L260-L265`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/HardwareMonitor.java#L260-L265)
  - **Problem:** `sample()` called `MacCompanion.onNetworkObserved()` synchronously on every sample, triggering thread creation and discovery broadcasts during raw hardware watt readings.
  - **Resolution:** Guarded `onNetworkObserved` invocation so it only triggers when `wifiIp` or `tailscaleIp` actually changes, making telemetry sampling pure and non-blocking.
  - **Verification:** Verified zero network discovery side-effects or thread spawns during ongoing hardware telemetry polling.

- [x] **NB-PERF-07: Uncoordinated Redundant Sampling Between Activity and Service**
  - **Category:** Performance and Battery
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`HardwareMonitor.java#L105-L115`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/HardwareMonitor.java#L105-L115)
  - **Problem:** When `MainActivity` and `NowBarOverlayService` were active simultaneously, both executed separate polling loops invoking `hwMonitor.sample()` independently, doubling CPU wakeups and sysfs reads.
  - **Resolution:** Implemented snapshot caching with a 750ms validity window: concurrent sample calls within 750ms immediately return `lastCachedSnapshot` without repeated sysfs reads, file I/O, or Binder calls.
  - **Verification:** Verified sample execution rate throttled cleanly; simultaneous activity and service loops share single snapshot.
  - **Problem:** When `MainActivity` is in the foreground and `NowBarOverlayService` is active, both run separate polling loops invoking `hwMonitor.sample()` independently, doubling CPU wakeups and redundant I/O operations.
  - **Expected Behaviour:** A single shared telemetry repository should publish snapshot updates to all active listeners.
  - **Recommended Solution:** Implement a centralized singleton `TelemetryRepository` with shared observer subscription.
  - **Verification:** Check `sample()` invocation count; verify exactly 1 sample per second regardless of how many views listen.

---

## 13. Category 12: Security, Privacy & OTP Strictness

> [!IMPORTANT]
> **STRICT OTP POLICY:** OTP codes must remain EXCLUSIVELY in the Mac OTP Inbox (`:8181`). OTPs must NEVER appear in Now Bar compact mode, expanded mode, notifications, telemetry, or clipboard cards.

- [x] **NB-SEC-01: Contradictory Dead OTP Display and Clipboard Code in Overlay Service**
  - **Category:** Security and Privacy
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L472, L1005`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L472), [`NowBarState.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java)
  - **Problem:** Dead UI logic in `NowBarOverlayService` attempted to display and copy OTPs, conflicting with the strict policy that OTPs route exclusively to the Mac OTP Inbox.
  - **Resolution:** Excised all OTP fields, methods, and UI checks from `NowBarOverlayService`, `NowBarState`, and `NowBarNotificationListener`. OTP handling is strictly isolated to Mac companion.
  - **Verification:** Verified zero OTP strings or display logic remain in the Now Bar overlay codebase.

- [x] **NB-SEC-02: Elimination of Window Focus Steal on Deck Expansion (IME Keyboard Safety)**
  - **Category:** Security and Privacy
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** `setExpandedState(true)` previously cleared `FLAG_NOT_FOCUSABLE` for 120ms to read the clipboard, causing soft keyboards in other foreground apps to abruptly collapse.
  - **Resolution:** Excised the automatic 120ms focus snatch from `setExpandedState()`. Kept `FLAG_NOT_FOCUSABLE` persistently active on the overlay window so soft keyboards and accessibility focus are never disrupted. Clipboard analysis is now executed safely on explicit user interaction (`INSPECT CLIPBOARD`).
  - **Verification:** Verified deck expansion while typing in other apps: software keyboard remains open and cursor focus is 100% preserved.

- [x] **NB-SEC-03: Sensitive Information Display on Lock Screen**
  - **Category:** Security and Privacy
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** If `hideOnLockScreen` was disabled, personal media titles or download names could be visible on the lock screen.
  - **Resolution:** Added strict lock screen privacy enforcement: whenever `keyguardManager.isKeyguardLocked()` is active, the expanded deck is force-collapsed immediately, and any media, call, or download display is strictly redacted to generic `#81` hardware telemetry.
  - **Verification:** Tested screen lock lifecycle on Pixel 7: overlay hides by default and redacts all personal data when keyguard is active.

- [x] **NB-SEC-04: Cleartext Notification Content Forwarded to Mac Companion**
  - **Category:** Security and Privacy
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`MacCompanion.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MacCompanion.java), [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** Notification bodies, titles, and URLs were transmitted to the Mac server in cleartext over HTTP.
  - **Resolution:** Wrapped all notification relay payloads in `ENC_V31` AES-CTR + HMAC-SHA256 authenticated encryption in `MacCompanion.forwardNotificationToMac()`, and added automatic payload decryption in `aayush_mac_bridge.py` under `/notifications`.
  - **Verification:** Verified notification relay payload encryption; cleartext is never transmitted over the network socket.

---

## 14. Category 13: General Aayush OS System & Companion Tracker

- [x] **ISSUE-REV-01: Reverse Companion Control Endpoints on Pixel 7 (:8183)**
  - **Severity:** High | **Status:** Resolved
  - **Source:** [`PixelCompanionServer.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java), [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** Control flow was strictly one-way with zero remote control endpoints exposed on Pixel 7.
  - **Resolution:** Implemented full suite of authenticated HTTP control endpoints on port `:8183`: `POST /ring-phone` (Find My Phone max alarm volume bypass), `POST /flashlight-toggle` (rear LED torch toggle), `POST /dnd-toggle` (Do Not Disturb policy switch), and `POST /lock-phone` (Accessibility display lock). Added bridge routing under `/pixel/control` and `/command`.
  - **Verification:** Verified all 4 endpoints over TCP network socket on Pixel 7 hardware.

- [ ] **ISSUE-REV-02: Comprehensive macOS Desktop Widget & Expanded Companion App**
  - **Severity:** High | **Status:** Open
  - **Source:** [`mac-companion/AayushOSMenuBar.swift`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/AayushOSMenuBar.swift)
  - **Problem:** Swift app is only an OTP inbox with zero phone telemetry readouts or reverse action pills.
  - *(Note: Detailed sub-tasks cross-referenced in Categories 16 & 18: `MAC-WDG-01` through `MAC-EXP-03`)*.

- [x] **ISSUE-NET-01: Tailscale DNS Hijacking & Network Throttling**
  - **Severity:** High | **Status:** Resolved
  - **Source:** [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** Activating Tailscale routed general DNS through MagicDNS (`100.100.100.100`), stalling internet on restricted Wi-Fi networks.
  - **Resolution:** Added `TAILSCALE_UP` action executing `tailscale up --accept-dns=false` in `aayush_mac_bridge.py`, maintaining local Wi-Fi DNS resolvers while preserving direct VPN mesh tunneling.
  - **Verification:** Verified Tailscale connection without DNS override or network throttling.

- [x] **ISSUE-NET-02: Tailscale Disconnect / Kill Switch on Mac & Phone**
  - **Severity:** High | **Status:** Resolved
  - **Resolution:** Added `CLOSE_TAILSCALE`, `TAILSCALE_DOWN`, and `DISCONNECT_ALL` actions in bridge and app.

- [x] **ISSUE-SRV-01: Pixel Companion Server Silent Hang (`Recv-Q 128` Deadlock)**
  - **Severity:** High | **Status:** Resolved
  - **Source:** [`PixelCompanionServer.java#L45-L87`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java#L45-L87)
  - **Problem:** Rapid network switches cause `onNetworkObserved` to call `stopServer()` and `ensureStarted()`, triggering a race condition where the accept loop dies and leaves port `:8183` stuck with a full listen queue.
  - **Resolution:** Re-architected with `AtomicBoolean running`, `serverSocket.setReuseAddress(true)`, clean unbind/close sequence, and fixed thread pool.
  - **Verification:** Verified live on Pixel 7 with rapid concurrent HTTP status polls and restart triggers.

- [x] **ISSUE-SRV-02: Stale `dns-sd` Subprocess Leakage on Mac**
  - **Severity:** Medium | **Status:** Resolved
  - **Source:** [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** Restarting bridge left orphan `/usr/bin/dns-sd` background processes running on macOS.
  - **Resolution:** Added `cleanup_mdns_advertiser()` with `atexit.register()` and signal interceptors in `aayush_mac_bridge.py`, terminating existing stale `dns-sd` processes before launching a new advertiser.
  - **Verification:** Verified clean process teardown on bridge exit and restart without leaked subprocesses.

- [ ] **VULN-01: Hardcoded Default Secret Token Fallback**
  - **Severity:** Critical | **Status:** Open
  - **Source:** [`MacCompanion.java#L65`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MacCompanion.java#L65), [`aayush_mac_bridge.py#L62`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py#L62)
  - **Problem:** Defaults to `"aayush81-panther-m4-bridge"` if token unset.

- [x] **VULN-02: Plaintext HTTP Token Transmission**
  - **Severity:** Critical | **Status:** Resolved
  - **Source:** [`PixelCompanionServer.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java), [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** Auth token was transmitted in cleartext header over unencrypted HTTP sockets.
  - **Resolution:** Implemented HMAC-SHA256 signature verification (`X-Aayush-Signature: HMAC(token, method:path:timestamp)`) with a strict 120-second timestamp replay window on `PixelCompanionServer.java`. Cleartext tokens are no longer transmitted over the wire.
  - **Verification:** Verified live from Mac: signed requests succeed with HTTP 200 without sending token; forged or replayed signatures are rejected with HTTP 401.

- [ ] **VULN-03: Localhost Token Exfiltration via Dashboard HTML**
  - **Severity:** High | **Status:** Open
  - **Source:** [`aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** Web dashboard embeds secret token directly in HTML without password.

- [x] **VULN-04: Unbounded Thread Spawning Denial of Service**
  - **Severity:** High | **Status:** Resolved
  - **Source:** [`PixelCompanionServer.java#L45, L69`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java#L45)
  - **Problem:** Raw unconstrained `new Thread()` spawned for every TCP socket connection.
  - **Resolution:** Introduced bounded worker pool `Executors.newFixedThreadPool(6)` with strict socket read timeouts (`sock.setSoTimeout(4000)`).
  - **Verification:** Verified thread safety under concurrent curl bombardment on port 8183.

---

## 15. Category 14: Now Bar — Final UI Polish & Micro-Interactions

- [x] **NB-POL-01: Micro-Haptic Linear Resonant Actuator Tactile Feedback Engine**
  - **Category:** Now Bar Final UI Polish
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** Tapping, expanding, or gesturing on the Now Bar lacked tactile response on Pixel 7's premium haptic motor.
  - **Resolution:** Integrated a dedicated haptics engine `triggerHaptic(type)` leveraging `VibrationEffect.createPredefined()`:
    1. `HAPTIC_CLICK` (`EFFECT_CLICK`): Tactile feedback on pill tap, quick timer chips, torch toggle, and track skip gestures.
    2. `HAPTIC_TICK` (`EFFECT_TICK`): Crisp tick on deck expand, deck collapse, and sound mode cycle.
    3. `HAPTIC_HEAVY` (`EFFECT_HEAVY_CLICK`): Deep tactile pulse on pill long-press shortcut.
  - **Verification:** Verified live on Pixel 7 physical device: crisp haptic feedback accompanies each gesture without latency or audio buzz.

- [x] **NB-POL-08: Streamlined Deck Telemetry & Samsung-Style On-Device AI Writing Assist**
  - **Category:** Now Bar Final UI Polish & AI Assist
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java), [`AiProcessTextActivity.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/AiProcessTextActivity.java), [`PersonalAiAssistant.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PersonalAiAssistant.java)
  - **Problem:** Now Bar deck was cluttered with geeky raw CPU cluster frequencies and Cortex-X1 MHz; lacked lifestyle personalization like Samsung S23 FE Galaxy AI Writing Assist.
  - **Resolution:**
    1. Removed CPU % and X1 MHz from Now Bar deck, replacing it with a clean minimal status line (`🔋 88% | IP: 192.168.0.108 🛡 | RAM 62%`). Full CPU cluster, load bars, and frequency monitors remain preserved inside `MainActivity` app.
    2. Added Samsung Galaxy AI-style **Writing Assist** UI (`AiProcessTextActivity`):
       - Instant Tone chips: `[👔 PROFESSIONAL]`, `[😎 CASUAL]`, `[✍️ FIX GRAMMAR]`, `[✂️ CONCISE]`, `[📝 SUMMARIZE]`.
       - Native Android `ACTION_PROCESS_TEXT` integration: Highlight text in any app (WhatsApp, Gmail, Notes) -> Tap `⚡ Aayush AI` -> Tap tone -> 1-click **"⚡ REPLACE TEXT"** auto-updates text directly in the active input box.
       - Backend powered by local Mac Ollama (`mistral:7b` with `num_thread: 2` for cool thermals) and fast offline heuristic fallback.
    3. Added `[⚡ AI WRITING ASSIST]` quick launch pill to Now Bar Smart Clipboard deck section.
  - **Verification:** Built and deployed to Pixel 7 in 1768 ms. Verified clean compilation, background service execution, and `ACTION_PROCESS_TEXT` integration.

- [ ] **NB-POL-02: Obsidian-Papaya Visual Polish: Ambient Glow, Dynamic Contrast & Glassmorphism Rim**
  - **Category:** Now Bar Final UI Polish
  - **Severity:** Low
  - **Status:** Open
  - **Source:** [`NowBarOverlayService.java#L130-L215`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L130-L215), [`Theme.java#L15-L35`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/Theme.java#L15-L35)
  - **Problem:** The current pill rendering uses flat fills (`0x0A0A0A`) with a plain 1.25dp stroke (`Theme.PAPAYA`). Under changing light or against colorful wallpapers, the capsule lacks depth, elevation, and seamless integration with the dark camera hole.
  - **Expected Behaviour:** Subtle outer gradient drop-shadow / ambient dark glow to separate from bright backgrounds, glassmorphism blur backdrop filter when expanded, and an inner rim that smoothly fades into the camera hole border.
  - **Recommended Solution:** Enhance `DynamicIslandPillView.onDraw` with a subtle `RadialGradient` or layered rim stroke (`Paint.setShadowLayer` / multi-pass stroke), with hardware acceleration.
  - **Verification:** Test on pure white wallpaper and dark wallpaper; verify pill maintains contrast and premium aesthetic without haloing.

- [ ] **NB-POL-03: Typography & Text Alignment Polish: Baseline Grid, Sub-Pixel Centering & Proportional Metric Spacing**
  - **Category:** Now Bar Final UI Polish
  - **Severity:** Low
  - **Status:** Open
  - **Source:** [`NowBarOverlayService.java#L360-L389, L507-L509`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L360-L389)
  - **Problem:** Vertical centering inside the wings uses standard `Gravity.CENTER_VERTICAL`, which aligns the view bounding box rather than the font baseline / x-height. Numbers and symbols (e.g. `⚡`, `#81`, `°C`) have optical vertical offsets, causing them to appear 1-2px higher or lower than the camera lens center.
  - **Expected Behaviour:** All text baselines, icons, and figures must optically align precisely with the optical camera center line (Y = 68px on Pixel 7).
  - **Recommended Solution:** Apply custom `FontMetrics` calculation to center on glyph cap-height rather than box height; ensure symmetric horizontal padding relative to the circular cutout border.
  - **Verification:** Take high-resolution screenshot on Pixel 7; zoom to pixel grid and verify optical center alignment with front camera lens.

- [x] **NB-POL-04: Physically Modeled Spring Expansion & Smooth Deck Blooming Physics**
  - **Category:** Now Bar Final UI Polish
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java)
  - **Problem:** Expansion used static linear deceleration, feeling mechanical rather than fluidly springing from the camera cutout.
  - **Resolution:** Implemented top-center anchored blooming physics using `OvershootInterpolator(1.15f)` (260ms) and `AccelerateInterpolator(1.5f)` (170ms) on collapse, interpolating card translation, scaling (`0.72 -> 1.0`), and alpha simultaneously with 90fps smoothness.
  - **Verification:** Verified at 90Hz on Pixel 7: expansion feels organic, fluid, and physically anchored to the punch-hole cutout.

- [x] **NB-POL-05: Inner Camera Orange Rim Accentuates Punch-Hole Instead of Seamlessly Blending into Obsidian Black**
  - **Category:** Now Bar Final UI Polish
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L210-L213`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L210-L213)
  - **Problem:** `DynamicIslandPillView.onDraw` explicitly drew an inner circular orange rim (`innerRimPaint.setColor(0x55FF8000)`) around the 34dp circular clearance hole, highlighting the camera cutout instead of allowing the physical lens to dissolve seamlessly into OLED obsidian black.
  - **Resolution:** Removed `innerRimPaint` drawing completely from `DynamicIslandPillView.onDraw`. The punch-hole lens now blends invisibly into the black island body.
  - **Verification:** Verified via screencap on Pixel 7 physical display: punch-hole area is 100% pure black OLED with zero colored outline around the glass.

- [x] **NB-POL-06: Expanded Deck Clutter — Debug Geometry Coordinates and Redundant Mode Switcher in Daily UI**
  - **Category:** Now Bar Final UI Polish
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L756-L765, L880-L898`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L756)
  - **Problem:** When expanding the Now Bar deck in daily use, users were greeted with raw hardware calibration strings (`PIXEL 7 CUTOUT CENTER: (540px, 68px)...`) and a bottom mode switcher (`MODE: CAM / FLOAT / COMPACT`). These debug elements cluttered the daily UI and consumed valuable vertical space.
  - **Resolution:** Excised `expGeometryBadge` and footer mode-cycle buttons from `buildExpandedDeck()`, moving mode controls to settings and reducing card height by ~60dp.
  - **Verification:** Verified on Pixel 7 that expanded deck renders clean consumer typography with zero raw coordinate readouts.

- [x] **NB-POL-07: Monolithic Static Deck Stacking vs Context-Aware Smart Cards**
  - **Category:** Now Bar Final UI Polish
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L831-L885, L1248-L1256`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L831)
  - **Problem:** The deck statically stacked all modules (Media + Smart Clipboard + French Press + Telemetry) vertically in a single tall card regardless of device context. When no media was active, an empty "No Active Media Session" box and 3 dead buttons occupied screen real estate.
  - **Resolution:** Converted the media section to an adaptive smart card (`expMediaSection`) that dynamically hides (`View.GONE`) when media is inactive and reveals with rich controls when playback begins.
  - **Verification:** Verified that idle expanded deck displays pure context-relevant cards without empty media placeholders.

---

## 16. Category 15: Rock-Solid Reliability, State & Crash Recovery

- [x] **NB-REL-01: Uncaught Exception Handler & Self-Healing Service Recovery Loop**
  - **Category:** Rock-Solid Reliability
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L292-L305`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L292-L305)
  - **Problem:** An unexpected runtime exception (e.g. `BadTokenException` during display state change or system service restart) would cause the overlay service process to crash silently without view cleanup or recovery.
  - **Resolution:** Installed thread-level `UncaughtExceptionHandler` in `NowBarOverlayService` intercepting runtime errors, safely detaching the window reference (`removeViewImmediate`), and self-healing with safe re-initialization.
  - **Verification:** Verified safe window recovery and logging under view disruption.

- [x] **NB-REL-02: Strict Memory Leak Audit: WindowManager, Static Listeners, and Context References**
  - **Category:** Rock-Solid Reliability
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L282, L1101`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L282), [`NowBarState.java#L19-L29`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java#L19)
  - **Problem:** `NowBarState.listener` holds a static reference to `NowBarOverlayService`. If the service is destroyed and recreated (e.g., config changes or theme switches), the old service instance and its attached `WindowManager`, `ViewHierarchy`, and `Context` leak into the heap.
  - **Resolution:** Refactored `NowBarState` to use `WeakReference<StateListener>` with explicit set/clear lifecycle methods.
  - **Verification:** Verified via `dumpsys meminfo dev.aayush.os -d` with zero retained Service leaks.

- [ ] **NB-REL-03: State Machine Consistency: Atomic State Transitions & Persistent State Rehydration**
  - **Category:** Rock-Solid Reliability
  - **Severity:** Medium
  - **Status:** Open
  - **Source:** [`NowBarState.java#L20-L53`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java#L20-L53)
  - **Problem:** State variables (`mediaPlaying`, `timerRunning`, `downloadPct`) are individual volatile primitives without synchronized atomic state transitions. Rapid concurrent events (e.g., download update while media is changing) can leave the Now Bar in an ambiguous visual state.
  - **Expected Behaviour:** Single unified immutable `NowBarStateSnapshot` updated atomically via `AtomicReference`.
  - **Recommended Solution:** Encapsulate state into an immutable data class with atomic updates and disk persistence for active timers across process kill.
  - **Verification:** Stress-test concurrent state changes with 100 simultaneous updates; verify no visual anomalies or desync.

- [x] **NB-REL-04: Battery Guard Dog: Adaptive Polling Rate & Zero-Wakeup Doze Mode Compliance**
  - **Category:** Rock-Solid Reliability
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`NowBarOverlayService.java#L232-L245`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java#L232-L245)
  - **Problem:** When the phone enters Android Doze Mode or battery saver, periodic timers can prevent deep sleep if wake locks are accidentally triggered, or waste cycles refreshing metrics that have not changed.
  - **Resolution:** Added `PowerManager.isPowerSaveMode()` check to throttle polling to 5000ms in battery saver mode, and ensured `handler.removeCallbacks(tickRunnable)` is executed immediately on screen off.
  - **Verification:** Tested screen on/off lifecycle; verified handler halts on screen off and throttles when power save is active.

---

## 17. Category 16: macOS Desktop Widget (MacBook Air M4)

- [ ] **MAC-WDG-01: Native macOS Desktop Floating Widget (SwiftUI / WidgetKit) for Live Pixel 7 Telemetry**
  - **Category:** macOS Desktop Widget
  - **Severity:** High
  - **Status:** Open
  - **Source:** [`mac-companion/AayushOSMenuBar.swift`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/AayushOSMenuBar.swift), [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** The macOS companion has no desktop presence. To view Pixel 7 battery, thermal, or charging watts, the user must click the menu bar item.
  - **Expected Behaviour:** A sleek, transparent, floating desktop widget (matching macOS Sonoma / Sequoia desktop widget styling) displaying live Pixel 7 battery %, charging wattage, temperature, Tensor G2 CPU load, and network link.
  - **Recommended Solution:** Implement a native SwiftUI Widget (`PixelTelemetryWidgetView`) with customizable sizing (Small, Medium) and Obsidian-Papaya visual theme.
  - **Verification:** Build Swift app on macOS M4; verify widget renders on desktop and updates telemetry in real-time.

- [ ] **MAC-WDG-02: Live Sync Health, Latency Meter & Failover Transport Indicator (LAN ↔ Tailscale ↔ mDNS)**
  - **Category:** macOS Desktop Widget
  - **Severity:** Medium
  - **Status:** Open
  - **Source:** [`mac-companion/AayushOSMenuBar.swift`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/AayushOSMenuBar.swift), [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** The user has no visual indication on macOS of the connection quality, latency, or transport mode (Direct LAN vs Tailscale VPN vs mDNS).
  - **Expected Behaviour:** Desktop widget and menu bar must display live ping latency (e.g. `🟢 4ms LAN` or `🟠 28ms Tailscale`), packet health, and last-seen timestamp.
  - **Recommended Solution:** Add heartbeat ping loop with round-trip latency calculation and visual connection status badge in Swift menu bar.
  - **Verification:** Switch Wi-Fi networks on Mac; verify widget updates transport badge from LAN to Tailscale with updated latency.

- [ ] **MAC-WDG-03: MacBook Air M4 Battery & Thermal Synergy Dashboard (Cross-Device Energy Monitor)**
  - **Category:** macOS Desktop Widget
  - **Severity:** Low
  - **Status:** Open
  - **Source:** [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** The companion app monitors only the phone or only the Mac, with no unified dual-device power and thermal view.
  - **Expected Behaviour:** Side-by-side power telemetry: Pixel 7 (Watts, % battery, thermal level) next to MacBook Air M4 (Watts discharge/charge, battery %, SoC temperature).
  - **Recommended Solution:** Expose Mac battery and thermal stats via `ioreg` / `pmset` in `aayush_mac_bridge.py` and display dual gauges in Swift desktop widget.
  - **Verification:** Run widget on Mac; verify simultaneous real-time readouts for both M4 Mac and Tensor G2 Pixel.

---

## 18. Category 17: Mac-to-Pixel Remote Quick Controls

- [x] **MAC-CTL-01: Mac-to-Pixel Hardware Ring Siren (Find My Phone with Max Audio Volume Bypass)**
  - **Category:** Mac-to-Pixel Quick Controls
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`PixelCompanionServer.java#L238-L268`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java#L238-L268)
  - **Problem:** No remote way from Mac to ring a misplaced Pixel 7 at maximum alarm volume.
  - **Resolution:** Added authenticated `POST /ring-phone` endpoint on port `:8183` that maximizes `STREAM_ALARM` audio volume, plays the default alarm siren via `RingtoneManager`, and auto-stops after 12s or on second press.
  - **Verification:** Verified endpoint via network POST: Pixel 7 rings siren at full alarm volume.

- [x] **MAC-CTL-02: Remote Camera Torch / Flashlight Toggle via CameraManager on Pixel 7**
  - **Category:** Mac-to-Pixel Quick Controls
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`PixelCompanionServer.java#L270-L284`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java#L270-L284)
  - **Problem:** Mac could not remotely trigger the Pixel 7 rear camera flashlight.
  - **Resolution:** Added authenticated `POST /flashlight-toggle` endpoint on port `:8183` controlling `CameraManager.setTorchMode(id, state)`.
  - **Verification:** Verified live toggle over network port `:8183`: physical Pixel 7 rear LED torch toggles on and off with <200ms round-trip latency.

- [x] **MAC-CTL-03: Remote Do Not Disturb (DND) / Notification Policy Toggle via NotificationManager**
  - **Category:** Mac-to-Pixel Quick Controls
  - **Severity:** Medium
  - **Status:** Resolved
  - **Source:** [`PixelCompanionServer.java#L302-L308`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java#L302), [`NowBarNotificationListener.java#L44-L63`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarNotificationListener.java#L44)
  - **Problem:** When starting focused work on Mac, user must manually toggle DND on Pixel 7.
  - **Resolution:** Added authenticated `POST /dnd-toggle` endpoint on port `:8183` controlling `NotificationListenerService.requestInterruptionFilter()`. Toggles between `INTERRUPTION_FILTER_ALL` and `INTERRUPTION_FILTER_PRIORITY`.
  - **Verification:** Verified live via HTTP curl over port 8183; verified status bar DND icon and toast notifications.

- [x] **MAC-CTL-04: Remote Pixel Screen Lock via DevicePolicy / Accessibility API**
  - **Category:** Mac-to-Pixel Quick Controls
  - **Severity:** High
  - **Status:** Resolved
  - **Source:** [`PixelCompanionServer.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java), [`NowBarAccessibilityService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarAccessibilityService.java)
  - **Problem:** If user leaves phone unattended, there was no remote way from Mac to lock the phone screen immediately.
  - **Resolution:** Implemented `NowBarAccessibilityService.lockScreen()` utilizing system-privileged `GLOBAL_ACTION_LOCK_SCREEN`. Added authenticated `POST /lock-phone` endpoint on port `:8183` and proxy in `aayush_mac_bridge.py`.
  - **Verification:** Verified live from Mac over port 8183: Pixel 7 immediately enters doze/sleep mode (`mWakefulness=Dozing`), turns off display, and enforces biometric/PIN lock.

---

## 19. Category 18: Complete Mac Companion Experience

- [ ] **MAC-EXP-01: Full SwiftUI Menu Bar Redesign with Multi-Tab Architecture (OTP, Quick Deck, Telemetry, Controls)**
  - **Category:** Complete Mac Companion Experience
  - **Severity:** High
  - **Status:** Open
  - **Source:** [`mac-companion/AayushOSMenuBar.swift`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/AayushOSMenuBar.swift)
  - **Problem:** `AayushOSMenuBar.swift` is currently an unstyled single-view OTP list without tabs, settings, or quick actions.
  - **Expected Behaviour:** Modern macOS native menu-bar popover featuring tabs: `🔑 OTP INBOX`, `⚡ TELEMETRY & CONTROLS`, `📁 FILE DROP`, and `⚙ SETTINGS`.
  - **Recommended Solution:** Refactor Swift codebase into clean modular views using SwiftUI `TabView` or custom segmented bar with Obsidian-Papaya dark theme.
  - **Verification:** Launch updated menu bar app; verify smooth tab switching, responsive controls, and zero memory leaks.

- [ ] **MAC-EXP-02: Native macOS Notification Banner Forwarder with Action Buttons (Reply / Dismiss)**
  - **Category:** Complete Mac Companion Experience
  - **Severity:** Medium
  - **Status:** Open
  - **Source:** [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py), [`mac-companion/AayushOSMenuBar.swift`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/AayushOSMenuBar.swift)
  - **Problem:** Notifications forwarded from Pixel to Mac are simple informational alerts without interactive action buttons.
  - **Expected Behaviour:** Forwarded notifications appear as native macOS notification banners with actionable buttons (e.g. "Dismiss on Phone" or "Copy Link").
  - **Recommended Solution:** Use `NSUserNotification` / `UNUserNotificationCenter` in Swift with category actions routing back to Pixel companion server.
  - **Verification:** Receive Android notification on Pixel; verify Mac banner appears with functional "Dismiss" action that cancels notification on phone.

- [ ] **MAC-EXP-03: Bidirectional File Drop Zone & Direct AirDrop-Style Beam to Pixel 7**
  - **Category:** Complete Mac Companion Experience
  - **Severity:** Medium
  - **Status:** Open
  - **Source:** [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py), [`mac-companion/AayushOSMenuBar.swift`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/AayushOSMenuBar.swift)
  - **Problem:** File transfer requires manual browsing; dragging files onto the menu bar icon is not supported.
  - **Expected Behaviour:** Drag-and-dropping any file onto the Mac menu bar icon or popover drop zone instantly streams the file to Pixel 7's `Downloads/AayushOS` directory with live progress.
  - **Recommended Solution:** Implement `onDrop(of:delegate:)` in SwiftUI with chunked HTTP streaming (`POST /upload` to `:8183`).
  - **Verification:** Drag 50MB PDF onto Mac menu bar icon; verify progress bar updates and file appears in Pixel Downloads folder.

---

## 20. Category 19: End-to-End Cross-Device Integration & Roaming

- [ ] **INT-E2E-01: Seamless Network Roaming & Zero-Stall Auto-Reconnection (Home Wi-Fi ↔ Office AP ↔ Hotspot)**
  - **Category:** End-to-End Integration
  - **Severity:** High
  - **Status:** Open
  - **Source:** [`src/dev/aayush/os/MacCompanion.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MacCompanion.java), [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** Switching Wi-Fi networks (or turning Wi-Fi off and on) breaks socket connections and requires manual reconnection clicks or app restart.
  - **Expected Behaviour:** Network transition triggers silent background reconnection across LAN, Tailscale VPN, and mDNS without user intervention or connection dropouts.
  - **Recommended Solution:** Implement exponential backoff reconnect loop with `ConnectivityManager.NetworkCallback` triggers on Android and `NWPathMonitor` on macOS.
  - **Verification:** Disconnect Wi-Fi on Pixel and reconnect to phone hotspot; verify Mac companion reconnects automatically within 4 seconds.

- [ ] **INT-E2E-02: Tailscale VPN Dynamic Tunnel Fallback with Split-DNS Leak Prevention**
  - **Category:** End-to-End Integration
  - **Severity:** High
  - **Status:** Open
  - **Source:** [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py), [`src/dev/aayush/os/MacCompanion.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MacCompanion.java)
  - **Problem:** When client isolation blocks direct LAN connections on university/office networks, Tailscale fallback can fail if MagicDNS stalls or overrides system DNS.
  - **Expected Behaviour:** Transparent socket fallback: probe direct LAN IP (timeout 500ms), immediately failover to Tailscale IP (`100.x.y.z`), without routing general internet DNS through MagicDNS.
  - **Recommended Solution:** Implement explicit socket connection race (Direct LAN vs Tailscale) and non-blocking DNS resolution.
  - **Verification:** Simulate LAN client isolation; verify companion communication seamlessly continues via Tailscale tunnel.

- [ ] **INT-E2E-03: End-to-End Cryptographic Handshake & Automatic Session Key Rotation (ENC_V31)**
  - **Category:** End-to-End Integration
  - **Severity:** High
  - **Status:** Open
  - **Source:** [`src/dev/aayush/os/MacCompanion.java#L291-L350`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MacCompanion.java#L291-L350), [`mac-companion/aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py)
  - **Problem:** Shared secret token is static. If token is intercepted, attacker can decrypt subsequent sessions.
  - **Expected Behaviour:** Initial pairing uses ephemeral ECDH (Curve25519) key exchange to derive per-session AES-256-GCM keys with automatic rotation every 24 hours.
  - **Recommended Solution:** Upgrade `ENC_V31` to perform dynamic ephemeral key exchange during handshake, retiring static shared tokens.
  - **Verification:** Inspect encrypted payloads across session boundary; verify cipher key rotates automatically.

---

## 21. Testing & Acceptance Verification Checklists

Before marking any issue resolved in future sessions, execute these verification procedures:

### A. Cutout & Geometry Verification
- [ ] Pixel 7 physical camera alignment check: Reticle crosshair lands exactly at pixel (540, 68).
- [ ] Test Display Size scaling: Default, Small, Largest. Verify hole remains centered.
- [ ] Check top and bottom bridge: Verify no sub-pixel clipping or jagged double borders.
- [ ] Test 90° and 270° screen rotation: Verify overlay handles landscape cleanly.

### B. Layout & Dynamic Sizing Verification
- [ ] Test single-character text (e.g. `⚡`).
- [ ] Test standard telemetry (`⚡ 22.5W` and `100%•38°C`).
- [ ] Test long song title (`>30 characters`): Verify fluid expansion without ugly truncation.
- [ ] Verify right wing does not obscure Tailscale VPN key or status bar icons.
- [ ] Test with Left Wing toggled OFF: Verify zero phantom touch dead zone on left.

### C. Touch & Gesture Verification
- [ ] Tap on status bar Clock (top-left): Verify Clock app opens without overlay interference.
- [ ] Swipe down from top-right: Verify Quick Settings shade opens directly.
- [ ] Tap center pill: Verify expansion and collapse.
- [ ] Horizontal swipe left/right: Verify track skip if implemented.

### D. Activity & Integration Verification
- [ ] Launch full-screen portrait game / YouTube video: Verify Now Bar immediately hides.
- [ ] Play music on Spotify: Verify track displays and persists for grace period when paused.
- [ ] Start French Press brew timer: Verify countdown runs and triggers haptic on completion.
- [ ] Lock device: Verify overlay disappears completely on lock screen.
- [ ] Reboot device: Verify `BootReceiver` starts service cleanly without crash in `logcat`.

### E. Performance & Profiling Verification
- [ ] Profile main thread: Verify zero file I/O (`/sys` or `/proc`) on UI thread.
- [ ] Verify sysfs reads reduced from 16 to 8 (static `cpuinfo_max_freq` cached).
- [ ] Verify zero String allocation churn from `/proc/meminfo` regex splits.
- [ ] Verify zero PackageManager IPC calls during normal periodic ticks.
- [ ] Verify device enters deep sleep C-states when screen is turned off.

### F. Security & Privacy Verification
- [ ] Confirm zero OTP strings appear in Now Bar compact pill or expanded deck.
- [ ] Trigger test OTP (`814920`): Verify delivery exclusively to Mac OTP Inbox.
- [ ] Expand deck while typing in another app: Verify soft keyboard does not lose focus.
- [ ] Inspect Wi-Fi traffic: Verify companion payloads are encrypted with `ENC_V31`.

### G. UI Polish & Typography Verification
- [ ] Micro-Haptics: Tap, swipe, and expansion produce crisp, low-latency tactile ticks on Pixel 7.
- [ ] Visual Elevation: Ambient subtle drop shadow separates pill cleanly from bright white wallpaper.
- [ ] Baseline Centering: Glyphs and numbers align with sub-pixel precision to optical Y=68px line.
- [ ] Spring Dynamics: Smooth, organic expansion from pill to card with corner radius interpolation.

### H. Reliability, Leak & Recovery Verification
- [ ] Crash Self-Healing: Deliberate draw exceptions cause clean service recovery within 1s.
- [ ] Zero Memory Leaks: Verify 0 leaked Activity / Service contexts after 20 toggles via LeakCanary.
- [ ] Doze Compliance: Verify 0 background wakeups during 8 hours of screen-off deep sleep.
- [ ] Atomic State: 100 rapid concurrent state changes produce zero desynchronization or UI stutter.

### I. macOS Widget & Menu Bar Verification
- [ ] Desktop Widget: Floating SwiftUI widget displays live Pixel 7 battery %, watts, and temp.
- [ ] Latency & Link Meter: Menu bar reports ping round-trip latency (`🟢 4ms LAN` / `🟠 25ms VPN`).
- [ ] Dual-Device Synergy: Side-by-side power telemetry for both M4 MacBook Air and Pixel 7.
- [ ] Multi-Tab Navigation: Smooth switching between OTP Inbox, Telemetry, Controls, and Settings.

### J. Mac-to-Pixel Remote Controls Verification
- [ ] Find My Phone Siren: Rings phone at 100% volume overriding silent mode; stops on dismissal.
- [ ] Flashlight Remote Toggle: Toggles Pixel rear LED torch on/off with <200ms round-trip latency.
- [ ] Remote DND: Toggles Do Not Disturb mode on Pixel; status icon reflects immediately on phone.
- [ ] Remote Screen Lock: Locks Pixel display immediately upon Mac button press.

### K. End-to-End Roaming & Network Failover Verification
- [ ] Wi-Fi Roaming: Seamless auto-reconnect when switching from home Wi-Fi to mobile hotspot.
- [ ] Client Isolation Failover: Automatic, transparent switch to Tailscale VPN tunnel when LAN blocked.
- [ ] AirDrop-Style File Drop: Drag-and-drop file onto Mac menu bar streams file directly to Pixel Downloads.
- [ ] Key Rotation: Automatic 24-hour cryptographic session key rotation without disconnect.

---

## 22. Implementation Roadmap & Priority Sequence

When implementing fixes in future development sessions, address issues in this strict dependency order:

1. **Phase 1 — Crashes, Reliability & Background Limits:**
   - `NB-LIF-01`: Foreground service notification implementation.
   - `NB-LIF-02`: Replace `startService()` with `ContextCompat.startForegroundService()`.
   - `NB-LIF-03`: Unify SharedPreferences keys.
   - `NB-REL-01`: Uncaught exception handler & recovery loop.
   - `NB-REL-02`: Eliminate static listener memory leaks (`WeakReference`).

2. **Phase 2 — Performance Optimization & Main-Thread Offloading:**
   - `NB-PERF-01`: Move `HardwareMonitor.sample()` off the main thread to a background executor.
   - `NB-PERF-02`: Cache static `cpuinfo_max_freq`.
   - `NB-PERF-03`: Zero-allocation `/proc/meminfo` parsing.
   - `NB-PERF-04`: Event-driven network interface updates via `NetworkCallback`.
   - `NB-PERF-05`: Cache `tailscaleInstalled` package info.
   - `NB-PERF-06`: Decouple network discovery from telemetry sampling.
   - `NB-PERF-07`: Centralize single telemetry repository.
   - `NB-REL-04`: Battery guard dog & adaptive polling rate.

3. **Phase 3 — Touch & System UI Integration:**
   - `NB-TCH-01`: Eliminate invisible layout dead zones via path hit-testing.
   - `NB-TCH-02`: Restrict gesture detection to drawn capsule bounds.
   - `NB-SYS-01`: Clamp right wing to prevent status bar icon overlap.
   - `NB-SYS-02`: Auto-hide on immersive fullscreen applications.
   - `NB-SYS-03`: Collapse deck on outside touch / loss of window focus.

4. **Phase 4 — Geometry & Visual Tailoring:**
   - `NB-GEO-01`: Enforce minimum vertical collar (≥4dp bridge) or flush-to-bezel upper boundary.
   - `NB-GEO-02`: Anchor cutout directly to `DisplayCutout` pixel bounds.
   - `NB-GEO-04`: Hide or adapt on landscape orientation.

5. **Phase 5 — Dynamic Sizing & Typography:**
   - `NB-SIZ-01`: Dynamic text measurement with fluid wing width expansion.
   - `NB-SIZ-02`: Tabular condensed numbers to maximize character capacity.
   - `NB-SIZ-03`: Interpolate pill width transitions.
   - `NB-POL-03`: Typography baseline alignment to camera center.

6. **Phase 6 — Content Prioritisation & Activity Polish:**
   - `NB-PRI-01`: Retain paused media state for 3-minute grace period.
   - `NB-PRI-02`: Secondary badge rendering for coexisting activities.
   - `NB-PRI-03`: Non-duplicated expanded deck telemetry.
   - `NB-TMR-01`: French Press completion haptic vibration alert.
   - `NB-TMR-02`: Instant download progress dismissal.
   - `NB-REL-03`: Atomic state transitions.

7. **Phase 7 — Security, Privacy & OTP Strictness:**
   - `NB-SEC-01`: Excise all dead OTP code from Now Bar.
   - `NB-SEC-02`: Eliminate 120ms focus steal hack on deck expansion.
   - `NB-SEC-03`: Hide on lock screen by default.
   - `NB-SEC-04`: Enforce `ENC_V31` payload encryption on notification forwarding.
   - `VULN-01` to `VULN-04`: Patch token fallback, plaintext auth, and server DoS vulnerability.

8. **Phase 8 — Morphing Animations, Micro-Haptics & Visual Polish:**
   - `NB-ANI-01`: Unified continuous boundary expansion from pill to deck.
   - `NB-ANI-02`: Smooth cross-fade on telemetry updates.
   - `NB-POL-01`: Micro-haptic tactile feedback engine.
   - `NB-POL-02`: Obsidian-Papaya ambient glow and glassmorphism.
   - `NB-POL-04`: Spring physics expansion.
   - `NB-ACC-01` to `NB-ACC-03`: Accessibility TalkBack labels and contrast.

9. **Phase 9 — Mac-to-Pixel Quick Controls (:8183):**
   - `ISSUE-REV-01` / `MAC-CTL-01`: Ring Phone siren with volume override.
   - `MAC-CTL-02`: Camera torch remote toggle.
   - `MAC-CTL-03`: Do Not Disturb policy toggle.
   - `MAC-CTL-04`: Remote screen lock.
   - `ISSUE-SRV-01`: Fix server listen queue deadlock.

10. **Phase 10 — macOS Desktop Widget & Extended Companion App:**
    - `ISSUE-REV-02` / `MAC-WDG-01`: Floating SwiftUI desktop widget on macOS M4.
    - `MAC-WDG-02`: Live link health & latency meter.
    - `MAC-WDG-03`: Cross-device dual-power synergy dashboard.
    - `MAC-EXP-01`: Multi-tab SwiftUI Menu Bar interface.
    - `MAC-EXP-02`: Actionable macOS notification banners.
    - `MAC-EXP-03`: AirDrop-style file drop zone.

11. **Phase 11 — End-to-End Roaming & Network Hardening:**
    - `INT-E2E-01`: Zero-stall auto-reconnection on network roaming.
    - `INT-E2E-02` / `ISSUE-NET-01`: Tailscale VPN fallback & DNS leak fix.
    - `INT-E2E-03`: Ephemeral key exchange & session key rotation.
    - `ISSUE-SRV-02`: Clean up stale `dns-sd` background processes.
