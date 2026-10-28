package com.javarhol.murren

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process

/** Decides whether an unlock was for people, a quick check, or the itch. */
object Judge {
    private val SYSTEM_PACKAGES = setOf("android", "com.android.systemui")

    fun judge(ctx: Context, p: Prefs, start: Long, end: Long): Engine.Verdict {
        val apps = if (hasUsageAccess(ctx)) appsUsed(ctx, start, end) else null
        val allowed = p.allowlist + ctx.packageName
        if (!apps.isNullOrEmpty() && apps.all { it in allowed }) return Engine.Verdict.PEOPLE
        // Quick checks are forgiven, but only a few an hour: rapid checking is the habit itself.
        if (end - start <= p.graceSec * 1_000L && p.recentQuickChecks(end) < Engine.QUICK_CHECKS_PER_HOUR) {
            return Engine.Verdict.QUICK
        }
        return Engine.Verdict.COMPULSIVE
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)!!
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Apps brought to the foreground during the session, ignoring the launcher and system UI. */
    private fun appsUsed(ctx: Context, start: Long, end: Long): Set<String> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)!!
        val events = usm.queryEvents(start - 1_000, end + 1_000)
        val event = UsageEvents.Event()
        val used = mutableSetOf<String>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) used += event.packageName
        }
        return used - SYSTEM_PACKAGES - homePackages(ctx)
    }

    @Suppress("DEPRECATION")
    private fun homePackages(ctx: Context): Set<String> =
        ctx.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
            .toSet()
}
