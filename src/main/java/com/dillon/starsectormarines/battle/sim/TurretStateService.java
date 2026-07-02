package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code TURRET_STATE} component — typed by-id access to a
 * turret's live aim/recoil/burst state in the archetype {@link EntityWorld}.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}): it <em>owns</em>
 * a component's data and exposes the methods to read/modify it. A consumer reaches it
 * via {@code sim.turretState()} / {@code roster.turretState()} and calls
 * {@code turretState.facingDegrees(id)} / {@code turretState.setRecoilTimer(id, v)}
 * directly — no {@link World} hop.
 *
 * <p>{@code TURRET_STATE} is OPTIONAL — <b>presence IS "is a live turret"</b>:
 * {@link #isTurret} is the presence check. Unlike {@code HOME}, the field readers
 * here are not fail-loud gated behind a separate doc note because every caller
 * already holds a known turret id (the type-tag {@code UnitType.isTurret()} check
 * happens upstream); still, a read on a unit with no {@code TURRET_STATE} throws,
 * same as every other optional-component data owner.
 *
 * <p><b>Single-writer per turret.</b> {@code battle.turret.TurretBehavior} runs
 * inside the <em>parallel</em> {@code UPDATE_UNITS} dispatch, but a turret only
 * ever writes its <em>own</em> {@code TURRET_STATE} — each unit is processed by
 * exactly one worker — so these unsynchronized reads/writes are safe exactly as
 * the old per-instance field writes on the dissolved {@code MapTurret} subclass
 * were.
 *
 * <p><b>The burst fields are deliberately self-contained</b> — see the
 * {@link BattleComponents#TURRET_STATE} javadoc: they are NOT the COMBAT burst
 * columns infantry/mech/drone {@code beginBurst} writes, because
 * {@code battle.infantry.InfantryWeapons.tick} would double-process a turret
 * burst through the wrong (infantry) firing pipeline if it did.
 */
public final class TurretStateService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public TurretStateService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} is a live turret (carries TURRET_STATE). Gate the other reads on this. */
    public boolean isTurret(long id) { return entityWorld.has(id, components.TURRET_STATE); }

    /** Current barrel facing, degrees. 0° = +Y (north). */
    public float facingDegrees(long id) { return entityWorld.getFloat(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_FACING_DEGREES); }

    /** Sets {@code id}'s current barrel facing, degrees. */
    public void setFacingDegrees(long id, float v) { entityWorld.setFloat(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_FACING_DEGREES, v); }

    /** Sim-seconds since {@code id}'s last fired round — drives the renderer's per-round recoil slide. */
    public float recoilTimer(long id) { return entityWorld.getFloat(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_RECOIL_TIMER); }

    /** Sets {@code id}'s sim-seconds since its last fired round. */
    public void setRecoilTimer(long id, float v) { entityWorld.setFloat(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_RECOIL_TIMER, v); }

    /** The {@link TurretKind} baked into {@code id} at construction (stats/sprite/firing profile). */
    public TurretKind kind(long id) { return (TurretKind) entityWorld.getObject(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_KIND); }

    /** Rounds left in {@code id}'s current burst, excluding the trigger-pull round; {@code 0} = idle/single-shot. */
    public int burstRemaining(long id) { return entityWorld.getInt(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_BURST_REMAINING); }

    /** Sets {@code id}'s remaining burst-round count. */
    public void setBurstRemaining(long id, int v) { entityWorld.setInt(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_BURST_REMAINING, v); }

    /** Sim-seconds until {@code id}'s next burst round fires. */
    public float burstTimer(long id) { return entityWorld.getFloat(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_BURST_TIMER); }

    /** Sets {@code id}'s sim-seconds until the next burst round fires. */
    public void setBurstTimer(long id, float v) { entityWorld.setFloat(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_BURST_TIMER, v); }

    /** Entity id of the target locked when {@code id}'s burst started, {@code 0L} when idle. */
    public long burstTargetId(long id) { return entityWorld.getLong(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_BURST_TARGET_ID); }

    /** Sets the entity id of the target locked for {@code id}'s current burst. */
    public void setBurstTargetId(long id, long v) { entityWorld.setLong(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_BURST_TARGET_ID, v); }
}
