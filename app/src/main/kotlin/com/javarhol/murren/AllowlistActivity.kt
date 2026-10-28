package com.javarhol.murren

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import android.widget.ArrayAdapter
import android.widget.ListView

/** Apps that never count against you, like calling and texting family or work chat. */
class AllowlistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Prefs(this)

        @Suppress("DEPRECATION")
        val apps = packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedBy { it.second.lowercase() }

        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = ArrayAdapter(
                this@AllowlistActivity, android.R.layout.simple_list_item_multiple_choice, apps.map { it.second },
            )
            clipToPadding = false
        }
        apps.forEachIndexed { i, app -> list.setItemChecked(i, app.first in p.allowlist) }
        list.setOnItemClickListener { _, _, i, _ ->
            val pkg = apps[i].first
            p.allowlist = if (list.isItemChecked(i)) p.allowlist + pkg else p.allowlist - pkg
        }
        applySystemBarPadding(list)
        setContentView(list)
    }

    companion object {
        /** First run: allow the default phone and SMS apps. */
        fun seedDefaults(ctx: Context, p: Prefs) {
            if (p.seeded) return
            val defaults = listOfNotNull(
                ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
                Telephony.Sms.getDefaultSmsPackage(ctx),
            )
            p.allowlist = p.allowlist + defaults
            p.seeded = true
        }
    }
}
