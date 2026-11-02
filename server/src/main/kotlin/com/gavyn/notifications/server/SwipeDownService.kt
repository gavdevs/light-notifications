package com.gavyn.notifications.server

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Opens this tool when the screen is swiped down on the toolbox.
 *
 * LightOS has no notification shade, so there is nothing above the toolbox to pull down.
 * This supplies one: while the toolbox is in front, a transparent strip across the top of
 * the screen watches for a downward stroke and starts [TARGET], which is this tool's own
 * `LightActivity`. That activity is `singleTask` and `exported`, so a second swipe brings the
 * existing list back to the front instead of stacking another copy of it.
 *
 * ### Why an accessibility service
 *
 * There is no way to observe a touch this app does not own. Gesture detection through the
 * accessibility API needs touch exploration switched on, which changes how the whole phone is
 * driven; `dispatchGesture` sends touches and cannot receive them. What is left is an overlay
 * window, and an overlay window that receives a touch has taken it — a swallowed [MotionEvent]
 * cannot be handed back to the app underneath once the stroke has begun.
 *
 * That cost is bounded rather than paid on every touch. `FLAG_NOT_TOUCH_MODAL` keeps every
 * touch outside the strip going exactly where it went before, and the window is never
 * focusable, so this can never cost a key — which matters on a phone whose volume wheel and
 * camera button are bound to other tools. Only a stroke that begins inside the strip is ours,
 * and on the toolbox the strip covers the empty band above the tool list.
 *
 * ### Why the strip has to be a real window and not a rule in a listener
 *
 * A window is what decides who receives a touch, and that decision is made before any listener
 * runs. A strip that covered something and merely ignored the touches there would leave that
 * something *unreachable*, which is worse than the problem it was fixing. That is why the strip
 * is full width and short, rather than running down the screen: on the toolbox the top band is
 * empty, so there is nothing to make unreachable.
 *
 * ### The launch needs the overlay appop
 *
 * On Android 14 a background activity start from a service is dropped silently without
 * `SYSTEM_ALERT_WINDOW` granted — `startActivity` throws nothing and returns nothing. So the
 * service refuses to fire until the grant is actually there, rather than animating a swipe that
 * opens nothing. See the manifest for the one-line adb grant.
 */
class SwipeDownService : AccessibilityService() {

    private companion object {
        const val TAG = "SwipeDownService"

        /** This tool's own activity, which the SDK generates from `lighttool.toml`'s `id`. */
        const val TARGET = "com.gavyn.notifications"
        const val TARGET_ACTIVITY = "com.thelightphone.sdk.LightActivity"

        const val STRIP_HEIGHT_DP = 56f
        const val TRIGGER_DP = 40f
        const val SLOP_DP = 12f
    }

    private var windowManager: WindowManager? = null
    private var strip: View? = null
    private var gesture = SwipeDownGesture(triggerPx = 0f, slopPx = 0f)

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Our own activity is not "the toolbox", but it is not something to hide the strip
        // for either: leaving it up avoids a gap between the swipe and the relaunch.
        if (pkg == TARGET) return
        if (isHomePackage(pkg)) attach() else detach()
    }

    /**
     * The strip only exists while the toolbox is in front. Anywhere else it would be a band
     * across the top of an app that never asked for it.
     */
    private fun attach() {
        if (strip != null) return
        val wm = windowManager ?: return
        if (!canStartActivities()) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted — swipe will not open $TARGET")
            return
        }

        val density = resources.displayMetrics.density
        gesture = SwipeDownGesture(
            triggerPx = TRIGGER_DP * density,
            slopPx = SLOP_DP * density,
        )

        val view = object : View(this) {}
        view.setBackgroundColor(Color.TRANSPARENT)
        // Not a control: this view draws nothing and exists only to be the window that receives
        // the stroke, so there is no content to expose to a screen reader.
        @Suppress("ClickableViewAccessibility")
        view.setOnTouchListener { v, event -> onTouch(v, event) }

        val screenPx = runCatching {
            // FLAG_LAYOUT_IN_SCREEN puts this window in the screen's own coordinates, so the
            // number to measure against is the one that counts the status bar.
            wm.currentWindowMetrics.bounds.height()
        }.getOrElse { resources.displayMetrics.heightPixels }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            stripHeight(screenPx, (STRIP_HEIGHT_DP * density).toInt()),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Never focusable: an overlay holding key focus would swallow the wheel and the
            // camera button, which is the one thing this must not do to the rest of the phone.
            // FLAG_NOT_TOUCH_MODAL is what keeps everything below the strip behaving normally.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = 0
        }

        runCatching { wm.addView(view, params) }
            .onSuccess { strip = view }
            .onFailure { Log.w(TAG, "could not add strip window", it) }
    }

    private fun detach() {
        val view = strip ?: return
        strip = null
        gesture.cancel()
        runCatching { windowManager?.removeView(view) }
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean = try {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture.down(event.rawX, event.rawY)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val ours = gesture.move(event.rawX, event.rawY)
                if (gesture.stage == SwipeStage.Armed) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
                ours
            }
            MotionEvent.ACTION_UP -> {
                val fires = gesture.up()
                if (fires) open()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                gesture.cancel()
                true
            }
            else -> false
        }
    } catch (e: Throwable) {
        // An accessibility service that throws often enough is stood down by the system, and
        // a stood-down service is a gesture that silently stopped existing.
        Log.w(TAG, "dropping stroke", e)
        gesture.cancel()
        false
    }

    private fun open() {
        val intent = Intent().apply {
            setClassName(TARGET, TARGET_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "could not open $TARGET", it) }
    }

    /**
     * Whether `startActivity` from here can work at all. Checked up front because on Android 14
     * the failure is silent, and a swipe that animates and opens nothing reads as a broken
     * gesture rather than a missing grant.
     */
    private fun canStartActivities(): Boolean = android.provider.Settings.canDrawOverlays(this)

    override fun onInterrupt() {
        gesture.cancel()
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }
}
