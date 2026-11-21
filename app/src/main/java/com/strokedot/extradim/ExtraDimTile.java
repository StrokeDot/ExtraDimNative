package com.strokedot.extradim;

public final class ExtraDimTile extends DisplayToggleTile {
    @Override boolean effectActive() {
        return DimController.active(this);
    }

    @Override void toggleEffect() {
        DimController.toggle(this);
    }
}
