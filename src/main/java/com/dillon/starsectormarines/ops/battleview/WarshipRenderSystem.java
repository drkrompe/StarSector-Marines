package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.warship.VanillaBeamLook;
import com.dillon.starsectormarines.battle.warship.VanillaWeapon;
import com.dillon.starsectormarines.battle.warship.VanillaWeaponLooks;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipPointDefence;
import com.dillon.starsectormarines.battle.warship.WarshipScale;
import com.dillon.starsectormarines.battle.warship.WarshipShell;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;

/**
 * Draws a warship hanging over the battle: the game's own hull art at the scale
 * every hull is drawn at, and each fitted weapon's own turret sprite on the slot
 * the hull's data places it, pointing where the barrel points.
 *
 * <p>Nothing here is authored by the mod. The hull and turret art are the
 * install's, named by the hull spec and the weapon spec, so a player who has
 * flown the ship sees the ship they know.
 *
 * <p>Rounds in flight are drawn from the weapon's own projectile art: a shell or
 * missile as its sprite, an energy bolt as a streak in the projectile's fringe
 * and core colours, a missile with a glowing tail behind it. A muzzle flash
 * lights the barrel for the instant after it fires, in the weapon's own flash
 * colour. They share the hull's layer so they read as coming from it, and so the
 * headless review frames, which draw that layer, show them.
 *
 * <p>Reads the simulation's warships and nothing else; what they fire, and what
 * it does, is the simulation's.
 */
public final class WarshipRenderSystem implements RenderSystem {

