package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirHandling;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * A flock: a set of swarm drones that fly as one body, and the <b>anchor</b> its commander moves for it
 * (`air-nouns.md`, Flocks). The commander gives the flock one {@link FlockTask} with its point, route or target;
 * {@link FlockSystem} moves the anchor for that task and each member steers to it by flocking ({@link FlockFlight}).
 * No member is ever ordered on its own.
 *
 * <p>It is the drone squad's {@link DroneAnchor}, the seam the hub's and the tender's drones already fly to, so its
 * members are ordinary drones on the shared swarm action. The anchor's home and leash are the network's: the
 * flock never takes its anchor further than {@link #leashCells()} from {@link #homeX() home} except on a strike,
 * which the commander orders knowing where the target is.
 *
 * <p>Task, route and target are set in the serial part of the tick; anchor, field and roster are published there
 * and read from the parallel unit dispatch, so those fields are volatile and replaced rather than mutated.
 */
public final class Flock implements DroneAnchor {

    private final int id;
    private final Faction side;
    private final FlockWeights weights;
    private final float homeX, homeY, leash;
    final List<Long> members = new ArrayList<>();
    private int strength;
    int squadId = -1;
    /** The cradle at the flock's home, or {@code 0L} when it has none. */
    long cradleId;
    FlockLivery livery = FlockLivery.DOMAIN;
    final FlockCradle.Clock rebuild = new FlockCradle.Clock();
    final FlockDock dock = new FlockDock();
    private volatile boolean nodeStands = true;
    /** Tick the field was last built, for the rebuild cadence. */
    int fieldTick = Integer.MIN_VALUE / 2;

    private FlockTask task = FlockTask.SCREEN;
    private float[] route = new float[0];
    private int routeIndex;
    private float stationX, stationY;
    private long target;

    private volatile float x, y;
    private volatile long strikeTarget;
    private volatile long focus;
    private volatile int[] contactCells = new int[0];
    private volatile int contactTick = -1;
    /** The roofed target's cell and threshold last found, so a target standing still is not searched again. */
    int thresholdForX = Integer.MIN_VALUE, thresholdForY = Integer.MIN_VALUE;
    int[] threshold;
    private volatile FlockField field;
    private volatile long[] ids = NO_IDS;
    private volatile float[] positions = NO_POSITIONS;
    private volatile float[] velocities = NO_POSITIONS;
    private volatile long[] contacts = NO_IDS;
    private volatile boolean alive = true;

    Flock(int id, Faction side, FlockWeights weights, float homeX, float homeY, float leashCells) {
        this.id = id;
        this.side = side;
        this.weights = weights;
        this.homeX = homeX;
        this.homeY = homeY;
        this.leash = leashCells;
        this.x = homeX;
        this.y = homeY;
        this.stationX = homeX;
        this.stationY = homeY;
    }

    public int id() { return id; }

    public Faction side() { return side; }

    public FlockWeights weights() { return weights; }

    public float homeX() { return homeX; }

    public float homeY() { return homeY; }

    public FlockTask task() { return task; }

    /** The cradle's entity id, or {@code 0L} for a flock put up without one. */
    public long cradle() { return cradleId; }

    /**
     * Whether the control node this flock's cradle answers to stands; the network sets it. A cradle whose node is
     * down rebuilds nothing. Standing until told otherwise.
     */
    public void nodeStands(boolean stands) { nodeStands = stands; }

    public boolean nodeStands() { return nodeStands; }

    /** Members flying now. */
    public int size() { return members.size(); }

    /** Drones docked aboard the cradle, out of play. */
    public int aboard() { return dock.aboard(); }

    /** Whether the whole flock is docked: nothing flying and drones aboard. */
    public boolean docked() { return members.isEmpty() && dock.aboard() > 0; }

    /** Members the flock was made with: its strength when whole. */
    public int strength() { return strength; }

    /** The flying members, in the order they joined. */
    public List<Long> memberIds() { return List.copyOf(members); }

    /** Hostiles any member saw on the last scan: what the flock tells its commander. */
    public long[] contacts() { return contacts; }

    /** Where each of {@link #contacts()} was when seen, as {@code x0, y0, x1, y1, ...} cells. */
    public int[] contactCells() { return contactCells; }

    /** The tick of the scan {@link #contacts()} came from, or -1 before the first. */
    public int contactTick() { return contactTick; }

    /** Whether the strike is held at a threshold: its target is under a roof the flock never goes under. */
    public boolean holdingThreshold() { return target != 0L && strikeTarget == 0L && task == FlockTask.STRIKE; }

    /** The field the members descend to the anchor, or null before the first is built. */
    public FlockField field() { return field; }

    // ---- the commander's tasks ----

    /** Hold a station at {@code (sx, sy)}. */
    public void screen(float sx, float sy) {
        task = FlockTask.SCREEN;
        stationX = sx;
        stationY = sy;
        target = 0L;
    }

    /** Fly the route {@code x0, y0, x1, y1, ...} and hold its last point. */
    public void probe(float... points) {
        if (points.length < 2 || points.length % 2 != 0) throw new IllegalArgumentException("route is x, y pairs");
        task = FlockTask.PROBE;
        route = points.clone();
        routeIndex = 0;
        target = 0L;
    }

    /** Close on {@code unit}, wrap it and strafe it in passes. */
    public void strike(long unit) {
        task = FlockTask.STRIKE;
        target = unit;
    }

    /** Follow {@code unit} as far as the leash allows. */
    public void pursue(long unit) {
        task = FlockTask.PURSUE;
        target = unit;
    }

    /** Go home. */
    public void returnHome() {
        task = FlockTask.RETURN;
        target = 0L;
    }

    /** The unit the flock is striking or pursuing, or {@code 0L}. */
    public long target() { return target; }

    // ---- the system's part (serial) ----

    void joined(long member) {
        members.add(member);
        strength = Math.max(strength, members.size());
    }

    /** Where the task wants the anchor now, given the target's position when there is one. */
    float[] goal(float targetX, float targetY) {
        switch (task) {
            case PROBE: {
                float gx = route[2 * routeIndex], gy = route[2 * routeIndex + 1];
                return new float[]{gx, gy};
            }
            case STRIKE:
                return new float[]{targetX, targetY};
            case PURSUE:
                return leashed(targetX, targetY);
            case RETURN:
                return new float[]{homeX, homeY};
            case SCREEN:
            default:
                return leashed(stationX, stationY);
        }
    }

    /** A probe whose anchor has reached its current point goes on to the next; the last is held. */
    void arrived() {
        if (task == FlockTask.PROBE && routeIndex < route.length / 2 - 1) routeIndex++;
    }

    void moveAnchor(float ax, float ay) {
        x = ax;
        y = ay;
    }

    void publish(long[] ids, float[] positions, float[] velocities, FlockField field, long strikeTarget, long focus) {
        this.focus = focus;
        this.ids = ids;
        this.positions = positions;
        this.velocities = velocities;
        this.field = field;
        this.strikeTarget = strikeTarget;
    }

    void publishContacts(long[] seen, int[] cells, int tick) {
        contacts = seen;
        contactCells = cells;
        contactTick = tick;
    }

    void disband() {
        alive = false;
    }

    private float[] leashed(float gx, float gy) {
        float dx = gx - homeX, dy = gy - homeY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d <= leash) return new float[]{gx, gy};
        return new float[]{homeX + dx * leash / d, homeY + dy * leash / d};
    }

    // ---- what the members read (parallel) ----

    @Override public float anchorX() { return x; }

    @Override public float anchorY() { return y; }

    @Override public boolean anchorAlive() { return alive; }

    @Override public float leashCells() { return leash; }

    @Override public long[] escortIds() { return ids; }

    @Override public float[] escortPositions() { return positions; }

    /** The members' velocities when the roster was published, as {@code vx0, vy0, vx1, vy1, ...}. */
    public float[] escortVelocities() { return velocities; }

    /** The unit the members wrap, or {@code 0L} when the flock is not striking or holds its threshold. */
    public long strikeTarget() { return strikeTarget; }

    /** The unit the members shoot at when they see it: the strike or pursuit target, or {@code 0L}. */
    @Override public long focusTarget() { return focus; }

    @Override public Flock flock() { return this; }

    @Override public AirHandling handling(long member) { return FlockFlight.HANDLING; }

    /** The lineage this flock was built by, which decides how its drones and cradle are drawn. */
    public FlockLivery livery() { return livery; }
}
