package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConquestArrivalPolicyTest {

    @Test
    void conquestDefaultsToSixSeatSortiesButMissionCanOverrideTheDoctrine() {
        Mission paired = Mission.builder()
                .id("paired").name("Paired").type(MissionType.CONQUEST)
                .risk(RiskLevel.LOW).requiredDrops(2).build();
        Mission independent = Mission.builder(paired)
                .marineArrivalPolicy(MarineArrivalPolicy.INDEPENDENT_FULL_LOAD)
                .build();

        List<ShuttleType> transports = List.of(
                ShuttleType.VALKYRIE, ShuttleType.VALKYRIE);
        List<ShuttleAssignment> pairedManifest =
                DetachmentResolver.buildShuttleManifest(paired, transports);
        List<ShuttleAssignment> independentManifest =
                DetachmentResolver.buildShuttleManifest(independent, transports);

        assertEquals(MarineArrivalPolicy.PAIRED_HALF_SQUAD,
                paired.marineArrivalPolicy);
        assertEquals(ConquestArrivalConfig.DEFAULT,
                paired.conquestArrivalConfig());
        assertEquals(List.of(6, 6), pairedManifest.stream()
                .map(assignment -> assignment.seatsPerSortie).toList());
        assertEquals(List.of(12, 12), independentManifest.stream()
                .map(assignment -> assignment.seatsPerSortie).toList());
    }
}
