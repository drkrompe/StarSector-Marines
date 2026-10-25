package com.dillon.starsectormarines.battle.turret;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * The battle's hardpoint flux shields, by turret ({@code ground-turret-tiers.md}). A turret has one when its
 * structure defines a {@link FluxShieldSpec} and its look fits it ({@link StructureLook#shield}); the rules are
 * {@link FluxShield}'s, and this decides what threatens each gun and which rounds meet its arc.
 *
 * <ul>
 *   <li><b>Threat.</b> A gun is threatened while it has a target, toward that target, and for as long as it is
 *   being shot at, toward whoever shot it last: a flank shot swings the arc round.</li>
 *   <li><b>Bearing.</b> A round is judged by where it came from, its shooter's bearing from the gun. A burst
 *   credited to a shooter in front of the gun is stopped by a front arc wherever it landed; damage credited to
 *   no one (a crash, a fire) is never stopped.</li>
 *   <li><b>Silence.</b> An overloaded shield's gun holds fire until the overload is over, and an energy gun while
 *   it lets the capacitors it draws on bleed down ({@link FluxShield#gunHeld}).</li>
 * </ul>
 *
 * <p>Ticked, and its blocks decided, on the serial passes; a gun's own update reads {@link #silenced} and calls
 * {@link #noteRound} for itself.
 */
public final class TurretShieldService {

    /** Where an entity is, or {@code NaN} when it is nowhere this service can use. */
    public interface Positions {
        float x(long id);
        float y(long id);
    }

    /** What a gun is engaging this tick, or 0 for nothing. */
    public interface Targets {
        long targetOf(long id);
    }

    /** One fitted turret. */
    public static final class Fitted {
        public final long id;
        public final FluxShield shield;
        public final ShieldTint tint;
        float struckBearing = Float.NaN;
        float sinceStruck = Float.POSITIVE_INFINITY;

        Fitted(long id, FluxShield shield, ShieldTint tint) {
            this.id = id;
            this.shield = shield;
            this.tint = tint;
        }
    }

    private final Long2ObjectOpenHashMap<Fitted> byId = new Long2ObjectOpenHashMap<>();
    /** Registration order, so a tick visits shields the same way every run. */
    private final LongArrayList order = new LongArrayList();

    /** Fits turret {@code id} with a shield of {@code spec} drawn in {@code tint}, its arc facing {@code facing}. */
    public void fit(long id, FluxShieldSpec spec, ShieldTint tint, float facingDegrees) {
        if (!byId.containsKey(id)) order.add(id);
        byId.put(id, new Fitted(id, new FluxShield(spec, facingDegrees), tint));
    }

    /** Takes turret {@code id}'s shield away: it was destroyed, or rebuilt without one. */
    public void remove(long id) {
        if (byId.remove(id) != null) order.rem(id);
    }

    /** Turret {@code id}'s shield, or null when it has none. */
    public Fitted fitted(long id) {
        return byId.get(id);
    }

    /** How many turrets carry a shield. */
    public int size() {
        return order.size();
    }

    /** The {@code i}th fitted turret in registration order. */
    public Fitted at(int i) {
        return byId.get(order.getLong(i));
    }

    /** Advances every shield by {@code dt}, each facing what threatens its gun. */
    public void tick(float dt, Positions positions, Targets targets) {
        for (int i = 0, n = order.size(); i < n; i++) {
            Fitted f = byId.get(order.getLong(i));
            f.sinceStruck += dt;
            float threat = Float.NaN;
            long target = targets.targetOf(f.id);
            if (target != 0L) threat = bearing(positions, f.id, target);
            if (Float.isNaN(threat) && f.sinceStruck <= f.shield.spec().lingerSeconds()) threat = f.struckBearing;
            f.shield.tick(dt, threat);
        }
    }

    /**
     * Whether turret {@code target}'s shield stops a round of {@code damage} credited to {@code attacker}. Any
     * credited round, stopped or not, tells the gun where it is being shot from.
     */
    public boolean absorbs(long target, long attacker, float damage, Positions positions) {
        Fitted f = byId.get(target);
        if (f == null || attacker == 0L || attacker == target) return false;
        float bearing = bearing(positions, target, attacker);
        if (Float.isNaN(bearing)) return false;
        f.struckBearing = bearing;
        f.sinceStruck = 0f;
        return f.shield.absorb(bearing, damage);
    }

    /** Whether turret {@code id}'s gun is silent: its shield is overloaded, or its energy gun is letting flux bleed. */
    public boolean silenced(long id) {
        Fitted f = byId.get(id);
        return f != null && (f.shield.overloaded() || f.shield.gunHeld());
    }

    /** Turret {@code id} fired a round. */
    public void noteRound(long id) {
        Fitted f = byId.get(id);
        if (f != null) f.shield.noteRound();
    }

    /** Bearing in degrees from {@code from} to {@code to}, or {@code NaN} when either is nowhere. */
    static float bearing(Positions positions, long from, long to) {
        float dx = positions.x(to) - positions.x(from);
        float dy = positions.y(to) - positions.y(from);
        if (!Float.isFinite(dx) || !Float.isFinite(dy) || (dx == 0f && dy == 0f)) return Float.NaN;
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }
}
