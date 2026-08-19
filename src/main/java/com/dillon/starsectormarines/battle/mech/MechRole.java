package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * Doctrine slot for a mech chassis. Every mech carries the same all-arounder
 * weapon set ({@link MechWeapon#CHAINGUN} + {@link MechWeapon#SRM_POD} +
 * {@link MechWeapon#LRM_ARTILLERY}) — the role changes <em>how</em> the
 * planner positions the mech and which weapons it's willing to fire from a
 * given posture, not which weapons are mounted.
 *
 * <p>Stage 1 shipped {@link #LR_SUPPORT} and {@link #ARMORED_SUPPORT}.
 * Stage 2 adds {@link #ASSAULT}; {@code RECON} remains parked (see
 * {@code roadmap/ai/13-mech-goap.md}).
 *
 * <p>Assigned at spawn time by {@link BattleSetup}'s defender cluster mint.
 * The commander tier (parked, see {@code roadmap/ai/12-squad-of-squads.md})
 * will eventually overwrite the field via {@code ObjectiveAssignment} —
 * spawn assignment is the stub that lets the planner start expressing
 * doctrinal differentiation before the commander layer lands.
 */
public enum MechRole {
    /**
     * Long-range overwatch. Prefers engagement at LRM band (~28–40 cells),
     * actively withholds SRMs and chaingun even when in-band targets exist
     * so the mech doesn't reveal position by firing short-range weapons.
     * Movement anchors to a cover cell with LoS along the squad's threat
     * axis.
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
