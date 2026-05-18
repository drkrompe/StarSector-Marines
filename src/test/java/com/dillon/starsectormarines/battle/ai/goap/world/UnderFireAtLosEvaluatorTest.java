package com.dillon.starsectormarines.battle.ai.goap.world;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.ShotEvent;
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
 * Story A — exercises {@link Predicate#UNDER_FIRE_AT_LOS} via
 * {@link WorldStateBuilder#build}. Verifies the "hostile shot near a
 * squadmate with LOS back to the firing cell" contract.
 */
public class UnderFireAtLosEvaluatorTest {

    private static final int W = 16;
    private static final int H = 16;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Open floor with a solid wall column at x=7 (rows 0..H-1). Splits LOS cleanly. */
    private static BattleSimulation wallSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        for (int y = 0; y < H; y++) {
            grid.setWalkable(7, y, false);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Unit marineAt(int x, int y, int squadId) {
        Unit u = new Unit("m", Faction.MARINE, UnitType.MARINE, x, y);
        u.squadId = squadId;
        return u;
    }

    @Test
    public void emptyShotsReadsFalse() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;

        Unit marine = marineAt(5, 5, squadId);
        sim.addUnit(marine);

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertFalse(s.get(Predicate.UNDER_FIRE_AT_LOS),
                "no shots in flight → predicate is false");
    }

    @Test
    public void hostileShotNearSquadmateWithLosReadsTrue() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;

        Unit marine = marineAt(5, 5, squadId);
        sim.addUnit(marine);

        // Hostile shot from (10.5, 5.5) aimed at the marine's cell (5.5, 5.5).
        // Open floor → LOS clear from marine cell to (10,5).
        sim.postShot(new ShotEvent(10.5f, 5.5f, 5.5f, 5.5f, true, Faction.DEFENDER, 1.0f));

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertTrue(s.get(Predicate.UNDER_FIRE_AT_LOS),
                "a hostile shot landing near a squadmate with LOS to the firing cell trips the predicate");
    }

    @Test
    public void hostileShotThroughWallReadsFalse() {
        BattleSimulation sim = wallSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;

        Unit marine = marineAt(3, 5, squadId);
        sim.addUnit(marine);

        // "Shot" originating on the far side of the wall (x=10), targeting near
        // the marine. In a real sim a wall would block the shot — this synthetic
        // shot exists in the active list, but LOS from marine (3,5) to (10,5)
        // is blocked by the wall column at x=7. Evaluator must filter on the
        // LOS check.
        sim.postShot(new ShotEvent(10.5f, 5.5f, 3.5f, 5.5f, true, Faction.DEFENDER, 1.0f));

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertFalse(s.get(Predicate.UNDER_FIRE_AT_LOS),
                "shot whose origin is occluded from the squadmate does not trip — the LOS-to-origin check filters it out");
    }

    @Test
    public void friendlyShotIsIgnored() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;

        Unit marine = marineAt(5, 5, squadId);
        sim.addUnit(marine);

        // Marine-faction shot near the marine — same faction, shouldn't trip.
        sim.postShot(new ShotEvent(10.5f, 5.5f, 5.5f, 5.5f, true, Faction.MARINE, 1.0f));

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertFalse(s.get(Predicate.UNDER_FIRE_AT_LOS),
                "same-faction shot must not trip — UNDER_FIRE_AT_LOS is about hostile incoming");
    }

    @Test
    public void distantShotDoesNotTrip() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;

        Unit marine = marineAt(5, 5, squadId);
        sim.addUnit(marine);

        // Hostile shot landing way out of squadmate proximity (target at 14, 14).
        sim.postShot(new ShotEvent(10.5f, 10.5f, 14.5f, 14.5f, true, Faction.DEFENDER, 1.0f));

        WorldState s = WorldStateBuilder.build(squad, sim);
        assertFalse(s.get(Predicate.UNDER_FIRE_AT_LOS),
                "shot whose target endpoint is far from every squadmate doesn't trip");
    }
}
