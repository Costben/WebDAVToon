package erl.webdavtoon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * 支持缩放的 RecyclerView
 * 直接在 RecyclerView 内部实现矩阵变换，减少布局层级
 */
class ZoomableRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val TAG = "ZoomableRV"

    private val transformMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    
    private var minScale = 1f
    private var maxScale = 5f
    
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    
    private val displayRect = RectF()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var moveLogCounter = 0
    
    /**
     * 是否处于多选模式。
     * 在多选模式下，禁用缩放和平移功能，恢复原生滚动行为。
     */
    var isSelectionMode = false
        set(value) {
            field = value
            if (value) {
                // 进入多选模式时重置缩放
                resetScale(true)
            }
        }

    interface OnTapListener {
        fun onSingleTap(xFraction: Float, yFraction: Float): Boolean
        fun onLongPress()
    }

    var onTapListener: OnTapListener? = null
    var onScaleChangedListener: ((scale: Float) -> Unit)? = null

    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        // 允许子项绘制到 View 之外（缩放后）
        clipChildren = false
    }

    fun setMaxScale(scale: Float) {
        this.maxScale = scale
    }

    fun getScale(): Float {
        transformMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    fun resetScale(animate: Boolean = true) {
        if (animate) {
            animateScale(1f, width / 2f, height / 2f)
        } else {
            transformMatrix.reset()
            invalidate()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        
        // 简化 dispatchDraw：直接应用矩阵变换到整个 View 坐标系。
        // 这样 Matrix 缩放时的 (0,0) 就是 View 的左上角，与 ScaleGestureDetector 的焦点坐标系一致。
        canvas.concat(transformMatrix)
        
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // 如果处于多选模式，完全退回到原生 RecyclerView 的拦截逻辑
        if (isSelectionMode) {
            return super.onInterceptTouchEvent(e)
        }
        
        // 如果有多个手指，我们可能要缩放，拦截它
        if (e.pointerCount > 1) {
            return true
        }

        val scale = getScale()
        val isScaled = scale > 1.01f
        
        // 在缩放状态下，我们可能需要拦截滑动来平移画布
        if (isScaled && e.actionMasked == MotionEvent.ACTION_MOVE) {
            val dx = Math.abs(e.x - lastTouchX)
            val dy = Math.abs(e.y - lastTouchY)
            
            // 如果放大后，内容宽度大于视图宽度，且水平滑动明显，拦截用于左右平移
            if (displayRect.width() > width && dx > dy * 1.2f) {
                return true
            }
        }
        
        // 注意：不要在这里再次进行逆变换。
        // 因为 dispatchTouchEvent 已经对分发给 super 的事件进行了逆变换。
        // 这里的 super.onInterceptTouchEvent(e) 接收到的 e 已经是变换后的坐标（如果从 dispatchTouchEvent 进来）。
        
        return super.onInterceptTouchEvent(e)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 如果处于多选模式，禁用所有缩放和平移处理，完全交给 super
        if (isSelectionMode) {
            return super.dispatchTouchEvent(ev)
        }

        // 处理基础坐标记录
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            lastTouchX = ev.x
            lastTouchY = ev.y
        }
        
        val wasScaling = scaleDetector.isInProgress
        scaleDetector.onTouchEvent(ev)
        val isScaling = scaleDetector.isInProgress
        
        // 我们需要传递事件给 gestureDetector 来处理单击和双击
        val gestureHandled = gestureDetector.onTouchEvent(ev)
        
        if (ev.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            // 当一个手指抬起时，重置 lastTouch 坐标为剩下的那个手指的位置，防止跳变
            // 必须在 isScaling || wasScaling 判断前处理，确保缩放结束时的坐标衔接正确
            val pointerIndex = ev.actionIndex
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            lastTouchX = ev.getX(newPointerIndex)
            lastTouchY = ev.getY(newPointerIndex)
        }

        if (isScaling || wasScaling) {
            // 如果正在缩放，记录当前最后触摸点（对于多点触摸，这通常是主手指位置）
            // 在缩放结束的瞬间，这个记录将作为接下来拖拽的起始点
            if (!isScaling && wasScaling) {
                // 缩放刚结束，由于 ACTION_POINTER_UP 可能已经更新了 lastTouchX/Y，
                // 这里我们不再覆盖它，除非不是 ACTION_POINTER_UP
                if (ev.actionMasked != MotionEvent.ACTION_POINTER_UP) {
                    lastTouchX = ev.x
                    lastTouchY = ev.y
                }
            } else {
                lastTouchX = ev.x
                lastTouchY = ev.y
            }
            // 如果正在缩放，不传递给 super (RecyclerView)，防止同时滚动
            return true
        }
        
        val scale = getScale()
        val isScaled = scale > 1.01f
        
        if (isScaled) {
            // 在缩放状态下，实现“自由拖拽”：同时处理 X 和 Y 轴移动
            if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
                val dx = ev.x - lastTouchX
                val dy = ev.y - lastTouchY
                
                moveLogCounter++
                if (moveLogCounter % 50 == 0) {
                    LogManager.log("DRAG (v1.1.7): dx=${"%.2f".format(dx)}, dy=${"%.2f".format(dy)}", Log.DEBUG, TAG)
                }

                // 1. 处理水平平移 (Matrix)
                // 只有图片宽度超出视图时，才需要手动平移 X 轴
                if (displayRect.width() > width) {
                    // 如果有水平移动分量，更新矩阵。不直接 return true，允许 Y 轴分量继续向下传递。
                    transformMatrix.postTranslate(dx, 0f)
                    applyBoundaries()
                    invalidate()
                }
            }

            // 2. 处理垂直滚动 (RecyclerView)
            // 将事件（逆变换后）传给 super，让 RecyclerView 原生处理 Y 轴滚动和各种点击。
            val transformedEvent = MotionEvent.obtain(ev)
            val inverseMatrix = Matrix()
            if (transformMatrix.invert(inverseMatrix)) {
                transformedEvent.transform(inverseMatrix)
                
                // 注意：这里我们不再拦截水平滑动，而是让它们同时发生。
                // X 轴由 Matrix 移动，Y 轴由 RecyclerView 滚动。
                val handled = super.dispatchTouchEvent(transformedEvent) || gestureHandled
                transformedEvent.recycle()
                
                lastTouchX = ev.x
                lastTouchY = ev.y
                return handled
            }
            transformedEvent.recycle()
        }
        
        val handled = super.dispatchTouchEvent(ev) || gestureHandled
        
        lastTouchX = ev.x
        lastTouchY = ev.y
        
        return handled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 在缩放状态下，主要的平移逻辑已经移动到 dispatchTouchEvent 中了，
        // 这里主要处理 ACTION_UP 和基础事件更新。
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                checkAndBound()
            }
        }
        
        return super.onTouchEvent(event)
    }

    /**
     * 实时应用边界限制，防止出现黑边
     */
    private fun applyBoundaries() {
        mapDisplayRect()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        var deltaX = 0f
        var deltaY = 0f

        // 水平方向修正：如果宽度大于 View，限制左右边界；如果小于，则居中
        if (displayRect.width() <= viewWidth) {
            deltaX = (viewWidth - displayRect.width()) / 2f - displayRect.left
        } else {
            if (displayRect.left > 0) {
                deltaX = -displayRect.left
            } else if (displayRect.right < viewWidth) {
                deltaX = viewWidth - displayRect.right
            }
        }

        // 垂直方向逻辑重构：
        // 在 Webtoon (RecyclerView) 模式下，垂直方向的内容是由 RecyclerView 自己管理的。
        // Matrix 变换只负责处理“由于缩放产生的局部偏移”。
        // 如果缩放后的显示区域高度小于 View 高度，说明这张（或这组）图片无法填满屏幕，我们需要让它垂直居中。
        if (displayRect.height() <= viewHeight) {
            deltaY = (viewHeight - displayRect.height()) / 2f - displayRect.top
        } else {
            // 如果缩放后的区域已经超过了屏幕高度，我们完全不应该在 applyBoundaries 里修正 deltaY。
            // 因为任何 deltaY 的强制修正都会与 RecyclerView 的原生滚动偏移（scrollY）产生冲突，
            // 导致“回弹”或“跳动”。
            // 这里我们保持 deltaY = 0f，让 RecyclerView 自由处理垂直滚动。
            deltaY = 0f
        }

        if (deltaX != 0f || deltaY != 0f) {
            moveLogCounter++
            if (moveLogCounter % 20 == 0) {
                LogManager.log("applyBoundaries: rect=$displayRect, dx=$deltaX, dy=$deltaY", Log.DEBUG, TAG)
            }
            transformMatrix.postTranslate(deltaX, deltaY)
            mapDisplayRect()
        }
    }

    private fun getMatrixValuesString(): String {
        val values = FloatArray(9)
        transformMatrix.getValues(values)
        return values.joinToString(", ")
    }

    private fun checkAndBound() {
        val scale = getScale()
        if (scale < minScale) {
            animateScale(minScale, width / 2f, height / 2f)
            return
        }

        applyBoundaries()
        invalidate()
    }

    private fun mapDisplayRect() {
        displayRect.set(0f, 0f, width.toFloat(), height.toFloat())
        transformMatrix.mapRect(displayRect)
    }

    private fun animateScale(targetScale: Float, focusX: Float, focusY: Float) {
        val currentScale = getScale()
        post(object : Runnable {
            val startTime = System.currentTimeMillis()
            val duration = 200f
            override fun run() {
                val t = min(1f, (System.currentTimeMillis() - startTime) / duration)
                val scale = currentScale + t * (targetScale - currentScale)
                val ratio = scale / getScale()
                
                // 使用传入的 focusX 和 focusY 作为缩放中心
                transformMatrix.postScale(ratio, ratio, focusX, focusY)
                
                // 动画缩放时实时修正边界
                applyBoundaries()
                
                invalidate()
                if (t < 1f) post(this)
                else checkAndBound()
            }
        })
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            // 缩放结束时，由于 ScaleGestureDetector 的 focalPoint 已经变化，
            // 我们不在这里重置 lastTouchX，因为 dispatchTouchEvent 里的 ACTION_POINTER_UP 逻辑更精准。
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val currentScale = getScale()
            val targetScale = currentScale * scaleFactor
            
            if (targetScale in (minScale * 0.8f)..(maxScale * 1.2f)) {
                LogManager.log("onScale: factor=$scaleFactor, focusX=${detector.focusX}, focusY=${detector.focusY}", Log.DEBUG, TAG)
                // 使用双指中心作为缩放原点，修复“焦点永远在顶部”的问题
                transformMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                
                // 缩放时实时修正边界
                applyBoundaries()
                
                onScaleChangedListener?.invoke(getScale())
                invalidate()
            }
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return onTapListener?.onSingleTap(
                (e.x / width.toFloat()).coerceIn(0f, 0.9999f),
                (e.y / height.toFloat()).coerceIn(0f, 0.9999f)
            ) ?: false
        }

        override fun onLongPress(e: MotionEvent) {
            // 长按时通知监听器，用于呼出 UI
            onTapListener?.onLongPress()
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val currentScale = getScale()
            if (currentScale > 1.1f) {
                animateScale(1f, width / 2f, height / 2f)
            } else {
                val targetScale = max(3f, maxScale / 2f).coerceAtMost(maxScale)
                animateScale(targetScale, e.x, e.y)
            }
            return true
        }
    }
}
