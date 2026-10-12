package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugTransportManifestTest {

    @Test
    void debugSelectionIsTheExactPhysicalRosterAndCoversEveryDrop() {
        Mission mission = mission(MissionSource.DEBUG, 11, 4);

        List<ShuttleAssignment> manifest = DetachmentResolver.buildShuttleManifest(
                mission, boats(ShuttleType.MULE, ShuttleType.MULE, ShuttleType.MULE));

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
                mission, boats(ShuttleType.KITE, ShuttleType.KITE));

        assertEquals(2, manifest.size());
        assertAssignment(manifest.get(0), ShuttleType.KITE, 3);
        assertAssignment(manifest.get(1), ShuttleType.KITE, 2);
    }

    @Test
    void productionMissionStillCoSourcesEmployerAeroshuttles() {
        Mission mission = mission(MissionSource.GENERATED, 11, 4);

        List<ShuttleAssignment> manifest = DetachmentResolver.buildShuttleManifest(
                mission, boats(ShuttleType.MULE, ShuttleType.MULE));

        assertEquals(3, DetachmentResolver.employerPhysicalShipCount(mission));
        assertEquals(5, manifest.size());
        assertAssignment(manifest.get(0), ShuttleType.AEROSHUTTLE, 2);
        assertAssignment(manifest.get(1), ShuttleType.AEROSHUTTLE, 1);
        assertAssignment(manifest.get(2), ShuttleType.AEROSHUTTLE, 1);
        assertAssignment(manifest.get(3), ShuttleType.MULE, 4);
        assertAssignment(manifest.get(4), ShuttleType.MULE, 3);
    }

    @Test
    void understrengthProductionManifestEmbarksExactlyTheNamedForce() {
        Mission mission = mission(MissionSource.GENERATED, 11, 4);

        List<ShuttleAssignment> manifest =
                DetachmentResolver.buildShuttleManifestForPersonnel(
                        mission, boats(ShuttleType.VALKYRIE, ShuttleType.VALKYRIE), 25);

        assertEquals(5, manifest.size(), "three employer craft plus two player craft");
        ShuttleAssignment firstPlayer = manifest.get(3);
        ShuttleAssignment secondPlayer = manifest.get(4);
        assertEquals(12, firstPlayer.embarkedPersonnel);
        assertEquals(13, secondPlayer.embarkedPersonnel);
        assertEquals(2, secondPlayer.cycles);
        assertEquals(12, secondPlayer.seatsForCycle(0));
        assertEquals(1, secondPlayer.seatsForCycle(1));
        assertEquals(25, CampaignMarineDeployment.requiredSeats(manifest, 3));
    }

    @ParameterizedTest
    @EnumSource(value = MissionSource.class, names = {"STATIONING"}, mode = EnumSource.Mode.EXCLUDE)
    void selectedSquadGetsEveryReturnTripRegardlessOfOfferSource(MissionSource source) {
        Mission mission = mission(source, 2, 1);
        var boat = FittedBoat.standard(ShuttleType.HERMES);
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, List.of(boat), 12);
        int firstPlayer = DetachmentResolver.employerPhysicalShipCount(mission);
        assertEquals(12, CampaignMarineDeployment.requiredSeats(manifest, firstPlayer));
        var lift = manifest.get(firstPlayer);
        assertEquals(3, lift.cycles);
        assertEquals(4, lift.seatsPerSortie);
        assertEquals(boat, lift.airframe);
        for (int cycle = 0; cycle < 3; cycle++) assertEquals(4, lift.seatsForCycle(cycle));
        if (firstPlayer > 0) assertEquals(6, manifest.get(0).embarkedPersonnel);
    }

    @ParameterizedTest
    @EnumSource(ShuttleType.class)
    void storyLiftPreservesPartialFinalLoadsForEveryHull(ShuttleType type) {
        var mission = mission(MissionSource.STORY, 2, 1);
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(
                mission, boats(type), 13);
        var lift = manifest.get(1);
        assertEquals(13, lift.embarkedPersonnel);
        assertEquals((13 + type.capacity - 1) / type.capacity, lift.cycles);
        assertEquals(13, IntStream.range(0, lift.cycles)
                .map(lift::seatsForCycle).sum());
    }

    @ParameterizedTest
    @EnumSource(value = MissionSource.class, names = {"STATIONING"}, mode = EnumSource.Mode.EXCLUDE)
    void missingPlayerLiftNeverFabricatesReplacementBoats(MissionSource source) {
        Mission mission = mission(source, 5, 2);
        assertFalse(DetachmentResolver.hasRequiredTransport(mission, List.of()));
        assertFalse(DetachmentResolver.hasRequiredTransport(mission, null));
        List<ShuttleAssignment> manifest = DetachmentResolver.buildShuttleManifest(mission, List.of());
        assertEquals(source.isDebug() ? 0 : 2, manifest.size());
        assertTrue(DetachmentResolver.hasRequiredTransport(mission, boats(ShuttleType.KITE)),
                "one real boat can cover remaining drops through repeat trips");
    }

    @Test
    void stationingAndConquestKeepTheirOwnArrivalCommitments() {
        Mission local = mission(MissionSource.STATIONING, 5, 2);
        Mission conquest = Mission.builder(mission(MissionSource.STORY, 5, 2))
                .type(MissionType.CONQUEST).build();
        for (Mission mission : List.of(local, conquest)) {
            var boats = boats(ShuttleType.HERMES);
            assertFalse(DetachmentResolver.usesSelectedPersonnel(mission));
            assertEquals(DetachmentResolver.buildShuttleManifest(mission, boats),
                    DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, 12));
        }
    }

    @Test
    void emptyCompanySelectionDoesNotManufacturePassengers() {
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(
                mission(MissionSource.STORY, 2, 1), boats(ShuttleType.HERMES), 0);
        assertEquals(1, manifest.size(), "Only the employer craft remains");
        assertEquals(0, CampaignMarineDeployment.requiredSeats(manifest, 1));
    }

    @Test
    void completeEmployerLiftAndStationedLocalLiftRemainAvailable() {
        Mission story = mission(MissionSource.STORY, 5, 5);
        assertTrue(DetachmentResolver.hasRequiredTransport(story, List.of()));
        assertEquals(5, DetachmentResolver.buildShuttleManifest(story, List.of()).stream()
                .mapToInt(assignment -> assignment.cycles).sum());
        Mission local = mission(MissionSource.STATIONING, 5, 2);
        assertTrue(DetachmentResolver.hasRequiredTransport(local, List.of()));
        assertEquals(5, DetachmentResolver.buildShuttleManifest(local, List.of()).stream()
                .mapToInt(assignment -> assignment.cycles).sum());
        assertFalse(DetachmentResolver.hasRequiredTransport(mission(MissionSource.DEBUG, 5, 5), List.of()));
        assertFalse(DetachmentResolver.hasRequiredTransport(mission(MissionSource.GENERATED, 5, 5), List.of()),
                "ordinary operations still require lift for the selected company force");
    }

    /** The company's boats at standard fit; what is under test here is the split. */
    private static List<FittedBoat> boats(ShuttleType... patterns) {
        return Arrays.stream(patterns).map(FittedBoat::standard).toList();
    }

    private static Mission mission(MissionSource source, int requiredDrops,
                                   int employerShuttles) {
        return Mission.builder()
                .id("test")
                .name("Test")
                .type(MissionType.ASSAULT)
                .source(source)
                .risk(RiskLevel.LOW)
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(requiredDrops)
                .employerShuttles(employerShuttles)
                .targetPlanetName("Test Colony")
                .build();
    }

    private static void assertAssignment(ShuttleAssignment assignment,
                                         ShuttleType type, int cycles) {
        assertEquals(type, assignment.type);
        assertEquals(cycles, assignment.cycles);
    }
}
