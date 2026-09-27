package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Small frozen grids and direct jobs/controller: no battle loop, renderer, or world generation. */
class ProgressiveVehicleRecoveryTest {
    @Test void malformedFacingNeverPublishesAReusableFailureKey() {
        for (float facing : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            try (Fixture f = new Fixture(true)) {
                f.body.facingDegrees = facing;
                f.state.rescueFirstStepTriedMask = 255;
                ProgressiveVehicleRecovery job = f.job();
                job.advance(1);
                assertTrue(job.complete());
                assertNull(job.route());
                assertNull(job.failedKey());
                assertFalse(FailedVehicleRecovery.cacheable(facing, 3f));
            }
        }
        assertFalse(FailedVehicleRecovery.cacheable(90f, Float.NaN));
        assertFalse(FailedVehicleRecovery.cacheable(90f, -1f));
        assertTrue(FailedVehicleRecovery.cacheable(90f, 3f));
    }

    @Test void slicedSuccessMatchesControlWithoutRestartingItsFrontier() {
        try (Fixture f = new Fixture(false)) {
            VehicleRoutePlanner.RescueRoute control = VehicleRoutePlanner.routeAvoidingForwardFirstOnDemand(
                    5, 8, 18, 8, f.body.facingDegrees, 0, f.fields.grid(), f.fields, f.fields,
                    new int[0], new int[0], 0, 3f, VehicleType.HEAVY_APC);
            assertNotNull(control);
            f.profile.reset();
            ProgressiveVehicleRecovery job = f.job();
            int slices = finish(job, f.profile, 3);
            assertTrue(slices > 8);
            assertEquals(control.firstStepDirectionBit(), job.route().firstStepDirectionBit());
            assertArrayEquals(control.points()[0], job.route().points()[0]);
            assertArrayEquals(control.points()[1], job.route().points()[1]);
            assertNull(f.state.failedRecovery);
        }
    }

