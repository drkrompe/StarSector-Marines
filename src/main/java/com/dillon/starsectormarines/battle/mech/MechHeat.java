package com.dillon.starsectormarines.battle.mech;

/** Battle-owned shared thermal reservoir. Control handback never resets it. */
public final class MechHeat {
    public static final float CAPACITY = 100f;
    public static final float COOLING_PER_SECOND = 8f;
    public static final float WARNING = 75f;
    private float heat;
    private boolean venting;
    private boolean aiCooling;

    public float value() { return heat; }
    public boolean venting() { return venting; }
    public boolean aiCooling() { return aiCooling; }

    public boolean permits(float cost, boolean autonomous) {
        return Float.isFinite(cost) && cost >= 0f && !venting
                && (!autonomous || (!aiCooling && heat + cost <= 90f)) && heat + cost <= CAPACITY;
    }

    /** Reserve only after the first round actually fires; cancelled packets do not refund heat. */
    public void commit(float cost) {
        if (!permits(cost, false)) throw new IllegalStateException("Mech trigger exceeds thermal headroom");
        heat += cost;
        if (heat >= WARNING) aiCooling = true;
        if (heat >= 95f) venting = true;
    }

    public void advance(float dt) {
        if (!Float.isFinite(dt) || dt < 0f) throw new IllegalArgumentException("Invalid cooling step");
        heat = Math.max(0f, heat - COOLING_PER_SECOND * dt);
        if (heat <= 55f) venting = false;
        if (heat <= 40f) aiCooling = false;
    }
}
