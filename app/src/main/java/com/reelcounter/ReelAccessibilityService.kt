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

class ReelAccessibilityService : AccessibilityService() {
    private var lastScrollAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
        updateNotification(StatsStore.todayCount(this))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !StatsStore.isTrackingEnabled(this)) return
        if (event.packageName?.toString() != INSTAGRAM_PACKAGE) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

        val now = System.currentTimeMillis()
        if (now - lastScrollAt < SCROLL_DEBOUNCE_MS) return
        lastScrollAt = now

        val todayCount = StatsStore.recordReel(this)
        updateNotification(todayCount)
    }

    override fun onInterrupt() = Unit

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ReelCounter live count",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Updates when an Instagram Reel scroll is detected"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createCountIcon(count: Int): Icon {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val label = if (count > 999) "99+" else count.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when (label.length) {
                1 -> 34f
                2 -> 28f
                else -> 21f
            }
        }
        val baseline = (48f - (paint.ascent() + paint.descent())) / 2f
        canvas.drawText(label, 24f, baseline, paint)
        return Icon.createWithBitmap(bitmap)
    }

    private fun updateNotification(todayCount: Int) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(createCountIcon(todayCount))
            .setContentTitle("ReelCounter")
            .setContentText("$todayCount Reels today")
            .setSubText("Instagram Reel counter")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val CHANNEL_ID = "reel_counter_live"
        private const val NOTIFICATION_ID = 247
        private const val SCROLL_DEBOUNCE_MS = 1000L
    }
}
