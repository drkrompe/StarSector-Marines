package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.marine.BoatWorkshop;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.TestPurse;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeployWarningsTest {
    @Test void repairClockReadsInWholeHours() {
        assertEquals(15, DeployWarnings.hoursToFit(10.6f, 10f));
        assertEquals(24, DeployWarnings.hoursToFit(11f, 10f));
        assertEquals(0, DeployWarnings.hoursToFit(9f, 10f), "A timer already run reads as fit");
    }

    @Test void casualtiesLampCountsEmptyBilletsToReplenish() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var roster = new MarineRoster();
            var squad = roster.createSquad();
            roster.recruitToSquad(squad.id(), 12);
            assertFalse(DeployWarnings.casualties(roster, Set.of(squad.id()), 0).lit());
            var members = roster.squadMembers(squad);
            roster.applySoldierOutcome(Map.of(members.get(1).id(), MarineSoldierStatus.KIA));
            var lamp = DeployWarnings.casualties(roster, Set.of(squad.id()), 0);
            assertTrue(lamp.lit());
            List<String> notes = lamp.hint().notes();
            assertTrue(notes.stream().anyMatch(n -> n.contains("1 empty")), notes.toString());
            assertFalse(DeployWarnings.casualties(roster, Set.of(), 0).lit(), "Only selected squads count");
        }
    }

    @Test void strengthLampLightsOnlyBelowTheRecommendation() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            assertTrue(DeployWarnings.undermanned(23, 24).lit());
            assertFalse(DeployWarnings.undermanned(24, 24).lit());
        }
    }

    @Test void aShuttleUnderRepairLightsTheLiftLampWithItsHours() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var deck = new BoatDeck();
            deck.reconcile("ship", ShuttleType.HERMES, 2);
            assertFalse(DeployWarnings.lift(null, deck.boats(), 0f, null).lit());
            deck.settleRecovery("battle", Map.of(deck.boats().get(0).id(), 0.25f), List.of(), 0f);
            var lamp = DeployWarnings.lift(null, deck.boats(), 0f, null);
            assertTrue(lamp.lit());
            assertFalse(lamp.refused(), "A second boat still flies");
            assertTrue(lamp.hint().notes().stream().anyMatch(n -> n.contains("12 h")), lamp.hint().notes().toString());
            assertTrue(lamp.hint().notes().stream().anyMatch(n -> n.contains("1 of 2")), lamp.hint().notes().toString());
        }
    }

    @Test void aShuttleMissingAPartSaysWhichBoat() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var deck = new BoatDeck();
            deck.reconcile("ship", ShuttleType.HERMES, 1);
            var stripped = deck.boats().get(0);
            new BoatWorkshop(deck, TestPurse.rich()).strip(stripped.id(), BoatFittingSlot.DRIVE);
            assertFalse(stripped.missingParts().isEmpty());
            var lamp = DeployWarnings.lift(null, List.of(stripped), 0f, null);
            assertTrue(lamp.lit());
            assertTrue(lamp.hint().notes().get(0).startsWith(stripped.displayName()), lamp.hint().notes().toString());
        }
    }

    @Test void aRefusedLaunchLeadsWithItsReasonEvenWithNoBoatToName() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var lamp = DeployWarnings.lift(null, List.of(), 0f, "the reason");
            assertTrue(lamp.lit());
            assertTrue(lamp.refused());
            assertEquals("the reason", lamp.hint().notes().get(0));
            assertTrue(String.valueOf(lamp.markupProperty("classes")).contains("deploy-lamp-refused"));
        }
    }
}
