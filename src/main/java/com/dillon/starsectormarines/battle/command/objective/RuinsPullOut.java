package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;

import java.util.ArrayList;
import java.util.List;

/**
 * How a Ruins battle ends for the company ({@code ruins-expedition.md}, Pressing on or pulling out): won when the haul
 * boards; <b>pulled out</b> when the company leaves while its pull-out point is held, bringing its banked finds home
 * and leaving the vault; lost otherwise. The family's Retreat is Pull out, and it is an ordinary withdrawal, a loss,
 * while the pull-out point is lost.
 */
public final class RuinsPullOut {

    /** How an expedition ends. */
    public enum Resolution {
        /** The haul boarded: banked finds, the vault store, then vanilla's haul. */
        WON,
        /** Left while the pull-out point was held: the banked finds come home, the vault stays sealed. */
        PULLED_OUT,
        /** Wiped out, the haul lost, or a retreat while the pull-out point was lost: nothing comes home. */
        LOST
    }

    private RuinsPullOut() {}

    /**
     * @param won             the company won the battle (the haul boarded)
     * @param retreated       the company's retreat was confirmed
     * @param pullOutPointHeld whether the pull-out point was held when the battle ended
     */
    public static Resolution resolve(boolean won, boolean retreated, boolean pullOutPointHeld) {
        if (won) return Resolution.WON;
        return retreated && pullOutPointHeld ? Resolution.PULLED_OUT : Resolution.LOST;
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
        RuinsVaultObjective vault = vault(sim);
        return vault == null ? null : resolve(won, retreated, vault.pullOutPointHeld());
    }

    /**
     * The circles a withdrawing company extracts unhurt from: where it landed, the pull-out point, and every other
     * held lift point. Each point is a small pad, so it gets the same circle a landing pad does.
     */
    public static List<LandingPad> extractionPads(List<LandingPad> landing, RuinsVaultObjective vault) {
        List<LandingPad> pads = new ArrayList<>(landing);
        if (vault == null) return pads;
        pads.add(point(vault.pullOutPointCellX(), vault.pullOutPointCellY()));
        for (int i = 0; i < vault.liftPoints().size(); i++) {
            if (vault.liftPointAvailable(i)) pads.add(point(vault.liftPointCellX(i), vault.liftPointCellY(i)));
        }
        return pads;
    }

    private static LandingPad point(int x, int y) {
        return new LandingPad(x, y, 1, 1, LandingPad.Approach.WEST);
    }
}
