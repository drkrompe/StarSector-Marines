package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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

    public void tick(float dt, BattleSimulation sim) {
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
            if (shell.weapon.damagePerShot() >= HEAVY_IMPACT_DAMAGE) {
                sim.spawnHeavyImpact(shell.toX, shell.toY, shell.blastCells);
            }
        }
    }

    private void tickMount(float dt, BattleSimulation sim, Warship ship, Warship.Mount m) {
        m.secondsSinceFired += dt;
        m.cooldown -= dt;
        VanillaWeapon w = m.weapon;
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
