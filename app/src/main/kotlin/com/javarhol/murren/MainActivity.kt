package com.javarhol.murren

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
 * A watch face for the day: pace in a draining ring, the pacer bar against
 * your usual day, splits like laps, then the finish summary and settings.
 * Deliberately shows no countdown to the next split buzz: a timer would be one
 * more reason to unlock.
 */
class MainActivity : Activity() {
    companion object {
        // Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, Android 16+.
        private const val ACTION_PROMOTION_SETTINGS = "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"
    }

    private lateinit var p: Prefs
    private lateinit var pal: Palette
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        p = Prefs(this)
        pal = Style.palette(this)
        AllowlistActivity.seedDefaults(this, p)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(28))
        }
        val scroll = ScrollView(this).apply {
            addView(content)
            clipToPadding = false
            setBackgroundColor(pal.bg)
        }
        window.decorView.setBackgroundColor(pal.bg)
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
        if (p.enabled) {
            if (!WatchService.running) {
                WatchService.start(this)
            } else if (p.hiddenDay.isNotEmpty()) {
                // Opening the app brings back a pace you swiped away.
                p.hiddenDay = ""
                Notifier.showPace(this, force = true)
            }
        }
        render()
    }

    private fun render() {
        content.removeAllViews()
        val now = Clock.now()
        val day = p.day(p.dayKey)

        // Top bar: name on the left, the day's state on the right.
        val state = when {
            !p.enabled -> pill("Paused", dot = false)
            Clock.inQuiet(p, now) -> pill("☾", dot = false)
            else -> pill("Live", dot = true)
        }
        row(caps("MÜRREN", 13f), View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }, state)
        renderSetup()

        when {
            !p.enabled -> {
                big("Paused")
                button("Start") {
                    p.enabled = true
                    WatchService.start(this)
                    render()
                }
            }
            Clock.inQuiet(p, now) -> big("☾  ${Clock.formatMinute(p.quietEnd)}")
            else -> renderRun(now)
        }

        val today = Clock.dayKey(p, now)
        section(if (p.day(today).dayEnd > 0) "FINISH" else "TODAY")
        Summary.rows(p, today, now).forEach { (glyph, line) -> glyphRow(glyph, line) }

        val earlier = p.dayKeys().filter { it < today }.take(6)
        if (earlier.isNotEmpty()) {
            section("EARLIER")
            earlier.forEach { key ->
                caps(key, 10f).apply { setPadding(0, dp(10), 0, 0); content.addView(this) }
                Summary.rows(p, key, now).forEach { (glyph, line) -> glyphRow(glyph, line) }
            }
        }

        if (p.enabled) {
            section("")
            button("Pause") {
                p.enabled = false
                WatchService.stop(this)
                render()
            }
        }
        renderSettings()
    }

    /** The live view: hero ring, pacer bar, stats, race, splits. */
    private fun renderRun(now: Long) {
        val day = p.day(p.dayKey)

        val r = Pace.reading(p, now)
        content.addView(
            HeroRing(
                this,
                charge = Pace.fraction(p, now).toFloat(),
                number = r.minutesPerHour.toString(),
                label = "MIN ${r.word.uppercase()} PHONE / HR",
                pal = pal,
            ),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) },
        )

        // Pacer bar against your usual day.
        val ghost = History.ghost(p, p.dayKey)
        Notifier.minutesUnderUsual(p, now)?.let { under ->
            content.addView(
                PacerBar(this, under, pal),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(10) },
            )
            caps("VS ${ghost?.label?.removePrefix("your ")?.uppercase()}", 10f).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                content.addView(this)
            }
        }

        // Stats, like average pace / split on a watch.
        val awake = (now - day.dayStart).coerceAtLeast(60_000)
        val onSoFar = History.todaySoFar(p, now)
        val avgOn = (onSoFar / 60_000.0 / (awake / 3_600_000.0)).toInt()
        val avg = if (p.showTimeOn) avgOn else 60 - avgOn
        val splits = History.splits(day)
        val current = now - day.lastCompulsiveEnd
        val splitCount = splits.size + if (current >= 5 * 60_000L) 1 else 0
        divider()
        row(
            stat("AVG TODAY", "$avg", "/hr"),
            stat("SPLIT", Clock.formatClock(current), ""),
            stat("SPLITS", "$splitCount", ""),
        )
        divider()

        if (ghost != null) {
            section("RACE")
            val todayHourly = History.hourly(day)
            content.addView(
                GhostChart(
                    this,
                    from = History.dayMinute(day.dayStart),
                    to = History.dayMinute(Clock.nextAt(p.quietStart, now)).let {
                        if (it <= History.dayMinute(day.dayStart)) 1439 else it
                    },
                    now = History.dayMinute(now),
                    today = { m -> if (m >= History.dayMinute(now)) onSoFar else History.cumulative(todayHourly, m) },
                    ghost = { m -> ghost.at(m) },
                    pal = pal,
                ),
            )
        }

        if (splitCount > 0) section("SPLITS")
        var lap = splits.size + if (current >= 5 * 60_000L) 1 else 0
        if (current >= 5 * 60_000L) {
            lapRow(lap--, "${Clock.formatMinute(Clock.minuteOfDay(day.lastCompulsiveEnd))} – now", current, p.splitRungs, live = true)
        }
        splits.reversed().forEach { s ->
            lapRow(
                lap--,
                "${Clock.formatMinute(Clock.minuteOfDay(s.start))} – ${Clock.formatMinute(Clock.minuteOfDay(s.end))}",
                s.length, s.rungs, live = false,
            )
        }
    }

    // --- Building blocks ---

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun caps(s: String, size: Float) = TextView(this).apply {
        text = s
        textSize = size
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.22f
        setTextColor(pal.dim)
    }

    private fun pill(s: String, dot: Boolean) = TextView(this).apply {
        text = if (dot) "●  $s" else s
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(pal.ink)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(pal.track) }
        if (dot) {
            val span = android.text.SpannableString(text)
            span.setSpan(android.text.style.ForegroundColorSpan(pal.glow), 0, 1, 0)
            text = span
        }
    }

    private fun big(s: String) = TextView(this).apply {
        text = s
        textSize = 44f
        typeface = Style.numerals(context)
        setTextColor(pal.ink)
        setPadding(0, dp(28), 0, dp(4))
        content.addView(this)
    }

    private fun divider() = content.addView(
        View(this).apply { setBackgroundColor(pal.line) },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(14) },
    )

    private fun stat(label: String, value: String, unit: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setPadding(0, dp(12), 0, dp(2))
        addView(caps(label, 10f))
        addView(
            TextView(context).apply {
                text = android.text.SpannableStringBuilder().apply {
                    append(value)
                    if (unit.isNotEmpty()) {
                        val s = length
                        append(unit)
                        setSpan(android.text.style.RelativeSizeSpan(0.5f), s, length, 0)
                        setSpan(android.text.style.ForegroundColorSpan(pal.dim), s, length, 0)
                    }
                }
                textSize = 32f
                typeface = Style.numerals(context)
                setTextColor(pal.ink)
            },
        )
    }

    private fun lapRow(n: Int, time: String, length: Long, rungs: Int, live: Boolean) {
        fun cell(s: String, size: Float, color: Int, face: Typeface?, w: Float, align: Int) = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            face?.let { typeface = it }
            gravity = align or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
        }
        row(
            cell("$n", 17f, pal.dim, Style.numerals(this), 0.12f, Gravity.START),
            cell(time, 14f, pal.ink, null, 0.48f, Gravity.START),
            cell(Clock.formatClock(length), 22f, if (live) pal.glow else pal.ink, Style.numerals(this), 0.24f, Gravity.END),
            cell("●".repeat(rungs), 10f, pal.glow, null, 0.16f, Gravity.END),
        )
        content.addView(
            View(this).apply { setBackgroundColor(pal.line) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)),
        )
    }

    private fun glyphRow(glyph: String, line: String) = row(
        TextView(this).apply {
            text = glyph
            textSize = 16f
            setTextColor(pal.dusk)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.WRAP_CONTENT)
        },
        TextView(this).apply {
            text = line
            textSize = 15f
            setTextColor(pal.ink)
        },
    )

    private fun section(title: String) {
        caps(title, 10.5f).apply {
            setPadding(0, dp(24), 0, dp(8))
            content.addView(this)
        }
    }

    private fun text(s: String, size: Float, bold: Boolean = false, dim: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(if (dim) pal.dim else pal.ink)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, 0)
            setLineSpacing(0f, 1.15f)
            content.addView(this)
        }

    private fun button(label: String, onClick: () -> Unit) {
        content.addView(
            Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { onClick() }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) },
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
        setTextColor(pal.ink)
        gravity = Gravity.CENTER
        setPadding(dp(16), 0, dp(16), 0)
    }

    private fun row(vararg views: View) {
        content.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
                views.forEach { addView(it) }
            },
        )
    }

    private fun appNotificationSettings() =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

    private fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
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
        section("SETUP")
        steps.forEach { (title, why, intent) ->
            text(title, 16f, bold = true)
            text(why, 14f, dim = true)
            button("Allow") { startActivity(intent) }
        }
    }

    private fun renderSettings() {
        section("SETTINGS")

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
}
