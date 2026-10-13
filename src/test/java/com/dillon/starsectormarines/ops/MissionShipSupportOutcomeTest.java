package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.ShipSupportResult;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The resolver reads how each called ship ended from the frozen battle, and only member-called ships. */
class MissionShipSupportOutcomeTest {

    @BeforeEach
    void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(60, 40);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(60, 40));
        sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 10, 20));
        sim.spawn(new EntitySpec("far", Faction.DEFENDER, UnitType.MILITIA, 55, 35));
        return sim;
    }

    private static Warship called(BattleSimulation sim, ShipSupportSource source) throws Exception {
        Warship ship = Warship.fit(source, Faction.MARINE, 30f, 20f, 0f);
        ship.calledFrom(source);
        sim.addWarship(ship);
        return ship;
    }

    private static Mission mission() {
        return Mission.builder().id("ship-support:1").name("Ship Support").type(MissionType.ASSAULT)
                .source(MissionSource.GENERATED).risk(RiskLevel.MEDIUM).requirements("Committed assault")
                .mapPosition(0.5f, 0.5f).requiredDrops(1).build();
    }

    @Test
    void calledMemberShipsAreRecordedWithTheirEndAndDebugShipsAreNot() throws Exception {
        try (BattleSimulation sim = simulation()) {
            ShipSupportSource hurt = ShipSupportSource.stock("onslaught", "onslaught_Standard", "Anvil", false)
                    .withMemberId("member-1");
            ShipSupportSource lost = ShipSupportSource.stock("paragon", "paragon_Elite", "Bulwark", false)
                    .withMemberId("member-2");
            ShipSupportSource debug = ShipSupportSource.stock("onslaught", "onslaught_Standard", "Debug", false);
            Warship hurtShip = called(sim, hurt);
            Warship lostShip = called(sim, lost);
            called(sim, debug);

            hurtShip.takeDamage(hurtShip.maxHitPoints * 0.2f, 1000f);
            lostShip.takeDamage(lostShip.maxHitPoints * 10f, 1000f);
            for (int i = 0; i < 5; i++) sim.advance(BattleSimulation.TICK_DT);
            assertTrue(lostShip.destroyed());
            assertTrue(sim.warships().ships().stream().noneMatch(s -> s == lostShip),
                    "a destroyed hull leaves the live list");

            MissionOutcome outcome = MissionResolver.compute(sim, mission(), null);

            assertEquals(2, outcome.shipsCalled.size(), "the debug ship has no member to write to");
            ShipSupportResult first = outcome.shipsCalled.get(0);
            assertEquals("member-1", first.memberId());
            assertEquals("Anvil", first.shipName());
            assertTrue(!first.destroyed() && first.hullFraction() > 0f && first.hullFraction() < 1f);
            ShipSupportResult second = outcome.shipsCalled.get(1);
            assertEquals("member-2", second.memberId());
            assertTrue(second.destroyed());
            assertEquals(0f, second.hullFraction());
        }
    }

    @Test
    void aBattleThatCalledNothingRecordsNothing() {
        try (BattleSimulation sim = simulation()) {
            assertTrue(MissionResolver.compute(sim, mission(), null).shipsCalled.isEmpty());
        }
    }
}
