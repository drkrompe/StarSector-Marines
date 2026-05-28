package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.setup.BattleSetup;

import com.dillon.starsectormarines.ops.RiskLevel;

/**
 * Mission map size tier. Used by {@link BattleSetup} factories to scale the
 * generated grid with the mission's {@link RiskLevel}: bigger missions get
 * more terrain to fight across, smaller ones stay tight and quick.
 *
 * <p>Cell counts grow roughly 1× / 1.6× / 2.5× to keep the perf budget on a
 * known curve — the largest tier is still well below the conquest preview
 * size (240×160) used in the map-gen tests.
 */
public enum MapScale {

    /** LOW-risk fallback. Tight map, quick op — recommended 20+ marines. */
    SMALL (112,  64),
    /** MEDIUM-risk default. Mid-sized urban fight — recommended 50+ marines. */
    MEDIUM(144, 80),
    /** HIGH-risk siege scale. Full city push — recommended 100+ marines. */
    LARGE (240, 160);

    public final int width;
    public final int height;

    MapScale(int width, int height) {
        this.width  = width;
        this.height = height;
    }

    public static MapScale forRisk(RiskLevel risk) {
        if (risk == null) return MEDIUM;
        switch (risk) {
            case LOW:    return SMALL;
            case HIGH:   return LARGE;
            case MEDIUM:
            default:     return MEDIUM;
        }
    }
}
