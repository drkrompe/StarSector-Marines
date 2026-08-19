package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.ColonyArchiveObjective;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SilentColonyOutcomeReportTest {

    @Test
    void recoveredArchiveAndPartialSurvivorsReportIndependently() {
        BattleSimulation sim = simulation();
        registerAndSeal(sim, 6, 2);
        ColonyArchiveObjective archive = archive(sim);
        sim.addObjective(archive);
        sim.spawn(new EntitySpec("Marine", Faction.MARINE,
                UnitType.MARINE, 2, 2));

        while (!sim.isComplete()) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        MissionOutcome outcome = MissionResolver.compute(
                sim, mission(6), null);

        assertEquals(2, outcome.civiliansRescued);
        assertEquals(AbandonedColonyArchiveOutcome.RECOVERED,
                outcome.colonyArchiveOutcome);
    }

    @Test
    void terminalLossReportsZeroSurvivorsAndLostArchive() {
        BattleSimulation sim = simulation();
        registerAndSeal(sim, 6, 0);
        sim.addObjective(archive(sim));
        sim.addObjective(new EliminateFactionObjective(
                Faction.DEFENDER, Faction.MARINE));

        sim.advance(BattleSimulation.TICK_DT);
        MissionOutcome outcome = MissionResolver.compute(
                sim, mission(6), null);

        assertEquals(0, outcome.civiliansRescued);
        assertEquals(AbandonedColonyArchiveOutcome.LOST,
                outcome.colonyArchiveOutcome);
    }

    @Test
    void unfinishedBattleDoesNotInventArchiveLoss() {
        BattleSimulation sim = simulation();
        registerAndSeal(sim, 6, 3);
        sim.addObjective(archive(sim));

        MissionOutcome outcome = MissionResolver.compute(
                sim, mission(6), null);

        assertEquals(3, outcome.civiliansRescued);
        assertEquals(AbandonedColonyArchiveOutcome.NONE,
                outcome.colonyArchiveOutcome);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(8, 8));
    }

    private static ColonyArchiveObjective archive(BattleSimulation sim) {
        return new ColonyArchiveObjective(2, 2,
                sim.getZoneGraph().zoneIdAt(2, 2));
    }

    private static void registerAndSeal(BattleSimulation sim, int count,
                                        int evacuated) {
        sim.getCivilianEvacuationTracker().prepareExpectedCount(count);
        for (int i = 0; i < count; i++) {
            long id = 100L + i;
            sim.getCivilianEvacuationTracker().register(id);
            if (i < evacuated) {
                sim.getCivilianEvacuationTracker().markEvacuated(id);
            }
        }
        sim.getCivilianEvacuationTracker().seal();
    }

    private static Mission mission(int survivors) {
        return new Mission("silent-colony:7", "Silent Colony",
                MissionType.EXTRACTION, MissionSource.CAMPAIGN_EVENT,
                0, RiskLevel.HIGH, "Funded blind expedition", "",
                0.5f, 0.5f, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                4, 0, "Eidolon", null, "neutral",
                -1L, 7L, 3, survivors, 55L,
                (byte) 0, (byte) 0, (byte) 100,
                (byte) 0, (byte) 0, Collections.emptyList());
    }
}
