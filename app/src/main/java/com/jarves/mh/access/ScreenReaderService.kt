package com.jarves.mh.access

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** One captured screen, tagged with the app it belongs to. */
data class ScreenSnapshot(
    val packageName: String = "",
    val text: String = "",
    val capturedAt: Long = 0L,
)

/**
 * Read-only screen capture.
 *
 * This service deliberately does not request `canPerformGestures` and never calls a
 * click or gesture API: it only mirrors what is currently on screen. Touch control is a
 * separate decision that needs its own review, so it stays out of this first step.
 */
class ScreenReaderService : AccessibilityService() {

    private val throttled = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected.value = true
        ScreenController.service = this
        ScreenController.reader = this
        instance = this
        requestRefresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            -> scheduleRefresh()
            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        connected.value = false
        if (ScreenController.service === this) ScreenController.service = null
        if (ScreenController.reader === this) ScreenController.reader = null
        if (instance === this) instance = null
        ScreenController.hideApproval()
        super.onDestroy()
    }

    /**
     * Content-changed events arrive in bursts while a list scrolls or a screen animates.
     * One capture per burst is plenty, and it keeps the node traversal off the busiest
     * part of the event stream.
     */
    private fun scheduleRefresh() {
        if (!throttled.compareAndSet(false, true)) return
        mainHandler.postDelayed({
            throttled.set(false)
            capture()
        }, REFRESH_INTERVAL_MS)
    }

    /** Re-captures immediately; callers use this when they cannot wait for an event. */
    internal fun requestRefresh() {
        mainHandler.post { capture() }
    }

    /**
     * Mirrors the active window into [snapshot].
     *
     * The app's own windows are skipped on purpose: the approval card and the chat screen
     * are drawn by this very package, so mirroring them would overwrite the screen the user
     * actually wants the agent to see. Keeping the last foreign screen is the whole point —
     * the user has to be inside this app to type the message that carries it.
     */
    private fun capture() {
        val node = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val pkg = runCatching { node.packageName?.toString().orEmpty() }.getOrDefault("")
        if (pkg == packageName) return
        val text = runCatching {
            buildString {
                append("package: ").append(pkg.ifBlank { "?" }).appendLine()
                node.collectReadableText(this, 0)
            }
        }.getOrNull()?.trim()
        if (text.isNullOrBlank()) return
        snapshot.value = ScreenSnapshot(pkg, text, System.currentTimeMillis())
        lastCaptureAt = System.currentTimeMillis()
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 400L

        private val snapshot = MutableStateFlow(ScreenSnapshot())
        val screenSnapshot: StateFlow<ScreenSnapshot> = snapshot

        private val connected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = connected

        @Volatile
        private var instance: ScreenReaderService? = null

        @Volatile
        private var lastCaptureAt = 0L

        fun currentText(): String = snapshot.value.text

        /** Package of the mirrored screen, or null when nothing foreign was captured yet. */
        fun currentPackageName(): String? = snapshot.value.packageName.takeIf { it.isNotBlank() }

        fun snapshotAgeMs(): Long =
            snapshot.value.capturedAt.takeIf { it > 0L }?.let { System.currentTimeMillis() - it } ?: -1L

        /**
         * Waits for a capture newer than [after], nudging the service to look again.
         *
         * The service refreshes on accessibility events, which is too slow right after a
         * gesture: the agent would read the screen from before its own tap and conclude that
         * nothing happened. This forces a fresh capture instead of returning the old one.
         */
        fun awaitSnapshotAfter(
            after: Long,
            timeoutMs: Long = 1_600L,
            expectedPackage: String? = null,
        ): String {
            val svc = instance ?: return currentText()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val current = snapshot.value
                val fresh = current.capturedAt > after
                val rightApp = expectedPackage == null || current.packageName == expectedPackage
                if (fresh && rightApp) return current.text
                svc.requestRefresh()
                try {
                    Thread.sleep(80L)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            return currentText()
        }

        /**
         * True when the user switched the service on in system settings. A service cannot
         * enable itself and there is no API to request it, so reading the secure setting is
         * the only reliable check.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ScreenReaderService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private const val MAX_DEPTH = 40
private const val MAX_OUTPUT_CHARS = 120_000

/**
 * Depth-first dump of visible text, labels and click targets.
 *
 * `(tap)` only marks what a later step could press; nothing here performs an action.
 */
private fun AccessibilityNodeInfo.collectReadableText(out: StringBuilder, depth: Int) {
    if (depth > MAX_DEPTH || out.length >= MAX_OUTPUT_CHARS) return
    if (!isVisibleToUser) return

    val label = listOfNotNull(
        text?.toString()?.trim()?.takeIf(String::isNotEmpty),
        contentDescription?.toString()?.trim()?.takeIf(String::isNotEmpty),
        hintText?.toString()?.trim()?.takeIf(String::isNotEmpty),
    ).distinct().joinToString(" · ")

    if (label.isNotEmpty()) {
        repeat(depth.coerceAtMost(6)) { out.append("  ") }
        out.append(label)
        val bounds = Rect()
        runCatching { getBoundsInScreen(bounds) }
        if (!bounds.isEmpty) out.append(" [").append(bounds.left).append(',').append(bounds.top).append(']')
        if (isClickable) out.append(" (tap)")
        out.append('\n')
    }

    for (index in 0 until childCount) {
        if (out.length >= MAX_OUTPUT_CHARS) return
        val child = runCatching { getChild(index) }.getOrNull() ?: continue
        child.collectReadableText(out, depth + 1)
        runCatching { child.recycle() }
    }
}
