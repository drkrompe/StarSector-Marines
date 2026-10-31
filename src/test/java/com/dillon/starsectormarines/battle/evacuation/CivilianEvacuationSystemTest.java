package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilianEvacuationSystemTest {

    @Test
    void marineMustReachShelterAndCohortWaitsWhenEscortFallsBehind() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload =
                CivilianEvacuationPayload.install(sim,
                        List.of(residential(12, 10)), 299L);
        assertNotNull(payload);
        long first = payload.entityId(0);
        int startX = sim.world().cellX(first);
        int startY = sim.world().cellY(first);

        for (int tick = 0; tick < 60; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(startX, sim.world().cellX(first));
        assertEquals(startY, sim.world().cellY(first));
        assertTrue(Paths.isEmpty(sim.movement().path(first)));
        assertTrue(sim.isCivilianShelterProtected());

        long marine = sim.spawn(new EntitySpec("rescue marine", Faction.MARINE,
                UnitType.MARINE, 2, 2));
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.isCivilianShelterProtected());
        assertTrue(Paths.isEmpty(sim.movement().path(first)));

        sim.world().setCellPos(marine,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(sim.isCivilianShelterProtected());
        assertTrue(sim.isCivilianEvacuationTriggered());

        sim.releaseFromRegistry(marine);
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(Paths.isEmpty(sim.movement().path(first)));
        assertTrue(sim.getCivilianEvacuationProgress().unescorted() > 0,
                "ordinary rescue publishes the actual escort wait, not only story missions");
    }

    @Test
    void shelterGarrisonCannotOpenItsOwnBarricadeOrEscortCivilians() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 306L);
        assertNotNull(payload);
        int squadId = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
        sim.registerShelterGuardSquad(squadId);
        sim.spawn(new EntitySpec("shelter guard", Faction.ALLY,
                UnitType.MILITIA, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY).squad(squadId));

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.isCivilianShelterProtected(),
                "the holdout cannot count as its own responding relief force");

        long responder = sim.spawn(new EntitySpec("responder", Faction.MARINE,
                UnitType.MARINE, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY));
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.isCivilianEvacuationTriggered());
        long civilian = payload.entityId(0);
        sim.releaseFromRegistry(responder);
        sim.clearPath(civilian);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(Paths.isEmpty(sim.movement().path(civilian)),
                "stationary shelter guards are not civilian escort anchors");
    }

    @Test
    void wipedObservedResponseForceReleasesTheShelterLastStand() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 307L);
        assertNotNull(payload);
        int squadId = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
        sim.registerShelterGuardSquad(squadId);
        sim.spawn(new EntitySpec("shelter guard", Faction.ALLY,
                UnitType.MILITIA, payload.placement.shelterX,
                payload.placement.shelterY).squad(squadId));
        long responder = sim.spawn(new EntitySpec("responder", Faction.MARINE,
                UnitType.MARINE, 2, 2));

        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.isCivilianShelterProtected());
        sim.releaseFromRegistry(responder);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(sim.isCivilianShelterProtected());
        assertTrue(sim.isCivilianEvacuationTriggered(),
                "the garrison must become targetable so elimination can resolve");
    }

    @Test
    void installedCohortRoutesBoardsAndCompletesWithoutCombatVictoryInference() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload =
                CivilianEvacuationPayload.install(sim,
                        List.of(residential(12, 10)), 300L);
        assertNotNull(payload);
        long escort = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                UnitType.MARINE,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY));

        // The marine leads: a step toward the lift whenever every walker has caught up with it.
        int liftX = payload.placement.liftX, liftY = payload.placement.liftY;
        for (int tick = 0; tick < 2_400 && !sim.isComplete(); tick++) {
            if (tick % 6 == 0 && everyoneWithin(sim, escort, 3)) {
                int x = sim.world().cellX(escort), y = sim.world().cellY(escort);
                sim.world().setCellPos(escort, x + Integer.compare(liftX, x), y + Integer.compare(liftY, y));
            }
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.isComplete());
        assertEquals(Faction.MARINE, sim.getWinner());
        CivilianEvacuationReport report =
                sim.getCivilianEvacuationTracker().report();
        assertNotNull(report);
        assertEquals(8, report.evacuated);
        assertEquals(0, report.lost);
        assertEquals(1, sim.liveUnitCount());
        for (int i = 0; i < payload.size(); i++) {
            assertEquals(0L, sim.resolveUnit(payload.entityId(i)));
            assertFalse(sim.getCivilianEvacuationTracker()
                    .state(payload.entityId(i))
                    == CivilianEvacuationTracker.State.LOST);
        }
    }

    @Test
    void escortLeashIsEvaluatedForEachCivilianIndependently() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 302L);
        assertNotNull(payload);
        long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                UnitType.MARINE, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY));
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.isCivilianEvacuationTriggered());

        long protectedCivilian = payload.entityId(0);
        long distantCivilian = payload.entityId(1);
        sim.world().setCellPos(protectedCivilian, 3, 3);
        sim.world().setCellPos(distantCivilian, 24, 20);
        sim.world().setCellPos(marine, 3, 7);
        sim.clearPath(protectedCivilian);
        sim.clearPath(distantCivilian);

        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(Paths.isEmpty(sim.movement().path(protectedCivilian)));
        assertTrue(Paths.isEmpty(sim.movement().path(distantCivilian)));
    }

    @Test
    void aCivilianFollowsItsSquadEvenAwayFromTheLift() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 303L);
        assertNotNull(payload);
        long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                UnitType.MARINE, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY));
        sim.advance(BattleSimulation.TICK_DT);

        // The squad walks off the far side of the civilian from the lift; the civilian goes after it.
        long civilian = payload.entityId(0);
        int[] far = farCorner(payload.placement);
        int cx = (far[0] + payload.placement.liftX) / 2, cy = (far[1] + payload.placement.liftY) / 2;
        sim.world().setCellPos(civilian, cx, cy);
        sim.world().setCellPos(marine, cx + Integer.compare(far[0], cx) * 6, cy + Integer.compare(far[1], cy) * 6);
        sim.clearPath(civilian);

        sim.advance(BattleSimulation.TICK_DT);

        int[] path = sim.movement().path(civilian);
        assertFalse(Paths.isEmpty(path), "a civilian with its squad in reach walks after it");
        assertTrue(Math.abs(Paths.destX(path) - sim.world().cellX(marine)) <= 1
                && Math.abs(Paths.destY(path) - sim.world().cellY(marine)) <= 1,
                "it walks to the squad, not to the lift");
    }

    @Test
    void aCivilianStandsBesideItsSquadAndWaitsOnceTheSquadHasGoneOutOfReach() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 308L);
        assertNotNull(payload);
        long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                UnitType.MARINE, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY));
        sim.advance(BattleSimulation.TICK_DT);

        // Away from the lift, in the far corner, so standing is not walking in to board.
        long civilian = payload.entityId(0);
        int[] far = farCorner(payload.placement);
        int inward = far[0] < 13 ? 1 : -1;
        sim.world().setCellPos(civilian, far[0], far[1]);
        sim.world().setCellPos(marine, far[0] + inward, far[1]);
        sim.clearPath(civilian);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(Paths.isEmpty(sim.movement().path(civilian)), "beside its squad it stands");

        sim.world().setCellPos(marine, far[0] + inward * (CivilianEvacuationSystem.FOLLOW_LEASH + 2), far[1]);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(Paths.isEmpty(sim.movement().path(civilian)), "a squad out of reach is lost; it waits");
        assertTrue(sim.getCivilianEvacuationProgress().unescorted() > 0);
    }

    @Test
    void nearbyThreatRoutesCivilianToScreenedSideOfMarine() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 304L);
        assertNotNull(payload);
        long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                UnitType.MARINE, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY));
        sim.advance(BattleSimulation.TICK_DT);

        long civilian = payload.entityId(0);
        sim.world().setCellPos(civilian, 10, 12);
        sim.world().setCellPos(marine, 12, 10);
        long threat = sim.spawn(new EntitySpec("runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 14, 10)
                .role(UnitRole.SWARM_PRESSURE));
        sim.clearPath(civilian);

        sim.advance(BattleSimulation.TICK_DT);

        int[] path = sim.movement().path(civilian);
        assertFalse(Paths.isEmpty(path));
        assertFalse(Paths.destX(path) == payload.placement.liftX
                && Paths.destY(path) == payload.placement.liftY);
        int fromMarineX = Paths.destX(path) - sim.world().cellX(marine);
        int fromMarineY = Paths.destY(path) - sim.world().cellY(marine);
        int awayFromThreatX = sim.world().cellX(marine) - sim.world().cellX(threat);
        int awayFromThreatY = sim.world().cellY(marine) - sim.world().cellY(threat);
        assertTrue(fromMarineX * awayFromThreatX
                + fromMarineY * awayFromThreatY > 0);
    }

    /** Whether every active civilian stands within {@code cells} of {@code marine}. */
    private static boolean everyoneWithin(BattleSimulation sim, long marine, int cells) {
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        for (int i = 0; i < tracker.registeredCount(); i++) {
            long id = tracker.entityIdAt(i);
            if (tracker.state(id) != CivilianEvacuationTracker.State.ACTIVE
                    || sim.resolveUnit(id) == 0L) continue;
            if (Math.abs(sim.world().cellX(id) - sim.world().cellX(marine)) > cells
                    || Math.abs(sim.world().cellY(id) - sim.world().cellY(marine)) > cells) return false;
        }
        return true;
    }

    @Test
    void boardingCreatesNoCorpseOrLoss() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload =
                CivilianEvacuationPayload.install(sim,
                        List.of(residential(12, 10)), 301L);
        assertNotNull(payload);

        long first = payload.entityId(0);
        sim.world().setCellPos(first, payload.placement.liftX,
                payload.placement.liftY);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(CivilianEvacuationTracker.State.EVACUATED,
                sim.getCivilianEvacuationTracker().state(first));
        assertEquals(0L, sim.resolveUnit(first));
        assertEquals(0, sim.getCivilianEvacuationTracker().lostCount());
    }

    @Test
    void physicalPickupMustLandBeforeCiviliansBoard() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 305L);
        assertNotNull(payload);
        float pickupX = payload.placement.liftX + 0.5f;
        float pickupY = payload.placement.liftY + 0.5f;
        long shuttleId = sim.spawnShuttle(ShuttleType.VALKYRIE,
                Faction.CIVILIAN, pickupX, pickupY,
                pickupX, pickupY, pickupX, pickupY + 10f, 0f);
        ShuttleMission mission = sim.world().mission(shuttleId);
        mission.marinesRemaining = 0;
        mission.awaitingEvacuees = true;
        mission.evacueeCapacity = payload.size();
        assertTrue(sim.attachCivilianPickupShuttle(shuttleId));
        for (int i = 0; i < payload.size(); i++) {
            sim.world().setCellPos(payload.entityId(i),
                    payload.placement.liftX, payload.placement.liftY);
        }

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(ShuttleState.INCOMING, mission.state);
        assertEquals(payload.size(),
                sim.getCivilianEvacuationTracker().activeCount());
        assertEquals(0, mission.evacueesAboard);

        // Over the pickup point, so the run in is over — but a vertical lift
        // settles onto a point rather than arriving on it, and the civilians
        // wait for its weight to be on the ground.
        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(ShuttleState.PAD_DESCENT, mission.state);
        assertEquals(0, mission.evacueesAboard, "boarded a craft still in the air");

        for (int i = 0; i < 200 && mission.state == ShuttleState.PAD_DESCENT; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(ShuttleState.LANDED, mission.state);
        assertEquals(payload.size(), mission.evacueesAboard);
        assertEquals(0, sim.getCivilianEvacuationTracker().activeCount());
        assertFalse(mission.awaitingEvacuees);
    }

    @Test
    void aHoldoutWaitsBehindItsOwnDoorUntilAMarineReachesIt() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 299L);
        assertNotNull(payload);
        int[] door = farCorner(payload.placement);
        long holdout = sim.spawn(new EntitySpec("holdout", Faction.CIVILIAN,
                UnitType.CIVILIAN, door[0], door[1]).role(UnitRole.VIP));
        assertEquals(2, sim.addCivilianHoldout(door[0], door[1], door[0] - 1, door[1] - 1,
                door[0] + 1, door[1] + 1, -1, List.of(holdout)));
        assertEquals(payload.size() + 1, sim.getCivilianEvacuationTracker().expectedCount());

        long marine = sim.spawn(new EntitySpec("rescue marine", Faction.MARINE,
                UnitType.MARINE, payload.placement.shelterApproachX, payload.placement.shelterApproachY));
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.getCivilianRedoubts().get(0).released(), "the shelter opens to the marine at its door");
        assertFalse(sim.getCivilianRedoubts().get(1).released(), "the holdout is nobody's door yet");
        assertTrue(Paths.isEmpty(sim.movement().path(holdout)));

        sim.world().setCellPos(marine, door[0], door[1]);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.getCivilianRedoubts().get(1).released());
        assertEquals(1, sim.getCivilianRedoubts().get(1).walking());
    }

    @Test
    void aHoldoutWithNoGarrisonFallsToTheSwarmAtItsWalls() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 299L);
        assertNotNull(payload);
        int[] door = farCorner(payload.placement);
        long holdout = sim.spawn(new EntitySpec("holdout", Faction.CIVILIAN,
                UnitType.CIVILIAN, door[0], door[1]).role(UnitRole.VIP));
        sim.addCivilianHoldout(door[0], door[1], door[0] - 1, door[1] - 1,
                door[0] + 1, door[1] + 1, -1, List.of(holdout));
        sim.advance(BattleSimulation.TICK_DT);
        assertFalse(sim.getCivilianRedoubts().get(1).fallen(), "no swarm at the walls yet");

        sim.spawn(new EntitySpec("runner", Faction.DEFENDER, UnitType.MARINE, door[0] + 2, door[1]));
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.getCivilianRedoubts().get(1).fallen());
        assertEquals(CivilianEvacuationTracker.State.LOST, sim.getCivilianEvacuationTracker().state(holdout),
                "an overrun holdout's people leave the cohort");
    }

    @Test
    void aRallyHandsTheWholeGroupToAnotherEscortAndHoldsPastTheLeash() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential(12, 10)), 311L);
        assertNotNull(payload);
        long first = sim.spawn(new EntitySpec("first", Faction.MARINE,
                UnitType.MARINE, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY));
        sim.advance(BattleSimulation.TICK_DT);

        // Two people beside the first escort, away from the lift; the second escort across the map, past the leash.
        int[] far = farCorner(payload.placement);
        int inward = far[0] < 13 ? 1 : -1;
        long a = payload.entityId(0), b = payload.entityId(1);
        sim.world().setCellPos(a, far[0], far[1]);
        sim.world().setCellPos(b, far[0], far[1] + (far[1] < 11 ? 1 : -1));
        sim.world().setCellPos(first, far[0] + inward, far[1]);
        long second = sim.spawn(new EntitySpec("second", Faction.MARINE, UnitType.MARINE,
                far[0] + inward * (CivilianEvacuationSystem.FOLLOW_LEASH + 4), far[1]));
        long swarm = sim.spawn(new EntitySpec("rav", Faction.DEFENDER, UnitType.MARINE, 13, 1));
        sim.clearPath(a);
        sim.clearPath(b);
        sim.advance(BattleSimulation.TICK_DT);
        List<Long> group = sim.civilianRallyGroup(a);
        assertTrue(group.contains(a) && group.contains(b), "both follow the first escort: " + group);

        assertEquals(0, sim.rallyCivilians(a, swarm), "a rally is only to the company's own");
        assertEquals(group.size(), sim.rallyCivilians(a, second));
        sim.world().setCellPos(swarm, 13, 21);
        sim.advance(BattleSimulation.TICK_DT);

        for (long civilian : new long[]{a, b}) {
            int[] path = sim.movement().path(civilian);
            assertFalse(Paths.isEmpty(path), "a rallied civilian walks to its new escort past the leash");
            assertTrue(Math.abs(Paths.destX(path) - sim.world().cellX(second)) <= 1,
                    "it walks to the new escort, not the one beside it");
        }
    }

    /** A cell in the grid corner farthest from the lift, clear of the shelter. */
    private static int[] farCorner(CivilianEvacuationPlacement placement) {
        return new int[]{placement.liftX < 13 ? 23 : 2, placement.liftY < 11 ? 19 : 2};
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(26, 22);
        for (int y = 0; y < 22; y++) {
            for (int x = 0; x < 26; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(26, 22));
    }

    private static PointOfInterest residential(int x, int y) {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                x - 2, y - 2, x + 2, y + 2, x, y, x, y);
    }
}
