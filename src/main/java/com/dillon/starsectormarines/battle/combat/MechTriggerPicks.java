package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.mech.MechWeaponMount;

/**
 * What each of a piloted mech's two triggers is on: {@link MechFireGroups#LINKED} or one hardpoint
 * ({@code slot.ordinal() + 1}) of that trigger's group.
 */
public record MechTriggerPicks(int left, int right) {
    /** Both triggers on LINKED: what a pilot starts with. */
    public static final MechTriggerPicks LINKED = new MechTriggerPicks(MechFireGroups.LINKED, MechFireGroups.LINKED);

    public int pick(FireTrigger trigger) { return trigger == FireTrigger.LEFT ? left : right; }

    public MechTriggerPicks with(FireTrigger trigger, int pick) {
        return trigger == FireTrigger.LEFT ? new MechTriggerPicks(pick, right) : new MechTriggerPicks(left, pick);
    }

    /** The hardpoints the left button fires. */
    public int leftMask(MechWeaponMount[] mounts) { return MechFireGroups.mask(mounts, FireTrigger.LEFT, left); }

    /** The hardpoints the right button fires. */
    public int rightMask(MechWeaponMount[] mounts) { return MechFireGroups.mask(mounts, FireTrigger.RIGHT, right); }

    /** Everything either button fires; the rest of the chassis holds. */
    public int firedMask(MechWeaponMount[] mounts) { return leftMask(mounts) | rightMask(mounts); }
}
