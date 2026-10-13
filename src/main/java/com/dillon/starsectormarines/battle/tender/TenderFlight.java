package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.drone.DroneAnchor;
import com.dillon.starsectormarines.battle.drone.DroneHash;
import com.dillon.starsectormarines.battle.drone.SortieFlight;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a tender's drones are doing between the bay and their task: leaving it,
 * being on task, or coming home.
 *
 * <p>Every drone out of the bay has a {@link Sortie} on one of three legs.
 * {@code LAUNCH}: it has just left the bay and is flying out to an exit point.
 * {@code TASK}: it is about its business (the escort ring, or a work station).
 * {@code RECOVER}: it has been recalled, stood down or is limping home, and flies
 * a short approach line to the bay, docking only on arrival. The goal a leg
 * implies is computed here once per tick, in the serial part of the tick, and
 * published as an immutable array, so the parallel unit dispatch reads a value
 * that is stable for the tick.
 *
 * <p>It also owns the escort's picture of the world, scanned a few times a
 * second rather than every tick: the hostile nearest the hull inside its reach,
 * and the roster of the wing in slot order. Nothing here is random; the little
 * variation drones show comes from {@link DroneHash}.
 */
final class TenderFlight {

    enum Leg { LAUNCH, TASK, RECOVER }

    /** One drone's trip out of the bay and, eventually, back. */
    static final class Sortie {
        final long id;
        final boolean armed;
        /** A defender's place in the wing, or a builder's work station; stable for the sortie. */
        int slot;
        Leg leg = Leg.LAUNCH;
        float legSeconds;
        /** Sim time before which a recalled drone keeps to its task, so a wave comes home in a line. */
        float releaseAt;
        /** Set once the drone has reached the approach point and turned in to the bay. */
        boolean approached;
        /** Home because it is badly hurt rather than because it was called; a recall does not turn it round. */
        boolean damaged;
        /** This drone's own phase in [0, 1), for a different exit bearing, formation breath and hover. */
        final float phase;
        /** Where the leg wants the drone this tick, or null when it is on task. Replaced, never mutated. */
        volatile float[] goal;

        Sortie(long id, boolean armed, int slot) {
            this.id = id;
            this.armed = armed;
            this.slot = slot;
            this.phase = DroneHash.unit(id, 1);
        }
    }

    /** How far from the hull a hostile counts as a threat to it, in cells. */
    static final float REACH_CELLS = 30f;
    /** How far from the hull the escort will stray, in cells. */
    static final float LEASH_CELLS = 24f;
    /** Ticks between scans of the roster for the nearest threat. */
    static final int SCAN_TICKS = 6;
    /** A defence drone below this share of its hit points limps home. */
    static final float RETREAT_FRACTION = 0.4f;
    /** Seconds a drone that came home hurt spends in the bay before it can launch again. */
    static final float REPAIR_SECONDS = 10f;
    /** A launching drone is on its task after this long even if it never reached the exit point. */
    static final float LAUNCH_TIMEOUT = 5f;
    /** The longest a homing drone leads a moving bay by, in seconds. */
    static final float MAX_LEAD_SECONDS = 3f;
    /** The speed a homing drone is assumed to close at when leading the bay, cells per second. */
    static final float DRONE_SPEED = 3.5f;
    /** A homing drone docks after this long even if it never closed on the bay. */
    static final float RECOVER_TIMEOUT = 40f;

    private final Map<Long, Sortie> sorties = new LinkedHashMap<>();
    private final List<Float> repairs = new ArrayList<>();
    private volatile long[] escortIds = DroneAnchor.NO_IDS;
    private volatile float[] escortPositions = DroneAnchor.NO_POSITIONS;
    private volatile long focus;
    private volatile boolean threatened;
    private int scanCountdown;
    private float lastBayX = Float.NaN;
    private float lastBayY;
    private float bayVx;
    private float bayVy;

    Sortie begin(long id, boolean armed, int slot) {
        Sortie s = new Sortie(id, armed, slot);
        sorties.put(id, s);
        return s;
    }

    Sortie of(long id) {
        return sorties.get(id);
    }

    void forget(long id) {
        sorties.remove(id);
    }

    float[] goalOf(long id) {
        Sortie s = sorties.get(id);
        return s == null ? null : s.goal;
    }

    long focus() {
        return focus;
    }

    boolean threatened() {
        return threatened;
    }

    long[] escortIds() {
        return escortIds;
    }

    float[] escortPositions() {
        return escortPositions;
    }

