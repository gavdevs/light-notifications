package com.loosewire.notifications.server

import kotlin.math.abs

/**
 * Where a swipe in the top strip is, and whether releasing it would open the tool.
 *
 * `fires` is the only thing a caller should branch on for feedback: a haptic on the
 * transition of `fires` means "a release now would work", and one on the transition of
 * `stage` would buzz for a stroke that is being given away as a scroll.
 */
enum class SwipeStage(val fires: Boolean) {
    Idle(fires = false),
    Watching(fires = false),
    Armed(fires = true),
    Cancelled(fires = false),
}

/**
 * A downward stroke, measured from where the finger landed.
 *
 * The gesture only ever runs inside the strip, so the finger always starts at the top of
 * the screen and "down" is unambiguous. Both failures it guards against are ordinary
 * things a thumb does near the top edge:
 *
 *  - **moving up**, which is someone reaching for a tool and pulling the list instead;
 *  - **moving sideways**, which is a scroll or a page turn that happened to begin in the
 *    strip and must not be mistaken for this.
 *
 * A cancellation is permanent for the stroke. Once a gesture has been given away it cannot
 * come back, because the finger has already been told the strip is not going to do anything
 * and re-arming on the way down would fire on a stroke the user had abandoned.
 *
 * Free of Android types so it can be exercised without one. See `SwipeDownTest`.
 */
class SwipeDownGesture(
    private val triggerPx: Float,
    private val slopPx: Float,
) {
    var stage: SwipeStage = SwipeStage.Idle
        private set

    private var startX = 0f
    private var startY = 0f

    fun down(x: Float, y: Float) {
        startX = x
        startY = y
        stage = SwipeStage.Watching
    }

    /** Returns whether the stroke still belongs to the strip. A `false` here is a scroll. */
    fun move(x: Float, y: Float): Boolean {
        if (stage == SwipeStage.Cancelled) return false
        if (stage == SwipeStage.Idle) return false

        val dy = y - startY
        val dx = x - startX

        if (dy < -slopPx) {
            stage = SwipeStage.Cancelled
            return false
        }
        // Sideways dominance, checked only once past the slop so that the jitter of a
        // finger resting on the screen is not a cancellation.
        if (abs(dx) > slopPx && abs(dx) > dy) {
            stage = SwipeStage.Cancelled
            return false
        }

        stage = if (dy >= triggerPx) SwipeStage.Armed else SwipeStage.Watching
        return true
    }

    /** Whether this stroke fires, and resets for the next one. */
    fun up(): Boolean {
        val fires = stage == SwipeStage.Armed
        stage = SwipeStage.Idle
        return fires
    }

    fun cancel() {
        stage = SwipeStage.Idle
    }
}

/**
 * The strip's height, given the screen.
 *
 * Capped at a quarter of the screen for the same reason BrightControl caps its edge strips:
 * the height is a setting, but a dp value cannot know how tall this panel is, and a strip
 * taller than the screen is a window with negative height — which the WindowManager takes as
 * `WRAP_CONTENT` on a view that draws nothing, i.e. a gesture that silently stopped existing.
 *
 * Free of Android types so it can be exercised without one.
 */
fun stripHeight(screenPx: Int, desiredPx: Int): Int =
    desiredPx.coerceIn(1, (screenPx / 4).coerceAtLeast(1))

/**
 * Packages whose screen is the toolbox, where the strip belongs.
 *
 * The strip only exists while one of these is in front. Anywhere else it would be a band
 * across the top of an app that never asked for it, and — because a window that receives a
 * touch has taken it — one that cannot be given back once the stroke has begun.
 *
 * `com.lightos` is LightOS on hardware. The other two are the emulator, which is a different
 * package by design so it cannot share the real LightOS uid.
 */
val HOME_PACKAGES: Set<String> = setOf(
    "com.lightos",
    "com.lightos.launcher",
    "com.thelightphone.sdk.emulator",
)

fun isHomePackage(pkg: String?): Boolean = pkg != null && pkg in HOME_PACKAGES
