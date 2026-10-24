package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.turret.FluxShield;

/**
 * How hot a shielded hardpoint's flux hardware is drawn: the capacitor banks and radiators bolted to its plinth
 * ({@code StructureLook.fluxBankPath}) are painted twice, cold and at full charge, and the hot layer is laid over the
 * cold one at this heat ({@code ground-turret-tiers.md}). Presentation only, read off the simulation's
 * {@link FluxShield}; its flicker is hashed from the turret and the simulation tick, so the same battle draws the same
 * hardware every time and nothing here draws from a random stream the battle uses.
 *
 * <ul>
 *   <li><b>Charging.</b> The radiators heat with the capacitors' flux.</li>
 *   <li><b>Strain.</b> Past {@link #STRAIN} of capacity the glow stutters (and {@code TurretFx} throws sparks off
 *   the banks): the shield is near breaking.</li>
 *   <li><b>Venting.</b> At a break the capacitors dump into the radiators, which are at their hottest and then cool
 *   through the overload; the shield can rise again when they are cold.</li>
 * </ul>
 */
final class TurretFluxHeat {

    /** Share of capacity past which the hardware shows strain. */
    static final float STRAIN = 0.75f;

    private TurretFluxHeat() {}

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
