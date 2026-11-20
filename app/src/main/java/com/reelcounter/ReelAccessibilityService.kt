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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelAccessibilityService : AccessibilityService() {
    private var lastReelIndex: Int? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastReelIndex = null
        updateNotification(this, StatsStore.todayCount(this))
        StatsStore.addDiagnostic(this, "SERVICE connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString()
        if (packageName != INSTAGRAM_PACKAGE) {
            lastReelIndex = null
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

                if (previousIndex == null) {
                    lastReelIndex = currentIndex
                    StatsStore.addDiagnostic(this, "REELS baseline index=$currentIndex")
                } else if (currentIndex != previousIndex) {
                    lastReelIndex = currentIndex
                    val count = StatsStore.recordReel(this)
                    updateNotification(this, count)
                    StatsStore.addDiagnostic(
                        this,
                        "REEL counted index=$previousIndex->$currentIndex today=$count"
                    )
                }
            } else if (sourceClass == RECYCLER_VIEW) {
                // A normal feed scroll is not a Reel navigation. Clear the baseline
                // so returning to the Reels viewer cannot count an index jump.
                lastReelIndex = null
            }

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

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val REELS_VIEW_PAGER = "androidx.viewpager.widget.ViewPager"
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

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                Notification.Builder(context)
            }
            val notification = builder
                .setSmallIcon(Icon.createWithBitmap(bitmap))
                .setContentTitle("ReelCounter")
                .setContentText("$todayCount Reels today")
                .setSubText("Instagram Reel counter")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }
}
