package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;

import java.util.ArrayList;
import java.util.List;

/**
 * How a Ruins battle ends for the company ({@code ruins-expedition.md}, Pressing on or pulling out): won when the haul
 * boards; <b>pulled out</b> when the company leaves before that, bringing its banked finds home and leaving the vault;
 * lost when the haul is lost or abandoned, or the company is wiped out. The family's Retreat is Pull out, and a
 * confirmed one always pulls out, in either phase and wherever the company stands.
 */
public final class RuinsPullOut {

    /** How an expedition ends. */
    public enum Resolution {
        /** The haul boarded: banked finds, the vault store, then vanilla's haul. */
        WON,
        /** The company left: the banked finds come home, the vault stays sealed. */
        PULLED_OUT,
        /** The haul lost or abandoned, or the company wiped out: nothing comes home. */
        LOST
    }

    private RuinsPullOut() {}

    /**
     * @param won       the company won the battle (the haul boarded)
     * @param retreated the company's retreat was confirmed
     */
    public static Resolution resolve(boolean won, boolean retreated) {
        if (won) return Resolution.WON;
        return retreated ? Resolution.PULLED_OUT : Resolution.LOST;
    }

    /** The battle's vault, or null when it is not a Ruins battle. */
    public static RuinsVaultObjective vault(BattleView sim) {
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective vault) return vault;
        }
        return null;
    }

    /** The Ruins resolution of an ended battle; null when it is not a Ruins battle. */
    public static Resolution of(BattleView sim, boolean won, boolean retreated) {
        return vault(sim) == null ? null : resolve(won, retreated);
    }

    /**
     * The circles a pulling-out company extracts unhurt from: where it landed, the pull-out point (the landing in
     * phase A, the entry in phase B), and every other held lift point.
     */
    public static List<LandingPad> extractionPads(List<LandingPad> landing, RuinsVaultObjective vault) {
        if (vault == null) return List.copyOf(landing);
        List<int[]> held = new ArrayList<>();
        for (int i = 0; i < vault.liftPoints().size(); i++) {
            if (vault.liftPointAvailable(i)) held.add(new int[]{vault.liftPointCellX(i), vault.liftPointCellY(i)});
        }
        return extractionPads(landing, vault.pullOutPointCellX(), vault.pullOutPointCellY(), held);
    }

    /**
     * The circles from their parts. Each point is a small pad, so it gets the same circle a landing pad does.
     *
     * @param heldLiftPoints each held lift point's cell as {x, y}
     */
    public static List<LandingPad> extractionPads(List<LandingPad> landing, int pullOutX, int pullOutY,
                                                  List<int[]> heldLiftPoints) {
        List<LandingPad> pads = new ArrayList<>(landing);
        pads.add(point(pullOutX, pullOutY));
        for (int[] cell : heldLiftPoints) pads.add(point(cell[0], cell[1]));
        return pads;
    }

    private static LandingPad point(int x, int y) {
        return new LandingPad(x, y, 1, 1, LandingPad.Approach.WEST);
    }
}
