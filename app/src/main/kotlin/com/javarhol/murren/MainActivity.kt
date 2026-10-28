package com.javarhol.murren

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Setup, today's summary and settings. Deliberately shows no countdown to the
 * next buzz: a timer would be one more reason to unlock.
 */
class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        p = Prefs(this)
        AllowlistActivity.seedDefaults(this, p)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply {
            addView(content)
            clipToPadding = false
        }
        applySystemBarPadding(scroll)
        setContentView(scroll)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        text("Mürren", 30f, bold = true)
        text("A quiet buzz for every stretch you leave your phone alone.", 15f, dim = true)

        renderSetup()

        section("")
        if (p.enabled) {
            text("Watching quietly. Put the phone away.", 16f)
            button("Pause") {
                p.enabled = false
                WatchService.stop(this)
                render()
            }
        } else {
            text("Paused.", 16f)
            button("Start") {
                p.enabled = true
                WatchService.start(this)
                render()
            }
        }

        val now = Clock.now()
        val today = Clock.dayKey(p, now)
        section("Today")
        text(Summary.text(p, today, now), 16f)

        val earlier = p.dayKeys().filter { it < today }.take(6)
        if (earlier.isNotEmpty()) {
            section("Earlier")
            earlier.forEach { key ->
                text(key, 13f, dim = true)
                text(Summary.text(p, key, now), 14f)
            }
        }

        renderSettings()
    }

    private fun renderSetup() {
        val steps = buildList {
            if (!Judge.hasUsageAccess(this@MainActivity)) {
                add(
                    Triple(
                        "Usage access",
                        "So texting family and allowed apps don't count against you.",
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                    ),
                )
            }
            if (!Alarms.canBePrecise(this@MainActivity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(
                    Triple(
                        "Precise timing",
                        "So buzzes arrive on time instead of minutes late.",
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                    ),
                )
            }
            if (!getSystemService(PowerManager::class.java)!!.isIgnoringBatteryOptimizations(packageName)) {
                @Suppress("BatteryLife")
                add(
                    Triple(
                        "Keep running",
                        "So Android doesn't put Mürren to sleep.",
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName"),
                        ),
                    ),
                )
            }
        }
        if (steps.isEmpty()) return
        section("Setup")
        steps.forEach { (title, why, intent) ->
            text(title, 16f, bold = true)
            text(why, 14f, dim = true)
            button("Allow") { startActivity(intent) }
        }
    }

    private fun renderSettings() {
        section("Settings")

        text("Quiet hours", 16f, bold = true)
        text("No buzzes. Get through them phone-free for a morning buzz.", 14f, dim = true)
        button("${Clock.formatMinute(p.quietStart)} – ${Clock.formatMinute(p.quietEnd)}") {
            pickTime(p.quietStart) { start ->
                pickTime(p.quietEnd) { end ->
                    p.quietStart = start
                    p.quietEnd = end
                    Engine.reschedule(this)
                    render()
                }
            }
        }

        text("Quick check grace", 16f, bold = true)
        text("Unlocks shorter than this are forgiven, up to ${Engine.QUICK_CHECKS_PER_HOUR} an hour.", 14f, dim = true)
        row(
            smallButton("−") { p.graceSec = (p.graceSec - 5).coerceAtLeast(5); render() },
            label("${p.graceSec} s"),
            smallButton("+") { p.graceSec = (p.graceSec + 5).coerceAtMost(60); render() },
        )

        text("Allowed apps", 16f, bold = true)
        text("Calls, texts and work chat that never count against you.", 14f, dim = true)
        button("${p.allowlist.size} apps") { startActivity(Intent(this, AllowlistActivity::class.java)) }

        text("Feel the buzzes", 16f, bold = true)
        row(
            smallButton("Reward") { Buzz.reward(this) },
            smallButton("Morning") { Buzz.morning(this) },
        )

        content.addView(
            Switch(this).apply {
                text = "Test mode (minutes become seconds)"
                textSize = 15f
                isChecked = p.testMode
                setPadding(0, dp(16), 0, 0)
                setOnCheckedChangeListener { _, checked ->
                    p.testMode = checked
                    Engine.reschedule(this@MainActivity)
                }
            },
        )
    }

    private fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun section(title: String) {
        text(title, 13f, bold = true, dim = true).setPadding(0, dp(28), 0, dp(4))
    }

    private fun text(s: String, size: Float, bold: Boolean = false, dim: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (dim) alpha = 0.65f
            setPadding(0, dp(4), 0, 0)
            content.addView(this)
        }

    private fun button(label: String, onClick: () -> Unit) {
        content.addView(
            Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { onClick() }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun label(s: String) = TextView(this).apply {
        text = s
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(16), 0, dp(16), 0)
    }

    private fun row(vararg views: View) {
        content.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                views.forEach { addView(it) }
            },
        )
    }
}
