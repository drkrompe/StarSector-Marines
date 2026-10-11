package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipScale;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Draws a warship hanging over the battle: the game's own hull art at the scale
 * every hull is drawn at, and each fitted weapon's own turret sprite on the slot
 * the hull's data places it, pointing where the barrel points.
 *
 * <p>Nothing here is authored by the mod. The hull and turret art are the
 * install's, named by the hull spec and the weapon spec, so a player who has
 * flown the ship sees the ship they know.
 *
 * <p>Reads the simulation's warships and nothing else; what they fire, and what
 * it does, is the simulation's.
 */
public final class WarshipRenderSystem implements RenderSystem {

    private final BattleSprites sprites;

    public WarshipRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.SHUTTLES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        for (Warship ship : ctx.sim.warships().ships()) {
            ShuttleSpriteCache hull = sprites.spriteByPath(ship.spritePath);
            if (hull == null) continue;
            float rad = (float) Math.toRadians(ship.facingDegrees);
            float c = (float) Math.cos(rad);
            float s = (float) Math.sin(rad);
            // The sprite's centre sits at the authored pivot from the centre of
            // gravity, in the hull frame; the hull rotates about its CoG.
            float hx = ship.x + (ship.pivotX * c - ship.pivotY * s);
            float hy = ship.y + (ship.pivotX * s + ship.pivotY * c);
            float pxH = ship.lengthCells * cellPx;
            out.addSprite(RenderLayer.SHUTTLES, hull.sprite,
                    cam.cellToScreenX(hx), cam.cellToScreenY(hy),
                    pxH * hull.aspect, pxH, ship.facingDegrees,
                    1f, 1f, 1f, ctx.alphaMult);
            for (Warship.Mount m : ship.mounts()) {
                drawMount(out, cam, cellPx, ctx.alphaMult, ship, m);
            }
        }
    }

    private void drawMount(DrawList out, BattleCamera cam, float cellPx, float alpha,
                           Warship ship, Warship.Mount m) {
        String path = m.slot.hardpoint() ? m.weapon.hardpointSprite() : m.weapon.turretSprite();
        ShuttleSpriteCache art = sprites.spriteByPath(path);
        if (art == null) return;
        float w = WarshipScale.cells(art.pxW) * cellPx;
        float h = WarshipScale.cells(art.pxH) * cellPx;
        // A hardpoint is fixed to the hull; a turret points where its barrel does.
        float heading = m.slot.hardpoint() ? ship.facingDegrees + m.slot.angleDegrees() : m.headingDegrees;
        out.addSprite(RenderLayer.SHUTTLES, art.sprite,
                cam.cellToScreenX(ship.mountX(m)), cam.cellToScreenY(ship.mountY(m)),
                w, h, heading, 1f, 1f, 1f, alpha);
    }
}
