package com.reelcounter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var todayValue: TextView
    private lateinit var totalValue: TextView
    private lateinit var statusValue: TextView
    private lateinit var trackingButton: Button
    private lateinit var diagnosticValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildDashboard()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
        if (hasNotificationPermission()) {
            ReelAccessibilityService.updateNotification(this, StatsStore.todayCount(this))
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ReelAccessibilityService.updateNotification(this, StatsStore.todayCount(this))
        }
    }

    private fun buildDashboard() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(28, 36, 28, 28)
        }
        content.addView(TextView(this).apply { text = "ReelCounter"; textSize = 30f })
        content.addView(TextView(this).apply { text = "Reel detection diagnostics"; textSize = 16f })
        todayValue = TextView(this).apply { textSize = 42f }
        content.addView(todayValue)
        content.addView(TextView(this).apply { text = "REELS TODAY"; textSize = 13f })
        totalValue = TextView(this).apply { textSize = 22f }
        content.addView(totalValue)
        statusValue = TextView(this).apply { textSize = 14f }
        content.addView(statusValue)

        trackingButton = Button(this)
        trackingButton.setOnClickListener {
            val enabled = !StatsStore.isTrackingEnabled(this)
            StatsStore.setTrackingEnabled(this, enabled)
            refreshDashboard()
        }
        content.addView(trackingButton)

        content.addView(Button(this).apply {
            text = "Reset counts"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Reset all counts?")
                    .setMessage("This clears today's count and the all-time total.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reset") { _, _ ->
                        StatsStore.resetCounts(this@MainActivity)
                        ReelAccessibilityService.updateNotification(this@MainActivity, 0)
                        refreshDashboard()
                    }
                    .show()
            }
        })
        content.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        content.addView(TextView(this).apply {
            text = "LATEST INSTAGRAM EVENTS"
            textSize = 14f
        })
        diagnosticValue = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }
        content.addView(diagnosticValue)
        content.addView(Button(this).apply {
            text = "Refresh diagnostic log"
            setOnClickListener { refreshDashboard() }
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        setContentView(scroll)
    }

    private fun refreshDashboard() {
        todayValue.text = StatsStore.todayCount(this).toString()
        totalValue.text = "All-time total: ${StatsStore.totalCount(this)}"
        val enabled = StatsStore.isTrackingEnabled(this)
        trackingButton.text = if (enabled) "Stop tracking" else "Start tracking"
        statusValue.text = if (enabled) {
            "Tracking is ON. Diagnostic events are being collected; automatic Reel counting remains paused until Reel detection is verified."
        } else {
            "Tracking is OFF. Instagram events are not being collected."
        }
        diagnosticValue.text = StatsStore.diagnostics(this)
    }
}