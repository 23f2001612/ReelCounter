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

        val title = TextView(this).apply {
            text = "ReelCounter"
            textSize = 30f
        }
        val subtitle = TextView(this).apply {
            text = "Your Instagram Reels tracker"
            textSize = 16f
        }
        todayValue = TextView(this).apply { textSize = 42f }
        val todayLabel = TextView(this).apply {
            text = "REELS TODAY"
            textSize = 13f
        }
        totalValue = TextView(this).apply { textSize = 22f }
        statusValue = TextView(this).apply { textSize = 14f }

        val enableButton = Button(this).apply {
            text = "Enable background tracking"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val trackingButton = Button(this).apply {
            text = if (StatsStore.isTrackingEnabled(this@MainActivity)) "Pause tracking" else "Resume tracking"
            setOnClickListener {
                val enabled = !StatsStore.isTrackingEnabled(this@MainActivity)
                StatsStore.setTrackingEnabled(this@MainActivity, enabled)
                text = if (enabled) "Pause tracking" else "Resume tracking"
                refreshDashboard()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(todayValue)
        root.addView(todayLabel)
        root.addView(totalValue)
        root.addView(statusValue)
        root.addView(enableButton)
        root.addView(trackingButton)
        setContentView(root)
    }

    private fun refreshDashboard() {
        todayValue.text = StatsStore.todayCount(this).toString()
        totalValue.text = "All-time total: ${StatsStore.totalCount(this)}"
        statusValue.text = if (StatsStore.isTrackingEnabled(this)) {
            "Tracking is enabled. ReelCounter can count while you use other apps."
        } else {
            "Tracking is paused."
        }
    }
}
