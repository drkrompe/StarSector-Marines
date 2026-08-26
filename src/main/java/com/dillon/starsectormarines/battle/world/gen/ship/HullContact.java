package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * Where a room has to meet the outside of the ship, if it has to at all.
 *
 * <p>Most rooms only need to fit. A few are defined by what they open onto, and
 * for those a placement that fits is still wrong: a boat bay buried amidships
 * opens onto the compartment next door, and an engine room a third of the way up
 * the hull is not driving anything. This is the axis on which those rooms are
 * placed, as opposed to {@link DeckZone}, which is the stretch of hull a room
 * merely belongs in.
 */
public enum HullContact {

    /** Anywhere the packing allows. Almost every room. */
    NONE,
    /** Must reach the side of the ship — a bay that launches something. */
    FLANK,
    /** Must sit against the transom, at the after end of the hull. */
    STERN
}
