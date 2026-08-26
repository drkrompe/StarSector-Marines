package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot;
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
    private static final Color DEFENSE_QUIET = new Color(0x58, 0xB8, 0x88, 0x24);
    private static final Color DEFENSE_SUSPECTED = new Color(0xE8, 0x98, 0x38, 0x38);
    private static final Color DEFENSE_ACTIVE = new Color(0xD8, 0x48, 0x98, 0x48);
    private static final Color STRONGPOINT = new Color(0x50, 0xD8, 0xE8, 0xD8);
    private static final Color DEFENSE_ACTION = new Color(0xF0, 0xD0, 0x58, 0xD8);
    private static final Color SELECTED = new Color(0xFF, 0xFF, 0xFF, 0xE8);

    private AssaultCommanderOverlayPublisher() { }

    public static void publish(HighlightOverlay overlay,
                               CommanderSnapshot<?> commander,
                               int selectedSquadId) {
        AssaultSearchSnapshot snapshot = commander != null
                && commander.detail() instanceof AssaultSearchSnapshot assault
                ? assault : null;
        AssaultDefenseSnapshot defense = commander != null
                && commander.detail() instanceof AssaultDefenseSnapshot detail
                ? detail : null;
        if (defense != null) {
            publishDefense(overlay, defense, selectedSquadId);
            return;
        }
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
        overlay.put(HighlightOverlay.SRC_ASSAULT_STRONGPOINTS, List.of());
        overlay.put(HighlightOverlay.SRC_ASSAULT_ACTIONS, actions);
        overlay.put(HighlightOverlay.SRC_ASSAULT_SELECTED_SECTOR, selectedSector);
        overlay.put(HighlightOverlay.SRC_ASSAULT_SELECTED_ACTION, selectedAction);
    }

    private static void publishDefense(HighlightOverlay overlay,
                                       AssaultDefenseSnapshot snapshot,
                                       int selectedSquadId) {
        List<CellHighlight> areas = new ArrayList<>();
        for (AssaultDefenseSnapshot.AreaState area : snapshot.areas()) {
            Color color = switch (area.reportState()) {
                case QUIET -> DEFENSE_QUIET;
                case SUSPECTED -> DEFENSE_SUSPECTED;
                case ACTIVE -> DEFENSE_ACTIVE;
            };
            areas.add(new CellHighlight(area.minCellX(), area.minCellY(),
                    area.width(), area.height(), color));
        }
        List<CellHighlight> strongpoints = new ArrayList<>();
        for (AssaultDefenseSnapshot.StrongpointState point
                : snapshot.strongpoints()) {
            strongpoints.add(new CellHighlight(point.rallyCellX(),
                    point.rallyCellY(), STRONGPOINT));
        }
        List<CellHighlight> actions = new ArrayList<>();
        for (AssaultDefenseSnapshot.SquadDirective directive
                : snapshot.directives()) {
            if (directive.markerCellX() >= 0
                    && directive.markerCellY() >= 0) {
                actions.add(new CellHighlight(directive.markerCellX(),
                        directive.markerCellY(), DEFENSE_ACTION));
            }
        }
        List<CellHighlight> selectedArea = new ArrayList<>();
        List<CellHighlight> selectedAction = new ArrayList<>();
        AssaultDefenseSnapshot.SquadDirective selected =
                snapshot.directiveFor(selectedSquadId);
        if (selected != null) {
            AssaultDefenseSnapshot.AreaState area = snapshot.area(
                    selected.areaIndex());
            if (area != null) {
                selectedArea.add(new CellHighlight(area.minCellX(),
                        area.minCellY(), area.width(), area.height(), SELECTED));
            }
            if (selected.markerCellX() >= 0 && selected.markerCellY() >= 0) {
                selectedAction.add(new CellHighlight(
                        selected.markerCellX() - 1,
                        selected.markerCellY() - 1, 3, 3, SELECTED));
            }
        }
        overlay.put(HighlightOverlay.SRC_ASSAULT_SECTORS, areas);
        overlay.put(HighlightOverlay.SRC_ASSAULT_STRONGPOINTS, strongpoints);
        overlay.put(HighlightOverlay.SRC_ASSAULT_ACTIONS, actions);
        overlay.put(HighlightOverlay.SRC_ASSAULT_SELECTED_SECTOR, selectedArea);
        overlay.put(HighlightOverlay.SRC_ASSAULT_SELECTED_ACTION, selectedAction);
    }

    public static void clear(HighlightOverlay overlay) {
        overlay.clear(HighlightOverlay.SRC_ASSAULT_SECTORS);
        overlay.clear(HighlightOverlay.SRC_ASSAULT_ACTIONS);
        overlay.clear(HighlightOverlay.SRC_ASSAULT_STRONGPOINTS);
        overlay.clear(HighlightOverlay.SRC_ASSAULT_SELECTED_SECTOR);
        overlay.clear(HighlightOverlay.SRC_ASSAULT_SELECTED_ACTION);
    }
}
