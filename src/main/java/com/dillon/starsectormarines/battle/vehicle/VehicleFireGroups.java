package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.FireGroups;
import com.dillon.starsectormarines.battle.combat.ManualPointFire;
import com.dillon.starsectormarines.battle.turret.StructureDef;

/**
 * A driven hull's weapons as {@link FireGroups} mounts: index 0 is the main turret and index {@code i + 1} the
 * hull's {@code i}th secondary mount ({@link GroundTurret#secondaries}). A pick is LINKED or {@code index + 1}, so
 * pick 1 is the main gun and pick 2 the first secondary mount. The sorting is {@link FireGroups}'s: the main gun
 * leads the left button and every other mount is on the right, a crewed defence on neither.
 */
public final class VehicleFireGroups {
    private VehicleFireGroups() {}

    /** The index of the main turret. */
    public static final int MAIN = 0;

    /** The hull's mounts; null where a mount is shot away or cannot be fired at a point. Empty for an unarmed hull. */
    public static FireGroups.Mount[] mounts(VehicleType type, GroundTurret turret) {
        if (type == null || turret == null || !type.hasTurretWeapon()) return new FireGroups.Mount[0];
        FireGroups.Mount[] out = new FireGroups.Mount[1 + turret.secondaries.size()];
        if (!turret.destroyed()) out[MAIN] = mount(turret.structureFor(type), false, false);
        for (int i = 0; i < turret.secondaries.size(); i++) {
            VehicleMount mount = turret.secondaries.get(i).mount();
            out[i + 1] = mount(mount.structure(), true, mount.crewed());
        }
        return out;
    }

    private static FireGroups.Mount mount(StructureDef structure, boolean second, boolean crewed) {
        if (crewed) return FireGroups.Mount.CREWED;
        var weapon = structure.mount.weapon;
        if (!ManualPointFire.supportsPointFire(weapon)) return null;
        return new FireGroups.Mount(ManualPointFire.ordnance(weapon), second, false);
    }
}
