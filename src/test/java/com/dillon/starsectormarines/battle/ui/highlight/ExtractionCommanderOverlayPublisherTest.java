package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot;
import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExtractionCommanderOverlayPublisherTest {

    @Test
    void publishesSourcePayloadEgressActionsAndSelectedIntent() {
        HighlightOverlay overlay = new HighlightOverlay();
        ExtractionCommandSnapshot detail = new ExtractionCommandSnapshot(
                100, Faction.MARINE, "IN_TRANSIT", "EXTRACTION-01",
                "package", 30, 20, 18, 14, 16, 13, 5, 6, 0.5f,
                true, 7, false, false,
                ExtractionPayloadObjective.Failure.NONE,
                List.of(new ExtractionCommandSnapshot.SquadIntent(7,
                        ExtractionCommandSnapshot.Role.PAYLOAD_ELEMENT,
                        "PACKAGE_ESCORT", AssignmentKind.ESCORT,
                        18, 14, false)));
        CommanderSnapshot<ExtractionCommandSnapshot> commander =
                new CommanderSnapshot<>(Faction.MARINE,
                        "extraction-attacker", "IN_TRANSIT", 100, 90,
                        1, 0, List.of(), List.of(), detail);

        ExtractionCommanderOverlayPublisher.publish(overlay, commander, 7);

        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_EXTRACTION_SOURCE).size());
        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_EXTRACTION_PAYLOAD).size());
        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_EXTRACTION_GUIDE).size());
        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_EXTRACTION_EGRESS).size());
        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_EXTRACTION_ACTIONS).size());
        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_EXTRACTION_SELECTED_ACTION).size());

        ExtractionCommanderOverlayPublisher.clear(overlay);
        assertFalse(overlay.hasSource(
                HighlightOverlay.SRC_EXTRACTION_SOURCE));
        assertFalse(overlay.hasSource(
                HighlightOverlay.SRC_EXTRACTION_ACTIONS));
    }
}
