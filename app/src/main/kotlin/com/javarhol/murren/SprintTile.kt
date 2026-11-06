package com.javarhol.murren

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: tap to start a Zack Zack sprint, tap again to stop it. */
class SprintTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        val p = Prefs(this)
        if (Sprint.active(p, Clock.now())) Sprint.stop(this) else Sprint.start(this, p.sprintMinutes)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val p = Prefs(this)
        val now = Clock.now()
        val running = Sprint.active(p, now)
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Zack Zack"
        tile.icon = Icons.zack()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) "→ ${Clock.formatMinute(Clock.minuteOfDay(p.sprintEnd))}" else "${p.sprintMinutes} min"
        }
        tile.updateTile()
    }
}
