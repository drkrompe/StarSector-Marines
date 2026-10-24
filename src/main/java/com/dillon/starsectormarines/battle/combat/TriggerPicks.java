package com.dillon.starsectormarines.battle.combat;

/**
 * What each of a controlled machine's two triggers is on: {@link FireGroups#LINKED} or one mount
 * ({@code index + 1}) of that trigger's group. Mechs and vehicles share it.
 */
public record TriggerPicks(int left, int right) {
    /** Both triggers on LINKED: what a pilot or a driver starts with. */
    public static final TriggerPicks LINKED = new TriggerPicks(FireGroups.LINKED, FireGroups.LINKED);

    public int pick(FireTrigger trigger) { return trigger == FireTrigger.LEFT ? left : right; }

    public TriggerPicks with(FireTrigger trigger, int pick) {
        return trigger == FireTrigger.LEFT ? new TriggerPicks(pick, right) : new TriggerPicks(left, pick);
    }

    /** The mounts the left button fires. */
    public int leftMask(FireGroups.Mount[] mounts) { return FireGroups.mask(mounts, FireTrigger.LEFT, left); }

    /** The mounts the right button fires. */
    public int rightMask(FireGroups.Mount[] mounts) { return FireGroups.mask(mounts, FireTrigger.RIGHT, right); }

    /** Everything either button fires; the rest holds. */
    public int firedMask(FireGroups.Mount[] mounts) { return leftMask(mounts) | rightMask(mounts); }

    /** What the buttons fire while held: each button's pick, only while that button is down. */
    public int heldMask(FireGroups.Mount[] mounts, boolean primary, boolean secondary) {
        return (primary ? leftMask(mounts) : 0) | (secondary ? rightMask(mounts) : 0);
    }
}
