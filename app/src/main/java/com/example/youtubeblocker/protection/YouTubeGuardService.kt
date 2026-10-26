package com.example.youtubeblocker.protection

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.youtubeblocker.ReasonRules
import com.example.youtubeblocker.data.BlockerApplication
import com.example.youtubeblocker.data.Entry
import com.example.youtubeblocker.ui.theme.YouTubeBlockerTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.TimeZone
import java.util.UUID

class YouTubeGuardService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = SessionGate()
    private val data get() = (application as BlockerApplication).data
    private var overlay: GateOverlay? = null
    private var reason by mutableStateOf("")
    private var minimum by mutableStateOf<Int?>(null)
    private var saving by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var attemptId = ""
    private var confirmedAt: Long? = null
    private var confirmedZone: String? = null
    private var lastApp: String? = null
    private var lastAppEventTime = 0L
    private var leaving = false
    private var settingsJob: Job? = null
    private var registered = false
    private var imePackages = emptySet<String>()
    private val transientPackages = setOf("android", "com.android.systemui", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.packageinstaller")

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> reset()
                Intent.ACTION_USER_PRESENT -> if (lastApp == ProtectionStatus.YOUTUBE && !locked()) showGate()
                Intent.ACTION_INPUT_METHOD_CHANGED -> updateKeyboards()
            }
        }
    }

    override fun onServiceConnected() {
        reset()
        leaving = false
        lastApp = null
        lastAppEventTime = 0L
        ProtectionStatus.connected.value = true
        ProtectionStatus.failure.value = null
        updateKeyboards()
        if (!registered) {
            ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_INPUT_METHOD_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
    }

    private fun updateKeyboards() {
        imePackages = getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
    }

    private fun locked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
        !getSystemService(PowerManager::class.java).isInteractive

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val app = event.packageName?.toString() ?: return
        if (locked()) { reset(); return }
        if (app in imePackages || app in transientPackages) return
        // Own overlay also produces window events. Only the actual app activity ends a session.
        if (app == packageName && event.className?.toString() != "com.example.youtubeblocker.MainActivity") return
        // A disappearing pane is not a foreground transition. It can arrive after Home.
        if (event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED != 0) return
        if (event.eventTime < lastAppEventTime) return
        if (leaving && app == ProtectionStatus.YOUTUBE) return
        lastAppEventTime = event.eventTime
        lastApp = app
        if (app == ProtectionStatus.YOUTUBE) {
            showGate()
        } else {
            // Home is asynchronous: remove the overlay only once another app is visible.
            reset()
            leaving = false
        }
    }

    private fun showGate() {
        if (leaving) return
        if (!gate.enter()) return
        reason = ""
        minimum = null
        saving = false
        error = null
        attemptId = UUID.randomUUID().toString()
        confirmedAt = null
        confirmedZone = null
        attachOverlay()
        if (overlay != null) loadMinimum()
    }

    private fun attachOverlay() {
        val window = GateOverlay(this, ::cancel) {
            YouTubeBlockerTheme {
                ReasonScreen(reason, minimum, saving, error, { reason = it }, ::confirm, ::cancel, ::loadMinimum)
            }
        }
        try {
            window.show()
            overlay = window
            ProtectionStatus.failure.value = null
        } catch (_: Exception) {
            window.close()
            ProtectionStatus.failure.value = "Не удалось показать экран причины. Переподключите сервис в настройках."
            gate.leave()
            // Avoid leaving an unguarded YouTube on screen when window creation fails.
            leaving = performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun loadMinimum() {
        settingsJob?.cancel()
        val token = gate.generation
        error = null
        settingsJob = scope.launch {
            try {
                val value = data.minimum.first()
                if (gate.generation == token) minimum = value
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (gate.generation == token) error = "Не удалось загрузить настройку длины. Попробуйте ещё раз."
            }
        }
    }

    private fun confirm() {
        if (leaving) return
        val required = minimum ?: return
        if (!ReasonRules.valid(reason, required)) return
        val token = gate.beginSave() ?: return
        saving = true
        error = null
        // Preserve identity and timestamp on retry, including a commit with an uncertain result.
        val entry = Entry(attemptId, confirmedAt ?: System.currentTimeMillis().also { confirmedAt = it },
            confirmedZone ?: TimeZone.getDefault().id.also { confirmedZone = it }, ReasonRules.clean(reason), required)
        scope.launch {
            try {
                data.entries.insert(entry)
                if (gate.saved(token)) {
                    saving = false
                    overlay?.close()
                    overlay = null
                    reason = ""
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                gate.failed(token)
                if (gate.generation == token) {
                    saving = false
                    error = "Не удалось сохранить причину. Текст остался здесь — попробуйте ещё раз."
                }
            }
        }
    }

    private fun cancel() {
        if (saving || leaving) return
        leaving = true
        if (performGlobalAction(GLOBAL_ACTION_HOME)) {
            // Keep covering YouTube until the launcher arrives. Removing this window now
            // briefly restores YouTube's focus and queues a spurious new-entry event.
            lastApp = null
        } else {
            leaving = false
            error = "Не удалось перейти на главный экран. Используйте системную кнопку «Домой»."
        }
    }

    private fun reset() {
        gate.leave()
        settingsJob?.cancel()
        overlay?.close()
        overlay = null
        reason = ""
        minimum = null
        saving = false
        error = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlay != null) {
            overlay?.close()
            overlay = null
            attachOverlay()
        }
    }

    override fun onInterrupt() { reset() }

    override fun onUnbind(intent: Intent?): Boolean {
        ProtectionStatus.connected.value = false
        reset()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ProtectionStatus.connected.value = false
        reset()
        if (registered) unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }
}
