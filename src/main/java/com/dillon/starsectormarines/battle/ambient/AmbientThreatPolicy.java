package com.dillon.starsectormarines.battle.ambient;

/** What kind of nearby combatant interrupts an ambient assignment. */
public enum AmbientThreatPolicy {
    /** Authored display scenes keep playing regardless of the roster around them. */
    NONE,
    /** Guards and workers yield only when an opposing combatant enters the radius. */
    HOSTILE_COMBATANT,
    /**
     * Workers who stay at it until somebody actually shoots them.
     *
     * <p>Proximity is the wrong question for a trade that is armed and has a
     * job. A technician who downed tools because an enemy came within fourteen
     * cells would spend a battle standing about in the one part of the map an
     * attack passes through, and the facility they were posted to would stop
     * working the moment anybody looked at it — which is a garrison's whole
     * rear going idle for the sight of a scout.
     *
     * <p>So the trigger is being hit rather than being approached. They keep
     * working with a firefight going on around them, break off when a round
     * finds them, and go back to it once nothing has for a few seconds — and in
     * between they are an ordinary armed unit and will shoot back.
     */
    UNDER_FIRE,
    /** Civilians yield to any nearby armed actor and return to their normal role. */
    ANY_COMBATANT
}
