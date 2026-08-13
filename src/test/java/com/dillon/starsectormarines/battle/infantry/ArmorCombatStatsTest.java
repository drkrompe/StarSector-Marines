package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorCombatStatsTest {

    @Test
    void armorSeedsHealthMobilityEvasionAndDamageReduction() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(12, 12));
        MarineArmorPattern armor = MarineArmorPattern.CHARCOAL;
        EntitySpec spec = new EntitySpec("armored", Faction.MARINE, UnitType.MARINE, 5, 5)
                .armor(armor.bonusHp, armor.damageReduction,
                        armor.moveSpeedMult, armor.incomingAccuracyMult);
        long marine = sim.spawn(spec);

        assertEquals(30f, sim.world().maxHp(marine), 1e-6f);
        assertEquals(30f, sim.world().hp(marine), 1e-6f);
        assertEquals(UnitType.MARINE.moveSpeed * 0.96f,
                sim.world().moveSpeed(marine), 1e-6f);
        assertEquals(0.88f, sim.world().damageTakenMult(marine), 1e-6f);
        assertEquals(0.96f, sim.world().incomingAccuracyMult(marine), 1e-6f);

        sim.applyDamage(marine, 10f, 1f, 0f);
        assertEquals(21.2f, sim.world().hp(marine), 1e-6f);
    }

    @Test
    void scoutAndHeavyArmorHaveDistinctRoles() {
        MarineArmorPattern scout = MarineArmorPattern.BLUE_SCOUT;
        MarineArmorPattern heavy = MarineArmorPattern.RED_ELITE;

        assertTrue(scout.moveSpeedMult > 1f);
        assertTrue(scout.incomingAccuracyMult < heavy.incomingAccuracyMult);
        assertTrue(heavy.damageReduction > scout.damageReduction);
        assertTrue(heavy.bonusHp > scout.bonusHp);
        assertTrue(heavy.moveSpeedMult < scout.moveSpeedMult);
    }
}
