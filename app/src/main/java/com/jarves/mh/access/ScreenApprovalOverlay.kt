package com.jarves.mh.access

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.jarves.mh.R
import com.jarves.mh.model.ScreenActionRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Approval card drawn by the accessibility service, not by the activity.
 *
 * A normal dialog can only be seen while this app is in the foreground, which is exactly the
 * wrong moment: the agent wants to act on *another* app, so the user has to stay there. An
 * accessibility overlay is the one window type that needs no extra permission and can sit on
 * top of whatever is on screen.
 *
 * It is removed before the gesture is dispatched — otherwise the tap would land on the card
 * itself instead of the app underneath.
 */
class ScreenApprovalOverlay(
    private val service: AccessibilityService,
    private val onApprove: () -> Unit,
    private val onReject: () -> Unit,
    private val onExpired: () -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var windowManager: WindowManager? = null

    val isShowing: Boolean get() = root != null

    /** Returns false when the window could not be added, so the caller can fall back. */
    fun show(request: ScreenActionRequest, target: String): Boolean {
        val attached = runOnMain {
            if (root != null) return@runOnMain false
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@runOnMain false
            val card = buildCard(request, target)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM
                horizontalMargin = 0.04f
                verticalMargin = 0.03f
            }
            runCatching { wm.addView(card, params) }
                .onSuccess {
                    windowManager = wm
                    root = card
                }
                .isSuccess
        } ?: false
        if (attached) {
            handler.postDelayed({
                // The helper gives up after 120 s; answer first so the agent is not left hanging.
                if (root != null) {
                    dismiss()
                    onExpired()
                }
            }, EXPIRY_MS)
        }
        return attached
    }

    fun dismiss() {
        runOnMain {
            handler.removeCallbacksAndMessages(null)
            val view = root ?: return@runOnMain
            root = null
            runCatching { windowManager?.removeView(view) }
            windowManager = null
        }
    }

    private fun buildCard(request: ScreenActionRequest, target: String): View {
        val density = service.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val night = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val cardColor = if (night) Color.parseColor("#F21F2124") else Color.parseColor("#F2FFFFFF")
        val primary = if (night) Color.parseColor("#FFF1F3F4") else Color.parseColor("#FF1A1C1E")
        val secondary = if (night) Color.parseColor("#FFB0B6BD") else Color.parseColor("#FF5F6368")
        val accent = Color.parseColor("#FF4C8DF6")

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(cardColor)
                setStroke(dp(1), if (night) Color.parseColor("#33FFFFFF") else Color.parseColor("#1F000000"))
            }
            elevation = dp(12).toFloat()
        }

        card.addView(
            TextView(service).apply {
                text = service.getString(R.string.screen_control_request_title)
                setTextColor(primary)
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        if (target.isNotBlank()) {
            card.addView(
                TextView(service).apply {
                    text = service.getString(R.string.screen_control_request_app, target)
                    setTextColor(secondary)
                    textSize = 12f
                    setPadding(0, dp(4), 0, 0)
                },
            )
        }
        card.addView(
            TextView(service).apply {
                text = request.describe()
                setTextColor(primary)
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(10), 0, 0)
            },
        )
        if (request.reason.isNotBlank()) {
            card.addView(
                TextView(service).apply {
                    text = request.reason
                    setTextColor(secondary)
                    textSize = 12f
                    setPadding(0, dp(6), 0, 0)
                },
            )
        }

        val buttons = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(14), 0, 0)
        }
        buttons.addView(
            actionButton(
                label = service.getString(R.string.screen_control_deny),
                textColor = secondary,
                fill = Color.TRANSPARENT,
            ) {
                dismiss()
                onReject()
            },
        )
        buttons.addView(
            actionButton(
                label = service.getString(R.string.screen_control_approve),
                textColor = Color.WHITE,
                fill = accent,
            ) {
                dismiss()
                onApprove()
            },
        )
        card.addView(buttons)
        return card
    }

    private fun actionButton(
        label: String,
        textColor: Int,
        fill: Int,
        onClick: () -> Unit,
    ): TextView {
        val density = service.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        return TextView(service).apply {
            text = label
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(fill)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.marginStart = dp(10)
            layoutParams = params
        }
    }

    /**
     * WindowManager calls must happen on the main thread, but the mailbox watcher runs on IO.
     * Blocking that watcher for the few milliseconds an add/remove takes keeps the caller
     * synchronous, which is what the "did the card really appear?" fallback needs.
     */
    private fun <T> runOnMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return runCatching(block).getOrNull()
        }
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<Any?>(1)
        handler.post {
            holder[0] = runCatching(block).getOrNull()
            latch.countDown()
        }
        if (!latch.await(2, TimeUnit.SECONDS)) return null
        @Suppress("UNCHECKED_CAST")
        return holder[0] as? T
    }

    private companion object {
        const val EXPIRY_MS = 110_000L
    }
}
