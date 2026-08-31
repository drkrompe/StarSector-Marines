package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.ui.picking.Selection;

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
        overlay.put(HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION, List.of(
                new CellHighlight(order.destinationX(), order.destinationY(),
                        HighlightOverlay.COLOR_SQUAD_MOVE_DESTINATION)));
    }
}
