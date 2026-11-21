package com.strokedot.extradim;

public final class BlueLightTile extends DisplayToggleTile {
    @Override boolean effectActive() {
        return BlueLightController.active(this);
    }

    @Override void toggleEffect() {
        BlueLightController.toggle(this);
    }
}
