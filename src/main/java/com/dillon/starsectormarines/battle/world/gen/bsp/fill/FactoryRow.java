package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.ServiceLane;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.function.BiPredicate;

/**
 * The factory row set piece ({@code set-piece-blocks.md}): two or three workshops side by side along a
 * private service lane. The lane runs the length of the row with a vehicle gate at each end onto the
 * street; the workshops' loading doors open onto it and their blank backs face the street. Across the
 * lane, machinery, pipe runs and a parked utility vehicle at each end stand against the far wall,
 * leaving pockets of cover between them and the lane itself clear from gate to gate.
 *
 * <p>One authored layout in its own local frame, turned whole to one of four poses: the lane runs along
 * local x with its gates at {@code x = 0} and {@code x = length - 1}, the workshops' backs are the local
 * bottom ({@code y = 0}) and the far wall the local top. The row comes in a few authored lengths; among the
 * poses whose gates meet the street best it takes the longest. Placement refuses before it changes anything;
 * a compound that cannot hold the row keeps its ordinary fill. Selection draws nothing from the layout
 * random stream.
 */
public final class FactoryRow {

    /** The authored lengths along the lane, longest first; the longer rows hold three workshops. */
    public static final int[] LENGTHS = {20, 18, 16, 14};
    /** Local depth, workshop backs to the far wall. */
    public static final int DEPTH = 13;
    /** Rows of workshop, from their blank backs to their loading doors. */
    public static final int HALL = 6;
    /** Clear width of the lane, which is also the width of each gate. */
    public static final int LANE = 4;
    /** First row of the pockets across the lane; the far wall stands on the row after the second. */
    public static final int POCKET = HALL + LANE;
    /** Local row of the gate cells and the lane's centre line: the second of the lane's two middle rows. */
    public static final int CENTRE = HALL + LANE / 2;
    /** Rows of a row's length the parked vehicle at each end stands on, counted in from the end wall. */
    static final int VEHICLE_FROM = 2, VEHICLE_LENGTH = 3;
    /** Rows outside each gate that must be open ground, the first of them street. */
    static final int GATE_OUT = 4;
    private static final int WALL_HP = 80;
    private static final String VEHICLE = "doodad.parked-utility-truck";
    private static final BuildingShellCore.BuildingConfig WORKSHOP = BuildingIndustrialFiller.CONFIG;
    private static final BuildingShellCore.BuildingConfig STORE = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, "WAREHOUSE", PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.INDUSTRIAL);

    /** Where a row stands: its world corner (minimum x and y), its quarter turns and its length. */
    public record Pose(int left, int bottom, int turn, int length) {
        public int width() { return (turn & 1) == 0 ? length : DEPTH; }
        public int height() { return (turn & 1) == 0 ? DEPTH : length; }

        /** World cell of local {@code (x, y)}. */
        public int[] cell(int x, int y) {
            return switch (turn & 3) {
                case 0 -> new int[]{left + x, bottom + y};
                case 1 -> new int[]{left + DEPTH - 1 - y, bottom + x};
                case 2 -> new int[]{left + length - 1 - x, bottom + DEPTH - 1 - y};
                default -> new int[]{left + y, bottom + length - 1 - x};
            };
        }

        /** World direction of a local step {@code (dx, dy)}. */
        public int[] step(int dx, int dy) {
            return switch (turn & 3) {
                case 0 -> new int[]{dx, dy};
                case 1 -> new int[]{-dy, dx};
                case 2 -> new int[]{-dx, -dy};
                default -> new int[]{dy, -dx};
            };
        }
    }

    private FactoryRow() { }

    /**
     * The workshops' spans along the lane, {@code {first x, last x}} each, over the whole length. Neighbours
     * share their party wall, so each span starts on the last cell of the one before.
     */
    static int[][] halls(int length) {
        int count = length >= 18 ? 3 : 2;
        int[][] spans = new int[count][];
        int cells = length + count - 1;
        int base = cells / count, extra = cells % count, x = 0;
        for (int i = 0; i < count; i++) {
            // The odd cells go to the middle workshop, or the last of two.
            int w = base + (i == count / 2 ? extra : 0);
            spans[i] = new int[]{x, x + w - 1};
            x += w - 1;
        }
        return spans;
    }

    /** Whether a {@code w} by {@code h} rectangle holds the shortest row in some pose. */
    public static boolean fits(int w, int h) {
        int shortest = LENGTHS[LENGTHS.length - 1];
        return (w >= shortest && h >= DEPTH) || (w >= DEPTH && h >= shortest);
    }

    /** Whether local {@code (x, y)} is in a gate: the lane's rows in either end wall. */
    static boolean gate(int length, int x, int y) {
        return (x == 0 || x == length - 1) && y >= HALL && y < POCKET;
    }

    /** Whether local {@code (x, y)} is the far wall or an end wall beside the pockets. */
    static boolean wall(int length, int x, int y) {
        return y >= POCKET && (y == DEPTH - 1 || x == 0 || x == length - 1);
    }

    /** Whether local {@code (x, y)} lies under the parked vehicle at either end of the pockets. */
    static boolean vehicle(int length, int x, int y) {
        if (y < POCKET || y > POCKET + 1) return false;
        int in = Math.min(x, length - 1 - x);
        return in >= VEHICLE_FROM && in < VEHICLE_FROM + VEHICLE_LENGTH;
    }

    /**
     * The single-cell prop at local {@code (x, y)} across the lane, or null for open ground: a generator
     * against each end wall, the vehicle, a clear pocket beyond it, then pipe runs along the far wall to the
     * middle with a drum cluster stood out before every third length of pipe. Mirrored end to end, so the
     * lane reads the same from either gate.
     */
    static String pocket(int length, int x, int y) {
        if (y < POCKET || y > POCKET + 1 || x <= 0 || x >= length - 1 || vehicle(length, x, y)) return null;
        int in = Math.min(x, length - 1 - x);
        boolean back = y == POCKET + 1;
        if (in == 1) return back ? "doodad.industrial-generator" : null;
        if (in <= VEHICLE_FROM + VEHICLE_LENGTH) return null;
        if (back) return "doodad.industrial-pipe-bundle";
        return (in - VEHICLE_FROM - VEHICLE_LENGTH) % 3 == 2 ? "doodad.industrial-drum-cluster" : null;
    }

    /**
     * The best pose inside the compound, or null. Every footprint cell must be the compound's own ground
     * ({@code owned}: a member leaf, or a street frame that belongs to nobody) and not reserved for the
     * road, and both gates must open onto street. The gates facing the most reserved road win, with the most
     * street behind the workshops' backs, then the longest row, then the first found.
     */
    public static Pose choose(GenContext c, Compound compound, BiPredicate<Integer, Integer> owned) {
        return choose(c, compound.left, compound.top, compound.right, compound.bottom, owned);
    }

    /**
     * The best pose over the ground {@code left..right, top..bottom} and the street frame one cell round it, or
     * null, under the rules of {@link #choose(GenContext, Compound, BiPredicate)}.
     */
    public static Pose choose(GenContext c, int left, int top, int right, int bottom,
                              BiPredicate<Integer, Integer> owned) {
        boolean[][] reserved = c.get(BspKeys.ROAD_RESERVATION);
        boolean[][] road = c.get(BspKeys.ROAD_CELLS);
        if (road == null) return null;
        Pose best = null;
        int bestScore = 0;
        for (int length : LENGTHS) {
            for (int turn = 0; turn < 4; turn++) {
                int w = (turn & 1) == 0 ? length : DEPTH, h = (turn & 1) == 0 ? DEPTH : length;
                for (int y = top - 1; y + h - 1 <= bottom + 1; y++) {
                    for (int x = left - 1; x + w - 1 <= right + 1; x++) {
                        Pose pose = new Pose(x, y, turn, length);
                        int score = score(c, pose, owned, reserved, road) * 100 + length;
                        if (score > 100 && score > bestScore) {
                            bestScore = score;
                            best = pose;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** How well the gates and backs meet the street, or 0 where the row may not stand there. Read-only. */
    static int score(GenContext c, Pose pose, BiPredicate<Integer, Integer> owned,
                     boolean[][] reserved, boolean[][] road) {
        int right = pose.left + pose.width() - 1, top = pose.bottom + pose.height() - 1;
        if (pose.left < 1 || pose.bottom < 1 || right >= c.width - 1 || top >= c.height - 1) return 0;
        for (int y = pose.bottom; y <= top; y++) {
            for (int x = pose.left; x <= right; x++) {
                if (!owned.test(x, y)) return 0;
                if (reserved != null && reserved[x][y]) return 0;
            }
        }
        for (LandingPad pad : c.landingPads) {
            if (pose.left <= pad.right() && right >= pad.left() && pose.bottom <= pad.top() && top >= pad.bottom())
                return 0;
        }
        int score = 1;
        for (int y = HALL; y < POCKET; y++) {
            for (int end = 0; end < 2; end++) {
                int x = end == 0 ? -1 : pose.length;
                int dx = end == 0 ? -1 : 1;
                for (int out = 0; out < GATE_OUT; out++) {
                    int[] cell = pose.cell(x + dx * out, y);
                    if (!c.grid.inBounds(cell[0], cell[1]) || !c.grid.isWalkable(cell[0], cell[1])) return 0;
                }
                int[] mouth = pose.cell(x, y);
                if (!road[mouth[0]][mouth[1]]) return 0;
                score += reserved != null && reserved[mouth[0]][mouth[1]] ? 4 : 1;
            }
        }
        for (int x = 0; x < pose.length; x++) {
            int[] behind = pose.cell(x, -1);
            if (c.grid.inBounds(behind[0], behind[1]) && road[behind[0]][behind[1]]) score++;
        }
        return score;
    }

    /** Lays the row at {@code pose}, which {@link #choose} returned. Publishes its workshops and its lane. */
    public static ServiceLane place(GenContext c, Pose pose) {
        TileRegistry registry = TileRegistry.installed();
        int length = pose.length;
        int right = pose.left + pose.width() - 1, top = pose.bottom + pose.height() - 1;
        c.doodads.removeIf(d -> d.cellX <= right && d.cellX + d.footprintCellsX - 1 >= pose.left
                && d.cellY <= top && d.cellY + d.footprintCellsY - 1 >= pose.bottom);
        for (int y = 0; y < DEPTH; y++) {
            for (int x = 0; x < length; x++) {
                int[] cell = pose.cell(x, y);
                c.grid.setWalkableFloor(cell[0], cell[1]);
                c.grid.openAllEdges(cell[0], cell[1]);
                c.topology.setFixture(cell[0], cell[1], false);
                c.topology.setGroundKind(cell[0], cell[1], y < HALL ? GroundKind.INDOOR
                        : gate(length, x, y) ? GroundKind.STRIPED : GroundKind.COURTYARD);
                c.markMadeGround(cell[0], cell[1]);
                if (!wall(length, x, y)) continue;
                c.grid.setWalkable(cell[0], cell[1], false);
                c.grid.setWallHp(cell[0], cell[1], WALL_HP);
                c.topology.setGroundKind(cell[0], cell[1], GroundKind.INDOOR);
            }
        }

        // The workshops, their loading doors onto the lane and every other wall blank.
        BuildingPlacement front = BuildingPlacement.frontOnly(side(pose.step(0, 1)));
        int[][] halls = halls(length);
        for (int i = 0; i < halls.length; i++) {
            int[] a = pose.cell(halls[i][0], 0), b = pose.cell(halls[i][1], HALL - 1);
            BlockLeaf hall = new BlockLeaf(Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), false);
            boolean middle = halls.length == 3 && i == 1;
            PointOfInterest poi = BuildingShellCore.carve(hall, c.grid, c.topology, c.doodads, c.rng,
                    middle ? STORE : WORKSHOP, front);
            if (poi != null) c.pois.add(poi);
        }
        // A striped apron on the lane in front of each loading door.
        for (int x = 1; x < length - 1; x++) {
            int[] door = pose.cell(x, HALL - 1);
            if (!c.grid.isDoorway(door[0], door[1])) continue;
            int[] apron = pose.cell(x, HALL);
            c.topology.setGroundKind(apron[0], apron[1], GroundKind.STRIPED);
        }

        // Across the lane: machinery and pipe runs against the far wall, a parked vehicle at each end.
        for (int y = POCKET; y <= POCKET + 1; y++) {
            for (int x = 1; x < length - 1; x++) {
                String id = pocket(length, x, y);
                if (id != null) solid(c, pose.cell(x, y), registry.doodad(id));
            }
        }
        DoodadDef truck = registry.doodad(VEHICLE);
        if (truck != null) {
            parkVehicle(c, pose, VEHICLE_FROM, truck);
            parkVehicle(c, pose, length - VEHICLE_FROM - VEHICLE_LENGTH, truck);
        }

        int[] gateA = pose.cell(0, CENTRE), gateB = pose.cell(length - 1, CENTRE);
        int[] a0 = pose.cell(0, CENTRE - 1), b0 = pose.cell(length - 1, CENTRE - 1);
        ServiceLane lane = new ServiceLane(gateA[0], gateA[1], gateB[0], gateB[1],
                (gateA[0] + a0[0]) / 2f + .5f, (gateA[1] + a0[1]) / 2f + .5f,
                (gateB[0] + b0[0]) / 2f + .5f, (gateB[1] + b0[1]) / 2f + .5f, LANE);
        c.serviceLanes.add(lane);
        return lane;
    }

    /** Parks the utility vehicle across the two pocket rows from local {@code x}, lying along the lane. */
    private static void parkVehicle(GenContext c, Pose pose, int x, DoodadDef def) {
        int[] a = pose.cell(x, POCKET), b = pose.cell(x + VEHICLE_LENGTH - 1, POCKET + 1);
        int minX = Math.min(a[0], b[0]), minY = Math.min(a[1], b[1]);
        Doodad prop = new Doodad(minX, minY, def, pose.turn & 1);
        for (int yy = minY; yy < minY + prop.footprintCellsY; yy++) {
            for (int xx = minX; xx < minX + prop.footprintCellsX; xx++) {
                c.grid.setWalkable(xx, yy, false);
                c.grid.setSeeThrough(xx, yy, true);
                c.topology.setFixture(xx, yy, true);
            }
        }
        c.doodads.add(prop);
    }

    private static void solid(GenContext c, int[] cell, DoodadDef def) {
        if (def == null) return;
        c.grid.setWalkable(cell[0], cell[1], false);
        c.grid.setSeeThrough(cell[0], cell[1], true);
        c.topology.setFixture(cell[0], cell[1], true);
        c.doodads.add(new Doodad(cell[0], cell[1], def));
    }

    private static BuildingPlacement.Side side(int[] step) {
        if (step[0] < 0) return BuildingPlacement.Side.LEFT;
        if (step[0] > 0) return BuildingPlacement.Side.RIGHT;
        return step[1] < 0 ? BuildingPlacement.Side.TOP : BuildingPlacement.Side.BOTTOM;
    }
}
