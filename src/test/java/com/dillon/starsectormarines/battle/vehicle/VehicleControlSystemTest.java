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
     * The captured stall (urban Road frozen 11): a dozer in a street, its tail against a house wall it had swung past
     * and the wall behind it, its route turning away from the house. Planned on its breach view, where that wall is
     * open, the turn swings its tail into it, so the step was refused every tick; with nothing clear behind it could
     * not back off, and it stood there all battle. Its hull grinds down the wall it is held by, and it goes on.
     */
    @Test
    void aDozerWhoseTailIsHeldByAWallItsRouteCountsOpenGrindsItAndGoesOn() {
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
        long id = convoy.spawn(VehicleType.DOZER_BREACHER, Faction.DEFENDER, mission);
        GroundBody body = convoy.body(id);
        // Its side and tail a hair off the house, as the sweep left them.
        body.teleport(9.01f + VehicleType.DOZER_BREACHER.visualWidthCells * .5f,
                8.01f + VehicleType.DOZER_BREACHER.visualLengthCells * .5f, 0f);
        body.speed = 0f;
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);
        controls.setBlade(new DozerBlade(navigation, new MapEditor(navigation), null));

        for (int i = 0; i < 400 && body.y < 20f; i++) controls.tick(id, 0.05f, xs, ys, VehicleLeg.MOVE_ORDER);

        assertTrue(body.y >= 20f, "it goes on up its route (held at " + body.x + "," + body.y + " @ "
                + body.facingDegrees + ")");
        boolean ground = false;
        for (int y = 7; y <= 9; y++) ground |= grid.isWalkable(8, y);
        assertTrue(ground, "through the wall that held its tail");
    }

    @Test
    void aHeldTruckDoesNotBackOff() {
        VehicleMission mission = new VehicleMission(new float[]{0f, 1f}, new float[]{0f, 0f},
                new float[]{1f, 0f}, new float[]{0f, 0f}, 0f, 4);
        VehicleControlComponent s = new VehicleControlComponent();
        s.localPlanFailureRerouteAttempted = true;
        s.localPlanFailureTime = VehicleController.PLANLESS_REVERSE_SECONDS;
        assertTrue(VehicleControlSystem.planlessReverseDue(mission, s));
        mission.speedCap = 0f;
        assertFalse(VehicleControlSystem.planlessReverseDue(mission, s), "held by its driver, it waits");
        mission.speedCap = Float.POSITIVE_INFINITY;
        s.localPlanFailureRerouteAttempted = false;
        assertFalse(VehicleControlSystem.planlessReverseDue(mission, s), "its reroute comes first");
    }

    /** The town's loading yard round the stuck truck, x 272-306 and y 88-114, with the lane west out of it. */
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