    @Test void disconnectedRequestRemainsPendingThenFinishesWithoutAFakePartialRoute() {
        try (Fixture f = new Fixture(true)) {
            ProgressiveVehicleRecovery job = f.job();
            job.advance(1);
            assertFalse(job.complete());
            assertNull(job.route());
            assertNull(f.state.failedRecovery);
            assertEquals(1, f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT));
            assertTrue(finish(job, f.profile, 7) > 8);
            assertTrue(job.complete());
            assertNull(job.route());
            assertEquals(8, f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT),
                    "one exhausted connected region per legal starting bearing; slices do not restart it");
            int attempts = f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT);
            job.advance(7);
            assertEquals(attempts, f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT));
        }
    }

    @Test void closedReciprocalEdgeAndDiagonalCornerRefuseWithoutAnyAdjacentAStar() {
        try (Fixture f = new Fixture(false)) {
            f.fields.grid().setEdgePassable(6, 8, Direction.W, false);
            f.state.rescueFirstStepTriedMask = 255 ^ (1 << Direction.E.bit());
            ProgressiveVehicleRecovery job = f.job();
            job.advance(3);
            assertTrue(job.complete());
            assertNull(job.route());
            assertEquals(0, f.profile.countOf(Bucket.PATHFIND));
            assertEquals(0, f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT));
            f.fields.grid().setWalkable(6, 8, false);
            f.state.rescueFirstStepTriedMask = 255 ^ (1 << Direction.NE.bit());
            job = f.job();
            job.advance(3);
            assertTrue(job.complete());
            assertNull(job.route());
            assertEquals(0, f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT));
        }
    }

    @Test void cardinalModeNeverStartsADiagonalFirstStep() {
        boolean previous = GridPathfinder.USE_CARDINAL_NAVIGATION;
        try (Fixture f = new Fixture(false)) {
            GridPathfinder.USE_CARDINAL_NAVIGATION = true;
            f.state.rescueFirstStepTriedMask = 15;
            ProgressiveVehicleRecovery job = f.job();
            job.advance(3);
            assertTrue(job.complete());
            assertNull(job.route());
            assertEquals(0, f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT));
        } finally {
            GridPathfinder.USE_CARDINAL_NAVIGATION = previous;
        }
    }

    @Test void meaningfulContextChangesInvalidateButSubcellAndSameRankingHeadingDoNot() {
        List<Consumer<Fixture>> changes = List.of(
                f -> f.state.routeXs = f.state.routeXs.clone(),
                f -> f.state.leg = VehicleLeg.MOVE_ORDER,
                f -> f.mission.routeFields = fields(false),
                f -> f.live.setWalkable(0, 0, false),
                f -> f.fields.grid().setWalkable(0, 0, false),
                f -> f.body.x += 1f,
                f -> f.body.facingDegrees += 90f,
                f -> f.state.rescueFirstStepTriedMask = 1,
                f -> { f.state.rerouteAvoidCount = 1; f.state.rerouteAvoidX[0] = 9; });
        for (Consumer<Fixture> change : changes) {
            try (Fixture f = new Fixture(false)) {
                ProgressiveVehicleRecovery job = f.job();
                assertTrue(f.matches(job));
                change.accept(f);
                assertFalse(f.matches(job));
            }
        }
        try (Fixture f = new Fixture(false)) {
            f.body.facingDegrees = -82f;
            ProgressiveVehicleRecovery job = f.job();
            f.body.x += 0.1f;
            f.body.facingDegrees += 0.1f;
            assertTrue(f.matches(job));
        }
    }

    @Test void controllerResumesEachTickAndOnlyMemoizesACompletedFailure() {
        try (Fixture f = new Fixture(true)) {
            ControllerFixture c = new ControllerFixture(f);
            c.controls.tick(c.id, VehicleController.LOCAL_PLAN_FAILURE_REROUTE_SEC,
                    f.mission.inboundX, f.mission.inboundY, VehicleLeg.DELIVERY_RUN);
            VehicleControlComponent state = c.convoy.control(c.id);
            assertNotNull(state.pendingRecovery);
            assertNull(state.failedRecovery);
            int calls = f.profile.countOf(Bucket.VEHICLE_RECOVERY_SEARCH);
            float timer = state.timeSinceProgress;
            int ticks = 0;
            while (state.pendingRecovery != null && ticks++ < 2000) {
                int beforeAttempts = f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT);
                int beforeExpanded = f.profile.countOf(Bucket.VEHICLE_RECOVERY_EXPANDED);
                c.controls.tick(c.id, 1f / 30f, f.mission.inboundX, f.mission.inboundY,
                        VehicleLeg.DELIVERY_RUN);
                assertTrue(f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT) - beforeAttempts <= 1);
                assertTrue(f.profile.countOf(Bucket.VEHICLE_RECOVERY_EXPANDED) - beforeExpanded <= 7);
                if (state.pendingRecovery != null) assertNull(state.failedRecovery);
            }
            assertTrue(ticks < 2000);
            assertNotNull(state.failedRecovery);
            assertEquals(calls + ticks, f.profile.countOf(Bucket.VEHICLE_RECOVERY_SEARCH));
            assertTrue(state.timeSinceProgress > timer, "pending work never renews the stall clock");
            int attempts = f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT);
            c.controls.tick(c.id, VehicleController.STALL_SECONDS + .01f,
                    f.mission.inboundX, f.mission.inboundY, VehicleLeg.DELIVERY_RUN);
            assertEquals(attempts, f.profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT));
            assertEquals(1, f.profile.countOf(Bucket.VEHICLE_RECOVERY_FAILED_REUSE));
        }
    }

    @Test void controllerCancelsPendingForManualControlNewRouteAndLiveTerrain() {
        for (int change = 0; change < 3; change++) {
            try (Fixture f = new Fixture(true)) {
                ControllerFixture c = new ControllerFixture(f);
                c.controls.tick(c.id, VehicleController.LOCAL_PLAN_FAILURE_REROUTE_SEC,
                        f.mission.inboundX, f.mission.inboundY, VehicleLeg.DELIVERY_RUN);
                VehicleControlComponent state = c.convoy.control(c.id);
                assertNotNull(state.pendingRecovery);
                if (change == 0) c.controls.tickManual(c.id, 0f, 0f, 0f);
                else if (change == 1) c.controls.clearRoute(c.id);
                else {
                    c.navigation.getGrid().setWalkable(0, 0, false);
                    c.controls.tick(c.id, 1f / 30f, f.mission.inboundX,
                            f.mission.inboundY, VehicleLeg.DELIVERY_RUN);
                }
                assertNull(state.pendingRecovery);
                assertNull(state.failedRecovery);
            }
        }
    }

    private static int finish(ProgressiveVehicleRecovery job, TickInnerProfile profile, int quantum) {
        int slices = 0;
        while (!job.complete() && slices++ < 3000) {
            int expanded = profile.countOf(Bucket.VEHICLE_RECOVERY_EXPANDED);
            int attempts = profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT);
            job.advance(quantum);
            assertTrue(profile.countOf(Bucket.VEHICLE_RECOVERY_EXPANDED) - expanded <= quantum);
            assertTrue(profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT) - attempts <= 1);
            if (!job.complete()) assertNull(job.route());
        }
        assertTrue(job.complete());
        return slices;
    }

    private static ProgressiveVehicleField fields(boolean split) {
        NavigationGrid grid = new NavigationGrid(24, 18);
        for (int y = 0; y < 18; y++) for (int x = 0; x < 24; x++) {
            if (!split || x != 12) grid.setWalkableFloor(x, y);
        }
        return ProgressiveVehicleField.capture(grid, new CellTopology(24, 18), 0);
    }

    private static final class Fixture implements AutoCloseable {
        final ProgressiveVehicleField fields;
        final NavigationGrid live;
        final GroundBody body = VehicleType.HEAVY_APC.createBody();
        final VehicleControlComponent state = new VehicleControlComponent();
        final VehicleMission mission = new VehicleMission(new float[]{5.5f, 18.5f},
                new float[]{8.5f, 8.5f}, new float[]{18.5f, 5.5f}, new float[]{8.5f, 8.5f}, 0f, 4);
        final TickInnerProfile profile = new TickInnerProfile();
        final TickInnerProfile previous = TickInnerProfile.currentIfBound();
        Fixture(boolean split) {
            fields = fields(split);
            live = fields.grid().copyVehicleRoutingTopology();
            mission.routeFields = fields;
            state.routeXs = mission.inboundX;
            state.routeYs = mission.inboundY;
            state.leg = VehicleLeg.DELIVERY_RUN;
            body.teleport(5.5f, 8.5f, -90f);
            TickInnerProfile.setCurrent(profile);
        }
        ProgressiveVehicleRecovery job() {
            return new ProgressiveVehicleRecovery(state, mission, body, VehicleType.HEAVY_APC,
                    live, fields, 5, 8, 18, 8, 3f);
        }
        boolean matches(ProgressiveVehicleRecovery job) {
            return job.matches(state, mission, body, VehicleType.HEAVY_APC, live);
        }
        @Override public void close() { TickInnerProfile.setCurrent(previous); }
    }

    private static final class ControllerFixture {
        final NavigationService navigation;
        final ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(24, 18), null).convoy();
        final VehicleControlSystem controls;
        final long id;
        ControllerFixture(Fixture f) {
            f.live.setWalkable(5, 8, false); // failed live local plan; frozen recovery world remains valid
            navigation = new NavigationService(f.live, new CellTopology(24, 18));
            id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, f.mission);
            convoy.body(id).teleport(5.5f, 8.5f, -90f);
            controls = new VehicleControlSystem(convoy, navigation, true, 7);
        }
    }
}
