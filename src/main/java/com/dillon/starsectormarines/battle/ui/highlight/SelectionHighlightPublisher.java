package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.picking.Selection;

import java.util.ArrayList;
import java.util.List;

/**
 * Production publisher of the selected-squad cell highlight. Run every frame
 * from the main loop (not the {@code @DebugOnly} squad-plan panel): reads the
 * shared {@link Selection} and, when a squad is picked, fills
 * {@link HighlightOverlay#SRC_SELECTED_SQUAD} with that squad's live members in
 * green. When the selection also pins one exact member, a separate gold source
 * paints that member last so mixed-role mech lances retain an unambiguous
 * player-facing primary selection. This is the real selection-feedback cue —
 * it works in a prod build where the debug panel is stripped.
 *
 * <p>Self-healing: an empty member list (squad wiped out, or a stale id after
 * the squad disappeared) drops the source via {@link HighlightOverlay#put}; no
 * selection at all clears it.
 *
 * <p>The captain (gold) and GOAP action-cell sources stay debug-fed; only the
 * squad-member cue is promoted to production here.
 */
public final class SelectionHighlightPublisher {

    private SelectionHighlightPublisher() {}

    public static void publish(Selection selection, BattleSimulation sim, HighlightOverlay overlay) {
        if (selection == null || sim == null || overlay == null) return;
        int squadId = selection.getSelectedSquadId();
        if (squadId == Selection.NONE) {
            overlay.clear(HighlightOverlay.SRC_SELECTED_SQUAD);
            overlay.clear(HighlightOverlay.SRC_SELECTED_UNIT);
            return;
        }
        List<CellHighlight> members = new ArrayList<>();
        List<CellHighlight> selected = new ArrayList<>(1);
        long selectedUnit = selection.getSelectedUnitEntityId();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (!sim.squad().hasSquad(u) || sim.squad().squadId(u) != squadId) continue;
            int cellX = sim.world().cellX(u);
            int cellY = sim.world().cellY(u);
            members.add(new CellHighlight(
                    cellX, cellY, HighlightOverlay.COLOR_SELECTED_UNIT));
            if (u == selectedUnit) {
                selected.add(new CellHighlight(
                        cellX, cellY, HighlightOverlay.COLOR_SELECTED_PRIMARY));
            }
        }
        overlay.put(HighlightOverlay.SRC_SELECTED_SQUAD, members);
        overlay.put(HighlightOverlay.SRC_SELECTED_UNIT, selected);
    }
}
