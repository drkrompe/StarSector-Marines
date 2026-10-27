package com.dillon.starsectormarines.battle.command;

/**
 * What a squad or lance can <em>do</em> to a defended position, as an assault plan reads it (`phased-assault.md`,
 * "Capabilities and roles"). Capabilities are derived from the equipment the unit actually carries, never from what the
 * unit is called ({@link CapabilityProfile}), so a new rocket, mount or tool slots in the moment its data says what it
 * does to a wall or a nest. A squad usually has several: a rocket section is at once a source of anti-structure fire and
 * a distance breacher, and it can be spent on only one of them at a time.
 */
public enum AssaultCapability {
    /** Volume of direct fire at range: keeps heads down and wears a position. Rounds per second. */
    SUPPRESSION,
    /** Direct fire that hurts armoured structures (nests, bunkers): shells and rockets, not rifle rounds. Damage per second. */
    DIRECT_STRUCTURE_FIRE,
    /** Indirect, arcing fire from well outside a position's reach (missile racks, mortars). Damage per second. */
    STANDOFF_STRUCTURE_FIRE,
    /** Opens a wall from outside the nests' reach, over a number of shells. Wall damage per second. */
    DISTANCE_BREACH,
    /** Opens a wall by standing at it: placed charges. Wall damage per second. */
    CLOSE_BREACH,
    /** Can enter a building and clear it: infantry. Able bodies. */
    ASSAULT,
    /** Can hold ground that has been taken. Able bodies. */
    HOLD,
    /**
     * Can direct an airframe from the side's pool onto ground: the squad carries a forward-observer radio. Operators, not damage:
     * what a strike does is the pool's and the aircraft's, and whether one is on offer is read from the side's air picture.
     */
    AIR_DIRECTION,
    /** Can stand a cloud of smoke on ground within a short throw: grenades, not damage. Throws available (the reserve), one per cloud. */
    SMOKE_SCREEN
}
