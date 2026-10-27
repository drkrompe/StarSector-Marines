package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeployWarningsTest {
    @Test void woundedRecoverOnAClockInWholeHours() {
        assertEquals(15, DeployWarnings.hoursToFit(10.6f, 10f));
        assertEquals(24, DeployWarnings.hoursToFit(11f, 10f));
        assertEquals(0, DeployWarnings.hoursToFit(9f, 10f), "A timer already run reads as fit");
    }

    @Test void casualtiesLampSeparatesWoundedHoursFromBilletsToReplenish() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var roster = new MarineRoster();
            var squad = roster.createSquad();
            roster.recruitToSquad(squad.id(), 12);
            assertFalse(DeployWarnings.casualties(roster, Set.of(squad.id()), 0f, 0).lit());
            var members = roster.squadMembers(squad);
            roster.applySoldierOutcome(Map.of(members.get(0).id(), MarineSoldierStatus.WIA,
                    members.get(1).id(), MarineSoldierStatus.KIA), 0f, 1f);
            roster.shortenWoundedRecovery(members.get(0).id(), 0.5f);
            var lamp = DeployWarnings.casualties(roster, Set.of(squad.id()), 0f, 0);
            assertTrue(lamp.lit());
            List<String> notes = lamp.hint().notes();
            assertTrue(notes.stream().anyMatch(n -> n.contains("12 h")), notes.toString());
            assertTrue(notes.stream().anyMatch(n -> n.contains("1 empty")), notes.toString());
            assertFalse(DeployWarnings.casualties(roster, Set.of(), 0f, 0).lit(), "Only selected squads count");
        }
    }

    @Test void strengthLampLightsOnlyBelowTheRecommendation() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            assertTrue(DeployWarnings.undermanned(23, 24).lit());
            assertFalse(DeployWarnings.undermanned(24, 24).lit());
        }
    }
}
