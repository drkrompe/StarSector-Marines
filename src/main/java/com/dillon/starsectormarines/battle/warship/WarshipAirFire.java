package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirTargetService;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.WarshipAirRules.AirTargetClass;
import com.dillon.starsectormarines.battle.warship.WarshipAirRules.WeaponClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ship-versus-air: a warship's mounts shooting hostile aircraft, under the
 * weapon rules of {@link WarshipAirRules}.
 *
 * <p>It is a second customer of the ship's mounts, not a second weapon model. A
 * mount takes an aircraft only when its weapon may engage that class of craft,
 * the ship's {@link AirFireOrder} (or, for an {@code AUTO} ship, its posture)
 * allows the target, it has the ordnance, and the flux pool lets the burst
 * start; it then traverses, leads and fires the weapon's burst with the same
 * cooldowns, magazines and flux the ground fire pays. The ground side is
 * untouched: a mount in the middle of a ground burst finishes it, and a ship
 * whose order is {@code AUTO} under a posture that does not look at the air
 * never reaches this class's choosing at all.
 *
 * <h2>What a round does</h2>
 * A projectile flies as an ordinary {@link WarshipShell} to the point the craft
 * is led to ({@link WarshipAirRules#leadPoint}), over the flight time its own
 * speed implies, and is drawn by the same renderer; a beam pulses instantly with
 * a tracer. Whether a round hits is decided when it is fired, by a hash-seeded
 * roll against {@link WarshipAirRules#roundHitChance}; a hit round, on arrival,
 * lands its damage on the craft through the shared body damage route
 * ({@link BattleSimulation#applyAimedDamage}), the armour and structure law every
 * body takes, so an aircraft dies the one way an aircraft
 * dies: the cook-off, the wreck, the berth written off, the passengers lost. A
 * miss flies wide of the craft by a hashed offset. A round whose shell has been
 * shot down never lands.
 *
 * <p>Rounds already committed to kill a craft count against it, so a battery
 * does not empty itself into one fighter while another is free.
 */
public final class WarshipAirFire {

    /** A mount fires once its barrel is this close to the bearing of the lead point. */
    static final float ALIGNED_DEGREES = 4f;
    /** A mount that cannot bring its barrel onto a target in this long gives it up. */
    static final float ALIGN_PATIENCE_SECONDS = 2.5f;
    /** The shortest rest after an engagement that was given up. */
    private static final float GIVE_UP_REST_SECONDS = 1f;
    /** A miss passes the craft by at least this many body radii and at most this many more. */
    private static final float MISS_MIN_RADII = 1.5f;
    private static final float MISS_SPREAD_RADII = 2.5f;

    /** What a ship may look at in the air under its current order and posture. */
    enum Scope { NONE, DROPS, THREATS, ALL }

    /** A hostile-or-not aircraft as the guns see it this tick. */
    private record Craft(long id, Faction faction, float x, float y, float vx, float vy, float radius,
                         AirTargetClass target, boolean armed, boolean drop, float durability) {}

    /** One round on its way to a craft. */
    private static final class Shot {
        final WarshipShell shell;
        final long craftId;
        final boolean hit;
        final float damage;
        final float penetration;
        final Warship ship;
        final VanillaWeapon weapon;
        final float aimX;
        final float aimY;

        Shot(WarshipShell shell, long craftId, boolean hit, float damage, float penetration, Warship ship,
             VanillaWeapon weapon, float aimX, float aimY) {
            this.shell = shell;
            this.craftId = craftId;
            this.hit = hit;
            this.damage = damage;
            this.penetration = penetration;
            this.ship = ship;
            this.weapon = weapon;
            this.aimX = aimX;
            this.aimY = aimY;
        }
    }

    /** One mount's trigger pull at one craft. */
    private static final class Engagement {
        final long craftId;
        int roundsLeft;
        float timer;
        float unaligned;
        boolean paid;

        Engagement(long craftId, int rounds) {
            this.craftId = craftId;
            this.roundsLeft = rounds;
        }
    }

    private final List<Craft> crafts = new ArrayList<>();
    private final Map<Long, Craft> byId = new HashMap<>();
    private final Map<Long, Float> committed = new HashMap<>();
    private final List<Shot> shots = new ArrayList<>();
    private final Map<Warship.Mount, Engagement> engagements = new IdentityHashMap<>();
    private final Map<Warship.Mount, WeaponClass> classes = new IdentityHashMap<>();
    private final Map<String, Integer> roundsByWeapon = new TreeMap<>();
    private final Map<String, Integer> hitsByWeapon = new TreeMap<>();
    private final List<WarshipShell> shells;
    private final WarshipPointDefence tracers;
    private int engagementsStarted;
    private int roundsFired;
    private int roundsHit;
    private float damageLanded;

    WarshipAirFire(List<WarshipShell> shells, WarshipPointDefence tracers) {
        this.shells = shells;
        this.tracers = tracers;
    }

    /** Engagements begun (a mount's trigger pulls at aircraft), for evidence. */
    public int engagements() {
        return engagementsStarted;
    }

    /** Rounds fired at aircraft, for evidence. */
    public int roundsFired() {
        return roundsFired;
    }

    /** Rounds that struck an aircraft, for evidence. */
    public int roundsHit() {
        return roundsHit;
    }

    /** Damage landed on aircraft, for evidence. */
    public float damageLanded() {
        return damageLanded;
    }

    /** Rounds fired at aircraft by weapon id, for evidence. */
    public Map<String, Integer> roundsByWeapon() {
        return Collections.unmodifiableMap(roundsByWeapon);
    }

    /** Rounds that struck an aircraft by weapon id, for evidence. */
    public Map<String, Integer> hitsByWeapon() {
        return Collections.unmodifiableMap(hitsByWeapon);
    }

    /** What the ship may shoot at in the air now. */
    static Scope scope(Warship ship) {
        return switch (ship.airFire()) {
            case OFF -> Scope.NONE;
            case ALL -> Scope.ALL;
            case AUTO -> switch (ship.posture()) {
                case AIR_SUPERIORITY -> Scope.THREATS;
                case INTERCEPT -> Scope.DROPS;
                default -> Scope.NONE;
            };
        };
    }

    /** Refreshes the aircraft the guns can see and lands the rounds that have arrived; once per tick, after shells advance. */
    void advance(BattleSimulation sim, List<Warship> ships) {
        if (shots.isEmpty() && engagements.isEmpty() && !anyLooksUp(ships)) {
            // Nobody is looking at the air and nothing is in flight: the common case costs nothing.
            crafts.clear();
            byId.clear();
            return;
        }
        refresh(sim);
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot s = shots.get(i);
            if (s.shell != null) {
                if (s.shell.intercepted) {
                    release(s);
                    shots.remove(i);
                    continue;
                }
                if (s.shell.elapsed() < s.shell.flightSeconds) continue;
            }
            shots.remove(i);
            release(s);
            land(sim, s);
        }
    }

    private static boolean anyLooksUp(List<Warship> ships) {
        for (Warship ship : ships) {
            if (scope(ship) != Scope.NONE) return true;
        }
        return false;
    }

    private void release(Shot s) {
        if (!s.hit) return;
        committed.merge(s.craftId, -s.damage, Float::sum);
        Float left = committed.get(s.craftId);
        if (left != null && left <= 0.001f) committed.remove(s.craftId);
    }

    private void land(BattleSimulation sim, Shot s) {
        if (!s.hit) return;
        Craft craft = byId.get(s.craftId);
        if (craft == null) return;
        sim.applyAimedDamage(s.craftId, CombatTelemetryService.NO_ATTACKER, s.damage, s.penetration);
        damageLanded += s.damage;
        roundsHit++;
        hitsByWeapon.merge(s.weapon.id(), 1, Integer::sum);
        tracers.trace(s.ship, s.weapon, craft.x(), craft.y(), craft.x(), craft.y(), true);
    }

    private void refresh(BattleSimulation sim) {
        crafts.clear();
        byId.clear();
        World world = sim.world();
        AirTargetService targets = sim.getRoster().airTargets();
        long[] ids = sim.getAirEntityIds().clone();
        Arrays.sort(ids);
        for (long id : ids) {
            ShuttleMission mission = world.mission(id);
            Faction faction = world.airFaction(id);
            AirBody body = world.kinematics(id);
            if (mission == null || faction == null || body == null || mission.state == ShuttleState.GONE) continue;
            if (!targets.isPresent(id) || !targets.isAirborne(id)) continue;
            Airframe frame = world.airframe(id);
            AirTargetClass cls = frame instanceof FighterProfile ? AirTargetClass.FIGHTER : AirTargetClass.TRANSPORT;
            boolean mech = mission.mechVariant != null || (mission.mechVariants != null && mission.mechVariants.length > 0);
            boolean drop = mission.marinesRemaining > 0 || mech;
            Craft craft = new Craft(id, faction, body.x, body.y, body.vx, body.vy, targets.targetRadius(id), cls,
                    mission.strikeSortie, drop, Math.max(0f, world.hp(id)) + Math.max(0f, world.armor(id)));
            crafts.add(craft);
            byId.put(id, craft);
        }
    }

    /**
     * Gives one mount its turn at the air. Returns {@code true} when the mount spent this tick on an
     * aircraft (the caller must then not run the mount's ground or ordnance logic), {@code false} when it
     * is not engaged with one and the caller proceeds as it always did. The decrement of the mount's
     * cooldown is this method's whenever it returns {@code true}.
     *
     * @param ordnanceBusy a point-defence mount that has hostile ordnance to shoot: ordnance outranks aircraft
     */
    boolean tickMount(float dt, BattleSimulation sim, Warship ship, Warship.Mount m, boolean ordnanceBusy) {
        Scope scope = scope(ship);
        if (scope == Scope.NONE || crafts.isEmpty() && !engagements.containsKey(m)) {
            engagements.remove(m);
            return false;
        }
        WeaponClass wc = classes.computeIfAbsent(m, WarshipAirRules::classify);
        boolean pd = wc == WeaponClass.POINT_DEFENCE;
        VanillaWeapon w = m.weapon;
        if (ship.overloaded() || (pd && !ship.fluxPool().canDefend()) || ordnanceBusy) {
            engagements.remove(m);
            return false;
        }
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        Engagement e = engagements.get(m);
        Craft craft = null;
        if (e != null) {
            craft = byId.get(e.craftId);
            if (craft == null || !eligible(ship, scope, wc, craft) || !inReach(ship, m, mx, my, craft)) {
                engagements.remove(m);
                e = null;
                craft = null;
            }
        }
        if (e == null) {
            if (m.burstRemaining > 0 || m.cooldown - dt > 0f) return false;
            if (!pd && !ship.hasOrdnance(m)) return false;
            float cost = WarshipPointDefence.engagementFlux(w);
            if (!pd && !ship.fluxPool().canStart(cost)) return false;
            craft = pick(ship, m, mx, my, scope, wc);
            if (craft == null) return false;
            e = new Engagement(craft.id(), w.beam() ? 1 : Math.max(1, w.burstSize()));
            engagements.put(m, e);
        }
        m.secondsSinceFired += dt;
        m.cooldown -= dt;
        m.aimed = false;
        float[] aim = WarshipAirRules.leadPoint(mx, my, craft.x(), craft.y(), craft.vx(), craft.vy(), w);
        float bearing = AirBody.facingToward(aim[0] - mx, aim[1] - my);
        WarshipSystem.traverse(dt, ship, m, bearing);
        boolean aligned = Math.abs(WarshipFacing.wrap(bearing - m.headingDegrees)) <= ALIGNED_DEGREES;
        if (!aligned) {
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
        if (!pd && !ship.hasOrdnance(m)) {
            engagements.remove(m);
            return true;
        }
        if (!e.paid) {
            // The burst is paid for when the barrel is on the target and it is about to fire, not when a target is chosen,
            // so a mount that never gets onto its target spends nothing.
            float cost = WarshipPointDefence.engagementFlux(w);
            if (!pd && !ship.fluxPool().canStart(cost)) {
                engagements.remove(m);
                return true;
            }
            ship.fluxPool().spend(cost);
            e.paid = true;
            engagementsStarted++;
        }
        fireRound(sim, ship, m, craft, aim, wc);
        e.roundsLeft--;
        e.timer = w.burstDelaySeconds();
        if (e.roundsLeft <= 0) {
            engagements.remove(m);
            m.cooldown = Math.max(w.cooldownSeconds(), pd || w.beam() ? WarshipPointDefence.MIN_ENGAGEMENT_SECONDS : 0.05f);
        }
        return true;
    }

    private static boolean eligible(Warship ship, Scope scope, WeaponClass wc, Craft c) {
        if (!ship.faction.hostileTo(c.faction())) return false;
        if (!WarshipAirRules.mayEngage(wc, c.target())) return false;
        return scope != Scope.DROPS || c.drop();
    }

    private static boolean inReach(Warship ship, Warship.Mount m, float mx, float my, Craft c) {
        float[] aim = WarshipAirRules.leadPoint(mx, my, c.x(), c.y(), c.vx(), c.vy(), m.weapon);
        float reach = WarshipScale.cells(m.weapon.rangePx());
        return Math.hypot(aim[0] - mx, aim[1] - my) <= reach
                && Warship.bears(m, ship.facingDegrees, mx, my, aim[0], aim[1]);
    }

    /**
     * The craft this mount shoots next: armed craft before loaded boats before the rest, then the
     * nearest, then the lowest id; never one the rounds already in the air will kill.
     */
    private Craft pick(Warship ship, Warship.Mount m, float mx, float my, Scope scope, WeaponClass wc) {
        Craft best = null;
        int bestTier = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (Craft c : crafts) {
            if (!eligible(ship, scope, wc, c) || !inReach(ship, m, mx, my, c)) continue;
            if (committed.getOrDefault(c.id(), 0f) >= c.durability()) continue;
            int tier = c.armed() ? 0 : c.drop() ? 1 : 2;
            double d = Math.hypot(c.x() - mx, c.y() - my);
            if (tier < bestTier || (tier == bestTier && d < bestDist)) {
                best = c;
                bestTier = tier;
                bestDist = d;
            }
        }
        return best;
    }

    private void fireRound(BattleSimulation sim, Warship ship, Warship.Mount m, Craft craft, float[] aim,
                           WeaponClass wc) {
        VanillaWeapon w = m.weapon;
        m.shotsFired++;
        if (wc != WeaponClass.POINT_DEFENCE) ship.spendOrdnance(m);
        m.secondsSinceFired = 0f;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        float distance = (float) Math.hypot(craft.x() - mx, craft.y() - my);
        float crossing = WarshipAirRules.crossingSpeed(mx, my, craft.x(), craft.y(), craft.vx(), craft.vy());
        float chance = WarshipAirRules.roundHitChance(w, craft.target(), distance, crossing);
        long seed = WarshipAirRules.roundSeed(m.slot.id(), m.shotsFired, craft.id(), ship.faction.ordinal());
        boolean hit = WarshipAirRules.unit(seed, 0) < chance;
        float ax = aim[0];
        float ay = aim[1];
        if (!hit) {
            // A miss passes wide of the craft, to the side the hash chooses, at a few body radii.
            float dx = ax - mx;
            float dy = ay - my;
            float len = Math.max(1e-3f, (float) Math.hypot(dx, dy));
            float side = WarshipAirRules.unit(seed, 1) < 0.5f ? -1f : 1f;
            float off = craft.radius() * (MISS_MIN_RADII + MISS_SPREAD_RADII * WarshipAirRules.unit(seed, 2));
            ax += side * off * (-dy / len);
            ay += side * off * (dx / len);
        }
        float damage = w.beam() ? w.damagePerSecond() * WarshipAirRules.BEAM_PULSE_SECONDS : w.damagePerShot();
        float penetration = WarshipSystem.penetration(w.damageType());
        roundsFired++;
        roundsByWeapon.merge(w.id(), 1, Integer::sum);
        WarshipShell shell = null;
        if (w.beam()) {
            tracers.trace(ship, w, mx, my, ax, ay, hit);
        } else {
            float speed = Math.max(1f, WarshipScale.cells(w.projectileSpeedPx()));
            // An anti-fighter missile (a Locust, a Swarmer) flies its own path at the craft: the weave and the
            // curve of a missile, not a bullet's line. Where it lands, and when it hits, are decided as before.
            MissileFlightSpec flight = w.missile() ? w.flight() : null;
            MissilePath path = flight != null
                    ? MissilePath.plan(flight, mx, my, ax, ay, seed)
                    : MissilePath.straight(mx, my, ax, ay, speed);
            float visual = Math.max(1f, WarshipScale.cells(w.explosionRadiusPx()));
            shell = new WarshipShell(w, ship.faction, ship, mx, my, path.landX(), path.landY(), path, visual,
                    0f, 0f, visual, null, null, 0f, null, 0f).inert();
            shells.add(shell);
        }
        if (hit) committed.merge(craft.id(), damage, Float::sum);
        Shot shot = new Shot(shell, craft.id(), hit, damage, penetration, ship, w, ax, ay);
        if (shell == null) {
            // A beam lands at once.
            land(sim, shot);
            release(shot);
        } else {
            shots.add(shot);
        }
    }
}
