package com.dillon.starsectormarines.battle.vehicle;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small real-search fixtures: no battle/world tick, clocks, sleeps, or mocked planner. */
class FailedVehicleRecoveryTest {
    @AfterEach
    void releaseProfile() { TickInnerProfile.setCurrent(null); }

    @Test
    void identicalFailedRequestSkipsActualRouteWorkAndChangedRequestRetries() {
        Request request = new Request(true);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        assertNull(request.search(true));
        int attempts = count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT);
        int expanded = count(profile, Bucket.VEHICLE_RECOVERY_EXPANDED);
        assertTrue(attempts > 0);
        assertTrue(expanded > 0);
        assertNotNull(request.state.failedRecovery);
        assertNull(request.search(true));
        assertEquals(attempts, count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT));
        assertEquals(expanded, count(profile, Bucket.VEHICLE_RECOVERY_EXPANDED));
        assertEquals(1, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_REUSE));
        assertEquals(1, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_RESULT));

        request.facing += 1f;
        assertNull(request.search(true));
        assertEquals(attempts * 2, count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT));
        assertEquals(expanded * 2, count(profile, Bucket.VEHICLE_RECOVERY_EXPANDED));
        assertEquals(2, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_RESULT));
    }

    @Test
    void disabledControlRecomputesSameNullAndDiscardsMemo() {
        Request request = new Request(true);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        assertNull(request.search(true));
        int attempts = count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT);
        assertNull(request.search(false));
        assertNull(request.state.failedRecovery);
        assertNull(request.search(false));
        assertEquals(attempts * 3, count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT));
        assertEquals(0, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_REUSE));
        assertEquals(3, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_RESULT));
    }

    @Test
    void successfulSearchIsNeverMemoizedAndMatchesDisabledControl() {
        Request request = new Request(false);
        // A previous failed request must not survive a changed, successful request.
        request.state.failedRecovery = request.memo();
        request.facing += 1f;
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        VehicleRoutePlanner.RescueRoute first = request.search(true);
        assertNotNull(first);
        assertNull(request.state.failedRecovery);
        int attempts = count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT);
        assertTrue(attempts > 0);
        VehicleRoutePlanner.RescueRoute second = request.search(true);
        VehicleRoutePlanner.RescueRoute control = request.search(false);
        assertNotNull(second);
        assertNotNull(control);
        assertEquals(attempts * 3, count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT));
        assertEquals(first.firstStepDirectionBit(), control.firstStepDirectionBit());
        assertArrayEquals(first.points()[0], control.points()[0]);
        assertArrayEquals(first.points()[1], control.points()[1]);
        assertEquals(0, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_RESULT));
        assertEquals(0, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_REUSE));
    }

    @Test
    void everySearchInputClassInvalidatesTheCopiedKey() {
        List<Consumer<Request>> changes = List.of(
                r -> r.fields = fields(true),
                r -> r.fields.grid().setWalkable(0, 0, false),
                r -> r.type = null, // The catalog currently has only one real vehicle type.
                r -> r.state.routeXs = r.state.routeXs.clone(),
                r -> r.state.routeYs = r.state.routeYs.clone(),
                r -> r.startX++, r -> r.startY++, r -> r.goalX++, r -> r.goalY++,
                r -> r.facing++, r -> r.radius++,
                r -> r.state.rescueFirstStepTriedMask++,
                r -> r.state.rerouteAvoidCount++,
                r -> r.state.rerouteAvoidX[0]++,
                r -> r.state.rerouteAvoidY[0]++);
        for (int i = 0; i < changes.size(); i++) {
            Request request = new Request(true);
            FailedVehicleRecovery memo = request.memo();
            assertTrue(request.matches(memo));
            changes.get(i).accept(request);
            assertFalse(request.matches(memo), "changed input " + i);
        }
    }

    @Test
    void unusedAvoidanceCapacityDoesNotChangeRequestAndResetAlwaysClearsMemo() {
        Request request = new Request(true);
        FailedVehicleRecovery memo = request.memo();
        request.state.rerouteAvoidX[7] = 99;
        request.state.rerouteAvoidY[7] = 99;
        assertTrue(request.matches(memo));
        request.state.failedRecovery = memo;
        VehicleControlSystem.resetTracking(request.state, false);
        assertNull(request.state.failedRecovery);
        assertEquals(1, request.state.rerouteAvoidCount);
        request.state.failedRecovery = memo;
        VehicleControlSystem.resetTracking(request.state, true);
        assertNull(request.state.failedRecovery);
        assertEquals(0, request.state.rerouteAvoidCount);
    }

    @Test
    void failedRequestsAreOwnedByEachVehicleEvenWithSharedRouteInputs() {
        Request first = new Request(true);
        Request second = new Request(true);
        second.fields = first.fields;
        second.state.routeXs = first.state.routeXs;
        second.state.routeYs = first.state.routeYs;
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        assertNull(first.search(true));
        int attempts = count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT);
        assertNull(second.state.failedRecovery);
        assertNull(second.search(true));
        assertEquals(attempts * 2, count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT));
        assertNotNull(second.state.failedRecovery);
        assertNull(first.search(true));
        assertNull(second.search(true));
        assertEquals(2, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_REUSE));
    }

    @Test
    void malformedGeometryDoesNotStoreOrReuseFailure() {
        List<Consumer<Request>> changes = List.of(
                r -> r.facing = Float.NaN, r -> r.facing = Float.POSITIVE_INFINITY,
                r -> r.radius = Float.NaN, r -> r.radius = -1f);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        for (Consumer<Request> change : changes) {
            Request request = new Request(true);
            request.state.rescueFirstStepTriedMask = 255;
            change.accept(request);
            // Even a matching externally populated key must not qualify.
            request.state.failedRecovery = request.memo();
            assertNull(request.search(true));
            assertNull(request.state.failedRecovery);
        }
        assertEquals(0, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_REUSE));
        assertEquals(changes.size(), count(profile, Bucket.VEHICLE_RECOVERY_FAILED_RESULT));
    }

    @Test
    void publicControllerKeepsRetryCadenceAndClearsMemoOnManualRouteAndTrackingReset() {
        Request request = new Request(true);
        // Recovery reads its committed frozen world. The current chassis pose
        // is invalid on the live grid, so the rolling planner holds it still.
        NavigationGrid liveGrid = request.fields.grid().copyVehicleRoutingTopology();
        liveGrid.setWalkable(5, 8, false);
        NavigationService navigation = new NavigationService(liveGrid, new CellTopology(24, 18));
        ConvoyService convoy = new UnitRosterService(new UnitSpatialIndex(24, 18), null).convoy();
        VehicleMission mission = new VehicleMission(request.state.routeXs, request.state.routeYs,
                new float[]{18.5f, 5.5f}, new float[]{8.5f, 8.5f}, 0f, 4);
        mission.routeFields = request.fields;
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        convoy.body(id).teleport(5.5f, 8.5f, -90f);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);
        VehicleControlComponent state = convoy.control(id);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);

        controls.tick(id, VehicleController.LOCAL_PLAN_FAILURE_REROUTE_SEC,
                mission.inboundX, mission.inboundY, VehicleLeg.DELIVERY_RUN);
        int attempts = count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT);
        int expanded = count(profile, Bucket.VEHICLE_RECOVERY_EXPANDED);
        assertTrue(attempts > 0);
        assertTrue(expanded > 0);
        assertNotNull(state.failedRecovery);
        assertEquals(1, count(profile, Bucket.VEHICLE_RECOVERY_SEARCH));

        controls.tick(id, VehicleController.STALL_SECONDS,
                mission.inboundX, mission.inboundY, VehicleLeg.DELIVERY_RUN);
        assertEquals(1, count(profile, Bucket.VEHICLE_RECOVERY_SEARCH),
                "the ordinary stall retry is still strictly after its threshold");
        controls.tick(id, 0.01f, mission.inboundX, mission.inboundY, VehicleLeg.DELIVERY_RUN);
        assertEquals(2, count(profile, Bucket.VEHICLE_RECOVERY_SEARCH));
        assertEquals(1, count(profile, Bucket.VEHICLE_RECOVERY_FAILED_REUSE));
        assertEquals(attempts, count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT));
        assertEquals(expanded, count(profile, Bucket.VEHICLE_RECOVERY_EXPANDED));
        assertEquals(0f, state.timeSinceProgress);
        assertEquals(5.5f, convoy.body(id).x);
        assertEquals(8.5f, convoy.body(id).y);

        controls.tickManual(id, 0f, 0f, 0f);
        assertNull(state.failedRecovery);
        controls.tick(id, VehicleController.STALL_SECONDS + 0.01f,
                mission.inboundX, mission.inboundY, VehicleLeg.DELIVERY_RUN);
        assertNotNull(state.failedRecovery);
        assertEquals(attempts * 2, count(profile, Bucket.VEHICLE_RECOVERY_ATTEMPT));

        controls.clearRoute(id);
        assertNull(state.failedRecovery);
        assertNull(state.routeXs);
        controls.tick(id, VehicleController.LOCAL_PLAN_FAILURE_REROUTE_SEC,
                mission.inboundX, mission.inboundY, VehicleLeg.DELIVERY_RUN);
        assertNotNull(state.failedRecovery);
        controls.tick(id, 0f, mission.inboundX.clone(), mission.inboundY.clone(), VehicleLeg.DELIVERY_RUN);
        assertNull(state.failedRecovery, "installing a new route resets the prior failed request");
    }

    private static int count(TickInnerProfile profile, Bucket bucket) {
        return profile.snapshot().counts[bucket.ordinal()];
    }

    private static ProgressiveVehicleField fields(boolean split) {
        NavigationGrid grid = new NavigationGrid(24, 18);
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 24; x++) {
                if (!split || x != 12) grid.setWalkableFloor(x, y);
            }
        }
        return ProgressiveVehicleField.capture(grid, new CellTopology(24, 18), 0);
    }

    private static final class Request {
        final VehicleControlComponent state = new VehicleControlComponent();
        ProgressiveVehicleField fields;
        VehicleType type = VehicleType.HEAVY_APC;
        int startX = 5, startY = 8, goalX = 18, goalY = 8;
        float facing = 90f, radius = 1f;

        Request(boolean split) {
            fields = fields(split);
            state.routeXs = new float[]{5.5f, 18.5f};
            state.routeYs = new float[]{8.5f, 8.5f};
            state.rerouteAvoidCount = 1;
            state.rerouteAvoidX[0] = 2;
            state.rerouteAvoidY[0] = 2;
        }

        FailedVehicleRecovery memo() {
            return new FailedVehicleRecovery(state, fields, type,
                    startX, startY, goalX, goalY, facing, radius);
        }

        boolean matches(FailedVehicleRecovery memo) {
            return memo.matches(state, fields, type, startX, startY, goalX, goalY, facing, radius);
        }

        VehicleRoutePlanner.RescueRoute search(boolean reuse) {
            return VehicleControlSystem.frozenRescue(state, fields, type,
                    startX, startY, goalX, goalY, facing, radius, reuse);
        }
    }
}
