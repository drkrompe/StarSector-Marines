package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.FireGroups;
import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlReticle.Load;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlReticle.Loads;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.MountedGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleFireGroups;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * The controlled ground body's triggers as the reticle carries them: the same clocks and ammunition the panel's
 * strips show, read at the cursor so the player need not look away to see why a gun went quiet. Each button reads
 * the mounts its current pick fires, as its mark does.
 */
public final class DirectControlLoad {
    private DirectControlLoad() {}

    /** Both buttons' loads; ready for a warship, a tender or nothing. */
    public static Loads read(BattleSimulation sim) {
        if (sim == null || !sim.directControl().active() || sim.directControl().warshipControl()) return Loads.READY;
        long id = sim.directControl().activeUnitId();
        var world = sim.world();
        if (sim.directControl().controlledVehicleId() == id) return vehicle(sim, id);
        if (!world.isAlive(id)) return Loads.READY;
        if (world.hasMechLoadout(id)) {
            return new Loads(mech(sim, id, FireTrigger.LEFT), mech(sim, id, FireTrigger.RIGHT));
        }
        // A Marine's right button throws, and its items keep their counts on the panel.
        return new Loads(marine(sim, id), Load.READY);
    }

    /**
     * A Marine's left button: the primary's cycle, or its reload with its magazine; or, on a special, that special's
     * readying or cooldown, whichever has longer to run.
     */
    private static Load marine(BattleSimulation sim, long id) {
        var world = sim.world();
        SpecialEquipmentDef special = sim.directControl().leftSpecial();
        if (special != null) {
            float windup = (1f - sim.directControl().marineLeft().readiness()) * special.aimDuration();
            float cooldown = world.secondaryCooldownTimer(id);
            float readiness = windup > cooldown ? Load.clock(windup, special.aimDuration())
                    : Load.clock(cooldown, special.cooldown());
            return new Load(readiness, 0, 0, false);
        }
        var combat = sim.combat();
        if (!combat.has(id) || combat.primaryWeaponDef(id) == null) return Load.READY;
        int capacity = combat.magazineSize(id);
        float reload = combat.reloadTimer(id);
        if (capacity > 0 && reload > 0f) {
            return new Load(Load.clock(reload, combat.reloadSeconds(id)), combat.roundsLoaded(id), capacity, true);
        }
        return new Load(Load.clock(combat.cooldownTimer(id), combat.attackCooldown(id)),
                combat.roundsLoaded(id), capacity, false);
    }

    /**
     * The mounts the trigger's pick fires, merged: each its cycle and its trigger packs. A rotary's clock is its spin,
     * which the panel shows; on the reticle it is always ready, with its belt as the arc.
     */
    private static Load mech(BattleSimulation sim, long id, FireTrigger trigger) {
        MechWeaponMount[] mounts = sim.world().mechLoadout(id).mounts();
        TriggerPicks picks = sim.directControl().triggerPicks();
        int mask = trigger == FireTrigger.LEFT ? picks.leftMask(MechFireGroups.mounts(mounts))
                : picks.rightMask(MechFireGroups.mounts(mounts));
        Load load = null;
        for (MechWeaponMount mount : mounts) {
            if (mount == null || !MechFireGroups.contains(mask, mount)) continue;
            load = Load.merge(load, mech(mount));
        }
        return load != null ? load : Load.READY;
    }

    static Load mech(MechWeaponMount mount) {
        if (mount.isRotary()) return new Load(1f, mount.rotary.beltRounds(), mount.rotary.def().belt(), false);
        int capacity = mount.component.ammoCapacity;
        return new Load(Load.clock(mount.cycleRemaining, mount.component.cycleSeconds()),
                capacity < 0 ? 0 : mount.ammo, Math.max(0, capacity), false);
    }

    /** The hull's guns each button's pick fires ({@link VehicleFireGroups}), merged. */
    private static Loads vehicle(BattleSimulation sim, long id) {
        GroundTurret turret = sim.convoy().turret(id);
        var type = sim.convoy().vehicleType(id);
        if (turret == null || type == null || turret.structureFor(type) == null) return Loads.READY;
        FireGroups.Mount[] mounts = VehicleFireGroups.mounts(type, turret);
        TriggerPicks picks = sim.directControl().triggerPicks();
        int left = picks.leftMask(mounts);
        int right = picks.rightMask(mounts);
        Load leftLoad = null, rightLoad = null;
        for (int index = 0; index < mounts.length; index++) {
            if (!FireGroups.contains(left | right, index)) continue;
            Load load;
            if (index == VehicleFireGroups.MAIN) {
                load = turret(turret, turret.structureFor(type).mount);
            } else {
                MountedGun gun = turret.secondaries.get(index - 1);
                load = turret(gun.turret(), gun.mount().structure().mount);
            }
            if (FireGroups.contains(left, index)) leftLoad = Load.merge(leftLoad, load);
            if (FireGroups.contains(right, index)) rightLoad = Load.merge(rightLoad, load);
        }
        return new Loads(leftLoad != null ? leftLoad : Load.READY, rightLoad != null ? rightLoad : Load.READY);
    }

    /** One gun's cooldown and ready rounds; a mount authored with no capacity never runs dry and draws no arc. */
    static Load turret(GroundTurret turret, TurretMountDef mount) {
        if (turret.rotary != null) return new Load(1f, turret.rotary.beltRounds(), turret.rotary.def().belt(), false);
        boolean limited = mount.ammoCapacity > 0;
        return new Load(Load.clock(turret.cooldownTimer, mount.weapon.cooldown),
                limited ? turret.ammo : 0, limited ? turret.capacity : 0, false);
    }
}
