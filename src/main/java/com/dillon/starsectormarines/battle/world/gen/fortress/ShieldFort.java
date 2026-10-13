package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.shield.ShieldGenerator;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Building;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Cluster;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * The shield fort as an authored compound layout, in one cardinally rotatable local frame. It is a different fortress
 * from the battery fort rather than that one with the guns swapped, because it defends something different: not a
 * gun line that wants a field of fire, but a single field projector that should be reached only by a long way round.
 *
 * <ul>
 *   <li>the <b>outer belt</b> ({@link FortBelt}): an irregular ring of trench, sandbag line, wire and nests standing off
 *       the works across a glacis, as for every fort;</li>
 *   <li>the <b>main works</b>: an octagon, its four corners cut deep, with a round tower on each diagonal face instead of
 *       a pointed bastion at each corner, and <em>three</em> gates (a main gate and two posterns), each with a gatehouse
 *       and a baffle;</li>
 *   <li>the <b>inner keep</b>: a ring wall round the generator, two cells thick, open only at two diagonal gaps on
 *       opposite sides, so that getting through an outer gate puts a squad in a yard, not at the emitter;</li>
 *   <li>the <b>buildings</b>, kept to the far half of the works from the main gate: the power plant (whose loss drops the
 *       dome) and the control room (whose holder the dome obeys).</li>
 * </ul>
 *
 * <p>The footprint is the whole of it, belt included; the works stand in the middle at ({@link #OX}, {@link #OY}).
 */
public final class ShieldFort {

    /** Local width and height of the footprint, belt and wire included. */
    public static final int WIDTH = 80, HEIGHT = 80;

    /** Where the main works' own frame begins in the footprint, and its size. */
    public static final int OX = 17, OY = 17, WORKS = 46;

    private static final int CUT = 12;
    private static final int TOWER_RADIUS = 5;
    /** The works' middle cell, in the works' frame: the generator stands here. */
    public static final int CENTRE = 22;
    /** The inner keep's ring: cells whose squared distance from the generator lies in this band. */
    private static final int KEEP_INNER_SQ = 36, KEEP_OUTER_SQ = 64;

    /** Round-tower centres, one on each diagonal face, in the works' frame. */
    private static final int[][] TOWERS = {{6, 6}, {39, 6}, {6, 39}, {39, 39}};

    /** The cells of the wall left open as gates: the main gate on the south face, posterns west and east. */
    public static final List<int[]> GATE_CELLS = gateCells();

    /**
     * The gatehouse pieces: the two 3x3 towers either side of each gate and the leaves over the passage, the art turned
     * to face its gate (west as authored; north one quarter, east two, south three).
     */
    public static final List<FortPlan.Piece> GATEHOUSE = List.of(
            new FortPlan.Piece(FortPlan.PieceKind.TOWER, OX + 18, OY + 41, 3, 3, 3),
            new FortPlan.Piece(FortPlan.PieceKind.TOWER, OX + 24, OY + 41, 3, 3, 3),
            new FortPlan.Piece(FortPlan.PieceKind.GATE, OX + 21, OY + WORKS - 2, 3, 2, 3),
            new FortPlan.Piece(FortPlan.PieceKind.TOWER, OX + 2, OY + 18, 3, 3, 0),
            new FortPlan.Piece(FortPlan.PieceKind.TOWER, OX + 2, OY + 24, 3, 3, 0),
            new FortPlan.Piece(FortPlan.PieceKind.GATE, OX, OY + 21, 2, 3, 0),
            new FortPlan.Piece(FortPlan.PieceKind.TOWER, OX + WORKS - 5, OY + 18, 3, 3, 2),
            new FortPlan.Piece(FortPlan.PieceKind.TOWER, OX + WORKS - 5, OY + 24, 3, 3, 2),
            new FortPlan.Piece(FortPlan.PieceKind.GATE, OX + WORKS - 2, OY + 21, 2, 3, 2));

    /** Gates in order; the first is the main gate. */
    private static final List<FortPlan.Gate> GATES = List.of(
            new FortPlan.Gate(OX + 22, OY + WORKS, 0, 1),
            new FortPlan.Gate(OX - 1, OY + 22, -1, 0),
            new FortPlan.Gate(OX + WORKS, OY + 22, 1, 0));

    /** Nest cells, one at the middle of each round tower. */
    public static final List<int[]> NESTS = List.of(
            new int[]{OX + 6, OY + 6}, new int[]{OX + 39, OY + 6},
            new int[]{OX + 6, OY + 39}, new int[]{OX + 39, OY + 39});

    /** The shield generator's three-by-three footprint is centred here. */
    public static final int[] GENERATOR = {OX + CENTRE, OY + CENTRE};

    private static final int[] POWER_FLOOR = {OX + 13, OY + 4, 11, 5};
    private static final int[] CONTROL_FLOOR = {OX + 28, OY + 7, 7, 5};

    /** Paved vehicle lanes (x, y, width, height): from the main gate round the baffle and up to the power plant. */
    public static final int[][] LANES = {
            {OX + 21, OY + 39, 3, 8}, {OX + 10, OY + 39, 15, 2}, {OX + 10, OY + 9, 3, 32},
            {OX + 10, OY + 10, 9, 3}, {OX, OY + 21, 11, 3}, {OX + 35, OY + 21, 11, 3}, {OX + 34, OY + 9, 3, 14}};

    /** Open ground every placement must find round the works: the works dilated by this many cells. */
    public static final int GLACIS = 4;

    private static final boolean[][] INSIDE = new boolean[WIDTH][HEIGHT];
    private static final boolean[][] WALL = new boolean[WIDTH][HEIGHT];
    private static final boolean[][] CLEAR = new boolean[WIDTH][HEIGHT];
    private static final List<int[]> CLEAR_CELLS = new ArrayList<>();

    private static final int[][] BELT_VERTICES = {
            {26, 6}, {35, 4}, {46, 6}, {54, 5}, {66, 13}, {72, 22}, {75, 34}, {73, 46}, {76, 56}, {67, 67},
            {56, 73}, {46, 75}, {36, 72}, {26, 74}, {14, 66}, {6, 55}, {4, 44}, {7, 34}, {4, 24}, {12, 14}};

    private static final FortBelt.Spec BELT_SPEC = new FortBelt.Spec(WIDTH, HEIGHT, BELT_VERTICES,
            new int[]{3, 5, 9, 13, 15, 18}, OX + WORKS / 2, OY + WORKS / 2,
            List.of(new FortBelt.Lane(0, 1, OY + WORKS, OX + 22), new FortBelt.Lane(-1, 0, OX, OY + 22),
                    new FortBelt.Lane(1, 0, OX + WORKS, OY + 22)),
            ShieldFort::isCleared);

    private static final List<FortBelt.Cell> BELT;

    static {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) INSIDE[x][y] = trace(x - OX, y - OY);
        }
        // Gatehouse towers and baffles stand inside the trace as solid blocks.
        boolean[][] block = new boolean[WIDTH][HEIGHT];
        mark(block, OX + 18, OY + 41, 3, 3);
        mark(block, OX + 24, OY + 41, 3, 3);
        mark(block, OX + 19, OY + 37, 9, 2);
        mark(block, OX + 2, OY + 18, 3, 3);
        mark(block, OX + 2, OY + 24, 3, 3);
        mark(block, OX + 7, OY + 19, 2, 9);
        mark(block, OX + WORKS - 5, OY + 18, 3, 3);
        mark(block, OX + WORKS - 5, OY + 24, 3, 3);
        mark(block, OX + WORKS - 9, OY + 19, 2, 9);
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
                WALL[x][y] = layer1[x][y] || layer2 || block[x][y] || keep(x - OX, y - OY);
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
        BELT = FortBelt.build(BELT_SPEC);
    }

    private ShieldFort() {}

    private static List<int[]> gateCells() {
        List<int[]> cells = new ArrayList<>();
        for (int i = 21; i <= 23; i++) {
            cells.add(new int[]{OX + i, OY + WORKS - 1});
            cells.add(new int[]{OX + i, OY + WORKS - 2});
            cells.add(new int[]{OX, OY + i});
            cells.add(new int[]{OX + 1, OY + i});
            cells.add(new int[]{OX + WORKS - 1, OY + i});
            cells.add(new int[]{OX + WORKS - 2, OY + i});
        }
        return List.copyOf(cells);
    }

    private static void mark(boolean[][] grid, int x, int y, int w, int h) {
        for (int dx = 0; dx < w; dx++) for (int dy = 0; dy < h; dy++) grid[x + dx][y + dy] = true;
    }

    private static boolean in(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && INSIDE[x][y];
    }

    private static boolean l1(boolean[][] layer, int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && layer[x][y];
    }

    /** The octagon with a round tower on each diagonal face, in the works' own frame. */
    private static boolean trace(int wx, int wy) {
        boolean rect = wx >= 0 && wy >= 0 && wx < WORKS && wy < WORKS
                && wx + wy >= CUT && (WORKS - 1 - wx) + wy >= CUT
                && wx + (WORKS - 1 - wy) >= CUT && (WORKS - 1 - wx) + (WORKS - 1 - wy) >= CUT;
        if (rect) return true;
        for (int[] c : TOWERS) {
            int dx = wx - c[0], dy = wy - c[1];
            if (dx * dx + dy * dy <= TOWER_RADIUS * TOWER_RADIUS) return true;
        }
        return false;
    }

    /** Whether the works-frame cell is part of the inner keep's ring wall, its two diagonal gaps excepted. */
    private static boolean keep(int wx, int wy) {
        int dx = wx - CENTRE, dy = wy - CENTRE;
        int sq = dx * dx + dy * dy;
        if (sq < KEEP_INNER_SQ || sq > KEEP_OUTER_SQ) return false;
        boolean gap = Math.abs(dx - dy) <= 2 && dx * dy > 0;
        return !gap;
    }

    /** Whether the footprint cell is part of the inner keep's ring wall. */
    public static boolean isKeep(int x, int y) {
        return in(x, y) && keep(x - OX, y - OY);
    }

    private static boolean isCleared(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && CLEAR[x][y];
    }

    /** The cells that must be open ground for a fort to stand: the works and the glacis round them. */
    public static List<int[]> clearCells() {
        return CLEAR_CELLS;
    }

    /** Whether the footprint cell is within the main works, wall included. */
    public static boolean isWorks(int x, int y) {
        return in(x, y);
    }

    /** Whether the local cell is part of the wall, gatehouses, baffles and inner keep included, and not a gate. */
    public static boolean isWall(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && WALL[x][y];
    }

    /** The layout: two buildings. Walls, belt, lanes and dressing are the host's to stamp from this frame. */
    public static CompoundLayout layout() {
        List<Building> core = new ArrayList<>();
        core.add(new Building(RoomPurpose.ENGINE_ROOM, RoomShape.rectangle(POWER_FLOOR[2], POWER_FLOOR[3]),
                POWER_FLOOR[0], POWER_FLOOR[1], List.of(new Doorway(5, 5))));
        core.add(new Building(RoomPurpose.CONTROL_ROOM, RoomShape.rectangle(CONTROL_FLOOR[2], CONTROL_FLOOR[3]),
                CONTROL_FLOOR[0], CONTROL_FLOOR[1], List.of(new Doorway(3, 5))));
        return new CompoundLayout(RoomShape.rectangle(WIDTH, HEIGHT), List.of(
                new Cluster("Shield fort buildings", core)), List.of(), List.of());
    }

    /** The floor of the building housing {@code aspect}: x, y, width, height in the footprint frame. */
    public static int[] floor(FortAspect aspect) {
        return switch (aspect) {
            case POWER -> POWER_FLOOR.clone();
            case CONTROL_ROOM -> CONTROL_FLOOR.clone();
            default -> throw new IllegalArgumentException("a shield fort has no building for " + aspect);
        };
    }

    /** The door of the building housing {@code aspect}, as the footprint cell of its wall ring. */
    public static int[] door(FortAspect aspect) {
        int[] f = floor(aspect);
        return new int[]{f[0] + (aspect == FortAspect.POWER ? 5 : 3), f[1] + f[3]};
    }

    /** The middle of the three-by-three core of {@code aspect}, in the local frame. */
    public static int[] coreCentre(FortAspect aspect) {
        int[] floor = floor(aspect);
        return new int[]{floor[0] + floor[2] / 2, floor[1] + floor[3] / 2};
    }

    /** The shield fort as a {@link FortPlan}, for the stage that places every kind. */
    public static final FortPlan PLAN = new FortPlan() {
        private final List<FortAspect> aspects = List.of(FortAspect.POWER, FortAspect.CONTROL_ROOM);

        @Override public FortKind kind() { return FortKind.SHIELD_FORT; }
        @Override public int width() { return WIDTH; }
        @Override public int height() { return HEIGHT; }
        @Override public CompoundLayout layout() { return ShieldFort.layout(); }
        @Override public boolean isWorks(int x, int y) { return ShieldFort.isWorks(x, y); }
        @Override public boolean isWall(int x, int y) { return ShieldFort.isWall(x, y); }
        @Override public List<int[]> clearCells() { return CLEAR_CELLS; }
        @Override public List<Gate> gates() { return GATES; }
        @Override public List<int[]> gateCells() { return GATE_CELLS; }
        @Override public List<FortPlan.Piece> gatehouse() { return GATEHOUSE; }
        @Override public List<int[]> nests() { return NESTS; }
        @Override public int[][] lanes() { return LANES; }
        @Override public List<FortBelt.Cell> belt() { return BELT; }
        @Override public List<FortAspect> aspects() { return aspects; }
        @Override public int[] floor(FortAspect aspect) { return ShieldFort.floor(aspect); }
        @Override public int[] door(FortAspect aspect) { return ShieldFort.door(aspect); }
        @Override public int[] coreCentre(FortAspect aspect) { return ShieldFort.coreCentre(aspect); }
        @Override public String coreStructureId(FortAspect aspect) {
            return switch (aspect) {
                case POWER -> TurretCatalogRegistry.FORT_POWER_STRUCTURE_ID;
                case CONTROL_ROOM -> TurretCatalogRegistry.FORT_CONTROL_ROOM_STRUCTURE_ID;
                default -> throw new IllegalArgumentException("a shield fort has no core for " + aspect);
            };
        }
        @Override public String centrepieceStructureId() { return ShieldGenerator.STRUCTURE_ID; }
        @Override public int[] centrepiece() { return GENERATOR; }
        @Override public FortAspect centrepieceAspect() { return FortAspect.EMITTER; }
    };
}
