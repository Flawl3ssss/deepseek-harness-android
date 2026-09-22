package ai.deepseek.dsh

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/** Quick Settings тайл (ARCH-MOD-05, R-11): статус + тап перезапускает сервис. */
@RequiresApi(Build.VERSION_CODES.N)
class DshTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = if (DshService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        if (!DshService.running && Paths.isInstalled(this)) {
            val i = Intent(this, DshService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } else {
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
