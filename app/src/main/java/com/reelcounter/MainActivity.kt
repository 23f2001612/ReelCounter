package com.reelcounter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(13, 15, 20)
    private val card = Color.rgb(25, 28, 36)
    private val muted = Color.rgb(155, 162, 176)
    private val white = Color.rgb(245, 246, 250)
    private val yellow = Color.rgb(255, 210, 65)
    private lateinit var page: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var nav: LinearLayout
    private var selectedTab = "Home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildShell()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        renderTab()
        if (hasNotificationPermission()) ReelAccessibilityService.updateNotification(this, StatsStore.todayCount(this))
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ReelAccessibilityService.updateNotification(this, StatsStore.todayCount(this))
        }
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(0, insets.systemWindowInsetTop, 0, 0)
                nav.setPadding(dp(6), dp(7), dp(6), maxOf(dp(8), insets.systemWindowInsetBottom))
                insets
            }
        }
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(28)) }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(7), dp(6), dp(8))
            background = rounded(card, 22)
        }
        val navWrap = LinearLayout(this).apply { setPadding(dp(14), dp(8), dp(14), dp(8)); setBackgroundColor(bg) }
        navWrap.addView(nav, LinearLayout.LayoutParams(-1, -2))
        root.addView(navWrap)
        setContentView(root)
        renderTab()
        root.requestApplyInsets()
    }

    private fun renderTab() {
        page.removeAllViews()
        nav.removeAllViews()
        listOf("Home", "Insights", "History", "Settings").forEach { tab ->
            val active = selectedTab == tab
            val item = TextView(this).apply {
                text = when (tab) { "Home" -> "⌂\nHome"; "Insights" -> "◷\nInsights"; "History" -> "▤\nHistory"; else -> "⚙\nSettings" }
                gravity = Gravity.CENTER
                textSize = if (resources.configuration.screenWidthDp < 380) 10f else 11f
                setTextColor(if (active) yellow else muted)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                setOnClickListener { selectedTab = tab; renderTab() }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        when (selectedTab) {
            "Home" -> homePage()
            "Insights" -> insightsPage()
            "History" -> historyPage()
            else -> settingsPage()
        }
        scroll.scrollTo(0, 0)
    }

    private fun homePage() {
        header("REELCOUNTER", "Your scrolling, made visible.")
        val tracking = StatsStore.isTrackingEnabled(this)
        val hero = cardLayout()
        hero.addView(label("TODAY'S REEL COUNT", muted, 11f))
        hero.addView(big(StatsStore.todayCount(this).toString(), yellow, if (resources.configuration.screenWidthDp < 380) 46f else 50f))
        hero.addView(label("Reels watched today", muted, 13f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(dot(if (tracking) Color.rgb(76, 210, 139) else muted))
        row.addView(label(if (tracking) "TRACKING ACTIVE" else "TRACKING PAUSED", if (tracking) Color.rgb(112, 224, 165) else muted, 11f).apply { setPadding(dp(8), 0, 0, 0) })
        hero.addView(row)
        page.addView(hero)
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(metricCard("WATCH TIME", formatDuration(StatsStore.todayWatchSeconds(this)), "Today's Reels time"), LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(metricCard("SESSIONS", StatsStore.todaySessions(this).toString(), "Times opened"), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        page.addView(stats)
        page.addView(section("Daily goal"))
        val goal = StatsStore.dailyGoalMinutes(this)
        val watched = StatsStore.todayWatchSeconds(this)
        val progress = (watched.toFloat() / (goal * 60f)).coerceIn(0f, 1f)
        val goalCard = cardLayout()
        goalCard.addView(label("${formatDuration(watched)} of ${goal}m", white, 18f, true))
        goalCard.addView(progressBar(progress))
        goalCard.addView(label(if (progress >= 1f) "Goal reached" else "${formatDuration((goal * 60L - watched).coerceAtLeast(0))} left to your goal", muted, 12f))
        page.addView(goalCard)
        page.addView(section("This week"))
        val week = StatsStore.weekCounts(this)
        val totalWeek = week.sumOf { it.second }
        val weekCard = cardLayout()
        weekCard.addView(big(totalWeek.toString(), white, 30f))
        weekCard.addView(label("Reels in the last 7 days", muted, 12f))
        week.forEach { (day, count) ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(7), 0, dp(7)) }
            line.addView(label(day.uppercase(Locale.getDefault()), muted, 10f), LinearLayout.LayoutParams(dp(42), -2))
            line.addView(progressBar(if (week.maxOfOrNull { it.second } == 0) 0f else count.toFloat() / week.maxOf { it.second }), LinearLayout.LayoutParams(0, dp(8), 1f))
            line.addView(label(count.toString(), white, 12f, true).apply { gravity = Gravity.RIGHT }, LinearLayout.LayoutParams(dp(42), -2))
            weekCard.addView(line)
        }
        page.addView(weekCard)
        page.addView(section("Controls"))
        val toggle = actionButton(if (tracking) "Pause tracking" else "Start tracking", yellow) {
            StatsStore.setTrackingEnabled(this, !tracking)
            renderTab()
        }
        page.addView(toggle)
        val access = actionButton("Accessibility permission", card) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        page.addView(access)
        page.addView(label("Time tracks while the Reels viewer is detected. It may briefly overcount during screen transitions.", muted, 10f).apply { setPadding(dp(3), dp(12), dp(3), 0) })
    }

    private fun insightsPage() {
        header("INSIGHTS", "Understand your viewing habits.")
        val watch = StatsStore.weekWatchSeconds(this)
        val count = StatsStore.weekCounts(this).sumOf { it.second }
        val sessions = StatsStore.todaySessions(this)
        page.addView(metricCard("LAST 7 DAYS · WATCH TIME", formatDuration(watch), "Time in the Reels viewer"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(metricCard("REELS THIS WEEK", count.toString(), "Across 7 days"), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(metricCard("SESSIONS TODAY", sessions.toString(), "Separate visits"), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
        page.addView(row)
        page.addView(section("Daily breakdown"))
        val values = StatsStore.weekCounts(this)
        val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        val box = cardLayout()
        values.forEach { (day, amount) ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
            line.addView(label(day, muted, 12f), LinearLayout.LayoutParams(dp(48), -2))
            line.addView(progressBar(amount.toFloat() / max), LinearLayout.LayoutParams(0, dp(9), 1f))
            line.addView(label(amount.toString(), white, 12f, true).apply { gravity = Gravity.RIGHT }, LinearLayout.LayoutParams(dp(42), -2))
            box.addView(line)
        }
        page.addView(box)
        page.addView(label("Average time per Reel", muted, 12f).apply { setPadding(0, dp(20), 0, dp(5)) })
        val avg = if (count == 0) 0L else watch / count
        page.addView(big(formatDuration(avg), yellow, 28f))
        page.addView(label("Calculated from tracked viewer time divided by Reel count. It is an estimate, not per-video playback telemetry.", muted, 11f))
    }

    private fun historyPage() {
        header("HISTORY", "Your last 14 days.")
        StatsStore.history(this).forEach { (day, count, seconds) ->
            val item = cardLayout()
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(label(day, white, 14f, true), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(label(count.toString() + " reels", yellow, 13f, true))
            item.addView(line)
            item.addView(label("Watch time  ·  ${formatDuration(seconds)}", muted, 12f))
            page.addView(item)
        }
    }

    private fun settingsPage() {
        header("SETTINGS", "Make ReelCounter work your way.")
        page.addView(section("Daily watch-time goal"))
        val goal = StatsStore.dailyGoalMinutes(this)
        page.addView(actionButton("Change goal · ${goal} minutes", card) {
            val options = arrayOf("15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes", "120 minutes", "180 minutes")
            val values = intArrayOf(15, 30, 45, 60, 90, 120, 180)
            AlertDialog.Builder(this).setTitle("Daily goal").setItems(options) { _, which ->
                StatsStore.setDailyGoalMinutes(this, values[which])
                renderTab()
            }.show()
        })
        page.addView(section("Data"))
        page.addView(actionButton("Export statistics (CSV)", card) { exportCsv() })
        page.addView(actionButton("Reset all statistics", card) {
            AlertDialog.Builder(this).setTitle("Reset all statistics?")
                .setMessage("This permanently clears Reel counts, watch time and session history on this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset") { _, _ ->
                    StatsStore.resetCounts(this)
                    ReelAccessibilityService.updateNotification(this, 0)
                    renderTab()
                    Toast.makeText(this, "Statistics reset", Toast.LENGTH_SHORT).show()
                }.show()
        })
        page.addView(section("Permissions & diagnostics"))
        page.addView(actionButton("Open Accessibility settings", card) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        page.addView(actionButton("Refresh diagnostic log", card) { renderTab() })
        val logs = cardLayout()
        logs.addView(label(StatsStore.diagnostics(this), muted, 10f))
        page.addView(logs)
        page.addView(label("Privacy: statistics are stored locally on this device. ReelCounter does not read video content or identify what you watch.", muted, 11f).apply { setPadding(0, dp(16), 0, 0) })
    }

    private fun exportCsv() {
        val rows = mutableListOf("Date,Reels,Watch seconds")
        StatsStore.history(this).forEach { (day, count, seconds) ->
            rows.add("\"$day\",$count,$seconds")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "ReelCounter statistics")
            putExtra(Intent.EXTRA_TEXT, rows.joinToString("\n"))
        }
        startActivity(Intent.createChooser(send, "Export statistics"))
    }

    private fun header(title: String, subtitle: String) {
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val mark = ImageView(this).apply {
            setImageResource(R.drawable.ic_reel_logo)
            contentDescription = "ReelCounter logo"
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(9) }
        }
        brand.addView(mark)
        brand.addView(label(title, yellow, 11f, true))
        page.addView(brand)
        page.addView(big(subtitle, white, if (resources.configuration.screenWidthDp < 380) 22f else 25f).apply {
            setPadding(0, dp(10), 0, dp(17))
        })
    }

    private fun section(text: String): TextView =
        label(text, white, 16f, true).apply { setPadding(0, dp(15), 0, dp(7)) }

    private fun cardLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(card, 20)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun metricCard(title: String, value: String, foot: String): LinearLayout {
        val box = cardLayout()
        box.addView(label(title, muted, 9f, true))
        box.addView(big(value, white, if (resources.configuration.screenWidthDp < 380) 21f else 23f).apply { setPadding(0, dp(7), 0, dp(4)) })
        box.addView(label(foot, muted, 9f))
        return box
    }

    private fun label(value: String, color: Int, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun big(value: String, color: Int, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun dot(color: Int) = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
    }

    private fun progressBar(progress: Float): View = View(this).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(yellow)
        }
        alpha = if (progress <= 0f) 0.12f else 0.35f + progress * 0.65f
        scaleX = progress.coerceAtLeast(0.025f)
        pivotX = 0f
        layoutParams = LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(12); bottomMargin = dp(12) }
    }

    private fun actionButton(text: String, color: Int, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(if (color == yellow) bg else white)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(17), 0, dp(17), 0)
        background = rounded(color, 15)
        minHeight = dp(52)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> String.format(Locale.getDefault(), "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.getDefault(), "%dm %02ds", m, s)
            else -> "${s}s"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}