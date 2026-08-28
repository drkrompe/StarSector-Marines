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
    void armorSeedsSeparatePoolRatingMobilityAndEvasion() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(12, 12));
        MarineArmorPattern armor = MarineArmorPattern.CHARCOAL;
        EntitySpec spec = new EntitySpec("armored", Faction.MARINE, UnitType.MARINE, 5, 5)
                .armor(armor.armorCapacity, armor.armorRating,
                        armor.moveSpeedMult, armor.incomingAccuracyMult);
        long marine = sim.spawn(spec);

        assertEquals(25f, sim.world().maxHp(marine), 1e-6f);
        assertEquals(25f, sim.world().hp(marine), 1e-6f);
        assertEquals(9f, sim.world().armor(marine), 1e-6f);
        assertEquals(8f, sim.world().armorRating(marine), 1e-6f);
        assertEquals(UnitType.MARINE.moveSpeed * 0.96f,
                sim.world().moveSpeed(marine), 1e-6f);
        assertEquals(1f, sim.world().damageTakenMult(marine), 1e-6f);
        assertEquals(0.96f, sim.world().incomingAccuracyMult(marine), 1e-6f);

        sim.applyDamage(marine, 10f, 5f, 0f);
        assertEquals(25f, sim.world().hp(marine), 1e-6f);
        assertEquals(2.375f, sim.world().armor(marine), 1e-6f);
    }

    @Test
    void scoutAndHeavyArmorHaveDistinctRoles() {
        MarineArmorPattern scout = MarineArmorPattern.BLUE_SCOUT;
        MarineArmorPattern heavy = MarineArmorPattern.RED_ELITE;

        assertTrue(scout.moveSpeedMult > 1f);
        assertTrue(scout.incomingAccuracyMult < heavy.incomingAccuracyMult);
        assertTrue(heavy.armorRating > scout.armorRating);
        assertTrue(heavy.armorCapacity > scout.armorCapacity);
        assertTrue(heavy.moveSpeedMult < scout.moveSpeedMult);
    }

    @Test
    void armorlessProfileKeepsMobilityAndEvasionWithoutArmorCapability() {
        BattleSimulation sim = arena();
        MarineArmorPattern armorless = MarineArmorPattern.ARMORLESS;
        long marine = sim.spawn(new EntitySpec("armorless", Faction.MARINE,
                UnitType.MARINE, 5, 5).armor(armorless.armorCapacity,
                armorless.armorRating, armorless.moveSpeedMult,
                armorless.incomingAccuracyMult));

        assertTrue(!sim.world().hasArmor(marine));
        assertEquals(UnitType.MARINE.moveSpeed * armorless.moveSpeedMult,
                sim.world().moveSpeed(marine), 1e-6f);
        assertEquals(armorless.incomingAccuracyMult,
                sim.world().incomingAccuracyMult(marine), 1e-6f);
    }

    @Test
    void coverReducesDamageBeforeArmorEfficiency() {
        BattleSimulation sim = arena();
        sim.getGrid().setCoverAtFacing(5, 5, NavigationGrid.FACING_W, 2);
        long marine = sim.spawn(new EntitySpec("covered", Faction.MARINE,
                UnitType.MARINE, 5, 5).armor(9f, 8f));

        sim.applyDamage(marine, 10f, 5f, 0f);

        assertEquals(9f - 7f * 0.6625f, sim.world().armor(marine), 1e-6f);
        assertEquals(25f, sim.world().hp(marine), 1e-6f);
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(12, 12));
    }
}
