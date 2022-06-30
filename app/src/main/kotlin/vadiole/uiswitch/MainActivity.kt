package vadiole.uiswitch

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowCompat
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars

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

        setContentView(
            TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(matchParent, matchParent)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 14f.dp)
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                text = "UISwitch by vadiole"
            }
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBars(newConfig)
        window.decorView.setBackgroundColor(getColor(R.color.windowBackground))
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