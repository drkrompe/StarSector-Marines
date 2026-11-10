package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.turret.FluxPool;

/**
 * Battle-owned shared flux reservoir. Control handback never resets it.
 *
 * <p>A mech with a shield emitter feeds its shield from this same pool ({@code FluxShield}'s pooled shield): every
 * round the shield stops and every second it is held up {@link #charge charges} it, so blocking spends the guns'
 * headroom and firing spends the shield's. A charge is never refused; one that fills the pool
 * {@link #overload overloads} the mech: shield down and every gun silent while the pool vents to empty.
 */
public final class MechFlux implements FluxPool {
    public static final float CAPACITY = 100f;
    public static final float DISSIPATION_PER_SECOND = 6f;
    public static final float WARNING = 75f;
    private float capacity = CAPACITY;
    private float dissipation = DISSIPATION_PER_SECOND;
    private float flux;
    private boolean venting;
    private boolean aiDissipating;
    private float overloadSeconds;
    private float overloadLeft;

    public MechFlux() { }

    public MechFlux(float capacity, float dissipationPerSecond) {
        configure(capacity, dissipationPerSecond);
    }

    /** Re-size the reservoir; only valid before any flux has been committed. */
    public void configure(float capacity, float dissipationPerSecond) {
        if (!(capacity > 0f) || !Float.isFinite(capacity)
                || !(dissipationPerSecond >= 0f) || !Float.isFinite(dissipationPerSecond)) {
            throw new IllegalArgumentException("Invalid flux frame");
        }
        if (flux != 0f || venting || aiDissipating) {
            throw new IllegalStateException("Flux frame fixed once flux is committed");
        }
        this.capacity = capacity;
        this.dissipation = dissipationPerSecond;
    }

    @Override
    public float capacity() { return capacity; }
    public float dissipationPerSecond() { return dissipation; }
    @Override
    public float value() { return flux; }
    @Override
    public boolean venting() { return venting; }
    public boolean aiDissipating() { return aiDissipating; }
    /** Whether the pool is overloaded: every gun on it silent, and its shield down, until it has vented. */
    @Override
    public boolean overloaded() { return overloadLeft > 0f; }
    /** Share of the overload still to run, {@code [0, 1]}; zero when not overloaded. */
    @Override
    public float overloadShare() { return overloadLeft > 0f ? overloadLeft / overloadSeconds : 0f; }

    public boolean permits(float cost, boolean autonomous) {
        return Float.isFinite(cost) && cost >= 0f && !venting && overloadLeft <= 0f
                && (!autonomous || (!aiDissipating && flux + cost <= scaled(90f))) && flux + cost <= capacity;
    }

    /**
     * Seconds of dissipation until {@link #permits} would admit {@code cost}: zero when it does now, and infinite when
     * it never can (the cost is past what the reservoir admits, or nothing dissipates). Nothing else committing in the
     * meantime is assumed, so a caller still asks {@link #permits} before it fires.
     */
    public float secondsUntilPermits(float cost, boolean autonomous) {
        if (permits(cost, autonomous)) return 0f;
        if (overloadLeft > 0f) return overloadLeft + secondsAfterOverload(cost, autonomous);
        float level = capacity - cost;
        if (autonomous) level = Math.min(level, scaled(90f) - cost);
        if (venting) level = Math.min(level, scaled(55f));
        if (autonomous && aiDissipating) level = Math.min(level, scaled(40f));
        if (level < 0f || !(dissipation > 0f)) return Float.POSITIVE_INFINITY;
        return Math.max(0f, flux - level) / dissipation;
    }

    /** Reserve only after the first round actually fires; cancelled packets do not refund flux. */
    public void commit(float cost) {
        if (!permits(cost, false)) throw new IllegalStateException("Mech trigger exceeds flux headroom");
        flux += cost;
        if (flux >= scaled(WARNING)) aiDissipating = true;
        if (flux >= scaled(95f)) venting = true;
    }

    /**
     * Flux from outside the guns, a blocked round or a held shield: never refused, unlike a trigger's
     * {@link #commit}, and it never starts a vent, so a shield stays up until the pool is full. Past the
     * {@link #WARNING} mark the AI's guns hold fire for the shield. True when it filled the pool, which the shield
     * answers by {@link #overload overloading} it; nothing is charged while overloaded.
     */
    @Override
    public boolean charge(float amount) {
        if (!Float.isFinite(amount) || amount < 0f) throw new IllegalArgumentException("Invalid flux charge");
        if (overloadLeft > 0f) return false;
        flux = Math.min(capacity, flux + amount);
        if (flux >= scaled(WARNING)) aiDissipating = true;
        return flux >= capacity;
    }

    /**
     * Overloads the pool for {@code seconds}: every gun on it silent, its shield down, and the pool venting from full
     * to empty over that time; then it is clear, with no vent or AI hold left to run.
     */
    @Override
    public void overload(float seconds) {
        if (!(seconds > 0f) || !Float.isFinite(seconds)) throw new IllegalArgumentException("Invalid overload");
        overloadSeconds = seconds;
        overloadLeft = seconds;
        flux = capacity;
        venting = false;
        aiDissipating = true;
    }

    public void advance(float dt) {
        if (!Float.isFinite(dt) || dt < 0f) throw new IllegalArgumentException("Invalid dissipation step");
        if (overloadLeft > 0f) {
            overloadLeft = Math.max(0f, overloadLeft - dt);
            flux = capacity * overloadLeft / overloadSeconds;
            if (overloadLeft > 0f) return;
            aiDissipating = false;
            return;
        }
        flux = Math.max(0f, flux - dissipation * dt);
        if (flux <= scaled(55f)) venting = false;
        if (flux <= scaled(40f)) aiDissipating = false;
    }

    /**
     * Holds the reservoir at {@code value}, venting or not, with dissipation off so it stays there: for staging a
     * look at a hot mech, never for play.
     */
    void stage(float value, boolean venting) {
        this.dissipation = 0f;
        this.flux = Math.max(0f, Math.min(capacity, value));
        this.venting = venting;
        this.aiDissipating = venting || flux >= scaled(WARNING);
    }

    /** What {@link #secondsUntilPermits} would answer for {@code cost} on the empty, clear pool an overload leaves. */
    private float secondsAfterOverload(float cost, boolean autonomous) {
        float limit = autonomous ? Math.min(capacity, scaled(90f)) : capacity;
        return cost <= limit ? 0f : Float.POSITIVE_INFINITY;
    }

    /** A stock-100 threshold expressed as the same fraction of this reservoir. */
    private float scaled(float stockThreshold) {
        return capacity == CAPACITY ? stockThreshold : stockThreshold * capacity / CAPACITY;
    }
}
