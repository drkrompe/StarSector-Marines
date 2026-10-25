package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirHandling;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tethered drone flight of one hull: the hull it works from, the roles its bay carries
 * ({@link DroneRole}), the drones it has out, and what is still in the bay.
 *
 * <p>It is a {@link DroneAnchor} that is an {@linkplain #escort() escort}, so the shared swarm action flies every
 * drone. What a drone does is decided here, by role: an escort flies the loose formation round the hull and engages
 * what comes into its reach; every other role is sent to a station on a {@linkplain #sortieGoal sortie}. A sensor
 * holds a point ahead of the hull, a halo drone orbits tight round it, and a strike drone flies into its target. The
 * complement is fixed when the hull lands, and a drone lost is lost for the battle, except the strike drone, which
 * the bay rebuilds. When the hull stops being alive, the anchor is dead and {@link DroneBaySystem} brings every drone
 * still out down with it. The drones are remote-controlled from the hull rather than free hunters, which is the line
 * the Sector's AI law draws.
 *
 * <p>Pose, roster, stations and focus are published in the serial part of the tick and read from the parallel unit
 * dispatch, so every field the swarm action reads is volatile and replaced rather than mutated.
 */
public final class DroneWing implements DroneAnchor {

    /** How far from the hull a hostile counts as a threat to it, in cells. */
    public static final float REACH_CELLS = 24f;
    /** How far from the hull a drone will stray, in cells. */
    public static final float LEASH_CELLS = 18f;
    /** Seconds between two launches from the bay. */
    public static final float LAUNCH_INTERVAL = 1.2f;
    /** How far ahead of the hull's bow the sensor pair holds station, in cells. */
    public static final float SENSOR_AHEAD_CELLS = 8f;
    /** How far either side of the hull's line the sensors of a pair hold station, in cells. */
    public static final float SENSOR_SPREAD_CELLS = 5f;
    /** How far outside the hull's own length the halo orbits, in cells. */
    public static final float HALO_STANDOFF_CELLS = 1.2f;
    /** How fast the halo turns round the hull, degrees per second. */
    public static final float HALO_DEG_PER_SEC = 45f;
    /** Seconds the bay takes to build a new strike drone after one is spent or shot down. */
    public static final float STRIKE_REBUILD_SECONDS = 30f;

    /** Sensors and halo drones have to keep up with a hover hull, so they fly faster than an escort. */
    static final AirHandling FAST_HANDLING = new AirHandling() {
        @Override public float maxSpeed()             { return 6f; }
        @Override public float accel()                { return 10f; }
        @Override public float brakingAccel()         { return 12f; }
        @Override public float maxTurnRateDegPerSec() { return 260f; }
        @Override public float lateralDriftDamping()  { return 6f; }
        @Override public float stationDamping()       { return 9f; }
    };

    /** A strike drone is a munition on its way in: fast, hard to turn away, and not built to hover. */
    static final AirHandling STRIKE_HANDLING = new AirHandling() {
        @Override public float maxSpeed()             { return 8f; }
        @Override public float accel()                { return 16f; }
        @Override public float brakingAccel()         { return 10f; }
        @Override public float maxTurnRateDegPerSec() { return 300f; }
        @Override public float lateralDriftDamping()  { return 8f; }
        @Override public float stationDamping()       { return 6f; }
    };

    private final long hullId;
    private final List<DroneRole> bay;
    private final float halfLength;
    private final Map<Long, DroneRole> roles = new LinkedHashMap<>();
    /** Index into {@link #bay} of the next role to launch; strike slots are skipped, they launch on a target. */
    private int nextSlot;
    private int strikesInBay;
    private int strikesBuilding;
    private float rebuildClock;
    private float launchClock = LAUNCH_INTERVAL;
    private long strikeTarget;
    private float strikeX, strikeY;
    int squadId = -1;

    private volatile float x, y, heading;
    private volatile boolean alive = true;
    private volatile long focus;
    private volatile long[] ids = NO_IDS;
    private volatile float[] positions = NO_POSITIONS;
    private volatile Map<Long, float[]> stations = Map.of();
    private volatile Map<Long, DroneRole> roleView = Map.of();

    public DroneWing(long hullId, List<DroneRole> bay, float hullLengthCells) {
        this.hullId = hullId;
        this.bay = List.copyOf(bay);
        this.halfLength = hullLengthCells * .5f;
        for (DroneRole role : this.bay) if (role == DroneRole.STRIKE) strikesInBay++;
        skipStrikeSlots();
    }

    public long hullId() { return hullId; }

    /** Drones this hull carried into battle. */
    public int complement() { return bay.size(); }

    /** Drones in the bay: never launched, or a strike drone rebuilt. */
    public int inBay() {
        int waiting = strikesInBay;
        for (int i = nextSlot; i < bay.size(); i++) if (bay.get(i) != DroneRole.STRIKE) waiting++;
        return waiting;
    }

    /** Strike drones ready in the bay. */
    public int strikesReady() { return strikesInBay; }

    /** Drones out of the bay and still flying, in launch order. */
    public List<Long> drones() { return List.copyOf(roles.keySet()); }

    /** The role a flying drone was launched in, or null for a drone this wing did not launch. */
    public DroneRole role(long droneId) { return roles.get(droneId); }

    /** Moves the anchor with its hull. */
    public void follow(float hullX, float hullY, float hullHeading) {
        x = hullX;
        y = hullY;
        heading = hullHeading;
    }

    /**
     * Advances the bay's clocks and answers which role may leave it this tick, or null. Escorts, sensors and halo
     * drones go one at a time, {@link #LAUNCH_INTERVAL} apart, in bay order; a strike drone goes only once they are
     * out and only at a target ({@code target}). Nothing launches after the hull is lost.
     */
    public DroneRole readyToLaunch(float dt, boolean target) {
        if (!alive) return null;
        if (strikesBuilding > 0) {
            rebuildClock -= dt;
            if (rebuildClock <= 0f) {
                strikesBuilding--;
                strikesInBay++;
                rebuildClock = strikesBuilding > 0 ? STRIKE_REBUILD_SECONDS : 0f;
            }
        }
        launchClock += dt;
        if (launchClock < LAUNCH_INTERVAL) return null;
        if (nextSlot < bay.size()) return bay.get(nextSlot);
        return strikesInBay > 0 && target ? DroneRole.STRIKE : null;
    }

    /** Records a drone that left the bay in {@code role}. */
    public void launched(long droneId, DroneRole role) {
        launchClock = 0f;
        roles.put(droneId, role);
        if (role == DroneRole.STRIKE) {
            strikesInBay--;
        } else {
            nextSlot++;
            skipStrikeSlots();
        }
        roleView = Map.copyOf(roles);
    }

    /** Forgets a drone that is no longer flying. It does not go back in the bay, but a strike drone is rebuilt. */
    public void lost(long droneId) {
        DroneRole role = roles.remove(droneId);
        if (role == null) return;
        if (role == DroneRole.STRIKE) {
            if (strikesBuilding++ == 0) rebuildClock = STRIKE_REBUILD_SECONDS;
            if (droneId == strikeDrone()) strikeTarget = 0L;
        }
        roleView = Map.copyOf(roles);
    }

    /** Seconds until the next strike drone is built, or zero when none is building. */
    public float rebuildRemaining() { return strikesBuilding > 0 ? Math.max(0f, rebuildClock) : 0f; }

    /** The hull is wrecked or gone: the anchor dies, and nothing more launches. */
    public void hullLost() { alive = false; }

    /** Points the strike drone at {@code target}, now at ({@code tx},{@code ty}); {@code 0L} sends it home. */
    public void aimStrike(long target, float tx, float ty) {
        strikeTarget = target;
        strikeX = tx;
        strikeY = ty;
    }

    /** What the strike drone is flying into, or {@code 0L}. */
    public long strikeTarget() { return strikeTarget; }

    /** The flying strike drone, or {@code 0L}. */
    public long strikeDrone() {
        for (Map.Entry<Long, DroneRole> e : roles.entrySet()) if (e.getValue() == DroneRole.STRIKE) return e.getKey();
        return 0L;
    }

    /**
     * Publishes the flight for the swarm action: the escorts' roster in slot order with where each is now, every
     * other drone's station at {@code seconds} on the simulation clock, and the threat the hull faces.
     */
    public void publish(long[] droneIds, float[] droneXy, long nearestThreat, float seconds) {
        publish(droneIds, droneXy, nearestThreat, seconds, Map.of());
    }

    /**
     * As {@link #publish(long[], float[], long, float)}, with the stations {@code sensorStations} gives the sensor
     * drones (a {@link SensorSearch} plan); a sensor with none holds the fixed station off the bow.
     */
    public void publish(long[] droneIds, float[] droneXy, long nearestThreat, float seconds,
                        Map<Long, float[]> sensorStations) {
        ArrayList<Long> escorts = new ArrayList<>();
        ArrayList<Float> escortXy = new ArrayList<>();
        int sensors = 0, halos = 0;
        for (DroneRole r : roles.values()) {
            if (r == DroneRole.SENSOR) sensors++;
            else if (r == DroneRole.HALO) halos++;
        }
        Map<Long, float[]> next = new LinkedHashMap<>();
        int sensor = 0, halo = 0;
        for (int i = 0; i < droneIds.length; i++) {
            DroneRole role = roles.get(droneIds[i]);
            if (role == null) continue;
            switch (role) {
                case ESCORT -> {
                    escorts.add(droneIds[i]);
                    escortXy.add(droneXy[2 * i]);
                    escortXy.add(droneXy[2 * i + 1]);
                }
                case SENSOR -> {
                    float[] planned = sensorStations.get(droneIds[i]);
                    next.put(droneIds[i], planned != null ? planned
                            : sensorStation(x, y, heading, halfLength, sensor, sensors));
                    sensor++;
                }
                case HALO -> next.put(droneIds[i], haloStation(x, y, halfLength, halo++, halos, seconds));
                case STRIKE -> next.put(droneIds[i], strikeTarget != 0L
                        ? new float[]{strikeX, strikeY} : sensorStation(x, y, heading + 180f, halfLength, 0, 1));
            }
        }
        long[] escortIds = new long[escorts.size()];
        float[] escortPositions = new float[escortXy.size()];
        for (int i = 0; i < escortIds.length; i++) escortIds[i] = escorts.get(i);
        for (int i = 0; i < escortPositions.length; i++) escortPositions[i] = escortXy.get(i);
        ids = escortIds;
        positions = escortPositions;
        stations = Map.copyOf(next);
        focus = nearestThreat;
    }

    /**
     * Where sensor {@code slot} of {@code count} holds station: {@link #SENSOR_AHEAD_CELLS} beyond the bow, spread
     * either side of the hull's line, turning with it.
     */
    static float[] sensorStation(float ax, float ay, float headingDeg, float halfLength, int slot, int count) {
        double h = Math.toRadians(headingDeg);
        float fx = (float) -Math.sin(h), fy = (float) Math.cos(h);
        float ahead = halfLength + SENSOR_AHEAD_CELLS;
        float side = count <= 1 ? 0f : SENSOR_SPREAD_CELLS * (2f * slot / (count - 1) - 1f);
        return new float[]{ax + fx * ahead + fy * side, ay + fy * ahead - fx * side};
    }

    /** Where halo drone {@code slot} of {@code count} is on its orbit round the hull at {@code seconds}. */
    static float[] haloStation(float ax, float ay, float halfLength, int slot, int count, float seconds) {
        double b = Math.toRadians(seconds * HALO_DEG_PER_SEC + 360f * slot / Math.max(1, count));
        float r = halfLength + HALO_STANDOFF_CELLS;
        return new float[]{ax + r * (float) -Math.sin(b), ay + r * (float) Math.cos(b)};
    }

    private void skipStrikeSlots() {
        while (nextSlot < bay.size() && bay.get(nextSlot) == DroneRole.STRIKE) nextSlot++;
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
    @Override public float[] sortieGoal(long member) { return stations.get(member); }

    @Override
    public AirHandling handling(long member) {
        DroneRole role = roleView.get(member);
        return role == null ? Drone.HANDLING : role.handling();
    }

    @Override
    public boolean mayFire(long member) {
        DroneRole role = roleView.get(member);
        return role == null || role.fires();
    }
}
