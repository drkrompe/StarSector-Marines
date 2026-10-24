package com.dillon.starsectormarines.battle.combat;

/**
 * The two trigger groups a directly controlled machine fires from its mouse buttons, and the cycle each button
 * walks through its group, for every carrier with weapon mounts: a mech's hardpoints and a vehicle's turrets alike.
 * The carrier describes its mounts ({@link Mount}) in a fixed index order and this sorts them; nothing here knows
 * what a hardpoint or a turret is.
 *
 * <p>The split is by role. A direct-fire gun is primary (left button); a missile, rocket or indirect-fire mount is
 * secondary (right button), and so is any secondary turret, whatever it throws, so a hull's second gun is never
 * merged into its main gun's group. A carrier whose primary mounts are all ordnance puts them on the left button
 * (an all-rack chassis, or a launcher hull, is not stranded without a primary), keeping its secondary turrets on
 * the right; one with nothing but secondary turrets left puts them on the left. A crewed defence (a mount with its
 * own gunner) never joins either group and keeps firing on its own.
 *
 * <p>Each button has a list of members: {@link #LINKED} (the whole group at once), then each mount of its group in
 * index order. A group of one mount has only LINKED, which is that mount. The pick is LINKED or a mount
 * ({@code index + 1}); a mount the button's pick leaves out holds fire. An absent mount (a null entry: destroyed,
 * or not one the cursor can be fired with) is never a member.
 *
 * <p>Groups are bit masks over the mount indices ({@code 1 << index}).
 */
public final class FireGroups {
    private FireGroups() {}

    /** The pick that fires the button's whole group. */
    public static final int LINKED = 0;

    /**
     * What a carrier says about one of its mounts. {@code ordnance}: it throws missiles or rockets, or arcs.
     * {@code secondTurret}: a turret beside the hull's main one. {@code crewed}: a defence with its own gunner.
     */
    public record Mount(boolean ordnance, boolean secondTurret, boolean crewed) {
        public static final Mount GUN = new Mount(false, false, false);
        public static final Mount ORDNANCE = new Mount(true, false, false);
        public static final Mount SECOND_TURRET = new Mount(false, true, false);
        public static final Mount CREWED = new Mount(false, false, true);
    }

    /** Mounts in the left button's group. */
    public static int primaryMask(Mount[] mounts) {
        int guns = 0, ordnance = 0, second = 0;
        for (int i = 0; i < mounts.length; i++) {
            Mount mount = mounts[i];
            if (mount == null || mount.crewed()) continue;
            if (mount.secondTurret()) second |= 1 << i;
            else if (mount.ordnance()) ordnance |= 1 << i;
            else guns |= 1 << i;
        }
        return guns != 0 ? guns : ordnance != 0 ? ordnance : second;
    }

    /** Mounts in the right button's group; empty when the carrier has one group. */
    public static int secondaryMask(Mount[] mounts) {
        int listed = 0;
        for (int i = 0; i < mounts.length; i++) {
            if (mounts[i] != null && !mounts[i].crewed()) listed |= 1 << i;
        }
        return listed & ~primaryMask(mounts);
    }

    public static int groupMask(Mount[] mounts, FireTrigger trigger) {
        return trigger == FireTrigger.LEFT ? primaryMask(mounts) : secondaryMask(mounts);
    }

    /** The button's cycle in order: LINKED, then each mount of a group of two or more. Empty when it has no group. */
    public static int[] members(Mount[] mounts, FireTrigger trigger) {
        int group = groupMask(mounts, trigger);
        if (group == 0) return new int[0];
        int count = Integer.bitCount(group);
        if (count == 1) return new int[]{LINKED};
        int[] members = new int[count + 1];
        int next = 1;
        for (int index = 0; index < Integer.SIZE && next <= count; index++) {
            if ((group & (1 << index)) != 0) members[next++] = index + 1;
        }
        return members;
    }

    /** Whether {@code pick} is one of the button's members. */
    public static boolean isMember(Mount[] mounts, FireTrigger trigger, int pick) {
        for (int member : members(mounts, trigger)) if (member == pick) return true;
        return false;
    }

    /** The member after {@code pick}, wrapping to LINKED; LINKED when {@code pick} is no longer a member. */
    public static int next(Mount[] mounts, FireTrigger trigger, int pick) {
        int[] members = members(mounts, trigger);
        for (int i = 0; i < members.length; i++) {
            if (members[i] == pick) return members[(i + 1) % members.length];
        }
        return LINKED;
    }

    /** The member a number key jumps to ({@code index} 0 is key 1), or -1 past the end of the list. */
    public static int member(Mount[] mounts, FireTrigger trigger, int index) {
        int[] members = members(mounts, trigger);
        return index >= 0 && index < members.length ? members[index] : -1;
    }

    /** What the button fires on {@code pick}: its group on LINKED, one of its mounts, or nothing. */
    public static int mask(Mount[] mounts, FireTrigger trigger, int pick) {
        int group = groupMask(mounts, trigger);
        if (pick == LINKED) return group;
        int index = pick - 1;
        if (index < 0 || index >= Integer.SIZE) return 0;
        return group & (1 << index);
    }

    /** Whether the mount at {@code index} is in {@code mask}. */
    public static boolean contains(int mask, int index) {
        return index >= 0 && index < Integer.SIZE && (mask & (1 << index)) != 0;
    }

    /** HUD badge for a mount: the button that fires it under the current picks, or empty when it holds. */
    public static String badge(Mount[] mounts, TriggerPicks picks, int index) {
        return (contains(picks.leftMask(mounts), index) ? "L" : "")
                + (contains(picks.rightMask(mounts), index) ? "R" : "");
    }

    /** The button whose group a mount is in, {@code L} or {@code R}, picked or not; empty when neither. */
    public static String group(Mount[] mounts, int index) {
        return contains(primaryMask(mounts), index) ? "L" : contains(secondaryMask(mounts), index) ? "R" : "";
    }
}
