package com.example.youtubeblocker.protection

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow

object ProtectionStatus {
    const val YOUTUBE = "com.google.android.youtube"
    val connected = MutableStateFlow(false)
    val failure = MutableStateFlow<String?>(null)

    fun enabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val component = ComponentName(context, YouTubeGuardService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == component }
    }

    fun youtubeInstalled(context: Context) = runCatching {
        context.packageManager.getApplicationInfo(YOUTUBE, 0).enabled
    }.getOrDefault(false)

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
