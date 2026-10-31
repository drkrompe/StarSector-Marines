package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * A field on the battle map a side's air pool flies from ({@link AirPoolService#basedAt}), in place of the map edge.
 *
 * <p>A based side's stock is what stands ready on the field, not a ledger count: an airframe is ready once it is on its
 * stand and turned round, it is out while it flies, it comes home to the same stand to be rearmed and refuelled, and one
 * lost is replaced on the field on the field's own timer. The pool keeps doing everything else (the calls, the
 * sorties' bookkeeping, the side's anti-air memory), so a based sortie is the same sortie an edge-flown one is, only
 * taking off and landing where the side can see it.
 */
public interface AirBase {

    /** Airframes of {@code kind} standing ready to fly now. */
    int ready(AirPoolKind kind);

    /**
     * Puts one ready airframe of {@code kind} into the air as {@code airframe}, bound for {@code (x, y)} and home again
     * to the stand it left; {@code 0L} when none stands ready. The craft is the field's to spawn and position; the pool
     * then loads it and tracks the sortie.
     */
    long launch(BattleSimulation sim, AirPoolKind kind, FighterProfile airframe, float x, float y);

    /**
     * Sim seconds until the field next replaces a lost airframe of {@code kind}, or a negative number when none of that
     * kind is being replaced.
     */
    float nextReplacementSeconds(AirPoolKind kind);
}
