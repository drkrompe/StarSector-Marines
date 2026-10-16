package com.dillon.starsectormarines.battle.mech;

/** Battle-owned shared thermal reservoir. Control handback never resets it. */
public final class MechHeat {
    public static final float CAPACITY = 100f;
    public static final float COOLING_PER_SECOND = 6f;
    public static final float WARNING = 75f;
    private float capacity = CAPACITY;
    private float cooling = COOLING_PER_SECOND;
    private float heat;
    private boolean venting;
    private boolean aiCooling;

    public MechHeat() { }

    public MechHeat(float capacity, float coolingPerSecond) {
        configure(capacity, coolingPerSecond);
    }

    /** Re-size the reservoir; only valid before any heat has been committed. */
    public void configure(float capacity, float coolingPerSecond) {
        if (!(capacity > 0f) || !Float.isFinite(capacity)
                || !(coolingPerSecond >= 0f) || !Float.isFinite(coolingPerSecond)) {
            throw new IllegalArgumentException("Invalid heat frame");
        }
        if (heat != 0f || venting || aiCooling) {
            throw new IllegalStateException("Heat frame fixed once heat is committed");
        }
        this.capacity = capacity;
        this.cooling = coolingPerSecond;
    }

    public float capacity() { return capacity; }
    public float coolingPerSecond() { return cooling; }
    public float value() { return heat; }
    public boolean venting() { return venting; }
    public boolean aiCooling() { return aiCooling; }

    public boolean permits(float cost, boolean autonomous) {
        return Float.isFinite(cost) && cost >= 0f && !venting
                && (!autonomous || (!aiCooling && heat + cost <= scaled(90f))) && heat + cost <= capacity;
    }

    /** Reserve only after the first round actually fires; cancelled packets do not refund heat. */
    public void commit(float cost) {
        if (!permits(cost, false)) throw new IllegalStateException("Mech trigger exceeds thermal headroom");
        heat += cost;
        if (heat >= scaled(WARNING)) aiCooling = true;
        if (heat >= scaled(95f)) venting = true;
    }

    public void advance(float dt) {
        if (!Float.isFinite(dt) || dt < 0f) throw new IllegalArgumentException("Invalid cooling step");
        heat = Math.max(0f, heat - cooling * dt);
        if (heat <= scaled(55f)) venting = false;
        if (heat <= scaled(40f)) aiCooling = false;
    }

    /** A stock-100 threshold expressed as the same fraction of this reservoir. */
    private float scaled(float stockThreshold) {
        return capacity == CAPACITY ? stockThreshold : stockThreshold * capacity / CAPACITY;
    }
}
