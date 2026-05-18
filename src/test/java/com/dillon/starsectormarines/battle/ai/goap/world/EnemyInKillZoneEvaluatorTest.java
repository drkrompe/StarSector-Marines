package com.dillon.starsectormarines.battle.ai.goap.world;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story A — exercises {@link Predicate#ENEMY_IN_KILL_ZONE} via
 * {@link WorldStateBuilder#build}. Verifies the LOS-ticks hysteresis and the
 * non-garrison short-circuit (marines/patrols always read true, the gate is
 * a no-op for them).
 */
public class EnemyInKillZoneEvaluatorTest {

    private static final int W = 16;
    private static final int H = 16;

    /** Open floor — no walls, LOS is straightforward. */
    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Unit defenderAt(int x, int y, int squadId) {
        Unit u = new Unit("d", Faction.DEFENDER, UnitType.MARINE, x, y);
        u.squadId = squadId;
        return u;
    }

    private static Unit marineAt(int x, int y) {
        return new Unit("m", Faction.MARINE, UnitType.MARINE, x, y);
    }

    @Test
    public void nonGarrisonSquadAlwaysReadsTrue() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.holdsFireUntilKillZone = false;

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertTrue(s.get(Predicate.ENEMY_IN_KILL_ZONE),
                "non-garrison squads must short-circuit to TRUE — EngagePosture's precondition stays a no-op for marines/patrols");
    }

    @Test
    public void garrisonReadsFalseWithoutSustainedLos() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.holdsFireUntilKillZone = true;
        squad.killZoneLosTicks = 0;

        Unit defender = defenderAt(5, 5, squadId);
        sim.addUnit(defender);
        sim.addUnit(marineAt(8, 5));

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertFalse(s.get(Predicate.ENEMY_IN_KILL_ZONE),
                "garrison with 0 LOS ticks fails the hysteresis check — gate stays closed");
    }

    @Test
    public void garrisonReadsTrueOnceCounterCrossesThreshold() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.holdsFireUntilKillZone = true;
        squad.killZoneLosTicks = BattleSimulation.KILL_ZONE_LOS_TICKS_THRESHOLD;

        Unit defender = defenderAt(5, 5, squadId);
        sim.addUnit(defender);
        // Marine within kill-zone range (8 cells), LOS clear on open floor.
        sim.addUnit(marineAt(8, 5));

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertTrue(s.get(Predicate.ENEMY_IN_KILL_ZONE),
                "garrison with full LOS-tick count + a visible close enemy must trip the gate");
    }

    @Test
    public void garrisonReadsFalseEvenWithTicksWhenNoCloseEnemyVisible() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.holdsFireUntilKillZone = true;
        squad.killZoneLosTicks = BattleSimulation.KILL_ZONE_LOS_TICKS_THRESHOLD;

        Unit defender = defenderAt(2, 2, squadId);
        sim.addUnit(defender);
        // Marine far beyond KILL_ZONE_RANGE_CELLS (8).
        sim.addUnit(marineAt(14, 14));

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertFalse(s.get(Predicate.ENEMY_IN_KILL_ZONE),
                "the gate also requires a currently-visible enemy in the kill zone — if everyone retreats out the gate closes again");
    }

    @Test
    public void singleTickLosDoesNotTrip() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.holdsFireUntilKillZone = true;
        squad.killZoneLosTicks = 1; // one tick, not enough

        Unit defender = defenderAt(5, 5, squadId);
        sim.addUnit(defender);
        sim.addUnit(marineAt(8, 5));

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertFalse(s.get(Predicate.ENEMY_IN_KILL_ZONE),
                "a single tick of LOS doesn't trip the gate — hysteresis suppresses flicker on transient sightings");
    }
}
