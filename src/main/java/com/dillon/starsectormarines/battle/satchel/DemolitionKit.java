package com.dillon.starsectormarines.battle.satchel;

import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * Whether a unit carries the means to destroy a building outright: a contact-demolition satchel kit with a charge left.
 * It is the one definition of demolition capability, read off the unit's own special equipment, so a commander's idea
 * of a squad that can wreck and the squad's ability to plant a charge cannot drift apart. A commander may know it of
 * its own squads (it is the kit they were issued), never of an enemy's.
 */
public final class DemolitionKit {

    private DemolitionKit() {}

    /** Whether {@code unit} holds a satchel kit with at least one use left. */
    public static boolean carried(World world, long unit) {
        if (!world.hasSecondaryWeapon(unit)) return false;
        SpecialEquipmentDef kit = world.specialEquipment(unit);
        return kit != null
                && kit.aiPolicy() == SpecialAiPolicy.CONTACT_DEMOLITION
                && kit.hasAvailableUse(world.secondaryAmmo(unit));
    }
}
