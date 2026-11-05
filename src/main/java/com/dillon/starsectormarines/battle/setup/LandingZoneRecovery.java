package com.dillon.starsectormarines.battle.setup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether a company is back at its landing zone: every surviving body on the ground (a soldier, a mech, a vehicle)
 * stands inside the zone's circle, or has stopped trying to reach it.
 *
 * <p>A body outside the circle has stopped trying when it has gone {@code stallTicks} without getting a cell closer
 * to the pad than it ever was: a mech wedged in rubble or a vehicle its own pathing cannot bring out would otherwise
 * hold the mission open for good, and no route proof answers that for every chassis alike. A body still making its way
 * keeps the company waiting. Someone already aboard a pickup counts as back, and at least one survivor must be.
 */
public final class LandingZoneRecovery {
    /** One body on the ground, by id, at a world position. */
    public record Body(long id, float x, float y) { }

    private record Approach(float bestDistance, int bestTick) { }

    private final float padX, padY, radius;
    private final int stallTicks;
    private final Map<Long, Approach> approaches = new HashMap<>();
    private int inside, coming, stalled;

    public LandingZoneRecovery(float padX, float padY, float radius, int stallTicks) {
        if (!(radius > 0f) || stallTicks <= 0) throw new IllegalArgumentException("a landing zone needs a radius and a stall time");
        this.padX = padX; this.padY = padY; this.radius = radius; this.stallTicks = stallTicks;
    }

    /**
     * Reads where the company stands on {@code tick}.
     *
     * @param ground every surviving body of the company on the ground
     * @param aboard survivors already aboard a pickup that has not been lost
     * @return whether the company is back
     */
    public boolean observe(int tick, List<Body> ground, int aboard) {
        inside = coming = stalled = 0;
        Map<Long, Approach> seen = new HashMap<>();
        for (Body body : ground) {
            float distance = (float) Math.hypot(body.x() - padX, body.y() - padY);
            if (distance <= radius) {
                inside++;
                continue;
            }
            Approach approach = approaches.get(body.id());
            if (approach == null || distance <= approach.bestDistance() - 1f) approach = new Approach(distance, tick);
            seen.put(body.id(), approach);
            if (tick - approach.bestTick() >= stallTicks) stalled++; else coming++;
        }
        // A body that reached the circle and left again starts its approach afresh.
        approaches.clear();
        approaches.putAll(seen);
        return coming == 0 && inside + aboard > 0;
    }

    /** Bodies inside the circle at the last observation. */
    public int inside() { return inside; }

    /** Bodies outside the circle still making their way to it at the last observation. */
    public int coming() { return coming; }

    /** Bodies outside the circle that have stopped getting closer, and so no longer hold the company. */
    public int stalled() { return stalled; }
}
