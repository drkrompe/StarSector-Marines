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

    public void tick(float dt, BattleSimulation sim) {
        landShells(dt, sim);
        for (Warship ship : ships) {
            for (Warship.Mount mount : ship.mounts()) {
                tickMount(dt, sim, ship, mount);
            }
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
        if (m.burstRemaining == 0) m.headingDegrees = ship.facingDegrees + m.slot.angleDegrees();
        VanillaWeapon w = m.weapon;
        if (m.burstRemaining > 0) {
            m.burstTimer -= dt;
            if (m.burstTimer <= 0f) {
                fire(sim, ship, m);
                m.burstRemaining--;
                m.burstTimer = w.burstDelaySeconds();
                if (m.burstRemaining == 0) m.cooldown = Math.max(w.cooldownSeconds(), 0.05f);
            }
            return;
        }
        if (m.cooldown > 0f) return;
        if (!acquire(sim, ship, m)) {
            m.cooldown = 0.5f;
            return;
        }
        m.burstRemaining = w.burstSize();
        m.burstTimer = 0f;
        m.headingDegrees = AirBody.facingToward(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m));
    }

    /** Commits the mount's aim to the nearest enemy it can reach and traverse to. */
    private boolean acquire(BattleSimulation sim, Warship ship, Warship.Mount m) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        float range = WarshipScale.cells(m.weapon.rangePx());
        float bestD2 = range * range;
        boolean found = false;
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            Faction f = roster.identity().faction(u);
            if (!ship.faction.hostileTo(f)) continue;
            float dx = world.x(u) - mx;
            float dy = world.y(u) - my;
            float d2 = dx * dx + dy * dy;
            if (d2 >= bestD2 || !withinArc(ship, m, dx, dy)) continue;
            bestD2 = d2;
            m.aimX = world.x(u);
            m.aimY = world.y(u);
            found = true;
        }
        return found;
    }

    private static boolean withinArc(Warship ship, Warship.Mount m, float dx, float dy) {
        float bearing = AirBody.facingToward(dx, dy);
        float mountHeading = ship.facingDegrees + m.slot.angleDegrees();
        float diff = Math.abs(wrap(bearing - mountHeading));
        return diff <= Math.max(m.slot.arcDegrees(), 1f) * 0.5f;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
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
