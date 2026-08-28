package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code MITIGATION} component — the screen an actor holds
 * up ({@code combat-durability-nouns.md}).
 *
 * <p>Mitigation is a bounded fraction of post-cover damage refused, for an
 * explicit duration, across a bounded arc measured from the wearer's facing at
 * the moment of the hit. It is not armour and never becomes armour: it removes
 * damage before the armour package sees it, and what it removed is reported as
 * its own quantity.
 *
 * <p><b>The three laws are enforced here, not left to authoring.</b>
 * {@link #grant} clamps the fraction below {@link #MAX_FRACTION} so a screen can
 * never be total, clamps the arc below {@link #MAX_ARC_DEGREES} so it can never
 * be all-round, and refuses a non-positive duration so there is no mitigation
 * without a clock. Authoring validates the same rules with better error
 * messages; this is the floor under every future source.
 *
 * <p><b>Mitigations do not sum.</b> One slot, and {@link #grant} keeps the
 * stronger of what is live and what is offered. Summation is how two
 * individually reasonable authored numbers reach immunity without anyone
 * noticing, and the arc rule cannot rescue a design that has already reached 1.
 * The bound this buys is deliberate: a weaker screen offered under a stronger
 * live one is dropped rather than queued behind it.
 */
public final class MitigationService {

    /** The largest fraction a screen may refuse. A hit that cannot land is an off-switch, not a capability. */
    public static final float MAX_FRACTION = Math.nextDown(1f);

    /** The widest arc a screen may cover. Turning to deal with what is behind you has to cost the front. */
    public static final float MAX_ARC_DEGREES = Math.nextDown(360f);

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public MitigationService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Whether this actor carries anything that can raise a screen. Most do not. */
    public boolean has(long id) {
        return entityWorld.has(id, components.MITIGATION);
    }

    /** Sim-seconds left on the raised screen; {@code 0} when nothing is up. */
    public float remaining(long id) {
        return has(id) ? Math.max(0f, get(id, BattleComponents.MITIGATION_REMAINING)) : 0f;
    }

    /** True while a screen is actually raised. */
    public boolean isActive(long id) {
        return remaining(id) > 0f;
    }

    /** The fraction the raised screen refuses inside its arc; {@code 0} when nothing is up. */
    public float fraction(long id) {
        return isActive(id) ? get(id, BattleComponents.MITIGATION_FRACTION) : 0f;
    }

    /** Total arc width the raised screen covers, centred on {@link #facingDegrees}. */
    public float arcDegrees(long id) {
        return isActive(id) ? get(id, BattleComponents.MITIGATION_ARC_DEGREES) : 0f;
    }

    /** The direction the screen currently points. Simulation state, maintained by {@link MitigationSystem}. */
    public float facingDegrees(long id) {
        return has(id) ? get(id, BattleComponents.MITIGATION_FACING_DEGREES) : 0f;
    }

    /**
     * Raises a screen, or keeps the stronger one when something is already up.
     * Returns false and changes nothing when the actor carries no mitigation
     * capability, when the duration is not positive, or when a stronger screen
     * is already live.
     */
    public boolean grant(long id, float fraction, float arcDegrees, float durationSeconds) {
        if (!has(id)) return false;
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0f) return false;
        float offered = clamp(fraction, MAX_FRACTION);
        if (offered <= 0f) return false;
        if (isActive(id) && fraction(id) >= offered) return false;
        set(id, BattleComponents.MITIGATION_FRACTION, offered);
        set(id, BattleComponents.MITIGATION_ARC_DEGREES, clamp(arcDegrees, MAX_ARC_DEGREES));
        set(id, BattleComponents.MITIGATION_REMAINING, durationSeconds);
        return true;
    }

    /** Points the screen. Wrapped to {@code [-180, 180)} so arc tests need no second normalization. */
    public void face(long id, float degrees) {
        if (!has(id)) return;
        set(id, BattleComponents.MITIGATION_FACING_DEGREES, wrapDegrees(degrees));
    }

    /**
     * Drains the clock by one tick and drops the screen the moment it expires,
     * leaving no residue — the actor then resolves damage exactly as it did
     * before anything was raised. Idempotent at zero.
     */
    public void tick(long id, float dt) {
        if (!isActive(id)) return;
        float left = remaining(id) - dt;
        if (left > 0f) {
            set(id, BattleComponents.MITIGATION_REMAINING, left);
            return;
        }
        clear(id);
    }

    /** Drops the screen now. Safe on an actor that never had one. */
    public void clear(long id) {
        if (!has(id)) return;
        set(id, BattleComponents.MITIGATION_FRACTION, 0f);
        set(id, BattleComponents.MITIGATION_ARC_DEGREES, 0f);
        set(id, BattleComponents.MITIGATION_REMAINING, 0f);
    }

    /**
     * The fraction this actor refuses from a hit arriving out of
     * {@code (sourceX, sourceY)} while it stands at {@code (targetX, targetY)}.
     * Returns {@code 0} outside the arc, with nothing raised, and for a hit
     * whose source cannot be located — an unattributed hit has no bearing to
     * measure the arc against, and inventing one would make a screen work
     * against scripted damage it was never facing.
     */
    public float fractionAgainst(long id, float targetX, float targetY,
                                 float sourceX, float sourceY) {
        if (!isActive(id)) return 0f;
        float dx = sourceX - targetX;
        float dy = sourceY - targetY;
        if (dx == 0f && dy == 0f) return 0f;
        return covers(facingDegrees(id), AirBody.facingToward(dx, dy), arcDegrees(id))
                ? fraction(id) : 0f;
    }

    /**
     * Whether a screen of {@code arcDegrees} centred on {@code facingDegrees}
     * covers a hit arriving along {@code incomingDegrees}. Pure so prediction,
     * balance harnesses, and presentation all ask the same question the damage
     * path asks.
     */
    public static boolean covers(float facingDegrees, float incomingDegrees, float arcDegrees) {
        if (!(arcDegrees > 0f)) return false;
        float half = Math.min(arcDegrees, MAX_ARC_DEGREES) * 0.5f;
        return Math.abs(wrapDegrees(incomingDegrees - facingDegrees)) <= half;
    }

    /** Normalizes any angle to {@code [-180, 180)}. */
    public static float wrapDegrees(float degrees) {
        float wrapped = (degrees + 180f) % 360f;
        if (wrapped < 0f) wrapped += 360f;
        return wrapped - 180f;
    }

    private static float clamp(float value, float ceiling) {
        if (!Float.isFinite(value) || value <= 0f) return 0f;
        return Math.min(value, ceiling);
    }

    private float get(long id, int field) {
        return entityWorld.getFloat(id, components.MITIGATION, field);
    }

    private void set(long id, int field, float value) {
        entityWorld.setFloat(id, components.MITIGATION, field, value);
    }
}
