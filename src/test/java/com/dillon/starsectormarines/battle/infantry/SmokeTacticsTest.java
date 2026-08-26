package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeTacticsTest {

    @Test
    void exposedAdvanceReservesOneCarrierAndWaitsForOpaqueSmoke() {
        BattleSimulation sim = openArena(30, 12);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long carrier = sim.spawn(new EntitySpec("smoke", Faction.MARINE,
                UnitType.MARINE, 5, 5)
                .squad(squadId)
                .specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                        SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID).startingAmmo()));
        long threat = sim.spawn(new EntitySpec("threat", Faction.DEFENDER,
                UnitType.MARINE, 15, 5));
        Squad squad = sim.getSquad(squadId);
        squad.centroidX = 5.5f;
        squad.centroidY = 5.5f;

        assertTrue(SmokeTactics.holdForAdvanceSmoke(squad, threat, 20, 5, sim));
        assertEquals(carrier, squad.smokeCarrierId);
        assertTrue(SmokeTactics.holdForAdvanceSmoke(squad, threat, 20, 5, sim),
                "the squad keeps waiting on the existing reservation");

        float duration = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID).smokeGrenadeSpec().throwDuration();
        sim.world().setSecondaryActionTimer(carrier, duration * 0.5f);
        assertTrue(InfantryUnitPrep.tickAimAndShortCircuit(carrier, sim));
        assertEquals(1, sim.world().secondaryAmmo(carrier));
        assertEquals(1, sim.smokeFields().throwsInFlight().size());

        sim.smokeFields().tick(
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID).smokeGrenadeSpec().flightSeconds());
        assertEquals(1, sim.smokeFields().activeFields().size());
        assertFalse(SmokeTactics.holdForAdvanceSmoke(squad, threat, 20, 5, sim),
                "an active screen releases the squad instead of scheduling a duplicate");
        assertEquals(0L, squad.smokeCarrierId);
        assertEquals(1, sim.world().secondaryAmmo(carrier));
    }

    @Test
    void smokeIsNeverTreatedAsAnOpportunityWeapon() {
        BattleSimulation sim = openArena(30, 12);
        long carrier = sim.spawn(new EntitySpec("smoke", Faction.MARINE,
                UnitType.MARINE, 5, 5)
                .specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                        SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID).startingAmmo()));
        sim.spawn(new EntitySpec("hard-target", Faction.DEFENDER,
                UnitType.HEAVY_MECH, 12, 5));

        assertFalse(InfantryUnitPrep.tryOpportunitySpecial(carrier, sim));
        assertEquals(0f, sim.world().secondaryActionTimer(carrier));
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
