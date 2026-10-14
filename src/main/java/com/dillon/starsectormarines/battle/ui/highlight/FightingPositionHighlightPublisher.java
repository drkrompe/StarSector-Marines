package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.battle.decision.goap.action.FightingPositionClaims;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.render2d.VisibleCellRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the player where fighting positions are while they are choosing where to send infantry.
 *
 * <p>While a friendly infantry squad is selected (which includes aiming a defend-area order,
 * since aiming needs a selection) two sources are published: every position cell as a faint
 * ground wash, and the cells the selected squads' marines currently hold claims on. Dug-in marines
 * are marked on the marine itself by {@code DugInMarkerRenderSystem}, not by a cell outline here.
 * With nothing selected, no infantry squad selected, or {@link DigInSystem#isEnabled()} off, the
 * sources are cleared.
 *
 * <p>Observation only: everything is read from the position index, the claim registry and the
 * dig-in clock, and nothing is written back. Cells outside {@code visible} are not published,
 * so the cost follows what is on screen rather than the size of the map's earthworks.
 */
public final class FightingPositionHighlightPublisher {

    private FightingPositionHighlightPublisher() { }

    /**
     * @param visible the cells on screen, or {@code null} to publish every cell
     */
    public static void publish(Selection selection, BattleSimulation sim,
                               HighlightOverlay overlay, VisibleCellRect visible) {
        if (overlay == null) return;
        if (selection == null || sim == null || !DigInSystem.isEnabled()
                || !hasInfantrySelected(selection, sim)) {
            clear(overlay);
            return;
        }
        FightingPositionIndex index = sim.fightingPositions();
        if (index == null || index.runCount() == 0) {
            clear(overlay);
            return;
        }

        List<CellHighlight> positions = new ArrayList<>();
        for (int run = 0; run < index.runCount(); run++) {
            for (int i = 0, n = index.runSize(run); i < n; i++) {
                int x = index.runCellX(run, i);
                int y = index.runCellY(run, i);
                if (visible == null || visible.contains(x, y)) {
                    positions.add(new CellHighlight(x, y, HighlightOverlay.COLOR_FIGHTING_POSITION));
                }
            }
        }
        overlay.put(HighlightOverlay.SRC_FIGHTING_POSITIONS, positions);

        FightingPositionClaims claims = sim.fightingPositionClaims();
        List<CellHighlight> claimed = new ArrayList<>();
        for (int squadId : selection.getSelectedSquadIds()) {
            for (int i = 0, n = sim.squadMemberCount(squadId); i < n; i++) {
                int[] held = claims.cellOf(sim.squadMemberAt(squadId, i));
                if (held != null && (visible == null || visible.contains(held[0], held[1]))) {
                    claimed.add(new CellHighlight(held[0], held[1],
                            HighlightOverlay.COLOR_FIGHTING_POSITION_CLAIMED));
                }
            }
        }
        overlay.put(HighlightOverlay.SRC_FIGHTING_POSITION_CLAIMS, claimed);
    }

    private static void clear(HighlightOverlay overlay) {
        overlay.clear(HighlightOverlay.SRC_FIGHTING_POSITIONS);
        overlay.clear(HighlightOverlay.SRC_FIGHTING_POSITION_CLAIMS);
    }

    /** Whether any selected squad is a live friendly infantry squad. */
    private static boolean hasInfantrySelected(Selection selection, BattleSimulation sim) {
        for (int squadId : selection.getSelectedSquadIds()) {
            Squad squad = sim.getSquad(squadId);
            if (squad != null && squad.aliveMembers > 0 && squad.faction == Faction.MARINE
                    && !squad.isMechSquad() && !squad.isDroneSquad()) {
                return true;
            }
        }
        return false;
    }
}
