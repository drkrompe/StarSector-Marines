package com.dillon.starsectormarines.battle.assault;

import java.util.ArrayList;
import java.util.List;

/**
 * One machine's standing guard over one garrisonable its own side mans ({@link MachineGuards}): the
 * cell it stands on beside the place, and the threat axis that cell was chosen to cover. Written in
 * serial phases (the order systems' command-boundary pass and {@link MachineGuards#tickVehicles}),
 * except that a mech's own unit update records a cell it could not route to; the turret pass reads it.
 *
 * <p>A guard names no target. The machine's own doctrine and turret pick what to engage; the order
 * owns only where the machine stands and which way it faces while nothing is in its sights.
 */
public final class MachineGuard {

    private final AssaultTarget target;
    private int guardX;
    private int guardY;
    private float axisX;
    private float axisY;
    int lastMannedTick;
    int lastReviewTick;
    int routeFailures;
    private final List<int[]> unreachable = new ArrayList<>();
    private boolean chooseAgain;

    public MachineGuard(AssaultTarget target, int guardX, int guardY, float axisX, float axisY, int tick) {
        this.target = target;
        this.guardX = guardX;
        this.guardY = guardY;
        this.axisX = axisX;
        this.axisY = axisY;
        this.lastMannedTick = tick;
        this.lastReviewTick = tick;
    }

    /** The garrisonable guarded. */
    public AssaultTarget target() { return target; }

    public int guardX() { return guardX; }

    public int guardY() { return guardY; }

    /** The unit threat axis, from the place's centre toward where the side believes the threat is. */
    public float axisX() { return axisX; }

    public float axisY() { return axisY; }

    /** The last tick the side was believed to man the place (its own people in it, or sent to it). */
    public int lastMannedTick() { return lastMannedTick; }

    /** Moves the guard to a cell chosen again for a threat axis read again. */
    public void moveGuard(int x, int y, float newAxisX, float newAxisY) {
        guardX = x;
        guardY = y;
        axisX = newAxisX;
        axisY = newAxisY;
    }

    /**
     * Records that the machine could not route to its guard cell: the cell is set aside and a new one is to be chosen
     * on the next serial pass. Answers how many cells have failed; the order gives up after a few.
     */
    public int noteRouteFailure() {
        unreachable.add(new int[] {guardX, guardY});
        chooseAgain = true;
        return ++routeFailures;
    }

    /** Whether a route failure asked for a new cell; clears the request. */
    public boolean takeChooseAgain() {
        boolean again = chooseAgain;
        chooseAgain = false;
        return again;
    }

    /** Guard cells the machine could not reach, kept clear of when one is chosen again. */
    public List<int[]> unreachable() {
        return unreachable;
    }
}
