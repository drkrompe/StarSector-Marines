package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * Which installed mount an autonomous mech offers its flux reservoir to first. Against a machine (a mech or a ground
 * vehicle) the mounts are tried in order of how well each defeats that target's armor, by the durability law's own
 * measure, {@code clamp(penetration / armorRating, 0, 1)}, with slot order kept on ties. Against anything else the
 * slot order stands (arms, left shoulder, right shoulder).
 *
 * <p>The order matters because the reservoir admits one packet at a time: a chaingun firing first after every
 * release takes flux that its SRM shoulders, the chassis's anti-armor weapons, never get back
 * (heavy-equipment-progression.md). Fire at infantry is untouched.
 */
public final class MechFireOrder {

    private MechFireOrder() {
    }

    /**
     * The target's armor rating when it is a machine; zero otherwise, which keeps the slot order.
     */
    public static float machineArmorRating(long target, BattleView sim) {
        return machineArmorRating(sim.world(), target, sim.bodies().carrierOf(target) instanceof ConvoyService);
    }

    /** As above, told whether the target is a ground vehicle; a mech is known by its loadout. */
    public static float machineArmorRating(World world, long target, boolean groundVehicle) {
        if (target == 0L || !world.hasArmor(target)) return 0f;
        boolean machine = world.hasMechLoadout(target) || groundVehicle;
        return machine ? Math.max(0f, world.armorRating(target)) : 0f;
    }

    /**
     * Mount indices in the order to try them. A rating of zero or less is not armor and keeps the slot order; a null
     * mount sorts last. Stable, so equally effective mounts keep their slot order.
     */
    public static int[] order(MechWeaponMount[] mounts, float armorRating) {
        WeaponDef[] weapons = new WeaponDef[mounts.length];
        for (int i = 0; i < mounts.length; i++) weapons[i] = mounts[i] == null ? null : mounts[i].component.weaponDef();
        return order(weapons, armorRating);
    }

    /**
     * As above for weapons alone: a vehicle's mounts, the main gun first, sharing the hull's reservoir. A null weapon (a
     * gun shot away) sorts last.
     */
    public static int[] order(WeaponDef[] weapons, float armorRating) {
        int[] order = new int[weapons.length];
        float[] key = new float[weapons.length];
        for (int i = 0; i < weapons.length; i++) {
            order[i] = i;
            key[i] = armorRating <= 0f ? 0f
                    : weapons[i] == null ? -1f : efficiency(weapons[i], armorRating);
        }
        for (int i = 1; i < order.length; i++) {
            int index = order[i];
            int j = i - 1;
            while (j >= 0 && key[order[j]] < key[index]) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = index;
        }
        return order;
    }

    /** How much of a round's damage strips this armor: the better of the weapon's payloads, clamped at full. */
    static float efficiency(MechWeaponComponent component, float armorRating) {
        return efficiency(component.weaponDef(), armorRating);
    }

    static float efficiency(WeaponDef weapon, float armorRating) {
        float penetration = Math.max(weapon.penetration, weapon.contactPenetration);
        return Math.min(1f, penetration / armorRating);
    }
}
