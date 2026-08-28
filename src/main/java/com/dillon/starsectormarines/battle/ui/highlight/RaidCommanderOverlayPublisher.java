package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.RaidCommandSnapshot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Projects the published Raid target, egress, and squad actions onto the map. */
@DebugOnly
public final class RaidCommanderOverlayPublisher {
    private static final Color TARGET = new Color(0xFF, 0x68, 0x58, 0xB8);
    private static final Color SECURED = new Color(0x68, 0xE0, 0x98, 0xB8);
    private static final Color EGRESS = new Color(0x58, 0xB8, 0xFF, 0xB8);
    private static final Color ACTION = new Color(0xFF, 0xD8, 0x58, 0xD8);
    private static final Color SELECTED = new Color(0xFF, 0xFF, 0xFF, 0xE8);

    private RaidCommanderOverlayPublisher() { }

    public static void publish(HighlightOverlay overlay,
                               CommanderSnapshot<?> commander,
                               int selectedSquadId) {
        RaidCommandSnapshot snapshot = commander != null
                && commander.detail() instanceof RaidCommandSnapshot raid
                ? raid : null;
        if (snapshot == null) {
            clear(overlay);
            return;
        }
        overlay.put(HighlightOverlay.SRC_RAID_TARGET, List.of(new CellHighlight(
                snapshot.targetCellX() - 2, snapshot.targetCellY() - 2,
                5, 5, snapshot.targetSecured() ? SECURED : TARGET)));
        overlay.put(HighlightOverlay.SRC_RAID_EGRESS,
                snapshot.egressCellX() >= 0 ? List.of(new CellHighlight(
                        snapshot.egressCellX() - 3,
                        snapshot.egressCellY() - 3, 7, 7, EGRESS)) : List.of());
        List<CellHighlight> actions = new ArrayList<>();
        List<CellHighlight> selected = new ArrayList<>();
        for (RaidCommandSnapshot.SquadIntent intent : snapshot.squadIntents()) {
            if (intent.targetCellX() < 0 || intent.targetCellY() < 0) continue;
            actions.add(new CellHighlight(intent.targetCellX(),
                    intent.targetCellY(), ACTION));
            if (intent.squadId() == selectedSquadId) {
                selected.add(new CellHighlight(intent.targetCellX() - 1,
                        intent.targetCellY() - 1, 3, 3, SELECTED));
            }
        }
        overlay.put(HighlightOverlay.SRC_RAID_ACTIONS, actions);
        overlay.put(HighlightOverlay.SRC_RAID_SELECTED_ACTION, selected);
    }

    public static void clear(HighlightOverlay overlay) {
        overlay.clear(HighlightOverlay.SRC_RAID_TARGET);
        overlay.clear(HighlightOverlay.SRC_RAID_EGRESS);
        overlay.clear(HighlightOverlay.SRC_RAID_ACTIONS);
        overlay.clear(HighlightOverlay.SRC_RAID_SELECTED_ACTION);
    }
}
