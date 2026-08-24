package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * Doctrine slot for a mech chassis. Roles remain independent of the physical
 * hardpoints installed by {@link MechVariant}: the role changes <em>how</em>
 * the planner positions the mech and which installed weapons it is willing to
 * fire from a given posture.
 *
 * <p>Stage 1 shipped {@link #LR_SUPPORT} and {@link #ARMORED_SUPPORT}.
 * Stage 2 adds {@link #ASSAULT}; {@code RECON} remains parked (see
 * {@code ai-nouns.md}).
 *
 * <p>Assigned with the mech loadout at spawn or deployment by
 * {@link BattleSetup}. A mission commander may supply objective context
 * without rewriting this role; role-specific goals decide how to serve that
 * assignment.
 */
public enum MechRole {
    /**
     * Medium/long-range overwatch. Prefers a screened firing lane 24–36 cells
     * from the threat, uses its installed arms weapon at the inner edge and
     * LRMs farther out, and withholds SRMs. Another Sirocco does not count as
     * the friendly screen.
     */
    LR_SUPPORT,
    /**
     * Squad backstop. Paces a designated friendly infantry squad at a
     * follow distance large enough that chaingun fire outranges marine
     * rifles, fires whichever weapon has an in-band target with LoS, no
     * withholding. Movement anchors to the squad's centroid.
     */
    ARMORED_SUPPORT,
    /**
     * Point doctrine. Advances into an assigned objective zone or closes on
     * a live contact while firing on the move. Used by either faction; the
     * squad's faction determines friend and foe at runtime.
     */
    ASSAULT
}
