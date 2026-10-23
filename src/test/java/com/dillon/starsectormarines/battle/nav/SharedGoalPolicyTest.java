package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedGoalPolicyTest {

    @Test
    void sharedGoalFieldsStartAtTheCrossover() {
        int crossover = SharedGoalPolicy.minimumSharedGoalUnits();

        if (crossover > 0) {
            assertFalse(SharedGoalPolicy.usesSharedGoalFields(crossover - 1));
        }
        assertTrue(SharedGoalPolicy.usesSharedGoalFields(crossover));
    }
}
