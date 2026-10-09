package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.Supplier;

/** Pointer-transparent carried hardware, above the whole fitting document. */
public final class MechWeaponDragCanvas implements CanvasProducer {
    private final Supplier<LayeredMechAssets> assets;
    private MechWeaponComponent component;
    private float documentX;
    private float documentY;

    public MechWeaponDragCanvas(Supplier<LayeredMechAssets> assets) { this.assets = assets; }

    public void show(MechWeaponComponent component, float documentX, float documentY) {
        this.component = component;
        this.documentX = documentX;
        this.documentY = documentY;
    }

    public void clear() { component = null; }

    @Override public void draw(CanvasContext c) {
        if (component == null || assets.get() == null) return;
        LayeredSpriteCache sprite = MechEquipmentGridCanvas.spriteFor(assets.get(), component);
        if (sprite == null) return;
        float x = c.metrics().toCanvasX(documentX) + 18;
        float y = c.metrics().toCanvasY(documentY) + 18;
        float scale = Math.min(64f / sprite.pxWidth, 82f / sprite.pxHeight);
        c.fillRect(x - 4, y - 4, 72, 90, new Color(7, 12, 9, 205));
        MechLabDollCanvas.drawCornerBrackets(c, x - 4, y - 4, 72, 90,
                new Color(0xedc273), 1.5f, 11, 4);
        c.sprite(sprite.sourcePath, sprite.sprite, x + 32, y + 41,
                sprite.pxWidth * scale, sprite.pxHeight * scale, 0, Color.WHITE);
    }
}
