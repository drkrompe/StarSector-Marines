package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
