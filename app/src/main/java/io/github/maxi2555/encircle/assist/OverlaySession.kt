package io.github.maxi2555.encircle.assist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.maxi2555.encircle.ui.EncircleTheme
import io.github.maxi2555.encircle.ui.OverlayController
import io.github.maxi2555.encircle.ui.OverlayScreen

/**
 * The full-screen overlay. The system hands it a screenshot of whatever was on screen when the
 * user long-pressed home; the UI shows that frozen frame and lets the user pick text and codes on it.
 */
class OverlaySession(context: Context) : VoiceInteractionSession(context) {

    private val owner = SessionLifecycleOwner()
    private lateinit var controller: OverlayController

    override fun onCreate() {
        super.onCreate()
        owner.create()
        owner.resume()
        controller = OverlayController(context) { hide() }
        window.window?.let(::configureWindow)
    }

    private fun configureWindow(w: Window) {
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowCompat.setDecorFitsSystemWindows(w, false)
        @Suppress("DEPRECATION")
        run {
            w.statusBarColor = Color.TRANSPARENT
            w.navigationBarColor = Color.TRANSPARENT
            w.isNavigationBarContrastEnforced = false
            w.isStatusBarContrastEnforced = false
        }
        w.attributes = w.attributes.apply {
            // No slide-in: the overlay must appear exactly on top of the frozen frame.
            windowAnimations = 0
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    override fun onCreateContentView(): View {
        val decor = window.window!!.decorView
        decor.setViewTreeLifecycleOwner(owner)
        decor.setViewTreeSavedStateRegistryOwner(owner)
        decor.setViewTreeViewModelStoreOwner(owner)
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
            setContent { EncircleTheme { OverlayScreen(controller) } }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                // The session's built-in container pads for system bars; we draw edge to edge instead.
                var p = v.parent
                while (p is View && p !== decor) {
                    p.fitsSystemWindows = false
                    p.setPadding(0, 0, 0, 0)
                    p = p.parent
                }
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        controller.begin(screenshotExpected = showFlags and SHOW_WITH_SCREENSHOT != 0)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        controller.onScreenshot(screenshot)
    }

    override fun onBackPressed() {
        controller.dismiss()
    }

    override fun onHide() {
        controller.reset()
        super.onHide()
    }

    override fun onDestroy() {
        controller.dispose()
        owner.destroy()
        super.onDestroy()
    }
}
