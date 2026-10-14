package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.decision.goap.action.FightingPositionClaims;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * The fighting-position layer of a review frame: which cells are prepared ground, which of those a marine
 * holds a claim on, and which marines are dug in.
 *
 * <p>Observation only. Everything here is read off the simulation's public surface after the tick — the
 * position index, the claim registry and the per-marine dig-in clock — and nothing is written back, so a
 * frame with this layer and a frame without it are the same battle. Positions are washed in green, claimed
 * cells outlined in white, and the cell under each dug-in marine in magenta.
 */
public final class FightingPositionReviewAnnotations {

    private FightingPositionReviewAnnotations() { }

    /** Nothing at all for a null simulation or a map with no positions. */
    public static ReviewAnnotations forBattle(BattleSimulation simulation) {
        if (simulation == null) return ReviewAnnotations.NONE;
        FightingPositionIndex index = simulation.fightingPositions();
        if (index.runCount() == 0) return ReviewAnnotations.NONE;
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (int run = 0; run < index.runCount(); run++) {
            List<ReviewAnnotation.Cell> cells = new ArrayList<>(index.runSize(run));
            for (int i = 0; i < index.runSize(run); i++) {
                cells.add(new ReviewAnnotation.Cell(index.runCellX(run, i), index.runCellY(run, i)));
            }
            marks.cells(cells, "POSITION " + index.runSize(run), ReviewStyle.POSITION);
        }

        FightingPositionClaims claims = simulation.fightingPositionClaims();
        List<ReviewAnnotation.Cell> claimed = new ArrayList<>();
        List<ReviewAnnotation.Cell> dugIn = new ArrayList<>();
        for (Squad squad : simulation.getSquads()) {
            for (int i = 0, count = simulation.squadMemberCount(squad.id); i < count; i++) {
                long member = simulation.squadMemberAt(squad.id, i);
                if (simulation.resolveUnit(member) == 0L) continue;
                int[] held = claims.cellOf(member);
                if (held != null) claimed.add(new ReviewAnnotation.Cell(held[0], held[1]));
                if (simulation.isDugIn(member)) {
                    dugIn.add(new ReviewAnnotation.Cell(simulation.world().cellX(member),
                            simulation.world().cellY(member)));
                }
            }
        }
        marks.cells(claimed, "CLAIMED " + claimed.size(), ReviewStyle.CLAIMED);
        marks.cells(dugIn, "DUG IN " + dugIn.size(), ReviewStyle.DUG_IN);
        return marks.build();
    }
}
