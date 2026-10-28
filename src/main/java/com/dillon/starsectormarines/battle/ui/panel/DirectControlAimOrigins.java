package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.FireGroups;
import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.combat.MechFireGroups;
import com.dillon.starsectormarines.battle.combat.TriggerPicks;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.GroundVehicleTurretSystem;
import com.dillon.starsectormarines.battle.vehicle.MountedGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleFireGroups;
import java.util.ArrayList;
import java.util.List;

/** Reads actual carrier poses and selected direct barrels without advancing their clocks. */
public final class DirectControlAimOrigins {
    private DirectControlAimOrigins() {}
    public record Origin(float x, float y, float bodyX, float bodyY) {}
    public static List<Origin> mech(MechLoadoutComponent loadout, TriggerPicks picks,
                                    float x, float y, float waistX, float waistY) {
        List<Origin> origins = new ArrayList<>();
        int fired = picks.firedMask(MechFireGroups.mounts(loadout.mounts()));
        for (var mount : loadout.mounts()) {
            // An arcing rack flies over walls, so a lane from its muzzle would say nothing true.
            if (!HeavyWeapons.firesFlat(mount) || !MechFireGroups.contains(fired, mount)) continue;
            var muzzle = MechHardpointGeometry.muzzle(x, y, waistX, waistY,
                    loadout.torsoFacingDegrees, loadout, mount, MechHardpointGeometry.nextReleaseIndex(mount));
            origins.add(new Origin(muzzle.x(), muzzle.y(), x, y));
        }
        return origins;
    }

    private static Origin turretOrigin(GroundBody body, GroundTurret turret, TurretMountDef mount,
                                       TurretMountGeometry.Point ring) {
        var muzzle = TurretMountGeometry.muzzle(ring.x(), ring.y(), turret.facingDeg, mount,
                turret.burstRemaining > 0 ? TurretMountGeometry.releaseIndex(
                        mount.weapon.burstCount, turret.burstRemaining) : 0);
        return new Origin(muzzle.x(), muzzle.y(), body.x, body.y);
    }

    public static List<Origin> read(BattleSimulation sim) {
        long id = sim.directControl().activeUnitId();
        float x = sim.world().renderX(id), y = sim.world().renderY(id);
        List<Origin> origins = new ArrayList<>();
        if (sim.directControl().controlledVehicleId() != 0L) {
            var type = sim.convoy().vehicleType(id);
            var body = sim.convoy().body(id);
            var turret = sim.convoy().turret(id);
            if (type == null || body == null || turret == null || !type.hasTurretWeapon()) return origins;
            // The main gun, and each second gun a button's pick fires.
            int fired = sim.directControl().triggerPicks().firedMask(VehicleFireGroups.mounts(type, turret));
            if (FireGroups.contains(fired, VehicleFireGroups.MAIN)) {
                origins.add(turretOrigin(body, turret, turret.structureFor(type).mount,
                        GroundVehicleTurretSystem.mountPoint(type, body)));
            }
            for (int i = 0; i < turret.secondaries.size(); i++) {
                MountedGun gun = turret.secondaries.get(i);
                var mount = gun.mount().structure().mount;
                // An arcing rack flies over walls, so a lane from its muzzle would say nothing true.
                if (!FireGroups.contains(fired, i + 1) || ManualPointFire.arcs(mount.weapon)) continue;
                origins.add(turretOrigin(body, gun.turret(), mount,
                        GroundVehicleTurretSystem.mountPoint(body, gun.mount().mountX(), gun.mount().mountY())));
            }
        } else if (sim.world().hasMechLoadout(id)) {
            var loadout = sim.world().mechLoadout(id);
            float waistX = 0f, waistY = 0f;
            var components = sim.getRoster().components();
            var world = sim.getEntityWorld();
            if (world.has(id, components.MECH_GAIT_STATE)) {
                MechGaitState gait = (MechGaitState) world.getObject(id, components.MECH_GAIT_STATE,
                        BattleComponents.MECH_GAIT_STATE_STATE);
                if (gait != null) { waistX = gait.waistOffsetX(); waistY = gait.waistOffsetY(); }
            }
            origins.addAll(mech(loadout, sim.directControl().triggerPicks(), x, y, waistX, waistY));
        } else origins.add(new Origin(x, y, x, y));
        return origins;
    }
}
