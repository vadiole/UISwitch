package vadiole.uiswitch.control

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import vadiole.uiswitch.R
import vadiole.uiswitch.ResourcesOwner

class UISwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle), ResourcesOwner {
    private val trackDrawable = context.getDrawable(R.drawable.switch_track)!!.apply {
        setTint(context.getColor(R.color.green_primary))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        trackDrawable.setBounds(0, 0, w, h)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpec = MeasureSpec.makeMeasureSpec(51.dp, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(31.dp, MeasureSpec.EXACTLY)
        super.onMeasure(widthSpec, heightSpec)
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        trackDrawable.draw(canvas)
    }
}