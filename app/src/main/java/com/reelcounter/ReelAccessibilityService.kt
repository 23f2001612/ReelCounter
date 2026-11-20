package com.reelcounter

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelAccessibilityService : AccessibilityService() {
    private var lastReelIndex: Int? = null
    private var lastReelItemCount: Int? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private var watchTimerRunning = false
    private var lastWatchTick = 0L

    private val watchTick = object : Runnable {
        override fun run() {
            if (!watchTimerRunning) return
            val root = rootInActiveWindow
            val stillInReels = StatsStore.isTrackingEnabled(this@ReelAccessibilityService) &&
                root?.packageName?.toString() == INSTAGRAM_PACKAGE && containsReelsPager(root)
            if (!stillInReels) {
                stopWatchTimer()
                return
            }
            val now = SystemClock.elapsedRealtime()
            val elapsed = ((now - lastWatchTick) / 1000L).coerceAtLeast(1L).coerceAtMost(2L)
            repeat(elapsed.toInt()) { StatsStore.recordWatchSecond(this@ReelAccessibilityService) }
            lastWatchTick = now
            timerHandler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastReelIndex = null
        lastReelItemCount = null
        updateNotification(this, StatsStore.todayCount(this))
        StatsStore.addDiagnostic(this, "SERVICE connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString()
        if (packageName != INSTAGRAM_PACKAGE) {
            lastReelIndex = null
            lastReelItemCount = null
            stopWatchTimer()
            return
        }
        if (!StatsStore.isTrackingEnabled(this)) {
            lastReelIndex = null
            stopWatchTimer()
            return
        }

        val root = rootInActiveWindow
        if (root != null && containsReelsPager(root)) startWatchTimer() else stopWatchTimer()

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val source = event.source
            val sourceClass = source?.className?.toString()
            val bounds = android.graphics.Rect()
            source?.getBoundsInScreen(bounds)
            StatsStore.addDiagnostic(
                this,
                "SCROLL class=${sourceClass ?: event.className ?: "?"} id=${source?.viewIdResourceName ?: "-"} scrollable=${source?.isScrollable ?: false} from=${event.fromIndex} to=${event.toIndex} items=${event.itemCount} bounds=${bounds.flattenToString()}"
            )

            if (sourceClass == REELS_VIEW_PAGER && source?.isScrollable == true && event.toIndex >= 0) {
                val currentIndex = event.toIndex
                val previousIndex = lastReelIndex
                val currentItemCount = event.itemCount
                if (previousIndex == null) {
                    lastReelIndex = currentIndex
                    lastReelItemCount = currentItemCount
                } else if (currentIndex != previousIndex) {
                    val crossedReels = kotlin.math.abs(currentIndex - previousIndex)
                    lastReelIndex = currentIndex
                    lastReelItemCount = currentItemCount
                    if (crossedReels > MAX_REEL_STEPS_PER_EVENT) {
                        StatsStore.addDiagnostic(this, "REELS ignored navigation jump steps=$crossedReels")
                    } else {
                        var count = StatsStore.todayCount(this)
                        repeat(crossedReels) { count = StatsStore.recordReel(this) }
                        updateNotification(this, count)
                        StatsStore.addDiagnostic(this, "REELS counted steps=$crossedReels today=$count")
                    }
                }
            } else if (sourceClass == RECYCLER_VIEW) {
                lastReelIndex = null
                lastReelItemCount = null
            }
            logScrollableNodes(rootInActiveWindow)
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            StatsStore.addDiagnostic(this, "WINDOW ${event.className ?: "?"}")
        }
    }

    private fun startWatchTimer() {
        if (watchTimerRunning) return
        watchTimerRunning = true
        lastWatchTick = SystemClock.elapsedRealtime()
        StatsStore.startSession(this)
        StatsStore.addDiagnostic(this, "WATCH session started")
        timerHandler.postDelayed(watchTick, 1000L)
    }

    private fun stopWatchTimer() {
        if (!watchTimerRunning) return
        watchTimerRunning = false
        timerHandler.removeCallbacks(watchTick)
        StatsStore.addDiagnostic(this, "WATCH session paused")
    }

    private fun containsReelsPager(root: AccessibilityNodeInfo): Boolean {
        var found = false
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (found || depth > 12) return
            if (node.className?.toString() == REELS_VIEW_PAGER && node.isScrollable) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) found = true
                return
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1)
                child.recycle()
                if (found) return
            }
        }
        walk(root, 0)
        return found
    }

    private fun logScrollableNodes(root: AccessibilityNodeInfo?) {
        if (root == null) return
        var visited = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= 30 || depth > 7) return
            visited++
            if (node.isScrollable || node.viewIdResourceName != null) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                StatsStore.addDiagnostic(this, "NODE d=$depth class=${node.className} id=${node.viewIdResourceName ?: "-"} scroll=${node.isScrollable} bounds=${bounds.flattenToString()}")
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

    override fun onInterrupt() { stopWatchTimer() }

    override fun onDestroy() {
        stopWatchTimer()
        super.onDestroy()
    }

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val REELS_VIEW_PAGER = "androidx.viewpager.widget.ViewPager"
        private const val MAX_REEL_STEPS_PER_EVENT = 3
        private const val RECYCLER_VIEW = "androidx.recyclerview.widget.RecyclerView"
        private const val CHANNEL_ID = "reel_counter_live"
        private const val NOTIFICATION_ID = 247

        fun updateNotification(context: Context, todayCount: Int) {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "ReelCounter live count", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Displays the confirmed Instagram Reel count"
                    setShowBadge(false)
                }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val label = todayCount.coerceAtLeast(0).toString()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 34f
            }
            while (paint.measureText(label) > 46f && paint.textSize > 8f) paint.textSize -= 1f
            val baseline = (48f - (paint.ascent() + paint.descent())) / 2f
            canvas.drawText(label, 24f, baseline, paint)
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context)
            val notification = builder.setSmallIcon(Icon.createWithBitmap(bitmap))
                .setContentTitle("ReelCounter")
                .setContentText("$todayCount Reels today")
                .setSubText("Instagram Reel counter")
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS).build()
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }
}