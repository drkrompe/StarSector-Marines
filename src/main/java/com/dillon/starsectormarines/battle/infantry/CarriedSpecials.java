package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.function.Predicate;

/**
 * A Marine's two special-item slots: the <b>hand</b> ({@code SECONDARY_WEAPON}), whose item every channel, aim and
 * throw runs through, and the <b>belt</b> ({@code BELT}), one stowed thrown item (smoke or a hand grenade). A Marine
 * with a smoke grenade and a frag carries one in each.
 *
 * <p>The belt has no channel of its own. To use its item the hand must be idle; the two are swapped, the throw runs
 * through the hand's ordinary channel, and once the hand is idle again {@link #stowBack} returns a non-throw item
 * (a rocket launcher, say) to the hand, so the belt always comes back to holding a throw. Two throws need no return:
 * whichever is in hand is fine. Readers that ask "what does this Marine carry of this kind" go through
 * {@link #find} rather than the hand alone, so the swap is invisible to them.
 */
public final class CarriedSpecials {
    private CarriedSpecials() {}

    /** Where an item is carried. */
    public enum Slot { HAND, BELT }

    /** The slot holding an item of {@code kind}, the hand first; null when neither does. */
    public static Slot find(World world, long id, Predicate<SpecialEquipmentDef> kind) {
        if (!world.hasSecondaryWeapon(id)) return null;
        SpecialEquipmentDef hand = world.specialEquipment(id);
        if (hand != null && kind.test(hand)) return Slot.HAND;
        if (world.hasBelt(id)) {
            SpecialEquipmentDef belt = world.beltEquipment(id);
            if (belt != null && kind.test(belt)) return Slot.BELT;
        }
        return null;
    }

    public static SpecialEquipmentDef spec(World world, long id, Slot slot) {
        return slot == Slot.HAND ? world.specialEquipment(id) : world.beltEquipment(id);
    }

    public static int ammo(World world, long id, Slot slot) {
        return slot == Slot.HAND ? world.secondaryAmmo(id) : world.beltAmmo(id);
    }

    public static float cooldown(World world, long id, Slot slot) {
        return slot == Slot.HAND ? world.secondaryCooldownTimer(id) : world.beltCooldownTimer(id);
    }

    /** The carried item of {@code kind}, or null. */
    public static SpecialEquipmentDef item(World world, long id, Predicate<SpecialEquipmentDef> kind) {
        Slot slot = find(world, id, kind);
        return slot == null ? null : spec(world, id, slot);
    }

    /** Uses left on the carried item of {@code kind}; 0 when there is none. */
    public static int ammo(World world, long id, Predicate<SpecialEquipmentDef> kind) {
        Slot slot = find(world, id, kind);
        return slot == null ? 0 : ammo(world, id, slot);
    }

    /** Nothing is in the hand's channel: no aim, throw or plant running and no smoke point held. */
    public static boolean handIdle(World world, long id) {
        return world.hasSecondaryWeapon(id) && world.secondaryActionTimer(id) <= 0f
                && world.smokeThrowCommit(id) == null;
    }

    /**
     * Whether an item of {@code kind} can be used now: carried with a use left, off its cooldown, and the hand idle
     * (a belt item needs the hand to swap into). Reads only.
     */
    public static boolean ready(World world, long id, Predicate<SpecialEquipmentDef> kind) {
        Slot slot = find(world, id, kind);
        return slot != null && ammo(world, id, slot) > 0 && cooldown(world, id, slot) <= 0f && handIdle(world, id);
    }

    /**
     * Puts the item of {@code kind} in the hand, swapping it out of the belt when it is there and the hand is idle.
     * True when the hand now holds it.
     */
    public static boolean draw(World world, long id, Predicate<SpecialEquipmentDef> kind) {
        Slot slot = find(world, id, kind);
        if (slot == Slot.HAND) return true;
        if (slot != Slot.BELT || !handIdle(world, id)) return false;
        world.swapBelt(id);
        return true;
    }

    /**
     * Returns a non-throw item from the belt to the hand once the hand is idle, undoing the swap a belt throw made.
     * Run every tick; a no-op for anyone without a belt.
     */
    public static void stowBack(World world, long id) {
        if (!world.hasBelt(id) || !handIdle(world, id)) return;
        SpecialEquipmentDef belt = world.beltEquipment(id);
        if (belt != null && !belt.isThrownItem()) world.swapBelt(id);
    }
}
