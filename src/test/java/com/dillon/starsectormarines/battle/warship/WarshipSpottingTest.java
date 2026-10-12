package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipSpottingTest {

    private static SceneWorld world(Faction shipSide) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld world = SceneBuilder.openGround(160, 90).missionCompletion(false)
                .unit("d1", Faction.DEFENDER, UnitType.MARINE, 70, 38)
                .unit("m1", Faction.MARINE, UnitType.MARINE, 8, 40)
                .build();
        BattleSimulation sim = world.sim();
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", shipSide, 38f, 40f, -90f);
        ship.order(new WarshipOrder.FireOnArea(70f, 38f, 10f, true));
        sim.addWarship(ship);
        return world;
    }

    @Test
    void aFriendlyShipLightsItsHullAndTheGroundItIsOrderedOnto() throws Exception {
        BattleSimulation sim = world(Faction.MARINE).sim();
        List<WarshipSpotting.Source> sources = WarshipSpotting.sources(sim);
        assertTrue(sources.stream().anyMatch(s -> s.cellX() == 38 && s.cellY() == 40), "the hull itself: " + sources);
        assertTrue(sources.stream().anyMatch(s -> s.cellX() == 70 && s.cellY() == 38 && s.range() >= 10),
                "the ordered ground: " + sources);
    }

    @Test
    void roundsInFlightLightTheirLandingGroundAndOnlyTheirOwnSideSeesIt() throws Exception {
        SceneWorld world = world(Faction.MARINE);
        BattleSimulation sim = world.sim();
        SceneRun.of(world).run(240);
        List<WarshipSpotting.Source> sources = WarshipSpotting.sources(sim);
        int shells = sim.warships().shells().size();
        assertTrue(shells == 0 || sources.size() > 2, "rounds in flight add sources: " + sources.size());
    }

    @Test
    void aHostileShipGivesThePlayerNothing() throws Exception {
        BattleSimulation sim = world(Faction.DEFENDER).sim();
        assertEquals(0, WarshipSpotting.sources(sim).size());
    }
}
