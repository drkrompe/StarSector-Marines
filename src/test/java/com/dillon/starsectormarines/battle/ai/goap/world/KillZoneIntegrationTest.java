package com.dillon.starsectormarines.battle.ai.goap.world;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.EngagePosture;
import com.dillon.starsectormarines.battle.ai.goap.actions.OverwatchPosture;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story A integration — drive {@link BattleSimulation#advance} for several
 * ticks against a hand-placed garrison + advancing marine and verify the
 * kill-zone LOS-tick counter accumulates and the kill-zone gate eventually
 * trips. Confirms the wired-up path: alert-update pass increments the counter
 * → WorldStateBuilder reads it → EngagePosture's precondition flips.
 */
public class KillZoneIntegrationTest {

    private static final int W = 20;
    private static final int H = 10;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void garrisonAccumulatesKillZoneTicksAndTripsGate() {
        BattleSimulation sim = openSim();
        int defSquadId = sim.mintSquad(Faction.DEFENDER, null);
        Squad defSquad = sim.getSquad(defSquadId);
        defSquad.holdsFireUntilKillZone = true;

        Unit defender = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        defender.squadId = defSquadId;
        sim.addUnit(defender);

        // Marine inside KILL_ZONE_RANGE_CELLS (8). Open floor → LOS clear.
        Unit marine = new Unit("m1", Faction.MARINE, UnitType.MARINE, 10, 5);
        sim.addUnit(marine);

        // Drive a few sim ticks. Each tick the alert-update pass increments
        // killZoneLosTicks (capped at the threshold).
        // TICK_DT is 1/30 sec; advance N ticks worth.
        float dt = 1.0f / 30f;
        for (int i = 0; i < BattleSimulation.KILL_ZONE_LOS_TICKS_THRESHOLD + 2; i++) {
            sim.advance(dt);
        }

        assertTrue(defSquad.killZoneLosTicks >= BattleSimulation.KILL_ZONE_LOS_TICKS_THRESHOLD,
                "garrison with sustained close-LOS to a marine must accumulate ticks to threshold; got " + defSquad.killZoneLosTicks);

        // World-state snapshot now reads true.
        WorldState s = WorldStateBuilder.build(defSquad, sim);
        assertTrue(s.get(Predicate.ENEMY_IN_KILL_ZONE),
                "after sustained LOS, the kill-zone predicate trips");

        // The planner-visible consequence: EngagePosture's precondition holds,
        // OverwatchPosture's precondition no longer holds.
        assertTrue(s.satisfies(WorldState.EMPTY.with(Predicate.ENEMY_IN_KILL_ZONE, true)));
        // Sanity — Overwatch's precondition is ENEMY_IN_KILL_ZONE=false, which
        // now conflicts with the current snapshot.
        assertFalse(s.satisfies(OverwatchPosture.INSTANCE.preconditions()),
                "once gate trips, Overwatch's preconditions no longer match");
        // Sanity — Engage's precondition specifies ENEMY_IN_KILL_ZONE=true,
        // and the snapshot now matches that bit. (LoS/range bits depend on
        // broader state but should also hold here: open floor + range 8 + close marine.)
        assertTrue(EngagePosture.INSTANCE.preconditions().isSpecified(Predicate.ENEMY_IN_KILL_ZONE));
        assertTrue(s.get(Predicate.ENEMY_IN_KILL_ZONE),
                "after the gate trips, the kill-zone bit reads true so Engage's matching bit is satisfied");
    }

    @Test
    public void killZoneCounterResetsWhenEnemyOutOfSight() {
        BattleSimulation sim = openSim();
        int defSquadId = sim.mintSquad(Faction.DEFENDER, null);
        Squad defSquad = sim.getSquad(defSquadId);
        defSquad.holdsFireUntilKillZone = true;
        // Pre-set the counter to half the threshold to simulate prior ticks.
        defSquad.killZoneLosTicks = BattleSimulation.KILL_ZONE_LOS_TICKS_THRESHOLD / 2;

        Unit defender = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        defender.squadId = defSquadId;
        sim.addUnit(defender);
        // No marines anywhere → no close-LOS sighting this tick → counter resets.

        sim.advance(1.0f / 30f);

        assertTrue(defSquad.killZoneLosTicks == 0,
                "no close-LOS sighting this tick → counter resets to 0; was " + defSquad.killZoneLosTicks);
    }

    @Test
    public void nonGarrisonSquadDoesNotAccumulateTicks() {
        // Patrol squads (holdsFireUntilKillZone = false) leave the counter at 0 —
        // the alert-update pass skips them entirely.
        BattleSimulation sim = openSim();
        int defSquadId = sim.mintSquad(Faction.DEFENDER, null);
        Squad defSquad = sim.getSquad(defSquadId);
        defSquad.holdsFireUntilKillZone = false;

        Unit defender = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        defender.squadId = defSquadId;
        sim.addUnit(defender);
        Unit marine = new Unit("m1", Faction.MARINE, UnitType.MARINE, 10, 5);
        sim.addUnit(marine);

        for (int i = 0; i < 10; i++) {
            sim.advance(1.0f / 30f);
        }

        assertTrue(defSquad.killZoneLosTicks == 0,
                "non-garrison squads must not accumulate ticks; was " + defSquad.killZoneLosTicks);
        // But the predicate still reads true for them (the short-circuit).
        WorldState s = WorldStateBuilder.build(defSquad, sim);
        assertTrue(s.get(Predicate.ENEMY_IN_KILL_ZONE),
                "non-garrison squads always read TRUE — the gate is a no-op for them");
    }
}
