package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Ticks the warships over the battle: each mount picks the nearest enemy it can
 * reach and traverse to, then fires its weapon's burst at that ground, every
 * round a real detonation that lands after the flight time its own speed
 * implies.
 *
 * <p>The shells are not a second damage model. A round becomes an ordinary
 * {@link PendingDetonation}, so splash, line of sight, wall breach, roof loss
 * and friendly fire come from the pipeline that already owns them, at the
 * figures the weapon itself carries. Friendly fire is unconditional, as it is
 * for every detonation.
 */
public final class WarshipSystem {

    /** Penetration by the game's damage type — what armour has to stop. */
    private static final float PENETRATION_KINETIC = 12f;
    private static final float PENETRATION_EXPLOSIVE = 8f;
    private static final float PENETRATION_ENERGY = 10f;
    private static final float PENETRATION_OTHER = 4f;

    private final List<Warship> ships = new ArrayList<>();
    private final List<Warship> shipsView = Collections.unmodifiableList(ships);
    private final List<WarshipShell> shells = new ArrayList<>();
    private final List<WarshipShell> shellsView = Collections.unmodifiableList(shells);
    private final Random random = new Random(20261011L);

    /** A round this heavy lands as a heavy blast; lighter ones are only a detonation. */
    private static final float HEAVY_IMPACT_DAMAGE = 100f;

    /**
     * Seconds between two fire cues from one mount. A Devastator fires twelve
     * rounds a second and a cue per round would be static, so a burst is heard
     * as one report per this interval; a single shot always sounds.
     */
    private static final float FIRE_CUE_GAP_SECONDS = 0.3f;
    /** Seconds between two landing cues for one weapon, for the same reason. */
    private static final float IMPACT_CUE_GAP_SECONDS = 0.2f;

    private final List<WarshipSoundCue> cues = new ArrayList<>();
    private final List<WarshipSoundCue> cuesView = Collections.unmodifiableList(cues);
    private final Map<Object, Float> lastCueAt = new IdentityHashMap<>();
    private final Set<Warship.Mount> litBeams = Collections.newSetFromMap(new IdentityHashMap<>());
    private float clock;

    /** Sounds asked for since the last {@link #beginFrame()}, for whoever presents the battle. */
    public List<WarshipSoundCue> cuesThisFrame() {
        return cuesView;
    }

    /** Forgets last frame's cues; called once per simulation advance so a paused caller does not replay them. */
    public void beginFrame() {
        cues.clear();
    }

    private boolean cueAllowed(Object key, float gap) {
        Float last = lastCueAt.get(key);
        if (last != null && clock - last < gap) return false;
        lastCueAt.put(key, clock);
        return true;
    }

    private void cue(WarshipSoundCue.Kind kind, String soundId, float x, float y) {
        if (soundId != null) cues.add(new WarshipSoundCue(kind, soundId, x, y, 1f));
    }

    /** Puts a ship over the battle. */
    public void add(Warship ship) {
        ships.add(ship);
    }

    public List<Warship> ships() {
        return shipsView;
    }

    /** Rounds in flight, for presentation and evidence. */
    public List<WarshipShell> shells() {
        return shellsView;
    }

    /** A turret fires once its barrel is this close to the bearing of what it has chosen. */
    private static final float ALIGNED_DEGREES = 2f;
    /** A beam this strong, in damage per second, is a lance that cuts ground; weaker beams are anti-ship and silent here. */
    private static final float LANCE_MIN_DPS = 500f;
    private static final float LANCE_TELEGRAPH_SECONDS = 1.5f;
    private static final float LANCE_PENETRATION = 10f;
    private static final int LANCE_WALL_DAMAGE = 100;
    private static final float LANCE_MIN_HALF_WIDTH = 1.2f;
    private static final float LANCE_MIN_SWEEP_RATE = 8f;
    private static final float LANCE_MAX_HALF_ANGLE = 1.2f;
    private static final float LANCE_REST_SECONDS = 2f;

    public void tick(float dt, BattleSimulation sim) {
        clock += dt;
        landShells(dt, sim);
        for (Warship ship : ships) {
            turnHull(dt, ship);
            for (Warship.Mount mount : ship.mounts()) {
                tickMount(dt, sim, ship, mount);
            }
        }
    }

    /**
     * Turns the hull toward the heading its order wants, at the hull's own rate:
     * it accelerates toward the turn, cruises at its maximum and brakes in time
     * to stop on the heading rather than swinging through it.
     */
    private static void turnHull(float dt, Warship ship) {
        if (ship.order() instanceof WarshipOrder.FireOnArea area && area.bringGunsToBear() && !ship.headingChosen()) {
            ship.desiredHeading = WarshipFacing.bestHeading(ship, area.x(), area.y());
            ship.markHeadingChosen();
        }
        float error = WarshipFacing.wrap(ship.desiredHeading - ship.facingDegrees);
        float wanted = Math.signum(error)
                * Math.min(ship.maxTurnDegPerSec, (float) Math.sqrt(2f * ship.turnAccelDegPerSec2 * Math.abs(error)));
        float step = ship.turnAccelDegPerSec2 * dt;
        ship.turnVelocity += Math.max(-step, Math.min(step, wanted - ship.turnVelocity));
        float move = ship.turnVelocity * dt;
        if (Math.abs(error) <= Math.abs(move) && Math.abs(ship.turnVelocity) < step * 2f) {
            ship.facingDegrees = ship.desiredHeading;
            ship.turnVelocity = 0f;
        } else {
            ship.facingDegrees = WarshipFacing.wrap(ship.facingDegrees + move);
        }
    }

