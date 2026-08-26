package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Projects one published Assault search picture into debug map marks. */
@DebugOnly
public final class AssaultCommanderOverlayPublisher {

    private static final Color SEARCHING = new Color(0x40, 0xA8, 0xFF, 0x28);
    private static final Color SUSPECTED = new Color(0xFF, 0xC8, 0x40, 0x38);
    private static final Color ACTIVE = new Color(0xFF, 0x58, 0x58, 0x48);
    private static final Color SEARCHED = new Color(0x68, 0xD8, 0x88, 0x20);
    private static final Color TARGET = new Color(0xFF, 0xE8, 0x70, 0xD8);
    private static final Color SELECTED = new Color(0xFF, 0xFF, 0xFF, 0xE8);

    private AssaultCommanderOverlayPublisher() { }

    public static void publish(HighlightOverlay overlay,
                               CommanderSnapshot<?> commander,
                               int selectedSquadId) {
        AssaultSearchSnapshot snapshot = commander != null
                && commander.detail() instanceof AssaultSearchSnapshot assault
                ? assault : null;
        if (snapshot == null) {
            clear(overlay);
            return;
        }
        List<CellHighlight> sectors = new ArrayList<>();
        for (AssaultSearchSnapshot.SectorState sector : snapshot.sectors()) {
            Color color = switch (sector.status()) {
                case SEARCHING -> SEARCHING;
                case SUSPECTED -> SUSPECTED;
                case ACTIVE -> ACTIVE;
                case SEARCHED -> SEARCHED;
            };
            sectors.add(new CellHighlight(sector.minCellX(), sector.minCellY(),
                    sector.width(), sector.height(), color));
        }
        List<CellHighlight> actions = new ArrayList<>();
        for (AssaultSearchSnapshot.SquadDirective directive : snapshot.directives()) {
            if (directive.targetCellX() >= 0 && directive.targetCellY() >= 0) {
                actions.add(new CellHighlight(directive.targetCellX(),
                        directive.targetCellY(), TARGET));
            }
        }
        List<CellHighlight> selectedSector = new ArrayList<>();
        List<CellHighlight> selectedAction = new ArrayList<>();
        AssaultSearchSnapshot.SquadDirective selected =
                snapshot.directiveFor(selectedSquadId);
        if (selected != null) {
            AssaultSearchSnapshot.SectorState sector = snapshot.sector(
                    selected.sectorIndex());
            if (sector != null) {
                selectedSector.add(new CellHighlight(sector.minCellX(),
                        sector.minCellY(), sector.width(), sector.height(), SELECTED));
            }
            if (selected.targetCellX() >= 0 && selected.targetCellY() >= 0) {
                selectedAction.add(new CellHighlight(selected.targetCellX() - 1,
                        selected.targetCellY() - 1, 3, 3, SELECTED));
            }
        }
        overlay.put(HighlightOverlay.SRC_ASSAULT_SECTORS, sectors);
        overlay.put(HighlightOverlay.SRC_ASSAULT_ACTIONS, actions);
        overlay.put(HighlightOverlay.SRC_ASSAULT_SELECTED_SECTOR, selectedSector);
        overlay.put(HighlightOverlay.SRC_ASSAULT_SELECTED_ACTION, selectedAction);
    }

    public static void clear(HighlightOverlay overlay) {
        overlay.clear(HighlightOverlay.SRC_ASSAULT_SECTORS);
        overlay.clear(HighlightOverlay.SRC_ASSAULT_ACTIONS);
        overlay.clear(HighlightOverlay.SRC_ASSAULT_SELECTED_SECTOR);
        overlay.clear(HighlightOverlay.SRC_ASSAULT_SELECTED_ACTION);
    }
}
