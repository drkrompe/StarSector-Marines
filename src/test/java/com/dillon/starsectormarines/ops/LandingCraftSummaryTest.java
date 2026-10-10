package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandingCraftSummaryTest {
    @Test
    void ordinaryTripsFollowSelectedPersonnelInsteadOfAuthoredDrops() {
        Map<String, Object> props = summary(MissionSource.GENERATED, 8, 0, 7, 1);
        assertEquals("2 boat trips planned", props.get("landingCraftSchedule"));
        assertTrue(props.get("landingCraftCapacity").toString().contains("6 seats / boat trip"));
        assertEquals(ShuttleType.AEROSHUTTLE.spritePath, props.get("landingCraftImage"));
        assertEquals("Aboard Test carrier", props.get("landingCraftOrigin"));
    }

    @Test
    void companyTripCountExcludesEmployerTripsAndUnusedBoats() {
        Map<String, Object> props = summary(MissionSource.STORY, 5, 3, 12, 6);
        assertEquals("2 boat trips planned", props.get("landingCraftSchedule"));
        assertTrue(props.get("landingCraftCapacity").toString().startsWith("6 available"));
    }

    @Test
    void debugIgnoresEmployerAndLabelsTheOverride() {
        Map<String, Object> props = summary(MissionSource.DEBUG, 5, 3, 12, 1);
        assertEquals("5 boat trips planned", props.get("landingCraftSchedule"));
        assertEquals("DEBUG override", props.get("landingCraftOrigin"));
    }

    @Test
    void missingBoatsDoNotPretendFallbackAssignmentsAreCompanyCraft() {
        assertEquals("Company lift unavailable", summary(MissionSource.GENERATED, 5, 3, 12, 0)
                .get("landingCraftSchedule"));
        assertEquals("Company lift unavailable", summary(MissionSource.STORY, 5, 3, 12, 0)
                .get("landingCraftSchedule"));
        assertEquals("Employer lift provided", summary(MissionSource.STORY, 5, 5, 0, 0)
                .get("landingCraftSchedule"));
        assertEquals("Company lift unavailable", summary(MissionSource.DEBUG, 5, 3, 12, 0)
                .get("landingCraftSchedule"));
    }

    @Test
    void stationingDoesNotClaimTheFleetBoats() {
        Map<String, Object> props = summary(MissionSource.STATIONING, 5, 0, 12, 6);
        assertEquals("Local lifts", props.get("landingCraftTitle"));
        assertEquals("No company boats committed", props.get("landingCraftSchedule"));
    }

    private static Map<String, Object> summary(MissionSource source, int drops, int employer,
                                                int personnel, int boats) {
        Mission mission = Mission.builder().id("test").name("Test").type(MissionType.SABOTAGE)
                .source(source).requiredDrops(drops).employerShuttles(employer).build();
        Map<String, Object> props = BriefingViewModel.baseProps();
        BriefingViewModel.putLandingCraft(props, mission,
                Collections.nCopies(boats, FittedBoat.standard(ShuttleType.AEROSHUTTLE)),
                personnel, "Test carrier");
        return props;
    }
}
