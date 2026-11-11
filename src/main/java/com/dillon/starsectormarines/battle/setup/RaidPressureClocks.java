package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.reinforcement.RaidPressureTrigger;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementRequest.Strength;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;

/**
 * How hard each security presses a raid over time ({@code raid-demolish-the-works.md}, slice 8): the harder the market,
 * the sooner its garrison columns come, the larger they start, and the larger the wave a wrecked machine brings.
 * Armour joins a fortified market's columns from the third, a guarded one's from the fifth, and never an open one's.
 * UNTUNED; the owner tunes these by playing.
 */
final class RaidPressureClocks {
    private RaidPressureClocks() {}

    static RaidPressureTrigger.Clock of(RaidSiteTemplate.Security security) {
        return switch (security) {
            case OPEN -> new RaidPressureTrigger.Clock(240, 150, Strength.SMALL, Strength.SMALL, -1);
            case GUARDED -> new RaidPressureTrigger.Clock(180, 90, Strength.MEDIUM, Strength.SMALL, 4);
            case FORTIFIED -> new RaidPressureTrigger.Clock(150, 60, Strength.LARGE, Strength.MEDIUM, 2);
        };
    }
}
