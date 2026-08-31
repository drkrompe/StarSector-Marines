package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.picking.Selection;

import java.util.List;

/** Publishes the selected mech's accepted destination as a world-cell cue. */
public final class MechMoveOrderHighlightPublisher {

    private MechMoveOrderHighlightPublisher() { }

    public static void publish(Selection selection, BattleSimulation sim,
                               HighlightOverlay overlay) {
        if (selection == null || sim == null || overlay == null) return;
        long selected = selection.getSelectedUnitEntityId();
        ActiveOrder order = selected != 0L
                ? sim.getMechMoveOrderService().activeOrder(selected) : null;
        if (order == null) {
            overlay.clear(HighlightOverlay.SRC_MECH_MOVE_DESTINATION);
            return;
        }
        overlay.put(HighlightOverlay.SRC_MECH_MOVE_DESTINATION, List.of(
                new CellHighlight(order.destinationX(), order.destinationY(),
                        HighlightOverlay.COLOR_MECH_MOVE_DESTINATION)));
    }
}
