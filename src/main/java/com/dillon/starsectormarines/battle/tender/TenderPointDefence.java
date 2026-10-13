package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.Interceptable;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.warship.VanillaWeapon;
import com.dillon.starsectormarines.battle.warship.VanillaWeapons;
import com.dillon.starsectormarines.battle.warship.WarshipPointDefence;
import com.dillon.starsectormarines.battle.warship.WarshipScale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tender's point defence: its defence drones, each burning incoming ordnance with
 * the mining laser the Borer carries in the game.
 *
 * <p>The tender has no guns of its own, so this is the only thing between it and a
 * hostile salvo, and it is deliberately weak. Everything a drone does is read from
 * the laser's own row in the weapon table rather than authored here: its reach, its
 * damage per second and the rate its mount turns.
 * <ul>
 *   <li><b>Reach</b> is the weapon's range, converted by {@link WarshipScale}, from
 *       where the drone <em>is</em>, so a drone out on the flank covers the ground it
 *       overflies and not only the hull.</li>
 *   <li><b>Strength</b> is a dwell. A beam is not a die roll: it burns for as long as it
 *       holds the ordnance, and the body falls when the damage it has taken reaches its
 *       hit points. A 60-hit-point missile costs one drone two seconds, a
 *       torpedo is out of reach of the whole wing, and a salvo bigger than the wing can
 *       burn through in the time it crosses saturates it and lands.</li>
 *   <li><b>Tracking</b> is the mount's turn rate. The beam slews toward the bearing of
 *       what it has chosen, deals damage only while it is on that bearing, and a body
 *       crossing faster than the mount can follow is simply not burnt.</li>
 * </ul>
 *
 * <p>Whom it protects: the drone engages what is bound for the hull before anything
 * else, then whatever else crosses its reach, soonest landing first, the whole wing on
 * the most urgent body it can reach. Only a drone that is out, alive and on station
 * fires: one that is docked is not on the field, one chasing a raider has other work,
 * and one that has been shot down is gone. The tracer is drawn through the warship
 * point-defence marks, so it is the beam the player knows.
 *
 * <p>Interception is not damage: a destroyed body never lands.
 */
public final class TenderPointDefence {

    /** The weapon the Borer drone carries (the {@code drone_borer} variant's only mount). */
    public static final String WEAPON_ID = "mininglaser";
    /** Cells beyond the hull's half length within which ordnance counts as bound for the tender. */
    static final float COVER_MARGIN_CELLS = 4f;
    /** A beam is on its target within this many degrees of the bearing. */
    static final float TOLERANCE_DEGREES = WarshipPointDefence.ALIGNED_DEGREES;
    /** Seconds between two tracers drawn while a beam holds. */
    static final float MARK_INTERVAL_SECONDS = 0.1f;

    /** One body that point defence could shoot at, wherever it comes from. */
    public record Candidate(Object source, float x, float y, float landingX, float landingY,
                            float hitpoints, float speedCells) {
    }

    private final Map<Long, Float> aim = new HashMap<>();
    private final Map<Long, Object> lock = new HashMap<>();
    private final Map<Long, Float> markClock = new HashMap<>();
    private final Map<Object, Float> burn = new IdentityHashMap<>();
    private int engagements;
    private int intercepts;
    private float beamSeconds;

    /** Targets the wing has started burning, for evidence. */
    public int engagements() {
        return engagements;
    }

    /** Bodies the wing has destroyed, for evidence. */
    public int intercepts() {
        return intercepts;
    }

    /** Drone-seconds a beam has held on target, for evidence. */
    public float beamSeconds() {
        return beamSeconds;
    }

    // ---- the rules, from the weapon's own figures -----------------------------------------------

    /** How far the laser reaches, in cells. */
    public static float reachCells(VanillaWeapon weapon) {
        return WarshipScale.cells(weapon.rangePx());
    }

    /** What a beam deals per second held on target, in the game's hit points. */
    public static float damagePerSecond(VanillaWeapon weapon) {
        return weapon.damagePerSecond();
    }

    /** How fast the mount turns, in degrees per second. */
    public static float turnRate(VanillaWeapon weapon) {
        return weapon.turnRateDegPerSec();
    }

    /** Seconds a beam must hold to destroy a body of {@code hitpoints}; infinite for a weapon that does no damage. */
    public static float secondsToDestroy(float hitpoints, VanillaWeapon weapon) {
        float dps = damagePerSecond(weapon);
        return dps <= 0f ? Float.POSITIVE_INFINITY : Math.max(0f, hitpoints) / dps;
    }

    /** Whether a drone fires at all: it is out of the bay, alive, and not away chasing a raider. */
    public static boolean onStation(boolean out, boolean alive, boolean pursuing) {
        return out && alive && !pursuing;
    }

    /** Whether ordnance is bound for the tender: it will land within {@code coverRadius} of the hull. */
    public static boolean boundForTender(Candidate c, float tenderX, float tenderY, float coverRadius) {
        return Math.hypot(c.landingX() - tenderX, c.landingY() - tenderY) <= coverRadius;
    }

    /** Moves {@code current} toward {@code bearing} by at most {@code maxStep} degrees, the short way round. */
    public static float slew(float current, float bearing, float maxStep) {
        float diff = wrap(bearing - current);
        if (Math.abs(diff) <= maxStep) return bearing;
        return wrap(current + Math.signum(diff) * maxStep);
    }

