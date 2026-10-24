package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;
import java.util.function.Predicate;

/**
 * A high-tech emplacement's light flux shield as one {@link ShieldDomeDraw}: the same shader bubble a planetary dome
 * is drawn with, a gun across (`ground-turret-tiers.md`). Presentation only. Rounds pass it and do their full damage;
 * what it shows is read off the frame's shots and the structure's health, so a live screen and a review frame draw
 * the same bubble with nothing fed to them.
 *
 * <ul>
 *   <li><b>Hits.</b> A hostile round in flight that ends inside the bubble lights it where it would cross the rim,
 *   on the side it came from: a line tracer from the moment it fires, a travelling round as it arrives.</li>
 *   <li><b>Down.</b> Below {@link #DOWN_BELOW} of its structure the generator is failing and the bubble is drawn
 *   collapsed, its rim a broken arc.</li>
 *   <li><b>Field.</b> The dome's hexagonal field is sized in map cells for a dome tens of cells across; a gun's bubble
 *   is drawn with {@link #HEX_DENSITY} times its radius as the field's scale, so its cells are a few to the bubble.</li>
 * </ul>
 *
 * <p>Pure arithmetic, no GL.
 */
final class TurretShieldField {

    /** Below this share of its structure a turret's shield is drawn down. */
    static final float DOWN_BELOW = 0.25f;
    /** How many times smaller than a dome's the bubble's hex field is drawn. */
    static final float HEX_DENSITY = 4f;
    /** A travelling round lights the bubble in its last this-many seconds of flight. */
    static final float ARRIVAL_SECONDS = 0.12f;
    /** How long a hit's flash takes to fade, in seconds. */
    static final float FLASH_SECONDS = 0.3f;
    /** How far past the bubble's edge a round may end and still be read as striking it, in bubble radii. */
    static final float CATCH = 1.15f;
    /** Intensity an impact is drawn at: a gun's bubble takes small arms, not ship rounds, so all hits draw alike. */
    static final float INTENSITY = 0.75f;

    private TurretShieldField() {}

    /**
     * Fills {@code draw} for a shield of {@code radiusCells} round ({@code x}, {@code y}) held by {@code holder}, at
     * {@code healthShare} of its structure, struck by whichever of {@code shots} it would catch. {@code travels}
     * says, per shot, whether it is a travelling body (true) or a line tracer.
     */
    static void describe(ShieldDomeDraw draw, float x, float y, float radiusCells, Faction holder, float healthShare,
                         List<ShotEvent> shots, Predicate<ShotEvent> travels,
                         BattleCamera camera, float cellPx, float alpha) {
        draw.sx = camera.cellToScreenX(x);
        draw.sy = camera.cellToScreenY(y);
        draw.radiusPx = radiusCells * cellPx;
        draw.radiusCells = radiusCells * HEX_DENSITY;
        float[] tint = ShieldDomeRenderSystem.tintFor(holder);
        draw.tint[0] = tint[0];
        draw.tint[1] = tint[1];
        draw.tint[2] = tint[2];
        draw.level = 0.15f;
        draw.overloaded = healthShare < DOWN_BELOW;
        draw.recovery = draw.overloaded ? 0.6f : 0f;
        draw.dropFlash = 0f;
        draw.alpha = alpha;
        float flash = 0f;
        int count = 0;
        if (!draw.overloaded) {
            for (int i = shots.size() - 1; i >= 0 && count < ShieldDomeDraw.MAX_IMPACTS; i--) {
                ShotEvent shot = shots.get(i);
                if (shot.shooterFaction == null || !holder.hostileTo(shot.shooterFaction)) continue;
                float ex = shot.toX - x;
                float ey = shot.toY - y;
                float reach = radiusCells * CATCH;
                if (ex * ex + ey * ey > reach * reach) continue;
                float age = age(shot, travels.test(shot));
                if (age < 0f) continue;
                // Where the round crosses the rim: on the line from the bubble's centre toward where it came from.
                float dx = shot.fromX - x;
                float dy = shot.fromY - y;
                float len = (float) Math.hypot(dx, dy);
                if (len < 1e-4f) continue;
                float rimX = x + dx / len * radiusCells * 0.92f;
                float rimY = y + dy / len * radiusCells * 0.92f;
                int at = count * 4;
                draw.impacts[at] = (camera.cellToScreenX(rimX) - draw.sx) / draw.radiusPx;
                draw.impacts[at + 1] = (camera.cellToScreenY(rimY) - draw.sy) / draw.radiusPx;
                draw.impacts[at + 2] = age;
                draw.impacts[at + 3] = INTENSITY;
                flash = Math.max(flash, 1f - age / FLASH_SECONDS);
                count++;
            }
        }
        draw.impactCount = count;
        draw.hitFlash = Math.max(0f, flash);
    }

    /**
     * Seconds since {@code shot} struck the bubble, or a negative number while it has not yet: a line tracer strikes
     * when it fires, a travelling round in its last {@link #ARRIVAL_SECONDS}.
     */
    static float age(ShotEvent shot, boolean travels) {
        if (!travels) return shot.lifetimeMax - shot.lifetime;
        return ARRIVAL_SECONDS - shot.lifetime;
    }
}
