package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.Interceptable;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A warship's point defence: the flak, vulcans and PD lasers its variant puts in
 * the slots, shooting down hostile ordnance that crosses their reach.
 *
 * <p>The mounts are independent of the ship's area-fire order. A ship on
 * {@code HoldFire} still defends itself and whatever lies under its guns, because
 * a mount's reach is the umbrella: any hostile round in flight inside a mount's
 * range (and arc) is a candidate, whichever way it is headed, so the ship covers
 * itself and the ground beneath the umbrella without being told what to protect.
 *
 * <h2>What a mount engages</h2>
 * The nearest hostile round it can bear on, among the rounds in flight that the
 * ground battle already tracks: {@link Projectile}s that opt in with
 * {@code pointDefenseTarget} (the same opt-in the carried interceptor pods read)
 * and the {@link WarshipShell}s hostile warships are firing. Friendly rounds are
 * never engaged. The barrel traverses at the weapon's own turn rate and fires
 * once it is within {@link #ALIGNED_DEGREES} of the bearing. A turret out of arc
 * or out of reach engages nothing.
 *
 * <h2>The hit rule</h2>
 * An engagement is one trigger pull: the weapon's burst, at most once per
 * {@link #MIN_ENGAGEMENT_SECONDS} (a slow weapon is held to its own cooldown).
 * Each round of the burst hits with {@link #roundHitChance}; the engagement
 * destroys the target if any round hits. The figure is derived from the weapon
 * rather than authored per gun:
 * <ul>
 *   <li>a beam is hitscan, {@link #BEAM_ACCURACY}; a projectile starts from
 *       {@link #SHOT_ACCURACY};</li>
 *   <li>accuracy falls linearly to half at the weapon's full reach;</li>
 *   <li>a projectile must lead its target: the faster the ordnance crosses
 *       relative to the round, the worse the lead, down to a floor of
 *       {@link #MIN_LEAD};</li>
 *   <li>the weapon's own cone ({@code min spread}) costs accuracy, to a floor.</li>
 * </ul>
 * A miss still costs the engagement, so a salvo larger than the mounts can answer
 * saturates them and the surplus lands. Interception is not damage: a destroyed
 * round simply never lands.
 *
 * <p>A shell's position and speed are read off its {@link MissilePath} at the
 * round's own clock, so a missile is engaged where it is drawn, weaving and
 * boosting, and a carrier shot down before its split releases no warheads.
 */
public final class WarshipPointDefence {

    /** A mount fires once its barrel is this close to the bearing of what it has chosen. */
    public static final float ALIGNED_DEGREES = 6f;

    /**
     * How far beyond its reach, as a multiple of it, a point-defence mount watches inbound ordnance. With nothing it
     * can shoot yet, it lays its barrel on the nearest such round, so a fast missile that is inside its reach for only
     * a few ticks meets a barrel already pointing at it rather than one still traversing from rest.
     */
    static final float TRACKING_REACH = 4f;
    /** The quickest a mount reselects and fires again, however short its weapon's own cooldown. */
    static final float MIN_ENGAGEMENT_SECONDS = 0.35f;
    /** A beam strikes where it points. */
    static final float BEAM_ACCURACY = 0.9f;
    /** The chance a round on the right line meets a target that is not crossing. */
    static final float SHOT_ACCURACY = 0.8f;
    /** The worst lead penalty a projectile suffers against fast ordnance. */
    static final float MIN_LEAD = 0.1f;
    /** The cone, in degrees, at which a gun is taken to be hopeless. */
    private static final float HOPELESS_SPREAD_DEGREES = 20f;
    private static final float MIN_SPREAD_FACTOR = 0.25f;
    /** How long a tracer stays readable, in sim-seconds. */
    private static final float MARK_SECONDS = 0.3f;

    /** One engagement, for the renderer and evidence: the mount's muzzle, the round it shot at, and whether it died. */
    public record Mark(Faction faction, VanillaWeapon weapon, float fromX, float fromY,
                       float toX, float toY, boolean stopped, float freshness) {
    }

    private static final class LiveMark {
        final Faction faction;
        final VanillaWeapon weapon;
        final float fromX;
        final float fromY;
        final float toX;
        final float toY;
        final boolean stopped;
        float remaining = MARK_SECONDS;

        LiveMark(Faction faction, VanillaWeapon weapon, float fromX, float fromY,
                 float toX, float toY, boolean stopped) {
            this.faction = faction;
            this.weapon = weapon;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.stopped = stopped;
        }
    }

    private final Random random = new Random(20261012L);
    private final List<LiveMark> marks = new ArrayList<>();
    private List<Mark> markView = Collections.emptyList();
    private int engagements;
    private int intercepts;
    private int hullMissileEngagements;
    private int hullMissileIntercepts;

    /**
     * {@code -Dbattle.warship.pdThreatFirst=false} is the control: a mount takes the nearest round whoever it is aimed
     * at. By default a round aimed at the mount's own hull counts as half as far off, so a ship's point defence turns
     * onto a salvo coming at it before rounds merely passing.
     */
    static final boolean THREAT_FIRST = !"false".equalsIgnoreCase(System.getProperty("battle.warship.pdThreatFirst"));
    /** A round aimed at the mount's own hull is weighed as this share of its distance (squared: a half). */
    static final float THREAT_DISTANCE_SQ_SHARE = 0.25f;

    /**
     * {@code -Dbattle.warship.pdHullReach=false} is the control. A missile or warhead aimed at a hull flies the hull
     * fight's geometry ({@link WarshipShipFire#HULL_MISSILE_SCALE}): {@link WarshipScale#HULL_REACH_MULTIPLIER} times
     * the base game's distance at {@link WarshipScale#HULL_RANGE} times its speed. Point defence answers it in the same
     * geometry: it reaches it at the hull reach, judges the shot as the base game would at the base game's distance and
     * speed, and cycles on the hull fight's clock ({@link WarshipScale#HULL_TEMPO}), so the shots a mount has at a
     * missile crossing its umbrella are the base game's. Without it a mount's umbrella is a sliver of a hull's length
     * that a hull missile crosses in a tick or two, and a salvo lands whole.
     */
    static final boolean HULL_REACH = !"false".equalsIgnoreCase(System.getProperty("battle.warship.pdHullReach"));

    /** Engagements made (shots at ordnance, hit or miss), for evidence. */
    public int engagements() {
        return engagements;
    }

    /** Rounds destroyed, for evidence. */
    public int intercepts() {
        return intercepts;
    }

    /** Engagements at a missile or warhead aimed at a hull, for evidence. */
    public int hullMissileEngagements() {
        return hullMissileEngagements;
    }

    /** Missiles and warheads aimed at a hull that point defence destroyed, for evidence. */
    public int hullMissileIntercepts() {
        return hullMissileIntercepts;
    }

    /** The recent engagements, newest last; {@code freshness} runs 1 to 0 as a tracer fades. */
    public List<Mark> marks() {
        return markView;
    }

    /**
     * The chance one round of {@code weapon} destroys ordnance at {@code distanceCells}
     * crossing at {@code ordnanceSpeedCells} (cells per second). See the class
     * comment for the rule.
     */
    public static float roundHitChance(VanillaWeapon weapon, float distanceCells, float ordnanceSpeedCells) {
        float reach = Math.max(0.001f, WarshipScale.cells(weapon.rangePx()));
        float reachFactor = 1f - 0.5f * Math.max(0f, Math.min(1f, distanceCells / reach));
        if (weapon.beam()) return BEAM_ACCURACY * reachFactor;
        float roundSpeed = Math.max(0.001f, WarshipScale.cells(weapon.projectileSpeedPx()));
        float lead = Math.max(MIN_LEAD, Math.min(1f, 1f - ordnanceSpeedCells / (2f * roundSpeed)));
        float spread = Math.max(MIN_SPREAD_FACTOR, 1f - weapon.spreadDegrees() / HOPELESS_SPREAD_DEGREES);
        return SHOT_ACCURACY * reachFactor * lead * spread;
    }

    /** The share of guided ground anti-air rounds one working point-defence mount stops, and the most any hull stops. */
    static final float GROUND_MISSILE_STOP_PER_MOUNT = 0.35f;
    static final float GROUND_MISSILE_STOP_MAX = 0.8f;

    /**
     * The chance the hull's point defence stops one guided round fired at it by the ground's anti-air. Such a round is
     * resolved as a timed round rather than flown as ordnance the mounts could track, so the defence is a share: each
     * working point-defence mount stops {@link #GROUND_MISSILE_STOP_PER_MOUNT} of them, up to
     * {@link #GROUND_MISSILE_STOP_MAX}, while the flux allows a defence at all.
     */
    public static float groundMissileStopChance(Warship ship) {
        if (ship.overloaded() || !ship.fluxPool().canDefend()) return 0f;
        int working = 0;
        for (Warship.Mount m : ship.mounts()) {
            if (!m.knockedOut() && WarshipAirRules.classify(m) == WarshipAirRules.WeaponClass.POINT_DEFENCE) working++;
        }
        return groundMissileStopChance(working);
    }

    static float groundMissileStopChance(int workingPointDefenceMounts) {
        return Math.min(GROUND_MISSILE_STOP_MAX, Math.max(0, workingPointDefenceMounts) * GROUND_MISSILE_STOP_PER_MOUNT);
    }

    /**
     * What one engagement costs the ship's flux: a projectile weapon's whole
     * burst at its per-shot cost, a beam its per-second cost over the shortest
     * engagement.
     */
    static float engagementFlux(VanillaWeapon weapon) {
        if (weapon.beam()) return weapon.fluxPerSecond() * MIN_ENGAGEMENT_SECONDS;
        return weapon.fluxPerShot() * Math.max(1, weapon.burstSize());
    }

    /** The chance one engagement (the weapon's whole burst) destroys the ordnance, whatever its toughness. */
    public static float engagementKillChance(VanillaWeapon weapon, float distanceCells, float ordnanceSpeedCells) {
        return engagementKillChance(weapon, distanceCells, ordnanceSpeedCells, 0f);
    }

    /**
     * How much a body's hit points blunt a round: none up to {@link #SOFT_HITPOINTS}, then falling as
     * {@code 1 / (1 + excess / TOUGHNESS_SCALE)}, so a swarm of weak missiles is the case flak was
     * designed for and a torpedo is not. Zero or unknown hit points are soft.
     */
    public static float toughnessFactor(float hitpoints) {
        return Interceptable.toughnessFactor(hitpoints);
    }

    /** As {@link #engagementKillChance(VanillaWeapon, float, float)}, for a body with {@code hitpoints}. */
    public static float engagementKillChance(VanillaWeapon weapon, float distanceCells, float ordnanceSpeedCells,
                                             float hitpoints) {
        float miss = 1f - roundHitChance(weapon, distanceCells, ordnanceSpeedCells) * toughnessFactor(hitpoints);
        int rounds = weapon.beam() ? 1 : Math.max(1, weapon.burstSize());
        return 1f - (float) Math.pow(miss, rounds);
    }

    /** The ordnance a mount has picked, and where it is now. */
    private static final class Target {
        Projectile projectile;
        Interceptable body;
        float hitpoints;
        float x;
        float y;
        float speed;
        float distanceSq = Float.MAX_VALUE;
        boolean atHull;
    }

    void ageMarks(float dt) {
        for (int i = marks.size() - 1; i >= 0; i--) {
            LiveMark mark = marks.get(i);
            mark.remaining -= dt;
            if (mark.remaining <= 0f) marks.remove(i);
        }
        List<Mark> views = new ArrayList<>(marks.size());
        for (LiveMark m : marks) {
            views.add(new Mark(m.faction, m.weapon, m.fromX, m.fromY, m.toX, m.toY, m.stopped,
                    Math.max(0f, m.remaining / MARK_SECONDS)));
        }
        markView = Collections.unmodifiableList(views);
    }

    /** One tick of one point-defence mount. */
    void tickMount(float dt, BattleSimulation sim, Warship ship, Warship.Mount m, List<WarshipShell> shells,
                   List<WarheadStrike> strikes) {
        m.secondsSinceFired += dt;
        m.cooldown -= dt;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        if (!ship.fluxPool().canDefend()) {
            // Overloaded: point defence is silent with everything else.
            WarshipSystem.traverse(dt, ship, m, rest);
            return;
        }
        Target target = nearest(sim, ship, m, mx, my, shells, strikes, 1f);
        if (target == null) {
            Target inbound = nearest(sim, ship, m, mx, my, shells, strikes, TRACKING_REACH);
            WarshipSystem.traverse(dt, ship, m,
                    inbound == null ? rest : AirBody.facingToward(inbound.x - mx, inbound.y - my));
            return;
        }
        float bearing = AirBody.facingToward(target.x - mx, target.y - my);
        WarshipSystem.traverse(dt, ship, m, bearing);
        if (m.cooldown > 0f) return;
        if (Math.abs(WarshipFacing.wrap(bearing - m.headingDegrees)) > ALIGNED_DEGREES) return;
        engage(ship, m, mx, my, target);
    }

    private void engage(Warship ship, Warship.Mount m, float mx, float my, Target target) {
        VanillaWeapon w = m.weapon;
        m.shotsFired++;
        m.secondsSinceFired = 0f;
        m.cooldown = Math.max(w.cooldownSeconds(), MIN_ENGAGEMENT_SECONDS);
        ship.fluxPool().spend(engagementFlux(w));
        float dx = target.x - mx;
        float dy = target.y - my;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float speed = target.speed;
        if (target.atHull && HULL_REACH) {
            // Against a hull missile, the base game's shot in the base game's geometry, on the hull fight's clock.
            m.cooldown *= WarshipScale.HULL_TEMPO;
            distance /= WarshipScale.HULL_REACH_MULTIPLIER;
            speed /= WarshipScale.HULL_RANGE;
        }
        boolean stopped = random.nextFloat() < engagementKillChance(w, distance, speed, target.hitpoints);
        engagements++;
        if (target.atHull) hullMissileEngagements++;
        if (stopped) {
            intercepts++;
            if (target.atHull) hullMissileIntercepts++;
            m.intercepts++;
            if (target.projectile != null) target.projectile.intercepted = true;
            if (target.body != null) target.body.intercept();
        }
        marks.add(new LiveMark(ship.faction, w, mx, my, target.x, target.y, stopped));
    }

    /** Whether {@code m} has hostile ordnance to shoot at now; ordnance outranks aircraft for a point-defence mount. */
    boolean hasTarget(BattleSimulation sim, Warship ship, Warship.Mount m,
                      List<WarshipShell> shells, List<WarheadStrike> strikes) {
        return nearest(sim, ship, m, ship.mountX(m), ship.mountY(m), shells, strikes, 1f) != null;
    }

    /** Draws a tracer for a shot at something that is not ordnance (an aircraft): same marks, same renderer. */
    void trace(Warship ship, VanillaWeapon weapon, float fromX, float fromY, float toX, float toY, boolean hit) {
        trace(ship.faction, weapon, fromX, fromY, toX, toY, hit);
    }

    /** As above for a shooter that is not a warship (a tender's drone): the tracer is drawn in the weapon's own colours. */
    public void trace(Faction faction, VanillaWeapon weapon, float fromX, float fromY, float toX, float toY,
                      boolean hit) {
        marks.add(new LiveMark(faction, weapon, fromX, fromY, toX, toY, hit));
    }

    /**
     * The nearest hostile round in flight that the mount can bear on within {@code reachMultiplier} times its reach.
     */
    private static Target nearest(BattleSimulation sim, Warship ship, Warship.Mount m, float mx, float my,
                                  List<WarshipShell> shells, List<WarheadStrike> strikes, float reachMultiplier) {
        Target best = new Target();
        for (Projectile p : sim.getActiveProjectiles()) {
            if (!p.pointDefenseTarget || p.intercepted || p.remainingTime <= 0f) continue;
            if (!ship.faction.hostileTo(p.shooterFaction)) continue;
            consider(best, ship, m, mx, my, p.currentX(), p.currentY(), speedOf(p), 0f, p, null, reachMultiplier,
                    null);
        }
        for (WarshipShell s : shells) {
            if (s.intercepted || !s.canBeIntercepted() || !ship.faction.hostileTo(s.faction)) continue;
            consider(best, ship, m, mx, my, s.x(), s.y(), s.speedCells(), s.hitpoints(), null, s, reachMultiplier,
                    s.hullTarget());
        }
        for (WarheadStrike s : strikes) {
            if (s.intercepted || !ship.faction.hostileTo(s.faction)) continue;
            consider(best, ship, m, mx, my, s.x(), s.y(), s.speedCells(), s.hitpoints(), null, s, reachMultiplier,
                    s.hullTarget());
        }
        return best.projectile == null && best.body == null ? null : best;
    }

    private static void consider(Target best, Warship ship, Warship.Mount m, float mx, float my,
                                 float x, float y, float speed, float hitpoints, Projectile p, Interceptable s,
                                 float reachMultiplier, Warship aimedAt) {
        float dx = x - mx;
        float dy = y - my;
        float d2 = dx * dx + dy * dy;
        // A hull missile is reached, and weighed, in the hull fight's geometry.
        float scale = aimedAt != null && HULL_REACH ? WarshipScale.HULL_REACH_MULTIPLIER : 1f;
        float weighed = d2 / (scale * scale);
        // Weighed, not filtered: the reach test below is on the true distance.
        if (THREAT_FIRST && aimedAt == ship) weighed *= THREAT_DISTANCE_SQ_SHARE;
        if (weighed >= best.distanceSq) return;
        if (!Warship.bears(m, ship.facingDegrees, mx, my, x, y, reachMultiplier * scale)) return;
        best.projectile = p;
        best.body = s;
        best.hitpoints = hitpoints;
        best.x = x;
        best.y = y;
        best.speed = speed;
        best.distanceSq = weighed;
        best.atHull = aimedAt != null;
    }

    /** Cells per second a ground projectile crosses at, over its whole flight. */
    public static float speedOf(Projectile p) {
        float dx = p.toX - p.fromX;
        float dy = p.toY - p.fromY;
        return (float) Math.sqrt(dx * dx + dy * dy) / Math.max(0.001f, p.totalFlightTime);
    }
}
