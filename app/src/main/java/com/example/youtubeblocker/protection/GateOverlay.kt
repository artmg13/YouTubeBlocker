package com.example.youtubeblocker.protection

import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** A focusable accessibility window: touches cannot reach YouTube, but IME input works. */
class GateOverlay(context: Context, private val cancel: () -> Unit, content: @Composable () -> Unit) :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    private val manager = context.getSystemService(WindowManager::class.java)
    private val root = object : FrameLayout(context) {
        override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) cancel()
                return true
            }
            return super.dispatchKeyEventPreIme(event)
        }
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) cancel()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
    private val compose = ComposeView(context)
    private var attached = false

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        root.addView(compose, FrameLayout.LayoutParams(-1, -1))
        compose.setContent(content)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun show() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0,
            PixelFormat.OPAQUE,
        ).apply {
            title = "Причина входа в YouTube"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        manager.addView(root, params)
        attached = true
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun close() {
        if (attached) {
            manager.removeViewImmediate(root)
            attached = false
        }
        compose.disposeComposition()
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
