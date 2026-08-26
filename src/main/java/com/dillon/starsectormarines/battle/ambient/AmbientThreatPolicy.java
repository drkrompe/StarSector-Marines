package com.dillon.starsectormarines.battle.ambient;

/** What kind of nearby combatant interrupts an ambient assignment. */
public enum AmbientThreatPolicy {
    /** Authored display scenes keep playing regardless of the roster around them. */
    NONE,
    /** Guards and workers yield only when an opposing combatant enters the radius. */
    HOSTILE_COMBATANT,
    /** Civilians yield to any nearby armed actor and return to their normal role. */
    ANY_COMBATANT
}
