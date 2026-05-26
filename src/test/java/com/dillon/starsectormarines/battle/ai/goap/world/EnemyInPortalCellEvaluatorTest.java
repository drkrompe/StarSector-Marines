package com.dillon.starsectormarines.battle.ai.goap.world;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the {@link Predicate#ENEMY_IN_PORTAL_CELL} evaluator on
 * synthetic grids. The evaluator scopes the predicate to whichever portal id
 * the squad's {@link Squad#chokePointPortalId} field carries; tests verify
 * the on-cell / off-by-one / unscoped cases.
 */
public class EnemyInPortalCellEvaluatorTest {

    private static final int W = 10;
    private static final int H = 10;
    private static final int WALL_COL = 5;

    /** 10x10 split by a wall at column 5 with a doorway at (5, 5). Three zones: left floor, doorway cell, right floor. */
    private static BattleSimulation roomSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == WALL_COL) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(WALL_COL, 5);
        grid.setDoorway(WALL_COL, 5, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad defenderSquad(int aliveMembers) {
        Squad squad = new Squad(1, Faction.DEFENDER);
        squad.aliveMembers = aliveMembers;
        squad.centroidX = 2f;
        squad.centroidY = 5f;
        return squad;
    }

    @Test
    public void portalCellOccupiedByEnemyReadsTrue() {
        BattleSimulation sim = roomSim();
        // Pick the only portal in the sim — connects left+right via the doorway.
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();

        Squad squad = defenderSquad(1);
        squad.chokePointPortalId = portalId;

        // Enemy combatant standing exactly on the doorway cell.
        Unit attacker = new Unit("m1", Faction.MARINE, UnitType.MARINE, WALL_COL, 5);
        sim.addUnit(attacker);

        WorldState ws = WorldStateBuilder.build(squad, sim);
        assertTrue(ws.get(Predicate.ENEMY_IN_PORTAL_CELL),
                "enemy on the watched portal cell must trigger the predicate");
    }

    @Test
    public void enemyOneCellOffReadsFalse() {
        BattleSimulation sim = roomSim();
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();

        Squad squad = defenderSquad(1);
        squad.chokePointPortalId = portalId;

        // Enemy one cell away from the doorway — same row, one column off.
        Unit attacker = new Unit("m1", Faction.MARINE, UnitType.MARINE, 6, 5);
        sim.addUnit(attacker);

        WorldState ws = WorldStateBuilder.build(squad, sim);
        assertFalse(ws.get(Predicate.ENEMY_IN_PORTAL_CELL),
                "enemy adjacent to but not on the portal cell must not trigger");
    }

    @Test
    public void unscopedSquadReadsFalse() {
        BattleSimulation sim = roomSim();
        Squad squad = defenderSquad(1);
        // No portal watched (default -1).

        // Even with an enemy on the doorway, the predicate should read false:
        // the squad isn't running a ChokePointHold action.
        sim.addUnit(new Unit("m1", Faction.MARINE, UnitType.MARINE, WALL_COL, 5));

        WorldState ws = WorldStateBuilder.build(squad, sim);
        assertFalse(ws.get(Predicate.ENEMY_IN_PORTAL_CELL),
                "no portal scoped → predicate must read false regardless of map state");
    }

    @Test
    public void friendlyOnPortalCellReadsFalse() {
        BattleSimulation sim = roomSim();
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();
        Squad squad = defenderSquad(1);
        squad.chokePointPortalId = portalId;

        // A friendly on the doorway must not trigger — the predicate is
        // "enemy in portal cell," scoped by faction.
        Unit friendly = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, WALL_COL, 5);
        friendly.squadId = squad.id;
        sim.addUnit(friendly);

        WorldState ws = WorldStateBuilder.build(squad, sim);
        assertFalse(ws.get(Predicate.ENEMY_IN_PORTAL_CELL),
                "friendly unit on the doorway must not trigger the trap");
    }

    @Test
    public void deadEnemyOnPortalCellReadsFalse() {
        BattleSimulation sim = roomSim();
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();
        Squad squad = defenderSquad(1);
        squad.chokePointPortalId = portalId;

        Unit attacker = new Unit("m1", Faction.MARINE, UnitType.MARINE, WALL_COL, 5);
        sim.addUnit(attacker);
        TestUnits.kill(sim, attacker);

        WorldState ws = WorldStateBuilder.build(squad, sim);
        assertFalse(ws.get(Predicate.ENEMY_IN_PORTAL_CELL),
                "dead enemy on portal cell must not trigger — only alive combatants count");
    }

    @Test
    public void invalidPortalIdReadsFalse() {
        BattleSimulation sim = roomSim();
        Squad squad = defenderSquad(1);
        squad.chokePointPortalId = 9999; // out-of-range, portalById returns null

        sim.addUnit(new Unit("m1", Faction.MARINE, UnitType.MARINE, WALL_COL, 5));

        WorldState ws = WorldStateBuilder.build(squad, sim);
        assertFalse(ws.get(Predicate.ENEMY_IN_PORTAL_CELL),
                "stale or out-of-range portal id must read false rather than NPE");
    }
}
