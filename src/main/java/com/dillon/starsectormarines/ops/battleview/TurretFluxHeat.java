package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.turret.FluxShield;

/**
 * How a shielded hardpoint's flux device is drawn: one capacitor and one radiator bolted to its plinth's west wall
 * ({@code StructureLook.fluxDevicePath}), painted twice, cold and at full charge, with the hot layer laid over the cold
 * one at this heat, a ring of charge lamps round the capacitor, and the flux haze off the radiator
 * ({@code ground-turret-tiers.md}). Presentation only, read off the simulation's {@link FluxShield}; its flicker is
 * hashed from the turret and the simulation tick, so the same battle draws the same device every time and nothing
 * here draws from a random stream the battle uses.
 *
 * <ul>
 *   <li><b>Charging.</b> The radiator heats with the capacitor's flux, the lamps light one by one as it fills, and
 *   the radiator gives off a haze that thickens toward full.</li>
 *   <li><b>Strain.</b> Past {@link #STRAIN} of capacity the glow and the last lamp stutter (and {@code TurretFx}
 *   throws sparks off the capacitor): the shield is near breaking.</li>
 *   <li><b>Venting.</b> At a break the capacitor dumps into the radiator, which vents a plume through the overload
 *   while it cools and its lamps go out; the shield can rise again when it is cold.</li>
 * </ul>
 *
 * <p>The layout constants are the device's, in cells of its 3x3 sprite from the top-left corner, mirrored from
 * {@code make_flux_device_guide.py}; move them together.
 */
final class TurretFluxHeat {

    /** Share of capacity past which the hardware shows strain. */
    static final float STRAIN = 0.75f;

    /** The capacitor's centre, and the radius of its lamp ring. */
    static final float CAPACITOR_X = 0.36f;
    static final float CAPACITOR_Y = 1.14f;
    static final float LAMP_RING = 0.215f;
    /** How many charge lamps ring the capacitor, the first at the top and going clockwise. */
    static final int LAMPS = 6;
    /** The radiator's centre, where the haze rises from, and half its width. */
    static final float RADIATOR_X = 0.36f;
    static final float RADIATOR_Y = 1.81f;
    static final float RADIATOR_HALF_WIDTH = 0.22f;

    private TurretFluxHeat() {}

    /**
     * How bright charge lamp {@code k} is for turret {@code id} at {@code tick}, {@code [0, 1]}: the lamps fill in
     * order with the heat, the one filling now coming up as it goes, and under strain the highest lit lamp stutters.
     */
    static float lamp(long id, FluxShield shield, long tick, int k) {
        float filled = heat(shield) * LAMPS;
        float lit = Math.max(0f, Math.min(1f, filled - k));
        if (lit > 0f && k == Math.min(LAMPS, (int) Math.ceil(filled)) - 1) {
            lit *= 1f - 0.8f * strain(shield) * hash01(id, tick, 2);
        }
        return lit;
    }

    /** How hot the radiators are, {@code [0, 1]}: the flux held, or the overload still to vent. */
    static float heat(FluxShield shield) {
        return shield.overloaded() ? shield.overloadShare() : shield.fluxShare();
    }

    /** How strained the hardware looks, {@code [0, 1]}: zero below {@link #STRAIN}, one at full; none while venting. */
    static float strain(FluxShield shield) {
        if (shield.overloaded()) return 0f;
        return Math.max(0f, (shield.fluxShare() - STRAIN) / (1f - STRAIN));
    }

    /** The hot layer's opacity for turret {@code id} at {@code tick}: its heat, stuttering under strain. */
    static float hotAlpha(long id, FluxShield shield, long tick) {
        float stutter = 1f - 0.35f * strain(shield) * hash01(id, tick, 1);
        return heat(shield) * stutter;
    }

    /** A value in {@code [0, 1)} hashed from a turret, a tick and a salt: the same inputs, the same value. */
    static float hash01(long id, long tick, int salt) {
        long h = id * 0x9E3779B97F4A7C15L + tick * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
