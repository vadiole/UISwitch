package vadiole.uiswitch

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowCompat
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import vadiole.uiswitch.control.uiswitch.UISwitch

class MainActivity : Activity(), ResourcesOwner {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(
                0, insets.getInsets(statusBars()).top,
                0, insets.getInsets(navigationBars()).bottom
            )
            insets
        }

        val context = this

        setContentView(
            LinearLayout(context).apply {
                setPadding(0, 40.dp, 0, 40.dp)
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
                dividerDrawable = object : ColorDrawable() {
                    override fun getIntrinsicHeight(): Int {
                        return 40.dp
                    }
                }
                clipChildren = false
                addView(
                    UISwitch(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            wrapContent,
                            wrapContent,
                        )
                    }
                )
                addView(
                    UISwitch(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            wrapContent,
                            wrapContent,
                        )
                    }
                )
                addView(
                    UISwitch(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            wrapContent,
                            wrapContent,
                        )
                        scaleX = 1f / 5f
                        scaleY = 1f / 5f
                    }
                )
                addView(
                    UISwitch(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            wrapContent,
                            wrapContent,
                        )
                        scaleX = 1f / 5f
                        scaleY = 1f / 5f
                    }
                )

            }
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            updateSystemBars(resources.configuration)
        }
    }

    private fun updateSystemBars(configuration: Configuration) {
        val isNightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isNightMode
        insetsController.isAppearanceLightNavigationBars = !isNightMode
    }
}