package com.reelcounter

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelAccessibilityService : AccessibilityService() {
    private var lastReelIndex: Int? = null
    private var lastReelItemCount: Int? = null
    private val watchHandler = Handler(Looper.getMainLooper())
    private var watchTimerRunning = false
    private var lastWatchTick = 0L
    private var lastViewerDetectedAt = 0L
    private var lastWatchScanAt = 0L

    private val watchTick = object : Runnable {
        override fun run() {
            if (!watchTimerRunning) return
            val now = SystemClock.elapsedRealtime()
            val root = rootInActiveWindow
            val viewerVisible = StatsStore.isTrackingEnabled(this@ReelAccessibilityService) &&
                root?.packageName?.toString() == INSTAGRAM_PACKAGE && containsReelsPager(root)
            if (viewerVisible) lastViewerDetectedAt = now

            val elapsed = ((now - lastWatchTick) / 1000L).coerceIn(0L, 2L)
            if (elapsed > 0L && now - lastViewerDetectedAt <= VIEWER_GRACE_MS) {
                repeat(elapsed.toInt()) { StatsStore.recordWatchSecond(this@ReelAccessibilityService) }
            }
            lastWatchTick = now

            if (!StatsStore.isTrackingEnabled(this@ReelAccessibilityService) ||
                now - lastViewerDetectedAt > VIEWER_GRACE_MS
            ) {
                stopWatchTimer()
                return
            }
            watchHandler.postDelayed(this, 1000L)
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
            // Ignore transient system/overlay accessibility events. Only clear the
            // Reel baseline when a real window switch explicitly leaves Instagram.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                packageName != null
            ) {
                lastReelIndex = null
                lastReelItemCount = null
                StatsStore.addDiagnostic(this, "REELS baseline cleared: left Instagram package=$packageName")
            }
            return
        }

        if (!StatsStore.isTrackingEnabled(this)) {
            lastReelIndex = null
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val source = event.source
            val sourceClass = source?.className?.toString()
            val bounds = android.graphics.Rect()
            source?.getBoundsInScreen(bounds)

            StatsStore.addDiagnostic(
                this,
                "SCROLL class=${sourceClass ?: event.className ?: "?"} id=${source?.viewIdResourceName ?: "-"} scrollable=${source?.isScrollable ?: false} from=${event.fromIndex} to=${event.toIndex} items=${event.itemCount} x=${event.scrollX} y=${event.scrollY} maxX=${event.maxScrollX} maxY=${event.maxScrollY} bounds=${bounds.flattenToString()} text=${(source?.text ?: source?.contentDescription ?: "-").toString().take(60)}"
            )

            if (sourceClass == REELS_VIEW_PAGER && source?.isScrollable == true && event.toIndex >= 0) {
                val currentIndex = event.toIndex
                val previousIndex = lastReelIndex

                val currentItemCount = event.itemCount

                if (previousIndex == null) {
                    lastReelIndex = currentIndex
                    lastReelItemCount = currentItemCount
                    StatsStore.addDiagnostic(
                        this,
                        "REELS baseline index=$currentIndex items=$currentItemCount"
                    )
                } else if (currentIndex != previousIndex) {
                    val crossedReels = kotlin.math.abs(currentIndex - previousIndex)
                    lastReelIndex = currentIndex
                    lastReelItemCount = currentItemCount

                    // Ignore large pager resets, but do not reset the baseline
                    // just because Instagram appended more items while loading Reels.
                    if (crossedReels > MAX_REEL_STEPS_PER_EVENT) {
                        StatsStore.addDiagnostic(
                            this,
                            "REELS ignored navigation jump steps=$crossedReels index=$previousIndex->$currentIndex items=$currentItemCount"
                        )
                    } else {
                        var count = StatsStore.todayCount(this)
                        repeat(crossedReels) {
                            count = StatsStore.recordReel(this)
                        }
                        updateNotification(this, count)
                        StatsStore.addDiagnostic(
                            this,
                            "REELS counted steps=$crossedReels index=$previousIndex->$currentIndex items=$currentItemCount today=$count"
                        )
                    }
                }
            } else if (sourceClass == RECYCLER_VIEW) {
                // Instagram can emit RecyclerView scroll events for trays and nested
                // containers while the Reels pager remains active. Do not discard the
                // pager index; its next ViewPager event is still the authoritative index.
                StatsStore.addDiagnostic(this, "REELS baseline preserved on nested RecyclerView event")
            }

            logScrollableNodes(rootInActiveWindow)
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            refreshWatchDetection()
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                StatsStore.addDiagnostic(this, "WINDOW ${event.className ?: "?"}")
            }
        }
    }

    private fun refreshWatchDetection() {
        val scanNow = SystemClock.elapsedRealtime()
        if (scanNow - lastWatchScanAt < 1500L) return
        lastWatchScanAt = scanNow
        if (!StatsStore.isTrackingEnabled(this)) {
            stopWatchTimer()
            return
        }
        val root = rootInActiveWindow
        if (root?.packageName?.toString() == INSTAGRAM_PACKAGE && containsReelsPager(root)) {
            val now = SystemClock.elapsedRealtime()
            lastViewerDetectedAt = now
            if (!watchTimerRunning) {
                watchTimerRunning = true
                lastWatchTick = now
                StatsStore.startSession(this)
                StatsStore.addDiagnostic(this, "WATCH session started")
                watchHandler.postDelayed(watchTick, 1000L)
            }
        }
    }

    private fun containsReelsPager(root: AccessibilityNodeInfo): Boolean {
        var found = false
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (found || depth > 12) return
            if (node.className?.toString() == REELS_VIEW_PAGER && node.isScrollable) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.width() > 0 && bounds.height() > 0) found = true
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

    private fun stopWatchTimer() {
        if (!watchTimerRunning) return
        watchTimerRunning = false
        watchHandler.removeCallbacks(watchTick)
        StatsStore.addDiagnostic(this, "WATCH session paused")
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

    override fun onInterrupt() {
        stopWatchTimer()
    }

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
        private const val VIEWER_GRACE_MS = 5000L

        fun updateNotification(context: Context, todayCount: Int) {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "Reelo live count", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Displays the live Reel count"
                    setShowBadge(false)
                }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                Notification.Builder(context)
            }
            val liveAlertExtras = android.os.Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
                putString("android.shortCriticalText", todayCount.coerceAtLeast(0).toString())
            }
            val notification = builder
                .setSmallIcon(R.drawable.ic_reelo_notification)
                .setContentTitle("Reelo")
                .setContentText("$todayCount Reels today")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .addExtras(liveAlertExtras)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }
}
