package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * Which installed mount an autonomous mech offers its flux reservoir to first. Against a machine (a mech or a ground
 * vehicle) the mounts are tried in order of how well each defeats that target's armor, by the durability law's own
 * measure, {@code clamp(penetration / armorRating, 0, 1)}, with slot order kept on ties. Against anything else the
 * slot order stands (arms, left shoulder, right shoulder).
 *
 * <p>The order matters because the reservoir admits one packet at a time: a chaingun firing first after every
 * release takes flux that its SRM shoulders, the chassis's anti-armor weapons, never get back
 * (heavy-equipment-progression.md). Fire at infantry is untouched. {@code -Dbattle.mech.armorFireOrder=false} is
 * the control.
 */
public final class MechFireOrder {

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("battle.mech.armorFireOrder", "true"));

    private MechFireOrder() {
    }

    /**
     * The target's armor rating when it is a machine and the rule is on; zero otherwise, which keeps the slot order.
     */
    public static float machineArmorRating(long target, BattleView sim) {
        if (!ENABLED || target == 0L || !sim.world().hasArmor(target)) return 0f;
        boolean machine = sim.world().hasMechLoadout(target)
                || sim.bodies().carrierOf(target) instanceof ConvoyService;
        return machine ? Math.max(0f, sim.world().armorRating(target)) : 0f;
    }

    /**
     * Mount indices in the order to try them. A rating of zero or less is not armor and keeps the slot order; a null
     * mount sorts last. Stable, so equally effective mounts keep their slot order.
     */
    public static int[] order(MechWeaponMount[] mounts, float armorRating) {
        int[] order = new int[mounts.length];
        float[] key = new float[mounts.length];
        for (int i = 0; i < mounts.length; i++) {
            order[i] = i;
            key[i] = armorRating <= 0f ? 0f
                    : mounts[i] == null ? -1f : efficiency(mounts[i].component, armorRating);
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
        WeaponDef weapon = component.weaponDef();
        float penetration = Math.max(weapon.penetration, weapon.contactPenetration);
        return Math.min(1f, penetration / armorRating);
    }
}
