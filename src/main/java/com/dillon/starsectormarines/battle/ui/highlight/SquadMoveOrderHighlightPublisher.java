package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.squad.FrontageLine;
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
            if (order instanceof ActiveDefendAreaOrder defend) {
                areas.addAll(defend.line() != null ? lineHighlights(defend.line())
                        : defendAreaHighlights(defend));
            }
        }
        overlay.put(HighlightOverlay.SRC_SQUAD_MOVE_DESTINATION, areas);
    }

    /** The lines a right drag in progress would give, or nothing when no drag is under way. */
    public static void publishLinePreview(List<FrontageLine> lines, HighlightOverlay overlay) {
        if (overlay == null) return;
        if (lines == null || lines.isEmpty()) {
            overlay.clear(HighlightOverlay.SRC_SQUAD_LINE_PREVIEW);
            return;
        }
        List<CellHighlight> cells = new ArrayList<>();
        for (FrontageLine line : lines) cells.addAll(lineHighlights(line));
        overlay.put(HighlightOverlay.SRC_SQUAD_LINE_PREVIEW, cells);
    }

    /**
     * The drawn line, cell by cell, with a short tick toward the front at every few cells: the
     * line says where, the ticks say which way it faces.
     */
    static List<CellHighlight> lineHighlights(FrontageLine line) {
        List<CellHighlight> cells = new ArrayList<>();
        int steps = Math.max(1, (int) Math.ceil(line.length() * 2f));
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            float[] p = line.pointAt((float) i / steps, 0f);
            int x = Math.round(p[0]);
            int y = Math.round(p[1]);
            if (x == lastX && y == lastY) continue;
            lastX = x;
            lastY = y;
            cells.add(new CellHighlight(x, y, HighlightOverlay.COLOR_SQUAD_MOVE_DESTINATION));
        }
        int ticks = Math.max(1, Math.round(line.length() / FRONT_TICK_SPACING));
        for (int i = 0; i <= ticks; i++) {
            for (int ahead = 1; ahead <= FRONT_TICK_LENGTH; ahead++) {
                float[] p = line.pointAt((float) i / ticks, ahead);
                cells.add(new CellHighlight(Math.round(p[0]), Math.round(p[1]),
                        HighlightOverlay.COLOR_SQUAD_MOVE_DESTINATION));
            }
        }
        return List.copyOf(cells);
    }

    /** Cells between the facing ticks along a drawn line. */
    private static final float FRONT_TICK_SPACING = 3f;
    /** How far a facing tick reaches toward the front. */
    private static final int FRONT_TICK_LENGTH = 2;

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
