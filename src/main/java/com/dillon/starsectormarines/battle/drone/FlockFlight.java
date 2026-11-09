package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirHandling;
import com.dillon.starsectormarines.battle.air.AirSteeringSystem;
import com.dillon.starsectormarines.battle.air.SteeringMode;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.TurretAim;

/**
 * How one member of a {@link Flock} flies a tick: it finds its own way to the anchor on the flock's field
 * ({@link FlockField}), steers by flocking with its mates ({@link FlockSteering}), keeps out of walls and roofed
 * rooms ({@link #slide}), and fires when its nose comes on to a target in its short reach (the shipped
 * {@link TurretAim} fire-arc gate, so a strafing pass is the nose sweeping through the target).
 *
 * <p>On a strike each member's way leads to its orbit slot round the target instead of to the anchor
 * ({@link #orbitSlot}): slots evenly spaced, turning, so the flock wraps the target rather than stacking on the side it
 * came from. A member far from the target still comes in on the field, by its own route.
 *
 * <p>Runs in the parallel unit dispatch: it reads the flock's published roster and field and writes only its own body.
 */
public final class FlockFlight {

    /** A swarm drone is light and quick: faster than a hub drone and turning harder, so a flock darts. */
    public static final AirHandling HANDLING = new AirHandling() {
        @Override public float maxSpeed()             { return 6f; }
        @Override public float accel()                { return 14f; }
        @Override public float brakingAccel()         { return 16f; }
        @Override public float maxTurnRateDegPerSec() { return 360f; }
        @Override public float lateralDriftDamping()  { return 7f; }
        @Override public float stationDamping()       { return 9f; }
    };

    /** How far ahead along its asked-for velocity a member aims, seconds. */
    static final float LOOKAHEAD_SECONDS = 0.6f;
    /** The strike orbit's radius, cells: inside the swarm gun's short reach. */
    public static final float ORBIT_CELLS = 6f;
    /** How fast the strike slots turn round the target, degrees per second. */
    public static final float ORBIT_DEG_PER_SEC = 40f;
    /** Within this far of its slot past the orbit a member flies straight at the slot instead of down the field. */
    static final float ORBIT_CAPTURE_CELLS = 5f;

    private FlockFlight() {}

    static ActionStatus execute(long member, Squad squad, Flock flock, AirBody body, BattleControl sim) {
        float dt = BattleSimulation.TICK_DT;
        float prevX = body.x, prevY = body.y;

        long[] ids = flock.escortIds();
        float[] at = flock.escortPositions();
        float[] vel = flock.escortVelocities();
        int n = Math.min(ids.length, Math.min(at.length, vel.length) / 2);
        int self = -1;
        for (int i = 0; i < n; i++) if (ids[i] == member) self = i;

        float[] way = way(member, self, n, body, flock, sim);
        float[] v;
        if (self >= 0) {
            float[] xs = new float[n], ys = new float[n], vxs = new float[n], vys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = at[2 * i];
                ys[i] = at[2 * i + 1];
                vxs[i] = vel[2 * i];
                vys[i] = vel[2 * i + 1];
            }
            // The member's own live state, not the snapshot's: its mates are read as published.
            xs[self] = body.x;
            ys[self] = body.y;
            vxs[self] = body.vx;
            vys[self] = body.vy;
            v = FlockSteering.velocity(self, ids, xs, ys, vxs, vys, n, way[0], way[1], way[2], flock.weights());
        } else {
            float speed = flock.weights().maxSpeed() * Math.min(1f, way[2] / flock.weights().arriveCells());
            v = new float[]{way[0] * speed, way[1] * speed};
        }
        AirSteeringSystem.steer(body, body.x + v[0] * LOOKAHEAD_SECONDS, body.y + v[1] * LOOKAHEAD_SECONDS,
                SteeringMode.BRAKE_TO_STATION, HANDLING, dt);

        FlockField.Open open = FlockSystem.open(sim.getGrid(), sim.getTopology());
        float[] slid = slide(prevX, prevY, body.x, body.y, open);
        if (slid[0] != body.x) body.vx = 0f;
        if (slid[1] != body.y) body.vy = 0f;
        body.x = slid[0];
        body.y = slid[1];
        sim.world().setPos(member, body.x, body.y);

