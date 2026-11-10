package dev.aayush.os;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings Tile to toggle the Top Camera-Cutout Dynamic Now Bar.
 */
public class NowBarTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        syncTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent i = new Intent(this, NowBarOverlayService.class);
        if (NowBarOverlayService.isRunning) {
            stopService(i);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        }
        syncTile();
    }

    private void syncTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean active = NowBarOverlayService.isRunning;
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("Now Bar #81");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(active ? "Camera Island ON" : "Off");
        }
        tile.updateTile();
    }
}
