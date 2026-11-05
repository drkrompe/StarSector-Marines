package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleControlSystemTest {

    @Test
    void capturedInboundPoseCompletesItsTerminalApproach() {
        NavigationGrid grid = capturedTerminalGrid();
        NavigationService navigation = new NavigationService(grid, new CellTopology(260, 100));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(260, 100), null);
        ConvoyService convoy = roster.convoy();
        VehicleMission mission = new VehicleMission(
                new float[]{246f, 237.5f, 181.62f, 181.38f, 158.5f},
                new float[]{82.5f, 82.5f, 81.5f, 81.51f, 82.5f},
                new float[]{158.5f, 202.5f, 237.5f, 246f},
                new float[]{82.5f, 81.5f, 82.5f, 82.5f},
                0f, 4);
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        GroundBody body = convoy.body(id);
        body.teleport(161.15f, 81.23f, 84.27f);
        body.speed = 0f;
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);

        boolean arrived = false;
        for (int i = 0; i < 400 && !arrived; i++) {
            controls.tick(id, 0.05f, mission.inboundX, mission.inboundY, VehicleLeg.DELIVERY_RUN);
            arrived = controls.consumeArrived(id);
        }

        assertTrue(arrived, "the captured terminal merge must complete instead of holding"
                + " (pose=" + body.x + "," + body.y + " @ " + body.facingDegrees
                + ", failureTime=" + convoy.control(id).localPlanFailureTime
                + ", recovery=" + convoy.control(id).recovery + ")");
        assertTrue(body.x < 160f, "the APC should take its final safe forward successor before landing");
        assertTrue(VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                VehicleType.HEAVY_APC.visualLengthCells,
                VehicleType.HEAVY_APC.visualWidthCells, grid));
        assertTrue(LocalTrajectoryPlanner.isInTerminalGoalRegion(
                new Pose(body.x, body.y, body.facingDegrees),
                convoy.control(id).corridor, VehicleType.HEAVY_APC));
        assertEquals(0f, convoy.control(id).localPlanFailureTime, 0.001f);
    }

    /**
     * The captured stall: a HEAVY_APC departing east down an open lane on a
     * 280-wide map, exit waypoint at the usual off-map pad. Its rolling goal
     * leaves the grid around x=271 — nine cells before its own footprint does —
     * and the recorded truck sat at x=270.78 with speed 0 for the rest of the
     * battle. Departure must complete instead.
     */
    @Test
    void departingApcCrossesTheMapEdgeInsteadOfStallingAtTheHorizon() {
        NavigationGrid grid = new NavigationGrid(280, 100);
        carve(grid, 0, 78, 279, 82);
        NavigationService navigation = new NavigationService(grid, new CellTopology(280, 100));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(280, 100), null);
        ConvoyService convoy = roster.convoy();
        VehicleMission mission = new VehicleMission(
                new float[]{286f, 277.5f, 197.5f},
                new float[]{80.5f, 80.5f, 80.5f},
                new float[]{197.5f, 277.5f, 286f},
                new float[]{80.5f, 80.5f, 80.5f},
                0f, 4);
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        GroundBody body = convoy.body(id);
        body.teleport(262f, 80.5f, -90f);
        body.speed = 0f;
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);

        boolean arrived = false;
        for (int i = 0; i < 600 && !arrived; i++) {
            controls.tick(id, 0.05f, mission.outboundX, mission.outboundY, VehicleLeg.DEPARTURE_RUN);
            arrived = controls.consumeArrived(id);
        }

        assertTrue(arrived, "the departing APC must reach its off-map exit (stopped at x="
                + body.x + ", speed=" + body.speed
                + ", failureTime=" + convoy.control(id).localPlanFailureTime + ")");
        assertTrue(body.x > 279f, "it must actually leave the grid, not arrive inside the map");
    }

    @Test
    void onGridPlannerFailureHoldsInsteadOfDrivingTheCoarseElbow() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        carve(grid, 10, 0, 12, 12);
        carve(grid, 10, 10, 25, 12);
        NavigationService navigation = new NavigationService(grid, new CellTopology(30, 30));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(30, 30), null);
        ConvoyService convoy = roster.convoy();
        VehicleMission mission = new VehicleMission(
                new float[]{11.5f, 11.5f, 24.5f},
                new float[]{3.5f, 11.5f, 11.5f},
                new float[]{24.5f, 11.5f},
                new float[]{11.5f, 3.5f},
                0f, 4);
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);
        GroundBody body = convoy.body(id);

        for (int i = 0; i < 20; i++) {
            controls.tick(id, 0.1f, mission.inboundX, mission.inboundY, VehicleLeg.DELIVERY_RUN);
        }

        assertFalse(convoy.control(id).hasTrajectory(), "the elbow has no executable forward trajectory");
        assertEquals(11.5f, body.x, 0.001f,
                "an on-grid null plan must not fall through to coarse-corridor pursuit");
        assertEquals(3.5f, body.y, 0.001f);
        assertEquals(0f, body.speed, 0.001f);
        assertTrue(convoy.control(id).localPlanFailureRerouteAttempted,
                "an uninterrupted null plan makes only its immediate re-route attempt once");
    }

    /**
     * The captured stall (urban Road frozen 11): an outbound truck in the town's loading yard, nosed west against a
     * house with its way out running south of it. No forward plan exists from that pose and no reroute helps; it stood
     * there all battle with its convoy waiting behind it. It backs off and plans again from the roomier pose.
     */
    @Test
    void aTruckWithNoPlanFromItsPoseBacksOffAndGoesOn() {
        NavigationGrid grid = capturedYardGrid();
        NavigationService navigation = new NavigationService(grid, new CellTopology(320, 130));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(320, 130), null);
        ConvoyService convoy = roster.convoy();
        float[] xs = {287.04f, 288f, 280f, 270f, 250f};
        float[] ys = {99.24f, 95f, 94.5f, 94.5f, 94.5f};
        VehicleMission mission = new VehicleMission(xs, ys, xs, ys, 0f, 4);
        long id = convoy.spawn(VehicleType.SUPPLY_TRUCK, Faction.DEFENDER, mission);
        GroundBody body = convoy.body(id);
        body.teleport(287.04f, 99.24f, 82.05f);
        body.speed = 0f;
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);

        boolean stuck = false, reversed = false, replanned = false;
        for (int i = 0; i < 400 && !replanned; i++) {
            controls.tick(id, 0.05f, xs, ys, VehicleLeg.MOVE_ORDER);
            VehicleControlComponent s = convoy.control(id);
            stuck |= s.localPlanFailureRerouteAttempted;
            reversed |= s.recovery == VehicleControlComponent.Recovery.REVERSING;
            replanned = reversed && s.recovery == VehicleControlComponent.Recovery.NONE && s.hasTrajectory();
        }

        assertTrue(stuck, "the captured pose has no plan of its own");
        assertTrue(reversed, "with no plan from its pose the truck backs off");
        assertTrue(replanned, "and plans forward again from where it backed to (at " + body.x + "," + body.y
                + " @ " + body.facingDegrees + ")");
    }

    /**
     * The captured stall (The Burn, seed 202): a tank with a house's corner at its front right, a hair clear, its way
     * on behind it. It has no plan from that pose, cannot pivot through the corner, and has clear ground straight back;
     * but its reverse steers the nose toward the route, into the corner, and was refused before it had moved. It stood
     * there for minutes, every order given up and given again.
     */
    @Test
    void aHullWhoseSteeredReverseIsHeldBacksOffStraight() {
        NavigationGrid grid = new NavigationGrid(100, 240);
        carve(grid, 0, 0, 99, 239);
        for (int x = 66; x <= 72; x++) if (x != 68) grid.setWalkable(x, 209, false);
        grid.setWalkable(66, 208, false);
        grid.setWalkable(70, 208, false);
        NavigationService navigation = new NavigationService(grid, new CellTopology(100, 240));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(100, 240), null);
        ConvoyService convoy = roster.convoy();
        float[] xs = {64.5f, 61.83f, 60.62f, 63.24f};
        float[] ys = {210.5f, 207.83f, 204.62f, 164.55f};
        VehicleMission mission = new VehicleMission(xs, ys, xs, ys, 0f, 4);
        // Captured on the tracked Onager, which now hovers; the medium tank keeps its tracked drive.
        long id = convoy.spawn(VehicleType.MEDIUM_TANK, Faction.DEFENDER, mission);
        GroundBody body = convoy.body(id);
        float startX = 64.08222f, startY = 210.74971f;
        body.teleport(startX, startY, -79.06138f);
        body.speed = 0f;
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);

        boolean moved = false;
        for (int i = 0; i < 400 && !moved; i++) {
            controls.tick(id, 0.05f, xs, ys, VehicleLeg.MOVE_ORDER);
            moved = Math.hypot(body.x - startX, body.y - startY) > 1f;
        }

        assertTrue(moved, "it backs clear of the corner (held at " + body.x + "," + body.y + " @ "
                + body.facingDegrees + ", reverses " + convoy.control(id).planlessReverses + ")");
    }

    /**
     * The captured stall (urban Road frozen 11): a wall-crushing hull in a street, its tail against a house wall it had swung past
     * and the wall behind it, its route turning away from the house. Planned on its breach view, where that wall is
     * open, the turn swings its tail into it, so the step was refused every tick; with nothing clear behind it could
     * not back off, and it stood there all battle. Its hull grinds down the wall it is held by, and it goes on.
     */
    @Test
    void aCrushingHullWhoseTailIsHeldByAWallItsRouteCountsOpenGrindsItAndGoesOn() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 0, 0, 39, 39);
        CellTopology topology = new CellTopology(40, 40);
        for (int x = 9; x <= 12; x++) houseWall(grid, topology, x, 7);
        for (int y = 7; y <= 9; y++) houseWall(grid, topology, 8, y);
        NavigationService navigation = new NavigationService(grid, topology);
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(40, 40), null);
        ConvoyService convoy = roster.convoy();
        float[] xs = {10.96f, 10.96f, 22f, 22f};
        float[] ys = {10.31f, 14f, 24f, 36f};
        VehicleMission mission = new VehicleMission(xs, ys, xs, ys, 0f, 4);
        long id = convoy.spawn(VehicleType.BREAKTHROUGH_TANK, Faction.DEFENDER, mission);
        GroundBody body = convoy.body(id);
        // Its side and tail a hair off the house, as the sweep left them.
        body.teleport(9.01f + VehicleType.BREAKTHROUGH_TANK.visualWidthCells * .5f,
                8.01f + VehicleType.BREAKTHROUGH_TANK.visualLengthCells * .5f, 0f);
        body.speed = 0f;
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);
        controls.setWallCrush(new WallCrush(navigation, new MapEditor(navigation), null));

        for (int i = 0; i < 400 && body.y < 20f; i++) controls.tick(id, 0.05f, xs, ys, VehicleLeg.MOVE_ORDER);

        assertTrue(body.y >= 20f, "it goes on up its route (held at " + body.x + "," + body.y + " @ "
                + body.facingDegrees + ")");
        boolean ground = false;
        for (int y = 7; y <= 9; y++) ground |= grid.isWalkable(8, y);
        assertTrue(ground, "through the wall that held its tail");
    }

    /**
     * A slit through growth two cells wide, which a tank a little wider can only pass by brushing, with clear ground
     * behind. Its back-offs, turnarounds and the short plans they find must not keep it from the brushing that is the
     * last rung before its order is given up: with their resets of the failure clock it never comes at all.
     */
    @Test
    void aHullWithRoomBehindItStillReachesItsLastRung() {
        NavigationGrid grid = new NavigationGrid(44, 22);
        carve(grid, 0, 0, 43, 21);
        for (int x = 24; x <= 27; x++) {
            for (int y = 0; y <= 21; y++) {
                if (y == 10 || y == 11) continue;
                grid.setWalkable(x, y, false);
                grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, true);
                grid.setTag(x, y, NavigationGrid.CellTag.CRUSHABLE, true);
            }
        }
        NavigationService navigation = new NavigationService(grid, new CellTopology(44, 22));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(44, 22), null);
        ConvoyService convoy = roster.convoy();
        float[] xs = {17.5f, 40.5f};
        float[] ys = {11f, 11f};
        VehicleMission mission = new VehicleMission(xs, ys, xs, ys, 0f, 4);
        long id = convoy.spawn(VehicleType.BREAKTHROUGH_TANK, Faction.DEFENDER, mission);
        GroundBody body = convoy.body(id);
        body.teleport(17.5f, 11f, -90f);
        body.speed = 0f;
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);
        controls.setBrush(new SoftBrush(SoftBrush.NO_PROPS));

        boolean brushed = false;
        float seconds = 0f, mostClock = 0f;
        for (; seconds < 60f && (!brushed || body.x < 30f); seconds += 0.05f) {
            controls.tick(id, 0.05f, xs, ys, VehicleLeg.MOVE_ORDER);
            brushed |= convoy.control(id).brushing;
            mostClock = Math.max(mostClock, convoy.control(id).localPlanFailureTime);
        }

        assertTrue(brushed, "it brushes (held at " + body.x + "," + body.y + " after " + seconds + " s, failure clock "
                + "reached " + mostClock + ", " + convoy.control(id).recoveryAttempts + " back-offs)");
        assertTrue(body.x >= 30f, "and through the slit (held at " + body.x + "," + body.y + ")");
    }

    @Test
    void aNewRouteDoesNotRestartTheClockOfAHullWithNoPlan() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        for (int y = 12; y <= 16; y++) for (int x = 12; x <= 16; x++) grid.setWalkableFloor(x, y);
        for (int y = 22; y <= 27; y++) for (int x = 22; x <= 27; x++) grid.setWalkableFloor(x, y);
        NavigationService navigation = new NavigationService(grid, new CellTopology(30, 30));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(30, 30), null);
        ConvoyService convoy = roster.convoy();
        float[] xs = {14.5f, 24.5f}, ys = {14.5f, 24.5f};
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, new VehicleMission(xs, ys, xs, ys, 0f, 4));
        GroundBody body = convoy.body(id);
        body.teleport(14.5f, 14.5f, 0f);
        body.speed = 0f;
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);

        for (int i = 0; i < 30; i++) controls.tick(id, .05f, xs, ys, VehicleLeg.MOVE_ORDER);
        float clock = convoy.control(id).localPlanFailureTime;
        assertTrue(clock > 0f, "fixture check: it has no plan out of the box");

        controls.tick(id, .05f, new float[]{14.5f, 25.5f}, new float[]{14.5f, 25.5f}, VehicleLeg.MOVE_ORDER);
        assertTrue(convoy.control(id).localPlanFailureTime >= clock, "re-aimed, the clock runs on");
    }

    /** Open ground and a clear route: the planner always has an answer. */
    private static long hullOnOpenGround(ConvoyService convoy, float[] xs, float[] ys) {
        long id = convoy.spawn(VehicleType.MILITIA_TECHNICAL, Faction.DEFENDER, new VehicleMission(xs, ys, xs, ys, 0f, 4));
        convoy.body(id).teleport(xs[0], ys[0], 0f);
        convoy.body(id).speed = 0f;
        return id;
    }

    private static void stuckWhereItStands(GroundBody body, VehicleControlComponent s) {
        s.wallStuckTime = VehicleController.UNDRIVEN_PLAN_SECONDS;
        s.stuckOriginX = body.x;
        s.stuckOriginY = body.y;
        s.stuckOriginFacing = body.facingDegrees;
    }

    @Test
    void aPlanThatHasStoppedDrivingTheHullIsNoPlanAndTheClockRuns() {
        NavigationGrid grid = new NavigationGrid(60, 60);
        for (int y = 0; y < 60; y++) for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        NavigationService navigation = new NavigationService(grid, new CellTopology(60, 60));
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(60, 60), null).convoy();
        float[] xs = {30.5f, 30.5f}, ys = {10.5f, 50.5f};
        long id = hullOnOpenGround(convoy, xs, ys);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);
        VehicleControlComponent s = convoy.control(id);
        controls.tick(id, .05f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertTrue(s.hasTrajectory(), "fixture check: open ground has a plan");

        convoy.body(id).teleport(xs[0], ys[0], 0f);
        stuckWhereItStands(convoy.body(id), s);
        for (int i = 0; i < 10; i++) controls.tick(id, .05f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertFalse(s.hasTrajectory());
        assertTrue(s.dockingPath != null || s.localPlanFailureTime > 0f, "so the rungs for a hull with no plan run");

        float clock = s.localPlanFailureTime;
        controls.tick(id, .05f, new float[]{30.5f, 31.5f}, new float[]{10.5f, 50.5f}, VehicleLeg.MOVE_ORDER);
        assertTrue(s.hasTrajectory(), "a new route's plan gets a try");
        assertTrue(s.wallStuckTime > 0f, "but a new route frees nothing");
        assertTrue(s.localPlanFailureTime >= clock, "and the clock stands until the plan has got it clear");
    }

    /** A slot one hull wide and {@code length} long, up from y=12, the hull's tail just off its end wall, facing up it. */
    private static VehicleControlSystem hullInASlot(ConvoyService convoy, long[] hull, int length) {
        NavigationGrid grid = new NavigationGrid(40, 40);
        for (int y = 12; y < 12 + length; y++) for (int x = 18; x <= 20; x++) grid.setWalkableFloor(x, y);
        NavigationService navigation = new NavigationService(grid, new CellTopology(40, 40));
        float[] xs = {19.5f, 19.5f}, ys = {14.5f, 38.5f};
        hull[0] = hullOnOpenGround(convoy, xs, ys);
        GroundBody body = convoy.body(hull[0]);
        body.teleport(19.5f, 12.01f + VehicleType.MILITIA_TECHNICAL.visualLengthCells / 2f, 0f);
        return new VehicleControlSystem(convoy, navigation);
    }

    @Test
    void aHullWithNoRoomBehindDrivesOutOverItsNose() {
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(40, 40), null).convoy();
        long[] hull = new long[1];
        VehicleControlSystem controls = hullInASlot(convoy, hull, 12);
        GroundBody body = convoy.body(hull[0]);
        VehicleControlComponent s = convoy.control(hull[0]);
        float[] xs = {19.5f, 19.5f}, ys = {14.5f, 38.5f};
        controls.tick(hull[0], 0f, xs, ys, VehicleLeg.MOVE_ORDER);
        float x = body.x, y = body.y;
        assertTrue(controls.beginDriveOut(body, VehicleType.MILITIA_TECHNICAL, s));
        for (int i = 0; i < 20; i++) controls.tick(hull[0], .05f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertTrue(body.y > y + 1f, "up the slot, got " + body.y);
        assertEquals(x, body.x, 1e-3f, "and straight");
        assertEquals(0f, body.facingDegrees, 1e-3f);
        assertTrue(body.speed >= 0f, "forward");
    }

    @Test
    void aHullBoxedInAheadAsWellHasNowhereToDriveOut() {
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(40, 40), null).convoy();
        long[] hull = new long[1];
        VehicleControlSystem controls = hullInASlot(convoy, hull, (int) VehicleType.MILITIA_TECHNICAL.visualLengthCells + 1);
        assertFalse(controls.beginDriveOut(convoy.body(hull[0]), VehicleType.MILITIA_TECHNICAL, convoy.control(hull[0])));
    }

    @Test
    void movingOrTurningClearOfWhereItStuckLetsPlansBeTriedAgain() {
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(40, 40), null).convoy();
        long id = hullOnOpenGround(convoy, new float[]{10f, 20f}, new float[]{10f, 10f});
        GroundBody body = convoy.body(id);
        VehicleControlComponent s = new VehicleControlComponent();

        s.wallStuckTime = VehicleController.UNDRIVEN_PLAN_SECONDS - .1f;
        s.stuckOriginX = body.x;
        s.stuckOriginY = body.y;
        s.stuckOriginFacing = body.facingDegrees;
        assertFalse(VehicleControlSystem.planIsNotDriving(body, s), "a plan gets its time");

        stuckWhereItStands(body, s);
        assertTrue(VehicleControlSystem.planIsNotDriving(body, s));

        body.teleport(body.x, body.y, VehicleController.STUCK_ESCAPE_DEGREES + 1f);
        assertFalse(VehicleControlSystem.planIsNotDriving(body, s), "turned");
        assertEquals(0f, s.wallStuckTime);

        stuckWhereItStands(body, s);
        body.teleport(body.x + VehicleController.STUCK_ESCAPE_DIST + .1f, body.y, body.facingDegrees);
        assertFalse(VehicleControlSystem.planIsNotDriving(body, s), "moved");
    }

    @Test
    void aPlanFromWhereItsBackOffsBeganDoesNotEndTheFailure() {
        VehicleMission mission = new VehicleMission(new float[]{0f, 1f}, new float[]{0f, 0f},
                new float[]{1f, 0f}, new float[]{0f, 0f}, 0f, 4);
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(40, 40), null);
        ConvoyService convoy = roster.convoy();
        GroundBody body = convoy.body(convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission));
        body.teleport(10f, 10f, 0f);
        VehicleControlComponent s = new VehicleControlComponent();
        s.localPlanFailureTime = 3f;
        s.planlessHold = 1f;

        assertFalse(VehicleControlSystem.countPlanlessReverse(body, s), "a first back-off is no progress yet");
        VehicleControlSystem.clearPlanFailure(body, s);
        assertEquals(3f, s.localPlanFailureTime, "a short plan where it stands keeps the clock");
        assertEquals(0f, s.planlessHold, "though it spaces the next back-off afresh");

        body.teleport(10f, 10f + VehicleController.PLANLESS_CLEAR + 1f, 0f);
        VehicleControlSystem.clearPlanFailure(body, s);
        assertEquals(0f, s.localPlanFailureTime, "a plan that has got it clear ends the failure");
        assertEquals(0, s.planlessReverses);
    }

    @Test
    void aHeldTruckDoesNotBackOff() {
        VehicleMission mission = new VehicleMission(new float[]{0f, 1f}, new float[]{0f, 0f},
                new float[]{1f, 0f}, new float[]{0f, 0f}, 0f, 4);
        VehicleControlComponent s = new VehicleControlComponent();
        s.localPlanFailureRerouteAttempted = true;
        s.planlessHold = VehicleController.PLANLESS_REVERSE_SECONDS;
        assertTrue(VehicleControlSystem.planlessReverseDue(mission, s));
        mission.speedCap = 0f;
        assertFalse(VehicleControlSystem.planlessReverseDue(mission, s), "held by its driver, it waits");
        mission.speedCap = Float.POSITIVE_INFINITY;
        s.localPlanFailureRerouteAttempted = false;
        assertFalse(VehicleControlSystem.planlessReverseDue(mission, s), "its reroute comes first");
    }

    /** The town's loading yard round the stuck truck, x 272-306 and y 88-114, with the lane west out of it. */
    @Test
    void aHullDrivesRoundAHullStandingOnItsRoute() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 0, 0, 39, 39);
        NavigationService navigation = new NavigationService(grid, new CellTopology(40, 40));
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(40, 40), null).convoy();
        float[] xs = {20.5f, 20.5f}, ys = {4f, 36f};
        long id = hull(convoy, VehicleType.MILITIA_TECHNICAL, 20.5f, 4f, 0f);
        long parked = hull(convoy, VehicleType.BREAKTHROUGH_TANK, 20.5f, 18f, 90f);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);
        GroundBody body = convoy.body(id), other = convoy.body(parked);
        float[] contact = new float[3];

        boolean arrived = false;
        for (int i = 0; i < 1200 && !arrived; i++) {
            controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
            arrived = controls.consumeArrived(id);
            assertFalse(HullContact.overlap(body.x, body.y, body.facingDegrees,
                    VehicleType.MILITIA_TECHNICAL.visualLengthCells / 2f, VehicleType.MILITIA_TECHNICAL.visualWidthCells / 2f,
                    other.x, other.y, other.facingDegrees,
                    VehicleType.BREAKTHROUGH_TANK.visualLengthCells / 2f, VehicleType.BREAKTHROUGH_TANK.visualWidthCells / 2f,
                    0f, contact), "drove into the standing hull at " + body.x + "," + body.y);
        }
        assertTrue(arrived, "stopped at " + body.x + "," + body.y);
    }

    @Test
    void aHullHeldShortOfADestinationAnotherHasTakenArrivesWhereItIs() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 0, 0, 39, 39);
        NavigationService navigation = new NavigationService(grid, new CellTopology(40, 40));
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(40, 40), null).convoy();
        float[] xs = {20.5f, 20.5f}, ys = {4f, 20f};
        long id = hull(convoy, VehicleType.BREAKTHROUGH_TANK, 20.5f, 15f, 0f);
        hull(convoy, VehicleType.BREAKTHROUGH_TANK, 20.5f, 20f, 0f);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);

        controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertFalse(controls.consumeArrived(id), "not held yet");

        convoy.control(id).hullHold = VehicleController.DESTINATION_TAKEN_SECONDS;
        controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertTrue(controls.consumeArrived(id));
        assertEquals(0f, convoy.body(id).speed);
    }

    @Test
    void aHullHeldOnItsWayPastADestinationStillFreeDoesNotArrive() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 0, 0, 39, 39);
        NavigationService navigation = new NavigationService(grid, new CellTopology(40, 40));
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(40, 40), null).convoy();
        float[] xs = {20.5f, 20.5f}, ys = {4f, 26f};
        long id = hull(convoy, VehicleType.MILITIA_TECHNICAL, 20.5f, 17f, 0f);
        hull(convoy, VehicleType.MILITIA_TECHNICAL, 20.5f, 20.5f, 0f);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);

        convoy.control(id).hullHold = VehicleController.DESTINATION_TAKEN_SECONDS;
        controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertFalse(controls.consumeArrived(id));
    }

    @Test
    void aHullHeldNoseToAHullBacksOffForRoom() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 0, 0, 39, 39);
        NavigationService navigation = new NavigationService(grid, new CellTopology(40, 40));
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(40, 40), null).convoy();
        float[] xs = {20.5f, 20.5f}, ys = {4f, 36f};
        long id = hull(convoy, VehicleType.BREAKTHROUGH_TANK, 20.5f, 14f, 0f);
        hull(convoy, VehicleType.BREAKTHROUGH_TANK, 20.5f, 18.2f, 0f);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);

        controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertEquals(VehicleControlComponent.Recovery.NONE, convoy.control(id).recovery);
        convoy.control(id).hullHold = VehicleController.HULL_BACK_OFF_SECONDS;
        controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertEquals(VehicleControlComponent.Recovery.REVERSING, convoy.control(id).recovery);
        assertEquals(0f, convoy.control(id).hullHold);
    }

    @Test
    void aHullBacksOffAHullNoFurtherThanItsClearanceAllows() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 0, 0, 39, 39);
        // A wall 1.2 cells behind its tail and a hull 0.6 cells ahead of its nose, whatever the tank's length.
        float length = VehicleType.BREAKTHROUGH_TANK.visualLengthCells;
        float tail = 12.2f;
        for (int x = 0; x < 40; x++) grid.setWalkable(x, 10, false);
        NavigationService navigation = new NavigationService(grid, new CellTopology(40, 40));
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(40, 40), null).convoy();
        float[] xs = {20.5f, 20.5f}, ys = {4f, 36f};
        long id = hull(convoy, VehicleType.BREAKTHROUGH_TANK, 20.5f, tail + length / 2f, 0f);
        hull(convoy, VehicleType.BREAKTHROUGH_TANK, 20.5f, tail + length * 1.5f + .6f, 0f);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);
        convoy.control(id).hullHold = VehicleController.HULL_BACK_OFF_SECONDS;
        GroundBody body = convoy.body(id);
        float clearance = HybridAStarPlanner.PLANNER_CLEARANCE;

        controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertEquals(VehicleControlComponent.Recovery.REVERSING, convoy.control(id).recovery);
        for (int i = 0; i < 300 && convoy.control(id).recovery == VehicleControlComponent.Recovery.REVERSING; i++) {
            controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
            assertTrue(VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                    VehicleType.BREAKTHROUGH_TANK.visualLengthCells + clearance,
                    VehicleType.BREAKTHROUGH_TANK.visualWidthCells + clearance, grid), "backed into its clearance");
        }
        assertEquals(VehicleControlComponent.Recovery.NONE, convoy.control(id).recovery);
        assertEquals(0, convoy.control(id).recoveryAttempts);
    }

    @Test
    void aHullInsideItsClearanceBesideAWallPlansOutOnItsBareFootprint() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 0, 0, 39, 39);
        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;
        // A wall along its left flank, a tenth of a cell off the chassis: inside the clearance, not touching.
        int wallX = 20;
        for (int y = 0; y < 40; y++) grid.setWalkable(wallX, y, false);
        float x = wallX + 1f + tank.visualWidthCells / 2f + .1f;
        NavigationService navigation = new NavigationService(grid, new CellTopology(40, 40));
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(40, 40), null).convoy();
        float[] xs = {x, x}, ys = {6f, 36f};
        long id = hull(convoy, tank, x, 6f, 0f);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);

        assertFalse(VehicleFootprint.isPoseFeasible(x, 6f, 0f, tank.visualLengthCells + HybridAStarPlanner.PLANNER_CLEARANCE,
                tank.visualWidthCells + HybridAStarPlanner.PLANNER_CLEARANCE, grid));
        controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertTrue(convoy.control(id).hasTrajectory());
        assertEquals(0f, convoy.control(id).localPlanFailureTime);
        for (int i = 0; i < 90; i++) controls.tick(id, 1f / 30f, xs, ys, VehicleLeg.MOVE_ORDER);
        assertTrue(convoy.body(id).y > 7f, "still at " + convoy.body(id).y);
    }

    private static long hull(ConvoyService convoy, VehicleType type, float x, float y, float facing) {
        VehicleMission mission = new VehicleMission(new float[]{x, x}, new float[]{y, y + 1f},
                new float[]{x, x}, new float[]{y + 1f, y}, 0f, 0);
        mission.state = VehicleState.DEPLOYED;
        long id = convoy.spawn(type, Faction.DEFENDER, mission);
        convoy.body(id).teleport(x, y, facing);
        convoy.body(id).speed = 0f;
        return id;
    }

    private static NavigationGrid capturedYardGrid() {
        NavigationGrid grid = new NavigationGrid(320, 130);
        String[] northToSouth = {
                ".,,,,,,,.#####.#####......,,,,,,,,,",
                ".,,,,,,,............#####.,,,,,,,,,",
                ".,,,,,,,.#####.########.#.,,,,,,,,,",
                ",,,,,,,,.#...#.#...##...#.,,,,,,,,,",
                ",,,,,,,,.#.,.#.#.,........,,,,,,,,,",
                ",,,,,,,,.....#.#...######.,,,,,,,,,",
                ",,,,,,,,.#####.##########.,,,,,,,,,",
                ",,,,,,,,..................,,,,,,,,,",
                ",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,",
                ",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,",
                ",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,",
                ",,,..........,,,,,,,,,,,,,,,,,,,,,,",
                ",,,.######.#.,,,,,,,,,,,,,,,,,,,,,,",
                ",,,.###....#.,,,,,,,,,,,,,,,,,,,,,,",
                ",,,.#...,.##.,,,,,,,,,,,,,,,,,,,,,,",
                ",,,.#..,,.##.,,,,,,,,,,,,,,,,,,,,..",
                ",,,.##....##.,,,,,,,,,,,,,,,,,,,,.#",
                ",,,.##.#####.,,,,,,,,,,,,,,,,,,,,.#",
                ",,,..........,,,,,,,,,,,,,,,,,,,,.#",
                ",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,.#",
                ",,,,...............,,...........,.#",
                "..,,.###..###.#.##.,,.###.##.##.,.#",
                "#.,,.###...##....#.,,.###.....#.,.#",
                "#.,,.##..,.###.,.#.,,...........,.#",
                "#.,,....,,.#...,...,,.######.##.,.#",
                "#.,,.#.....#.....#.,,.###.##..#.,.#",
                "..,,.####.###.####.,,...........,.#"
        };
        int originX = 272;
        int originY = 88;
        for (int row = 0; row < northToSouth.length; row++) {
            int y = originY + northToSouth.length - 1 - row;
            for (int col = 0; col < northToSouth[row].length(); col++) {
                if (northToSouth[row].charAt(col) != '#') grid.setWalkableFloor(originX + col, y);
            }
        }
        carve(grid, 230, 93, originX - 1, 96);
        return grid;
    }

    private static void houseWall(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false);
        grid.setWallHp(x, y, 100);
        topology.setTag(x, y, CellTopology.Tag.WALL, true);
    }

    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) grid.setWalkableFloor(x, y);
        }
    }

    /** The 17x17 navigation window recorded with the stuck live APC. */
    private static NavigationGrid capturedTerminalGrid() {
        NavigationGrid grid = new NavigationGrid(260, 100);
        String[] northToSouth = {
                "..#.........#....",
                "..#......#.......",
                "..#......#..#....",
                "..#......########",
                "..#..............",
                "..#..............",
                "..#..............",
                ".................",
                "..#..............",
                "..#..............",
                "..#.....#########",
                "...##..#.........",
                "..#....#.########",
                "..#.#..#.##......",
                "..#.#..#.#.......",
                "..#.#..#.........",
                "..#.#..#.#......."
        };
        int originX = 153;
        int originY = 73;
        for (int row = 0; row < northToSouth.length; row++) {
            int y = originY + northToSouth.length - 1 - row;
            for (int col = 0; col < northToSouth[row].length(); col++) {
                if (northToSouth[row].charAt(col) == '.') {
                    grid.setWalkableFloor(originX + col, y);
                }
            }
        }
        return grid;
    }
}
