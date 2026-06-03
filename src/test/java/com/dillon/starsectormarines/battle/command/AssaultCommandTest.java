package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Coverage for {@link AssaultCommand}'s exterior-zone guard (story 17 bug 0a):
 * the search-and-destroy sweep must never hand a squad the open exterior flood
 * zone as a {@code CLEAR_ZONE} target — clearing it never completes, so the
 * squad would charge the whole map. Outdoor defenders are engaged ambiently by
 * {@code EliminateEnemiesGoal} instead.
 */
public class AssaultCommandTest {

    private static final int W = 30;
    private static final int H = 10;

    /**
     * One big exterior flood zone plus a small enclosed room in the top-left
     * corner (interior x∈[0,2], y∈[0,2]), sealed by an L-wall with a doorway at
     * (3,1). On the 2×2 sector grid this map produces, both the exterior's
     * centroid and the corner room fall in sector 0, so a squad there sees both
     * — exactly the situation that exercises the guard. The exterior is the
     * largest zone, so it's the cached exterior id.
     */
    private static BattleSimulation roomPlusExteriorSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkable(3, 0, false);
        grid.setWalkable(3, 2, false);
        grid.setWalkable(0, 3, false);
        grid.setWalkable(1, 3, false);
        grid.setWalkable(2, 3, false);
        grid.setWalkable(3, 3, false);
        grid.setDoorway(3, 1, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad addMarineSquad(BattleSimulation sim, float centroidX, float centroidY) {
        Entity leader = new Entity("m", Faction.MARINE, UnitType.MARINE,
                Math.round(centroidX), Math.round(centroidY));
        sim.addUnit(leader);
        int sid = sim.mintSquad(Faction.MARINE, leader);
        leader.squadId = sid;
        Squad squad = sim.getSquad(sid);
        squad.aliveMembers = 1;
        squad.centroidX = centroidX;
        squad.centroidY = centroidY;
        return squad;
    }

    private static void addDefender(BattleSimulation sim, int cellX, int cellY) {
        sim.addUnit(new Entity("d-" + cellX + "-" + cellY, Faction.DEFENDER, UnitType.MARINE, cellX, cellY));
    }

    @Test
    public void exteriorZoneIsNeverAssignedAsClearTarget() {
        BattleSimulation sim = roomPlusExteriorSim();
        AssaultCommand cmd = new AssaultCommand();
        Squad squad = addMarineSquad(sim, 5f, 3f);   // exterior, sector 0
        addDefender(sim, 7, 3);                       // exterior, sector 0
        addDefender(sim, 1, 1);                       // enclosed room, sector 0

        cmd.tick(sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a, "a clearable room defender exists → squad gets an assignment");
        assertEquals(AssignmentKind.CLEAR_ZONE, a.kind());
        int roomZone = sim.getZoneGraph().zoneIdAt(1, 1);
        int exteriorZone = sim.getZoneGraph().zoneIdAt(5, 3);
        assertEquals(roomZone, a.targetZoneId(),
                "target must be the enclosed room, not the open exterior");
        assertNotEquals(exteriorZone, a.targetZoneId(),
                "the exterior flood zone must never be a CLEAR_ZONE target");
    }

    @Test
    public void exteriorOnlyDefenderLeavesAssignmentNull() {
        BattleSimulation sim = roomPlusExteriorSim();
        AssaultCommand cmd = new AssaultCommand();
        Squad squad = addMarineSquad(sim, 5f, 3f);   // exterior, sector 0
        addDefender(sim, 8, 3);                       // exterior only

        cmd.tick(sim);

        assertNull(squad.assignedObjective,
                "exterior-only defender → no active sector, no CLEAR_ZONE; squad engages ambiently");
    }
}
