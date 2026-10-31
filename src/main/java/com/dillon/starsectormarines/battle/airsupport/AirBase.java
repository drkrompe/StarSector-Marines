package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.List;

/**
 * A field on the battle map a side's air pool flies from ({@link AirPoolService#basedAt}), in place of the map edge. A
 * side may have several; a call goes off the one with a ready airframe of its kind nearest the point.
 *
 * <p>A based side's stock is what stands ready on its fields, not a ledger count: an airframe is ready once it is on its
 * stand and turned round, it is out while it flies, it comes home to the same stand to be rearmed and refuelled, and one
 * lost is replaced on the field, by the field's own law. The pool keeps doing everything else (the calls, the sorties'
 * bookkeeping, the side's anti-air memory), so a based sortie is the same sortie an edge-flown one is, only taking off
 * and landing where the side can see it. {@link WingAirbase} is the airbase a mission lays (bays rebuilt by its crew);
 * {@link FieldAirBase} is a pool's stands on a field of its own (losses replaced on a timer).
 */
public interface AirBase {

    /** Airframes of {@code kind} standing ready to fly now. */
    int ready(AirPoolKind kind);

    /**
     * Squared cells from {@code (x, y)} to the nearest airframe of {@code kind} that could go now (one of
     * {@code wanted} preferred where the field tells them apart), or {@link Float#MAX_VALUE} when none could.
     */
    float reach(BattleSimulation sim, AirPoolKind kind, FighterProfile wanted, float x, float y);

    /** The airframe a call for {@code wanted} of {@code kind} at {@code (x, y)} would fly off this field. */
    default FighterProfile airframeFor(AirPoolKind kind, FighterProfile wanted, float x, float y) {
        return wanted;
    }

    /**
     * Puts one ready airframe of {@code kind} into the air as {@code airframe}, bound for {@code (x, y)} and home again
     * to the stand it left; {@code 0L} when none stands ready. The craft is the field's to spawn and position; the pool
     * then loads it and tracks the sortie.
     */
    long launch(BattleSimulation sim, AirPoolKind kind, FighterProfile airframe, float x, float y);

    /**
     * Sim seconds until the field next replaces a lost airframe of {@code kind}, or a negative number when none of that
     * kind is being replaced on a clock.
     */
    float nextReplacementSeconds(AirPoolKind kind);

    /** The kinds of the aircraft burned where they stood since the last call, one entry each, for the pool's losses. */
    default List<AirPoolKind> newlyBurned() {
        return List.of();
    }

    /** The field's bounds, {x0, y0, x1, y1} inclusive, or {@code null} where it keeps none. */
    default int[] bounds() {
        return null;
    }
}
