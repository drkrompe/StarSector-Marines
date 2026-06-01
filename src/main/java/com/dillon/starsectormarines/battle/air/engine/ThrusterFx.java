package com.dillon.starsectormarines.battle.air.engine;

/**
 * Per-air-entity engine-FX state component — the temporally-smoothed per-slot
 * thrust demand. Lives in a {@code ComponentStore<ThrusterFx>} keyed by air
 * entity id; an air craft that draws plumes <em>has</em> one, transports without
 * engine slots don't. The instantaneous target comes from {@link ThrusterDemand}
 * (a pure function of the flight model); this carries the value across ticks so
 * {@link ThrusterFxSystem} can ramp it instead of letting it snap 0↔1 with the
 * bang-bang acceleration the steering model produces.
 *
 * <p>Pure data: {@link #smoothed}{@code [i]} is the current glow weight in
 * {@code [0,1]} for slot {@code i}, the value the renderer feeds
 * {@link EngineFxRenderer} as the per-slot demand.
 */
public final class ThrusterFx {

    /** Smoothed per-slot demand in {@code [0,1]}, one entry per engine slot. */
    public final float[] smoothed;

    public ThrusterFx(int slotCount) {
        this.smoothed = new float[slotCount];
    }
}
