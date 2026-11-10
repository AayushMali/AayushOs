package dev.aayush.os;

import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/**
 * Quick Settings Tile to keep Pixel 7 screen awake (30 min timeout + Stay On While Plugged In)
 * while coding, and restore original timeout when toggled off.
 */
public class CaffeinateTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        syncTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = getSharedPreferences("aayush_os_prefs", MODE_PRIVATE);
        boolean active = prefs.getBoolean("caffeinate_active", false);

        try {
            if (!active) {
                int currentTimeout = Settings.System.getInt(
                        getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 60000);
                prefs.edit()
                        .putInt("saved_screen_timeout", currentTimeout)
                        .putBoolean("caffeinate_active", true)
                        .apply();
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 1800000); // 30m
                Settings.Global.putInt(getContentResolver(), Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 7);
                Toast.makeText(this, "☕ Caffeinate ON (Screen stays awake)", Toast.LENGTH_SHORT).show();
            } else {
                int savedTimeout = prefs.getInt("saved_screen_timeout", 60000);
                prefs.edit().putBoolean("caffeinate_active", false).apply();
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, savedTimeout);
                Settings.Global.putInt(getContentResolver(), Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
                Toast.makeText(this, "Caffeinate OFF (Timeout restored)", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Permission error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        syncTile();
    }

    private void syncTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean active = getSharedPreferences("aayush_os_prefs", MODE_PRIVATE)
                .getBoolean("caffeinate_active", false);
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("Caffeinate");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(active ? "Awake 30m" : "Normal sleep");
        }
        tile.updateTile();
    }
}
