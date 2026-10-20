package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;

import java.util.List;

/**
 * Publishes the selected vehicle's accepted destination, and — separately —
 * the cell an order was refused at.
 *
 * <p>Two sources rather than one, because they say opposite things. A
 * destination is where the vehicle is going; a refusal is where it is not, and
 * a player who clicked somewhere a chassis cannot reach needs to see that the
 * click was received and rejected rather than silently ignored.
 */
public final class VehicleMoveOrderHighlightPublisher {

    /** Shaft length of the arrow on an accepted order's destination, in cells. */
    private static final float FACING_ARROW_CELLS = 2.5f;

    private VehicleMoveOrderHighlightPublisher() { }

    public static void publish(Selection selection, BattleSimulation sim,
                               HighlightOverlay overlay) {
        if (selection == null || sim == null || overlay == null) return;
        long selected = selection.getSelectedVehicleId();
        VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
        if (selected == 0L || orders == null) {
            overlay.clear(HighlightOverlay.SRC_VEHICLE_MOVE_DESTINATION);
            overlay.clear(HighlightOverlay.SRC_VEHICLE_MOVE_REFUSED);
            overlay.clear(HighlightOverlay.SRC_VEHICLE_MOVE_FACING);
            return;
        }

        VehicleMoveOrderService.ActiveOrder order = orders.activeOrder(selected);
        if (order != null) {
            overlay.put(HighlightOverlay.SRC_VEHICLE_MOVE_DESTINATION, List.of(
                    new CellHighlight((int) Math.floor(order.destinationX()),
                            (int) Math.floor(order.destinationY()),
                            HighlightOverlay.COLOR_VEHICLE_MOVE_DESTINATION)));
            overlay.putFacings(HighlightOverlay.SRC_VEHICLE_MOVE_FACING, order.hasFacing()
                    ? List.of(new HighlightOverlay.FacingMark(order.destinationX(), order.destinationY(),
                            order.facingDegrees(), FACING_ARROW_CELLS))
                    : null);
        } else {
            overlay.clear(HighlightOverlay.SRC_VEHICLE_MOVE_DESTINATION);
            overlay.clear(HighlightOverlay.SRC_VEHICLE_MOVE_FACING);
        }

        VehicleMoveOrderService.RefusedOrder refused = orders.refusal(selected);
        if (refused != null) {
            overlay.put(HighlightOverlay.SRC_VEHICLE_MOVE_REFUSED, List.of(
                    new CellHighlight(refused.requestedX(), refused.requestedY(),
                            HighlightOverlay.COLOR_VEHICLE_MOVE_REFUSED)));
        } else {
            overlay.clear(HighlightOverlay.SRC_VEHICLE_MOVE_REFUSED);
        }
    }
}
