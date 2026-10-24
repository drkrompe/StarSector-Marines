package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;

/**
 * A piloted mech's hardpoints as {@link FireGroups} mounts, indexed by hardpoint ordinal, and the trigger questions
 * the mech's callers ask in hardpoint terms. The sorting and cycling rules are {@link FireGroups}'s: guns, cannons
 * and lasers on the left button, missile and rocket racks (arcing LRM racks included) on the right, LINKED first.
 *
 * <p>Only mounts that can be fired at a cursor point ({@link HeavyWeapons#supportsPointFire}) are listed; a
 * destroyed or empty hardpoint is never a member, and one out of ammunition stays. A pick is LINKED or a hardpoint
 * ({@code slot.ordinal() + 1}).
 */
public final class MechFireGroups {
    private MechFireGroups() {}

    /** The pick that fires the button's whole group. */
    public static final int LINKED = FireGroups.LINKED;

    /** Whether a mount belongs on the secondary button: it launches missiles or rockets. */
    public static boolean secondaryRole(MechWeaponMount mount) {
        return mount != null && mount.component.hardpointType == HardpointType.MISSILE;
    }

    /** The hardpoints as trigger mounts, by ordinal; null where no point-fire weapon is installed. */
    public static FireGroups.Mount[] mounts(MechWeaponMount[] mounts) {
        FireGroups.Mount[] out = new FireGroups.Mount[MechMountSlot.values().length];
        for (MechWeaponMount mount : mounts) {
            if (!HeavyWeapons.supportsPointFire(mount)) continue;
            out[mount.slot.ordinal()] = secondaryRole(mount) ? FireGroups.Mount.ORDNANCE : FireGroups.Mount.GUN;
        }
        return out;
    }

    /** Hardpoints in the left button's group. */
    public static int primaryMask(MechWeaponMount[] mounts) { return FireGroups.primaryMask(mounts(mounts)); }

    /** Hardpoints in the right button's group; empty when the chassis has one group. */
    public static int secondaryMask(MechWeaponMount[] mounts) { return FireGroups.secondaryMask(mounts(mounts)); }

    /** The button's cycle in order: LINKED, then each mount of a group of two or more. */
    public static int[] members(MechWeaponMount[] mounts, FireTrigger trigger) {
        return FireGroups.members(mounts(mounts), trigger);
    }

    public static boolean isMember(MechWeaponMount[] mounts, FireTrigger trigger, int pick) {
        return FireGroups.isMember(mounts(mounts), trigger, pick);
    }

    public static int next(MechWeaponMount[] mounts, FireTrigger trigger, int pick) {
        return FireGroups.next(mounts(mounts), trigger, pick);
    }

    public static int member(MechWeaponMount[] mounts, FireTrigger trigger, int index) {
        return FireGroups.member(mounts(mounts), trigger, index);
    }

    public static int mask(MechWeaponMount[] mounts, FireTrigger trigger, int pick) {
        return FireGroups.mask(mounts(mounts), trigger, pick);
    }

    public static boolean contains(int mask, MechWeaponMount mount) {
        return mount != null && FireGroups.contains(mask, mount.slot.ordinal());
    }

    /** HUD badge for a mount: the button that fires it under the current picks, or empty when it holds. */
    public static String badge(MechWeaponMount[] mounts, TriggerPicks picks, MechWeaponMount mount) {
        return mount == null ? "" : FireGroups.badge(mounts(mounts), picks, mount.slot.ordinal());
    }

    /** The button whose group a mount is in, {@code L} or {@code R}, picked or not; empty when neither. */
    public static String group(MechWeaponMount[] mounts, MechWeaponMount mount) {
        return mount == null ? "" : FireGroups.group(mounts(mounts), mount.slot.ordinal());
    }
}
