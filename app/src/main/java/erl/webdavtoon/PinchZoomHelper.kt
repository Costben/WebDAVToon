package erl.webdavtoon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.Rect
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import kotlin.math.roundToInt

class PinchZoomHelper(
    private val recyclerView: RecyclerView,
    private val zoomContainer: FrameLayout,
    private val onSpanCountChanged: (Int) -> Unit
) {
    private val state = PinchZoomState()
    private val scaleGestureDetector: ScaleGestureDetector
    
    // Config
    private var initialSpanCount: Int = 0
    private var targetSpanCount: Int = -1
    
    // State
    private var isAnimating = false
    private val zoomItems = mutableListOf<ZoomItem>()
    private var focalItem: ZoomItem? = null
    
    // For calculation
    private val activeTargetRect = Rect() // Where the focal item will end up
    
    init {
        scaleGestureDetector = ScaleGestureDetector(recyclerView.context, ScaleListener())
    }

    data class ZoomItem(
        val view: View,
        val adapterPosition: Int,
        val imageView: ImageView,
        val startRect: Rect, // Screen coordinates
        val startCenter: PointF, // Center relative to screen
        
        // Target state (relative to Focal Item in the virtual target grid)
        var targetRelativeOffset: PointF = PointF(0f, 0f), 
        var targetSize: Size = Size(0, 0)
    )
    
    data class Size(val width: Int, val height: Int)

    fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (isAnimating) return false
        return scaleGestureDetector.onTouchEvent(event)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (isAnimating) return false

            val layoutManager = recyclerView.layoutManager as? StaggeredGridLayoutManager ?: return false
            initialSpanCount = layoutManager.spanCount
            
            // 1. Identify Focal View
            val focalView = findFocalView(detector.focusX, detector.focusY) ?: return false
            val focalPos = recyclerView.getChildAdapterPosition(focalView)
            if (focalPos == RecyclerView.NO_POSITION) return false

            // 2. Prepare Container
            zoomContainer.removeAllViews()
            zoomContainer.visibility = View.VISIBLE
            zoomItems.clear()
            
            val rootLocation = IntArray(2)
            zoomContainer.getLocationOnScreen(rootLocation)
            val containerOffsetX = rootLocation[0]
            val containerOffsetY = rootLocation[1]

            // 3. Snapshot ALL visible views
            for (child in recyclerView.children) {
                val position = recyclerView.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) continue

                val childLocation = IntArray(2)
                child.getLocationOnScreen(childLocation)
                val left = childLocation[0] - containerOffsetX
                val top = childLocation[1] - containerOffsetY
                val rect = Rect(left, top, left + child.width, top + child.height)
                
                // Capture Bitmap
                val bitmap = captureView(child)
                val imageView = ShapeableImageView(recyclerView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(child.width, child.height).apply {
                        leftMargin = left
                        topMargin = top
                    }
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageBitmap(bitmap)
                    elevation = if (child == focalView) 20f else 10f // Focal on top
                    
                    // Rounded corners
                    shapeAppearanceModel = ShapeAppearanceModel.builder()
                        .setAllCornerSizes(12f) // Consistent with adapter
                        .build()
                }
                
                zoomContainer.addView(imageView)
                
                val item = ZoomItem(
                    view = child,
                    adapterPosition = position,
                    imageView = imageView,
                    startRect = rect,
                    startCenter = PointF(rect.centerX().toFloat(), rect.centerY().toFloat())
                )
                zoomItems.add(item)
                
                if (child == focalView) {
                    focalItem = item
                    // Set pivot for focal item to follow finger
                    val pivotX = detector.focusX + recyclerView.left - left // relative to view
                    val pivotY = detector.focusY + recyclerView.top - top
                    imageView.pivotX = pivotX
                    imageView.pivotY = pivotY
                }
            }

            if (focalItem == null) return false // Should not happen

            // Ensure focal item is on top of everything
            focalItem?.imageView?.bringToFront()

            // 4. Calculate Virtual Targets
            calculateTargetLayout()

            // 5. Hide RecyclerView
            recyclerView.visibility = View.INVISIBLE
            
            state.currentScale = 1.0f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focal = focalItem ?: return false
            state.currentScale *= detector.scaleFactor
            
            // Determine direction
            // If zooming in (2->1), target scale is e.g. 2.0.
            // If zooming out (1->2), target scale is e.g. 0.5.
            
            // Determine Target Span
            // We need to know which target layout we are aiming for to calculate progress correctly
            val targetSpan = if (state.currentScale > 1.0f) 
                (initialSpanCount - 1).coerceAtLeast(1) 
            else 
                initialSpanCount + 1
            
            // But we already calculated targets for BOTH directions? 
            // Wait, `calculateTargetLayout` needs to know direction?
            // Or we can pre-calculate both?
            // Let's simplify: We only calculate targets based on the *current* direction trend.
            // But onScaleBegin doesn't know direction yet.
            // So we should update targets if direction changes?
            // Or just assume:
            // If currentScale > 1.0, use ZoomIn Targets.
            // If currentScale < 1.0, use ZoomOut Targets.
            
            // Let's re-calc targets dynamically if needed, or pre-calc both.
            // For simplicity, let's re-calc if span direction implies a change.
            if (targetSpan != targetSpanCount) {
                targetSpanCount = targetSpan
                calculateTargetLayout() // Re-run for new target span
            }
            
            // Calculate Progress (0..1)
            // TargetScale is ratio of TargetWidth / StartWidth
            val targetWidth = recyclerView.width / targetSpanCount.toFloat()
            val startWidth = focal.startRect.width().toFloat()
            val targetScale = targetWidth / startWidth
            
            // Progress = (current - start) / (target - start)
            val progress = if (targetScale != 1f) 
                (state.currentScale - 1f) / (targetScale - 1f)
            else 0f
            
            val clampedProgress = progress.coerceIn(0f, 1f)
            
            // CLAMP SCALE STRICTLY
            // User requested: "When it reaches screen width, it shouldn't react anymore"
            // So we force currentScale to respect the limits derived from targetScale.
            if (targetScale > 1f) {
                // Zooming In (e.g. 2->1)
                // Range: [1.0, targetScale]
                if (state.currentScale > targetScale) state.currentScale = targetScale
                if (state.currentScale < 1.0f) state.currentScale = 1.0f
            } else {
                // Zooming Out (e.g. 1->2)
                // Range: [targetScale, 1.0]
                if (state.currentScale < targetScale) state.currentScale = targetScale
                if (state.currentScale > 1.0f) state.currentScale = 1.0f
            }
            
            // Re-update Focal Item with CLAMPED scale
            focal.imageView.scaleX = state.currentScale
            focal.imageView.scaleY = state.currentScale
            
            // Calculate Drift
            // Target Center for Focal Item
            val targetCX = activeTargetRect.centerX().toFloat()
            // val targetCY = activeTargetRect.centerY().toFloat() // We don't drift Y too much to keep it stable
            
            // Current Visual Center (if no translation was applied)
            // VisualCenter = Left + PivotX + (Width/2 - PivotX)*Scale
            val fPivotX = focal.imageView.pivotX
            val fW = focal.startRect.width()
            val fCurrentVisualCX = focal.imageView.left + fPivotX + (fW/2f - fPivotX) * state.currentScale
            
            // Drift Required to align VisualCenter with TargetCX
            val driftX = (targetCX - fCurrentVisualCX) * clampedProgress
            
            // Apply Drift
            focal.imageView.translationX = driftX
            // focal.imageView.translationY = driftY // Optional
            
            // Update Neighbors
            for (item in zoomItems) {
                if (item == focal) continue
                
                // Interpolate Offset
                // StartOffset: item.startCenter - focal.startCenter
                val startOffsetX = item.startCenter.x - focal.startCenter.x
                val startOffsetY = item.startCenter.y - focal.startCenter.y
                
                // TargetOffset: item.targetRelativeOffset (pre-calculated)
                // Interpolate
                // val currentRelX = startOffsetX + (item.targetRelativeOffset.x - startOffsetX) * clampedProgress
                // val currentRelY = startOffsetY + (item.targetRelativeOffset.y - startOffsetY) * clampedProgress
                
                // Scale the offset by current scale to maintain visual consistency with the expanding "world"
                // The "World" expands by state.currentScale.
                // The relative positions in the world morph from StartLayout to TargetLayout.
                // So: ActualOffset = InterpolatedLayoutOffset * Scale
                
                // Wait, if progress=0, Scale=1. Offset = StartOffset. Correct.
                // If progress=1, Scale=TargetScale. Offset = TargetOffset * TargetScale?
                // TargetOffset was calculated in the "Virtual Grid" which is already sized to TargetWidth.
                // So TargetOffset implies "Unscaled distance in the target layout"?
                // No, calculateTargetLayout uses pixels. TargetWidth is e.g. 1080px.
                // StartWidth is 540px.
                // TargetOffset is distance in 1080px world.
                // StartOffset is distance in 540px world.
                // If we scale StartOffset by 2.0, we get distance in 1080px world.
                // So TargetOffset is roughly StartOffset * TargetScale (if layout didn't change).
                // But layout changed.
                
                // Correct logic:
                // We want to interpolate between "Start State scaled by CurrentScale" and "Target State".
                // Actually, simpler:
                // ViewCenter = FocalCenter + CurrentRelOffset
                // We just need CurrentRelOffset to transition smoothly.
                // If we use: `Lerp(StartOffset * CurrentScale, TargetOffset * (CurrentScale/TargetScale?), progress)`?
                
                // Let's look at the endpoints.
                // t=0: Offset = StartOffset * 1.0.
                // t=1: Offset = TargetOffset. (The user sees the target layout at target scale).
                // So we want the interpolation to land on TargetOffset when Scale reaches TargetScale.
                
                // So: `Lerp(StartOffset * state.currentScale, TargetOffset, clampedProgress)`
                // Note: TargetOffset is the absolute pixel distance in the Target Layout (which corresponds to Scale=TargetScale).
                
                val currentOffsetX = startOffsetX * state.currentScale * (1 - clampedProgress) + item.targetRelativeOffset.x * clampedProgress
                val currentOffsetY = startOffsetY * state.currentScale * (1 - clampedProgress) + item.targetRelativeOffset.y * clampedProgress
                
                item.imageView.translationX = (focal.imageView.translationX + focal.imageView.left) + currentOffsetX - item.imageView.left
                item.imageView.translationY = (focal.imageView.translationY + focal.imageView.top) + currentOffsetY - item.imageView.top
                
                // Size Interpolation
                // Item should scale to match TargetSize eventually.
                // StartSize * Scale -> TargetSize
                // Lerp(StartSize * Scale, TargetSize, progress)
                val currentW = item.startRect.width() * state.currentScale * (1 - clampedProgress) + item.targetSize.width * clampedProgress
                val currentH = item.startRect.height() * state.currentScale * (1 - clampedProgress) + item.targetSize.height * clampedProgress
                
                // Apply Scale to ImageView (relative to its own size)
                item.imageView.scaleX = currentW / item.startRect.width()
                item.imageView.scaleY = currentH / item.startRect.height()
                
                // Reset pivots for neighbors (we control translation manually)
                item.imageView.pivotX = 0f
                item.imageView.pivotY = 0f
                
                // Adjust translation for pivot=0
                // Default pivot is center? No, we set to 0.
                // If pivot is 0, scaling extends right/down.
                // We want Center to be at calculated position.
                // CurrentCenter = (Left + TransX) + CurrentW/2
                // TargetCenter = FocalCenter + Offset
                // (Left + TransX) + CurrentW/2 = (FocalLeft + FocalTransX + FocalW/2) + Offset
                
                // val focalCenterX = focal.imageView.left + focal.imageView.translationX + (focal.startRect.width() * focal.imageView.scaleX) / 2 // Approximate pivot effect?
                // Actually focal uses Pivot. 
                // FocalCenter = FocalLeft + PivotX + (Center - PivotX)*Scale + TransX
                // Too complex.
                // Use `focal.imageView.x` + width/2 ?
                // `x` property includes translation.
                // val focalVisualCenterX = focal.imageView.x + (focal.startRect.width() * focal.imageView.scaleX) / 2 // Assuming pivot center? 
                // No, focal pivot is arbitrary.
                
                // Use View.getMatrix() to find center? No, too heavy.
                // Let's trust that we can calculate Focal Center from its properties.
                // VisualCenter = Left + PivotX + (Width/2 - PivotX)*Scale + TransX
                val fCx = focal.imageView.left + focal.imageView.pivotX + (focal.startRect.width()/2f - focal.imageView.pivotX) * focal.imageView.scaleX + focal.imageView.translationX
                val fCy = focal.imageView.top + focal.imageView.pivotY + (focal.startRect.height()/2f - focal.imageView.pivotY) * focal.imageView.scaleY + focal.imageView.translationY
                
                val targetCX = fCx + currentOffsetX
                val targetCY = fCy + currentOffsetY
                
                // Neighbor TopLeft = TargetCenter - CurrentSize/2
                val neighborLeft = targetCX - currentW / 2
                val neighborTop = targetCY - currentH / 2
                
                // Neighbor Trans = NeighborLeft - ItemLeft
                item.imageView.translationX = neighborLeft - item.imageView.left
                item.imageView.translationY = neighborTop - item.imageView.top
            }
            
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            val focal = focalItem ?: return
            
            // Determine success based on progress
            val targetWidth = recyclerView.width / targetSpanCount.toFloat()
            val startWidth = focal.startRect.width().toFloat()
            val targetScale = targetWidth / startWidth
            
            // Recalculate progress
            val progress = if (targetScale != 1f) 
                (state.currentScale - 1f) / (targetScale - 1f)
            else 0f
            
            if (progress > 0.4f && targetSpanCount != -1) {
                animateToTarget()
            } else {
                animateToReset()
            }
        }
    }
    
    private fun calculateTargetLayout() {
        val focal = focalItem ?: return
        if (targetSpanCount <= 0) return
        
        val targetWidth = recyclerView.width / targetSpanCount
        
        // We need to simulate the layout for ALL zoomItems.
        // Sort items by adapter position to place them in order.
        val sortedItems = zoomItems.sortedBy { it.adapterPosition }
        
        // Track column heights (y-offsets)
        val columnBottoms = IntArray(targetSpanCount) { 0 }
        
        // We need to determine "Start Row" to align roughly.
        // But SGLM is complex. 
        // Strategy: Just pack them from (0,0) in the virtual grid.
        // Then shift the whole grid so that Focal Item aligns with Focal Item's target position.
        
        sortedItems.forEachIndexed { index, item ->
            // Use index % targetSpanCount (Round Robin on VISIBLE items).
            // This ensures we fill columns evenly even if adapter positions have gaps 
            // (e.g. if only even items are visible, pos%2 would stack them all in col 0).
            val colIndex = index % targetSpanCount
            
            // Fallback: If Modulo creates huge gaps, maybe we shouldn't?
            // For now, let's assume Modulo is what the user wants ("Directly to 3rd column").
            
            // Calculate height maintaining aspect ratio
            val aspectRatio = item.startRect.height().toFloat() / item.startRect.width().toFloat()
            val targetH = (targetWidth * aspectRatio).roundToInt()
            
            // Find Top based on chosen column
            val top = columnBottoms[colIndex]
            val left = colIndex * targetWidth
            
            item.targetSize = Size(targetWidth, targetH)
            
            // Store temp coordinates (we will shift them later)
            // We use targetRelativeOffset to store absolute virtual coords temporarily
            item.targetRelativeOffset.set(left.toFloat(), top.toFloat())
            
            columnBottoms[colIndex] += targetH
        }
        
        // Now find Focal Item in virtual grid
        val focalVirtualX = focal.targetRelativeOffset.x
        val focalVirtualY = focal.targetRelativeOffset.y
        val focalVirtualCX = focalVirtualX + focal.targetSize.width / 2f
        val focalVirtualCY = focalVirtualY + focal.targetSize.height / 2f
        
        // Capture absolute virtual Left for Focal Item (Screen X in Target Layout)
        val focalTargetScreenLeft = focalVirtualX.toInt()
        
        // Convert all to relative offsets from Focal Center
        for (item in zoomItems) {
            val itemCX = item.targetRelativeOffset.x + item.targetSize.width / 2f
            val itemCY = item.targetRelativeOffset.y + item.targetSize.height / 2f
            
            item.targetRelativeOffset = PointF(itemCX - focalVirtualCX, itemCY - focalVirtualCY)
        }
        
        // Calculate where Focal Item should end up on screen (ActiveTargetRect)
        // Center vertically or maintain relative Y?
        // User wants "Zoom to next layout location".
        // Usually centered is good.
        val finalW = focal.targetSize.width
        val finalH = focal.targetSize.height
        
        // Use the Absolute Virtual Left we captured. 
        // This ensures that if Focal is in Col 1 (Right), it drifts to Right, 
        // and Col 0 (Left) stays on screen (at 0).
        val finalLeft = focalTargetScreenLeft
        
        // For Y, we center it on the start rect center to avoid jumping
        val finalTop = focal.startRect.centerY() - finalH / 2
        
        activeTargetRect.set(finalLeft, finalTop, finalLeft + finalW, finalTop + finalH)
    }
    
    private fun animateToTarget() {
        val focal = focalItem ?: return
        isAnimating = true
        
        // Update ActiveTargetRect to match Current Visual Vertical Center
        // This ensures the item stays vertically where the user left it, 
        // instead of snapping to the original center.
        val fView = focal.imageView
        val currentScale = fView.scaleY
        val currentTransY = fView.translationY
        val pivotY = fView.pivotY
        val top = fView.top
        val h = focal.startRect.height()
        val visualCY = top + currentTransY + pivotY + (h/2f - pivotY) * currentScale
        
        val finalH = focal.targetSize.height
        val newTop = visualCY - finalH / 2f
        val newBottom = newTop + finalH
        
        activeTargetRect.top = newTop.roundToInt()
        activeTargetRect.bottom = newBottom.roundToInt()
        // Note: We don't change Left/Right because we want to enforce Column alignment.
        
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 300
        
        // Capture start states for all items
        val startStates = zoomItems.map { item ->
            ItemAnimState(
                item,
                item.imageView.translationX,
                item.imageView.translationY,
                item.imageView.scaleX,
                item.imageView.scaleY
            )
        }
        
        // Calculate End States
        // Focal Item: Ends at activeTargetRect
        // But we must achieve this via Scale + Translation (Pivot is fixed at user touch)
        // FocalEndScale = TargetWidth / StartWidth
        val finalScale = focal.targetSize.width.toFloat() / focal.startRect.width()
        
        // VisualCenter = StartLeft + PivotX + (W/2 - PivotX)*Scale + TransX
        // We want VisualCenter to be activeTargetRect.centerX()
        // TransX = TargetCX - (StartLeft + PivotX + (W/2 - PivotX)*Scale)
        
        val fPivotX = focal.imageView.pivotX
        val fPivotY = focal.imageView.pivotY
        val fStartLeft = focal.imageView.left
        val fStartTop = focal.imageView.top
        
        val fTargetCX = activeTargetRect.centerX().toFloat()
        val fTargetCY = activeTargetRect.centerY().toFloat()
        
        val fEndTransX = fTargetCX - (fStartLeft + fPivotX + (focal.startRect.width()/2f - fPivotX) * finalScale)
        val fEndTransY = fTargetCY - (fStartTop + fPivotY + (focal.startRect.height()/2f - fPivotY) * finalScale)
        
        // Neighbors: Relative to Focal
        // NeighborCenter = FocalCenter + RelativeOffset
        
        animator.addUpdateListener { anim ->
            val fraction = anim.animatedFraction
            
            // Animate Focal
            val currentFocalScale = startStates.find { it.item == focal }!!.scaleX + (finalScale - startStates.find { it.item == focal }!!.scaleX) * fraction
            focal.imageView.scaleX = currentFocalScale
            focal.imageView.scaleY = currentFocalScale
            
            val currentFocalTransX = startStates.find { it.item == focal }!!.transX + (fEndTransX - startStates.find { it.item == focal }!!.transX) * fraction
            val currentFocalTransY = startStates.find { it.item == focal }!!.transY + (fEndTransY - startStates.find { it.item == focal }!!.transY) * fraction
            focal.imageView.translationX = currentFocalTransX
            focal.imageView.translationY = currentFocalTransY
            
            // Calculate Current Focal Center
            val fCx = fStartLeft + fPivotX + (focal.startRect.width()/2f - fPivotX) * currentFocalScale + currentFocalTransX
            val fCy = fStartTop + fPivotY + (focal.startRect.height()/2f - fPivotY) * currentFocalScale + currentFocalTransY
            
            // Animate Neighbors
            for (state in startStates) {
                if (state.item == focal) continue
                
                // Interpolate Size
                val startW = state.item.startRect.width() * state.scaleX
                val targetW = state.item.targetSize.width.toFloat()
                val currentW = startW + (targetW - startW) * fraction
                
                val startH = state.item.startRect.height() * state.scaleY
                val targetH = state.item.targetSize.height.toFloat()
                val currentH = startH + (targetH - startH) * fraction
                
                state.item.imageView.scaleX = currentW / state.item.startRect.width()
                state.item.imageView.scaleY = currentH / state.item.startRect.height()
                
                // Interpolate Position (Relative to Focal)
                // We know where they ARE (startStates) and where they should BE (Focal + Offset).
                // Actually, we should just interpolate the OFFSET?
                // No, we already computed complex positions in onScale.
                // Simpler: Interpolate from StartState (current visual) to EndState (Focal + TargetOffset).
                
                val targetCX = fCx + state.item.targetRelativeOffset.x
                val targetCY = fCy + state.item.targetRelativeOffset.y
                
                val targetLeft = targetCX - currentW / 2
                val targetTop = targetCY - currentH / 2
                
                val endTransX = targetLeft - state.item.imageView.left
                val endTransY = targetTop - state.item.imageView.top
                
                state.item.imageView.translationX = state.transX + (endTransX - state.transX) * fraction
                state.item.imageView.translationY = state.transY + (endTransY - state.transY) * fraction
            }
        }
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finishTransition(true)
            }
        })
        animator.start()
    }
    
    private fun animateToReset() {
        // Animate everything back to scale 1, trans 0
        isAnimating = true
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 250
        
        val startStates = zoomItems.map { item ->
            ItemAnimState(
                item,
                item.imageView.translationX,
                item.imageView.translationY,
                item.imageView.scaleX,
                item.imageView.scaleY
            )
        }
        
        animator.addUpdateListener { anim ->
            val fraction = anim.animatedFraction
            for (state in startStates) {
                state.item.imageView.scaleX = state.scaleX + (1f - state.scaleX) * fraction
                state.item.imageView.scaleY = state.scaleY + (1f - state.scaleY) * fraction
                state.item.imageView.translationX = state.transX + (0f - state.transX) * fraction
                state.item.imageView.translationY = state.transY + (0f - state.transY) * fraction
            }
        }
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finishTransition(false)
            }
        })
        animator.start()
    }
    
    private data class ItemAnimState(
        val item: ZoomItem,
        val transX: Float,
        val transY: Float,
        val scaleX: Float,
        val scaleY: Float
    )

    private fun finishTransition(isSuccess: Boolean) {
        if (isSuccess && targetSpanCount != -1 && focalItem != null) {
            onSpanCountChanged(targetSpanCount)
            
            // Replace LayoutManager to ensure clean state and reliable scrolling
            // (Modifying existing SGLM spanCount sometimes causes scroll position issues)
            val newLm = StaggeredGridLayoutManager(targetSpanCount, StaggeredGridLayoutManager.VERTICAL)
            newLm.gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE 
            recyclerView.layoutManager = newLm
            
            // Calculate offset based on VISUAL TOP to prevent jumping
            // The item should stay exactly where it is visually on the screen.
            val fView = focalItem!!.imageView
            // VisualTop = LayoutTop + PivotY - (PivotY * ScaleY) + TranslationY
            // (Note: TranslationY is 0 in our current implementation, but good to include)
            val visualTop = fView.y + fView.pivotY * (1f - fView.scaleY)
            
            val rvLoc = IntArray(2)
            recyclerView.getLocationOnScreen(rvLoc)
            val contLoc = IntArray(2)
            zoomContainer.getLocationOnScreen(contLoc)
            
            // Map VisualTop (in ZoomContainer) to RecyclerView coords
            val screenVisualTop = contLoc[1] + visualTop
            val offset = (screenVisualTop - rvLoc[1]).toInt()
            
            val targetPosition = focalItem!!.adapterPosition
            newLm.scrollToPositionWithOffset(targetPosition, offset)
            
            // Mask the transition: Keep ZoomContainer visible until RV is ready
            recyclerView.doOnPreDraw {
                // Now RV has laid out. Fade out ZoomContainer.
                // IMPORTANT: Make RV visible now so it shows through the fading ZoomContainer
                recyclerView.visibility = View.VISIBLE
                
                zoomContainer.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction {
                        zoomContainer.removeAllViews()
                        zoomContainer.visibility = View.GONE
                        zoomContainer.alpha = 1f
                        zoomItems.clear()
                        focalItem = null
                        isAnimating = false
                    }
                    .start()
            }
        } else {
            // Failed/Reset: Just clear immediately
            zoomContainer.removeAllViews()
            zoomContainer.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            zoomItems.clear()
            focalItem = null
            isAnimating = false
        }
    }

    private fun findFocalView(x: Float, y: Float): View? {
        var focalView = recyclerView.findChildViewUnder(x, y)
        if (focalView == null && recyclerView.childCount > 0) {
            var minDistance = Float.MAX_VALUE
            var closestView: View? = null
            for (child in recyclerView.children) {
                val centerX = child.left + child.width / 2f
                val centerY = child.top + child.height / 2f
                val dx = x - centerX
                val dy = y - centerY
                val distance = dx * dx + dy * dy
                if (distance < minDistance) {
                    minDistance = distance
                    closestView = child
                }
            }
            focalView = closestView
        }
        return focalView
    }

    private fun captureView(view: View): Bitmap {
        if (view.width <= 0 || view.height <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}
