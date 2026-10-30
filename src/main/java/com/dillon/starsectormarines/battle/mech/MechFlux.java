package com.dillon.starsectormarines.battle.mech;

/** Battle-owned shared flux reservoir. Control handback never resets it. */
public final class MechFlux {
    public static final float CAPACITY = 100f;
    public static final float DISSIPATION_PER_SECOND = 6f;
    public static final float WARNING = 75f;
    private float capacity = CAPACITY;
    private float dissipation = DISSIPATION_PER_SECOND;
    private float flux;
    private boolean venting;
    private boolean aiDissipating;

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

    public float capacity() { return capacity; }
    public float dissipationPerSecond() { return dissipation; }
    public float value() { return flux; }
    public boolean venting() { return venting; }
    public boolean aiDissipating() { return aiDissipating; }

    public boolean permits(float cost, boolean autonomous) {
        return Float.isFinite(cost) && cost >= 0f && !venting
                && (!autonomous || (!aiDissipating && flux + cost <= scaled(90f))) && flux + cost <= capacity;
    }

    /**
     * Seconds of dissipation until {@link #permits} would admit {@code cost}: zero when it does now, and infinite when
     * it never can (the cost is past what the reservoir admits, or nothing dissipates). Nothing else committing in the
     * meantime is assumed, so a caller still asks {@link #permits} before it fires.
     */
    public float secondsUntilPermits(float cost, boolean autonomous) {
        if (permits(cost, autonomous)) return 0f;
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

    public void advance(float dt) {
        if (!Float.isFinite(dt) || dt < 0f) throw new IllegalArgumentException("Invalid dissipation step");
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

    /** A stock-100 threshold expressed as the same fraction of this reservoir. */
    private float scaled(float stockThreshold) {
        return capacity == CAPACITY ? stockThreshold : stockThreshold * capacity / CAPACITY;
    }
}