    /** Presentation scale on a vanilla round's size: at ground scale the game's own proportions read as boulders. */
    private static final float ROUND_VISUAL_SCALE = 0.6f;
    private static final float MUZZLE_FLASH_SECONDS = 0.15f;
    private static final int TAIL_PUFFS = 8;
    private static final float TAIL_SPACING_CELLS = 0.6f;
    private static final String GLOW = "graphics/fx/particlealpha64linear.png";
    private static final Color DEFAULT_FLASH = new Color(255, 225, 165);
    private static final Color PD_FRINGE = new Color(255, 200, 90);
    private static final Color MISSILE_FLAME = new Color(255, 140, 40);
    private static final Color MISSILE_SMOKE = new Color(150, 150, 150);

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
            drawHull(out, cam, cellPx, ctx.alphaMult, ship);
            for (Warship.Mount m : ship.mounts()) {
                drawMount(out, cam, cellPx, ctx.alphaMult, ship, m);
                drawMuzzleFlash(out, cam, cellPx, ctx.alphaMult, ship, m);
            }
        }
        for (WarshipShell shell : ctx.sim.warships().shells()) {
            drawShell(out, cam, cellPx, ctx.alphaMult, shell);
        }
        for (WarshipPointDefence.Mark mark : ctx.sim.warships().pointDefence().marks()) {
            drawEngagement(out, cam, cellPx, ctx.alphaMult, mark);
        }
    }

    private void drawHull(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship) {
        ShuttleSpriteCache hull = sprites.spriteByPath(ship.spritePath);
        if (hull == null) return;
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
                1f, 1f, 1f, alpha);
    }

    private void drawMount(DrawList out, BattleCamera cam, float cellPx, float alpha,
                           Warship ship, Warship.Mount m) {
        String path = m.slot.hardpoint() ? m.weapon.hardpointSprite() : m.weapon.turretSprite();
        ShuttleSpriteCache art = sprites.spriteByPath(path);
        if (art == null) return;
        float w = WarshipScale.cells(art.pxW) * cellPx;
        float h = WarshipScale.cells(art.pxH) * cellPx;
        out.addSprite(RenderLayer.SHUTTLES, art.sprite,
                cam.cellToScreenX(ship.mountX(m)), cam.cellToScreenY(ship.mountY(m)),
                w, h, heading(ship, m), 1f, 1f, 1f, alpha);
    }

    /** A hardpoint is fixed to the hull; a turret points where its barrel does. */
    private static float heading(Warship ship, Warship.Mount m) {
        return m.slot.hardpoint() ? ship.facingDegrees + m.slot.angleDegrees() : m.headingDegrees;
    }

    private void drawMuzzleFlash(DrawList out, BattleCamera cam, float cellPx, float alpha,
                                 Warship ship, Warship.Mount m) {
        if (m.secondsSinceFired >= MUZZLE_FLASH_SECONDS) return;
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        float fade = 1f - m.secondsSinceFired / MUZZLE_FLASH_SECONDS;
        Color c = m.weapon.muzzleColor() != null ? m.weapon.muzzleColor() : DEFAULT_FLASH;
        double rad = Math.toRadians(heading(ship, m));
        // The flash sits a barrel's length out from the pivot, along the heading.
        float barrel = 1.2f;
        float fx = ship.mountX(m) - (float) Math.sin(rad) * barrel;
        float fy = ship.mountY(m) + (float) Math.cos(rad) * barrel;
        float size = Math.max(1.5f, WarshipScale.cells(m.weapon.muzzleLengthPx()) * 2f) * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite,
                cam.cellToScreenX(fx), cam.cellToScreenY(fy), size, size, 0f,
                c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.9f * fade * alpha);
    }

    /**
     * A point-defence tracer in the weapon's own colours, and, where the round
     * died, a flash of its blast colour at the kill; a miss is the tracer alone.
     */
    private void drawEngagement(DrawList out, BattleCamera cam, float cellPx, float alpha,
                                WarshipPointDefence.Mark mark) {
        VanillaWeapon w = mark.weapon();
        VanillaBeamLook beam = w.beam() ? VanillaWeaponLooks.beam(w.id()) : null;
        Color fringe = beam != null && beam.fringe() != null ? beam.fringe()
                : w.fringe() != null ? w.fringe() : PD_FRINGE;
        Color core = beam != null && beam.core() != null ? beam.core()
                : w.core() != null ? w.core() : Color.WHITE;
        float f = mark.freshness();
        float x0 = cam.cellToScreenX(mark.fromX());
        float y0 = cam.cellToScreenY(mark.fromY());
        float x1 = cam.cellToScreenX(mark.toX());
        float y1 = cam.cellToScreenY(mark.toY());
        float width = Math.max(2f, 0.18f * cellPx);
        out.addLine(RenderLayer.SHUTTLES, x0, y0, x1, y1, width,
                fringe.getRed() / 255f, fringe.getGreen() / 255f, fringe.getBlue() / 255f, 0.7f * f * alpha);
        out.addLine(RenderLayer.SHUTTLES, x0, y0, x1, y1, width * 0.4f,
                core.getRed() / 255f, core.getGreen() / 255f, core.getBlue() / 255f, f * alpha);
        if (!mark.stopped()) return;
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        Color c = w.explosionColor() != null ? w.explosionColor() : DEFAULT_FLASH;
        float size = Math.max(1.5f, 2.2f * (1.2f - f)) * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite, x1, y1, size, size, 0f,
                c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.9f * f * alpha);
    }

    private void drawShell(DrawList out, BattleCamera cam, float cellPx, float alpha, WarshipShell shell) {
        VanillaWeapon w = shell.weapon;
        float t = shell.progress();
        float x = shell.fromX + (shell.toX - shell.fromX) * t;
        float y = shell.fromY + (shell.toY - shell.fromY) * t;
        float heading = AirBody.facingToward(shell.toX - shell.fromX, shell.toY - shell.fromY);
        ShuttleSpriteCache art = sprites.spriteByPath(w.projectileSprite());
        if (art != null) {
            float wPx = WarshipScale.cells(art.pxW) * cellPx * ROUND_VISUAL_SCALE;
            float hPx = WarshipScale.cells(art.pxH) * cellPx * ROUND_VISUAL_SCALE;
            if (w.missile()) drawMissileTail(out, cam, cellPx, alpha, shell, x, y);
            out.addSprite(RenderLayer.SHUTTLES, art.sprite,
                    cam.cellToScreenX(x), cam.cellToScreenY(y), wPx, hPx, heading,
                    1f, 1f, 1f, alpha);
            return;
        }
        // No sprite: an energy or kinetic bolt, a streak in the projectile's own colours.
        float lengthCells = Math.max(0.6f, WarshipScale.cells(w.projectileLengthPx())) * ROUND_VISUAL_SCALE;
        float widthPx = Math.max(2f, WarshipScale.cells(w.projectileWidthPx()) * cellPx * ROUND_VISUAL_SCALE);
        double rad = Math.toRadians(heading);
        float tx = x + (float) Math.sin(rad) * lengthCells;
        float ty = y - (float) Math.cos(rad) * lengthCells;
        Color fringe = w.fringe() != null ? w.fringe() : Color.ORANGE;
        Color core = w.core() != null ? w.core() : Color.WHITE;
        out.addLine(RenderLayer.SHUTTLES, cam.cellToScreenX(x), cam.cellToScreenY(y),
                cam.cellToScreenX(tx), cam.cellToScreenY(ty), widthPx,
                fringe.getRed() / 255f, fringe.getGreen() / 255f, fringe.getBlue() / 255f, 0.8f * alpha);
        out.addLine(RenderLayer.SHUTTLES, cam.cellToScreenX(x), cam.cellToScreenY(y),
                cam.cellToScreenX(tx), cam.cellToScreenY(ty), widthPx * 0.4f,
                core.getRed() / 255f, core.getGreen() / 255f, core.getBlue() / 255f, alpha);
    }

    /** A missile's burning tail and the smoke it leaves, fading back along the line it has flown. */
    private void drawMissileTail(DrawList out, BattleCamera cam, float cellPx, float alpha,
                                 WarshipShell shell, float x, float y) {
        ShuttleSpriteCache glow = sprites.spriteByPath(GLOW);
        if (glow == null) return;
        float dx = shell.toX - shell.fromX;
        float dy = shell.toY - shell.fromY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float ux = dx / len;
        float uy = dy / len;
        float flown = shell.progress() * len;
        for (int i = 0; i < TAIL_PUFFS; i++) {
            float back = i * TAIL_SPACING_CELLS;
            if (back > flown) break;
            float fade = 1f - (float) i / TAIL_PUFFS;
            float size = (0.9f + 0.2f * i) * cellPx * ROUND_VISUAL_SCALE * 2f;
            Color c = i < 2 ? MISSILE_FLAME : MISSILE_SMOKE;
            out.addAdditiveSprite(RenderLayer.SHUTTLES, glow.sprite,
                    cam.cellToScreenX(x - ux * back), cam.cellToScreenY(y - uy * back),
                    size, size, 0f,
                    c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.55f * fade * alpha);
        }
    }
}
