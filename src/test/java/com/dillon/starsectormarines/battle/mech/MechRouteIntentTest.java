package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.action.DefendTrack;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.PathRequestStatus;
import com.dillon.starsectormarines.battle.nav.ContinuousRoute;
import com.dillon.starsectormarines.battle.nav.ClearanceRoutePlanner.Point;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Bare roster and a controllable request boundary: no battle or route worker required. */
class MechRouteIntentTest {
    private final NavigationGrid grid = floor();
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(24, 16), null);
    private final int squadId = roster.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
    private final Squad squad = roster.getSquad(squadId);
    private final long mech = spawn(3);
    private final List<Long> requests = new ArrayList<>();
    private PathRequestStatus response = PathRequestStatus.PENDING;
    private int clears;
    private final BattleControl sim = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "world" -> roster.world();
                case "movement" -> roster.movement();
                case "identity" -> roster.identity();
                case "squad" -> roster.squad();
                case "squadOf" -> squad;
                case "isRiding" -> roster.isRiding((long) args[0]);
                case "squadMemberCount" -> roster.squadMemberCount((int) args[0]);
                case "squadMemberAt" -> roster.squadMemberArray((int) args[0])[(int) args[1]];
                case "getGrid" -> grid;
                case "targetOf" -> 0L;
                case "canEngage" -> false;
                case "liveUnitCount" -> roster.liveCount();
                case "liveUnitAt" -> roster.denseArray()[(int) args[0]];
                case "bodies" -> roster.bodies();
                case "asyncDefendTrackRoutes" -> null;
                case "requestPath" -> {
                    requests.add(MechRouteIntent.cellKey((int) args[1], (int) args[2]));
                    yield response;
                }
                case "advanceMovement" -> null;
                case "clearPath" -> {
                    clears++;
                    roster.movement().setPathRef((long) args[0], new int[0]);
                    yield null;
                }
                default -> throw new AssertionError("Unexpected query: " + method.getName());
            });

    @Test
    void ownershipResetDiscardsPendingCandidateAndPreviousRefusals() {
        MechRouteIntent route = MechRouteIntent.forMember(mech, "engage", 7, sim);
        response = PathRequestStatus.FAILED;
        route.moveToward(mech, 12, 4, sim);
        response = PathRequestStatus.PENDING;
        route.moveToward(mech, 13, 5, sim);
        assertTrue(route.pending());
        assertTrue(route.rejected(12, 4));
        route.reset();
        assertFalse(route.pending());
        assertFalse(route.rejected(12, 4));
        MechRouteIntent.forMember(mech, "engage", 7, sim).moveToward(mech, 12, 4, sim);
        assertEquals(MechRouteIntent.cellKey(12, 4), requests.get(2));
    }

    @Test
    void pendingCandidateSurvivesNewScoresAndFailurePermitsAnAlternative() {
        MechRouteIntent route = MechRouteIntent.forMember(mech, "engage", 7, sim);
        assertEquals(PathRequestStatus.PENDING, route.moveToward(mech, 12, 4, sim));
        assertEquals(PathRequestStatus.PENDING, route.moveToward(mech, 13, 5, sim));
        assertEquals(List.of(MechRouteIntent.cellKey(12, 4), MechRouteIntent.cellKey(12, 4)), requests);
        assertEquals(0, clears);
        response = PathRequestStatus.FAILED;
        assertEquals(PathRequestStatus.FAILED, route.moveToward(mech, 13, 5, sim));
        response = PathRequestStatus.PENDING;
        assertEquals(PathRequestStatus.FAILED, route.moveToward(mech, 12, 4, sim));
        assertEquals(3, requests.size(), "known refusals do not requeue expensive work");
        route.moveToward(mech, 13, 5, sim);
        assertEquals(MechRouteIntent.cellKey(13, 5), requests.get(3));
    }

    @Test
    void topologyChangeAllowsRefusedDestinationToBeTriedAgain() {
        response = PathRequestStatus.FAILED;
        MechRouteIntent.forMember(mech, "engage", 7, sim).moveToward(mech, 12, 4, sim);
        MechRouteIntent.forMember(mech, "engage", 7, sim).moveToward(mech, 12, 4, sim);
        assertEquals(1, requests.size());
        grid.setWalkable(20, 10, false);
        MechRouteIntent.forMember(mech, "engage", 7, sim).moveToward(mech, 12, 4, sim);
        assertEquals(2, requests.size());
    }

    @Test
    void patrolDoesNotTreatAPendingProofAsAnUnreachableWaypoint() {
        assertTrue(PatrolMotion.moveToward(mech, sim, 12, 4));
        assertTrue(PatrolMotion.moveToward(mech, sim, 12, 4));
        response = PathRequestStatus.FAILED;
        assertFalse(PatrolMotion.moveToward(mech, sim, 12, 4));
    }

    @Test
    void formationFallbackWaitsForTheSelectedCellProofToFail() {
        long follower = spawn(4);
        squad.assignedObjective = ObjectiveAssignment.defendTrack(squadId, 12, 7);
        DefendTrack action = new DefendTrack(12, 7);
        action.execute(follower, squad, sim);
        action.execute(follower, squad, sim);
        assertEquals(List.of(MechRouteIntent.cellKey(11, 8), MechRouteIntent.cellKey(11, 8)), requests);
        response = PathRequestStatus.FAILED;
        action.execute(follower, squad, sim);
        response = PathRequestStatus.PENDING;
        action.execute(follower, squad, sim);
        assertEquals(MechRouteIntent.cellKey(12, 7), requests.get(3));
        assertEquals(0, clears);
    }

    @Test
    void refusedMoveOrderHandsBackWhilePendingOrderRemainsActive() {
        MechMoveOrderService service = new MechMoveOrderService();
        MechMoveOrderSystem system = new MechMoveOrderSystem(service);
        service.activate(mech, new MechMoveOrderService.ActiveOrder(12, 4, 12, 4));
        assertTrue(system.executeIfActive(mech, squad, sim));
        assertNotNull(service.activeOrder(mech));
        response = PathRequestStatus.FAILED;
        assertFalse(system.executeIfActive(mech, squad, sim));
        assertNull(service.activeOrder(mech));
    }

    @Test
    void boundedImprovementMeasuresPhysicalDistanceRatherThanLatticePointCount() {
        List<Point> direct = new ArrayList<>();
        for (int i = 0; i <= 40; i++) direct.add(new Point(i * 0.1f, 0f));
        assertTrue(MechRouteIntent.worthWalking(new ContinuousRoute(4, 0, .6f, 0L, direct)));
        assertFalse(MechRouteIntent.worthWalking(new ContinuousRoute(4, 0, .6f, 0L,
                List.of(new Point(0, 0), new Point(0, 20), new Point(4, 0)))));
    }

    @Test
    void pendingImprovementRetainsItsDetourLimitWhenResumed() {
        MechRouteIntent route = MechRouteIntent.forMember(mech, "improve", 7, sim);
        assertEquals(PathRequestStatus.PENDING, route.moveToward(mech, 12, 4, sim, true));
        roster.movement().setContinuousRouteRef(mech, new ContinuousRoute(12, 4, .6f,
                grid.topologyRevision(), List.of(new Point(3.5f, 3.5f), new Point(3.5f, 14f),
                new Point(20f, 14f), new Point(20f, 4.5f), new Point(12.5f, 4.5f))));
        response = PathRequestStatus.READY;
        assertTrue(route.resume(mech, sim));
        assertFalse(route.pending());
        assertTrue(route.rejected(12, 4));
        assertEquals(1, clears);
    }

    @Test
    void resolvedPerchWithBlockedActualLaneIsNotSelectedForever() {
        grid.setWalkable(5, 4, false);
        assertTrue(grid.hasLineOfFire(3.5f, 3.5f, 10.5f, 4.5f));
        assertFalse(grid.hasLineOfFire(3.5f, 4f, 10.5f, 4.5f));
        roster.world().setPos(mech, 3.5f, 4f);
        roster.movement().setContinuousRouteRef(mech, new ContinuousRoute(3, 3, .6f,
                grid.topologyRevision(), List.of(new Point(3.5f, 3.5f), new Point(3.5f, 4f))).withCompleted());
        MechRouteIntent route = MechRouteIntent.forMember(mech, "engage", 7, sim);
        assertTrue(route.rejectSettledPerch(mech, 10.5f, 4.5f, 0f, 20f, sim));
        assertTrue(route.rejected(3, 3));
        assertEquals(PathRequestStatus.FAILED, route.moveToward(mech, 3, 3, sim),
                "a refused perch is not made usable by its old arrival witness");
        assertFalse(route.rejectSettledPerch(mech, 10.5f, 4.5f, 0f, 20f, sim));
        assertEquals(1, clears, "an already refused witness is not cleared repeatedly");
        route.moveToward(mech, 12, 4, sim);
        assertFalse(route.rejectSettledPerch(mech, 10.5f, 4.5f, 0f, 20f, sim));
        assertTrue(route.pending());
    }

    private long spawn(int x) {
        long id = roster.spawn(MechVariant.BULWARK.applyTo(new EntitySpec(
                "mech", Faction.MARINE, UnitType.HEAVY_MECH, x, 3).squad(squadId)));
        roster.world().attachMechLoadout(id, MechVariant.BULWARK.createLoadout(MechRole.BALANCED));
        squad.aliveMembers++;
        if (squad.leaderId == 0L) squad.leaderId = id;
        return id;
    }

    private static NavigationGrid floor() {
        NavigationGrid grid = new NavigationGrid(24, 16);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
