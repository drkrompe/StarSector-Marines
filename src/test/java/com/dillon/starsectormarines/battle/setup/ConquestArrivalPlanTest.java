package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.PostDeliveryDisposition;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConquestArrivalPlanTest {

    @Test
    void fortyCommittedDropsCycleThroughOneReusableAeroshuttlePair() {
        List<ShuttleAssignment> manifest = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            manifest.add(new ShuttleAssignment(ShuttleType.VALKYRIE, 1, 6));
        }

        try (BattleSimulation sim = BattleSetup.createConquest(
                42L, manifest, false, OperationTier.REINFORCED, RiskLevel.LOW,
                TargetProfile.NEUTRAL, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                new ShuttleArrivalPlan(MarineArrivalPolicy.PAIRED_HALF_SQUAD, 0))) {
            List<ShuttleMission> missions = missions(sim);
            assertEquals(2, missions.size());
            for (long aircraft : sim.getAirEntityIds()) {
                assertEquals(ShuttleType.AEROSHUTTLE,
                        sim.world().airType(aircraft));
            }
            ShuttleMission first = missions.get(0);
            ShuttleMission second = missions.get(1);
            assertEquals(0, first.manifestOrdinal);
            assertEquals(1, second.manifestOrdinal);
            assertEquals(20, first.totalCycles);
            assertEquals(20, second.totalCycles);
            assertEquals(6, first.seatsPerSortie);
            assertEquals(6, second.seatsPerSortie);
            assertEquals(6, first.cycleLoadouts[0].length);
            assertEquals(6, second.cycleLoadouts[0].length);
            assertEquals(first.arrivalGroupId, second.arrivalGroupId);
            assertEquals(first.landingAreaId, second.landingAreaId);
            assertEquals(first.pendingDelay, second.pendingDelay);
            assertEquals(12, first.expectedArrivalStrength);
            assertEquals(12, second.expectedArrivalStrength);
            assertEquals(PostDeliveryDisposition.DEPART,
                    first.postDeliveryDisposition);
            assertEquals(PostDeliveryDisposition.DEPART,
                    second.postDeliveryDisposition);
            assertNotEquals(first.lzX + "," + first.lzY,
                    second.lzX + "," + second.lzY);
        }
    }

    @Test
    void selectedConquestCompanyExtendsTheReusablePairInsteadOfTruncatingSquads() {
        ShuttleArrivalPlan plan = new ShuttleArrivalPlan(
                MarineArrivalPolicy.PAIRED_HALF_SQUAD, 0);

        ShuttleArrivalPlan.ResolvedManifest resolved = plan.resolveManifest(
                List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 40, 6)),
                34 * 12);

        assertEquals(2, resolved.assignments().size());
        assertEquals(List.of(34, 34), resolved.assignments().stream()
                .map(assignment -> assignment.cycles).toList());
        assertEquals(34 * 12, resolved.assignments().stream()
                .mapToInt(assignment -> assignment.cycles
                        * assignment.seatsPerSortie)
                .sum());
    }

    @Test
    void employerAndPlayerCraftNeverShareAnArrivalGroup() {
        List<ShuttleAssignment> manifest = List.of(
                assignment(), assignment(), assignment(), assignment());
        try (BattleSimulation sim = BattleSetup.createConquest(
                77L, manifest, false, OperationTier.REINFORCED, RiskLevel.LOW,
                TargetProfile.NEUTRAL, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                new ShuttleArrivalPlan(MarineArrivalPolicy.PAIRED_HALF_SQUAD, 1))) {
            List<ShuttleMission> missions = missions(sim);
            assertNotEquals(missions.get(0).arrivalGroupId,
                    missions.get(1).arrivalGroupId);
            assertEquals(missions.get(1).arrivalGroupId,
                    missions.get(2).arrivalGroupId);
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
