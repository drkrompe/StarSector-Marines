package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugTransportManifestTest {

    @Test
    void debugSelectionIsTheExactPhysicalRosterAndCoversEveryDrop() {
        Mission mission = mission(MissionSource.DEBUG, 11, 4);

        List<ShuttleAssignment> manifest = DetachmentResolver.buildShuttleManifest(
                mission, Arrays.asList(
                        ShuttleType.MULE, ShuttleType.MULE, ShuttleType.MULE));

        assertEquals(0, DetachmentResolver.employerPhysicalShipCount(mission));
        assertEquals(3, manifest.size());
        assertAssignment(manifest.get(0), ShuttleType.MULE, 4);
        assertAssignment(manifest.get(1), ShuttleType.MULE, 4);
        assertAssignment(manifest.get(2), ShuttleType.MULE, 3);
    }

    @Test
    void civilianRescueDebugSourceUsesTheSameOverride() {
        Mission mission = mission(MissionSource.DEBUG_CIVILIAN_RESCUE, 5, 3);

        List<ShuttleAssignment> manifest = DetachmentResolver.buildShuttleManifest(
                mission, Arrays.asList(ShuttleType.KITE, ShuttleType.KITE));

        assertEquals(2, manifest.size());
        assertAssignment(manifest.get(0), ShuttleType.KITE, 3);
        assertAssignment(manifest.get(1), ShuttleType.KITE, 2);
    }

    @Test
    void productionMissionStillCoSourcesEmployerAeroshuttles() {
        Mission mission = mission(MissionSource.GENERATED, 11, 4);

        List<ShuttleAssignment> manifest = DetachmentResolver.buildShuttleManifest(
                mission, Arrays.asList(ShuttleType.MULE, ShuttleType.MULE));

        assertEquals(3, DetachmentResolver.employerPhysicalShipCount(mission));
        assertEquals(5, manifest.size());
        assertAssignment(manifest.get(0), ShuttleType.AEROSHUTTLE, 2);
        assertAssignment(manifest.get(1), ShuttleType.AEROSHUTTLE, 1);
        assertAssignment(manifest.get(2), ShuttleType.AEROSHUTTLE, 1);
        assertAssignment(manifest.get(3), ShuttleType.MULE, 4);
        assertAssignment(manifest.get(4), ShuttleType.MULE, 3);
    }

    private static Mission mission(MissionSource source, int requiredDrops,
                                   int employerShuttles) {
        return new Mission("test", "Test", MissionType.ASSAULT, source,
                0, RiskLevel.LOW, "", "", 0.5f, 0.5f,
                null, null, requiredDrops, employerShuttles,
                "Test Colony", null);
    }

    private static void assertAssignment(ShuttleAssignment assignment,
                                         ShuttleType type, int cycles) {
        assertEquals(type, assignment.type);
        assertEquals(cycles, assignment.cycles);
    }
}
