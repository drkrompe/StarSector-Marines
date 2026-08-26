package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.PostDeliveryDisposition;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConquestArrivalPlanTest {

    @Test
    void fortyCommittedDropsCycleThroughOneAeroshuttlePairPerDefaultLane() {
        List<ShuttleAssignment> manifest = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            manifest.add(new ShuttleAssignment(ShuttleType.VALKYRIE, 1, 6));
        }

        try (BattleSimulation sim = BattleSetup.createConquest(
                42L, manifest, false, OperationTier.REINFORCED, RiskLevel.LOW,
                TargetProfile.NEUTRAL, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                new ShuttleArrivalPlan(MarineArrivalPolicy.PAIRED_HALF_SQUAD, 0))) {
            List<ShuttleMission> missions = missions(sim);
            assertEquals(6, missions.size());
            for (long aircraft : sim.getAirEntityIds()) {
                assertEquals(ShuttleType.AEROSHUTTLE,
                        sim.world().airType(aircraft));
            }
            assertEquals(List.of(7, 7, 7, 7, 6, 6), missions.stream()
                    .map(mission -> mission.totalCycles).toList());
            assertEquals(40 * 6, missions.stream()
                    .mapToInt(mission -> mission.totalCycles
                            * mission.seatsPerSortie)
                    .sum());

            Map<Integer, List<ShuttleMission>> byArea = missions.stream()
                    .collect(Collectors.groupingBy(mission -> mission.landingAreaId));
            assertEquals(3, byArea.size());
            for (List<ShuttleMission> pair : byArea.values()) {
                assertEquals(2, pair.size());
                assertEquals(pair.get(0).arrivalGroupId,
                        pair.get(1).arrivalGroupId);
                assertNotEquals(pair.get(0).pendingDelay,
                        pair.get(1).pendingDelay);
                assertNotEquals(pair.get(0).rearmDelay,
                        pair.get(1).rearmDelay);
                assertNotEquals(pair.get(0).lzX + "," + pair.get(0).lzY,
                        pair.get(1).lzX + "," + pair.get(1).lzY);
            }
            for (ShuttleMission mission : missions) {
                assertEquals(6, mission.seatsPerSortie);
                assertEquals(6, mission.cycleLoadouts[0].length);
                assertEquals(12, mission.expectedArrivalStrength);
                assertEquals(PostDeliveryDisposition.DEPART,
                        mission.postDeliveryDisposition);
                assertTrue(mission.pendingDelay >= 0f);
                assertTrue(mission.rearmDelay
                        >= ShuttleMission.DEFAULT_REARM_DELAY_SEC);
            }
        }
    }

    @Test
    void selectedConquestCompanyBalancesEverySquadAcrossTheConfiguredPairs() {
        ShuttleArrivalPlan plan = new ShuttleArrivalPlan(
                MarineArrivalPolicy.PAIRED_HALF_SQUAD, 0);

        ShuttleArrivalPlan.ResolvedManifest resolved = plan.resolveManifest(
                List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 40, 6)),
                34 * 12);

        assertEquals(6, resolved.assignments().size());
        assertEquals(List.of(12, 12, 11, 11, 11, 11), resolved.assignments().stream()
                .map(assignment -> assignment.cycles).toList());
        assertEquals(34 * 12, resolved.assignments().stream()
                .mapToInt(assignment -> assignment.cycles
                        * assignment.seatsPerSortie)
                .sum());
    }

    @Test
    void missionCanConfigureTwoPairsAtEachOfTwoDropZones() {
        List<ShuttleAssignment> manifest = new ArrayList<>();
        for (int i = 0; i < 40; i++) manifest.add(assignment());
        ConquestArrivalConfig config = new ConquestArrivalConfig(2, 2, 0f);

        try (BattleSimulation sim = BattleSetup.createConquest(
                91L, manifest, false, OperationTier.REINFORCED, RiskLevel.LOW,
                TargetProfile.NEUTRAL, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                new ShuttleArrivalPlan(MarineArrivalPolicy.PAIRED_HALF_SQUAD,
                        0, config))) {
            List<ShuttleMission> missions = missions(sim);
            assertEquals(8, missions.size());
            assertEquals(List.of(5, 5, 5, 5, 5, 5, 5, 5), missions.stream()
                    .map(mission -> mission.totalCycles).toList());
            Map<Integer, Long> craftPerArea = missions.stream()
                    .collect(Collectors.groupingBy(mission -> mission.landingAreaId,
                            Collectors.counting()));
            assertEquals(2, craftPerArea.size());
            assertTrue(craftPerArea.values().stream().allMatch(count -> count == 4L));
            assertTrue(missions.stream().allMatch(mission ->
                    mission.rearmDelay == ShuttleMission.DEFAULT_REARM_DELAY_SEC));
        }
    }

    @Test
    void employerAndPlayerCraftNeverShareAnArrivalGroup() {
        List<ShuttleAssignment> manifest = List.of(
                assignment(), assignment(), assignment(), assignment(),
                assignment(), assignment(), assignment(), assignment());
        try (BattleSimulation sim = BattleSetup.createConquest(
                77L, manifest, false, OperationTier.REINFORCED, RiskLevel.LOW,
                TargetProfile.NEUTRAL, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                new ShuttleArrivalPlan(MarineArrivalPolicy.PAIRED_HALF_SQUAD, 2))) {
            List<ShuttleMission> missions = missions(sim);
            assertEquals(missions.get(0).arrivalGroupId,
                    missions.get(1).arrivalGroupId);
            assertNotEquals(missions.get(0).arrivalGroupId,
                    missions.get(2).arrivalGroupId);
            assertEquals(missions.get(2).arrivalGroupId,
                    missions.get(3).arrivalGroupId);
            assertTrue(missions.subList(2, missions.size()).stream()
                    .map(mission -> mission.landingAreaId).distinct().count() == 3L);
        }
    }

    private static ShuttleAssignment assignment() {
        return new ShuttleAssignment(ShuttleType.VALKYRIE, 1, 6);
    }

    private static List<ShuttleMission> missions(BattleSimulation sim) {
        List<ShuttleMission> missions = new ArrayList<>();
        for (long aircraft : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(aircraft);
            if (mission != null) missions.add(mission);
        }
        missions.sort(Comparator.comparingInt(mission -> mission.manifestOrdinal));
        return missions;
    }
}