    /** Advances every round in flight; one that arrives lands as a detonation and, if heavy, a heavy blast. */
    private void landShells(float dt, BattleSimulation sim) {
        for (int i = shells.size() - 1; i >= 0; i--) {
            WarshipShell shell = shells.get(i);
            if (!shell.advance(dt)) continue;
            shells.remove(i);
            sim.detonateNow(shell.landing());
            if (cueAllowed(shell.weapon, IMPACT_CUE_GAP_SECONDS)) {
                cue(WarshipSoundCue.Kind.IMPACT, shell.weapon.impactSoundId(), shell.toX, shell.toY);
            }
            if (shell.weapon.damagePerShot() >= HEAVY_IMPACT_DAMAGE) {
                sim.spawnHeavyImpact(shell.toX, shell.toY, shell.blastCells);
            }
        }
    }

    private void tickMount(float dt, BattleSimulation sim, Warship ship, Warship.Mount m) {
        m.secondsSinceFired += dt;
        m.cooldown -= dt;
        VanillaWeapon w = m.weapon;
        if (w.beam()) {
            tickBeam(dt, sim, ship, m);
            return;
        }
        if (m.burstRemaining > 0) {
            traverse(dt, ship, m, AirBody.facingToward(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m)));
            m.burstTimer -= dt;
            if (m.burstTimer <= 0f) {
                fire(sim, ship, m);
                m.burstRemaining--;
                m.burstTimer = w.burstDelaySeconds();
                if (m.burstRemaining == 0) {
                    m.cooldown = Math.max(w.cooldownSeconds(), 0.05f);
                    m.aimed = false;
                }
            }
            return;
        }
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        WarshipOrder.FireOnArea area = ship.order() instanceof WarshipOrder.FireOnArea a ? a : null;
        // A choice is kept until the mount has fired on it, so a turret can finish
        // swinging; it is dropped if the order changed or the hull has turned it out of arc.
        if (m.aimed && (area == null || !area.contains(m.aimX, m.aimY) || !Warship.bears(
                m, ship.facingDegrees, ship.mountX(m), ship.mountY(m), m.aimX, m.aimY))) {
            m.aimed = false;
        }
        if (!m.aimed && area != null) m.aimed = acquire(sim, ship, m, area);
        if (!m.aimed) {
            traverse(dt, ship, m, rest);
            return;
        }
        float bearing = AirBody.facingToward(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m));
        traverse(dt, ship, m, bearing);
        if (m.cooldown > 0f) return;
        if (Math.abs(WarshipFacing.wrap(bearing - m.headingDegrees)) > ALIGNED_DEGREES) return;
        m.burstRemaining = w.burstSize();
        m.burstTimer = 0f;
    }

    /**
     * A beam mount with an area to cut lays one sweep across the area's width as
     * seen from the mount, walked at the weapon's own traverse rate and kept inside
     * the slot's arc. Once laid the sweep is committed, like any lance: the
     * barrel follows it but does not re-aim it.
     */
    private void tickBeam(float dt, BattleSimulation sim, Warship ship, Warship.Mount m) {
        VanillaWeapon w = m.weapon;
        if (m.lance != null) {
            if (sim.lanceSweeps().active().contains(m.lance)) {
                m.headingDegrees = (float) Math.toDegrees(m.lance.angle()) - 90f;
                if (m.lance.isBurning()) {
                    m.secondsSinceFired = 0f;
                    if (litBeams.add(m)) {
                        cue(WarshipSoundCue.Kind.BEAM_LIT, w.fireSoundId(), ship.mountX(m), ship.mountY(m));
                    }
                }
                return;
            }
            litBeams.remove(m);
            m.lance = null;
        }
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        WarshipOrder.FireOnArea area = ship.order() instanceof WarshipOrder.FireOnArea a ? a : null;
        if (area == null || m.cooldown > 0f || w.damagePerSecond() < LANCE_MIN_DPS
                || !acquire(sim, ship, m, area)) {
            traverse(dt, ship, m, rest);
            return;
        }
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        float dx = area.x() - mx;
        float dy = area.y() - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float centre = (float) Math.atan2(dy, dx);
        float half = dist > area.radius()
                ? Math.min(LANCE_MAX_HALF_ANGLE, (float) Math.asin(area.radius() / dist)) : LANCE_MAX_HALF_ANGLE;
        // Kept inside the slot's own arc, measured about the slot's direction.
        float slotWorld = (float) Math.toRadians(rest + 90f);
        float halfArc = (float) Math.toRadians(Math.max(m.slot.arcDegrees(), 1f) * 0.5f);
        float start = Math.max(-halfArc, wrapRadians(centre - half - slotWorld));
        float end = Math.min(halfArc, wrapRadians(centre + half - slotWorld));
        if (end - start < 0.05f) {
            traverse(dt, ship, m, rest);
            return;
        }
        float rate = Math.max(LANCE_MIN_SWEEP_RATE, w.turnRateDegPerSec());
        float burn = Math.max(1f, (float) Math.toDegrees(end - start) / rate);
        LanceSweep sweep = new LanceSweep(0L, ship.faction, mx, my,
                slotWorld + start, slotWorld + end,
                WarshipScale.cells(w.rangePx()),
                Math.max(LANCE_MIN_HALF_WIDTH, WarshipScale.cells(w.beamWidthPx())),
                LANCE_TELEGRAPH_SECONDS, burn, w.damagePerSecond(),
                LANCE_PENETRATION, LANCE_WALL_DAMAGE, w.id());
        m.lance = sim.startLanceSweep(sweep);
        m.shotsFired++;
        m.cooldown = sweep.totalSeconds() + Math.max(w.cooldownSeconds(), LANCE_REST_SECONDS);
    }

    private static float wrapRadians(float r) {
        float d = (float) (r % (2 * Math.PI));
        if (d > Math.PI) d -= (float) (2 * Math.PI);
        if (d < -Math.PI) d += (float) (2 * Math.PI);
        return d;
    }

    /**
     * Swings the barrel toward {@code bearing} at the weapon's own traverse rate.
     * A fixed mount, a hardpoint or a weapon with no traverse, points where the
     * hull points it.
     */
    private static void traverse(float dt, Warship ship, Warship.Mount m, float bearing) {
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        float rate = m.weapon.turnRateDegPerSec();
        if (m.slot.hardpoint() || rate <= 0f) {
            m.headingDegrees = rest;
            return;
        }
        float step = rate * dt;
        float error = WarshipFacing.wrap(bearing - m.headingDegrees);
        m.headingDegrees = Math.abs(error) <= step
                ? bearing : WarshipFacing.wrap(m.headingDegrees + Math.signum(error) * step);
    }

    /**
     * Chooses, at random among the hostile units inside the ordered area that this
     * mount can reach, the one to fire on. Random rather than nearest so a
     * battery spreads over the ground it was given instead of piling every round
     * on the nearest man.
     */
    private boolean acquire(BattleSimulation sim, Warship ship, Warship.Mount m, WarshipOrder.FireOnArea area) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        int seen = 0;
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            Faction f = roster.identity().faction(u);
            if (!ship.faction.hostileTo(f)) continue;
            float ux = world.x(u);
            float uy = world.y(u);
            if (!area.contains(ux, uy) || !Warship.bears(m, ship.facingDegrees, mx, my, ux, uy)) continue;
            seen++;
            if (random.nextInt(seen) == 0) {
                m.aimX = ux;
                m.aimY = uy;
            }
        }
        return seen > 0;
    }

    private void fire(BattleSimulation sim, Warship ship, Warship.Mount m) {
        VanillaWeapon w = m.weapon;
        m.shotsFired++;
        m.secondsSinceFired = 0f;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        if (cueAllowed(m, FIRE_CUE_GAP_SECONDS)) cue(WarshipSoundCue.Kind.FIRE, w.fireSoundId(), mx, my);
        float dx = m.aimX - mx;
        float dy = m.aimY - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        // The weapon's own cone, as a lateral miss at the target's range.
        float miss = dist * (float) Math.tan(Math.toRadians(w.spreadDegrees() * 0.5f))
                * (random.nextFloat() - 0.5f) * 2f;
        float nx = dist > 0f ? -dy / dist : 0f;
        float ny = dist > 0f ? dx / dist : 0f;
        float lx = m.aimX + nx * miss;
        float ly = m.aimY + ny * miss;
        float speed = Math.max(1f, WarshipScale.cells(w.projectileSpeedPx()));
        float flight = dist / speed;
        float blast = Math.max(1f, WarshipScale.cells(w.explosionRadiusPx()));
        // A heavy round's own blast is the authored aftermath; the generic smoke
        // plume belongs to the lighter ones that have no blast of their own.
        boolean heavy = w.damagePerShot() >= HEAVY_IMPACT_DAMAGE;
        PendingDetonation landing = new PendingDetonation(
                0L, lx, ly, 0f,
                blast, w.damagePerShot(), penetration(w.damageType()),
                Math.round(w.damagePerShot()), ship.faction,
                /*aerialDelivery*/ false,
                /*wallDamageRadius*/ blast,
                /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false,
                /*authoredAftermath*/ heavy);
        shells.add(new WarshipShell(w, mx, my, lx, ly, flight, blast, landing));
    }

    private static float penetration(String damageType) {
        return switch (damageType) {
            case "KINETIC" -> PENETRATION_KINETIC;
            case "HIGH_EXPLOSIVE" -> PENETRATION_EXPLOSIVE;
            case "ENERGY" -> PENETRATION_ENERGY;
            default -> PENETRATION_OTHER;
        };
    }
}
