package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmRunnerContractTest {

    @Test
    void runnerAndPressureRoleAreAppendOnlyEnumTails() {
        UnitType[] types = UnitType.values();
        UnitRole[] roles = UnitRole.values();

        assertEquals(UnitType.SWARM_RUNNER, types[types.length - 1]);
        assertEquals(UnitRole.SWARM_PRESSURE, roles[roles.length - 1]);
        assertNotEquals(UnitType.ALIEN, UnitType.SWARM_RUNNER);
    }

    @Test
    void runnerUsesHeldSheetsAndCloseContactStats() {
        UnitType runner = UnitType.SWARM_RUNNER;

        assertEquals("graphics/battle/alien.png", runner.spritePath);
        assertEquals("graphics/battle/alien-dead.png", runner.deadSpritePath);
        assertTrue(runner.combatant);
        assertTrue(runner.moveSpeed > UnitType.ALIEN.moveSpeed);
        assertTrue(runner.attackRange <= 1.5f);
        assertTrue(runner.attackDamage > 0f);
        assertTrue(runner.drawnAsSheet());
    }
}
