package vadiole.uiswitch.control

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import vadiole.uiswitch.R
import vadiole.uiswitch.ResourcesOwner

class UISwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle), ResourcesOwner {
    private val backgroundPaint = Paint().apply {
        color = context.getColor(R.color.green_primary)
        isAntiAlias = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpec = MeasureSpec.makeMeasureSpec(51.dp, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(31.dp, MeasureSpec.EXACTLY)
        super.onMeasure(widthSpec, heightSpec)
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        canvas.drawPaint(backgroundPaint)
    }
}