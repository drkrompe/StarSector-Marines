package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import java.util.ArrayList;
import java.util.List;

/** Reads actual carrier poses and selected direct barrels without advancing their clocks. */
public final class DirectControlAimOrigins {
    private DirectControlAimOrigins() {}
    public record Origin(float x, float y, float bodyX, float bodyY) {}
    public static List<Origin> mech(MechLoadoutComponent loadout, int selected,
                                    float x, float y, float waistX, float waistY) {
        List<Origin> origins = new ArrayList<>();
        for (var mount : loadout.mounts()) {
            if (!HeavyWeapons.supportsPointFire(mount)
                    || selected != 0 && mount.slot.ordinal() != selected - 1) continue;
            var muzzle = MechHardpointGeometry.muzzle(x, y, waistX, waistY,
                    loadout.torsoFacingDegrees, loadout, mount, MechHardpointGeometry.nextReleaseIndex(mount));
            origins.add(new Origin(muzzle.x(), muzzle.y(), x, y));
        }
        return origins;
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
            float radians = (float) Math.toRadians(body.facingDegrees);
            float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
            float mx = body.x + type.turretMountX * cos - type.turretMountY * sin;
            float my = body.y + type.turretMountX * sin + type.turretMountY * cos;
            var mount = type.turretStructure().mount;
            var muzzle = TurretMountGeometry.muzzle(mx, my, turret.facingDeg, mount,
                    turret.burstRemaining > 0 ? TurretMountGeometry.releaseIndex(
                            mount.weapon.burstCount, turret.burstRemaining) : 0);
            origins.add(new Origin(muzzle.x(), muzzle.y(), body.x, body.y));
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
            origins.addAll(mech(loadout, sim.directControl().selectedWeapon(), x, y, waistX, waistY));
        } else origins.add(new Origin(x, y, x, y));
        return origins;
    }
}
