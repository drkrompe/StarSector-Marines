package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmPressureBehaviorPolicyTest {

    @Test
    void sharedGoalFieldsStartAtConfiguredCrossover() {
        int crossover =
                SwarmPressureBehavior.configuredMinimumSharedGoalUnits();

        if (crossover > 0) {
            assertFalse(SwarmPressureBehavior.usesSharedTargetFields(
                    crossover - 1));
        }
        assertTrue(SwarmPressureBehavior.usesSharedTargetFields(crossover));
    }
}
