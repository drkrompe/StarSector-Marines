package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;

/**
 * The two trigger groups a piloted mech fires from its mouse buttons, and the cycle each button
 * walks through its group. The left button holds the primary group and the right button the
 * secondary group; both can be held together.
 *
 * <p>The split is by weapon role, not by hardpoint: guns, cannons and lasers are primary, and
 * missile and rocket racks are secondary. Only mounts that can be fired at a cursor point
 * ({@link HeavyWeapons#supportsPointFire}) belong to either group; an arcing LRM rack is one,
 * and drops its salvo on the point. A chassis whose point-fire mounts are all one role keeps them all on the left
 * button and leaves the right button with nothing to fire.
 *
 * <p>Each button has a list of members: {@link #LINKED} (the whole group at once), then each mount
 * of its group in hardpoint order. A group of one mount has only LINKED, which is that mount. The
 * pick is LINKED or a hardpoint ({@code slot.ordinal() + 1}); a mount the button's pick leaves out
 * holds fire. A destroyed or empty hardpoint is never a member; one out of ammunition stays.
 *
 * <p>Groups are bit masks over the hardpoint ordinals ({@code 1 << slot.ordinal()}).
 */
public final class MechFireGroups {
    private MechFireGroups() {}

    /** The pick that fires the button's whole group. */
    public static final int LINKED = 0;

    /** Whether a mount belongs on the secondary button: it launches missiles or rockets. */
    public static boolean secondaryRole(MechWeaponMount mount) {
        return mount != null && mount.component.hardpointType == HardpointType.MISSILE;
    }

    /** Hardpoints in the left button's group. */
    public static int primaryMask(MechWeaponMount[] mounts) {
        int primary = 0;
        int secondary = 0;
        for (MechWeaponMount mount : mounts) {
            if (!HeavyWeapons.supportsPointFire(mount)) continue;
            if (secondaryRole(mount)) secondary |= bit(mount);
            else primary |= bit(mount);
        }
        return primary != 0 ? primary : secondary;
    }

    /** Hardpoints in the right button's group; empty when the chassis has one group. */
    public static int secondaryMask(MechWeaponMount[] mounts) {
        int primary = primaryMask(mounts);
        int secondary = 0;
        for (MechWeaponMount mount : mounts) {
            if (HeavyWeapons.supportsPointFire(mount) && secondaryRole(mount)) secondary |= bit(mount);
        }
        return secondary & ~primary;
    }

    public static int groupMask(MechWeaponMount[] mounts, FireTrigger trigger) {
        return trigger == FireTrigger.LEFT ? primaryMask(mounts) : secondaryMask(mounts);
    }

    /**
     * The button's cycle in order: LINKED, then each mount of a group of two or more. Empty when the
     * button has no group.
     */
    public static int[] members(MechWeaponMount[] mounts, FireTrigger trigger) {
        int group = groupMask(mounts, trigger);
        if (group == 0) return new int[0];
        int count = Integer.bitCount(group);
        if (count == 1) return new int[]{LINKED};
        int[] members = new int[count + 1];
        int next = 1;
        for (int ordinal = 0; ordinal < Integer.SIZE && next <= count; ordinal++) {
            if ((group & (1 << ordinal)) != 0) members[next++] = ordinal + 1;
        }
        return members;
    }

    /** Whether {@code pick} is one of the button's members. */
    public static boolean isMember(MechWeaponMount[] mounts, FireTrigger trigger, int pick) {
        for (int member : members(mounts, trigger)) if (member == pick) return true;
        return false;
    }

    /** The member after {@code pick}, wrapping to LINKED; LINKED when {@code pick} is no longer a member. */
    public static int next(MechWeaponMount[] mounts, FireTrigger trigger, int pick) {
        int[] members = members(mounts, trigger);
        for (int i = 0; i < members.length; i++) {
            if (members[i] == pick) return members[(i + 1) % members.length];
        }
        return LINKED;
    }

    /** The member a number key jumps to ({@code index} 0 is key 1), or -1 past the end of the list. */
    public static int member(MechWeaponMount[] mounts, FireTrigger trigger, int index) {
        int[] members = members(mounts, trigger);
        return index >= 0 && index < members.length ? members[index] : -1;
    }

    /** What the button fires on {@code pick}: its group on LINKED, one of its mounts, or nothing. */
    public static int mask(MechWeaponMount[] mounts, FireTrigger trigger, int pick) {
        int group = groupMask(mounts, trigger);
        if (pick == LINKED) return group;
        int ordinal = pick - 1;
        if (ordinal < 0 || ordinal >= Integer.SIZE) return 0;
        return group & (1 << ordinal);
    }

    public static boolean contains(int mask, MechWeaponMount mount) {
        return mount != null && (mask & bit(mount)) != 0;
    }

    /** HUD badge for a mount: the button that fires it under the current picks, or empty when it holds. */
    public static String badge(MechWeaponMount[] mounts, MechTriggerPicks picks, MechWeaponMount mount) {
        return (contains(picks.leftMask(mounts), mount) ? "L" : "")
                + (contains(picks.rightMask(mounts), mount) ? "R" : "");
    }

    /** The button whose group a mount is in, {@code L} or {@code R}, picked or not; empty when neither. */
    public static String group(MechWeaponMount[] mounts, MechWeaponMount mount) {
        return contains(primaryMask(mounts), mount) ? "L" : contains(secondaryMask(mounts), mount) ? "R" : "";
    }

    private static int bit(MechWeaponMount mount) {
        return 1 << mount.slot.ordinal();
    }
}
