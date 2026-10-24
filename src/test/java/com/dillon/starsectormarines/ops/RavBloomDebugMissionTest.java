package com.dillon.starsectormarines.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RavBloomDebugMissionTest {
    private static final List<DebugMissionCatalog.Scenario> RAV_BLOOM = List.of(
            DebugMissionCatalog.Scenario.CORDON_LINE, DebugMissionCatalog.Scenario.CORDON_ROAD,
            DebugMissionCatalog.Scenario.BROAD_FRONT, DebugMissionCatalog.Scenario.THE_BURN);

    @Test void everyRavBloomEntryBuildsAMissionWithoutAWorld() {
        for (var scenario : RAV_BLOOM) {
            assertTrue(scenario.ravBloom());
            Mission mission = scenario.mission();
            assertNotNull(mission.battleSeed, scenario.name());
            assertEquals(scenario, DebugMissionCatalog.scenario(mission));
            assertEquals("Rav Bloom", scenario.group);
            assertNotNull(DebugMissionCatalog.preset(mission));
        }
    }
}
