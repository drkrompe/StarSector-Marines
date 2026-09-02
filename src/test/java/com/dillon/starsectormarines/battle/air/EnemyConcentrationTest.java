package com.dillon.starsectormarines.battle.air;

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
 * Picking what an aircraft is sent at.
 *
 * <p>Asked of the chooser directly. Whether a field then flies at what it
 * chose is a dispatch question and belongs to {@code AirStrikeSystemTest}.
 */
class EnemyConcentrationTest {

    private static final int W = 80;
    private static final int H = 40;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /** A bunch of marines shoulder to shoulder around {@code (x, y)}. */
    private static void cluster(BattleSimulation sim, String tag, int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            sim.spawn(new EntitySpec(tag + i, Faction.MARINE, UnitType.MARINE,
                    x + (i % 3), y + (i / 3)));
        }
    }

    /** Settles the spatial index, which is what the scan reads. */
    private static void settle(BattleSimulation sim) {
        sim.advance(BattleSimulation.TICK_DT);
    }

    /** The bigger huddle wins, whichever is nearer. */
    @Test
    void theDensestClusterIsTheOneChosen() {
        try (BattleSimulation sim = openSim()) {
            cluster(sim, "small", 5, 5, 5);
            cluster(sim, "big", 60, 30, 12);
            settle(sim);

            long chosen = EnemyConcentration.densest(sim, Faction.DEFENDER);

            assertTrue(sim.world().x(chosen) > 40f,
                    "picked the smaller huddle at " + sim.world().x(chosen));
        }
    }

    /**
     * Told what is already being worked, it names something else.
     *
     * <p>This is what lets a field with several aircraft up spread them over
     * the battle rather than stacking them on one platoon.
     */
    @Test
    void aConcentrationAlreadyBeingWorkedIsPassedOver() {
        try (BattleSimulation sim = openSim()) {
            cluster(sim, "big", 60, 30, 12);
            cluster(sim, "second", 5, 5, 8);
            settle(sim);

            long busy = EnemyConcentration.densest(sim, Faction.DEFENDER);
            float[] engaged = { sim.world().x(busy), sim.world().y(busy) };
            long next = EnemyConcentration.densestAwayFrom(sim, Faction.DEFENDER, engaged, 1,
                    EnemyConcentration.SEPARATE_TARGET_DIST);

            assertTrue(next != 0L, "found nothing else on a map with two concentrations");
            float dx = sim.world().x(next) - engaged[0];
            float dy = sim.world().y(next) - engaged[1];
            assertTrue(Math.sqrt(dx * dx + dy * dy) >= EnemyConcentration.SEPARATE_TARGET_DIST,
                    "named a neighbour of the concentration already being attacked");
        }
    }

    /**
     * With only one concentration on the map, there is no second answer — and
     * it says so rather than naming somebody standing in the same platoon.
     *
     * <p>The caller is what decides to double up; that decision is not made
     * here by accident.
     */
    @Test
    void theOnlyConcentrationOnTheMapHasNoAlternative() {
        try (BattleSimulation sim = openSim()) {
            cluster(sim, "only", 60, 30, 12);
            settle(sim);

            long busy = EnemyConcentration.densest(sim, Faction.DEFENDER);
            float[] engaged = { sim.world().x(busy), sim.world().y(busy) };

            assertEquals(0L, EnemyConcentration.densestAwayFrom(sim, Faction.DEFENDER, engaged, 1,
                            EnemyConcentration.SEPARATE_TARGET_DIST),
                    "offered a second target inside the one concentration there is");
        }
    }

    /** Nothing to avoid is the same question as the plain one. */
    @Test
    void avoidingNothingIsTheOrdinaryChoice() {
        try (BattleSimulation sim = openSim()) {
            cluster(sim, "big", 60, 30, 12);
            cluster(sim, "small", 5, 5, 5);
            settle(sim);

            assertEquals(EnemyConcentration.densest(sim, Faction.DEFENDER),
                    EnemyConcentration.densestAwayFrom(sim, Faction.DEFENDER, null, 0, 0f));
        }
    }
}
