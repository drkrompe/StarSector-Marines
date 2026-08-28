package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The debug board's CONQUEST faction-comparison group. */
class DebugFactionComparisonMissionTest {

    private static List<Mission> group(String planetName, long randomSeed) {
        return MissionGenerator.debugFactionComparisonMissions(
                planetName, new Random(randomSeed), 20);
    }

    @Test
    void everyCataloguedRosterProfileGetsAnEntry() {
        List<Mission> missions = group("Test Colony", 11L);

        Set<String> expected = new LinkedHashSet<>();
        for (GroundRosterProfile profile : GroundRosterRegistry.profiles()) {
            expected.add(profile.id());
        }
        Set<String> covered = new LinkedHashSet<>();
        for (Mission mission : missions) {
            assertEquals(MissionType.CONQUEST, mission.type);
            assertEquals(OperationTier.FULL_STRENGTH, mission.tier);
            assertEquals(MissionSource.DEBUG, mission.source);
            assertNotNull(mission.defenderFactionOverride, mission.name);
            assertTrue(mission.name.startsWith("CONQUEST — "), mission.name);
            covered.add(GroundRosterRegistry
                    .resolve(mission.defenderFactionOverride).id());
        }
        assertEquals(expected, covered,
                "the group is derived from the roster catalog, so a newly catalogued"
                        + " profile appears on the board without a code change");
    }

    @Test
    void everyEntryLandsOnOneBattlefieldWithADistinctDefender() {
        List<Mission> missions = group("Test Colony", 11L);

        Long battlefield = missions.get(0).battleSeed;
        assertNotNull(battlefield);
        Set<String> defenders = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Mission mission : missions) {
            assertEquals(battlefield, mission.battleSeed,
                    "a per-entry seed would hand each faction a different map");
            assertTrue(defenders.add(mission.defenderFactionOverride),
                    "duplicate defender " + mission.defenderFactionOverride);
            assertTrue(names.add(mission.name), "duplicate name " + mission.name);
        }
    }

    @Test
    void theBattlefieldIsTheMissionsIdentityNotTheEntrysPlaceInTheGroup() {
        assertEquals(group("Test Colony", 11L).get(0).battleSeed,
                group("Test Colony", 999L).get(0).battleSeed,
                "the pinned map follows the mission, not the board's roll");
        assertNotEquals(group("Test Colony", 11L).get(0).battleSeed,
                group("Other Colony", 11L).get(0).battleSeed,
                "a different target world is a different battlefield");
    }

    @Test
    void ordinaryBoardEntriesKeepWallClockSeedingAndTheMarketsOwnDefender() {
        for (Mission mission : MissionGenerator.debugCivilianRescueMissions(
                "Test Colony", new Random(71L), 0)) {
            assertNull(mission.battleSeed, mission.name);
            assertNull(mission.defenderFactionOverride, mission.name);
        }
    }
}
