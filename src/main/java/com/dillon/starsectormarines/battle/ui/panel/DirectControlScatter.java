package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.MechTriggerPicks;
import com.dillon.starsectormarines.battle.control.MarineRightTrigger;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.vehicle.GroundVehicleTurretSystem;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

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
     * The controlled ground body's spread at its cursor, or none for a warship or no cursor. A Marine's right
     * button carries a hand grenade's burst radius when the grenade is its pick and it has one to throw.
     */
    public static Spread read(BattleSimulation sim) {
        if (sim == null || !sim.directControl().active() || sim.directControl().warshipControl()) return Spread.NONE;
        var aim = sim.directControl().intent();
        if (!Float.isFinite(aim.aimX()) || !Float.isFinite(aim.aimY())) return Spread.NONE;
        long id = sim.directControl().activeUnitId();
        if (sim.directControl().controlledVehicleId() == id) {
            var turret = sim.convoy().turret(id);
            var type = sim.convoy().vehicleType(id);
            var structure = type != null ? type.turretStructure() : null;
            if (turret == null || structure == null || turret.destroyed()) return Spread.NONE;
            TurretMountGeometry.Point ring = GroundVehicleTurretSystem.mountPoint(type, sim.convoy().body(id));
            return new Spread(vehicle(structure.mount.weapon,
                    (float) Math.hypot(aim.aimX() - ring.x(), aim.aimY() - ring.y())), -1f);
        }
        if (!sim.world().hasMechLoadout(id)) {
            return sim.directControl().marineRightPick() == MarineRightTrigger.Item.GRENADE
                    && sim.world().secondaryAmmo(id) > 0
                    ? new Spread(-1f, sim.world().specialEquipment(id).aoeRadius()) : Spread.NONE;
        }
        float distance = (float) Math.hypot(aim.aimX() - sim.world().renderX(id), aim.aimY() - sim.world().renderY(id));
        var loadout = sim.world().mechLoadout(id);
        var picks = sim.directControl().mechPicks();
        return new Spread(mech(loadout, picks, FireTrigger.LEFT, distance),
                mech(loadout, picks, FireTrigger.RIGHT, distance));
    }

    /**
     * The widest disk among the mounts {@code trigger}'s pick fires, for a cursor {@code distance} cells from the
     * chassis centre, or a negative value when none of them bursts.
     */
    public static float mech(MechLoadoutComponent loadout, MechTriggerPicks picks, FireTrigger trigger,
                             float distance) {
        MechWeaponMount[] mounts = loadout.mounts();
        int mask = trigger == FireTrigger.LEFT ? picks.leftMask(mounts) : picks.rightMask(mounts);
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