    /** Whether a beam pointing {@code aim} is on a target at {@code bearing}. */
    public static boolean onTarget(float aim, float bearing) {
        return Math.abs(wrap(bearing - aim)) <= TOLERANCE_DEGREES;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /** Seconds before the body lands at the speed it crosses now. */
    public static float secondsToLand(Candidate c) {
        return (float) Math.hypot(c.x() - c.landingX(), c.y() - c.landingY()) / Math.max(0.1f, c.speedCells());
    }

    /**
     * The body a drone at {@code (droneX, droneY)} burns: among those inside its {@code reach},
     * ordnance bound for the tender first, and within each group the one that lands soonest.
     * Wing-mates do not spread: a mining laser is slow, so the wing's best use of a short
     * window is to put every beam it can on the most urgent body and burn it down together,
     * and a drone for which that body is out of reach takes the next.
     */
    public static Candidate choose(List<Candidate> pool, float droneX, float droneY, float tenderX, float tenderY,
                                   float coverRadius, float reach) {
        Candidate best = null;
        boolean bestBound = false;
        float bestSeconds = Float.MAX_VALUE;
        float reachSq = reach * reach;
        for (Candidate c : pool) {
            float dx = c.x() - droneX;
            float dy = c.y() - droneY;
            if (dx * dx + dy * dy > reachSq) continue;
            boolean bound = boundForTender(c, tenderX, tenderY, coverRadius);
            float seconds = secondsToLand(c);
            if (best != null && (bestBound && !bound || bestBound == bound && seconds >= bestSeconds)) continue;
            best = c;
            bestBound = bound;
            bestSeconds = seconds;
        }
        return best;
    }

    // ---- the tick -------------------------------------------------------------------------------

    /** One tick of the wing's point defence. Serial: it destroys bodies the ground battle is tracking. */
    void tick(float dt, BattleSimulation sim, Tender tender) {
        VanillaWeapon weapon = VanillaWeapons.get(WEAPON_ID);
        List<Long> wing = new ArrayList<>(tender.defenders);
        lock.keySet().retainAll(wing);
        aim.keySet().retainAll(wing);
        markClock.keySet().retainAll(wing);
        if (weapon == null || wing.isEmpty()) {
            lock.clear();
            burn.clear();
            return;
        }
        List<Candidate> pool = gather(sim, tender);
        burn.keySet().removeIf(body -> pool.stream().noneMatch(c -> c.source() == body));
        float reach = reachCells(weapon);
        float cover = tender.ship().lengthCells * 0.5f + COVER_MARGIN_CELLS;
        for (Long id : wing) {
            boolean alive = sim.resolveUnit(id) != 0L;
            boolean pursuing = alive && sim.droneState().isDrone(id) && sim.droneState().pursuitTimer(id) > 0f;
            if (!onStation(true, alive, pursuing)) {
                lock.remove(id);
                continue;
            }
            float dx = sim.world().x(id);
            float dy = sim.world().y(id);
            Candidate target = choose(pool, dx, dy, tender.anchorX(), tender.anchorY(), cover, reach);
            if (target == null) {
                lock.remove(id);
                continue;
            }
            if (lock.put(id, target.source()) != target.source()) engagements++;
            float bearing = AirBody.facingToward(target.x() - dx, target.y() - dy);
            float heading = slew(aim.getOrDefault(id, bearing), bearing, turnRate(weapon) * dt);
            aim.put(id, heading);
            if (!onTarget(heading, bearing)) continue;
            beamSeconds += dt;
            float taken = burn.merge(target.source(), damagePerSecond(weapon) * dt, Float::sum);
            boolean destroyed = taken >= target.hitpoints();
            float clock = markClock.getOrDefault(id, 0f) - dt;
            if (clock <= 0f || destroyed) {
                sim.warships().pointDefence().trace(tender.faction(), weapon, dx, dy, target.x(), target.y(), destroyed);
                clock = MARK_INTERVAL_SECONDS;
            }
            markClock.put(id, clock);
            if (!destroyed) continue;
            destroy(target.source());
            intercepts++;
            burn.remove(target.source());
            pool.remove(target);
            for (Long other : wing) if (lock.get(other) == target.source()) lock.remove(other);
        }
    }

    private static void destroy(Object source) {
        if (source instanceof Interceptable body) body.intercept();
        else if (source instanceof Projectile round) round.intercepted = true;
    }

    /** Every hostile body in flight that point defence may shoot at: warship ordnance and opted-in ground rounds. */
    private static List<Candidate> gather(BattleSimulation sim, Tender tender) {
        List<Candidate> pool = new ArrayList<>();
        for (Interceptable b : sim.interceptableOrdnance()) {
            if (b.isIntercepted() || !tender.faction().hostileTo(b.faction())) continue;
            pool.add(new Candidate(b, b.x(), b.y(), b.landingX(), b.landingY(), b.hitpoints(), b.speedCells()));
        }
        for (Projectile p : sim.getActiveProjectiles()) {
            if (!p.pointDefenseTarget || p.intercepted || p.remainingTime <= 0f) continue;
            if (!tender.faction().hostileTo(p.shooterFaction)) continue;
            pool.add(new Candidate(p, p.currentX(), p.currentY(), p.toX, p.toY, Interceptable.SOFT_HITPOINTS,
                    WarshipPointDefence.speedOf(p)));
        }
        return pool;
    }
}
