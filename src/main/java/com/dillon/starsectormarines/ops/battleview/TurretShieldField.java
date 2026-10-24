package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.turret.FluxShield;
import com.dillon.starsectormarines.battle.turret.FluxShieldSpec;
import com.dillon.starsectormarines.battle.turret.ShieldTint;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * A hardpoint's directional flux shield as one {@link ShieldDomeDraw}: the dome shader's bubble cut to the arc the
 * shield covers ({@code ground-turret-tiers.md}). Everything drawn is read off the simulation's {@link FluxShield},
 * so a live screen and a review frame draw the same shield with nothing fed to them.
 *
 * <ul>
 *   <li><b>Raise and lower.</b> The arc grows out from its facing as the shield rises and folds back into it as it
 *   lowers; folded away, nothing is drawn.</li>
 *   <li><b>Strain.</b> The capacitors' fill is the dome's flux level: the field brightens and whitens toward
 *   overload, and near it the arc stutters with the capacitors ({@link TurretFluxCapacitors}).</li>
 *   <li><b>Blocks.</b> Each round the shield stopped lights the rim where it came from.</li>
 *   <li><b>Break.</b> On overload the arc shatters, its cells flickering out in the shield's own pale colour, and
 *   for the rest of the overload a ghost rim re-forms across the arc as the capacitors vent.</li>
 * </ul>
 *
 * <p>Pure arithmetic, no GL.
 */
final class TurretShieldField {

    /** How many times smaller than a dome's the shield's hex field is drawn. */
    static final float HEX_DENSITY = 4f;
    /** How long the shatter flickers after a break, in seconds. */
    static final float SHATTER_SECONDS = 0.8f;
    /**
     * How much stronger than a dome's the shield is drawn: the dome's faint fill is made for a bubble tens of cells
     * across, and at a gun's size it would barely read.
     */
    static final float PRESENCE = 1.6f;
    /** How long a block's flash takes to fade, in seconds. */
    static final float FLASH_SECONDS = 0.3f;

    /** The high-tech field emitter's blue: the pale cyan a friendly dome is drawn in. */
    static final float[] BLUE = {0.55f, 0.86f, 1.00f};
    /** Kept for a low- and mid-tech shield; no style fits one yet. */
    static final float[] ORANGE = {1.00f, 0.62f, 0.30f};
    /** What a blue shield strains and shatters toward: near white, so its break never reads as an orange shield. */
    static final float[] BLUE_STRAIN = {0.88f, 0.96f, 1.00f};
    static final float[] ORANGE_STRAIN = ShieldDomeShading.AMBER;

    private TurretShieldField() {}

    /**
     * Fills {@code draw} for {@code shield}, fitted to a gun at ({@code x}, {@code y}) in {@code tint}. False, with
     * {@code draw} untouched, when there is nothing to draw: the shield folded away and no break still showing.
     */
    static boolean describe(ShieldDomeDraw draw, long id, float x, float y, FluxShield shield, ShieldTint tint,
                            BattleCamera camera, float cellPx, long tick, float alpha) {
        boolean overloaded = shield.overloaded();
        if (!overloaded && shield.extent() <= 0f) return false;
        FluxShieldSpec spec = shield.spec();
        float radiusCells = spec.radiusCells();
        draw.sx = camera.cellToScreenX(x);
        draw.sy = camera.cellToScreenY(y);
        draw.radiusPx = radiusCells * cellPx;
        draw.radiusCells = radiusCells * HEX_DENSITY;
        float[] base = tint == ShieldTint.ORANGE ? ORANGE : BLUE;
        float[] strain = tint == ShieldTint.ORANGE ? ORANGE_STRAIN : BLUE_STRAIN;
        System.arraycopy(base, 0, draw.tint, 0, 3);
        System.arraycopy(strain, 0, draw.collapse, 0, 3);
        draw.level = shield.fluxShare();
        draw.overloaded = overloaded;
        draw.recovery = overloaded ? shield.overloadShare() : 0f;
        draw.dropFlash = shatter(shield.sinceBreak());
        // Near breaking the arc stutters with its capacitors, on the same beat as their flicker.
        float stutter = TurretFluxCapacitors.strain(shield);
        draw.alpha = alpha * PRESENCE * (1f - 0.4f * stutter * TurretFluxCapacitors.hash01(id, tick, 1));

        // The arc, in the dome's local frame: a broken shield keeps its full width so its break fills the arc it held.
        float halfArc = overloaded ? 0.5f * spec.arcDegrees() : shield.halfArcDegrees();
        float facing = (float) Math.toRadians(shield.facingDegrees());
        float dirX = camera.cellToScreenX(x + (float) Math.cos(facing)) - draw.sx;
        float dirY = camera.cellToScreenY(y + (float) Math.sin(facing)) - draw.sy;
        float len = (float) Math.hypot(dirX, dirY);
        draw.arcDirX = len > 0f ? dirX / len : 1f;
        draw.arcDirY = len > 0f ? dirY / len : 0f;
        draw.arcCos = halfArc >= 180f ? -2f : (float) Math.cos(Math.toRadians(halfArc));

        float flash = 0f;
        int count = 0;
        if (!overloaded) {
            for (int i = shield.hitCount() - 1; i >= 0 && count < ShieldDomeDraw.MAX_IMPACTS; i--) {
                float bearing = (float) Math.toRadians(shield.hitBearing(i));
                float rimX = x + (float) Math.cos(bearing) * radiusCells * 0.92f;
                float rimY = y + (float) Math.sin(bearing) * radiusCells * 0.92f;
                float age = shield.hitAge(i);
                int at = count * 4;
                draw.impacts[at] = (camera.cellToScreenX(rimX) - draw.sx) / draw.radiusPx;
                draw.impacts[at + 1] = (camera.cellToScreenY(rimY) - draw.sy) / draw.radiusPx;
                draw.impacts[at + 2] = age;
                draw.impacts[at + 3] = shield.hitWeight(i);
                flash = Math.max(flash, 1f - age / FLASH_SECONDS);
                count++;
            }
        }
        draw.impactCount = count;
        draw.hitFlash = Math.max(0f, flash);
        return true;
    }

    /**
     * The shatter's strength {@code sinceBreak} seconds after a break: fading over {@link #SHATTER_SECONDS} and
     * stuttering as it goes, so the shield flickers out rather than dimming.
     */
    static float shatter(float sinceBreak) {
        if (!(sinceBreak < SHATTER_SECONDS)) return 0f;
        float fade = 1f - sinceBreak / SHATTER_SECONDS;
        int beat = (int) (sinceBreak * 22f);
        float stutter = ((beat * 7 + 3) % 5) < 2 ? 0.3f : 1f;
        return fade * stutter;
    }
}
