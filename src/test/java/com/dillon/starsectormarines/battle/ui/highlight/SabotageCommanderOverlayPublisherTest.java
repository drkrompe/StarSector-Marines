package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SabotageCommanderOverlayPublisherTest {

    @Test
    void publishedSitesAndSelectedGroupBecomeMapMarks() {
        SabotageSiteSnapshot.SiteState site =
                new SabotageSiteSnapshot.SiteState(0, "SAB-01", "reactor",
                        12, 7, 3, 2f, 8f, true, false,
                        1, 0, 2, 12, 4f, 3f);
        SabotageSiteSnapshot.SquadDirective directive =
                new SabotageSiteSnapshot.SquadDirective(9, 0,
                        SabotageSiteSnapshot.GroupRole.SECURITY,
                        SabotageSiteSnapshot.AssignmentReason.SITE_SECURITY_PRESERVED,
                        AssignmentKind.CLEAR_ZONE, 3, 12, 7);
        SabotageSiteSnapshot detail = new SabotageSiteSnapshot(75, 60,
                Faction.MARINE, SabotageSiteSnapshot.Phase.PLANTING,
                List.of(site), List.of(), List.of(directive));
        CommanderSnapshot<SabotageSiteSnapshot> commander =
                new CommanderSnapshot<>(Faction.MARINE, "sabotage-attacker",
                        "PLANTING", 75, 60, 1, 0,
                        List.of(), List.of(), detail);
        HighlightOverlay overlay = new HighlightOverlay();

        SabotageCommanderOverlayPublisher.publish(overlay, commander, 9);

        CellHighlight marker = overlay.source(
                HighlightOverlay.SRC_SABOTAGE_SITES).get(0);
        assertEquals(12, marker.cellX);
        assertEquals(7, marker.cellY);
        CellHighlight selected = overlay.source(
                HighlightOverlay.SRC_SABOTAGE_SELECTED_SITE).get(0);
        assertEquals(11, selected.cellX);
        assertEquals(6, selected.cellY);
        assertEquals(3, selected.width);
        assertEquals(3, selected.height);

        SabotageCommanderOverlayPublisher.clear(overlay);
        assertTrue(overlay.source(HighlightOverlay.SRC_SABOTAGE_SITES).isEmpty());
    }
}
