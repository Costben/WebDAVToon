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
        get() = resources.displayMetrics.widthPixels / 3

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
                if (ev.x <= edgeWidth) {
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
}
