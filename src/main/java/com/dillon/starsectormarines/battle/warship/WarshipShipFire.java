package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ship-versus-ship: a warship's mounts firing on hostile hulls, under {@link WarshipShipRules}.
 *
 * <p>It is the third customer of the ship's mounts beside ground fire and {@link WarshipAirFire}, not a second
 * weapon model. A mount takes a hull only when its weapon may fire at a ship, the ship's {@link ShipFireOrder}
 * allows it, the hull is in the weapon's reach and the slot's arc, the mount has ordnance and the flux pool lets the
 * burst start; it then traverses onto the point the hull will be at, and fires the weapon's burst on the weapon's
 * own cooldown, magazine and flux. A mount in the middle of a ground or air burst finishes it first.
 *
 * <h2>What a round does</h2>
 * A projectile is aimed where the hull will be after the round's flight ({@link WarshipAirRules#leadPoint}),
 * thrown off by the weapon's cone as a hash of who fired what, and flies as an ordinary {@link WarshipShell} drawn
 * by the existing renderer. On arrival it lands on hulls only: whatever hostile hull its blast reaches takes it,
 * shield first (as flux, inside the arc) and then the armour rule, through {@link WarshipSystem#onDetonation}. A
 * round whose blast reaches no hull, because the hull moved or the cone threw it wide, carries on and comes down on
 * the ground ({@link WarshipShipRules#strayLanding}) as an ordinary detonation: stray fire is real on the ground.
 * A direct-fire round between hulls is not a point-defence target. A beam lands on the hull at once.
 */
public final class WarshipShipFire {

    /** A mount fires once its barrel is this close to the bearing of the lead point. */
    static final float ALIGNED_DEGREES = 3f;
    /** A mount that cannot bring its barrel onto the hull in this long gives it up. */
    static final float ALIGN_PATIENCE_SECONDS = 2.5f;
    private static final float GIVE_UP_REST_SECONDS = 0.5f;
    /** A ship turns toward a hostile hull this far beyond its longest anti-ship reach, so it is facing it by the time it can fire. */
    static final float NOTICE_REACH_SHARE = 1.5f;

    /** One mount's trigger pull at one hull. */
    private static final class Engagement {
        final Warship target;
        int roundsLeft;
        float timer;
        float unaligned;
        boolean paid;

        Engagement(Warship target, int rounds) {
            this.target = target;
            this.roundsLeft = rounds;
        }
    }

    private final Map<Warship.Mount, Engagement> engagements = new IdentityHashMap<>();
    private final Map<String, Integer> roundsByWeapon = new TreeMap<>();
    private final List<WarshipShell> shells;
    private final WarshipPointDefence tracers;
    private int engagementsStarted;
    private int roundsFired;
    private int roundsOnHull;
    private int roundsStrayed;

    WarshipShipFire(List<WarshipShell> shells, WarshipPointDefence tracers) {
        this.shells = shells;
        this.tracers = tracers;
    }

    /** Trigger pulls at hulls, for evidence. */
    public int engagements() {
        return engagementsStarted;
    }

    /** Rounds fired at hulls, for evidence. */
    public int roundsFired() {
        return roundsFired;
    }

    /** Rounds fired at a hull whose landing reached a hostile hull (shield or plate), for evidence. */
    public int roundsOnHull() {
        return roundsOnHull;
    }

    /** Rounds fired at a hull that reached none and came down on the ground, for evidence. */
    public int roundsStrayed() {
        return roundsStrayed;
    }

    /** Rounds fired at hulls by weapon id, for evidence. */
    public Map<String, Integer> roundsByWeapon() {
        return Collections.unmodifiableMap(roundsByWeapon);
    }

    void noteLanding(boolean onHull) {
        if (onHull) roundsOnHull++;
        else roundsStrayed++;
    }

    /** Whether the ship may fire at hulls now. */
    static boolean looksAtHulls(Warship ship) {
        return ship.shipFire() == ShipFireOrder.ALL && !ship.destroyed() && !ship.departing() && !ship.overloaded();
    }

    /** Whether {@code target} is a hull {@code ship} may fight. */
    static boolean hostileHull(Warship ship, Warship target) {
        return target != ship && !target.destroyed() && !target.departed() && ship.faction.hostileTo(target.faction);
    }

    /**
     * Chooses the hull the ship turns its guns on, once per tick: the nearest hostile hull within
     * {@link #NOTICE_REACH_SHARE} of its longest anti-ship reach, kept while it is still a hostile hull in that
     * range. Ties go to the earlier-added hull.
     */
    void chooseTarget(Warship ship, List<Warship> ships) {
        if (!looksAtHulls(ship)) {
            ship.hullTarget(null);
            return;
        }
        float notice = longestReach(ship) * NOTICE_REACH_SHARE;
        Warship current = ship.hullTarget();
        if (current != null && hostileHull(ship, current) && gap(ship, current) <= notice) return;
        Warship best = null;
        float bestGap = Float.MAX_VALUE;
        for (Warship other : ships) {
            if (!hostileHull(ship, other)) continue;
            float g = gap(ship, other);
            if (g <= notice && g < bestGap) {
                best = other;
                bestGap = g;
            }
        }
        ship.hullTarget(best);
    }

    private static float gap(Warship ship, Warship other) {
        return other.distanceTo(ship.x, ship.y);
    }

    /** The longest reach, in cells, of the ship's weapons that may fire at a hull; zero when it has none. */
    static float longestReach(Warship ship) {
        float reach = 0f;
        for (Warship.Mount m : ship.mounts()) {
            if (WarshipShipRules.mayFireAtHull(m.weapon)) reach = Math.max(reach, WarshipScale.cells(m.weapon.rangePx()));
        }
        return reach;
    }

    /**
     * Gives one mount its turn at the hulls. Returns {@code true} when the mount spent this tick on a hull (the
     * caller must then not run its ground logic), {@code false} when it is not engaged with one. The decrement of the
     * mount's cooldown is this method's whenever it returns {@code true}.
     */
    boolean tickMount(float dt, WarshipSystem system, Warship ship, Warship.Mount m, List<Warship> ships) {
        if (!looksAtHulls(ship) || !WarshipShipRules.mayFireAtHull(m.weapon)) {
            engagements.remove(m);
            return false;
        }
        VanillaWeapon w = m.weapon;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        Engagement e = engagements.get(m);
        if (e != null && (!hostileHull(ship, e.target) || !inReach(ship, m, mx, my, e.target))) {
            engagements.remove(m);
            e = null;
        }
        if (e == null) {
            if (m.burstRemaining > 0 || m.cooldown - dt > 0f) return false;
            if (!ship.hasOrdnance(m)) return false;
            if (!ship.fluxPool().canStart(WarshipPointDefence.engagementFlux(w))) return false;
            Warship target = pick(ship, m, mx, my, ships);
            if (target == null) return false;
            e = new Engagement(target, w.beam() ? 1 : Math.max(1, w.burstSize()));
            engagements.put(m, e);
        }
        m.secondsSinceFired += dt;
        m.cooldown -= dt;
        m.aimed = false;
        Warship target = e.target;
        float[] aim = WarshipAirRules.leadPoint(mx, my, target.centerX(), target.centerY(),
                target.velocityX, target.velocityY, w);
        float bearing = AirBody.facingToward(aim[0] - mx, aim[1] - my);
        WarshipSystem.traverse(dt, ship, m, bearing);
        if (Math.abs(WarshipFacing.wrap(bearing - m.headingDegrees)) > ALIGNED_DEGREES) {
            e.unaligned += dt;
            if (e.unaligned > ALIGN_PATIENCE_SECONDS) {
                engagements.remove(m);
                m.cooldown = Math.max(m.cooldown, GIVE_UP_REST_SECONDS);
            }
            return true;
        }
        e.unaligned = 0f;
        e.timer -= dt;
        if (e.timer > 0f) return true;
        if (!ship.hasOrdnance(m)) {
            engagements.remove(m);
            return true;
        }
        if (!e.paid) {
            // Paid for when the barrel is on the hull and about to fire, so a mount that never gets on spends nothing.
            float cost = WarshipPointDefence.engagementFlux(w);
            if (!ship.fluxPool().canStart(cost)) {
                engagements.remove(m);
                return true;
            }
            ship.fluxPool().spend(cost);
            e.paid = true;
            engagementsStarted++;
        }
        fireRound(system, ship, m, target, aim);
        e.roundsLeft--;
        e.timer = w.burstDelaySeconds();
        if (e.roundsLeft <= 0) {
            engagements.remove(m);
            m.cooldown = Math.max(w.cooldownSeconds(), w.beam() ? WarshipPointDefence.MIN_ENGAGEMENT_SECONDS : 0.05f);
        }
        return true;
    }

    private static boolean inReach(Warship ship, Warship.Mount m, float mx, float my, Warship target) {
        float[] aim = WarshipAirRules.leadPoint(mx, my, target.centerX(), target.centerY(),
                target.velocityX, target.velocityY, m.weapon);
        float reach = WarshipScale.cells(m.weapon.rangePx());
        return Math.hypot(aim[0] - mx, aim[1] - my) <= reach
                && Warship.bears(m, ship.facingDegrees, mx, my, aim[0], aim[1]);
    }

    /** The ship's chosen hull if this mount can reach it, else the nearest hostile hull it can; ties by add order. */
    private static Warship pick(Warship ship, Warship.Mount m, float mx, float my, List<Warship> ships) {
        Warship chosen = ship.hullTarget();
        if (chosen != null && hostileHull(ship, chosen) && inReach(ship, m, mx, my, chosen)) return chosen;
        Warship best = null;
        double bestDist = Double.MAX_VALUE;
        for (Warship other : ships) {
            if (!hostileHull(ship, other) || !inReach(ship, m, mx, my, other)) continue;
            double d = Math.hypot(other.centerX() - mx, other.centerY() - my);
            if (d < bestDist) {
                best = other;
                bestDist = d;
            }
        }
        return best;
    }

    private void fireRound(WarshipSystem system, Warship ship, Warship.Mount m, Warship target, float[] aim) {
        VanillaWeapon w = m.weapon;
        m.shotsFired++;
        ship.spendOrdnance(m);
        m.secondsSinceFired = 0f;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        roundsFired++;
        roundsByWeapon.merge(w.id(), 1, Integer::sum);
        system.fireCue(m, w, mx, my);
        float penetration = WarshipSystem.penetration(w.damageType());
        float blast = Math.max(1f, WarshipScale.cells(w.damageRadiusPx()));
        long seed = WarshipAirRules.roundSeed(m.slot.id(), m.shotsFired,
                ((long) ship.serial() << 20) ^ target.serial(), ship.faction.ordinal());
        if (w.beam()) {
            // A beam lands where it points: on the hull's near face, at once.
            float damage = w.damagePerSecond() * WarshipAirRules.BEAM_PULSE_SECONDS;
            boolean onHull = system.strikeHulls(WarshipSystem.detonation(ship.faction, target.centerX(),
                    target.centerY(), damage, penetration, blast));
            noteLanding(onHull);
            tracers.trace(ship, w, mx, my, target.centerX(), target.centerY(), onHull);
            return;
        }
        float dx = aim[0] - mx;
        float dy = aim[1] - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float miss = WarshipShipRules.coneMiss(w, dist, WarshipAirRules.unit(seed, 0));
        float nx = dist > 0f ? -dy / dist : 0f;
        float ny = dist > 0f ? dx / dist : 0f;
        float lx = aim[0] + nx * miss;
        float ly = aim[1] + ny * miss;
        float reach = WarshipScale.cells(w.rangePx());
        float[] stray = WarshipShipRules.strayLanding(mx, my, lx, ly, reach, WarshipAirRules.unit(seed, 1));
        float visual = Math.max(1f, WarshipScale.cells(w.explosionRadiusPx()));
        float speed = Math.max(1f, WarshipScale.cells(w.projectileSpeedPx()));
        MissilePath path = MissilePath.straight(mx, my, lx, ly, speed);
        shells.add(new WarshipShell(w, ship.faction, ship, mx, my, lx, ly, path, visual, w.damagePerShot(),
                penetration, blast, null, null, 0f, null, 0f).atHull(target, stray[0], stray[1], true));
    }
}
