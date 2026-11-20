package com.reelcounter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val requestCodeNotifications = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "ReelCounter Capsule Test"
            textSize = 24f
        }
        val description = TextView(this).apply {
            text = "This test sends an updating ongoing notification. We are checking whether Realme UI displays it in Live Alerts / Mini Capsule."
            textSize = 16f
        }
        val start = Button(this).apply {
            text = "Start test counter"
            setOnClickListener { requestPermissionAndStart() }
        }
        val stop = Button(this).apply {
            text = "Stop test counter"
            setOnClickListener { stopService(Intent(this@MainActivity, CounterService::class.java)) }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            addView(title)
            addView(description)
            addView(start)
            addView(stop)
        })
    }

    private fun requestPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCodeNotifications)
        } else {
            startCounter()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodeNotifications &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCounter()
        }
    }

    private fun startCounter() {
        val intent = Intent(this, CounterService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
