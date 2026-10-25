package com.dillon.starsectormarines.battle.evacuation;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a body of the swarm goes when it has nothing to fight, set by a director that owns it
 * ({@code set-piece-blocks.md}, Lanes and pressure). A driven body hunts as any body does; only its
 * idle wander is replaced by a walk to its goal, so prey it meets on the way still takes it.
 *
 * <p>Written only from the serial step (an objective's advance) and read from the parallel unit update,
 * so readers never see a write in progress.
 */
public final class SwarmDrives {

    private final Map<Long, int[]> goals = new HashMap<>();

    /** Sends {@code body} toward cell {@code (x, y)} whenever it is idle. */
    public void drive(long body, int x, int y) {
        int[] goal = goals.get(body);
        if (goal != null && goal[0] == x && goal[1] == y) return;
        goals.put(body, new int[]{x, y});
    }

    /** {@code body} wanders again as the swarm does. */
    public void release(long body) {
        goals.remove(body);
    }

    /** The cell {@code body} is driven to, or null when nobody drives it. */
    public int[] goal(long body) {
        return goals.get(body);
    }

    /** Whether anybody drives {@code body}. */
    public boolean driven(long body) {
        return goals.containsKey(body);
    }
}
