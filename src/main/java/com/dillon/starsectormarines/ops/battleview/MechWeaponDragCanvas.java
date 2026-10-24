package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.Supplier;

/**
 * Pointer-transparent carried hardware, above the whole fitting document: a weapon or an
 * internal (loaders are internals), drawn as its brick piece at the doll's brick size.
 */
public final class MechWeaponDragCanvas implements CanvasProducer {
    private final Supplier<LayeredMechAssets> assets;
    private MechWeaponComponent component;
    private MechInternal internal;
    private float documentX;
    private float documentY;

    public MechWeaponDragCanvas(Supplier<LayeredMechAssets> assets) {
        this.assets = assets;
        HardwareArt.preload();
    }

    public void show(MechWeaponComponent component, float documentX, float documentY) {
        carry(component, null, documentX, documentY);
    }

    public void show(MechInternal internal, float documentX, float documentY) {
        carry(null, internal, documentX, documentY);
    }

    private void carry(MechWeaponComponent component, MechInternal internal, float documentX, float documentY) {
        this.component = component;
        this.internal = internal;
        this.documentX = documentX;
        this.documentY = documentY;
    }

    public void clear() {
        component = null;
        internal = null;
    }

    @Override public void draw(CanvasContext c) {
        if (component == null && internal == null) return;
        float x = c.metrics().toCanvasX(documentX) + 18;
        float y = c.metrics().toCanvasY(documentY) + 18;
        int columns = component != null ? component.footprintColumns
                : internal != null ? internal.footprintColumns : 2;
        int rows = component != null ? component.footprintRows
                : internal != null ? internal.footprintRows : 1;
        Color tone = component != null ? HardpointBricks.tone(component.hardpointType)
                : internal != null ? HardpointBricks.tone(internal.role) : HardpointBricks.UTILITY;
        // The ghost is the piece itself, at the size it will have in its socket.
        HardpointBricks.Layout bricks = HardpointBricks.at(x, y, columns, rows,
                MechSocketConsole.BRICK_HEIGHT, MechSocketConsole.BRICK_GAP);
        HardpointBricks.drawFilled(c, bricks, 0, 0, bricks.columns(), bricks.rows(), tone);
        if (component != null) {
            HardwareArt.draw(c, component, bricks.left(), bricks.top(), bricks.width(), bricks.height(), 255);
        } else {
            HardwareArt.draw(c, internal, bricks.left(), bricks.top(), bricks.width(), bricks.height(), 255);
        }
    }
}
