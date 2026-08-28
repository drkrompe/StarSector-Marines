package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedGoalPolicyTest {

    @Test
    void sharedGoalFieldsStartAtConfiguredCrossover() {
        int crossover = SharedGoalPolicy.configuredMinimumSharedGoalUnits();

        if (crossover > 0) {
            assertFalse(SharedGoalPolicy.usesSharedGoalFields(crossover - 1));
        }
        assertTrue(SharedGoalPolicy.usesSharedGoalFields(crossover));
    }
}
