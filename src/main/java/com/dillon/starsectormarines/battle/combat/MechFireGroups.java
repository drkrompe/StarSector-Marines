package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;

/**
 * The two trigger groups a piloted mech fires from its mouse buttons. The left button fires
 * the primary group and the right button the secondary group; both can be held together.
 *
 * <p>The split is by weapon role, not by hardpoint: guns, cannons and lasers are primary, and
 * missile and rocket racks are secondary. Only mounts that can be fired at a cursor point
 * ({@link HeavyWeapons#supportsPointFire}) belong to either group, so an indirect launcher is
 * in neither. A chassis whose point-fire mounts are all one role keeps them all on the left
 * button and leaves the right button with nothing to fire, which is also what a chassis with
 * a single group has always done.
 *
 * <p>Groups are bit masks over the hardpoint ordinals ({@code 1 << slot.ordinal()}). Number
 * key 1 means the default groups; keys 2-4 re-point the <em>left</em> button at one hardpoint
 * and leave the right button on the secondary group.
 */
public final class MechFireGroups {
    private MechFireGroups() {}

    /** The left-button selection that means "the primary group". */
    public static final int DEFAULT_SELECTION = 0;

    /** Whether a mount belongs on the secondary button: it launches missiles or rockets. */
    public static boolean secondaryRole(MechWeaponMount mount) {
        return mount != null && mount.component.hardpointType == HardpointType.MISSILE;
    }

    /** Hardpoints the left button fires by default. */
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

    /** Hardpoints the right button fires; empty when the chassis has one group. */
    public static int secondaryMask(MechWeaponMount[] mounts) {
        int primary = primaryMask(mounts);
        int secondary = 0;
        for (MechWeaponMount mount : mounts) {
            if (HeavyWeapons.supportsPointFire(mount) && secondaryRole(mount)) secondary |= bit(mount);
        }
        return secondary & ~primary;
    }

    /** What the left button fires under a number-key selection (0 is the primary group). */
    public static int leftMask(MechWeaponMount[] mounts, int selection) {
        if (selection == DEFAULT_SELECTION) return primaryMask(mounts);
        int ordinal = selection - 1;
        if (ordinal < 0 || ordinal >= mounts.length) return 0;
        MechWeaponMount mount = mounts[ordinal];
        return HeavyWeapons.supportsPointFire(mount) ? bit(mount) : 0;
    }

    /** What the right button fires; number keys never change it. */
    public static int rightMask(MechWeaponMount[] mounts, int selection) {
        return secondaryMask(mounts);
    }

    public static boolean contains(int mask, MechWeaponMount mount) {
        return mount != null && (mask & bit(mount)) != 0;
    }

    /** HUD badge for a mount: {@code L}, {@code R}, {@code LR}, or empty when no button fires it. */
    public static String badge(MechWeaponMount[] mounts, int selection, MechWeaponMount mount) {
        return (contains(leftMask(mounts, selection), mount) ? "L" : "")
                + (contains(rightMask(mounts, selection), mount) ? "R" : "");
    }

    private static int bit(MechWeaponMount mount) {
        return 1 << mount.slot.ordinal();
    }
}
