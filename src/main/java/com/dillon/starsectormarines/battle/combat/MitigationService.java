package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code MITIGATION} component — the screen an actor holds
 * up ({@code combat-durability-nouns.md}).
 *
 * <p>Mitigation is a <b>soak pool</b>: a bounded quantity of post-cover damage
 * the screen absorbs from its covered arc, spent down as it absorbs and gone
 * when empty. It is not armour and never becomes armour — it removes damage
 * before the armour package sees it, and what it removed is reported as its own
 * quantity — but like armour it is finite, and that is the whole point. A
 * screen therefore ends two ways: the clock runs out, or the pool breaks.
 *
 * <p><b>The pool is what makes concentrated fire an answer.</b> A fraction held
 * for a duration is a soft invulnerability window with nothing to do about it
 * but wait; a pool is a quantity that massed fire can beat. A single hit larger
 * than what is left spends the remainder and carries the rest through to
 * armour at the ordinary efficiency — it neither wastes the overflow nor
 * absorbs it.
 *
 * <p><b>The laws are enforced here, not left to authoring.</b> {@link #grant}
 * clamps the arc below {@link #MAX_ARC_DEGREES} so a screen can never be
 * all-round, and refuses a non-positive duration or pool so there is no
 * mitigation without a clock and none without something to spend. Authoring
 * validates the same rules with better error messages; this is the floor under
 * every future source. Totality needs no separate rule any more: a finite pool
 * cannot refuse everything.
 *
 * <p><b>Mitigations do not sum.</b> One slot, and {@link #grant} keeps the
 * larger of what is live and what is offered. Summation is how two individually
 * reasonable authored numbers reach an unbeatable pool without anyone noticing.
 * The bound this buys is deliberate: a smaller screen offered under a larger
 * live one is dropped rather than queued behind it.
 */
public final class MitigationService {

    /** The widest arc a screen may cover. Turning to deal with what is behind you has to cost the front. */
    public static final float MAX_ARC_DEGREES = Math.nextDown(360f);

    /** How long the "this screen just broke" mark stays readable, in sim-seconds. */
    public static final float BREAK_FLASH_SECONDS = 0.45f;

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

    /**
     * Post-cover damage the raised screen can still absorb; {@code 0} when
     * nothing is up. This is the quantity, and it only ever falls.
     */
    public float soakRemaining(long id) {
        return has(id) ? Math.max(0f, get(id, BattleComponents.MITIGATION_SOAK_REMAINING)) : 0f;
    }

    /** The pool the live screen was raised with; {@code 0} when nothing is up. */
    public float soakCapacity(long id) {
        return has(id) ? Math.max(0f, get(id, BattleComponents.MITIGATION_SOAK_CAPACITY)) : 0f;
    }

    /**
     * How much of the pool is left, in {@code [0, 1]}. Presentation asks this;
     * the damage path never does.
     */
    public float soakFraction(long id) {
        float capacity = soakCapacity(id);
        return capacity > 0f ? Math.min(1f, soakRemaining(id) / capacity) : 0f;
    }

    /** True while a screen is actually raised: it has time left and something left to spend. */
    public boolean isActive(long id) {
        return remaining(id) > 0f && soakRemaining(id) > 0f;
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
     * Sim-seconds left of the mark left by the absorb that emptied the pool.
     * Presentation-only: it says a screen was beaten down rather than timed out,
     * which is a different thing to look at.
     */
    public float breakFlashRemaining(long id) {
        return has(id) ? Math.max(0f, get(id, BattleComponents.MITIGATION_BREAK_FLASH)) : 0f;
    }

    /**
     * Raises a screen with {@code soakAmount} of pool, or keeps the larger one
     * when something is already up. Returns false and changes nothing when the
     * actor carries no mitigation capability, when the duration or the pool is
     * not positive, or when a larger screen is already live.
     */
    public boolean grant(long id, float soakAmount, float arcDegrees, float durationSeconds) {
        if (!has(id)) return false;
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0f) return false;
        if (!Float.isFinite(soakAmount) || soakAmount <= 0f) return false;
        if (isActive(id) && soakRemaining(id) >= soakAmount) return false;
        set(id, BattleComponents.MITIGATION_SOAK_REMAINING, soakAmount);
        set(id, BattleComponents.MITIGATION_SOAK_CAPACITY, soakAmount);
        set(id, BattleComponents.MITIGATION_ARC_DEGREES, clampArc(arcDegrees));
        set(id, BattleComponents.MITIGATION_REMAINING, durationSeconds);
        set(id, BattleComponents.MITIGATION_BREAK_FLASH, 0f);
        return true;
    }

    /** Points the screen. Wrapped to {@code [-180, 180)} so arc tests need no second normalization. */
    public void face(long id, float degrees) {
        if (!has(id)) return;
        set(id, BattleComponents.MITIGATION_FACING_DEGREES, wrapDegrees(degrees));
    }

    /**
     * Spends {@code amount} out of the pool and drops the screen the moment the
     * pool empties, leaving the "it broke" mark behind for presentation to
     * find. Called by the damage path with what the durability model actually
     * absorbed, so the pool can never fall by more than the hit removed.
     */
    public void absorb(long id, float amount) {
        if (!isActive(id) || !Float.isFinite(amount) || amount <= 0f) return;
        float left = soakRemaining(id) - amount;
        if (left > 0f) {
            set(id, BattleComponents.MITIGATION_SOAK_REMAINING, left);
            return;
        }
        clear(id);
        set(id, BattleComponents.MITIGATION_BREAK_FLASH, BREAK_FLASH_SECONDS);
    }

    /**
     * Drains the clock and the break mark by one tick, and drops the screen the
     * moment its window expires — leaving no residue, so the actor then resolves
     * damage exactly as it did before anything was raised. Idempotent at zero.
     */
    public void tick(long id, float dt) {
        if (!has(id)) return;
        float flash = breakFlashRemaining(id);
        if (flash > 0f) {
            set(id, BattleComponents.MITIGATION_BREAK_FLASH, Math.max(0f, flash - dt));
        }
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
        set(id, BattleComponents.MITIGATION_SOAK_REMAINING, 0f);
        set(id, BattleComponents.MITIGATION_SOAK_CAPACITY, 0f);
        set(id, BattleComponents.MITIGATION_ARC_DEGREES, 0f);
        set(id, BattleComponents.MITIGATION_REMAINING, 0f);
    }

    /**
     * How much this actor's screen is able to absorb from a hit arriving out of
     * {@code (sourceX, sourceY)} while it stands at {@code (targetX, targetY)}.
     * Returns {@code 0} outside the arc, with nothing raised, and for a hit
     * whose source cannot be located — an unattributed hit has no bearing to
     * measure the arc against, and inventing one would make a screen work
     * against scripted damage it was never facing.
     *
     * <p>This is an <em>offer</em>, not a spend: the durability model decides
     * how much of it the hit actually consumed, and {@link #absorb} is what
     * takes that out of the pool.
     */
    public float soakAgainst(long id, float targetX, float targetY,
                             float sourceX, float sourceY) {
        if (!isActive(id)) return 0f;
        float dx = sourceX - targetX;
        float dy = sourceY - targetY;
        if (dx == 0f && dy == 0f) return 0f;
        return covers(facingDegrees(id), AirBody.facingToward(dx, dy), arcDegrees(id))
                ? soakRemaining(id) : 0f;
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

    private static float clampArc(float value) {
        if (!Float.isFinite(value) || value <= 0f) return 0f;
        return Math.min(value, MAX_ARC_DEGREES);
    }

    private float get(long id, int field) {
        return entityWorld.getFloat(id, components.MITIGATION, field);
    }

    private void set(long id, int field, float value) {
        entityWorld.setFloat(id, components.MITIGATION, field, value);
    }
}
