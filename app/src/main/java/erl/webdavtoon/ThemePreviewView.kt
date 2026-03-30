package erl.webdavtoon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt

class ThemePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    
    var colorPrimary: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var colorSecondary: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var colorTertiary: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var colorSurface: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val size = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f
        
        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)
        
        // Draw 4 quadrants
        // Top Left: Surface
        paint.color = colorSurface
        canvas.drawArc(rectF, 180f, 90f, true, paint)
        
        // Top Right: Primary
        paint.color = colorPrimary
        canvas.drawArc(rectF, 270f, 90f, true, paint)
        
        // Bottom Right: Secondary
        paint.color = colorSecondary
        canvas.drawArc(rectF, 0f, 90f, true, paint)
        
        // Bottom Left: Tertiary
        paint.color = colorTertiary
        canvas.drawArc(rectF, 90f, 90f, true, paint)
        
        // Draw border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x20000000 // Light gray border
        canvas.drawCircle(cx, cy, radius - 1f, paint)
        paint.style = Paint.Style.FILL
    }
}
