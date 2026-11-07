package com.example.focusesp

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Interactive full-screen overlay shown after a short-form surface (YouTube
 * Shorts or Instagram Reels) is blocked.
 *
 * It is owned by [FocusAccessibilityService] and uses
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY], so it never needs
 * the SYSTEM_ALERT_WINDOW permission and is not subject to background activity
 * launch restrictions. The overlay is opaque and touchable, which prevents the
 * user from interacting with the Short underneath it.
 *
 * Methods must be called from the main thread (accessibility callbacks and view
 * callbacks already run there).
 */
class ShortsBlockingOverlay(
    private val context: Context,
    private val messageProvider: () -> String,
    private val onDismiss: () -> Unit
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var messageView: TextView? = null
    private var eyebrowView: TextView? = null

    val isShowing: Boolean
        get() = overlayView != null

    fun show(eyebrow: String = DEFAULT_EYEBROW): Boolean {
        if (overlayView != null) {
            messageView?.text = messageProvider()
            eyebrowView?.text = eyebrow
            return true
        }

        return try {
            val view = buildView(eyebrow)
            windowManager.addView(view, buildLayoutParams())
            overlayView = view
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to show the blocking overlay", t)
            overlayView = null
            messageView = null
            eyebrowView = null
            false
        }
    }

    fun hide() {
        val view = overlayView ?: return
        overlayView = null
        messageView = null
        eyebrowView = null
        try {
            windowManager.removeViewImmediate(view)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to remove the blocking overlay", t)
        }
    }

    private fun dismiss() {
        hide()
        onDismiss()
    }

    private fun buildView(eyebrowText: String): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(48))
            setBackgroundColor(Color.TRANSPARENT)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#06101F"),
                    Color.parseColor("#0B1E3A"),
                    Color.parseColor("#02060D")
                )
            )
            isClickable = true
            isFocusable = true
            contentDescription = "FocusESP blocking screen"
            setOnClickListener { /* Consume taps outside the dismiss button. */ }
            @Suppress("DEPRECATION")
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        val eyebrow = TextView(context).apply {
            text = eyebrowText
            setTextColor(ACCENT_BLUE)
            textSize = 13f
            letterSpacing = 0.12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        eyebrowView = eyebrow

        val message = TextView(context).apply {
            text = messageProvider()
            setTextColor(TEXT_PRIMARY)
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
        }
        messageView = message

        val hint = TextView(context).apply {
            text = "Take a breath, then continue."
            setTextColor(TEXT_SECONDARY)
            textSize = 15f
            gravity = Gravity.CENTER
        }

        val dismissButton = TextView(context).apply {
            text = "Got it"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(36), dp(16), dp(36), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(ACCENT_BLUE_DEEP)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Dismiss the blocking screen and continue"
            setOnClickListener { dismiss() }
        }

        root.addView(eyebrow, wrapParams(topMargin = 0))
        root.addView(message, wrapParams(topMargin = 18))
        root.addView(hint, wrapParams(topMargin = 18))
        root.addView(dismissButton, wrapParams(topMargin = 32))

        return root
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "FocusESP content block"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun wrapParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = topMargin
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAG = "ShortsBlockingOverlay"
        const val DEFAULT_EYEBROW = "YOUTUBE SHORTS BLOCKED"
        val ACCENT_BLUE: Int = Color.parseColor("#60A5FA")
        val ACCENT_BLUE_DEEP: Int = Color.parseColor("#2F6FED")
        val TEXT_PRIMARY: Int = Color.parseColor("#F1F5F9")
        val TEXT_SECONDARY: Int = Color.parseColor("#B8C5D6")
    }
}
