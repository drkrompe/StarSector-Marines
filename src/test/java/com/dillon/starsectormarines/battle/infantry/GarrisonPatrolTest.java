package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rotation coverage for {@link GarrisonPatrol}'s QUIET state — guards against
 * the multi-room patrol collapsing into the largest room or oscillating between
 * two (the cursor is reverse-derived from the current waypoint cell's zone, so
 * a bug there would silently kill the headline behavior). Drives the real
 * {@code execute} path: each iteration forces the dwell to expire and parks the
 * squad on its current waypoint (so {@code squadHasArrived} is true), which
 * advances the round-robin to the next room.
 */
public class GarrisonPatrolTest {

    private static final int W = 40;
    private static final int H = 12;

    /**
     * Three stacked rooms on the right (A: y0–3, B: y5–7, C: y9–11; all
     * x25–39), split by walls at y=4 and y=8 with doorways, reached from the
     * outdoor flood (x0–23) through a doorway at (24,2). The compound footprint
     * spans all three.
     */
    private static BattleSimulation threeRoomSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == 24) continue;                       // outdoor | building wall
                if (x >= 25 && (y == 4 || y == 8)) continue; // room-splitting walls
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(24, 2);  grid.setDoorway(24, 2, true);  // outdoor ↔ A
        grid.setWalkableFloor(32, 4);  grid.setDoorway(32, 4, true);  // A ↔ B
        grid.setWalkableFloor(32, 8);  grid.setDoorway(32, 8, true);  // B ↔ C
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static TacticalNode compoundSpanningAllRooms() {
        TacticalNode n = new TacticalNode(TacticalNode.Kind.COMMAND_POST, 30, 2,
                30, 2, 30, 2, Faction.DEFENDER, 95, 4);
        n.setCompoundBounds(25, 0, 39, 11);
        return n;
    }

    @Test
    public void quietPatrolVisitsEveryRoomNotJustTheLargest() {
        BattleSimulation sim = threeRoomSim();
        TacticalNode node = compoundSpanningAllRooms();
        List<Integer> zones = GarrisonArea.garrisonZones(node, GarrisonCompound.GARRISON_MARGIN, sim);
        assertEquals(3, zones.size(), "footprint should resolve to the three rooms");

        GarrisonPatrol patrol = new GarrisonPatrol(zones);
        Unit leader = new Unit("L", Faction.MARINE, UnitType.MARINE, 30, 2);
        sim.addUnit(leader);
        Squad squad = new Squad(1, Faction.MARINE);
        squad.leader = leader;
        squad.aliveMembers = 1;
        squad.centroidX = 30; squad.centroidY = 2;

        // First tick picks the initial waypoint (largest room).
        patrol.execute(leader, squad, sim);

        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            squad.patrolDwellTimer = 0f;                 // dwell expired
            squad.centroidX = squad.patrolWaypointX;     // parked on the waypoint → arrived
            squad.centroidY = squad.patrolWaypointY;
            patrol.execute(leader, squad, sim);
            visited.add(sim.getZoneGraph().zoneIdAt(squad.patrolWaypointX, squad.patrolWaypointY));
        }

        assertEquals(3, visited.size(),
                "round-robin must cover all three rooms, not collapse to the largest or oscillate");
        for (int z : zones) {
            assertTrue(visited.contains(z), "every garrison room is visited by the patrol rotation");
        }
    }
}
