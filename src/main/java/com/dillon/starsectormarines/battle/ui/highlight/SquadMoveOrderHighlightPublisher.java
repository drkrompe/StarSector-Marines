package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.ui.picking.Selection;

import java.util.ArrayList;
import java.util.List;

/** Publishes the selected infantry squad or Mech lance's accepted tactical area/cell. */
public final class SquadMoveOrderHighlightPublisher {

    private SquadMoveOrderHighlightPublisher() { }

    public static void publish(Selection selection, BattleSimulation sim,
                               HighlightOverlay overlay) {
        if (selection == null || sim == null || overlay == null) return;
        List<CellHighlight> areas = new ArrayList<>();
        for (int selected : selection.getSelectedSquadIds()) {
            ActiveOrder order = sim.getSquadMoveOrderService().activeOrder(selected);
            // A line order is drawn only while it is being dragged: a standing stroke across the
            // map for every selected line is noise, and the men on their posts already show it.
            if (order instanceof ActiveDefendAreaOrder defend && defend.line() == null) {
                areas.addAll(defendAreaHighlights(defend));
            }
        }
        overlay.put(HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION, areas);
    }

    /**
     * The lines a right drag in progress would give, each with where its squad's marines will
     * stand, or nothing when no drag is under way.
     */
    public static void publishLinePreview(List<HighlightOverlay.LineMark> lines, HighlightOverlay overlay) {
        if (overlay == null) return;
        overlay.putLines(HighlightOverlay.SRC_SQUAD_LINE_PREVIEW, lines);
    }

    private static List<CellHighlight> defendAreaHighlights(
            ActiveDefendAreaOrder order) {
        List<CellHighlight> cells = new ArrayList<>();
        cells.add(new CellHighlight(order.destinationX(), order.destinationY(),
                HighlightOverlay.COLOR_SQUAD_MOVE_DESTINATION));
        int radius = order.radiusCells();
        int inner = Math.max(0, radius - 1);
        int outerSquared = radius * radius;
        int innerSquared = inner * inner;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distanceSquared = dx * dx + dy * dy;
                if (distanceSquared > outerSquared
                        || distanceSquared < innerSquared) continue;
                cells.add(new CellHighlight(order.destinationX() + dx,
                        order.destinationY() + dy,
                        HighlightOverlay.COLOR_SQUAD_MOVE_DESTINATION));
            }
        }
        return List.copyOf(cells);
    }
}
