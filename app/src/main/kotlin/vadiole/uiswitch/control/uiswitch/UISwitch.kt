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
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewConfiguration
import android.widget.Checkable
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import vadiole.uiswitch.R
import vadiole.uiswitch.ResourcesOwner

class UISwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    private val scale: Float = 1f,
) : View(context, attrs, defStyle), ResourcesOwner, Checkable {
    private var thumbMin = 0f
    private var thumbMax = 0f
    private var thumbRadius = 0f
    private var touchMode = TouchModeIdle
    private var touchX = 0f
    private var touchY = 0f
    private var wasToggledByDragging = false
    private val config = ViewConfiguration.get(context)
    private val touchSlop = config.scaledTouchSlop
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
    private val thumbRightAnimation = SpringAnimation(thumbRightPositionValueHolder)
    private val thumbLeftAnimation = SpringAnimation(thumbLeftPositionValueHolder)
    private val trackTintAnimation = SpringAnimation(trackTintFractionValueHolder)
    private val holdFromRightEffect = Runnable {
        stateInternal = State.Checked.Pressed
        springToPosition(thumbLeftAnimation, 1 - HoldEffectShift)
    }
    private val holdFromLeftEffect = Runnable {
        stateInternal = State.Unchecked.Pressed
        springToPosition(thumbRightAnimation, HoldEffectShift)
    }
    private var thumbPosition = ThumpPosition(right = 0f, left = 0f)
        set(value) {
            field = value
            invalidateThumbBounds()
        }
    private var stateInternal: State = State.Unchecked.Default
    sealed class State {
        sealed class Checked : State() {
            object Default : Checked()
            object Pressed : Checked()
        }

        sealed class Unchecked : State() {
            object Default : Unchecked()
            object Pressed : Unchecked()
        }
    }

    override fun isChecked(): Boolean = stateInternal is State.Checked

    override fun toggle() {
        isChecked = !isChecked
    }

    override fun setChecked(checked: Boolean) {
        cancelAllScheduledAnimations()
        stateInternal = if (checked) State.Checked.Default else State.Unchecked.Default
        val targetPosition = if (checked) 1f else 0f

        if (isAttachedToWindow && isLaidOut) {
            springToPosition(thumbRightAnimation, targetPosition, bounce = true)
            springToPosition(thumbLeftAnimation, targetPosition, bounce = true)
            springToPosition(trackTintAnimation, targetPosition)
        } else {
            snapToPosition(targetPosition)
        }
    }

    private fun setCheckedByDrag(checked: Boolean) {
        cancelAllScheduledAnimations()
        playSoundEffect(SoundEffectConstants.CLICK)
        stateInternal = if (checked) State.Checked.Pressed else State.Unchecked.Pressed
        wasToggledByDragging = true
        val targetPosition = if (checked) 1f else 0f
        val tailTargetPosition = if (checked) 1 - HoldEffectShift else HoldEffectShift

        if (isAttachedToWindow && isLaidOut) {
            if (isChecked) {
                springToPosition(thumbRightAnimation, targetPosition, bounce = true)
                springToPosition(thumbLeftAnimation, tailTargetPosition, bounce = true)
            } else {
                springToPosition(thumbRightAnimation, tailTargetPosition, bounce = true)
                springToPosition(thumbLeftAnimation, targetPosition, bounce = true)
            }
            springToPosition(trackTintAnimation, targetPosition)
        } else {
            snapToPosition(targetPosition)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (isEnabled) 1f else 0.5f
    }

    override fun performClick(): Boolean {
        toggle()
        val handled = super.performClick()
        if (!handled) {
            // View only makes a sound effect if the onClickListener was
            // called, so we'll need to make one here instead.
            playSoundEffect(SoundEffectConstants.CLICK)
        }
        return handled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isEnabled) {
                    touchMode = TouchModeDown
                    touchX = event.x
                    touchY = event.y
                    if (isChecked) {
                        handler.postDelayed(holdFromRightEffect, getHoldEffectAnimationDelay())
                    } else {
                        handler.postDelayed(holdFromLeftEffect, getHoldEffectAnimationDelay())
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
                        val dragToggleThreshold = getDragToggleThreshold(isChecked)
                        if (isChecked) {
                            when {
                                event.x < dragToggleThreshold -> {
                                    setCheckedByDrag(!isChecked)
                                }
                                event.x > measuredWidth && stateInternal != State.Checked.Default -> {
                                    isChecked = isChecked
                                }
                                event.x < measuredWidth && stateInternal != State.Checked.Pressed -> {
                                    holdFromRightEffect.run()
                                }
                            }
                        } else {
                            when {
                                event.x > dragToggleThreshold -> {
                                    setCheckedByDrag(!isChecked)
                                }
                                event.x < 0 && stateInternal != State.Unchecked.Default -> {
                                    isChecked = isChecked
                                }
                                event.x > 0 && stateInternal != State.Unchecked.Pressed -> {
                                    holdFromLeftEffect.run()
                                }
                            }
                        }
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelAllScheduledAnimations()
                when (touchMode) {
                    TouchModeIdle -> Unit
                    TouchModeDown -> {
                        performClick()
                        touchMode = TouchModeIdle
                        return true
                    }
                    TouchModeDragging -> {
                        val cancelThreshold = measuredWidth * CancelThresholdPercent
                        val isCancelledToLeft = !isChecked && event.x < -cancelThreshold
                        val isCancelledToRight = isChecked && event.x > measuredWidth + cancelThreshold

                        when {
                            wasToggledByDragging -> {
                                wasToggledByDragging = false
                                isChecked = isChecked
                            }
                            isCancelledToLeft || isCancelledToRight -> {
                                isChecked = isChecked
                            }
                            else -> {
                                toggle()
                                playSoundEffect(SoundEffectConstants.CLICK)
                            }
                        }
                        touchMode = TouchModeIdle
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpec = MeasureSpec.makeMeasureSpec((scale * Width.dp).roundToInt(), MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec((scale * Height.dp).roundToInt(), MeasureSpec.EXACTLY)
        super.onMeasure(widthSpec, heightSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        trackDrawable.setBounds(0, 0, w, h)
        val thumbOffset = (h.toFloat() * ThumbOffsetPercent)
        thumbRadius = h / 2f - thumbOffset
        thumbMin = thumbOffset + thumbRadius
        thumbMax = w - thumbOffset - thumbRadius
        invalidateThumbBounds()
        invalidateSpringMinVisibleChange()
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        trackDrawable.draw(canvas)
        thumb.draw(canvas)
        drawDebug(canvas)
    }

    private fun invalidateSpringMinVisibleChange() {
        val minChange = 1f / (thumbMax - thumbMin)
        thumbLeftAnimation.minimumVisibleChange = minChange
        thumbRightAnimation.minimumVisibleChange = minChange
        trackTintAnimation.minimumVisibleChange = 1f / 256f
    }

    private fun invalidateThumbBounds() {
        val thumbCenterLeftX = getThumbCenterPosition(fraction = thumbPosition.left)
        val thumbCenterRightX = getThumbCenterPosition(fraction = thumbPosition.right)
        val thumbCenterY = measuredHeight / 2f
        val thumbLeft = (thumbCenterLeftX - thumbRadius).roundToInt()
        val thumbRight = (thumbCenterRightX + thumbRadius).roundToInt()
        val thumbTop = (thumbCenterY - thumbRadius).roundToInt()
        val thumbBottom = (thumbCenterY + thumbRadius).roundToInt()
        thumb.setBounds(thumbLeft, thumbTop, thumbRight, thumbBottom)
        invalidate()
    }

    private fun springToPosition(animation: SpringAnimation, position: Float, bounce: Boolean = false) {
        animation.apply {
            spring = SpringForce(position).apply {
                stiffness = getSpringStiffness()
                dampingRatio = if (bounce) SpringBounceRatio else SpringNoBounce
            }
            start()
        }
    }

    private fun snapToPosition(position: Float) {
        thumbLeftAnimation.cancel()
        thumbRightAnimation.cancel()
        thumbLeftPositionValueHolder.value = position
        thumbRightPositionValueHolder.value = position
        trackTintFractionValueHolder.value = position
    }

    private fun getThumbCenterPosition(fraction: Float): Float {
        return thumbMin + (thumbMax - thumbMin) * fraction
    }

    private fun getDragToggleThreshold(isChecked: Boolean): Float {
        val percent = if (isChecked) (1 - DragToggleThresholdPercent) else DragToggleThresholdPercent
        return measuredWidth * percent
    }

    private fun getSpringStiffness(): Float {
        return SpringStiffness / getSystemAnimationScale()
    }

    private fun getHoldEffectAnimationDelay(): Long {
        return (HoldEffectAnimationDelay * getSystemAnimationScale()).roundToLong()
    }

    private fun getSystemAnimationScale(): Float {
        return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun cancelAllScheduledAnimations() {
        handler?.run {
            removeCallbacks(holdFromLeftEffect)
            removeCallbacks(holdFromRightEffect)
        }
    }

    private val debugPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val debugPaintGray = Paint().apply {
        color = Color.GRAY
        strokeWidth = 0f
        isAntiAlias = true
    }

    private fun drawDebug(canvas: Canvas) {
        if (!Debug) return
        canvas.drawLine(thumbMin, 0f, thumbMin, measuredHeight.toFloat(), debugPaintGray)
        canvas.drawLine(thumbMax, 0f, thumbMax, measuredHeight.toFloat(), debugPaintGray)
        val dragToggleThreshold = getDragToggleThreshold(isChecked)
        canvas.drawLine(dragToggleThreshold, 0f, dragToggleThreshold, measuredHeight.toFloat(), debugPaint)
        val thumbCenterLeftX = getThumbCenterPosition(fraction = thumbPosition.left)
        val thumbCenterRightX = getThumbCenterPosition(fraction = thumbPosition.right)
        val thumbCenterY = measuredHeight / 2f
        canvas.drawCircle(thumbCenterLeftX, thumbCenterY, 2f.dp, debugPaint)
        canvas.drawCircle(thumbCenterRightX, thumbCenterY, 2f.dp, debugPaint)
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
            updateShadow(bounds)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawRoundRect(circleBounds, radius, radius, paint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.OPAQUE

        override fun getConstantState(): ConstantState? = null

        private fun updateShadow(bounds: Rect) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return
            }
            val shadowRadius = bounds.height() * SHADOW_RADIUS_PERCENT
            val shadowY = bounds.height() * SHADOW_Y_PERCENT
            paint.setShadowLayer(shadowRadius, 0f, shadowY, shadowColor)
        }

        companion object {
            private const val SHADOW_RADIUS_PERCENT = 0.09677f
            private const val SHADOW_Y_PERCENT = 0.09677f
        }
    }

    class ThumpPosition(val right: Float, val left: Float)

    companion object {
        private const val Debug = true

        private const val TouchModeIdle = 0
        private const val TouchModeDown = 1
        private const val TouchModeDragging = 2

        private const val Width = 51
        private const val Height = 31
        private const val ThumbOffsetPercent = 0.0645f

        private const val HoldEffectAnimationDelay = 60L
        private const val SpringStiffness = 400f
        private const val SpringNoBounce = 1f
        private const val SpringBounceRatio = 0.75f
        private const val HoldEffectShift = 0.33f
        private const val CancelThresholdPercent = 0.5f
        private const val DragToggleThresholdPercent = 0.6f
    }
}