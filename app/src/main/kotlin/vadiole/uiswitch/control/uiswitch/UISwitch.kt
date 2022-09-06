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
    private var thumbMin = 0f
    private var thumbMax = 0f
    private var thumbRadius = 0f
    private var touchMode = TouchModeIdle
    private var touchX = 0f
    private var touchY = 0f
    private val config = ViewConfiguration.get(context)
    private val touchSlop = config.scaledTouchSlop
    private val minFlingVelocity = config.scaledMinimumFlingVelocity
    private val argbEvaluator = ArgbEvaluator()
    private var trackTintFraction = 0f
    private val thumbShadowColor = context.getColor(R.color.shadow)
    private val thumbColor = context.getColor(R.color.white)
    private val trackCheckedTint = context.getColor(R.color.track_checked)
    private val trackDefaultTint = context.getColor(R.color.track_default)
    private val trackDrawable = context.getDrawable(R.drawable.switch_track)!!.apply {
        setTint(trackDefaultTint)
    }
    private val thumb = ThumbDrawable(thumbColor = thumbColor, shadowColor = thumbShadowColor)
    private val minAnimVisibleChange = 1f / (resources.displayMetrics.density * (Width))
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
        minimumVisibleChange = minAnimVisibleChange
    }
    private val thumbLeftSpringAnimation = SpringAnimation(thumbLeftPositionValueHolder).apply {
        minimumVisibleChange = minAnimVisibleChange
    }
    private val trackTintAnimation = SpringAnimation(trackTintFractionValueHolder).apply {
        minimumVisibleChange = minAnimVisibleChange
    }
    private val holdFromRightEffect = Runnable {
        springToPosition(thumbLeftSpringAnimation, 1 - HoldEffectShift)
    }
    private val holdFromLeftEffect = Runnable {
        springToPosition(thumbRightSpringAnimation, HoldEffectShift)
    }
    private var checkedInternal: Boolean = false
        set(value) {
            field = value
            val targetPosition = if (value) 1f else 0f

            if (isAttachedToWindow && isLaidOut) {
                springToPosition(thumbRightSpringAnimation, targetPosition, bounce = true)
                springToPosition(thumbLeftSpringAnimation, targetPosition, bounce = true)
                springToPosition(trackTintAnimation, targetPosition)
            } else {
                snapToPosition(targetPosition)
            }
        }

    // 0f..1f
    private var thumbPosition = ThumpPosition(right = 0f, left = 0f)
        set(value) {
            field = value
            invalidateThumbBounds()
        }

    override fun isChecked(): Boolean = checkedInternal

    override fun toggle() {
        isChecked = !checkedInternal
    }

    override fun setChecked(checked: Boolean) {
        checkedInternal = checked
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (isEnabled) 1f else 0.5f
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
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (touchMode) {
                    TouchModeIdle -> Unit
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
                        return true
                    }
                    TouchModeDragging -> {
                        cancelAllScheduledAnimations()
                        toggle()
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun springToPosition(animation: SpringAnimation, position: Float, bounce: Boolean = false) {
        animation.apply {
            spring = SpringForce(position).apply {
                stiffness = SpringStiffness
                dampingRatio = if (bounce) SpringBounceRatio else SpringNoBounce
            }
            start()
        }
    }

    private fun snapToPosition(position: Float) {
        thumbLeftSpringAnimation.cancel()
        thumbRightSpringAnimation.cancel()
        cancelAllScheduledAnimations()
        thumbLeftPositionValueHolder.value = position
        thumbRightPositionValueHolder.value = position
        trackTintFractionValueHolder.value = position
    }

    private fun cancelAllScheduledAnimations() {
        handler?.run {
            removeCallbacks(holdFromLeftEffect)
            removeCallbacks(holdFromRightEffect)
        }
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
        invalidateThumbBounds()
    }

    private fun invalidateThumbBounds() {
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

    class ThumbDrawable(thumbColor: Int, private val shadowColor: Int) : Drawable() {
        private val paint = Paint().apply {
            color = thumbColor
            isAntiAlias = true
        }
        private val circleBounds = RectF()
        private var radius = 0f

        override fun onBoundsChange(bounds: Rect) {
            radius = min(bounds.height(), bounds.width()) / 2f
            circleBounds.set(bounds)
            val shadowRadius = bounds.height() * SHADOW_RADIUS_PERCENT
            val shadowY = bounds.height() * SHADOW_Y_PERCENT
            paint.setShadowLayer(shadowRadius, 0f, shadowY, shadowColor)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawRoundRect(circleBounds, radius, radius, paint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.OPAQUE

        companion object {
            private const val SHADOW_RADIUS_PERCENT = 0.09677f
            private const val SHADOW_Y_PERCENT = 0.09677f
        }
    }

    class ThumpPosition(val right: Float, val left: Float)

    companion object {
        private const val Mult = 5
        private const val Width = 51 * Mult
        private const val Height = 31 * Mult
        private const val ThumbOffsetPercent = 0.0645f

        private const val TouchModeIdle = 0
        private const val TouchModeDown = 1
        private const val TouchModeDragging = 2

        private const val HoldEffectAnimationDelay = 60L
        private const val SpringStiffness = 400f
        private const val SpringNoBounce = 1f
        private const val SpringBounceRatio = 0.8f
        private const val HoldEffectShift = 0.33f
    }
}