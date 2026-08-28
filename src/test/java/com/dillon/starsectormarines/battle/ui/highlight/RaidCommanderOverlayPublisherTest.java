package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.RaidCommandSnapshot;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidCommanderOverlayPublisherTest {

    @Test
    void publishesObjectiveEgressActionsAndSelectedIntent() {
        HighlightOverlay overlay = new HighlightOverlay();
        RaidCommandSnapshot detail = new RaidCommandSnapshot(100,
                Faction.MARINE, "EGRESS", "RAID-01", "depot",
                30, 20, 4, 5, 6, 6f, 6f,
                true, false, -1,
                List.of(new RaidCommandSnapshot.SquadIntent(7, "WITHDRAWAL",
                        "TARGET_SECURED_WITHDRAW", AssignmentKind.WITHDRAW,
                        5, 6)));
        CommanderSnapshot<RaidCommandSnapshot> commander =
                new CommanderSnapshot<>(Faction.MARINE, "raid-attacker",
                        "EGRESS", 100, 90, 1, 0, List.of(), List.of(), detail);

        RaidCommanderOverlayPublisher.publish(overlay, commander, 7);

        assertEquals(1, overlay.source(HighlightOverlay.SRC_RAID_TARGET).size());
        assertEquals(1, overlay.source(HighlightOverlay.SRC_RAID_EGRESS).size());
        assertEquals(1, overlay.source(HighlightOverlay.SRC_RAID_ACTIONS).size());
        assertEquals(1, overlay.source(
                HighlightOverlay.SRC_RAID_SELECTED_ACTION).size());
        RaidCommanderOverlayPublisher.clear(overlay);
        assertFalse(overlay.hasSource(HighlightOverlay.SRC_RAID_TARGET));
        assertTrue(overlay.source(HighlightOverlay.SRC_RAID_ACTIONS).isEmpty());
    }
}
