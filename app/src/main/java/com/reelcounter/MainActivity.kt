package com.reelcounter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var todayValue: TextView
    private lateinit var totalValue: TextView
    private lateinit var statusValue: TextView
    private lateinit var diagnosticValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildDashboard()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(28, 36, 28, 28)
        }
        root.addView(TextView(this).apply { text = "ReelCounter"; textSize = 30f })
        root.addView(TextView(this).apply { text = "Reel detection diagnostics"; textSize = 16f })
        todayValue = TextView(this).apply { textSize = 42f }
        root.addView(todayValue)
        root.addView(TextView(this).apply { text = "REELS TODAY"; textSize = 13f })
        totalValue = TextView(this).apply { textSize = 22f }
        root.addView(totalValue)
        statusValue = TextView(this).apply { textSize = 14f }
        root.addView(statusValue)
        root.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = if (StatsStore.isTrackingEnabled(this@MainActivity)) "Pause diagnostics" else "Resume diagnostics"
            setOnClickListener {
                val enabled = !StatsStore.isTrackingEnabled(this@MainActivity)
                StatsStore.setTrackingEnabled(this@MainActivity, enabled)
                text = if (enabled) "Pause diagnostics" else "Resume diagnostics"
                refreshDashboard()
            }
        })
        root.addView(TextView(this).apply {
            text = "LATEST INSTAGRAM EVENTS"
            textSize = 14f
        })
        diagnosticValue = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }
        root.addView(diagnosticValue)
        root.addView(Button(this).apply {
            text = "Refresh diagnostic log"
            setOnClickListener { refreshDashboard() }
        })
        setContentView(root)
    }

    private fun refreshDashboard() {
        todayValue.text = StatsStore.todayCount(this).toString()
        totalValue.text = "All-time total: ${StatsStore.totalCount(this)}"
        statusValue.text = if (StatsStore.isTrackingEnabled(this)) {
            "Diagnostic mode is active. Counting is temporarily paused to prevent false Reel totals."
        } else {
            "Diagnostics are paused."
        }
        diagnosticValue.text = StatsStore.diagnostics(this)
    }
}