package erl.webdavtoon

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.math.abs

class ExpandedEdgeDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var trackingStartX = -1f
    private var trackingStartY = -1f
    private var trackingActive = false
    private var triggered = false

    private val edgeWidth: Int
        get() = (resources.displayMetrics.widthPixels * widthFraction).toInt()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Let DrawerLayout's built-in detection handle whatever it can first.
        if (super.onInterceptTouchEvent(ev)) {
            resetTracking()
            return true
        }

        // Don't run the custom edge logic while a drawer is already open — let normal
        // touch handling (closing on tap-outside, swipe to close, server item swipe) run unchanged.
        if (isDrawerOpen(GravityCompat.START)) {
            resetTracking()
            return false
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // edgeWidth <= 0 means the swipe-to-open area is disabled entirely.
                if (edgeWidth > 0 && ev.x <= edgeWidth) {
                    trackingStartX = ev.x
                    trackingStartY = ev.y
                    trackingActive = true
                    triggered = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (trackingActive && !triggered) {
                    val dx = ev.x - trackingStartX
                    val dy = ev.y - trackingStartY
                    if (dx > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                        triggered = true
                        openDrawer(GravityCompat.START, true)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> resetTracking()
        }

        return false
    }

    private fun resetTracking() {
        trackingActive = false
        triggered = false
        trackingStartX = -1f
        trackingStartY = -1f
    }

    companion object {
        const val DEFAULT_EDGE_FRACTION = 1f / 3f

        /**
         * Fraction (0f..1f) of the screen width that acts as the swipe-to-open trigger area.
         * Process-wide and @Volatile so a change in Settings takes effect immediately without
         * recreating the host Activity. 0f disables swipe-to-open entirely.
         */
        @Volatile
        var widthFraction: Float = DEFAULT_EDGE_FRACTION
            set(value) {
                field = value.coerceIn(0f, 1f)
            }

        fun setWidthPercent(percent: Int) {
            widthFraction = percent.coerceIn(0, 100) / 100f
        }
    }
}
