package com.dillon.starsectormarines.battle.air.engine;

/** Shared presentation response for aircraft exhaust and its reflected hull light. */
public final class EngineBurn {
    private static final float DEMAND_FLOOR = .1f;
    private static final float ALPHA_FLOOR = .18f;

    private EngineBurn() {}

    /** Missing slot demand retains the uniform-throttle fallback. */
    public static float slotIntensity(float intensity, float[] perSlotDemand, int index) {
        if (perSlotDemand == null || index >= perSlotDemand.length) return intensity;
        float demand = Math.max(0f, Math.min(1f, perSlotDemand[index]));
        return intensity * (DEMAND_FLOOR + (1f - DEMAND_FLOOR) * demand);
    }

    /** Caller gates inactive engines; the floor describes a live nozzle at idle. */
    public static float flameAlpha(float alphaMult, float slotIntensity) {
        return alphaMult * (ALPHA_FLOOR + (1f - ALPHA_FLOOR) * slotIntensity);
    }
}
