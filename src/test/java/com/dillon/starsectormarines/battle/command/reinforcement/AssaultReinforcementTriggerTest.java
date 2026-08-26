package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssaultReinforcementTriggerTest {

    @Test
    void assaultUsesOwnForceDepletionWithoutOccupancyDrivenAlarms() {
        try (BattleSimulation sim = BattleSetup.createPlaceholder(607898L)) {
            ReinforcementService service = sim.getReinforcementService();

            assertTrue(service.triggers().stream()
                    .anyMatch(GarrisonDepletedTrigger.class::isInstance));
            assertFalse(service.triggers().stream()
                    .anyMatch(ObjectiveLostTrigger.class::isInstance));
            assertFalse(service.triggers().stream()
                    .anyMatch(FrontLineReinforcementTrigger.class::isInstance));
        }
    }
}
