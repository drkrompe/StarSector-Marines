package com.dillon.starsectormarines.battle.ai.goap;

/**
 * Named facts the GOAP planner reasons over. Each predicate is boolean-valued;
 * numeric concepts (HP fractions, distance bands, ammo counts) get
 * <b>bucketed</b> into discrete named predicates ({@code SQUAD_HP_BELOW_HALF}
 * rather than {@code SQUAD_HP_FRAC}). Keeps the planner pure-boolean and
 * forces action effects to be honest about what they change.
 *
 * <p>Adding a predicate = adding an entry here + registering a
 * {@code PredicateEvaluator} so {@code WorldStateBuilder} can snapshot it.
 * Action {@code preconditions}/{@code effects} reference predicates by enum
 * identity, so renaming is safe.
 *
 * <p><b>Role-parameterized predicates.</b> Some predicates conceptually need
 * parameters (which portal, which zone, which target). Encoding parameters
 * into the bitmask isn't possible — predicates are fixed enum bits. The
 * convention is to scope them implicitly to the squad's currently-assigned
 * objective: {@link #ENEMY_IN_PORTAL_CELL} means "the portal this squad's
 * choke-point action is watching"; {@link #ZONE_CLEAR} means "the zone this
 * squad's clear-zone action targets." The evaluator pulls the relevant id
 * from squad fields the action set up on assignment.
 */
public enum Predicate {

    // --- Stage 1: parity set ---

    /** Squad has at least one alive enemy known to be on the map. */
    HAS_TARGET,
    /** The assigned member (or, at squad scope, at least one squadmate) has line of sight to the squad's primary target. */
    HAS_LOS_TO_TARGET,
    /** The assigned member is within their {@code attackRange} of the squad's primary target. */
    IN_RANGE_OF_TARGET,
    /** The assigned member is within {@link com.dillon.starsectormarines.battle.ai.InfantryCohesion#COHESION_RADIUS} cells of the squad centroid. */
    WITHIN_COHESION_RADIUS,
    /** Goal-side marker — set true by {@code EngagePosture.effects}. The {@code EliminateEnemies} goal's desired state. */
    ENEMY_DAMAGED,

    // --- Stage 2: pre-declared surface (Story-bank: see roadmap/ai/10-tactical-stories.md) ---
    //
    // Each predicate is reserved here so subagent-implemented stories don't
    // collide on the shared enum. Evaluators are stubbed to `false` in
    // WorldStateBuilder until the owning story lands.

    /**
     * Squad strength has dropped to ≤50% of {@link com.dillon.starsectormarines.battle.squad.Squad#originalSize}.
     * Original Story B trigger — superseded by {@link #MORALE_BROKEN}, which
     * recovers over time and lets a squad re-enter the fight after pulling
     * back. Kept here (stubbed false) to preserve the enum slot until any
     * remaining references are cleared.
     *
     * @deprecated Use {@link #MORALE_BROKEN} for the SurviveContact trigger.
     */
    @Deprecated
    SQUAD_BELOW_HALF_STRENGTH,
    /**
     * Squad's {@link com.dillon.starsectormarines.battle.squad.Squad#morale} has
     * dropped below the broken threshold and not yet recovered above the
     * clear threshold. Story B trigger — drives SurviveContact. Drains on
     * hits/deaths, recovers when out of contact; capped by alive/original
     * ratio so heavy casualties cap the recovery ceiling.
     */
    MORALE_BROKEN,
    /** Squad's primary target has crossed into the overwatch action's defined kill zone (range bucket + LOS stability). Story A trigger. */
    ENEMY_IN_KILL_ZONE,
    /** At least one squadmate is taking incoming fire at a cell with LOS to the firing enemy. Story A re-trigger condition. */
    UNDER_FIRE_AT_LOS,
    /** The squad's primary target is currently suppressed (under sustained inaccurate fire from a friendly). Story C effect predicate. */
    ENEMY_SUPPRESSED,
    /** The assigned member's cell is on the friendly side of the friendly-mech-to-threat line. Story E geometry predicate. */
    BEHIND_FRIENDLY_RELATIVE_TO_THREAT,
    /** The assigned member's individual reposition cooldown is ready. Story G predicate (re-frames the existing {@code REPOSITION_CHANCE} timing). */
    CAN_REPOSITION,
    /** The zone the squad's current action targets has no live enemy combatants. Story K predicate; scoped to the action's assigned target zone. */
    ZONE_CLEAR,
    /** An enemy combatant is currently standing on the portal cell the squad's choke-point action watches. Story L trigger. */
    ENEMY_IN_PORTAL_CELL,
    /** The squad's {@link com.dillon.starsectormarines.battle.squad.Squad#assignedNode} carries a "must hold" priority flag. Story H gating predicate. */
    NODE_IS_MUST_HOLD,
    /** The squad's primary target sits in a high-density cluster of enemy combatants. Story I gating predicate — high density makes pursuit costly. */
    THREAT_DENSITY_HIGH_AT_TARGET,

    // --- Mech GOAP Stage 1 surface (see roadmap/ai/14-mech-stage1.md) ---

    /**
     * An LR Support mech in this squad has reached its overwatch cell and is
     * firing on the kill corridor. Goal-side marker — set true by
     * {@code OverwatchKillZone.effects}; the {@code OverwatchKillZone} goal's
     * desired state. The action ships a custom-plan so the planner never
     * regresses through this predicate; kept for goal-effect symmetry and so
     * future role-anchored mech goals can chain.
     */
    KILL_ZONE_COVERED,
    /**
     * An Armored Support mech in this squad is within follow distance of its
     * designated friendly infantry squad's centroid. Goal-side marker — set
     * true by {@code BackstopAssignedSquad.effects}; the
     * {@code BackstopAssignedSquadGoal}'s desired state. Same custom-plan
     * pattern as {@link #KILL_ZONE_COVERED} — never observed at search time.
     * Future Story E mech-screened advance reads this from the infantry side
     * to know "my squad has a mech behind us."
     */
    SQUAD_BACKED
}
