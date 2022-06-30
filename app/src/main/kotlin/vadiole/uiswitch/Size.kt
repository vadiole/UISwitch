package vadiole.uiswitch

import android.content.res.Resources
import android.view.ViewGroup
import kotlin.math.ceil

const val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
const val wrapContent = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * A little hack to avoid passing [Resources] every time when you need a size in dp
 *
 * Can be refactored to context receivers when available
 */
interface ResourcesOwner {
    fun getResources(): Resources

    val Int.dp: Int
        get() {
            return ceil(this * getResources().displayMetrics.density).toInt()
        }

    val Float.dp: Float
        get() {
            return this * getResources().displayMetrics.density
        }
}