package com.strokedot.extradim;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

abstract class DisplayToggleTile extends TileService {
    abstract boolean effectActive();
    abstract void toggleEffect();

    @Override public void onStartListening() {
        super.onStartListening();
        DimTransition.recover(this);
        updateTileState();
    }

    @Override public void onClick() {
        super.onClick();
        if (!DimController.hasPermission(this)) {
            Intent open = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                startActivityAndCollapse(pending);
            } else {
                startActivityAndCollapse(open);
            }
            return;
        }
        toggleEffect();
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        int state = Tile.STATE_UNAVAILABLE;
        if (DimController.hasPermission(this)) {
            state = effectActive() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        }
        tile.setState(state);
        tile.updateTile();
    }
}
