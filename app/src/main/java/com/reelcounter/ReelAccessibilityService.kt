package com.reelcounter

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
        updateNotification(StatsStore.todayCount(this))
        StatsStore.addDiagnostic(this, "SERVICE connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !StatsStore.isTrackingEnabled(this)) return
        if (event.packageName?.toString() != INSTAGRAM_PACKAGE) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val source = event.source
            val bounds = android.graphics.Rect()
            source?.getBoundsInScreen(bounds)
            StatsStore.addDiagnostic(
                this,
                "SCROLL class=${source?.className ?: event.className ?: "?"} id=${source?.viewIdResourceName ?: "-"} scrollable=${source?.isScrollable ?: false} bounds=${bounds.flattenToString()} text=${(source?.text ?: source?.contentDescription ?: "-").toString().take(60)}"
            )
            logScrollableNodes(rootInActiveWindow)
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            StatsStore.addDiagnostic(this, "WINDOW ${event.className ?: "?"}")
        }
    }

    private fun logScrollableNodes(root: AccessibilityNodeInfo?) {
        if (root == null) {
            StatsStore.addDiagnostic(this, "ROOT unavailable")
            return
        }
        var visited = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= 30 || depth > 7) return
            visited++
            if (node.isScrollable || node.viewIdResourceName != null) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                StatsStore.addDiagnostic(
                    this,
                    "NODE d=$depth class=${node.className} id=${node.viewIdResourceName ?: "-"} scroll=${node.isScrollable} bounds=${bounds.flattenToString()} text=${(node.text ?: node.contentDescription ?: "-").toString().take(35)}"
                )
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1)
                child.recycle()
                if (visited >= 30) break
            }
        }
        walk(root, 0)
    }

    override fun onInterrupt() = Unit

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "ReelCounter live count", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Displays the confirmed Instagram Reel count"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createCountIcon(count: Int): Icon {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val label = count.coerceAtLeast(0).toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 34f
        }
        while (paint.measureText(label) > 46f && paint.textSize > 8f) paint.textSize -= 1f
        val baseline = (48f - (paint.ascent() + paint.descent())) / 2f
        canvas.drawText(label, 24f, baseline, paint)
        return Icon.createWithBitmap(bitmap)
    }

    private fun updateNotification(todayCount: Int) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(createCountIcon(todayCount))
            .setContentTitle("ReelCounter")
            .setContentText("$todayCount Reels today")
            .setSubText("Instagram Reel counter")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val CHANNEL_ID = "reel_counter_live"
        private const val NOTIFICATION_ID = 247
    }
}