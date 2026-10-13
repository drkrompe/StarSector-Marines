package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.drone.DroneHash;

/**
 * Where each builder drone hovers while it works a site, and when its torch is lit.
 *
 * <p>The builders share the ring round the site out equally, one station each,
 * starting on the side the tender is on so the first to arrive are the nearest.
 * A drone keeps its station for the whole job; it hovers there with a small
 * jitter of its own, never still, never on another's point. The torch flickers in
 * short bursts keyed on the drone's id and the tick, so every drone strikes and
 * breaks its arc at its own moments and the picture needs no stored state.
 * Pure arithmetic; bearings use the air layer's sprite convention (0 is +Y,
 * counter-clockwise positive).
 */
public final class BuilderStations {

    /** Radius of the station ring, in cells. */
    public static final float RING_CELLS = 1.6f;
    /** Peak distance a hovering builder wanders from its station, in cells. */
    public static final float JITTER_CELLS = 0.22f;
    /** Ticks in one burst of torch flicker. */
    public static final int FLICKER_TICKS = 4;

    private BuilderStations() {}

    /**
     * The station for builder {@code slot} of {@code count}.
     *
     * @param towardTenderDeg bearing from the site to the tender
     * @param phase the drone's own phase in [0, 1)
     * @return {@code {x, y}}
     */
    public static float[] station(float siteX, float siteY, float towardTenderDeg, int slot, int count,
                                  float seconds, float phase) {
        int n = Math.max(1, count);
        float bearing = towardTenderDeg + 360f * slot / n;
        double r = Math.toRadians(bearing);
        double tau = Math.PI * 2.0;
        float jx = JITTER_CELLS * (float) Math.sin(tau * (0.8 * seconds + phase));
        float jy = JITTER_CELLS * (float) Math.cos(tau * (1.1 * seconds + 2.0 * phase));
        return new float[]{
                siteX + RING_CELLS * (float) -Math.sin(r) + jx,
                siteY + RING_CELLS * (float) Math.cos(r) + jy};
    }

    /** Whether builder {@code id}'s torch is lit on {@code tick}: on about two bursts in three. */
    public static boolean torchLit(long id, int tick) {
        return DroneHash.unit(id, 100 + Math.floorDiv(tick, FLICKER_TICKS)) > 0.33f;
    }
}