        aimAndFire(member, body, flock, sim);
        return ActionStatus.RUNNING;
    }

    /** The member's way as {@code {dx, dy, cells}}: to its strike slot, else down the field, else straight. */
    private static float[] way(long member, int self, int n, AirBody body, Flock flock, BattleControl sim) {
        long target = flock.strikeTarget();
        if (target != 0L && sim.world().isAlive(target)) {
            float tx = sim.world().x(target), ty = sim.world().y(target);
            float seconds = sim.getSimTickIndex() * BattleSimulation.TICK_DT;
            float[] slot = openSlot(tx, ty, Math.max(0, self), Math.max(1, n), seconds,
                    FlockSystem.open(sim.getGrid(), sim.getTopology()));
            float dx = slot[0] - body.x, dy = slot[1] - body.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float fromTarget = (float) Math.hypot(body.x - tx, body.y - ty);
            if (fromTarget <= ORBIT_CELLS + 2f * ORBIT_CAPTURE_CELLS && d > 1e-3f) {
                return new float[]{dx / d, dy / d, d};
            }
        }
        FlockField field = flock.field();
        float[] down = field == null ? null : field.way(body.x, body.y, flock.anchorX(), flock.anchorY());
        if (down != null) return down;
        float dx = flock.anchorX() - body.x, dy = flock.anchorY() - body.y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-3f) return new float[]{0f, 0f, 0f};
        return new float[]{dx / d, dy / d, d};
    }

    /**
     * Slot {@code k} of {@code n} on the strike orbit round {@code (tx, ty)} at battle time {@code seconds}: evenly
     * spaced bearings, the whole ring turning at {@link #ORBIT_DEG_PER_SEC}, so each member's nose sweeps the target
     * as it flies its slot.
     */
    static float[] orbitSlot(float tx, float ty, int k, int n, float seconds) {
        double bearing = Math.toRadians(360.0 * k / n + seconds * ORBIT_DEG_PER_SEC);
        return new float[]{tx + ORBIT_CELLS * (float) Math.cos(bearing), ty + ORBIT_CELLS * (float) Math.sin(bearing)};
    }

    /**
     * {@link #orbitSlot} pushed out along its bearing to the first open cell, up to {@link #ORBIT_CAPTURE_CELLS}
     * further: a target in a roofed room is wrapped from the open ground round its walls.
     */
    static float[] openSlot(float tx, float ty, int k, int n, float seconds, FlockField.Open open) {
        float[] slot = orbitSlot(tx, ty, k, n, seconds);
        float ux = (slot[0] - tx) / ORBIT_CELLS, uy = (slot[1] - ty) / ORBIT_CELLS;
        for (float r = ORBIT_CELLS; r <= ORBIT_CELLS + ORBIT_CAPTURE_CELLS; r += .5f) {
            float x = tx + ux * r, y = ty + uy * r;
            if (open.open((int) Math.floor(x), (int) Math.floor(y))) return new float[]{x, y};
        }
        return slot;
    }

    /**
     * Where a member moving from {@code (px, py)} to {@code (x, y)} may end up without entering a closed cell: the
     * move as asked when its cell is open, otherwise slid along whichever axis stays open, otherwise held where it
     * was. A member already in a closed cell (launched there, or a roof fell back) is let out rather than held.
     */
    static float[] slide(float px, float py, float x, float y, FlockField.Open open) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (open.open(cx, cy)) return new float[]{x, y};
        int pcx = (int) Math.floor(px), pcy = (int) Math.floor(py);
        if (!open.open(pcx, pcy)) return new float[]{x, y};
        if (open.open(cx, pcy)) return new float[]{x, py};
        if (open.open(pcx, cy)) return new float[]{px, y};
        return new float[]{px, py};
    }

    /** Acquire and fire as a turret on the drone's nose: it shoots only when its flight brings the nose on. */
    private static void aimAndFire(long member, AirBody body, Flock flock, BattleControl sim) {
        TurretAim.State s = new TurretAim.State();
        s.originCellX = sim.world().cellX(member);
        s.originCellY = sim.world().cellY(member);
        s.originX = body.x;
        s.originY = body.y;
        s.faction = sim.identity().faction(member);
        s.squadId = sim.squad().hasSquad(member) ? sim.squad().squadId(member) : Squad.NO_SQUAD;
        s.excludeFromCrowding = member;
        s.facingDegrees = body.facingDegrees;
        // The gun is fixed: the nose does the aiming, so the aim never turns ahead of the airframe.
        s.turnRateDegPerSec = 0f;
        s.attackRange = sim.world().attackRange(member);
        s.minRange = 0f;
        s.cooldownTimer = sim.world().cooldownTimer(member);
        s.attackCooldown = sim.combat().attackCooldown(member);
        s.target = sim.targetOf(member);
        long strike = flock.focusTarget();
        if (s.target == 0L && strike != 0L && sim.world().isAlive(strike)) s.target = strike;
        s.ignoreCloseWalls = true;
        s.closeWallRadius = sim.vision().airLosRadius(member);
        TurretAim.tick(s, sim.getTacticalScoring(), sim.getGrid(), sim.world(), sim.vision(), BattleSimulation.TICK_DT);
        sim.world().setCooldownTimer(member, s.cooldownTimer);
        sim.world().setTargetId(member, s.target);
        if (s.fireThisTick && s.target != 0L) {
            sim.fireShot(member, s.target, FireStance.STANCED);
            sim.combat().beginBurst(member, s.target);
        }
    }
}
