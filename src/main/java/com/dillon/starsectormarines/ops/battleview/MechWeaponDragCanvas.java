package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.util.function.Supplier;

/** Pointer-transparent carried hardware, above the whole fitting document. */
public final class MechWeaponDragCanvas implements CanvasProducer {
    private final Supplier<LayeredMechAssets> assets;
    private MechWeaponComponent component;
    private float documentX;
    private float documentY;

    public MechWeaponDragCanvas(Supplier<LayeredMechAssets> assets) {
        this.assets = assets;
        HardwareArt.preload();
    }

    public void show(MechWeaponComponent component, float documentX, float documentY) {
        this.component = component;
        this.documentX = documentX;
        this.documentY = documentY;
    }

    public void clear() { component = null; }

    @Override public void draw(CanvasContext c) {
        if (component == null) return;
        float x = c.metrics().toCanvasX(documentX) + 18;
        float y = c.metrics().toCanvasY(documentY) + 18;
        // The ghost is the piece itself: its coloured bricks and its painted icon.
        HardpointBricks.Layout bricks = HardpointBricks.fit(x, y, 72, 90,
                component.footprintColumns, component.footprintRows, 2f);
        HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(),
                HardpointBricks.tone(component.hardpointType));
        HardwareArt.draw(c, component, bricks.left(), bricks.top(), bricks.width(), bricks.height(), 255);
    }
}
