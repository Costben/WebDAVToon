package erl.webdavtoon

import android.animation.TimeInterpolator
import android.graphics.PointF
import android.view.animation.PathInterpolator

class PinchZoomState {
    var progress = 0f
    var currentScale = 1.0f
    var focusPoint = PointF()
    
    // 动画插值器：采用库中类似的曲线
    val interpolator: TimeInterpolator = PathInterpolator(0.215f, 0.61f, 0.355f, 1f)

    fun calculateProgress(scale: Float, targetRatio: Float): Float {
        return if (scale > 1.0f) {
            (scale - 1.0f) / (targetRatio - 1.0f)
        } else {
            (1.0f - scale) / (1.0f - targetRatio)
        }.coerceIn(0f, 1f)
    }
}
