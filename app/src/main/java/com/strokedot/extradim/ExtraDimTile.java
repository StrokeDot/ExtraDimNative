package com.strokedot.extradim;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class ExtraDimTile extends TileService {
    @Override public void onStartListening() { super.onStartListening(); DimTransition.recover(this); update(); }
    @Override public void onClick() {
        super.onClick();
        if (!DimController.hasPermission(this)) {
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                startActivityAndCollapse(pi);
            } else { startActivityAndCollapse(i); }
            return;
        }
        DimController.toggle(this);
        update();
    }
    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(!DimController.hasPermission(this) ? Tile.STATE_UNAVAILABLE :
                (DimController.active(this) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE));
        t.updateTile();
    }
}
