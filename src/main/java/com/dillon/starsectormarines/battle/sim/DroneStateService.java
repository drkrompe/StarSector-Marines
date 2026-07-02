package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code DRONE_STATE} component — typed by-id access to a
 * drone's live patrol/pursuit vectors in the archetype {@link EntityWorld}.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}): it <em>owns</em>
 * a component's data and exposes the methods to read/modify it. A consumer reaches it
 * via {@code sim.droneState()} / {@code roster.droneState()} and calls
 * {@code droneState.patrolGoalX(id)} / {@code droneState.setPursuitTimer(id, v)}
 * directly — no {@link World} hop.
 *
 * <p>{@code DRONE_STATE} is OPTIONAL — <b>presence IS "is a live drone"</b>:
 * {@link #isDrone} is the presence check. Unlike {@code HOME}, the field readers
 * here are not fail-loud gated behind a separate doc note because every caller
 * already holds a known drone id (the type-tag {@code UnitType.isDrone()} check
 * happens upstream); still, a read on a unit with no {@code DRONE_STATE} throws,
 * same as every other optional-component data owner.
 *
 * <p><b>Single-writer per drone.</b> {@code battle.drone.DroneSwarmAction} runs
 * inside the <em>parallel</em> {@code UPDATE_UNITS} dispatch, but a drone only
 * ever writes its <em>own</em> {@code DRONE_STATE} — each unit is processed by
 * exactly one worker — so these unsynchronized reads/writes are safe exactly as
 * the old per-instance field writes on the dissolved {@code Drone} subclass were.
 *
 * <p>{@code homeHubId} is read-only here (set once at spawn from
 * {@code EntitySpec.homeHubId}, never reassigned) — no setter, mirroring
 * {@code TurretStateService.kind}'s seed-once-no-live-write shape.
 */
public final class DroneStateService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public DroneStateService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} is a live drone (carries DRONE_STATE). Gate the other reads on this. */
    public boolean isDrone(long id) { return entityWorld.has(id, components.DRONE_STATE); }

    /** Current patrol waypoint cell x; {@link Float#NaN} = no waypoint yet. */
    public float patrolGoalX(long id) { return entityWorld.getFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PATROL_GOAL_X); }

    /** Sets {@code id}'s current patrol waypoint cell x. */
    public void setPatrolGoalX(long id, float v) { entityWorld.setFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PATROL_GOAL_X, v); }

    /** Current patrol waypoint cell y; {@link Float#NaN} = no waypoint yet. */
    public float patrolGoalY(long id) { return entityWorld.getFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PATROL_GOAL_Y); }

    /** Sets {@code id}'s current patrol waypoint cell y. */
    public void setPatrolGoalY(long id, float v) { entityWorld.setFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PATROL_GOAL_Y, v); }

    /** Last-known pursuit-target cell x; {@link Float#NaN} = no pursuit target on record. */
    public float pursuitGoalX(long id) { return entityWorld.getFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PURSUIT_GOAL_X); }

    /** Sets {@code id}'s last-known pursuit-target cell x. */
    public void setPursuitGoalX(long id, float v) { entityWorld.setFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PURSUIT_GOAL_X, v); }

    /** Last-known pursuit-target cell y; {@link Float#NaN} = no pursuit target on record. */
    public float pursuitGoalY(long id) { return entityWorld.getFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PURSUIT_GOAL_Y); }

    /** Sets {@code id}'s last-known pursuit-target cell y. */
    public void setPursuitGoalY(long id, float v) { entityWorld.setFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PURSUIT_GOAL_Y, v); }

    /** Sim-seconds remaining on {@code id}'s pursuit latch; {@code 0} = latch expired, back to patrol. */
    public float pursuitTimer(long id) { return entityWorld.getFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PURSUIT_TIMER); }

    /** Sets {@code id}'s sim-seconds remaining on the pursuit latch. */
    public void setPursuitTimer(long id, float v) { entityWorld.setFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PURSUIT_TIMER, v); }

    /** Entity id of the hub that launched {@code id}, {@code 0L} = none. Set once at spawn; never reassigned. */
    public long homeHubId(long id) { return entityWorld.getLong(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_HOME_HUB_ID); }
}
