package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.mapgen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link ConquestCommand}'s strip-partition + forward-most-
 * defender assignment policy. Uses a synthetic open-grid sim so the
 * lateral strip math is deterministic and the zone graph is a single
 * walkable region split only by the test fixture.
 */
public class ConquestCommandTest {

    /** 30×10 — wide enough that 3 lateral strips have meaningful distinct ranges (10 cells each), short enough that "forward" is short for SOUTH_TO_NORTH. */
    private static final int W = 30;
    private static final int H = 10;

    /**
     * Walls at x=10 and x=20 split the map into three disconnected zones
     * (one per strip range when the lateral axis is x). The wall split is
     * structurally what makes the commander testable: with a single open
     * zone, the zone's centroid lands in one strip, so the other strips
     * are empty of zones and any defender placed there can't be detected
     * by {@link ConquestCommand}. Three zones lets the
     * {@code defender-in-strip} tests actually exercise the assignment
     * code path.
     *
     * <p>For WEST_TO_EAST axis tests the same map still works as a single
     * open zone for the assignment-side checks (centroid-on-y bucketing),
     * but those tests focus on strip classification rather than zone
     * occupancy.
     */
    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == 10 || x == 20) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad addMarineSquad(BattleSimulation sim, float centroidX, float centroidY) {
        Unit leader = new Unit("m", Faction.MARINE, UnitType.MARINE,
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

    private static Unit addDefender(BattleSimulation sim, int cellX, int cellY) {
        Unit d = new Unit("d-" + cellX + "-" + cellY, Faction.DEFENDER, UnitType.MARINE, cellX, cellY);
        sim.addUnit(d);
        return d;
    }

    @Test
    public void firstTickPartitionsZonesIntoStrips() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.WEST_TO_EAST);
        // Add one defender so SOMEWHERE has the "not clear" predicate; squad
        // doesn't need to exist to trigger initialization.
        addMarineSquad(sim, 1f, 1f);
        addDefender(sim, 25, 5);

        cmd.tick(sim);

        // Every strip should be non-empty (the synthetic grid has a single
        // big zone but the strip-builder buckets by centroid lateral coord).
        // For WEST_TO_EAST the lateral axis is y — a single open zone with
        // centroid in the middle lands in exactly one strip. That's fine
        // for the assertion: at least one strip is populated.
        int populated = 0;
        for (int s = 0; s < ConquestCommand.STRIP_COUNT; s++) {
            if (!cmd.zonesInStrip(s).isEmpty()) populated++;
        }
        assertTrue(populated >= 1, "at least one strip should have zones after partition");
    }

    @Test
    public void squadOnLeftEdgeIsAssignedToStripZero_SouthToNorth() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        // SOUTH_TO_NORTH → lateral = x. W=30, STRIP_COUNT=3 → 10 cells/strip.
        // Squad at x=2 lands in strip 0.
        Squad squad = addMarineSquad(sim, 2f, 5f);
        addDefender(sim, 2, 9);

        cmd.tick(sim);

        assertEquals(0, cmd.stripIndexOf(squad.id), "x=2 in a 30-wide map should fall into strip 0");
    }

    @Test
    public void squadOnRightEdgeIsAssignedToLastStrip_SouthToNorth() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 28f, 5f);
        addDefender(sim, 28, 9);

        cmd.tick(sim);

        assertEquals(ConquestCommand.STRIP_COUNT - 1, cmd.stripIndexOf(squad.id),
                "x=28 in a 30-wide map should fall into the last strip");
    }

    @Test
    public void squadAssignmentIsStickyAcrossTicks() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 5f);
        addDefender(sim, 25, 9);

        cmd.tick(sim);
        int firstStrip = cmd.stripIndexOf(squad.id);

        // Move the squad's centroid across the map — without sticky
        // assignment, it would re-classify into a different strip.
        squad.centroidX = 28f;

        cmd.tick(sim);
        int secondStrip = cmd.stripIndexOf(squad.id);

        assertEquals(firstStrip, secondStrip,
                "sticky assignment — squad should not migrate strips on centroid drift in v1");
    }

    @Test
    public void emptyStripClearsAssignment() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        // Squad in strip 0; no defenders anywhere.
        Squad squad = addMarineSquad(sim, 2f, 5f);

        cmd.tick(sim);

        assertNull(squad.assignedObjective,
                "strip with no defenders → null assignment, squad falls through to EliminateEnemies");
    }

    @Test
    public void defenderInSquadStripDrivesClearZoneAssignment() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 5f);
        // Defender at (3, 9) — same strip as squad, forward of the squad.
        addDefender(sim, 3, 9);

        cmd.tick(sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a, "defender in strip → squad gets CLEAR_ZONE");
        assertEquals(AssignmentKind.CLEAR_ZONE, a.kind());
    }

    @Test
    public void defenderInOtherStripDoesNotDriveThisSquadsAssignment() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 5f);   // strip 0
        addDefender(sim, 25, 9);                     // strip 2

        cmd.tick(sim);

        // A defender in strip 2 is *not* this squad's problem — the partition
        // is the point. If the squad's strip is empty of defenders, no
        // assignment is written even if defenders exist elsewhere.
        // (The other strip's squad — none in this test — would get the
        // assignment instead.)
        assertNull(squad.assignedObjective,
                "defender outside this squad's strip should not pull this squad off-axis");
    }

    @Test
    public void idempotentReassignmentDoesNotChurnRecord() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 5f);
        addDefender(sim, 3, 9);

        cmd.tick(sim);
        ObjectiveAssignment first = squad.assignedObjective;
        cmd.tick(sim);
        ObjectiveAssignment second = squad.assignedObjective;

        assertNotNull(first);
        assertEquals(first, second,
                "stable inputs should produce the same assignment record across ticks");
    }

    @Test
    public void deadSquadIsSkipped() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 5f);
        squad.aliveMembers = 0;   // wiped
        addDefender(sim, 3, 9);

        cmd.tick(sim);

        assertNull(squad.assignedObjective,
                "wiped squad should not receive an assignment");
        assertEquals(-1, cmd.stripIndexOf(squad.id),
                "wiped squad should not even get a strip slotted");
    }

    @Test
    public void axisFlipChangesLateralDimension() {
        // Same squad position in two different commanders — for a 30×10
        // grid, x-centroid 15 and y-centroid 5 land in different strip
        // buckets depending on which axis is lateral.
        BattleSimulation simSN = openSim();
        BattleSimulation simWE = openSim();
        ConquestCommand cmdSN = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        ConquestCommand cmdWE = new ConquestCommand(TraversalAxis.WEST_TO_EAST);

        Squad sqSN = addMarineSquad(simSN, 25f, 5f);
        Squad sqWE = addMarineSquad(simWE, 25f, 5f);
        // Need a defender to trigger anything meaningful, but only the
        // strip-classification axis matters here.
        addDefender(simSN, 25, 9);
        addDefender(simWE, 28, 5);

        cmdSN.tick(simSN);
        cmdWE.tick(simWE);

        // SOUTH_TO_NORTH lateral = x → x=25/30 falls in strip 2.
        // WEST_TO_EAST   lateral = y → y=5/10  falls in strip 1.
        assertEquals(2, cmdSN.stripIndexOf(sqSN.id), "x=25 in 30-wide lateral → strip 2");
        assertEquals(1, cmdWE.stripIndexOf(sqWE.id), "y=5 in 10-tall lateral → strip 1");
        // Sanity: confirm they're different so we know the axis actually mattered.
        assertNotEquals(cmdSN.stripIndexOf(sqSN.id), cmdWE.stripIndexOf(sqWE.id),
                "axis flip should produce different strip assignments for the same coord");
    }
}