    /** Publishes the wing's roster, in slot order, with where each drone is now. */
    void publishEscort(List<Long> defenders, World world) {
        long[] ids = new long[defenders.size()];
        float[] at = new float[ids.length * 2];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = defenders.get(i);
            at[2 * i] = world.x(ids[i]);
            at[2 * i + 1] = world.y(ids[i]);
        }
        escortIds = ids;
        escortPositions = at;
    }

    /** The lowest work station no live builder holds. */
    int freeStation(List<Long> builders, int stations) {
        for (int slot = 0; slot < stations; slot++) {
            boolean taken = false;
            for (Long id : builders) {
                Sortie s = sorties.get(id);
                if (s != null && s.slot == slot) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return slot;
        }
        return stations;
    }

    /** Notes where the bay is this tick, to know how fast it is moving; call once a tick before stepping drones. */
    void trackBay(float bayX, float bayY, float dt) {
        if (!Float.isNaN(lastBayX) && dt > 0f) {
            bayVx = (bayX - lastBayX) / dt;
            bayVy = (bayY - lastBayY) / dt;
        }
        lastBayX = bayX;
        lastBayY = bayY;
    }

    /** Where a point on the moving bay will be when a drone at ({@code fromX, fromY}) arrives: a short lead, never more than {@link #MAX_LEAD_SECONDS}. */
    private float[] lead(float px, float py, float fromX, float fromY) {
        float seconds = Math.min(MAX_LEAD_SECONDS, (float) Math.hypot(px - fromX, py - fromY) / DRONE_SPEED);
        return new float[]{px + bayVx * seconds, py + bayVy * seconds};
    }

    // ---- legs --------------------------------------------------------------------------------

    /** Sends a drone home. {@code releaseAt} is the sim time it may start; {@code damaged} marks a retreat. */
    void recall(Sortie s, float releaseAt, boolean damaged) {
        if (s.leg == Leg.RECOVER) return;
        s.leg = Leg.RECOVER;
        s.legSeconds = 0f;
        s.releaseAt = releaseAt;
        s.approached = false;
        s.damaged = damaged;
    }

    /** Puts a drone that was on its way home back on task (the recall was withdrawn). */
    void resume(Sortie s) {
        if (s.leg != Leg.RECOVER || s.damaged) return;
        s.leg = Leg.TASK;
        s.legSeconds = 0f;
        s.goal = null;
    }

    /**
     * Advances one drone's leg and publishes its goal. Returns true when the drone
     * has arrived in the bay and should be taken off the field.
     */
    boolean step(Sortie s, float bodyX, float bodyY, float bayX, float bayY, float mouthDeg, float now, float dt) {
        s.legSeconds += dt;
        switch (s.leg) {
            case LAUNCH -> {
                float[] exit = SortieFlight.exitPoint(bayX, bayY, mouthDeg, s.phase);
                float d = (float) Math.hypot(bodyX - exit[0], bodyY - exit[1]);
                if (d <= SortieFlight.REACHED_CELLS || s.legSeconds > LAUNCH_TIMEOUT) {
                    s.leg = Leg.TASK;
                    s.legSeconds = 0f;
                    s.goal = null;
                } else {
                    s.goal = exit;
                }
                return false;
            }
            case RECOVER -> {
                if (now < s.releaseAt) {
                    s.goal = null;
                    return false;
                }
                float dBay = (float) Math.hypot(bodyX - bayX, bodyY - bayY);
                if (dBay <= SortieFlight.DOCK_CELLS || s.legSeconds > RECOVER_TIMEOUT) return true;
                if (!s.approached) {
                    float[] approach = SortieFlight.approachPoint(bayX, bayY, mouthDeg);
                    if (Math.hypot(bodyX - approach[0], bodyY - approach[1]) <= SortieFlight.REACHED_CELLS) {
                        s.approached = true;
                    } else {
                        s.goal = lead(approach[0], approach[1], bodyX, bodyY);
                        return false;
                    }
                }
                s.goal = lead(bayX, bayY, bodyX, bodyY);
                return false;
            }
            default -> {
                s.goal = null;
                return false;
            }
        }
    }

    // ---- repair ------------------------------------------------------------------------------

    /** Starts the clock on a drone that came home hurt. */
    void repairing(float now) {
        repairs.add(now + REPAIR_SECONDS);
    }

    /** How many drones in the bay are still being repaired at {@code now}. */
    int underRepair(float now) {
        repairs.removeIf(ready -> ready <= now);
        return repairs.size();
    }

    // ---- threat ------------------------------------------------------------------------------

    /**
     * Looks, a few times a second, for the hostile nearest the hull inside its
     * reach. That one is the escort's focus and its presence draws the ring in.
     */
    void scan(BattleSimulation sim, Faction side, float ax, float ay) {
        if (scanCountdown-- > 0) return;
        scanCountdown = SCAN_TICKS - 1;
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        long best = 0L;
        float bestD2 = REACH_CELLS * REACH_CELLS;
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            if (!side.hostileTo(roster.identity().faction(u))) continue;
            float dx = world.x(u) - ax;
            float dy = world.y(u) - ay;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = u;
            }
        }
        focus = best;
        threatened = best != 0L;
    }
}
