package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allies are counted, and counted separately. The company's ledger is what the
 * campaign acts on — the roster, the personnel outcome, reputation — and an
 * allied militia's dead are not the company's dead however hard they fought.
 */
class AlliedCasualtyLedgerTest {

    @Test
    void alliedSurvivorsAndDeadAreCountedInTheirOwnColumns() {
        try (BattleSimulation sim = simulation()) {
            sim.spawn(unit("marine-1", Faction.MARINE, UnitType.MARINE, 2, 2));
            sim.spawn(unit("ally-1", Faction.ALLY, UnitType.MILITIA, 4, 4));
            sim.spawn(unit("ally-2", Faction.ALLY, UnitType.MILITIA, 5, 4));
            long fallenAlly = sim.spawn(
                    unit("ally-3", Faction.ALLY, UnitType.MILITIA, 6, 4));
            sim.applyDamage(fallenAlly, 10_000f, 10_000f);
            sim.advance(BattleSimulation.TICK_DT);

            MissionOutcome outcome = MissionResolver.compute(sim, mission(), null);

            assertEquals(3, outcome.alliesEngaged,
                    "two standing and one dead is three who took the field");
            assertEquals(1, outcome.alliesLost);
            assertEquals(1, outcome.marinesEngaged,
                    "the company's own column counts the company only");
            assertEquals(0, outcome.marinesLost);
        }
    }

    @Test
    void anAlliedCasualtyNeverReachesThePersonnelLedger() {
        try (BattleSimulation sim = simulation()) {
            sim.spawn(unit("marine-1", Faction.MARINE, UnitType.MARINE, 2, 2));
            long fallenAlly = sim.spawn(
                    unit("ally-1", Faction.ALLY, UnitType.MILITIA, 4, 4));
            sim.applyDamage(fallenAlly, 10_000f, 10_000f);
            sim.advance(BattleSimulation.TICK_DT);

            MissionOutcome outcome = MissionResolver.compute(sim, mission(), null);

            assertEquals(1, outcome.alliesLost);
            assertTrue(outcome.fallenSoldierIds.isEmpty(),
                    "an allied death is not one of the company's fallen");
            assertTrue(outcome.survivingSoldierIds.isEmpty(),
                    "and the standing ally is not one of its survivors either");
        }
    }

    /** No allies on the field is the ordinary contract, and reads as zero. */
    @Test
    void anOrdinaryContractReportsNoAllies() {
        try (BattleSimulation sim = simulation()) {
            sim.spawn(unit("marine-1", Faction.MARINE, UnitType.MARINE, 2, 2));
            sim.spawn(unit("defender-1", Faction.DEFENDER, UnitType.MILITIA, 9, 9));

            MissionOutcome outcome = MissionResolver.compute(sim, mission(), null);

            assertEquals(0, outcome.alliesEngaged);
            assertEquals(0, outcome.alliesLost);
        }
    }

    private static EntitySpec unit(String id, Faction faction, UnitType type,
                                   int x, int y) {
        return new EntitySpec(id, faction, type, x, y);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(20, 20));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static Mission mission() {
        return Mission.builder()
                .id("allied-ledger:1")
                .name("Allied Ledger")
                .type(MissionType.ASSAULT)
                .source(MissionSource.GENERATED)
                .risk(RiskLevel.MEDIUM)
                .requirements("Committed assault")
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(1)
                .build();
    }
}
