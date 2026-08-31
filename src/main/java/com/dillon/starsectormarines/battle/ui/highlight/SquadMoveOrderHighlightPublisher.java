package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.ui.picking.Selection;

import java.util.ArrayList;
import java.util.List;

/** Publishes the selected infantry squad's accepted move or objective cell. */
public final class SquadMoveOrderHighlightPublisher {

    private SquadMoveOrderHighlightPublisher() { }

    public static void publish(Selection selection, BattleSimulation sim,
                               HighlightOverlay overlay) {
        if (selection == null || sim == null || overlay == null) return;
        int selected = selection.getSelectedSquadId();
        ActiveOrder order = selected != Selection.NONE
                ? sim.getSquadMoveOrderService().activeOrder(selected) : null;
        if (order == null) {
            overlay.clear(HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION);
            return;
        }
        if (order instanceof ActiveDefendAreaOrder defend) {
            overlay.put(HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION,
                    defendAreaHighlights(defend));
        } else {
            overlay.put(HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION, List.of(
                    new CellHighlight(order.destinationX(), order.destinationY(),
                            HighlightOverlay.COLOR_SQUAD_MOVE_DESTINATION)));
        }
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
