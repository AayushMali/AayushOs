package dev.aayush.os;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * High-Priority Accessibility Overlay Host for Dynamic Now Bar.
 * Provides the system-privileged TYPE_ACCESSIBILITY_OVERLAY window token (layer 241000)
 * which sits directly in front of the Android status bar (layer 151000), guaranteeing
 * 100% reliable tap, swipe, and expansion touch events on the camera punch-hole cutout.
 */
public class NowBarAccessibilityService extends AccessibilityService {
    public static volatile NowBarAccessibilityService instance = null;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        android.util.Log.i("NowBarAccess", "NowBarAccessibilityService connected!");
        if (NowBarOverlayService.activeInstance != null) {
            NowBarOverlayService.activeInstance.rebindAccessibilityOverlay();
        } else if (NowBarOverlayService.isRunning) {
            Intent i = new Intent(this, NowBarOverlayService.class);
            i.setAction(NowBarOverlayService.ACTION_REFRESH);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    /**
     * Executes instant lock screen on Pixel 7 via system-level Accessibility GLOBAL_ACTION_LOCK_SCREEN.
     * MAC-CTL-04 & ISSUE-REV-01.
     */
    public static boolean lockScreen() {
        if (instance != null) {
            return instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }
}
