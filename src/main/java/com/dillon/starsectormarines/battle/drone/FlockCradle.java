package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * A flock's cradle (`derelict-drone-flocks.md`, slice 4): the launch housing at the flock's home that rebuilds its
 * lost drones, one at a time and slowly, while the cradle stands, its control node stands, and the flock is home
 * to take them. It is the drone hub's structure ({@link UnitType#DRONE_HUB_STRUCTURE}, so it is targeted, armoured and
 * demolished as a hub is) with no hub behaviour of its own: the flock, not the cradle, decides where its drones fly.
 *
 * <p>Destroying the cradle stops the rebuild; the drones already up fly on. So does losing the zone's node, which the
 * network reports through {@link Flock#nodeStands(boolean)}.
 */
public final class FlockCradle {

    public static final float MAX_STRUCTURE = 120f;
    public static final float ARMOR_CAPACITY = 140f;
    public static final float ARMOR_RATING = 12f;
    /** Seconds to build one drone. Slow: a flock worn down in a fight comes back over minutes, not seconds. UNTUNED. */
    public static final float REBUILD_SECONDS = 20f;
    /** The flock is home, and may take a rebuilt drone, while its anchor is within this many cells of its cradle. */
    public static final float HOME_CELLS = 3f;

    private FlockCradle() {}

    /** The cradle's structure at {@code (cellX, cellY)}. */
    public static EntitySpec create(String id, Faction side, int cellX, int cellY) {
        return new EntitySpec(id, side, UnitType.DRONE_HUB_STRUCTURE, cellX, cellY)
                .health(MAX_STRUCTURE)
                .armor(ARMOR_CAPACITY, ARMOR_RATING)
                .attackDamage(0f)
                .attackRange(0f)
                .attackCooldown(1f)
                .accuracy(0f)
                .moveSpeed(0f)
                .role(UnitRole.STRUCTURE);
    }

    /** Cells either side of a cradle's own that its drawn housing covers, so must be open ground (the carousel's). */
    public static final int CLEARANCE_CELLS = 1;

    /**
     * Where a flock's cradle stands (`derelict-drone-flocks.md`, slice 7): the cell nearest {@code (homeX, homeY)},
     * within {@code reach} cells by the open ground, whose square of {@link #CLEARANCE_CELLS} round it is all open. A
     * flock homed at a doorway's threshold so keeps its cradle set back in the open: the housing never covers the door
     * or reaches into a wall or under a roof, and the entrance stays clear. A cradle never seals its cell, so ground
     * units pass it either way. Home itself where no such cell is in reach; {@code null} only when home is not open.
     */
    public static int[] site(int homeX, int homeY, FlockField.Open open, int reach) {
        if (!open.open(homeX, homeY)) return null;
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        frontier.add(new int[]{homeX, homeY, 0});
        seen.add(key(homeX, homeY));
        while (!frontier.isEmpty()) {
            int[] c = frontier.poll();
            if (clear(c[0], c[1], open)) return new int[]{c[0], c[1]};
            if (c[2] >= reach) continue;
            for (int[] d : STEPS) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (!open.open(nx, ny) || !seen.add(key(nx, ny))) continue;
                frontier.add(new int[]{nx, ny, c[2] + 1});
            }
        }
        return new int[]{homeX, homeY};
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static boolean clear(int x, int y, FlockField.Open open) {
        for (int dx = -CLEARANCE_CELLS; dx <= CLEARANCE_CELLS; dx++) {
            for (int dy = -CLEARANCE_CELLS; dy <= CLEARANCE_CELLS; dy++) {
                if (!open.open(x + dx, y + dy)) return false;
            }
        }
        return true;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    /**
     * A cradle's build clock: one drone every {@link #REBUILD_SECONDS} while it may build. Time spent unable to build
     * (cradle or node down, flock away, flock whole) does not count toward the next drone.
     */
    public static final class Clock {
        private float seconds;

        /** Advances by {@code dt}; true when a drone is ready to join the flock this tick. */
        public boolean advance(float dt, boolean cradleStands, boolean nodeStands, boolean home, int size,
                               int strength) {
            if (!cradleStands || !nodeStands || !home || size >= strength) {
                seconds = 0f;
                return false;
            }
            seconds += dt;
            if (seconds < REBUILD_SECONDS) return false;
            seconds -= REBUILD_SECONDS;
            return true;
        }
    }
}
