package com.dillon.starsectormarines.battle.drone;

import java.util.ArrayList;
import java.util.List;

/**
 * The tethered drone flight of one drone skimmer: the hull it screens, the drones it has launched, and how many it may
 * still launch.
 *
 * <p>It is a {@link DroneAnchor} that is an {@linkplain #escort() escort}, so the shared swarm action flies its drones
 * in a loose formation round the hull, engages what comes into the hull's reach and is drawn back by the leash —
 * exactly as a tender's screen is flown, with no second drone model. What makes it a skimmer's is the tether: the
 * complement is fixed when the hull lands, a drone lost is lost for the battle, and when the hull stops being alive
 * the anchor is dead and {@link DroneSkimmerSystem} brings every drone still out down with it. The drones are remote
 * controlled from the hull rather than free hunters, which is the line the Sector's AI law draws.
 *
 * <p>Pose, roster and focus are published in the serial part of the tick and read from the parallel unit dispatch,
 * so every field the swarm action reads is volatile and replaced rather than mutated.
 */
public final class SkimmerWing implements DroneAnchor {

    /** How far from the hull a hostile counts as a threat to it, in cells. */
    public static final float REACH_CELLS = 24f;
    /** How far from the hull a drone will stray, in cells. */
    public static final float LEASH_CELLS = 18f;
    /** Seconds between two launches from the bay. */
    public static final float LAUNCH_INTERVAL = 1.2f;

    private final long hullId;
    private final int complement;
    private final float halfLength;
    private final List<Long> drones = new ArrayList<>();
    private int launched;
    private float launchClock = LAUNCH_INTERVAL;
    int squadId = -1;

    private volatile float x, y, heading;
    private volatile boolean alive = true;
    private volatile long focus;
    private volatile long[] ids = NO_IDS;
    private volatile float[] positions = NO_POSITIONS;

    public SkimmerWing(long hullId, int complement, float hullLengthCells) {
        if (complement < 0) throw new IllegalArgumentException("Negative drone complement: " + complement);
        this.hullId = hullId;
        this.complement = complement;
        this.halfLength = hullLengthCells * .5f;
    }

    public long hullId() { return hullId; }

    /** Drones this hull carried into battle. */
    public int complement() { return complement; }

    /** Drones still in the bay: never launched, so never lost. */
    public int inBay() { return complement - launched; }

    /** Drones out of the bay and still flying, in launch order. */
    public List<Long> drones() { return List.copyOf(drones); }

    /** Moves the anchor with its hull. */
    public void follow(float hullX, float hullY, float hullHeading) {
        x = hullX;
        y = hullY;
        heading = hullHeading;
    }

    /**
     * Advances the bay's launch clock and answers whether a drone may leave it this tick: one at a time,
     * {@link #LAUNCH_INTERVAL} apart, until the complement is spent. Never again after the hull is lost.
     */
    public boolean readyToLaunch(float dt) {
        if (!alive || launched >= complement) return false;
        launchClock += dt;
        return launchClock >= LAUNCH_INTERVAL;
    }

    /** Records a drone that left the bay. */
    public void launched(long droneId) {
        launched++;
        launchClock = 0f;
        drones.add(droneId);
    }

    /** Forgets a drone that is no longer flying. It does not go back in the bay. */
    public void lost(long droneId) {
        drones.remove(droneId);
    }

    /** The hull is wrecked or gone: the anchor dies, and nothing more launches. */
    public void hullLost() { alive = false; }

    /** Publishes the flight's roster, in slot order, with where each drone is now, and the threat the hull faces. */
    public void publish(long[] droneIds, float[] droneXy, long nearestThreat) {
        ids = droneIds;
        positions = droneXy;
        focus = nearestThreat;
    }

    @Override public float anchorX() { return x; }
    @Override public float anchorY() { return y; }
    @Override public boolean anchorAlive() { return alive; }
    @Override public float anchorHeadingDegrees() { return heading; }
    @Override public float anchorHalfLengthCells() { return halfLength; }
    @Override public boolean escort() { return true; }
    @Override public float leashCells() { return LEASH_CELLS; }
    @Override public boolean threatened() { return focus != 0L; }
    @Override public long focusTarget() { return focus; }
    @Override public long[] escortIds() { return ids; }
    @Override public float[] escortPositions() { return positions; }
}
