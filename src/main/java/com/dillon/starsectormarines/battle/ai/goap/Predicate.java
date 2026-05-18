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
 * <p>The entries below are the Stage 1 parity set — enough to reproduce
 * {@code InfantryCombatantBehavior} through GOAP. Stage 2+ will add
 * {@code IN_COVER}, {@code FLANK_ROUTE_AVAILABLE}, {@code ENEMIES_SUPPRESSED},
 * etc.
 */
public enum Predicate {
    /** Squad has at least one alive enemy known to be on the map. */
    HAS_TARGET,
    /** The assigned member (or, at squad scope, at least one squadmate) has line of sight to the squad's primary target. */
    HAS_LOS_TO_TARGET,
    /** The assigned member is within their {@code attackRange} of the squad's primary target. */
    IN_RANGE_OF_TARGET,
    /** The assigned member is within {@link com.dillon.starsectormarines.battle.ai.InfantryCombatantBehavior#COHESION_RADIUS} cells of the squad centroid. */
    WITHIN_COHESION_RADIUS,
    /** Goal-side marker — set true by {@code EngageVisibleAction.effects}. The {@code EliminateEnemies} goal's desired state. */
    ENEMY_DAMAGED
}
