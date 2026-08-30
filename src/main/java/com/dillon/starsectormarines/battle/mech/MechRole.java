package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.setup.BattleSetup;

/**
 * Doctrine slot for a mech chassis. Roles remain independent of the physical
 * hardpoints installed by {@link MechVariant}: the role changes <em>how</em>
 * the planner positions the mech and which installed weapons it is willing to
 * fire from a given posture.
 *
 * <p>The serialized enum identifiers are compatibility-sensitive. Battlefield
 * doctrine exposes player-facing names through {@link #displayName()} while
 * preserving the original LR, armored-support, and assault constants.
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
    LR_SUPPORT("Long Range Support"),
    /**
     * Frontline support. Paces eligible infantry or non-cyclic mech allies,
     * takes the threat-facing side of their formation, and fires every
     * installed in-band weapon. Without a legal anchor it holds and defends.
     */
    ARMORED_SUPPORT("Tank"),
    /**
     * Point doctrine. Advances into an assigned objective zone or closes on
     * a live contact while firing on the move. Used by either faction; the
     * squad's faction determines friend and foe at runtime.
     */
    ASSAULT("Brawler"),
    /**
     * Mission-first direct-fire generalist. Fights at medium range when it
     * has room, but stands and answers a close threat rather than requiring
     * special ally geometry.
     */
    BALANCED("Balanced");

    private final String displayName;

    MechRole(String displayName) {
        this.displayName = displayName;
    }

    /** Player-facing doctrine label; enum names remain stable save/fixture ids. */
    public String displayName() {
        return displayName;
    }
}
