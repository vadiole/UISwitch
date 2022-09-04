package vadiole.uiswitch.control.uiswitch

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Checkable
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import vadiole.uiswitch.R
import vadiole.uiswitch.ResourcesOwner

class UISwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle), ResourcesOwner, Checkable {
    private var trackTintFraction = 0f
    private val argbEvaluator = ArgbEvaluator()
    private val trackCheckedTint = context.getColor(R.color.track_checked)
    private val trackDefaultTint = context.getColor(R.color.track_default)
    private val trackDrawable = context.getDrawable(R.drawable.switch_track)!!.apply {
        setTint(trackDefaultTint)
    }
    private val thumb = ThumbDrawable()
    private val thumbRightPositionValueHolder = object : FloatValueHolder() {
        override fun getValue(): Float = thumbPosition.right
        override fun setValue(value: Float) {
            thumbPosition = ThumpPosition(right = value, left = thumbPosition.left)
        }
    }
    private val thumbLeftPositionValueHolder = object : FloatValueHolder() {
        override fun getValue(): Float = thumbPosition.left
        override fun setValue(value: Float) {
            thumbPosition = ThumpPosition(right = thumbPosition.right, left = value)
        }
    }
    private val trackTintFractionValueHolder = object : FloatValueHolder() {
        override fun getValue(): Float = trackTintFraction
        override fun setValue(value: Float) {
            trackTintFraction = value
            val currentColor = argbEvaluator.evaluate(value, trackDefaultTint, trackCheckedTint) as Int
            trackDrawable.setTint(currentColor)
        }
    }

    private val thumbRightSpringAnimation = SpringAnimation(thumbRightPositionValueHolder).apply {
        minimumVisibleChange = 0.01f
    }
    private val thumbLeftSpringAnimation = SpringAnimation(thumbLeftPositionValueHolder).apply {
        minimumVisibleChange = 0.01f
    }
    private val trackTintAnimation = SpringAnimation(trackTintFractionValueHolder).apply {
        minimumVisibleChange = 0.01f
    }

    private val holdFromRightEffect = Runnable {
        springThumbLeftToPosition(0.7f)
    }

    private val holdFromLeftEffect = Runnable {
        springThumbRightToPosition(0.3f)
    }

    private var checkedInternal: Boolean = false
        set(value) {
            field = value
            val targetPosition = if (value) 1f else 0f
            springThumbRightToPosition(targetPosition)
            springThumbLeftToPosition(targetPosition)
            springToPosition(trackTintAnimation, targetPosition)
        }

    // 0f..1f
    private var thumbPosition = ThumpPosition(right = 0f, left = 0f)
        set(value) {
            field = value
            invalidateThumbPosition()
        }

    private var thumbMin = 0f
    private var thumbMax = 0f
    private var thumbRadius = 0f
    private var touchMode = TouchModeIdle
    private var touchX = 0f
    private var touchY = 0f

    private val config = ViewConfiguration.get(context)
    private val touchSlop = config.scaledTouchSlop
    private val minFlingVelocity = config.scaledMinimumFlingVelocity

    override fun isChecked(): Boolean = checkedInternal

    override fun toggle() {
        isChecked = !checkedInternal
    }

