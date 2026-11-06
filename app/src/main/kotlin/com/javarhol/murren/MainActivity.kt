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
 * A live workout view of the day (pace, splits, the race against your usual
 * day), then the finish summary and settings. Deliberately shows no countdown
 * to the next split buzz: a timer would be one more reason to unlock.
 */
class MainActivity : Activity() {
    companion object {
        // Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, Android 16+.
        private const val ACTION_PROMOTION_SETTINGS = "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"
    }

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
        if (p.enabled) WatchService.start(this) // no-op if already running
        render()
    }

    private fun render() {
        content.removeAllViews()
        val now = Clock.now()
        text("Zack Zack", 15f, bold = true, dim = true)
        renderSetup()

        if (!p.enabled) {
            text("Paused", 34f, bold = true)
            button("Start Zack Zack") {
                p.enabled = true
                WatchService.start(this)
                render()
            }
        } else if (Clock.inQuiet(p, now)) {
            text("Quiet hours", 34f, bold = true)
            text("Goodnight. Your day starts again at ${Clock.formatMinute(p.quietEnd)}.", 16f, dim = true)
        } else {
            renderRun(now)
        }

        val today = Clock.dayKey(p, now)
        section(if (p.day(today).dayEnd > 0) "Finish" else "Today so far")
        text(Summary.text(p, today, now), 16f)

        val earlier = p.dayKeys().filter { it < today }.take(6)
        if (earlier.isNotEmpty()) {
            section("Earlier")
            earlier.forEach { key ->
                text(key, 13f, dim = true)
                text(Summary.text(p, key, now), 14f)
            }
        }

        if (p.enabled) {
            section("")
            button("Pause Zack Zack") {
                p.enabled = false
                WatchService.stop(this)
                render()
            }
        }
        renderSettings()
    }

    /** The live workout view: pace, verdict bar, stats, the race against your usual day, splits. */
    private fun renderRun(now: Long) {
        val day = p.day(p.dayKey)
        if (day.started == Day.NOT_STARTED) {
            button("Start today") {
                Engine.startDay(this)
                render()
            }
            text("Your pace goes live in the status bar until quiet hours.", 14f, dim = true)
        }

        // Hero: the pace, big, like a running watch.
        val r = Pace.reading(p, now)
        val unit = TextView(this).apply {
            text = "/hr\nmin ${r.word} phone"
            textSize = 15f
            alpha = 0.65f
            setPadding(dp(8), 0, 0, 0)
        }
        val hero = TextView(this).apply {
            text = r.minutesPerHour.toString()
            textSize = 76f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
        }
        row(hero, unit)

        val ghost = History.ghost(p, p.dayKey)
        val usualOn = History.usualPace(p, ghost, now)
        val onPace = 60 - Pace.minutesPerHour(p, now)
        val usualShown = usualOn?.let { if (p.showTimeOn) it else 60 - it }
        usualOn?.let {
            text(
                when {
                    onPace < it * 0.75 -> "Under your usual pace ($usualShown/hr)"
                    onPace > it * 1.25 -> "Over your usual pace ($usualShown/hr)"
                    else -> "Around your usual pace ($usualShown/hr)"
                },
                15f,
            )
        }
        content.addView(PaceBar(this, r.minutesPerHour, usualShown))

        // Stats row, like average / current pace on a watch.
        val awake = (now - day.dayStart).coerceAtLeast(60_000)
        val onSoFar = History.todaySoFar(p, now)
        val avgOn = (onSoFar / 60_000.0 / (awake / 3_600_000.0)).toInt()
        val avg = if (p.showTimeOn) avgOn else 60 - avgOn
        val vsUsual = Notifier.ghostLine(p, now)?.substringBefore(" your")?.substringBefore(" with") ?: "–"
        row(
            stat("AVG TODAY", "$avg/hr"),
            stat("VS USUAL", vsUsual),
            stat("SPLIT", Clock.formatDuration(now - day.lastCompulsiveEnd)),
        )

        // The race against your own usual day.
        section("Race")
        if (ghost != null) {
            content.addView(
                GhostChart(
                    this,
                    from = History.dayMinute(day.dayStart),
                    to = History.dayMinute(Clock.nextAt(p.quietStart, now)).let {
                        if (it <= History.dayMinute(day.dayStart)) 1439 else it
                    },
                    now = History.dayMinute(now),
                    today = { m -> if (m >= History.dayMinute(now)) onSoFar else History.cumulative(History.hourly(day), m) },
                    ghost = { m -> ghost.at(m) },
                ),
            )
            text("━ Today   ┅ ${ghost.label.replaceFirstChar { it.uppercase() }}", 13f, dim = true)
        } else {
            text("Your usual day appears here after two days, to race against.", 14f, dim = true)
        }

        // Splits: phone-free stretches, newest first, like a lap list.
        section("Splits")
        val current = now - day.lastCompulsiveEnd
        if (current >= 5 * 60_000L) {
            splitRow("${Clock.formatMinute(Clock.minuteOfDay(day.lastCompulsiveEnd))} – now", current, p.splitRungs, live = true)
        }
        val splits = History.splits(day).reversed()
        splits.forEach { s ->
            splitRow(
                "${Clock.formatMinute(Clock.minuteOfDay(s.start))} – ${Clock.formatMinute(Clock.minuteOfDay(s.end))}",
                s.length,
                s.rungs,
                live = false,
            )
        }
        if (splits.isEmpty() && current < 5 * 60_000L) text("Put the phone down to start your first split.", 14f, dim = true)

        if (day.started > 0) {
            button("Finish for today") {
                Engine.finishDay(this)
                render()
            }
        }
    }

    private fun stat(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), dp(24), 0)
        addView(TextView(context).apply { text = label; textSize = 11f; alpha = 0.6f; letterSpacing = 0.08f })
        addView(TextView(context).apply { text = value; textSize = 18f; typeface = Typeface.DEFAULT_BOLD })
    }

    private fun splitRow(time: String, length: Long, rungs: Int, live: Boolean) {
        val marks = "●".repeat(rungs)
        row(
            TextView(this).apply {
                text = time
                textSize = 15f
                minWidth = dp(130)
            },
            TextView(this).apply {
                text = Clock.formatDuration(length)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                minWidth = dp(80)
            },
            TextView(this).apply {
                text = if (live) "$marks running" else marks
                textSize = 13f
                alpha = 0.7f
            },
        )
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(
                    Triple(
                        "Notifications",
                        "So your pace shows in the status bar and on the lock screen. They never make a sound.",
                        appNotificationSettings(),
                    ),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && !Notifier.canPromote(this@MainActivity)) {
                add(
                    Triple(
                        "Live updates",
                        "So your pace sits in the status bar as \"54/hr\".",
                        Intent(ACTION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
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
                        "So Android doesn't put Zack Zack to sleep.",
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
        val window = "${Clock.formatMinute(p.quietStart)} – ${Clock.formatMinute(p.quietEnd)}"
        if (p.autoQuiet) {
            val nights = Sleep.nights(p).size
            text(
                if (Sleep.learned(p) != null) {
                    "$window, learned from when you sleep (last $nights nights)."
                } else {
                    "Learning when you sleep. Using $window until ${Sleep.NIGHTS_NEEDED} nights are recorded ($nights so far)."
                },
                15f,
            )
            button("Set manually instead") {
                p.autoQuiet = false
                render()
            }
        } else {
            button(window) {
                pickTime(p.quietStart) { start ->
                    pickTime(p.quietEnd) { end ->
                        p.quietStart = start
                        p.quietEnd = end
                        Engine.reschedule(this)
                        render()
                    }
                }
            }
            button("Learn from my sleep") {
                p.autoQuiet = true
                if (!Clock.inQuiet(p, Clock.now())) Sleep.relearn(p)
                Engine.reschedule(this)
                render()
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
            smallButton("Split") { Buzz.reward(this, preview = true) },
            smallButton("Morning") { Buzz.morning(this, preview = true) },
        )

        text("Pace clock", 16f, bold = true)
        content.addView(
            Switch(this).apply {
                text = "Show time on phone (lower is better)"
                textSize = 15f
                isChecked = p.showTimeOn
                setOnCheckedChangeListener { _, checked ->
                    p.showTimeOn = checked
                    Notifier.showPace(this@MainActivity, force = true)
                    render()
                }
            },
        )
        text(
            if (p.showTimeOn) "Like a running pace: get your number down." else "Showing time off phone: get your number up.",
            14f,
            dim = true,
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

    private fun appNotificationSettings() =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

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
