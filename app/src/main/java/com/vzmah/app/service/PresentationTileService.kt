package com.vzmah.app.service

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vzmah.app.MainActivity

class PresentationTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(Tile.STATE_ACTIVE)
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startActivityAndCollapse(pendingIntent)
    }

    private fun updateTileState(state: Int) {
        val tile = qsTile ?: return
        tile.state = state
        tile.updateTile()
    }
}
