package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import org.junit.jupiter.api.Test;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MissionDialogueTest {
    @Test
    void authoredConversationSurvivesRenegotiationAndCannotBeMutatedThroughItsSource() {
        var turns = new ArrayList<>(List.of(MissionDialogue.say("Vale", "The shipment stays here."),
                MissionDialogue.say("Castle", "Then we need to talk about the ship.")));
        var mission = Mission.builder().id("conversation").dialogue(turns).build();
        turns.clear();
        var renegotiated = Mission.builder(mission).payout(18000).build();
        assertEquals(2, renegotiated.dialogue.size());
        assertEquals(mission.dialogue, renegotiated.dialogue);
        assertEquals("Vale: The shipment stays here.\n\nCastle: Then we need to talk about the ship.", mission.flavor);
        assertThrows(UnsupportedOperationException.class, () -> mission.dialogue.clear());
        var rewritten = Mission.builder(mission).flavor("A new report.").build();
        assertTrue(rewritten.dialogue.isEmpty());
        assertTrue(MissionDialogue.turns(rewritten).isEmpty(), "An explicit flavor rewrite cannot show a stale conversation");
    }

    @Test
    void legacyTextRecognizesOnlyKnownSpeakersAtLineStarts() {
        assertTrue(MissionDialogue.legacyTurns("Orders: Hold the workshop.\nReward: 18000.").isEmpty());
        assertTrue(MissionDialogue.legacyTurns("The log says Castle: we need a ship.").isEmpty());
        assertEquals(List.of(MissionDialogue.prose("Private channel established."),
                        MissionDialogue.say("Tamsin Vale", "The hold: leave it sealed.\nNo exceptions."),
                        MissionDialogue.say("Castle", "Understood.")),
                MissionDialogue.legacyTurns("Private channel established.\nTamsin Vale: The hold: leave it sealed.\nNo exceptions.\n\nCastle: Understood."));
        assertTrue(MissionDialogue.legacyTurns(null).isEmpty());
    }

    @Test
    void boardAndBriefingShareWordsAndIdentityButHaveIndependentElementIds() {
        var mission = ShepherdRecoveryCampaign.offer(new ShepherdRecoveryProgress(), "Asharu");
        var props = new HashMap<String, Object>();
        MissionDialogue.put(props, "detail", mission);
        MissionDialogue.put(props, "mission", mission);
        var board = rows(props.get("detailDialogue"));
        var briefing = rows(props.get("missionDialogue"));
        assertEquals(ShepherdRecoveryCampaign.BRIEF_TURNS.size(), board.size());
        for (int i = 0; i < board.size(); i++) {
            assertEquals(board.get(i).text(), briefing.get(i).text());
            assertEquals(board.get(i).speakerStyle(), briefing.get(i).speakerStyle());
            assertNotEquals(board.get(i).id(), briefing.get(i).id());
        }
        assertEquals(MissionDialogue.HIDDEN_STYLE, props.get("detailFlavorStyle"));
        assertNotEquals(board.get(0).speakerStyle(), board.get(1).speakerStyle());
        assertEquals(MissionDialogue.speakerStyle("Vale"), MissionDialogue.speakerStyle("TAMSIN VALE / BASIN RECLAMATION"));
        assertEquals(MissionDialogue.speakerStyle("Castle"), MissionDialogue.speakerStyle("CASTLE / COMPANY OPERATIONS"));
    }

    @Test
    void unvoicedNotesAndLegacyProseRemainAvailableWithoutInventedSpeakers() {
        var props = new HashMap<String, Object>();
        var mission = Mission.builder().dialogue(List.of(MissionDialogue.say("Vale", "A replacement party is ready."),
                MissionDialogue.prose("Previous inspection losses: 1."))).build();
        MissionDialogue.put(props, "mission", mission);
        var note = rows(props.get("missionDialogue")).get(1);
        assertEquals("", note.speaker());
        assertEquals(MissionDialogue.HIDDEN_STYLE, note.speakerVisibility());
        MissionDialogue.put(props, "mission", Mission.builder().flavor("No named speaker here.").build());
        assertTrue(rows(props.get("missionDialogue")).isEmpty());
        assertEquals("", props.get("missionFlavorStyle"));
        assertEquals(MissionDialogue.HIDDEN_STYLE, props.get("missionDialogueStyle"));
        MissionDialogue.put(props, "mission", null);
        assertTrue(rows(props.get("missionDialogue")).isEmpty());
    }

    @Test
    void authoredCatalogReferencesResolveAtProjectionTimeWithoutChangingSpeakerIdentity() throws Exception {
        var mission = ShepherdRecoveryCampaign.offer(new ShepherdRecoveryProgress(), "Asharu");
        assertTrue(mission.dialogue.get(0).text().startsWith("@"));
        assertTrue(mission.dialogue.get(0).speaker().startsWith("@"));
        assertEquals(ShepherdRecoveryCampaign.brief(), mission.flavor);
        assertFalse(mission.flavor.contains("@shepherd"), "Legacy plain text remains a readable derived transcript");
        var props = new HashMap<String, Object>();
        MissionDialogue.put(props, "mission", mission);
        var english = rows(props.get("missionDialogue"));
        assertFalse(english.get(0).text().startsWith("@"));
        try (var translated = new HeadlessStrings(Path.of("mod"), value -> "Translated " + value)) {
            MissionDialogue.put(props, "mission", mission);
            var localized = rows(props.get("missionDialogue"));
            assertEquals("Translated " + english.get(0).speaker(), localized.get(0).speaker());
            assertEquals("Translated " + english.get(0).text(), localized.get(0).text());
            assertEquals(english.get(0).speakerStyle(), localized.get(0).speakerStyle());
            assertEquals("Translated Castle", MissionDialogue.speakerName("CASTLE / COMPANY OPERATIONS"));
        }
        assertEquals("vale", mission.dialogue.get(0).speakerKey());
    }

    @Test
    void genericCatalogProseResolvesWithoutReplacingCallerProvidedFallback() throws Exception {
        String reference = "@missionSelectTheSelectedOperationSScaleFieldPresence";
        var mission = Mission.builder().flavor(reference).build();
        try (var translated = new HeadlessStrings(Path.of("mod"), value -> "Translated " + value)) {
            for (String prefix : List.of("detail", "mission")) {
                var props = new HashMap<String, Object>();
                props.put(prefix + "Flavor", mission.flavor);
                MissionDialogue.put(props, prefix, mission);
                assertTrue(rows(props.get(prefix + "Dialogue")).isEmpty());
                assertTrue(((String) props.get(prefix + "Flavor")).startsWith("Translated "));
                assertEquals("", props.get(prefix + "FlavorStyle"));

                props.put(prefix + "Flavor", "No contract selected.");
                MissionDialogue.put(props, prefix, null);
                assertEquals("No contract selected.", props.get(prefix + "Flavor"));

                props.put(prefix + "Flavor", reference);
                MissionDialogue.put(props, prefix, null);
                assertTrue(((String) props.get(prefix + "Flavor")).startsWith("Translated "));
            }
        }
    }
    @SuppressWarnings("unchecked")
    private static List<MissionDialogue.Row> rows(Object value) {
        return (List<MissionDialogue.Row>) value;
    }
}
