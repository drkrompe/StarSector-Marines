package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot;
import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot;
import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.evacuation.SwarmPressureSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExtractionCommanderOverlayPublisherTest {

    @Test
    void defenderOverlayPublishesOnlySourceAndOwnInterdictionActions() {
        HighlightOverlay overlay = new HighlightOverlay();
        ExtractionDefenseSnapshot detail = new ExtractionDefenseSnapshot(
                120, Faction.DEFENDER,
                ExtractionDefenseSnapshot.Phase.ALARM_INTERDICTION,
                "EXTRACTION-01", "package", 30, 20,
                true, 100, false, false,
                ExtractionPayloadObjective.Failure.NONE,
                2, 118, 3, 0,
                List.of(new ExtractionDefenseSnapshot.SquadIntent(9,
                        ExtractionDefenseSnapshot.Role.INTERDICTION,
                        "BELIEVED_CONTACT_INTERDICTION",
                        AssignmentKind.DEFEND_SITE, 22, 14, false)));
        CommanderSnapshot<ExtractionDefenseSnapshot> commander =
                new CommanderSnapshot<>(Faction.DEFENDER,
                        "extraction-defender", "ALARM_INTERDICTION", 120, 118,
                        3, 0, List.of(), List.of(), detail);

        ExtractionCommanderOverlayPublisher.publish(overlay, commander, 9);

        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_EXTRACTION_SOURCE).size());
        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_EXTRACTION_ACTIONS).size());
        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_EXTRACTION_SELECTED_ACTION).size());
        assertFalse(overlay.hasSource(
                HighlightOverlay.SRC_EXTRACTION_PAYLOAD));
        assertFalse(overlay.hasSource(
                HighlightOverlay.SRC_EXTRACTION_GUIDE));
        assertFalse(overlay.hasSource(
                HighlightOverlay.SRC_EXTRACTION_EGRESS));
    }

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

    @Test
    void rescueOverlayPublishesCorridorAndOwnedSwarmApproaches() {
        HighlightOverlay overlay = new HighlightOverlay();
        RescueCommandSnapshot detail = new RescueCommandSnapshot(
                100, Faction.MARINE, "IN_TRANSIT", "CIVILIAN-COHORT",
                "civilian cohort", 30, 20, 18, 14, 16, 13, 5, 6,
                8, 6, 1, 1, 0.125f, true, 7, 2,
                false, false, ExtractionPayloadObjective.Failure.NONE,
                List.of(new RescueCommandSnapshot.SquadIntent(7,
                        RescueCommandSnapshot.Role.COHORT_ESCORT,
                        "COHORT_ESCORT", AssignmentKind.ESCORT,
                        18, 14, false, false)));
        CommanderSnapshot<RescueCommandSnapshot> commander =
                new CommanderSnapshot<>(Faction.MARINE,
                        "rescue-corridor", "IN_TRANSIT", 100, 90,
                        1, 0, List.of(), List.of(), detail);
        SwarmPressureSnapshot director = new SwarmPressureSnapshot(
                1, 100, SwarmPressureSnapshot.Phase.COHORT_RELEASED,
                "PRESSURE_COHORT_SCREEN", 20, 14, 18, 1,
                List.of(), List.of(), List.of(
                new SwarmPressureSnapshot.WaveIntent(99L,
                        SwarmPressureSnapshot.Approach.NORTH, 12, 0,
                        SwarmPressureSnapshot.TargetContext.MARINE_SCREEN,
                        "RESTORE_PRESSURE_FLOOR")));

        ExtractionCommanderOverlayPublisher.publish(
                overlay, commander, 7, director);

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
                HighlightOverlay.SRC_RESCUE_SWARM_APPROACHES).size());
    }
}
