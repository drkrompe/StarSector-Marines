package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Building;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Cluster;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * The battery fort as an authored compound layout, in one cardinally rotatable local frame, built as defence in depth
 * from the outside in (see {@code forts.md}):
 *
 * <ul>
 *   <li>the <b>outer belt</b> ({@link FortBelt}): an irregular ring of trench, sandbag line and wire, with nests, standing
 *       off the main works across an open killing ground;</li>
 *   <li>the <b>main works</b>: a chamfered trace two cells thick with a pointed bastion at each corner, a gatehouse and
 *       baffle at each gate;</li>
 *   <li>the <b>interior</b>: a hardened fire-control bunker, a bermed magazine standing apart, a power plant with its
 *       yard, and the battery in a revetted gun pit.</li>
 * </ul>
 *
 * <p>The footprint is the whole of it, belt included; the works stand in the middle at ({@link #OX}, {@link #OY}).
 * Everything here is pure geometry in the local frame: {@code BatteryFortStage} places it on ground the city has left
 * free and rotates it as one. A fort is stamped into a city that already exists, so the works and their glacis need
 * clear ground and the belt follows whatever ground is open.
 */
public final class BatteryFort {

    /** Local width and height of the footprint, belt and wire included. */
    public static final int WIDTH = 82, HEIGHT = 72;

    /** Where the main works' own frame begins in the footprint, and its size. */
    public static final int OX = 17, OY = 17, WORKS_W = 48, WORKS_H = 36;

    private static final int CUT = 8;
    private static final int BASTION_RADIUS = 8;

    /** The cells of the wall left open as gates: the main gate on the west face, a postern on the north. */
    public static final List<int[]> GATE_CELLS = List.of(
            new int[]{OX, OY + 16}, new int[]{OX, OY + 17}, new int[]{OX, OY + 18},
            new int[]{OX + 1, OY + 16}, new int[]{OX + 1, OY + 17}, new int[]{OX + 1, OY + 18},
            new int[]{OX + 22, OY}, new int[]{OX + 23, OY}, new int[]{OX + 24, OY},
            new int[]{OX + 22, OY + 1}, new int[]{OX + 23, OY + 1}, new int[]{OX + 24, OY + 1});

    /** The open cell just outside the middle of each gate, which is where the way out to the road network begins. */
    public static final List<int[]> GATE_MOUTHS = List.of(new int[]{OX - 1, OY + 17}, new int[]{OX + 23, OY - 1});

    /** The middle of the battery platform's three-by-three footprint. */
    public static final int[] PLATFORM = {OX + 36, OY + 28};

    /** Bastion nests, one inside each corner bastion's pointed face. */
    public static final List<int[]> NESTS = List.of(
            new int[]{OX - 1, OY - 1}, new int[]{OX + WORKS_W, OY - 1},
            new int[]{OX - 1, OY + WORKS_H}, new int[]{OX + WORKS_W, OY + WORKS_H});

    /** Floors (x, y, width, height) of the three buildings, in the footprint frame. */
    private static final int[] BUNKER_FLOOR = {OX + 9, OY + 7, 7, 5};
    private static final int[] MAGAZINE_FLOOR = {OX + 36, OY + 5, 8, 6};
    private static final int[] POWER_FLOOR = {OX + 8, OY + 27, 11, 5};

    /** Paved vehicle lanes (x, y, width, height) in the footprint frame: concrete only where wheels need it. */
    public static final int[][] LANES = {
            {OX, OY + 16, 6, 3}, {OX + 4, OY + 13, 2, 9}, {OX + 4, OY + 19, 37, 3},
            {OX + 29, OY + 19, 3, 10}, {OX + 10, OY + 22, 3, 5},
            {OX + 38, OY + 11, 3, 9}, {OX + 11, OY + 12, 3, 8},
            {OX + 22, OY, 3, 5}};

    /** Open ground every placement must find round the works: the works dilated by this many cells. */
    public static final int GLACIS = 4;

    private static final boolean[][] CLEAR = new boolean[WIDTH][HEIGHT];
    private static final List<int[]> CLEAR_CELLS = new ArrayList<>();

    private static final boolean[][] INSIDE = new boolean[WIDTH][HEIGHT];
    private static final boolean[][] WALL = new boolean[WIDTH][HEIGHT];

    static {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) INSIDE[x][y] = trace(x - OX, y - OY);
        }
        // Gatehouse towers and baffles stand inside the trace as solid blocks.
        boolean[][] block = new boolean[WIDTH][HEIGHT];
        mark(block, OX + 2, OY + 13, 2, 3);
        mark(block, OX + 2, OY + 19, 2, 3);
        mark(block, OX + 6, OY + 13, 2, 7);
        mark(block, OX + 19, OY + 2, 3, 2);
        mark(block, OX + 25, OY + 2, 3, 2);
        mark(block, OX + 20, OY + 6, 7, 2);
        boolean[][] layer1 = new boolean[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (INSIDE[x][y] && (!in(x + 1, y) || !in(x - 1, y) || !in(x, y + 1) || !in(x, y - 1))) layer1[x][y] = true;
            }
        }
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (!INSIDE[x][y]) continue;
                boolean layer2 = !layer1[x][y] && (l1(layer1, x + 1, y) || l1(layer1, x - 1, y)
                        || l1(layer1, x, y + 1) || l1(layer1, x, y - 1));
                WALL[x][y] = layer1[x][y] || layer2 || block[x][y];
            }
        }
        for (int[] gate : GATE_CELLS) WALL[gate[0]][gate[1]] = false;
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                boolean near = false;
                for (int dx = -GLACIS; dx <= GLACIS && !near; dx++) {
                    for (int dy = -GLACIS; dy <= GLACIS && !near; dy++) near = in(x + dx, y + dy);
                }
                CLEAR[x][y] = near;
                if (near) CLEAR_CELLS.add(new int[]{x, y});
            }
        }
    }

    private BatteryFort() {}

    private static void mark(boolean[][] grid, int x, int y, int w, int h) {
        for (int dx = 0; dx < w; dx++) for (int dy = 0; dy < h; dy++) grid[x + dx][y + dy] = true;
    }

    private static boolean in(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && INSIDE[x][y];
    }

    private static boolean l1(boolean[][] layer, int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && layer[x][y];
    }

    /** The chamfered trace with a diamond bastion at each corner, in the works' own frame. */
    private static boolean trace(int wx, int wy) {
        boolean rect = wx >= 0 && wy >= 0 && wx < WORKS_W && wy < WORKS_H
                && wx + wy >= CUT && (WORKS_W - 1 - wx) + wy >= CUT
                && wx + (WORKS_H - 1 - wy) >= CUT && (WORKS_W - 1 - wx) + (WORKS_H - 1 - wy) >= CUT;
        if (rect) return true;
        int[][] centres = {{1, 1}, {WORKS_W - 2, 1}, {1, WORKS_H - 2}, {WORKS_W - 2, WORKS_H - 2}};
        for (int[] c : centres) {
            if (Math.abs(wx - c[0]) + Math.abs(wy - c[1]) <= BASTION_RADIUS) return true;
        }
        return false;
    }

    /** The cells that must be open ground for a fort to stand: the works and the glacis round them. */
    public static List<int[]> clearCells() {
        return CLEAR_CELLS;
    }

    /** Whether the footprint cell is part of the works or its glacis. */
    public static boolean isCleared(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && CLEAR[x][y];
    }

    /** Whether the footprint cell is within the main works, wall included. */
    public static boolean isWorks(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && INSIDE[x][y];
    }

    /** Whether the local cell is part of the works' wall, gatehouse and baffles included, and not a gate. */
    public static boolean isWall(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && WALL[x][y];
    }

    /** The layout: three buildings. Walls, belt, lanes and dressing are the host's to stamp from this frame. */
    public static CompoundLayout layout() {
        List<Building> core = new ArrayList<>();
        core.add(building(RoomPurpose.CONTROL_ROOM, BUNKER_FLOOR, new Doorway(3, 5)));
        core.add(building(RoomPurpose.ARMORY, MAGAZINE_FLOOR, new Doorway(3, 6)));
        core.add(building(RoomPurpose.ENGINE_ROOM, POWER_FLOOR, new Doorway(3, -1)));
        return new CompoundLayout(RoomShape.rectangle(WIDTH, HEIGHT), List.of(
                new Cluster("Fort buildings", core)), List.of(), List.of());
    }

    /** The floor of the building housing {@code aspect}: x, y, width, height in the footprint frame. */
    public static int[] floor(FortAspect aspect) {
        return switch (aspect) {
            case FIRE_CONTROL -> BUNKER_FLOOR.clone();
            case MAGAZINE -> MAGAZINE_FLOOR.clone();
            case POWER -> POWER_FLOOR.clone();
        };
    }

    /** The door of the building housing {@code aspect}, as the footprint cell of its wall ring. */
    public static int[] door(FortAspect aspect) {
        int[] f = floor(aspect);
        return aspect == FortAspect.POWER ? new int[]{f[0] + 3, f[1] - 1}
                : new int[]{f[0] + 3, f[1] + f[3]};
    }

    /** The middle of the three-by-three core of {@code aspect}, in the local frame. */
    public static int[] coreCentre(FortAspect aspect) {
        int[] floor = floor(aspect);
        return new int[]{floor[0] + floor[2] / 2, floor[1] + floor[3] / 2};
    }

    private static Building building(RoomPurpose purpose, int[] floor, Doorway door) {
        return new Building(purpose, RoomShape.rectangle(floor[2], floor[3]), floor[0], floor[1], List.of(door));
    }
}
