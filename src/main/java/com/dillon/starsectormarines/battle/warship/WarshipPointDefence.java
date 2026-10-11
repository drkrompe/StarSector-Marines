package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
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
 */
public final class WarshipPointDefence {

    /** A mount fires once its barrel is this close to the bearing of what it has chosen. */
    static final float ALIGNED_DEGREES = 6f;
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

    /** Engagements made (shots at ordnance, hit or miss), for evidence. */
    public int engagements() {
        return engagements;
    }

    /** Rounds destroyed, for evidence. */
    public int intercepts() {
        return intercepts;
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

    /** The chance one engagement (the weapon's whole burst) destroys the ordnance. */
    public static float engagementKillChance(VanillaWeapon weapon, float distanceCells, float ordnanceSpeedCells) {
        float miss = 1f - roundHitChance(weapon, distanceCells, ordnanceSpeedCells);
        int rounds = weapon.beam() ? 1 : Math.max(1, weapon.burstSize());
        return 1f - (float) Math.pow(miss, rounds);
    }

    /** The ordnance a mount has picked, and where it is now. */
    private static final class Target {
        Projectile projectile;
        WarshipShell shell;
        float x;
        float y;
        float speed;
        float distanceSq = Float.MAX_VALUE;
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
    void tickMount(float dt, BattleSimulation sim, Warship ship, Warship.Mount m, List<WarshipShell> shells) {
        m.secondsSinceFired += dt;
        m.cooldown -= dt;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        Target target = nearest(sim, ship, m, mx, my, shells);
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        if (target == null) {
            WarshipSystem.traverse(dt, ship, m, rest);
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
        float dx = target.x - mx;
        float dy = target.y - my;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        boolean stopped = random.nextFloat() < engagementKillChance(w, distance, target.speed);
        engagements++;
        if (stopped) {
            intercepts++;
            m.intercepts++;
            if (target.projectile != null) target.projectile.intercepted = true;
            if (target.shell != null) target.shell.intercepted = true;
        }
        marks.add(new LiveMark(ship.faction, w, mx, my, target.x, target.y, stopped));
    }

    /** The nearest hostile round in flight that the mount can reach and bear on. */
    private static Target nearest(BattleSimulation sim, Warship ship, Warship.Mount m,
                                  float mx, float my, List<WarshipShell> shells) {
        Target best = new Target();
        for (Projectile p : sim.getActiveProjectiles()) {
            if (!p.pointDefenseTarget || p.intercepted || p.remainingTime <= 0f) continue;
            if (!ship.faction.hostileTo(p.shooterFaction)) continue;
            consider(best, ship, m, mx, my, p.currentX(), p.currentY(), speedOf(p), p, null);
        }
        for (WarshipShell s : shells) {
            if (s.intercepted || !ship.faction.hostileTo(s.faction)) continue;
            consider(best, ship, m, mx, my, s.x(), s.y(), s.speedCells(), null, s);
        }
        return best.projectile == null && best.shell == null ? null : best;
    }

    private static void consider(Target best, Warship ship, Warship.Mount m, float mx, float my,
                                 float x, float y, float speed, Projectile p, WarshipShell s) {
        float dx = x - mx;
        float dy = y - my;
        float d2 = dx * dx + dy * dy;
        if (d2 >= best.distanceSq) return;
        if (!Warship.bears(m, ship.facingDegrees, mx, my, x, y)) return;
        best.projectile = p;
        best.shell = s;
        best.x = x;
        best.y = y;
        best.speed = speed;
        best.distanceSq = d2;
    }

    private static float speedOf(Projectile p) {
        float dx = p.toX - p.fromX;
        float dy = p.toY - p.fromY;
        return (float) Math.sqrt(dx * dx + dy * dy) / Math.max(0.001f, p.totalFlightTime);
    }
}
