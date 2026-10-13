package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tender's option list: every entry names a real catalog structure, and the keys follow the list. */
class BuildOptionTest {

    @Test
    void everyOptionNamesAStructureTheCatalogHas() {
        for (BuildOption option : BuildOption.ALL) {
            assertNotNull(TurretCatalogRegistry.requireStructure(option.structureId()), option.id());
        }
    }

    @Test
    void theListFillsAtMostTheNumberKeysWithDistinctOptions() {
        assertTrue(BuildOption.ALL.size() <= TenderPilot.OPTION_SLOTS);
        Set<String> ids = new HashSet<>();
        Set<String> structures = new HashSet<>();
        for (BuildOption option : BuildOption.ALL) {
            assertTrue(ids.add(option.id()), "duplicate id " + option.id());
            assertTrue(structures.add(option.structureId()), "duplicate structure " + option.structureId());
        }
    }

    @Test
    void numberKeysFollowTheListOrder() {
        for (int slot = 0; slot < BuildOption.ALL.size(); slot++) {
            assertSame(BuildOption.ALL.get(slot), TenderPilot.option(slot));
        }
        assertSame(BuildOption.VULCAN_TURRET, TenderPilot.option(0));
    }

    @Test
    void heavierStructuresTakeLongerAndCostNoLessThanTheVulcan() {
        for (BuildOption option : BuildOption.ALL) {
            if (!option.turretKind()) continue;
            assertTrue(option.buildSeconds() >= BuildOption.VULCAN_TURRET.buildSeconds(), option.id());
            assertTrue(option.cost() >= BuildOption.VULCAN_TURRET.cost(), option.id());
        }
    }

    @Test
    void thePodNamesTheStructureTheCarriedItemPlacesAndIsLighterThanAnyTurret() {
        BuildOption pod = BuildOption.POINT_DEFENCE_POD;
        assertEquals(BuildOption.Kind.POINT_DEFENCE, pod.kind());
        assertEquals(pod.structureId(), pod.podSpec().structureId());
        assertTrue(pod.buildSeconds() < BuildOption.VULCAN_TURRET.buildSeconds());
        assertTrue(pod.podSpec().lifetimeSeconds() > 0f);
        assertSame(pod, BuildOption.ALL.get(BuildOption.ALL.size() - 1), "the pod is the last key");
    }

    @Test
    void finishingATurretSiteSpawnsATurretAndFinishingAPodSiteQueuesAPlacement() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        try (BattleSimulation sim = new BattleSimulation(grid, new CellTopology(20, 20))) {
            sim.setMissionCompletionEnabled(false);
            int before = sim.liveUnitCount();
            TenderSystem.finish(BuildOption.POINT_DEFENCE_POD, Faction.MARINE, "pod", 5, 5, sim);
            assertEquals(before, sim.liveUnitCount(), "queued, not minted until the service ticks");
            sim.advance(BattleSimulation.TICK_DT);
            assertEquals(1, sim.pointDefense().activeEmplacements().size());
            assertEquals(Faction.MARINE, sim.pointDefense().activeEmplacements().get(0).faction());
            TenderSystem.finish(BuildOption.VULCAN_TURRET, Faction.MARINE, "gun", 10, 10, sim);
            assertEquals(1, sim.pointDefense().activeEmplacements().size(), "a turret is no pod");
            assertEquals(before + 2, sim.liveUnitCount());
        }
    }

    @Test
    void theDebugPanelCyclesThroughTheListAndWraps() {
        int index = 0;
        for (int i = 0; i < BuildOption.ALL.size(); i++) index = DebugTender.nextOption(index);
        assertEquals(0, index);
        assertEquals(1, DebugTender.nextOption(0));
    }
}
