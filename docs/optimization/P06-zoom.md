# P2-1：缩放算法改造

> 涉及文件：ZoomableRecyclerView.kt, PhotoViewActivity.kt

---

## 现状问题

- 使用 `Matrix.postScale` + `animateScale` 手写插值，动画无减速曲线
- 边界检查 `applyBoundaries()` 每帧都做 `mapRect`，开销不必要
- 双击缩放目标值是 `max(3f, maxScale/2f)` 硬编码，无语义
- 缩放手势没有压力感知

---

## 改造步骤

### 第 1 步：改用 Spring Animation

```
当前：animateScale 用 post 循环 + 线性插值
改造：
  1. 添加依赖：implementation("androidx.dynamicanimation:dynamicanimation:1.0.1")
  2. 用 SpringAnimation 替代手动动画循环
  3. 弹簧参数：stiffness = SpringForce.STIFFNESS_MEDIUM, dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
```

```kotlin
private fun animateScale(targetScale: Float, focusX: Float, focusY: Float) {
    val spring = SpringForce(targetScale)
        .setStiffness(SpringForce.STIFFNESS_MEDIUM)
        .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)

    SpringAnimation(this, object : FloatPropertyCompat<ZoomableRecyclerView>("scale") {
        override fun getValue(obj: ZoomableRecyclerView): Float = obj.getScale()
        override fun setValue(obj: ZoomableRecyclerView, value: Float) {
            val ratio = value / obj.getScale()
            transformMatrix.postScale(ratio, ratio, focusX, focusY)
            applyBoundaries()
            obj.invalidate()
        }
    }).setSpring(spring).start()
}
```

- 缩放和回弹动画更自然，有物理弹性效果
- 不再用手动 `post(this)` 循环

### 第 2 步：边界修正优化

```
当前：每帧都做 mapRect + 9 个条件判断
改造：
  1. 只在 ACTION_UP / ACTION_CANCEL 时做边界修正
  2. 缩放过程中不做边界修正，允许轻微越界
  3. 缩放结束后触发 snap-back 回弹动画
```

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            checkAndBound() // 只在手势结束时修正
        }
    }
    return super.onTouchEvent(event)
}
```

- 减少每帧的计算开销
- 手势过程中更流畅

### 第 3 步：智能焦点双击缩放

```
当前：animateScale(max(3f, maxScale/2f), e.x, e.y)  // 硬编码 3 倍
改造：
  1. 如果当前 scale > 1.1，双击回到 1x（原始尺寸）
  2. 如果当前 scale <= 1.1，双击缩放到"刚好填满屏幕宽度"
     目标 scale = screenWidth / contentWidth
  3. 以双击点为锚点，让点击区域保持在视野中心
```

```kotlin
override fun onDoubleTap(e: MotionEvent): Boolean {
    val currentScale = getScale()
    if (currentScale > 1.1f) {
        animateScale(1f, width / 2f, height / 2f)
    } else {
        // 计算填满屏幕宽度所需的缩放比
        val contentWidth = displayRect.width() / currentScale
        val targetScale = (width.toFloat() / contentWidth).coerceIn(minScale, maxScale)
        animateScale(targetScale, e.x, e.y)
    }
    return true
}
```

### 第 4 步：压力感知（可选）

```kotlin
// 在 ScaleListener.onScale 中
val pressure = event.getAxisValue(MotionEvent.AXIS_PRESSURE)
val adjustedFactor = 1f + (scaleFactor - 1f) * pressure
```

- 高压力时缩放更灵敏，低压力时更平缓
- 需要设备支持压力感应，否则 fallback 到原始 scaleFactor

### 第 5 步：减少 dispatchDraw 开销

```
当前：每次 dispatchDraw 都 canvas.save() + canvas.concat(transformMatrix) + canvas.restore()
改造：
  1. 只在 scale != 1f 时才应用矩阵变换
  2. scale == 1f 时直接调用 super.dispatchDraw，跳过矩阵操作
```

```kotlin
override fun dispatchDraw(canvas: Canvas) {
    val scale = getScale()
    if (scale > 1.01f) {
        canvas.save()
        canvas.concat(transformMatrix)
        super.dispatchDraw(canvas)
        canvas.restore()
    } else {
        super.dispatchDraw(canvas)
    }
}
```

---

## 验证要点

- 双击缩放动画有弹簧效果，不生硬
- 边界回弹自然，无黑边
- 双击语义清晰：1x ↔ 填满屏幕
- 缩放过程中帧率稳定，无卡顿
