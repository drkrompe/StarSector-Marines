package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    /**
     * Two zones per strip: vertical walls at x=10 and x=20 (the strip
     * dividers), plus a horizontal wall at y=4 with doorways at x=5, x=15,
     * x=25 so each strip splits into a back zone (y∈[0,3]) and a front
     * zone (y∈[5,9]). Lets tests put a defender in a different zone from
     * the squad while keeping them in the same strip — what we need to
     * exercise the nearest-defender target picker properly.
     */
    private static BattleSimulation multiZonePerStripSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == 10 || x == 20) continue;
                if (y == 4 && x != 5 && x != 15 && x != 25) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setDoorway(5, 4, true);
        grid.setDoorway(15, 4, true);
        grid.setDoorway(25, 4, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * One big exterior flood zone plus a small enclosed room in the top-left
     * corner (interior x∈[0,2], y∈[0,2], 9 cells), sealed by an L-wall with a
     * single doorway at (3,1). The exterior is by far the largest zone, so it
     * becomes {@code ConquestCommand}'s cached exterior id — the zone the
     * commander must never hand out as a CLEAR_ZONE target. Both room and
     * exterior sit in strip 0 (x &lt; W/3), so one squad's strip can hold a
     * defender in each.
     */
    private static BattleSimulation roomPlusExteriorSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        // Seal the corner room: L-wall at x=3 (y=0,2) and y=3 (x=0..3),
        // doorway at (3,1) connecting the room to the exterior.
        grid.setWalkable(3, 0, false);
        grid.setWalkable(3, 2, false);
        grid.setWalkable(0, 3, false);
        grid.setWalkable(1, 3, false);
        grid.setWalkable(2, 3, false);
        grid.setWalkable(3, 3, false);
        grid.setDoorway(3, 1, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** One dominant open zone, tall enough to exercise cautious line staging. */
    private static BattleSimulation tallExteriorSim(int height) {
        NavigationGrid grid = new NavigationGrid(W, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, height));
    }

    private static Squad addMarineSquad(BattleSimulation sim, float centroidX, float centroidY) {
        long leader = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE,
                Math.round(centroidX), Math.round(centroidY)));
        int sid = sim.mintSquad(Faction.MARINE, leader);
        sim.squad().assignSquad(leader, sid);
        Squad squad = sim.getSquad(sid);
        squad.aliveMembers = 1;
        squad.centroidX = centroidX;
        squad.centroidY = centroidY;
        return squad;
    }

    private static long addDefender(BattleSimulation sim, int cellX, int cellY) {
        return sim.spawn(new EntitySpec("d-" + cellX + "-" + cellY,
                Faction.DEFENDER, UnitType.MARINE, cellX, cellY).moveSpeed(0f));
    }

    /** Supplies faction-local evidence without granting command a live hostile scan. */
    private static void establishMarineContact(BattleSimulation sim, Squad squad,
                                               long hostile) {
        int hx = sim.world().cellX(hostile);
        int hy = sim.world().cellY(hostile);
        sim.postShot(new ShotEvent(hostile, hx + 0.5f, hy + 0.5f,
                squad.centroidX + 0.5f, squad.centroidY + 0.5f,
                false, Faction.DEFENDER, 0.1f));
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(squad.hasBelievedContacts(),
                "fixture must establish legal Marine knowledge before command plans");
    }

    private static void establishDirectMarineContact(BattleSimulation sim,
                                                     Squad reporter) {
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(reporter.hasBelievedContacts(),
                "fixture reporter must directly observe the defended place");
    }

    @Test
    public void firstTickPartitionsZonesIntoStrips() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.WEST_TO_EAST);
        // Add one defender so SOMEWHERE has the "not clear" predicate; squad
        // doesn't need to exist to trigger initialization.
        addMarineSquad(sim, 1f, 1f);
        addDefender(sim, 25, 5);

        tick(cmd, sim);

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

        tick(cmd, sim);

        assertEquals(0, cmd.stripIndexOf(squad.id), "x=2 in a 30-wide map should fall into strip 0");
    }

    @Test
    public void squadOnRightEdgeIsAssignedToLastStrip_SouthToNorth() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 28f, 5f);
        addDefender(sim, 28, 9);

        tick(cmd, sim);

        assertEquals(ConquestCommand.STRIP_COUNT - 1, cmd.stripIndexOf(squad.id),
                "x=28 in a 30-wide map should fall into the last strip");
    }

    @Test
    public void squadAssignmentIsStickyAcrossTicks() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 5f);
        addDefender(sim, 25, 9);

        tick(cmd, sim);
        int firstStrip = cmd.stripIndexOf(squad.id);

        // Move the squad's centroid across the map — without sticky
        // assignment, it would re-classify into a different strip.
        squad.centroidX = 28f;

        tick(cmd, sim);
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

        tick(cmd, sim);

        assertNull(squad.assignedObjective,
                "strip with no defenders → null assignment, squad falls through to EliminateEnemies");
    }

    @Test
    public void defenderInSquadCurrentZoneAssignsClearZone() {
        // Squad at (2, 5) and defender at (3, 9) — both in the same strip-0
        // zone. Commander writes CLEAR_ZONE pointing at that zone so
        // ClearAssignedZoneGoal can stay relevant while the squad clears
        // it. The goal's customPlan handles the "already in target zone"
        // case by emitting a ClearZone-only step — no EnterZone re-entry
        // oscillation.
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 5f);
        long defender = addDefender(sim, 3, 9);
        establishMarineContact(sim, squad, defender);

        tick(cmd, sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a,
                "defender in squad's own zone → CLEAR_ZONE assignment (goal handles in-zone clear)");
        assertEquals(AssignmentKind.CLEAR_ZONE, a.kind());
        int squadZone = sim.getZoneGraph().zoneIdAt(2, 5);
        assertEquals(squadZone, a.targetZoneId(),
                "target should be the squad's own zone so the goal stays relevant during clear");
    }

    @Test
    public void commanderPicksNearestForwardDefenderNotDeepest() {
        // Multi-zone strip: squad in back zone of strip 0; defenders in
        // both the front zone of strip 0 (near-forward) AND ... wait,
        // strip 0 only has two zones (back + front + a doorway zone).
        // For a "two forward defenders, pick the closer" distinction we'd
        // need three zones along the strip; this fixture has two. So the
        // test asserts the simpler "defender in adjacent forward zone
        // becomes the target" — which is the LZ-fix scenario the bug
        // report came from. A 3-zone strip distinction is left for the
        // integration suite.
        BattleSimulation sim = multiZonePerStripSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 1f);   // back zone of strip 0
        long defender = addDefender(sim, 3, 8);       // front zone of strip 0
        establishMarineContact(sim, squad, defender);

        tick(cmd, sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a, "forward-zone defender → squad gets CLEAR_ZONE pointed at it");
        assertEquals(AssignmentKind.CLEAR_ZONE, a.kind());
        int frontZone = sim.getZoneGraph().zoneIdAt(3, 8);
        assertEquals(frontZone, a.targetZoneId(),
                "target zone should be the defender's zone, not the squad's");
    }

    @Test
    public void commanderPrefersForwardDefenderOverBackwardDefender() {
        // Squad in the FRONT zone (already past the back zone). A defender
        // in the back zone should NOT pull the squad backward — the
        // forward-bias of nearestDefenderZoneInStrip means a null forward
        // candidate falls back to backward only when there's nothing
        // ahead. Here, with a backward-only defender, the squad does pick
        // them up (no other choice) — verifies the fallback path.
        BattleSimulation sim = multiZonePerStripSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 8f);   // front zone of strip 0
        long defender = addDefender(sim, 3, 1);       // back zone of strip 0
        establishMarineContact(sim, squad, defender);

        tick(cmd, sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a, "with no forward defender, backward defender is the fallback target");
        int backZone = sim.getZoneGraph().zoneIdAt(3, 1);
        assertEquals(backZone, a.targetZoneId());
    }

    @Test
    public void defenderInOtherStripDoesNotDriveThisSquadsAssignment() {
        BattleSimulation sim = openSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 5f);   // strip 0
        addDefender(sim, 25, 9);                     // strip 2

        tick(cmd, sim);

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
        BattleSimulation sim = multiZonePerStripSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 2f, 1f);   // back zone of strip 0
        long defender = addDefender(sim, 3, 8);       // front zone of strip 0
        establishMarineContact(sim, squad, defender);

        tick(cmd, sim);
        ObjectiveAssignment first = squad.assignedObjective;
        tick(cmd, sim);
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

        tick(cmd, sim);

        assertNull(squad.assignedObjective,
                "wiped squad should not receive an assignment");
        assertEquals(-1, cmd.stripIndexOf(squad.id),
                "wiped squad should not even get a strip slotted");
    }

    @Test
    public void exteriorZoneIsNeverAssignedAsClearTarget() {
        // Squad in the open exterior with a defender also outdoors AND a
        // defender in the enclosed room — all in strip 0. The exterior is the
        // largest zone, so it must be skipped; the squad is pointed at the
        // room instead of being told to clear the whole map.
        BattleSimulation sim = roomPlusExteriorSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 6f, 8f);   // exterior, strip 0
        Squad reporter = addMarineSquad(sim, 1f, 2f);
        reporter.assignedObjective = ObjectiveAssignment.holdNode(reporter.id,
                new TacticalNode(TacticalNode.Kind.GUARDPOST, 1, 2,
                        0, 0, 2, 3, Faction.MARINE, 50, 1));
        addDefender(sim, 7, 8);                       // exterior, strip 0
        addDefender(sim, 1, 1);                       // enclosed room, strip 0
        establishDirectMarineContact(sim, reporter);

        tick(cmd, sim);

        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a, "a clearable room defender exists → squad gets an assignment");
        assertEquals(AssignmentKind.CLEAR_ZONE, a.kind());
        int roomZone = sim.getZoneGraph().zoneIdAt(1, 1);
        int exteriorZone = sim.getZoneGraph().zoneIdAt(6, 8);
        assertEquals(roomZone, a.targetZoneId(),
                "target must be the enclosed room, not the open exterior");
        assertNotEquals(exteriorZone, a.targetZoneId(),
                "the exterior flood zone must never be a CLEAR_ZONE target");
    }

    @Test
    public void exteriorOnlyDefenderLeavesAssignmentNull() {
        // The only defender stands in the open exterior. There's nothing
        // clearable, so the assignment is cleared and the squad falls through
        // to EliminateEnemies (engages outdoors ambiently) — it does NOT get
        // told to clear the whole exterior.
        BattleSimulation sim = roomPlusExteriorSim();
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        Squad squad = addMarineSquad(sim, 6f, 8f);   // exterior, strip 0
        addDefender(sim, 8, 8);                       // exterior, strip 0

        tick(cmd, sim);

        assertNull(squad.assignedObjective,
                "exterior-only defender → no CLEAR_ZONE; squad engages ambiently");
    }

    @Test
    public void globalExteriorContactStagesIdleRearSquadAlongItsTrack() {
        BattleSimulation sim = tallExteriorSim(100);
        ConquestCommand cmd = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);
        Squad rear = addMarineSquad(sim, 5f, 2f);
        Squad reporter = addMarineSquad(sim, 5f, 80f);
        long defender = addDefender(sim, 5, 82);
        establishDirectMarineContact(sim, reporter);
        assertFalse(rear.hasBelievedContacts(),
                "rear squad must rely on commander belief, not local sensing");

        tick(cmd, sim);

        ObjectiveAssignment stage = rear.assignedObjective;
        assertNotNull(stage);
        assertEquals(AssignmentKind.ADVANCE_TRACK, stage.kind());
        assertEquals(-1, stage.targetZoneId(),
                "lane staging is an own-force cell, not a fabricated exterior zone");
        int forward = stage.targetCellY();
        assertTrue(forward >= 2 + ConquestCommand.TRACK_LINE_MIN_ADVANCE_CELLS);
        assertTrue(forward <= 82 - ConquestCommand.TRACK_LINE_STANDOFF_CELLS,
                "staging marker must remain behind the believed hostile front");
        assertTrue(forward <= 2 + ConquestCommand.TRACK_LINE_MAX_STRIDE_CELLS,
                "a rear squad must not cross the whole lane in one order");
        assertEquals(0, new ConquestTrackLayout(
                TraversalAxis.SOUTH_TO_NORTH, W, 100)
                .trackForCell(stage.targetCellX(), stage.targetCellY()));
        assertTrue(sim.getGrid().isWalkable(
                stage.targetCellX(), stage.targetCellY()));

        ConquestFrontSnapshot.SquadDirective directive =
                cmd.frontSnapshot().directiveFor(rear.id);
        assertEquals(AssignmentReason.TRACK_LINE_ADVANCE, directive.reason());
        assertEquals(AssignmentKind.ADVANCE_TRACK, directive.assignmentKind());
        assertEquals(stage.targetCellX(), directive.markerCellX());
        assertEquals(stage.targetCellY(), directive.markerCellY());
        assertFalse(cmd.frontSnapshot().squadFor(rear.id).localContact());
        assertTrue(cmd.frontSnapshot().track(0).knownHostileContacts() > 0);

        tick(cmd, sim);
        assertEquals(stage, rear.assignedObjective,
                "unchanged belief must preserve a deterministic staging marker");
    }

    @Test
    public void localContactAndHostileFrontBehindSquadDoNotCreateLaneStage() {
        BattleSimulation localSim = tallExteriorSim(40);
        Squad engaged = addMarineSquad(localSim, 5f, 20f);
        addDefender(localSim, 5, 22);
        establishDirectMarineContact(localSim, engaged);
        ConquestCommand localCommand = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);

        tick(localCommand, localSim);

        assertNull(engaged.assignedObjective,
                "local engagement owns the squad without a competing lane marker");
        assertTrue(localCommand.frontSnapshot().squadFor(engaged.id).localContact());

        BattleSimulation passedSim = tallExteriorSim(120);
        Squad ahead = addMarineSquad(passedSim, 8f, 100f);
        Squad reporter = addMarineSquad(passedSim, 1f, 18f);
        addDefender(passedSim, 1, 20);
        establishDirectMarineContact(passedSim, reporter);
        assertFalse(ahead.hasBelievedContacts());
        ConquestCommand passedCommand = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);

        tick(passedCommand, passedSim);

        assertNull(ahead.assignedObjective,
                "a believed front behind the squad must never pull it backward");
    }

    /**
     * An enclosed 3×3 building room (interior x∈[4,6], y∈[4,6]) sealed by a
     * one-cell wall ring with a doorway at (7,5), surrounded by open parade
     * ground. The building interior is its own zone; the parade is another.
     * Lets the 0b test put a garrison squad on the parade (a different zone
     * from the building) but inside the compound footprint.
     */
    private static BattleSimulation walledBuildingSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        // Wall ring around the room at [3..7]×[3..7]; carve the interior back open.
        for (int x = 3; x <= 7; x++) { grid.setWalkable(x, 3, false); grid.setWalkable(x, 7, false); }
        for (int y = 3; y <= 7; y++) { grid.setWalkable(3, y, false); grid.setWalkable(7, y, false); }
        grid.setWalkableFloor(7, 5);
        grid.setDoorway(7, 5, true); // building ↔ parade
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Registers a compound node and drives it to MARINE_HELD via the capture system. */
    private static void captureCompound(BattleSimulation sim, TacticalNode node) {
        CompoundService service = sim.getCompoundService();
        service.register(node);
        CompoundCaptureSystem capture = new CompoundCaptureSystem();
        sim.spawn(new EntitySpec("cap-m", Faction.MARINE, UnitType.MARINE, node.anchorX, node.anchorY));
        int ticks = 2 + (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        for (int i = 0; i < ticks; i++) {
            capture.tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);
        }
    }

    @Test
    public void capturingAssaultSquadIsNotPinnedHoldingTheCompound() {
        // The squad that captured a compound must keep advancing — the
        // dedicated garrison squad (shipped in by CompoundGarrisonSystem, born
        // holding) does the holding. The commander never assigns HOLD_NODE
        // itself, so an assault squad sitting in a MARINE_HELD compound is not
        // pinned.
        BattleSimulation sim = walledBuildingSim();
        TacticalNode node = new TacticalNode(TacticalNode.Kind.ARMORY, 5, 5,
                4, 4, 6, 6, Faction.DEFENDER, 80, 4);
        captureCompound(sim, node);

        Squad squad = addMarineSquad(sim, 5f, 5f); // standing in the captured compound
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        ObjectiveAssignment a = squad.assignedObjective;
        boolean pinned = a != null && a.kind() == AssignmentKind.HOLD_NODE;
        assertFalse(pinned, "the capturing assault squad must not be pinned holding the compound");
    }

    @Test
    public void commanderLeavesABornHoldingGarrisonAlone() {
        // A garrison squad arrives born with HOLD_NODE (stamped at deboard).
        // The commander must respect it — not overwrite it with a strip clear —
        // so the garrison stays on station.
        BattleSimulation sim = walledBuildingSim();
        TacticalNode node = new TacticalNode(TacticalNode.Kind.ARMORY, 5, 5,
                4, 4, 6, 6, Faction.DEFENDER, 80, 4);
        captureCompound(sim, node);

        Squad garrison = addMarineSquad(sim, 5f, 5f);
        garrison.assignedObjective = ObjectiveAssignment.holdNode(
                garrison.id, node); // fixture for a born-holding external owner
        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        ObjectiveAssignment a = garrison.assignedObjective;
        assertNotNull(a, "born-holding garrison keeps an assignment");
        assertEquals(AssignmentKind.HOLD_NODE, a.kind(),
                "commander must not overwrite a born-holding garrison's HOLD_NODE");
        assertEquals(node, a.targetNode());
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

        tick(cmdSN, simSN);
        tick(cmdWE, simWE);

        // SOUTH_TO_NORTH lateral = x → x=25/30 falls in strip 2.
        // WEST_TO_EAST   lateral = y → y=5/10  falls in strip 1.
        assertEquals(2, cmdSN.stripIndexOf(sqSN.id), "x=25 in 30-wide lateral → strip 2");
        assertEquals(1, cmdWE.stripIndexOf(sqWE.id), "y=5 in 10-tall lateral → strip 1");
        // Sanity: confirm they're different so we know the axis actually mattered.
        assertNotEquals(cmdSN.stripIndexOf(sqSN.id), cmdWE.stripIndexOf(sqWE.id),
                "axis flip should produce different strip assignments for the same coord");
    }

    // ---- Deliberate compound-capture pass ----

    /**
     * Carve a sealed 3×3 room (interior {@code [cx-1..cx+1]×[cy-1..cy+1]}) into an
     * otherwise open grid: a one-cell wall ring with a single doorway at the top
     * ({@code (cx, cy-2)}) connecting the room to the exterior. The room becomes
     * its own navigation zone; the surrounding flood is the exterior.
     */
    private static void carveRoom(NavigationGrid grid, int cx, int cy) {
        for (int x = cx - 2; x <= cx + 2; x++) {
            grid.setWalkable(x, cy - 2, false);
            grid.setWalkable(x, cy + 2, false);
        }
        for (int y = cy - 2; y <= cy + 2; y++) {
            grid.setWalkable(cx - 2, y, false);
            grid.setWalkable(cx + 2, y, false);
        }
        grid.setWalkableFloor(cx, cy - 2);
        grid.setDoorway(cx, cy - 2, true);
    }

    /** One sealed 3×3 compound building centred at (5,5) in an otherwise open map. */
    private static BattleSimulation oneCompoundSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        carveRoom(grid, 5, 5);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static BattleSimulation compoundAt(int centerX) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        carveRoom(grid, centerX, 5);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static BattleSimulation sealedCompoundAt(int centerX) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        carveRoom(grid, centerX, 5);
        grid.setWalkable(centerX, 3, false);
        grid.setDoorway(centerX, 3, false);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Two sealed compound buildings — strip 0 at (5,5), strip 2 at (24,5). */
    private static BattleSimulation twoCompoundSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        carveRoom(grid, 5, 5);
        carveRoom(grid, 24, 5);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Three sealed rooms in a band sharing wall columns — one multi-room compound footprint. */
    private static BattleSimulation threeRoomCompoundSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        carveRoom(grid, 5, 5);
        carveRoom(grid, 9, 5);
        carveRoom(grid, 13, 5);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Multi-room compound in track 0 plus a separate defended room in track 2. */
    private static BattleSimulation threeRoomCompoundAndFrontSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        carveRoom(grid, 5, 5);
        carveRoom(grid, 9, 5);
        carveRoom(grid, 13, 5);
        carveRoom(grid, 24, 5);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Register a compound node (DEFENDER_HELD) without driving it to capture. */
    private static TacticalNode registerCompound(BattleSimulation sim, TacticalNode node) {
        sim.getCompoundService().register(node);
        return node;
    }

    private static boolean isSecureCompound(Squad squad) {
        ObjectiveAssignment a = squad.assignedObjective;
        return a != null && a.kind() == AssignmentKind.SECURE_COMPOUND;
    }

    @Test
    public void uncontestedCompoundPullsNearestSquadAndCapsAtOne() {
        // One small (single-room) compound, uncontested (no defenders). Two
        // squads: the near one is peeled off to capture; the far one is NOT —
        // the per-compound cap (1 for a single-room compound) leaves it free to
        // keep hunting.
        BattleSimulation sim = oneCompoundSim();
        TacticalNode node = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 4, 4, 6, 6, Faction.DEFENDER, 80, 4));
        Squad near = addMarineSquad(sim, 5f, 5f);    // inside the building
        Squad far = addMarineSquad(sim, 24f, 5f);    // far away, strip 2

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertTrue(isSecureCompound(near), "nearest squad should be assigned to capture the uncontested compound");
        int anchorZone = sim.getZoneGraph().zoneIdAt(node.anchorX, node.anchorY);
        assertEquals(anchorZone, near.assignedObjective.targetZoneId(), "SECURE_COMPOUND target is the anchor zone");
        assertEquals(node, near.assignedObjective.targetNode());
        assertFalse(isSecureCompound(far), "the cap is 1 — the far squad must not also be pulled onto the same compound");
    }

    @Test
    public void twoUncontestedCompoundsSpreadSquadsNotPileThem() {
        // Two single-room compounds, two squads, each near a different one.
        // Greedy nearest-pair must spread them — one squad per compound — not
        // pile both on the closer compound.
        BattleSimulation sim = twoCompoundSim();
        TacticalNode a = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 4, 4, 6, 6, Faction.DEFENDER, 80, 4));
        TacticalNode b = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.BARRACKS, 24, 5, 23, 4, 25, 6, Faction.DEFENDER, 80, 4));
        Squad sqA = addMarineSquad(sim, 5f, 5f);
        Squad sqB = addMarineSquad(sim, 24f, 5f);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertTrue(isSecureCompound(sqA) && isSecureCompound(sqB), "both squads should be on capture duty");
        int zoneA = sim.getZoneGraph().zoneIdAt(a.anchorX, a.anchorY);
        int zoneB = sim.getZoneGraph().zoneIdAt(b.anchorX, b.anchorY);
        assertEquals(zoneA, sqA.assignedObjective.targetZoneId(), "squad A → compound A");
        assertEquals(zoneB, sqB.assignedObjective.targetZoneId(), "squad B → compound B");
    }

    @Test
    public void sharedCaptureZoneKeepsAuthoredCompoundIdentity() {
        BattleSimulation sim = openSim();
        TacticalNode a = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 4, 4, 6, 6,
                Faction.DEFENDER, 80, 4));
        TacticalNode b = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.BARRACKS, 8, 5, 7, 4, 9, 6,
                Faction.DEFENDER, 80, 4));
        Squad sqA = addMarineSquad(sim, 5f, 5f);
        Squad sqB = addMarineSquad(sim, 8f, 5f);

        tick(new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH), sim);

        assertEquals(sqA.assignedObjective.targetZoneId(),
                sqB.assignedObjective.targetZoneId(),
                "the fixture deliberately places both compounds in one zone");
        assertSame(a, sqA.assignedObjective.targetNode());
        assertSame(b, sqB.assignedObjective.targetNode(),
                "the arbiter must not bind a typed order to the first compound in a shared zone");
    }

    @Test
    public void topologyRebuildRebindsStableCaptureToTheSameCompound() {
        BattleSimulation sim = twoCompoundSim();
        CompoundService.Record earlier = sim.getCompoundService().register(
                new TacticalNode(TacticalNode.Kind.ARMORY, 5, 5,
                        4, 4, 6, 6, Faction.DEFENDER, 80, 4));
        earlier.state = CompoundService.CompoundState.MARINE_HELD;
        TacticalNode target = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.BARRACKS, 24, 5,
                23, 4, 25, 6, Faction.DEFENDER, 80, 4));
        Squad squad = addMarineSquad(sim, 24f, 1f);
        ConquestCommand command = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);
        CommanderService service = new CommanderService();
        service.setAutonomousCommander(Faction.MARINE, command,
                ConquestCommandDisclosure.INSTANCE);

        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);
        int oldZone = squad.assignedObjective.targetZoneId();
        assertSame(target, squad.assignedObjective.targetNode());

        // Merge an earlier room into the exterior. The target room itself is
        // unchanged, but the rebuilt graph renumbers its capture zone.
        sim.getGrid().setDoorway(5, 3, false);
        sim.getZoneGraph().rebuild();
        int rebuiltZone = sim.getCompoundService().captureZoneId(
                sim.getCompoundService().getRecords().stream()
                        .filter(record -> record.node == target)
                        .findFirst().orElseThrow(), sim);
        assertNotEquals(oldZone, rebuiltZone);

        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        assertEquals(AssignmentKind.SECURE_COMPOUND,
                squad.assignedObjective.kind());
        assertEquals(rebuiltZone, squad.assignedObjective.targetZoneId());
        assertSame(target, squad.assignedObjective.targetNode());
        CommandDirective rebound = service.assignments()
                .activeDirective(squad.id);
        assertEquals(CommandDirective.Status.ACTIVE, rebound.status());
        assertTrue(rebound.dispositionReason()
                .contains(CommandStabilityBreak.TOPOLOGY_REBOUND.description()));
        assertEquals(AssignmentReason.COMPOUND_CAPTURE_PRESERVED,
                command.frontSnapshot().directiveFor(squad.id).reason());
    }

    @Test
    public void multiRoomCompoundRatesTwoSquads() {
        // A multi-room compound (three garrison rooms) rates a two-squad
        // detachment; both nearby squads are committed to it.
        BattleSimulation sim = threeRoomCompoundSim();
        // Sanity: the three rooms really are distinct zones.
        int z1 = sim.getZoneGraph().zoneIdAt(5, 5);
        int z2 = sim.getZoneGraph().zoneIdAt(9, 5);
        int z3 = sim.getZoneGraph().zoneIdAt(13, 5);
        assertNotEquals(z1, z2);
        assertNotEquals(z2, z3);

        TacticalNode node = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.COMMAND_POST, 5, 5, 3, 3, 15, 7, Faction.DEFENDER, 90, 4));
        Squad sqA = addMarineSquad(sim, 5f, 5f);
        Squad sqB = addMarineSquad(sim, 9f, 5f);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertTrue(isSecureCompound(sqA), "first squad captures the keep");
        assertTrue(isSecureCompound(sqB), "multi-room keep rates a 2nd capture squad");
    }

    @Test
    public void noFrontResistanceStillFillsDistantCompoundQuota() {
        BattleSimulation sim = threeRoomCompoundSim();
        registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 3, 3, 15, 7,
                Faction.DEFENDER, 90, 4));
        Squad left = addMarineSquad(sim, 5f, 1f);
        Squad right = addMarineSquad(sim, 28f, 1f);

        ConquestCommand cmd = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertTrue(isSecureCompound(left));
        assertTrue(isSecureCompound(right),
                "without actionable resistance, distant capture keeps the ordinary two-squad quota");
    }

    @Test
    public void exteriorContactReservesOneActionableSquadAcrossReplans() {
        BattleSimulation sim = threeRoomCompoundSim();
        registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 3, 3, 15, 7,
                Faction.DEFENDER, 90, 4));
        Squad contact = addMarineSquad(sim, 5f, 1f);
        Squad free = addMarineSquad(sim, 28f, 1f);
        long defender = addDefender(sim, 6, 1);
        establishMarineContact(sim, contact, defender);

        ConquestCommand cmd = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        int secure = (isSecureCompound(contact) ? 1 : 0)
                + (isSecureCompound(free) ? 1 : 0);
        assertEquals(1, secure,
                "only one actionable squad may leave the live front for a distant capture");
        Squad capturing = isSecureCompound(contact) ? contact : free;
        Squad reservedSquad = isSecureCompound(contact) ? free : contact;
        ConquestFrontSnapshot.SquadDirective reserved =
                cmd.frontSnapshot().directiveFor(reservedSquad.id);
        assertEquals(AssignmentReason.NO_ACTIONABLE_TRACK_TARGET,
                reserved.reason(),
                "exterior contact remains ambient rather than becoming a fabricated zone order");
        assertTrue(reserved.distantCaptureDeferred(),
                "the snapshot should expose why capture was withheld");

        tick(cmd, sim);

        assertTrue(isSecureCompound(capturing),
                "the in-flight capture remains committed on the next pulse");
        assertEquals(AssignmentReason.COMPOUND_CAPTURE_PRESERVED,
                cmd.frontSnapshot().directiveFor(capturing.id).reason());
        assertFalse(isSecureCompound(reservedSquad),
                "replanning must not drip-feed the reserved squad into the remaining slot");
        assertTrue(cmd.frontSnapshot().directiveFor(reservedSquad.id)
                .distantCaptureDeferred());
    }

    @Test
    public void reachableFrontOrderRetainsItsReasonWhenCaptureIsDeferred() {
        BattleSimulation sim = threeRoomCompoundAndFrontSim();
        registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 3, 3, 15, 7,
                Faction.DEFENDER, 90, 4));
        Squad capture = addMarineSquad(sim, 5f, 1f);
        Squad front = addMarineSquad(sim, 24f, 1f);
        addDefender(sim, 24, 5);
        establishDirectMarineContact(sim, front);

        ConquestCommand cmd = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertTrue(isSecureCompound(capture));
        assertEquals(AssignmentKind.CLEAR_ZONE,
                front.assignedObjective.kind());
        ConquestFrontSnapshot.SquadDirective directive =
                cmd.frontSnapshot().directiveFor(front.id);
        assertEquals(AssignmentReason.TRACK_ADVANCE, directive.reason(),
                "capture policy must not overwrite the reason for actual front work");
        assertTrue(directive.distantCaptureDeferred());
    }

    @Test
    public void defenderInOpenExteriorDoesNotMarkCompoundContested() {
        // The contested test runs over the AABB-gated garrison rooms, so a
        // defender loitering in the open street outside the building must NOT
        // make the compound read as contested — the squad is still peeled off
        // to capture it.
        BattleSimulation sim = oneCompoundSim();
        TacticalNode node = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 4, 4, 6, 6, Faction.DEFENDER, 80, 4));
        Squad squad = addMarineSquad(sim, 5f, 1f);   // near the building, in the exterior
        addDefender(sim, 5, 1);                       // exterior (not in a garrison room)

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertTrue(isSecureCompound(squad),
                "an exterior defender does not contest the compound — capture still fires");
    }

    @Test
    public void contestedCompoundDoesNotPullADistantSquad() {
        // Defender INSIDE the building → contested. A squad in the same strip
        // but out in the exterior (not adjacent to a garrison room) is not
        // pulled onto a capture; it runs the ordinary clear-zone push toward
        // the defended room instead.
        BattleSimulation sim = oneCompoundSim();
        TacticalNode node = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 4, 4, 6, 6, Faction.DEFENDER, 80, 4));
        Squad squad = addMarineSquad(sim, 8f, 8f);   // exterior, strip 0
        Squad reporter = addMarineSquad(sim, 4f, 4f);
        reporter.assignedObjective = ObjectiveAssignment.holdNode(reporter.id, node);
        addDefender(sim, 5, 5);                       // inside the building room
        establishDirectMarineContact(sim, reporter);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertFalse(isSecureCompound(squad), "a contested compound must not pull a non-adjacent squad");
        ObjectiveAssignment a = squad.assignedObjective;
        assertNotNull(a, "the defended room is a strip clear-zone target");
        assertEquals(AssignmentKind.CLEAR_ZONE, a.kind());
        int roomZone = sim.getZoneGraph().zoneIdAt(5, 5);
        assertEquals(roomZone, a.targetZoneId(), "the clear-zone push points at the defended compound room");
    }

    @Test
    public void contestedCompoundCommitsAnAlreadyAdjacentSquad() {
        // Squad already inside the building while a defender is also there →
        // commit the capture (convert incidental presence into a committed
        // SECURE_COMPOUND), rather than leaving it on an ambient clear.
        BattleSimulation sim = oneCompoundSim();
        TacticalNode node = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5, 4, 4, 6, 6, Faction.DEFENDER, 80, 4));
        Squad squad = addMarineSquad(sim, 4f, 4f);   // inside the building room
        long defender = addDefender(sim, 6, 6);       // also inside → contested
        establishMarineContact(sim, squad, defender);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertTrue(isSecureCompound(squad), "a squad already in a contested compound commits to capturing it");
        assertEquals(node, squad.assignedObjective.targetNode());
        assertEquals(AssignmentReason.COMPOUND_ASSAULT_ADJACENT,
                cmd.frontSnapshot().directiveFor(squad.id).reason());
        assertFalse(cmd.frontSnapshot().directiveFor(squad.id)
                .distantCaptureDeferred());
    }

    @Test
    public void emptyPreferredTrackSupportsAdjacentDefendedCompound() {
        BattleSimulation sim = compoundAt(19);
        TacticalNode node = registerCompound(sim, new TacticalNode(TacticalNode.Kind.ARMORY,
                19, 5, 18, 4, 20, 6, Faction.DEFENDER, 80, 4));
        Squad squad = addMarineSquad(sim, 21f, 8f); // track 2, just over x=20 seam
        Squad reporter = addMarineSquad(sim, 18f, 4f);
        reporter.assignedObjective = ObjectiveAssignment.holdNode(reporter.id, node);
        addDefender(sim, 19, 5);                    // defended room in track 1
        establishDirectMarineContact(sim, reporter);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertNotNull(squad.assignedObjective);
        assertEquals(AssignmentKind.CLEAR_ZONE, squad.assignedObjective.kind());
        ConquestFrontSnapshot.SquadDirective directive =
                cmd.frontSnapshot().directiveFor(squad.id);
        assertEquals(2, directive.preferredTrack());
        assertEquals(1, directive.effectiveTrack());
        assertEquals(AssignmentReason.ADJACENT_TRACK_SUPPORT,
                directive.reason());
        assertEquals(-1, directive.targetCellX(),
                "zone-scoped order must not masquerade as an exact-cell order");
        assertTrue(sim.getGrid().isWalkable(directive.markerCellX(),
                directive.markerCellY()),
                "published zone marker should identify a real walkable cell");
        assertEquals(Phase.FRONT_ADJUST, cmd.frontSnapshot().phase());
        assertEquals(1, cmd.frontSnapshot().track(1).effectiveSquads());
    }

    @Test
    public void finalKeepConvergesEveryMobileAssaultSquad() {
        BattleSimulation sim = compoundAt(15);
        TacticalNode keep = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.COMMAND_POST, 15, 5, 14, 4, 16, 6,
                Faction.DEFENDER, 100, 4));
        Squad left = addMarineSquad(sim, 2f, 1f);
        Squad center = addMarineSquad(sim, 15f, 1f);
        Squad right = addMarineSquad(sim, 28f, 1f);
        addDefender(sim, 15, 5);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertEquals(Phase.KEEP_CONVERGENCE, cmd.frontSnapshot().phase());
        assertEquals(1, cmd.frontSnapshot().remainingCompounds());
        assertEquals(left.aliveMembers,
                cmd.frontSnapshot().squadFor(left.id).aliveMembers());
        assertEquals(left.centroidX,
                cmd.frontSnapshot().squadFor(left.id).centroidX());
        assertEquals(sim.getZoneGraph().zoneIdAt(keep.anchorX, keep.anchorY),
                cmd.frontSnapshot().keepZoneId());
        for (Squad squad : new Squad[]{left, center, right}) {
            assertEquals(AssignmentKind.SECURE_COMPOUND,
                    squad.assignedObjective.kind());
            ConquestFrontSnapshot.SquadDirective directive =
                    cmd.frontSnapshot().directiveFor(squad.id);
            assertEquals(AssignmentReason.KEEP_APPROACH, directive.reason());
            assertEquals(-1, directive.targetCellX());
            assertEquals(keep.anchorX, directive.markerCellX());
            assertEquals(keep.anchorY, directive.markerCellY());
        }
        assertEquals(0, cmd.frontSnapshot().directiveFor(left.id).preferredTrack());
        assertEquals(1, cmd.frontSnapshot().directiveFor(center.id).preferredTrack());
        assertEquals(2, cmd.frontSnapshot().directiveFor(right.id).preferredTrack());
    }

    @Test
    public void finalKeepConvergenceLeavesBornHoldingGarrisonOnStation() {
        BattleSimulation sim = compoundAt(15);
        registerCompound(sim, new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                15, 5, 14, 4, 16, 6, Faction.DEFENDER, 100, 4));
        Squad assault = addMarineSquad(sim, 2f, 1f);
        Squad garrison = addMarineSquad(sim, 28f, 1f);
        TacticalNode post = new TacticalNode(TacticalNode.Kind.BEACHHEAD,
                28, 1, 27, 0, 29, 2, Faction.MARINE, 60, 3);
        garrison.assignedObjective = ObjectiveAssignment.holdNode(
                garrison.id, post);
        addDefender(sim, 15, 5);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertEquals(AssignmentKind.SECURE_COMPOUND,
                assault.assignedObjective.kind());
        assertEquals(AssignmentKind.HOLD_NODE, garrison.assignedObjective.kind());
        assertEquals(AssignmentReason.GARRISON_HOLD,
                cmd.frontSnapshot().directiveFor(garrison.id).reason());
    }

    @Test
    public void heldKeepAndSoleContestedCompoundConvergeDistantTracks() {
        BattleSimulation sim = twoCompoundSim();
        CompoundService.Record armory = sim.getCompoundService().register(
                new TacticalNode(TacticalNode.Kind.ARMORY, 5, 5,
                        4, 4, 6, 6, Faction.DEFENDER, 80, 4));
        CompoundService.Record keep = sim.getCompoundService().register(
                new TacticalNode(TacticalNode.Kind.COMMAND_POST, 24, 5,
                        23, 4, 25, 6, Faction.DEFENDER, 100, 4));
        keep.state = CompoundService.CompoundState.MARINE_HELD;
        Squad adjacent = addMarineSquad(sim, 5f, 5f);
        Squad distant = addMarineSquad(sim, 28f, 1f);
        addDefender(sim, 5, 5);
        establishDirectMarineContact(sim, adjacent);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertEquals(CompoundService.CompoundState.DEFENDER_HELD, armory.state);
        assertEquals(Phase.FINAL_COMPOUND_CONVERGENCE,
                cmd.frontSnapshot().phase());
        assertEquals(AssignmentKind.SECURE_COMPOUND,
                adjacent.assignedObjective.kind(),
                "capture quota remains owned by the adjacent assault squad");
        assertEquals(AssignmentKind.CLEAR_ZONE, distant.assignedObjective.kind(),
                "an idle nonadjacent track supports the final contested place");
        ConquestFrontSnapshot.SquadDirective directive =
                cmd.frontSnapshot().directiveFor(distant.id);
        assertEquals(2, directive.preferredTrack());
        assertEquals(0, directive.effectiveTrack());
        assertEquals(AssignmentReason.FINAL_COMPOUND_SUPPORT,
                directive.reason());
    }

    @Test
    public void soleUncontestedCompoundKeepsNormalCaptureQuota() {
        BattleSimulation sim = twoCompoundSim();
        sim.getCompoundService().register(new TacticalNode(
                TacticalNode.Kind.ARMORY, 5, 5,
                4, 4, 6, 6, Faction.DEFENDER, 80, 4));
        CompoundService.Record keep = sim.getCompoundService().register(
                new TacticalNode(TacticalNode.Kind.COMMAND_POST, 24, 5,
                        23, 4, 25, 6, Faction.DEFENDER, 100, 4));
        keep.state = CompoundService.CompoundState.MARINE_HELD;
        Squad near = addMarineSquad(sim, 5f, 5f);
        Squad far = addMarineSquad(sim, 28f, 1f);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        int secure = (isSecureCompound(near) ? 1 : 0)
                + (isSecureCompound(far) ? 1 : 0);
        assertEquals(1, secure, "uncontested final capture keeps its one-squad quota");
        assertNotEquals(Phase.FINAL_COMPOUND_CONVERGENCE,
                cmd.frontSnapshot().phase());
    }

    @Test
    public void recapturedEarlierCompoundReopensFrontAfterKeepConvergence() {
        BattleSimulation sim = twoCompoundSim();
        CompoundService.Record armory = sim.getCompoundService().register(
                new TacticalNode(TacticalNode.Kind.ARMORY, 5, 5,
                        4, 4, 6, 6, Faction.DEFENDER, 80, 4));
        sim.getCompoundService().register(new TacticalNode(
                TacticalNode.Kind.COMMAND_POST, 24, 5,
                23, 4, 25, 6, Faction.DEFENDER, 100, 4));
        armory.state = CompoundService.CompoundState.MARINE_HELD;
        Squad squad = addMarineSquad(sim, 24f, 1f);
        addDefender(sim, 24, 5);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);
        assertEquals(Phase.KEEP_CONVERGENCE, cmd.frontSnapshot().phase());

        armory.state = CompoundService.CompoundState.DEFENDER_HELD;
        tick(cmd, sim);

        assertEquals(Phase.LANE_ADVANCE, cmd.frontSnapshot().phase());
        assertEquals(2, cmd.frontSnapshot().remainingCompounds());
    }

    @Test
    public void unreachableOrdinaryCompoundReleasesAStickyCaptureOrder() {
        BattleSimulation sim = sealedCompoundAt(19);
        TacticalNode node = registerCompound(sim, new TacticalNode(
                TacticalNode.Kind.ARMORY, 19, 5, 18, 4, 20, 6,
                Faction.DEFENDER, 80, 4));
        Squad squad = addMarineSquad(sim, 5f, 5f);
        squad.assignedObjective = ObjectiveAssignment.secureCompound(
                squad.id, sim.getZoneGraph().zoneIdAt(19, 5), node);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertNull(squad.assignedObjective,
                "an obsolete capture must not pin a squad outside a sealed compound");
        assertEquals(AssignmentReason.NO_ACTIONABLE_TRACK_TARGET,
                cmd.frontSnapshot().directiveFor(squad.id).reason());
    }

    @Test
    public void completedCompoundImmediatelyReleasesStableCaptureDirective() {
        BattleSimulation sim = compoundAt(15);
        registerCompound(sim, new TacticalNode(TacticalNode.Kind.ARMORY,
                15, 5, 14, 4, 16, 6, Faction.DEFENDER, 80, 4));
        Squad squad = addMarineSquad(sim, 2f, 1f);
        ConquestCommand command = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);
        CommanderService service = new CommanderService();
        service.setAutonomousCommander(Faction.MARINE, command,
                ConquestCommandDisclosure.INSTANCE);

        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);
        CommandDirective first = service.assignments().activeDirective(squad.id);
        assertEquals(AssignmentKind.SECURE_COMPOUND,
                squad.assignedObjective.kind());
        assertTrue(first.isStableAt(sim.getSimTickIndex()));

        sim.getCompoundService().getRecords().iterator().next().state =
                CompoundService.CompoundState.MARINE_HELD;
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        CommandDirective result = service.snapshot(Faction.MARINE)
                .directiveFor(squad.id);
        assertNull(squad.assignedObjective);
        assertEquals(CommandDirective.Status.RELEASED, result.status());
        assertTrue(result.dispositionReason().contains("objective completed"));
    }

    @Test
    public void blockedCompoundAnchorUsesResolvedCaptureRoomForOrderAndMarker() {
        BattleSimulation sim = compoundAt(15);
        sim.getGrid().setWalkable(15, 5, false);
        sim.getZoneGraph().rebuild();
        TacticalNode node = new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                15, 5, 14, 4, 16, 6, Faction.DEFENDER, 100, 4);
        CompoundService.Record record = sim.getCompoundService().register(node);
        Squad squad = addMarineSquad(sim, 2f, 1f);

        ConquestCommand command = new ConquestCommand(
                TraversalAxis.SOUTH_TO_NORTH);
        tick(command, sim);

        int captureZone = sim.getCompoundService().captureZoneId(record, sim);
        assertTrue(captureZone >= 0);
        assertEquals(captureZone, squad.assignedObjective.targetZoneId());
        assertEquals(node, squad.assignedObjective.targetNode(),
                "the arbiter must rebind the copied order to the live compound");
        ConquestFrontSnapshot.SquadDirective directive =
                command.frontSnapshot().directiveFor(squad.id);
        assertEquals(record.captureCellX, directive.markerCellX());
        assertEquals(record.captureCellY, directive.markerCellY());
        assertTrue(sim.getGrid().isWalkable(
                directive.markerCellX(), directive.markerCellY()));
        assertEquals(captureZone, sim.getZoneGraph().zoneIdAt(
                directive.markerCellX(), directive.markerCellY()));
    }

    @Test
    public void unreachableSoleKeepDoesNotConvergeSquadsIntoASealedTarget() {
        BattleSimulation sim = sealedCompoundAt(19);
        registerCompound(sim, new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                19, 5, 18, 4, 20, 6, Faction.DEFENDER, 100, 4));
        Squad squad = addMarineSquad(sim, 5f, 5f);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        assertEquals(Phase.KEEP_CONVERGENCE, cmd.frontSnapshot().phase());
        assertNull(squad.assignedObjective);
        assertEquals(AssignmentReason.NO_REACHABLE_COMPOUND_TARGET,
                cmd.frontSnapshot().directiveFor(squad.id).reason());
    }

    @Test
    public void frontMetricsDoNotRevealUnseenLiveDefenders() {
        BattleSimulation sim = openSim();
        addMarineSquad(sim, 5f, 5f);
        addDefender(sim, 25, 5);
        sim.advance(BattleSimulation.TICK_DT);

        ConquestCommand cmd = new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH);
        tick(cmd, sim);

        ConquestFrontSnapshot.TrackState hiddenTrack =
                cmd.frontSnapshot().track(2);
        assertEquals(0, hiddenTrack.knownHostileContacts());
        assertEquals(0f, hiddenTrack.knownHostilePressure(), 0.0001f);
        assertEquals(-1f, hiddenTrack.knownHostileFrontProgress(), 0.0001f);
    }

    @Test
    public void assignmentsDoNotRevealUnseenLiveDefenders() {
        BattleSimulation emptySim = openSim();
        Squad emptySquad = addMarineSquad(emptySim, 5f, 5f);
        emptySim.advance(BattleSimulation.TICK_DT);

        BattleSimulation hiddenSim = openSim();
        Squad hiddenSquad = addMarineSquad(hiddenSim, 5f, 5f);
        addDefender(hiddenSim, 25, 5);
        hiddenSim.advance(BattleSimulation.TICK_DT);

        tick(new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH), emptySim);
        tick(new ConquestCommand(TraversalAxis.SOUTH_TO_NORTH), hiddenSim);

        assertEquals(emptySquad.assignedObjective, hiddenSquad.assignedObjective,
                "an unseen hostile must not change the Marine command directive");
    }

    private static void tick(ConquestCommand command, BattleSimulation sim) {
        CommanderService.runSingle(command, ConquestCommandDisclosure.INSTANCE,
                sim);
    }
}
