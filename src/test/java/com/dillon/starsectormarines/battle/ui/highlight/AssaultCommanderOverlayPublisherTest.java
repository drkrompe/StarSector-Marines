package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssaultCommanderOverlayPublisherTest {

    @Test
    void sectorsActionsAndSelectionComeFromPublishedPicture() {
        AssaultSearchSnapshot.SectorState sector =
                new AssaultSearchSnapshot.SectorState(2, 20, 10, 10, 8,
                        AssaultSearchSnapshot.SectorStatus.ACTIVE,
                        3, 9, 2, 70, 1, 24, 13);
        AssaultSearchSnapshot.SquadDirective directive =
                new AssaultSearchSnapshot.SquadDirective(7, 2,
                        AssaultSearchSnapshot.AssignmentReason.SECTOR_SEARCH_ASSIGNED,
                        AssignmentKind.SWEEP_SECTOR, 24, 13);
        AssaultSearchSnapshot detail = new AssaultSearchSnapshot(75, 70,
                Faction.MARINE, AssaultSearchSnapshot.Phase.CONVERGE, 1,
                List.of(sector), List.of(), List.of(directive));
        CommanderSnapshot<AssaultSearchSnapshot> commander =
                new CommanderSnapshot<>(Faction.MARINE, "assault-attacker",
                        "CONVERGE", 75, 70, 1, 0, List.of(), List.of(), detail);
        HighlightOverlay overlay = new HighlightOverlay();

        AssaultCommanderOverlayPublisher.publish(overlay, commander, 7);

        CellHighlight area = overlay.source(
                HighlightOverlay.SRC_ASSAULT_SECTORS).get(0);
        assertEquals(20, area.cellX);
        assertEquals(10, area.cellY);
        assertEquals(10, area.width);
        assertEquals(8, area.height);
        assertEquals(24, overlay.source(
                HighlightOverlay.SRC_ASSAULT_ACTIONS).get(0).cellX);
        assertEquals(3, overlay.source(
                HighlightOverlay.SRC_ASSAULT_SELECTED_ACTION).get(0).width);

        AssaultCommanderOverlayPublisher.clear(overlay);
        assertTrue(overlay.source(HighlightOverlay.SRC_ASSAULT_SECTORS).isEmpty());
    }

    @Test
    void defenderAreasStrongpointsAndResponseComeFromPublishedPicture() {
        AssaultDefenseSnapshot.AreaState area =
                new AssaultDefenseSnapshot.AreaState(1, 15, 0, 15, 15,
                        80, 1, 1, 1, 1,
                        AssaultDefenseSnapshot.ReportState.ACTIVE,
                        2, 70, 490, 4f, 3f, 20, 7);
        AssaultDefenseSnapshot.StrongpointState point =
                new AssaultDefenseSnapshot.StrongpointState(0, "GATE", 1,
                        21, 7, 20, 7, 3, 80);
        AssaultDefenseSnapshot.SquadDirective directive =
                new AssaultDefenseSnapshot.SquadDirective(9, 1,
                        AssaultDefenseSnapshot.Role.RESPONDER,
                        AssaultDefenseSnapshot.Reason.ACTIVE_CONTACT_RESPONSE,
                        AssignmentKind.DEFEND_AREA, 20, 7);
        AssaultDefenseSnapshot detail = new AssaultDefenseSnapshot(75, 70,
                Faction.DEFENDER,
                AssaultDefenseSnapshot.Phase.REPORTED_CONTACT_RESPONSE,
                4, 1, List.of(area), List.of(point), List.of(),
                List.of(directive));
        CommanderSnapshot<AssaultDefenseSnapshot> commander =
                new CommanderSnapshot<>(Faction.DEFENDER, "assault-defender",
                        "REPORTED_CONTACT_RESPONSE", 75, 70, 4, 1,
                        List.of(), List.of(), detail);
        HighlightOverlay overlay = new HighlightOverlay();

        AssaultCommanderOverlayPublisher.publish(overlay, commander, 9);

        assertEquals(15, overlay.source(
                HighlightOverlay.SRC_ASSAULT_SECTORS).get(0).cellX);
        assertEquals(20, overlay.source(
                HighlightOverlay.SRC_ASSAULT_STRONGPOINTS).get(0).cellX);
        assertEquals(20, overlay.source(
                HighlightOverlay.SRC_ASSAULT_ACTIONS).get(0).cellX);
        assertNotEquals(overlay.source(HighlightOverlay.SRC_ASSAULT_STRONGPOINTS)
                        .get(0).color,
                overlay.source(HighlightOverlay.SRC_ASSAULT_ACTIONS)
                        .get(0).color);
        assertEquals(15, overlay.source(
                HighlightOverlay.SRC_ASSAULT_SELECTED_SECTOR).get(0).width);
        assertEquals(3, overlay.source(
                HighlightOverlay.SRC_ASSAULT_SELECTED_ACTION).get(0).width);
        AssaultCommanderOverlayPublisher.clear(overlay);
        assertTrue(overlay.source(
                HighlightOverlay.SRC_ASSAULT_STRONGPOINTS).isEmpty());
    }
}
