package com.dillon.starsectormarines.battle.battery;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipScale;
import com.dillon.starsectormarines.battle.warship.WarshipSystem;

/**
 * Advances every fortress battery once per tick: who holds its fire control, whether it can fire, the envelope it
 * shows to the ships overhead, and its guns.
 *
 * <p>The guns do not make a new damage rule. A round is a ship's shell fired from the ground
 * ({@link WarshipSystem#fireFromGround}): it flies, can be shot down, meets the shield and then the armour rule, and
 * lands on hulls alone. What a ground battery adds is only the aim at an airborne hull, and the same range and burst
 * figures the game gives the weapon on a ship.
 *
 * <p>An operational battery registers its envelope with the warship system as a live hazard hostile to every side but
 * its holder's, so a ship's standoff keeps its station out of reach; a silenced, captured-from or destroyed battery
 * withdraws it on the same tick.
 */
public final class FortressBatterySystem {

    /** Seconds a gun settles on a hull newly in reach before its first round leaves: the warning a ship gets. */
    public static final float LOCK_SECONDS = 1.5f;
    /** The shortest a gun rests between bursts, whatever the data says. */
    static final float MIN_CYCLE_SECONDS = 0.5f;

    public void tick(float dt, BattleSimulation sim) {
        FortressBatteryService service = sim.fortressBatteries();
        if (service.isEmpty()) return;
        WarshipSystem warships = sim.warships();
        for (FortressBattery battery : service.batteries()) {
            if (battery.destroyed()) continue;
            if (!sim.world().isAlive(battery.structureId())) {
                battery.markDestroyed();
                warships.clearBatteryHazard(battery);
                for (FortressBattery.Gun gun : battery.guns()) silence(gun);
                continue;
            }
            tickFireControl(dt, sim, battery);
            if (battery.operational()) {
                warships.setBatteryHazard(battery, battery.owner(), battery.x(), battery.y(), battery.envelopeCells());
            } else {
                warships.clearBatteryHazard(battery);
            }
            for (FortressBattery.Gun gun : battery.guns()) tickGun(dt, sim, battery, gun);
        }
    }

    private static void tickFireControl(float dt, BattleSimulation sim, FortressBattery battery) {
        float r = battery.structure().fireControlRadius;
        float rSq = r * r;
        Faction holder = battery.owner();
        boolean holderPresent = false;
        Faction hostile = null;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            Faction faction = sim.identity().faction(unit);
            if (faction == Faction.CIVILIAN || !standsAtPoints(sim.identity().type(unit))) continue;
            float dx = sim.world().x(unit) - battery.x();
            float dy = sim.world().y(unit) - battery.y();
            if (dx * dx + dy * dy > rSq) continue;
            if (holder.friendlyTo(faction)) holderPresent = true;
            else if (holder.hostileTo(faction) && hostile == null) hostile = faction;
        }
        battery.fireControl().tick(dt, holderPresent, hostile);
    }

    /** Whether a body of this type can hold or take a control point: people and machines, not emplacements or drones. */
    private static boolean standsAtPoints(UnitType type) {
        return !type.isStatic() && !type.isDrone();
    }

    private static void silence(FortressBattery.Gun gun) {
        gun.burstRemaining = 0;
        gun.lockSeconds = 0f;
        gun.target = null;
    }

    private void tickGun(float dt, BattleSimulation sim, FortressBattery battery, FortressBattery.Gun gun) {
        gun.cooldown -= dt;
        if (!battery.operational()) {
            silence(gun);
            return;
        }
        float reach = WarshipScale.cells(gun.weapon.rangePx());
        if (gun.burstRemaining > 0) {
            Warship target = gun.target;
            if (target == null || target.destroyed() || target.distanceTo(battery.x(), battery.y()) > reach) {
                // The hull has gone or left reach: the burst is abandoned where it stands.
                gun.burstRemaining = 0;
                gun.cooldown = Math.max(gun.cooldown, MIN_CYCLE_SECONDS);
                return;
            }
            gun.burstTimer -= dt;
            if (gun.burstTimer > 0f) return;
            if (sim.warships().fireFromGround(gun.weapon, battery.owner(), battery.x(), battery.y(), target) >= 0f) {
                gun.shotsFired++;
            }
            gun.burstRemaining--;
            gun.burstTimer = Math.max(gun.weapon.burstDelaySeconds(), 0.05f);
            if (gun.burstRemaining == 0) {
                gun.cooldown = Math.max(gun.weapon.cooldownSeconds(), MIN_CYCLE_SECONDS);
            }
            return;
        }
        Warship target = pick(sim, battery, gun, reach);
        if (target == null) {
            gun.lockSeconds = 0f;
            gun.target = null;
            return;
        }
        if (target != gun.target) {
            gun.target = target;
            gun.lockSeconds = 0f;
        }
        gun.lockSeconds += dt;
        if (gun.lockSeconds < LOCK_SECONDS || gun.cooldown > 0f) return;
        gun.burstRemaining = Math.max(1, gun.weapon.burstSize());
        gun.burstTimer = 0f;
    }

    /** The nearest hostile hull the gun can reach and its round can hurt, or {@code null}. */
    private static Warship pick(BattleSimulation sim, FortressBattery battery, FortressBattery.Gun gun, float reach) {
        float damage = gun.weapon.damagePerShot();
        float penetration = WarshipSystem.penetration(gun.weapon.damageType());
        Warship best = null;
        float bestGap = Float.MAX_VALUE;
        for (Warship ship : sim.warships().ships()) {
            if (ship.destroyed() || ship.departed() || !battery.owner().hostileTo(ship.faction)) continue;
            if (Warship.effectiveDamage(damage, penetration, ship.armour) <= 0f) continue;
            float gap = ship.distanceTo(battery.x(), battery.y());
            if (gap > reach || gap >= bestGap) continue;
            best = ship;
            bestGap = gap;
        }
        return best;
    }
}
