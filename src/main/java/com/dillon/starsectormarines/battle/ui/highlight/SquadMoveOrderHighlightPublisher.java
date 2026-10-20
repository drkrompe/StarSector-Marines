package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.command.ReactiveWatch;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.ReactiveManningService.Status;
import com.dillon.starsectormarines.battle.squad.ReactiveManningService.View;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.ui.picking.Selection;

import java.awt.Color;
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
        overlay.put(HighlightOverlay.SRC_GARRISON_WATCH, garrisonWatch(selection, sim));
    }

    /**
     * The GARRISON stance of each selected squad standing in it: its watch ring round the anchor, and a pip on the
     * place it is manning. A squad under the player's orders shows no ring, since the stance is standing aside.
     */
    static List<CellHighlight> garrisonWatch(Selection selection, BattleSimulation sim) {
        List<CellHighlight> cells = new ArrayList<>();
        for (int selected : selection.getSelectedSquadIds()) {
            View view = sim.getReactiveManning().view(selected);
            if (view.status() == Status.OFF || view.status() == Status.UNDER_ORDERS || view.anchorX() < 0) continue;
            ring(cells, view.anchorX(), view.anchorY(), ReactiveWatch.WATCH_RADIUS_CELLS,
                    HighlightOverlay.COLOR_GARRISON_WATCH);
            if (view.placeX() >= 0) {
                ring(cells, view.placeX(), view.placeY(), 2, HighlightOverlay.COLOR_GARRISON_PLACE);
                cells.add(new CellHighlight(view.placeX(), view.placeY(), HighlightOverlay.COLOR_GARRISON_PLACE));
            }
        }
        return List.copyOf(cells);
    }

    private static void ring(List<CellHighlight> cells, int cx, int cy, int radius, Color color) {
        int outer = radius * radius;
        int inner = Math.max(0, radius - 1) * Math.max(0, radius - 1);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int d = dx * dx + dy * dy;
                if (d <= outer && d >= inner) cells.add(new CellHighlight(cx + dx, cy + dy, color));
            }
        }
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
