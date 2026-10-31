package com.javarhol.murren

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** All settings and engine state, persisted so alarms work even after the process dies. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("murren", Context.MODE_PRIVATE)

    // Settings
    var enabled by Pref(sp, "enabled", false)
    var testMode by Pref(sp, "testMode", false)
    var quietStart by Pref(sp, "quietStart", 22 * 60) // minute of day
    var quietEnd by Pref(sp, "quietEnd", 7 * 60)
    var graceSec by Pref(sp, "graceSec", 20)
    var seeded by Pref(sp, "seeded", false)
    var allowlist: Set<String>
        get() = sp.getStringSet("allowlist", emptySet())!!.toSet()
        set(value) {
            sp.edit().putStringSet("allowlist", value).commit()
        }

    // Engine state
    var dayKey by Pref(sp, "dayKey", "")
    var rung by Pref(sp, "rung", 0)
    var stretchStart by Pref(sp, "stretchStart", 0L)
    var sessionOpen by Pref(sp, "sessionOpen", false)
    var sessionStart by Pref(sp, "sessionStart", 0L)
    var pendingReward by Pref(sp, "pendingReward", false)
    var overnightTracked by Pref(sp, "overnightTracked", false)
    var overnightClean by Pref(sp, "overnightClean", false)
    private var quickTimes by Pref(sp, "quickTimes", "")

    // Pace clock: smoothed fraction of time off the phone, as of paceAt
    var pace by Pref(sp, "pace", 1f)
    var paceAt by Pref(sp, "paceAt", 0L)
    var paceOn by Pref(sp, "paceOn", false)

    fun recentQuickChecks(now: Long): Int =
        quickTimes.split(',').mapNotNull { it.toLongOrNull() }.count { now - it < HOUR }

    fun addQuickCheck(now: Long) {
        val kept = quickTimes.split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < HOUR }
        quickTimes = (kept + now).joinToString(",")
    }

    fun day(key: String) = Day(sp, key)

    /** Day keys with recorded stats, newest first. */
    fun dayKeys(): List<String> =
        sp.all.keys.filter { it.startsWith("d.") }.map { it.split('.')[1] }.distinct().sortedDescending()

    companion object {
        private const val HOUR = 60 * 60 * 1000L
    }
}

/** Stats for one day. A day runs from the end of quiet hours to the next end of quiet hours. */
class Day(sp: SharedPreferences, val key: String) {
    var dayStart by Pref(sp, "d.$key.dayStart", 0L)
    var dayEnd by Pref(sp, "d.$key.dayEnd", 0L)
    var lastCompulsiveEnd by Pref(sp, "d.$key.lastCompEnd", 0L)
    var compulsiveMs by Pref(sp, "d.$key.compulsiveMs", 0L)
    var longestMs by Pref(sp, "d.$key.longestMs", 0L)
    var unlocks by Pref(sp, "d.$key.unlocks", 0)
    var people by Pref(sp, "d.$key.people", 0)
    var quick by Pref(sp, "d.$key.quick", 0)
    var compulsive by Pref(sp, "d.$key.compulsive", 0)
    var buzzes by Pref(sp, "d.$key.buzzes", 0)
    var overnight by Pref(sp, "d.$key.overnight", OVERNIGHT_UNKNOWN)

    companion object {
        const val OVERNIGHT_UNKNOWN = 0
        const val OVERNIGHT_CLEAN = 1
        const val OVERNIGHT_BROKEN = 2
    }
}

private class Pref<T>(
    private val sp: SharedPreferences,
    private val key: String,
    private val default: T,
) : ReadWriteProperty<Any?, T> {
    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = when (default) {
        is Boolean -> sp.getBoolean(key, default)
        is Int -> sp.getInt(key, default)
        is Long -> sp.getLong(key, default)
        is Float -> sp.getFloat(key, default)
        is String -> sp.getString(key, default)
        else -> error("Unsupported pref type for $key")
    } as T

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val edit = sp.edit()
        when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is String -> edit.putString(key, value)
            else -> error("Unsupported pref type for $key")
        }
        // commit, not apply: alarm receivers may be killed right after writing.
        edit.commit()
    }
}
