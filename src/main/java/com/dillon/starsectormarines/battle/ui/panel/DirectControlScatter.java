package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.FireGroups;
import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.control.MarineRightTrigger;
import com.dillon.starsectormarines.battle.infantry.CarriedSpecials;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.vehicle.GroundVehicleTurretSystem;
import com.dillon.starsectormarines.battle.vehicle.MountedGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleFireGroups;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * How widely a button's thrown rockets or LRM salvo would land at the cursor: the widest landing disk among the
 * mounts its pick fires whose rounds burst where they are thrown. The reticle carries it in that button's tick.
 * Guns have no spread here; their rounds fly a lane, which {@link DirectControlAimLane} already draws.
 */
public final class DirectControlScatter {
    private DirectControlScatter() {}

    /** Landing-disk radii in cells for the two buttons; negative where the pick has no ground burst. */
    public record Spread(float leftCells, float rightCells) {
        public static final Spread NONE = new Spread(-1f, -1f);
        public float of(FireTrigger trigger) { return trigger == FireTrigger.LEFT ? leftCells : rightCells; }
    }

    /**
     * The controlled ground body's spread at its cursor, or none for a warship or no cursor. A Marine's left
     * button blooms with a rocket on it; its right button carries a hand grenade's burst radius when the
     * grenade is its pick and it has one to throw.
     */
    public static Spread read(BattleSimulation sim) {
        if (sim == null || !sim.directControl().active() || sim.directControl().warshipControl()) return Spread.NONE;
        var aim = sim.directControl().intent();
        if (!Float.isFinite(aim.aimX()) || !Float.isFinite(aim.aimY())) return Spread.NONE;
        long id = sim.directControl().activeUnitId();
        if (sim.directControl().controlledVehicleId() == id) {
            var turret = sim.convoy().turret(id);
            var type = sim.convoy().vehicleType(id);
            var body = sim.convoy().body(id);
            if (turret == null || type == null || body == null || turret.structureFor(type) == null) return Spread.NONE;
            // Each button spreads as widely as the widest of the mounts its pick fires.
            FireGroups.Mount[] mounts = VehicleFireGroups.mounts(type, turret);
            var picks = sim.directControl().triggerPicks();
            int left = picks.leftMask(mounts), right = picks.rightMask(mounts);
            float leftSpread = -1f, rightSpread = -1f;
            for (int index = 0; index < mounts.length; index++) {
                if (!FireGroups.contains(left | right, index)) continue;
                boolean main = index == VehicleFireGroups.MAIN;
                MountedGun gun = main ? null : turret.secondaries.get(index - 1);
                TurretMountGeometry.Point ring = main ? GroundVehicleTurretSystem.mountPoint(type, body)
                        : GroundVehicleTurretSystem.mountPoint(body, gun.mount().mountX(), gun.mount().mountY());
                WeaponDef weapon = main ? turret.structureFor(type).mount.weapon : gun.mount().structure().mount.weapon;
                float spread = vehicle(weapon, (float) Math.hypot(aim.aimX() - ring.x(), aim.aimY() - ring.y()));
                if (FireGroups.contains(left, index)) leftSpread = Math.max(leftSpread, spread);
                if (FireGroups.contains(right, index)) rightSpread = Math.max(rightSpread, spread);
            }
            return new Spread(leftSpread, rightSpread);
        }
        if (!sim.world().hasMechLoadout(id)) {
            // A Marine's left button blooms with a rocket on it, and with the flamer's splash; a rifle's round flies a lane.
            SpecialEquipmentDef picked = sim.directControl().leftSpecial();
            float reach = (float) Math.hypot(aim.aimX() - sim.world().renderX(id), aim.aimY() - sim.world().renderY(id));
            float left = picked == null ? -1f : picked.isHeldStream() ? picked.aoeRadius()
                    : ManualPointFire.scatterRadiusCells(picked.weaponDef(),
                    DirectControlFireMarks.specialAccuracy(sim, id, picked), reach, 0f);
            SpecialEquipmentDef grenade = sim.directControl().marineRightPick() == MarineRightTrigger.Item.GRENADE
                    ? CarriedSpecials.item(sim.world(), id, SpecialEquipmentDef::isThrownGrenade) : null;
            float right = grenade != null
                    && CarriedSpecials.ammo(sim.world(), id, SpecialEquipmentDef::isThrownGrenade) > 0
                    ? grenade.aoeRadius() : -1f;
            return new Spread(left, right);
        }
        float distance = (float) Math.hypot(aim.aimX() - sim.world().renderX(id), aim.aimY() - sim.world().renderY(id));
        var loadout = sim.world().mechLoadout(id);
        var picks = sim.directControl().triggerPicks();
        return new Spread(mech(loadout, picks, FireTrigger.LEFT, distance),
                mech(loadout, picks, FireTrigger.RIGHT, distance));
    }

    /**
     * The widest disk among the mounts {@code trigger}'s pick fires, for a cursor {@code distance} cells from the
     * chassis centre, or a negative value when none of them bursts.
     */
    public static float mech(MechLoadoutComponent loadout, TriggerPicks picks, FireTrigger trigger,
                             float distance) {
        MechWeaponMount[] mounts = loadout.mounts();
        int mask = trigger == FireTrigger.LEFT ? picks.leftMask(MechFireGroups.mounts(mounts)) : picks.rightMask(MechFireGroups.mounts(mounts));
        float radius = -1f;
        for (MechWeaponMount mount : mounts) {
            if (mount == null || !MechFireGroups.contains(mask, mount)) continue;
            radius = Math.max(radius, HeavyWeapons.manualScatterRadiusCells(loadout, mount, distance));
        }
        return radius;
    }

    /** A vehicle turret's disk with the cursor {@code distance} cells from its turret ring; negative for a gun. */
    public static float vehicle(WeaponDef weapon, float distance) {
        return ManualPointFire.scatterRadiusCells(weapon, weapon.accuracy, distance, 0f);
    }
}