    override fun setChecked(checked: Boolean) {
        checkedInternal = checked
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isEnabled) {
                    touchMode = TouchModeDown
                    touchX = event.x
                    touchY = event.y
                    if (isChecked) {
                        handler.postDelayed(holdFromRightEffect, HoldEffectAnimationDelay)
                    } else {
                        handler.postDelayed(holdFromLeftEffect, HoldEffectAnimationDelay)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (touchMode) {
                    TouchModeIdle -> {}
                    TouchModeDown -> {
                        val x: Float = event.x
                        val y: Float = event.y
                        if (abs(x - touchX) > touchSlop || abs(y - touchY) > touchSlop) {
                            touchMode = TouchModeDragging
                            parent.requestDisallowInterceptTouchEvent(true)
                            touchX = x
                            touchY = y
                            return true
                        }
                    }
                    TouchModeDragging -> {
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (touchMode) {
                    TouchModeIdle -> {}
                    TouchModeDown -> {
                        cancelAllScheduledAnimations()
                        toggle()
                    }
                    TouchModeDragging -> {
                        cancelAllScheduledAnimations()
                        toggle()
                    }
                }
            }
        }
        return true
    }

    private fun springThumbRightToPosition(finalPosition: Float) {
        springToPosition(thumbRightSpringAnimation, finalPosition)
    }

    private fun springThumbLeftToPosition(finalPosition: Float) {
        springToPosition(thumbLeftSpringAnimation, finalPosition)
    }

    private fun springToPosition(animation: SpringAnimation, position: Float) {
        animation.apply {
            spring = SpringForce(position).apply {
                stiffness = SpringStiffness
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            start()
        }
    }

    private fun cancelAllScheduledAnimations() {
        handler.removeCallbacks(holdFromLeftEffect)
        handler.removeCallbacks(holdFromRightEffect)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpec = MeasureSpec.makeMeasureSpec(Width.dp, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(Height.dp, MeasureSpec.EXACTLY)
        super.onMeasure(widthSpec, heightSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        trackDrawable.setBounds(0, 0, w, h)
        val thumbOffset = (h.toFloat() * ThumbOffsetPercent)
        thumbRadius = h / 2f - thumbOffset
        thumbMin = thumbOffset + thumbRadius
        thumbMax = w - thumbOffset - thumbRadius
        invalidateThumbPosition()
    }

    private fun invalidateThumbPosition() {
        val thumbCenterLeftX = thumbMin + (thumbMax - thumbMin) * thumbPosition.left
        val thumbCenterRightX = thumbMin + (thumbMax - thumbMin) * thumbPosition.right
        val thumbCenterY = measuredHeight / 2f
        val thumbLeft = (thumbCenterLeftX - thumbRadius).roundToInt()
        val thumbRight = (thumbCenterRightX + thumbRadius).roundToInt()
        val thumbTop = (thumbCenterY - thumbRadius).roundToInt()
        val thumbBottom = (thumbCenterY + thumbRadius).roundToInt()
        thumb.setBounds(thumbLeft, thumbTop, thumbRight, thumbBottom)
        invalidate()
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        trackDrawable.draw(canvas)
        thumb.draw(canvas)
//        drawDebug(canvas)
    }

    private val debugPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.GREEN
        strokeWidth = 1f
        isAntiAlias = true
    }

    private fun drawDebug(canvas: Canvas) {
        canvas.drawLine(thumbMin, 0f, thumbMin, measuredHeight.toFloat(), debugPaint)
        canvas.drawLine(thumbMax, 0f, thumbMax, measuredHeight.toFloat(), debugPaint)
    }

    class ThumbDrawable : Drawable() {
        private val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }

        private val circleBounds = RectF()
        private var radius = 0f

        override fun onBoundsChange(bounds: Rect) {
            radius = min(bounds.height(), bounds.width()) / 2f
            circleBounds.set(bounds)
            val shadowRadius = bounds.height() * SHADOW_RADIUS_PERCENT
            val shadowY = bounds.height() * SHADOW_Y_PERCENT
            paint.setShadowLayer(
                shadowRadius,
                0f,
                shadowY,
                0x33000000,
            )
        }

        override fun draw(canvas: Canvas) {
            canvas.drawRoundRect(circleBounds, radius, radius, paint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        override fun getOpacity(): Int = PixelFormat.OPAQUE

        companion object {
            private const val SHADOW_RADIUS_PERCENT = 0.25926f
            private const val SHADOW_Y_PERCENT = 0.11111f
        }
    }

    class ThumpPosition(val right: Float, val left: Float)

    companion object {

        private const val Mult = 5
        private const val Width = 51 * Mult
        private const val Height = 31 * Mult
        private const val ThumbOffsetPercent = 0.074074f

        private const val TouchModeIdle = 0
        private const val TouchModeDown = 1
        private const val TouchModeDragging = 2

        private const val HoldEffectAnimationDelay = 300L
        private const val SpringStiffness = 300f
    }
}