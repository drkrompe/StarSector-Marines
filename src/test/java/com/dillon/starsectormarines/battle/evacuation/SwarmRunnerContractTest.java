package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
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
        assertTrue(runner.drawnAsLayers());
        assertEquals(20f, runner.maxHp, 0.001f);
        assertEquals(15f, UnitType.ALIEN.maxHp, 0.001f);
        assertTrue(runner.maxHp > UnitType.ALIEN.maxHp);
        assertTrue(!runner.drawsLayeredWeapon());
    }

    @Test
    void serviceGradeMarineWeaponsHaveDeliberateSwarmBreakpoints() {
        assertEquals(2, hitsToKill(UnitType.ALIEN, MarineWeapon.PULSE_RIFLE));
        assertEquals(3, hitsToKill(UnitType.ALIEN, MarineWeapon.SMG));
        assertEquals(1, hitsToKill(UnitType.ALIEN, MarineWeapon.DMR));

        assertEquals(3, hitsToKill(UnitType.SWARM_RUNNER, MarineWeapon.PULSE_RIFLE));
        assertEquals(4, hitsToKill(UnitType.SWARM_RUNNER, MarineWeapon.SMG));
        assertEquals(2, hitsToKill(UnitType.SWARM_RUNNER, MarineWeapon.DMR));
    }

    private static int hitsToKill(UnitType target, MarineWeapon weapon) {
        float damage = InfantryCombatStats.damage(
                weapon, EquipmentGrade.SERVICE);
        return (int) Math.ceil(target.maxHp / damage);
    }
}
