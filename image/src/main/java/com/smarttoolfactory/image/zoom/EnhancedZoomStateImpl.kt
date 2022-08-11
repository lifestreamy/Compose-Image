package com.smarttoolfactory.image.zoom

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import com.smarttoolfactory.image.util.coerceIn
import com.smarttoolfactory.image.util.getCropRect
import kotlinx.coroutines.coroutineScope

/**
 *  * State of the enhanced zoom that uses animations and fling
 *  to animate to bounds or have movement after pointers are up.
 *  Allows to change zoom, pan,  translate, or get current state by
 * calling methods on this object. To be hosted and passed to [Modifier.enhancedZoom].
 * Also contains [EnhancedZoomData] about current transformation area of Composable and
 * visible are of image being zoomed, rotated, or panned. If any animation
 * is going on current [isAnimationRunning] is true and [EnhancedZoomData] returns rectangle
 * that belongs to end of animation.
 *
 * @param imageSize size of the image that is zoomed or transformed. Size of the image
 * is required to get [Rect] of visible area after current transformation.
 * @param initialZoom zoom set initially
 * @param minZoom minimum zoom value
 * @param maxZoom maximum zoom value
 * @param flingGestureEnabled when set to true dragging pointer builds up velocity. When last
 * pointer leaves Composable a movement invoked against friction till velocity drops down
 * to threshold
 * @param moveToBoundsEnabled when set to true if image zoom is lower than initial zoom or
 * panned out of image boundaries moves back to bounds with animation.
 * ##Note
 * Currently rotating back to borders is not available
 * @param zoomEnabled when set to true zoom is enabled
 * @param panEnabled when set to true pan is enabled
 * @param rotationEnabled when set to true rotation is enabled
 * @param limitPan limits pan to bounds of parent Composable. Using this flag prevents creating
 * empty space on sides or edges of parent
 */
open class EnhancedZoomState constructor(
    val imageSize: IntSize,
    initialZoom: Float = 1f,
    minZoom: Float = .5f,
    maxZoom: Float = 5f,
    flingGestureEnabled: Boolean = false,
    moveToBoundsEnabled: Boolean = true,
    zoomEnabled: Boolean = true,
    panEnabled: Boolean = true,
    rotationEnabled: Boolean = false,
    limitPan: Boolean = false
) : BaseEnhancedZoomState(
    initialZoom = initialZoom,
    minZoom = minZoom,
    maxZoom = maxZoom,
    flingGestureEnabled = flingGestureEnabled,
    moveToBoundsEnabled = moveToBoundsEnabled,
    zoomEnabled = zoomEnabled,
    panEnabled = panEnabled,
    rotationEnabled = rotationEnabled,
    limitPan = limitPan
) {

    private val rectDraw: Rect
        get() = Rect(
            offset = Offset.Zero,
            size = Size(size.width.toFloat(), size.height.toFloat())
        )

    val enhancedZoomData: EnhancedZoomData
        get() = EnhancedZoomData(
            zoom = animatableZoom.targetValue,
            pan = animatablePan.targetValue,
            rotation = animatableRotation.targetValue,
            imageRegion = rectDraw,
            visibleRegion = calculateRectBounds()
        )

    private fun calculateRectBounds(): Rect {

        val width = size.width
        val height = size.height
        val zoom = animatableZoom.targetValue
        val pan = animatablePan.targetValue

        // Offset for interpolating offset from (imageWidth/2,-imageWidth/2) interval
        // to (0, imageWidth) interval when
        // transform origin is TransformOrigin(0.5f,0.5f)
        val horizontalCenterOffset = width * (zoom - 1) / 2f
        val verticalCenterOffset = height * (zoom - 1) / 2f

        val bounds = getBounds()

        val offsetX = (horizontalCenterOffset - pan.x.coerceIn(-bounds.x, bounds.x))
            .coerceAtLeast(0f) / zoom
        val offsetY = (verticalCenterOffset - pan.y.coerceIn(-bounds.y, bounds.y))
            .coerceAtLeast(0f) / zoom

        val offset = Offset(offsetX, offsetY)

        return getCropRect(
            bitmapWidth = imageSize.width,
            bitmapHeight = imageSize.height,
            containerWidth = width.toFloat(),
            containerHeight = height.toFloat(),
            pan = offset,
            zoom = zoom,
            rectSelection = rectDraw
        )
    }
}

open class BaseEnhancedZoomState constructor(
    initialZoom: Float = 1f,
    minZoom: Float = .5f,
    maxZoom: Float = 5f,
    val flingGestureEnabled: Boolean = true,
    val moveToBoundsEnabled: Boolean = true,
    zoomEnabled: Boolean = true,
    panEnabled: Boolean = true,
    rotationEnabled: Boolean = false,
    limitPan: Boolean = false
) : ZoomState(
    initialZoom = initialZoom,
    initialRotation = 0f,
    minZoom = minZoom,
    maxZoom = maxZoom,
    zoomEnabled = zoomEnabled,
    panEnabled = panEnabled,
    rotationEnabled = rotationEnabled,
    limitPan = limitPan
) {
    private val velocityTracker = VelocityTracker()

    open suspend fun onGesture(
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        rotation: Float,
        mainPointer: PointerInputChange,
        changes: List<PointerInputChange>
    ) = coroutineScope {

        updateZoomState(
            centroid = centroid,
            zoomChange = zoom,
            panChange = pan,
            rotationChange = rotation
        )

        // Fling Gesture
        if (flingGestureEnabled) {
            if (changes.size == 1) {
                addPosition(mainPointer.uptimeMillis, mainPointer.position)
            }
        }
    }

    suspend fun onGestureEnd(onFinish: () -> Unit) {
        if (flingGestureEnabled && zoom > 1) {
            fling()
        }
        if (moveToBoundsEnabled) {
            resetToValidBounds()
        }
        onFinish()
    }

    // Double Tap
    suspend fun onDoubleTap(onAnimationEnd: () -> Unit) {
        if (flingGestureEnabled) {
            resetTracking()
        }
        resetWithAnimation()
        onAnimationEnd()
    }

    // TODO Add resetting back to bounds for rotated state as well
    /**
     * Resets to bounds with animation and resets tracking for fling animation
     */
    private suspend fun resetToValidBounds() {
        val zoom = zoom.coerceAtLeast(1f)
        val bounds = getBounds()
        val pan = pan.coerceIn(-bounds.x..bounds.x, -bounds.y..bounds.y)
        resetWithAnimation(pan = pan, zoom = zoom)
        resetTracking()
    }

    /*
        Fling gesture
     */
    private fun addPosition(timeMillis: Long, position: Offset) {
        velocityTracker.addPosition(
            timeMillis = timeMillis,
            position = position
        )
    }

    /**
     * Create a fling gesture when user removes finger from scree to have continuous movement
     * until [velocityTracker] speed reached to lower bound
     */
    private suspend fun fling() {
        val velocityTracker = velocityTracker.calculateVelocity()
        val velocity = Offset(velocityTracker.x, velocityTracker.y)

        animatablePan.animateDecay(
            velocity,
            exponentialDecay(
                absVelocityThreshold = 20f
            )
        )
    }

    private fun resetTracking() {
        velocityTracker.resetTracking()
    }
}
