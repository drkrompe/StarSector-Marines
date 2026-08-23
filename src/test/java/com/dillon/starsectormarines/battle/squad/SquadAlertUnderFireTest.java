package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadAlertUnderFireTest {

    private static final int W = 24;
    private static final int H = 16;

    @Test
    void hostileEndpointMarksEveryNearbySquadButNotDistantSquads() {
        BattleSimulation sim = openSim();
        Squad firstNearby = squadAt(sim, Faction.MARINE, 5, 5);
        Squad secondNearby = squadAt(sim, Faction.MARINE, 7, 5);
        Squad distantMarine = squadAt(sim, Faction.MARINE, 12, 5);

        sim.postShot(shot(Faction.DEFENDER, 10.5f, 5.5f, 5.5f, 5.5f));
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(firstNearby._underFireAtLosThisTick,
                "a hostile endpoint near one squad marks it under fire");
        assertTrue(secondNearby._underFireAtLosThisTick,
                "the inclusive two-cell boundary can independently mark another squad");
        assertFalse(distantMarine._underFireAtLosThisTick,
                "units outside the two-cell endpoint radius are not candidates");
    }

    @Test
    void shotIsFilteredAgainstEachCandidateSquadsFaction() {
        BattleSimulation sim = openSim();
        Squad marine = squadAt(sim, Faction.MARINE, 5, 5);
        Squad defender = squadAt(sim, Faction.DEFENDER, 6, 5);

        sim.postShot(shot(Faction.MARINE, 10.5f, 5.5f, 5.5f, 5.5f));
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(marine._underFireAtLosThisTick,
                "same-faction incoming fire is ignored");
        assertTrue(defender._underFireAtLosThisTick,
                "the same shot remains hostile to a different nearby squad");
    }

    @Test
    void endpointProximityStillRequiresLosBackToShotOrigin() {
        BattleSimulation sim = wallSim();
        Squad blocked = squadAt(sim, Faction.MARINE, 3, 5);
        Squad clear = squadAt(sim, Faction.MARINE, 11, 5);

        sim.postShot(shot(Faction.DEFENDER, 10.5f, 5.5f, 3.5f, 5.5f));
        sim.postShot(shot(Faction.DEFENDER, 13.5f, 5.5f, 11.5f, 5.5f));
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(blocked._underFireAtLosThisTick,
                "a wall between the nearby member and firing cell blocks the signal");
        assertTrue(clear._underFireAtLosThisTick,
                "a nearby member with clear sight back to the firing cell is marked");
    }

    private static Squad squadAt(BattleSimulation sim, Faction faction, int x, int y) {
        int squadId = sim.mintSquad(faction, UnitType.MARINE);
        sim.spawn(new EntitySpec("member", faction, UnitType.MARINE, x, y).squad(squadId));
        return sim.getSquad(squadId);
    }

    private static ShotEvent shot(Faction faction, float fromX, float fromY,
                                  float toX, float toY) {
        return new ShotEvent(fromX, fromY, toX, toY, true, faction, 1f);
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = openGrid();
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static BattleSimulation wallSim() {
        NavigationGrid grid = openGrid();
        for (int y = 0; y < H; y++) {
            grid.setWalkable(7, y, false);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return grid;
    }
}
