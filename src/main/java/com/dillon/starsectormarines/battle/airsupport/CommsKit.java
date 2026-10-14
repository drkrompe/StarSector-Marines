package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.marine.CommsKitSpec;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * Whether a unit carries the means to direct its side's air: a forward-observer radio in its special-equipment slot.
 * The one definition of that capability, read off the unit's own item, so a commander's idea of a squad that can call a
 * strike and the squad's ability to do it cannot drift apart. A commander may know it of its own squads (it is the kit
 * they were issued), never of an enemy's. Sibling of {@code DemolitionKit}.
 */
public final class CommsKit {

    private CommsKit() {}

    /** The radio {@code unit} carries, or {@code null} when it carries none. */
    public static CommsKitSpec specOf(World world, long unit) {
        if (!world.hasSecondaryWeapon(unit)) return null;
        SpecialEquipmentDef kit = world.specialEquipment(unit);
        if (kit == null || kit.aiPolicy() != SpecialAiPolicy.FORWARD_OBSERVER) return null;
        return kit.commsKitSpec();
    }

    /** Whether {@code unit} carries a forward-observer radio. */
    public static boolean carried(World world, long unit) {
        return specOf(world, unit) != null;
    }
}
