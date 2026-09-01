package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

/**
 * What an operation's tier and risk let a generated place ask of its attacker.
 *
 * <p>The translation from the mission's own vocabulary into the generator's,
 * beside {@link com.dillon.starsectormarines.battle.world.model.MapScale#forTier}
 * for the same reason: {@code battle.world.gen} is campaign-free by contract and
 * cannot name a tier, so the tier is turned into a plain
 * {@link Fortification.Demand} at the boundary and the generator reasons about
 * that. The tier owns the ceiling because scale is the tier's business — a
 * First Contract job is small whether or not it is dangerous — and the risk
 * owns the rung either side of the world's answer because variance is what
 * risk means. See {@code mission-tier-nouns.md} and {@code precincts.md}.
 */
public final class MissionFortification {

    private MissionFortification() {}

    /**
     * The hardest place an operation at this tier may be asked to take, and how
     * far the risk moves the world's own answer toward or away from it.
     *
     * @param tier {@code null} reads as Established, as {@code MapScale} reads it
     * @param risk {@code null} reads as medium: no variance
     */
    public static Fortification.Demand demand(OperationTier tier, RiskLevel risk) {
        return new Fortification.Demand(ceiling(tier), variance(risk));
    }

    private static Fortification.Strength ceiling(OperationTier tier) {
        if (tier == null) return Fortification.Strength.GARRISON;
        return switch (tier) {
            case FIRST_CONTRACT -> Fortification.Strength.PICKET;
            case ESTABLISHED -> Fortification.Strength.GARRISON;
            case VETERAN -> Fortification.Strength.STRONGHOLD;
            case REINFORCED, FULL_STRENGTH -> Fortification.Strength.CITADEL;
        };
    }

    private static int variance(RiskLevel risk) {
        if (risk == null) return 0;
        return switch (risk) {
            case LOW -> -1;
            case MEDIUM -> 0;
            case HIGH -> 1;
        };
    }
}
