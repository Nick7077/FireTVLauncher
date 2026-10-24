package com.nick.tvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the Home button open our launcher on Fire OS, which otherwise always sends it to
 * Amazon's launcher.
 *
 * <p>Primary path: we filter the Home key itself and consume it. If we let it through,
 * Android blocks other apps from starting activities for 5 seconds after Home is pressed,
 * so the Amazon screen would show for that long before we could replace it.
 *
 * <p>Fallback path: whenever Amazon's home screen still comes to the front some other way
 * (boot, an app exiting to home), we bring our launcher up over it.
 */
public class HomeWatcherService extends AccessibilityService {
    private static final String TAG = "HomeWatcher";
    // The stock launcher package also hosts Settings, so only its home screen is redirected.
    private static final String STOCK_HOME_PREFIX = "com.amazon.tv.launcher.ui.HomeActivity";
    private static final long DEBOUNCE_MS = 500;
    private static final long LONG_PRESS_MS = 600;

    private long lastRedirect;
    private boolean longPressHandled;

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Every key on the remote passes through here; never log them.
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;

        long heldMs = event.getEventTime() - event.getDownTime();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                longPressHandled = false;
            } else if (!longPressHandled && heldMs >= LONG_PRESS_MS) {
                longPressHandled = true;
                openSettings();
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && !longPressHandled) {
            // Amazon's long-press quick-settings overlay needs a signature permission,
            // so a long press opens full Settings instead.
            if (heldMs >= LONG_PRESS_MS) {
                openSettings();
            } else {
                showLauncher();
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !AppRepository.STOCK_LAUNCHER.contentEquals(pkg)) return;
        CharSequence cls = event.getClassName();
        if (cls == null || !cls.toString().startsWith(STOCK_HOME_PREFIX)) return;
        showLauncher();
    }

    private void showLauncher() {
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirect < DEBOUNCE_MS) return;
        lastRedirect = now;

        safeStart(new Intent(this, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    private void openSettings() {
        safeStart(AppRepository.settingsIntent(getPackageManager()));
    }

    /** A crash here would unbind the service and leave Home going to Amazon's screen. */
    private void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Could not start " + intent.getComponent(), e);
        }
    }

    @Override
    public void onInterrupt() {
    }
}
