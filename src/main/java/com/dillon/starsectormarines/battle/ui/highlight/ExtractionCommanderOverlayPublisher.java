package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot;
import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Projects the generic Extraction payload corridor and squad duties. */
@DebugOnly
public final class ExtractionCommanderOverlayPublisher {
    private static final Color SOURCE = new Color(0xFF, 0xB0, 0x58, 0xB8);
    private static final Color PAYLOAD = new Color(0x68, 0xE0, 0x98, 0xD8);
    private static final Color GUIDE = new Color(0x70, 0xF0, 0xD0, 0xB8);
    private static final Color EGRESS = new Color(0x58, 0xB8, 0xFF, 0xB8);
    private static final Color ACTION = new Color(0xD8, 0xB0, 0xFF, 0xD8);
    private static final Color SELECTED = new Color(0xFF, 0xFF, 0xFF, 0xE8);

    private ExtractionCommanderOverlayPublisher() { }

    public static void publish(HighlightOverlay overlay,
                               CommanderSnapshot<?> commander,
                               int selectedSquadId) {
        ExtractionCommandSnapshot snapshot = commander != null
                && commander.detail() instanceof ExtractionCommandSnapshot extraction
                ? extraction : null;
        ExtractionDefenseSnapshot defense = commander != null
                && commander.detail() instanceof ExtractionDefenseSnapshot extraction
                ? extraction : null;
        if (snapshot == null && defense == null) {
            clear(overlay);
            return;
        }
        if (defense != null) {
            publishDefense(overlay, defense, selectedSquadId);
            return;
        }
        overlay.put(HighlightOverlay.SRC_EXTRACTION_SOURCE,
                List.of(new CellHighlight(snapshot.sourceCellX() - 2,
                        snapshot.sourceCellY() - 2, 5, 5, SOURCE)));
        overlay.put(HighlightOverlay.SRC_EXTRACTION_PAYLOAD,
                snapshot.payloadCellX() >= 0 ? List.of(new CellHighlight(
                        snapshot.payloadCellX() - 1,
                        snapshot.payloadCellY() - 1, 3, 3, PAYLOAD))
                        : List.of());
        overlay.put(HighlightOverlay.SRC_EXTRACTION_GUIDE,
                snapshot.corridorGuideCellX() >= 0 ? List.of(
                        new CellHighlight(snapshot.corridorGuideCellX(),
                                snapshot.corridorGuideCellY(), GUIDE))
                        : List.of());
        overlay.put(HighlightOverlay.SRC_EXTRACTION_EGRESS,
                snapshot.egressCellX() >= 0 ? List.of(new CellHighlight(
                        snapshot.egressCellX() - 3,
                        snapshot.egressCellY() - 3, 7, 7, EGRESS))
                        : List.of());
        List<CellHighlight> actions = new ArrayList<>();
        List<CellHighlight> selected = new ArrayList<>();
        for (ExtractionCommandSnapshot.SquadIntent intent
                : snapshot.squadIntents()) {
            if (intent.targetCellX() < 0 || intent.targetCellY() < 0) continue;
            actions.add(new CellHighlight(intent.targetCellX(),
                    intent.targetCellY(), ACTION));
            if (intent.squadId() == selectedSquadId) {
                selected.add(new CellHighlight(intent.targetCellX() - 1,
                        intent.targetCellY() - 1, 3, 3, SELECTED));
            }
        }
        overlay.put(HighlightOverlay.SRC_EXTRACTION_ACTIONS, actions);
        overlay.put(HighlightOverlay.SRC_EXTRACTION_SELECTED_ACTION, selected);
    }

    private static void publishDefense(HighlightOverlay overlay,
                                       ExtractionDefenseSnapshot snapshot,
                                       int selectedSquadId) {
        overlay.put(HighlightOverlay.SRC_EXTRACTION_SOURCE,
                List.of(new CellHighlight(snapshot.sourceCellX() - 2,
                        snapshot.sourceCellY() - 2, 5, 5, SOURCE)));
        overlay.clear(HighlightOverlay.SRC_EXTRACTION_PAYLOAD);
        overlay.clear(HighlightOverlay.SRC_EXTRACTION_GUIDE);
        overlay.clear(HighlightOverlay.SRC_EXTRACTION_EGRESS);
        List<CellHighlight> actions = new ArrayList<>();
        List<CellHighlight> selected = new ArrayList<>();
        for (ExtractionDefenseSnapshot.SquadIntent intent
                : snapshot.squadIntents()) {
            if (intent.targetCellX() < 0 || intent.targetCellY() < 0) continue;
            actions.add(new CellHighlight(intent.targetCellX(),
                    intent.targetCellY(), ACTION));
            if (intent.squadId() == selectedSquadId) {
                selected.add(new CellHighlight(intent.targetCellX() - 1,
                        intent.targetCellY() - 1, 3, 3, SELECTED));
            }
        }
        overlay.put(HighlightOverlay.SRC_EXTRACTION_ACTIONS, actions);
        overlay.put(HighlightOverlay.SRC_EXTRACTION_SELECTED_ACTION, selected);
    }

    public static void clear(HighlightOverlay overlay) {
        overlay.clear(HighlightOverlay.SRC_EXTRACTION_SOURCE);
        overlay.clear(HighlightOverlay.SRC_EXTRACTION_PAYLOAD);
        overlay.clear(HighlightOverlay.SRC_EXTRACTION_GUIDE);
        overlay.clear(HighlightOverlay.SRC_EXTRACTION_EGRESS);
        overlay.clear(HighlightOverlay.SRC_EXTRACTION_ACTIONS);
        overlay.clear(HighlightOverlay.SRC_EXTRACTION_SELECTED_ACTION);
    }
}
