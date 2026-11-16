package com.strokedot.extradim;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class DimShortcutService extends AccessibilityService {
    private AccessibilityButtonController.AccessibilityButtonCallback callback;
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityButtonController controller = getAccessibilityButtonController();
        callback = new AccessibilityButtonController.AccessibilityButtonCallback() {
            @Override public void onClicked(AccessibilityButtonController c) {
                DimController.toggle(DimShortcutService.this);
            }
        };
        try { controller.registerAccessibilityButtonCallback(callback); }
        catch (RuntimeException ignored) { }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (callback != null) {
            try { getAccessibilityButtonController().unregisterAccessibilityButtonCallback(callback); }
            catch (Exception ignored) { }
        }
        super.onDestroy();
    }
}
