package com.dillon.starsectormarines.battle.warship;

/**
 * A warship's flux: the fire-rate governor. Every shot and every second of beam
 * adds flux to one pool; the pool drains continuously at the hull's dissipation
 * rate. It exists to give the ship a <em>rhythm</em> a player can read, not to
 * balance it: the ship's damage is untouched, and what flux decides is when the
 * guns are talking and when they are not.
 *
 * <p>A burst or a lance is <em>paid for when it is started</em>: the whole
 * burst's {@code energy/shot} times its rounds, or the beam's {@code energy/second}
 * times its burn. That keeps the pool honest about what the ship has committed
 * to, and lets the soft limit refuse a salvo that would not fit.
 *
 * <p>Capacity and dissipation come from the hull table's {@code max flux} and
 * {@code flux dissipation}, and what a shot costs from the weapon table's
 * {@code energy/shot} and {@code energy/second}. Point defence spends from the
 * same pool.
 *
 * <h2>The three states</h2>
 * <ul>
 *   <li>{@link State#FIRING} — the mounts may start new bursts and sweeps.</li>
 *   <li>{@link State#VENTING} — flux reached {@link #SOFT_LIMIT} of capacity, so
 *       the ship stops <em>starting</em> area fire (a burst already under way
 *       completes, a laid lance burns out) until flux has drained to
 *       {@link #RESUME_LEVEL}. This is the surge-then-rest rhythm; point defence
 *       is not held back by it. While firing, a salvo that would not fit in what
 *       is left of the pool is not started, so the soft limit also keeps a ship
 *       off a real overload; the only things that can still trip one are point
 *       defence and a salvo started from an empty pool that is itself larger than
 *       the pool. It can be switched off ({@link #softLimit}).</li>
 *   <li>{@link State#OVERLOADED} — flux reached capacity. Every mount falls
 *       silent, point defence included, and bursts in progress are abandoned
 *       (a lance already laid still burns out). It lasts {@link #OVERLOAD_MIN_SECONDS}
 *       to {@link #OVERLOAD_MAX_SECONDS}, longer the further the charge that
 *       tripped it went past capacity: one {@link #OVERLOAD_FULL_EXCESS} of
 *       capacity over is the longest. Dissipation continues throughout and the
 *       pool is empty when the ship comes back.</li>
 * </ul>
 *
 * <p>Disabled by default: a disabled pool never fills and every query says the
 * ship may fire.
 */
final class WarshipFlux {

    enum State {
        FIRING("firing"), VENTING("venting"), OVERLOADED("overloaded");

        final String label;

        State(String label) {
            this.label = label;
        }
    }

    /** Share of capacity at which the ship stops starting new fire. */
    static final float SOFT_LIMIT = 0.85f;
    /** Share of capacity the pool must drain to before the ship starts again. */
    static final float RESUME_LEVEL = 0.55f;
    static final float OVERLOAD_MIN_SECONDS = 4f;
    static final float OVERLOAD_MAX_SECONDS = 10f;
    /** Overshoot, as a share of capacity, that earns the longest overload. */
    static final float OVERLOAD_FULL_EXCESS = 0.1f;

    private final float baseCapacity;
    private final float baseDissipation;
    private boolean enabled;
    private float scale = 1f;
    private boolean softLimit = true;
    private float flux;
    private State state = State.FIRING;
    private float overloadSecondsRemaining;
    private int overloads;
    private boolean dissipationHeld;

    WarshipFlux(float baseCapacity, float baseDissipation) {
        this.baseCapacity = baseCapacity;
        this.baseDissipation = baseDissipation;
    }

    boolean enabled() {
        return enabled;
    }

    void enabled(boolean on) {
        this.enabled = on;
        if (!on) {
            flux = 0f;
            state = State.FIRING;
            overloadSecondsRemaining = 0f;
        }
    }

    float scale() {
        return scale;
    }

    void scale(float next) {
        if (!(next > 0f)) throw new IllegalArgumentException("flux scale must be positive");
        this.scale = next;
        flux = Math.min(flux, capacity());
    }

    void softLimit(boolean on) {
        this.softLimit = on;
        if (!on && state == State.VENTING) state = State.FIRING;
    }

    boolean softLimit() {
        return softLimit;
    }

    float capacity() {
        return baseCapacity * scale;
    }

    float dissipation() {
        return baseDissipation * scale;
    }

    float flux() {
        return flux;
    }

    /** Flux as a share of capacity, 0 to 1; zero when disabled. */
    float fraction() {
        return enabled ? Math.min(1f, flux / capacity()) : 0f;
    }

    State state() {
        return state;
    }

    boolean overloaded() {
        return enabled && state == State.OVERLOADED;
    }

    float overloadSecondsRemaining() {
        return overloaded() ? overloadSecondsRemaining : 0f;
    }

    /** How many times the pool has overloaded, for evidence. */
    int overloads() {
        return overloads;
    }

    /** Whether the ship may start a new burst or lay a new lance at all. */
    boolean canStartFire() {
        return !enabled || state == State.FIRING;
    }

    /** Whether a salvo costing {@code cost} may be started: the ship is firing and, under the soft limit, it fits. */
    boolean canStart(float cost) {
        if (!canStartFire()) return false;
        if (!enabled || !softLimit) return true;
        return flux <= 0f || flux + cost < capacity();
    }

    /** Whether point defence may shoot; only an overload silences it. */
    boolean canDefend() {
        return !overloaded();
    }

    /**
     * Holds the pool's dissipation off: while a shield is raised the ship vents
     * nothing. Set afresh every tick by whoever holds it (the shield); an
     * overload ignores it, because a ship that has overloaded has no shield up
     * and dissipates throughout.
     */
    void holdDissipation(boolean held) {
        this.dissipationHeld = held;
    }

    boolean dissipationHeld() {
        return dissipationHeld && !overloaded();
    }

    /** Drains the pool and moves between states. */
    void update(float dt) {
        if (!enabled) return;
        if (!dissipationHeld()) flux = Math.max(0f, flux - dissipation() * dt);
        if (state == State.OVERLOADED) {
            overloadSecondsRemaining -= dt;
            if (overloadSecondsRemaining <= 0f) {
                overloadSecondsRemaining = 0f;
                flux = 0f;
                state = State.FIRING;
            }
            return;
        }
        float capacity = capacity();
        if (state == State.FIRING && softLimit && flux >= SOFT_LIMIT * capacity) {
            state = State.VENTING;
        } else if (state == State.VENTING && flux <= RESUME_LEVEL * capacity) {
            state = State.FIRING;
        }
    }

    /** Adds {@code amount} of flux; reaching capacity overloads the ship. */
    void spend(float amount) {
        if (!enabled || amount <= 0f || state == State.OVERLOADED) return;
        flux += amount;
        float capacity = capacity();
        if (flux >= capacity) {
            float excess = (flux - capacity) / capacity;
            float t = Math.min(1f, excess / OVERLOAD_FULL_EXCESS);
            overloadSecondsRemaining = OVERLOAD_MIN_SECONDS + t * (OVERLOAD_MAX_SECONDS - OVERLOAD_MIN_SECONDS);
            flux = capacity;
            state = State.OVERLOADED;
            overloads++;
        }
    }
}
