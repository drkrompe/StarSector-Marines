package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.setup.BattleSetup;

import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

/**
 * Mission map size band. Ordinary {@link BattleSetup} factories select it
 * from the mission's {@link OperationTier}: bigger operations get more terrain
 * to fight across, while risk varies pressure within that space.
 *
 * <p>Cell counts grow roughly 1× / 1.6× / 2.5× to keep the perf budget on a
 * known curve — the largest tier is still well below the conquest preview
 * size (240×160) used in the map-gen tests.
 */
public enum MapScale {

    /** First-contract scale. Tight map, quick op — recommended 20+ marines. */
    SMALL (112,  64),
    /** Established/veteran scale. Mid-sized urban fight — recommended 50+ marines. */
    MEDIUM(144, 80),
    /** Reinforced/full-strength scale. Full city push — recommended 100+ marines. */
    LARGE (240, 160);

    public final int width;
    public final int height;

    MapScale(int width, int height) {
        this.width  = width;
        this.height = height;
    }

    /**
     * Map size for an operation's tier. Scale is the tier's business — a
     * beginner's job is tight and quick whether or not it is dangerous, and a
     * late-game siege needs room whether or not it is a surprise.
     */
    public static MapScale forTier(OperationTier tier) {
        if (tier == null) return MEDIUM;
        switch (tier) {
            case FIRST_CONTRACT: return SMALL;
            case REINFORCED:
            case FULL_STRENGTH:  return LARGE;
            case ESTABLISHED:
            case VETERAN:
            default:             return MEDIUM;
        }
    }

    /**
     * Compatibility bridge for paths with no mission behind them. Prefer
     * {@link #forTier}; see {@code OperationTier.forRisk}.
     */
    public static MapScale forRisk(RiskLevel risk) {
        return forTier(OperationTier.forRisk(risk));
    }
}
