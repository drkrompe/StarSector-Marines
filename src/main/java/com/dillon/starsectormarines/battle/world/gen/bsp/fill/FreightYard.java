package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.vehicle.TurnAwareCorridor;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
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
 * The freight yard set piece ({@code set-piece-blocks.md}): a walled compound holding a warehouse
 * whose loading door faces into a yard, a vehicle gate opposite it onto a street, a striped loading
 * bay between the two where a hauler stands nose to the gate, freight stacked along the yard walls
 * as cover facing the gate, and a narrow side gap for people.
 *
 * <p>One authored layout in its own local frame, turned whole to one of four poses: the gate is the
 * local bottom edge ({@code y = 0}) and opens onto the street outside it, the warehouse fills the
 * local top. Placement refuses before it changes anything; a compound that cannot hold the yard
 * keeps its ordinary fill. Selection draws nothing from the layout random stream.
 */
public final class FreightYard {

    /** Local width across the gate. */
    public static final int WIDTH = 14;
    /** Local depth, gate to back wall. */
    public static final int DEPTH = 18;
    /** Rows of yard between the gate wall and the warehouse. */
    public static final int YARD = 9;
    /** Width of the vehicle gate, wide enough to drive a hauler straight out. */
    public static final int GATE = 4;
    /** Width of the side gap people use. */
    static final int GAP = 2;
    /** First row of the side gap, in the yard's west wall. */
    static final int GAP_Y = 4;
    /** Local row the hauler's middle stands on, its tail toward the warehouse door. */
    public static final int STAND_Y = YARD - 4;
    /** Rows the freight stacks along each yard wall stand on, two-deep, with gaps between to move through. */
    static final int[] STACK_ROWS = {1, 6};
    /** Rows outside the gate where a hauler leaving must be able to turn either way. */
    static final int TURN_OUT = 4;
    private static final int WALL_HP = 80;
    private static final BuildingShellCore.BuildingConfig HALL = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, "WAREHOUSE", PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.INDUSTRIAL);

    /** Where a yard stands: its world corner (minimum x and y) and how many quarter turns it was given. */
    public record Pose(int left, int bottom, int turn) {
        public int width() { return (turn & 1) == 0 ? WIDTH : DEPTH; }
        public int height() { return (turn & 1) == 0 ? DEPTH : WIDTH; }

        /** World cell of local {@code (x, y)}. */
        public int[] cell(int x, int y) {
            return switch (turn & 3) {
                case 0 -> new int[]{left + x, bottom + y};
                case 1 -> new int[]{left + DEPTH - 1 - y, bottom + x};
                case 2 -> new int[]{left + WIDTH - 1 - x, bottom + DEPTH - 1 - y};
                default -> new int[]{left + y, bottom + WIDTH - 1 - x};
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

    private FreightYard() { }

    /**
     * Whether local {@code (x, y)} holds stacked freight: two-deep runs along both yard walls, outside the
     * drive, broken where people move between them and kept clear of the side gap.
     */
    static boolean stack(int x, int y) {
        if (x != 1 && x != 2 && x != WIDTH - 3 && x != WIDTH - 2) return false;
        boolean run = false;
        for (int row : STACK_ROWS) run |= y == row || y == row + 1;
        if (!run) return false;
        return !(x <= 2 && y >= GAP_Y - 1 && y <= GAP_Y + GAP);
    }

    private static boolean isFirstRow(int y) {
        for (int row : STACK_ROWS) if (y == row) return true;
        return false;
    }

    static boolean onGate(int x) { return x >= WIDTH / 2 - GATE / 2 && x < WIDTH / 2 + GATE / 2; }

    /**
     * The best pose inside the compound, or null. Every footprint cell must be the compound's own
     * ground ({@code owned}: a member leaf, or a street frame between members that belongs to
     * nobody) and not reserved for the road; the gate must open onto street a vehicle can reach.
     * The gate facing the most reserved road wins, then the most street, then the first found.
     */
    public static Pose choose(GenContext c, Compound compound, BiPredicate<Integer, Integer> owned) {
        boolean[][] reserved = c.get(BspKeys.ROAD_RESERVATION);
        boolean[][] road = c.get(BspKeys.ROAD_CELLS);
        Pose best = null;
        int bestScore = 0;
        for (int turn = 0; turn < 4; turn++) {
            int w = (turn & 1) == 0 ? WIDTH : DEPTH, h = (turn & 1) == 0 ? DEPTH : WIDTH;
            for (int bottom = compound.top - 1; bottom + h - 1 <= compound.bottom + 1; bottom++) {
                for (int left = compound.left - 1; left + w - 1 <= compound.right + 1; left++) {
                    Pose pose = new Pose(left, bottom, turn);
                    int score = score(c, pose, owned, reserved, road);
                    if (score > bestScore) {
                        bestScore = score;
                        best = pose;
                    }
                }
            }
        }
        return best;
    }

    /** How well the gate meets the street, or 0 where the yard may not stand there. Read-only. */
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
        // A hauler leaving by the gate turns into the street outside it, so the street has to hold one turning.
        int[] l = pose.cell(WIDTH / 2 - 1, -TURN_OUT), r = pose.cell(WIDTH / 2, -TURN_OUT);
        if (!c.grid.inBounds(l[0], l[1]) || !c.grid.inBounds(r[0], r[1])) return 0;
        if (!TurnAwareCorridor.canSetOutFrom((l[0] + r[0]) / 2f + .5f, (l[1] + r[1]) / 2f + .5f,
                VehicleType.CARGO_CARRIER, c.grid)) return 0;
        int score = 1;
        for (int x = 0; x < WIDTH; x++) {
            if (!onGate(x)) continue;
            for (int out = 1; out <= 2; out++) {
                int[] cell = pose.cell(x, -out);
                if (!c.grid.inBounds(cell[0], cell[1]) || !c.grid.isWalkable(cell[0], cell[1])) return 0;
            }
            int[] mouth = pose.cell(x, -1);
            if (road == null || !road[mouth[0]][mouth[1]]) return 0;
            score += reserved != null && reserved[mouth[0]][mouth[1]] ? 4 : 1;
        }
        return score;
    }

    /** Lays the yard at {@code pose}, which {@link #choose} returned. Publishes its warehouse and its bay. */
    public static LoadingBay place(GenContext c, Pose pose) {
        TileRegistry registry = TileRegistry.installed();
        int right = pose.left + pose.width() - 1, top = pose.bottom + pose.height() - 1;
        c.doodads.removeIf(d -> d.cellX <= right && d.cellX + d.footprintCellsX - 1 >= pose.left
                && d.cellY <= top && d.cellY + d.footprintCellsY - 1 >= pose.bottom);
        for (int y = 0; y < DEPTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int[] cell = pose.cell(x, y);
                c.grid.setWalkableFloor(cell[0], cell[1]);
                c.grid.openAllEdges(cell[0], cell[1]);
                c.topology.setFixture(cell[0], cell[1], false);
                c.topology.setGroundKind(cell[0], cell[1], y < YARD ? GroundKind.DIRT : GroundKind.INDOOR);
                c.markMadeGround(cell[0], cell[1]);
            }
        }
        // The wall round the whole compound, broken by the vehicle gate and the side gap.
        for (int y = 0; y < DEPTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean ring = x == 0 || x == WIDTH - 1 || y == 0 || y == DEPTH - 1;
                if (!ring) continue;
                int[] cell = pose.cell(x, y);
                if ((y == 0 && onGate(x)) || (x == 0 && y >= GAP_Y && y < GAP_Y + GAP)) {
                    c.topology.setGroundKind(cell[0], cell[1], GroundKind.STRIPED);
                    continue;
                }
                c.grid.setWalkable(cell[0], cell[1], false);
                c.grid.setWallHp(cell[0], cell[1], WALL_HP);
                c.topology.setGroundKind(cell[0], cell[1], GroundKind.INDOOR);
            }
        }
        // The bay and its drive: striped from the gate to where the hauler's tail stands.
        for (int y = 1; y < YARD; y++) {
            for (int x = WIDTH / 2 - GATE / 2; x < WIDTH / 2 + GATE / 2; x++) {
                int[] cell = pose.cell(x, y);
                c.topology.setGroundKind(cell[0], cell[1], GroundKind.STRIPED);
            }
        }
        // Freight along both yard walls, outside the drive: cover facing the gate.
        DoodadDef crates = registry.doodad("doodad.industrial-crate-stack");
        DoodadDef pallets = registry.doodad("doodad.industrial-pallet-stack");
        for (int y = 1; y < YARD; y++) {
            for (int x = 1; x < WIDTH - 1; x++) {
                if (stack(x, y)) solid(c, pose.cell(x, y), isFirstRow(y) ? crates : pallets);
            }
        }

        BuildingPlacement.Side frontage = side(pose.step(0, -1));
        int[] a = pose.cell(1, YARD), b = pose.cell(WIDTH - 2, DEPTH - 2);
        BlockLeaf hall = new BlockLeaf(Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), false);
        PointOfInterest poi = BuildingShellCore.carve(hall, c.grid, c.topology, c.doodads, c.rng, HALL,
                BuildingPlacement.frontageOnly(frontage));
        if (poi != null) c.pois.add(poi);

        // The hauler stands on the bay's centre line, between its two middle cells.
        int[] l = pose.cell(WIDTH / 2 - 1, STAND_Y), r = pose.cell(WIDTH / 2, STAND_Y);
        int[] nose = pose.step(0, -1);
        float heading = (float) Math.toDegrees(Math.atan2(nose[1], nose[0]));
        int[] door = door(c, pose);
        int[] gate = pose.cell(WIDTH / 2, -1);
        int[] side = pose.cell(-1, GAP_Y);
        LoadingBay bay = new LoadingBay((l[0] + r[0]) / 2f + .5f, (l[1] + r[1]) / 2f + .5f,
                heading, door[0], door[1], gate[0], gate[1], side[0], side[1]);
        c.loadingBays.add(bay);
        return bay;
    }

    /** The yard cell in front of the warehouse's loading door nearest the bay, or the bay's own end where none was cut. */
    private static int[] door(GenContext c, Pose pose) {
        int[] best = pose.cell(WIDTH / 2, YARD - 1);
        int bestOffset = Integer.MAX_VALUE;
        for (int y = YARD; y <= YARD + 2; y++) {
            for (int x = 1; x < WIDTH - 1; x++) {
                int[] cell = pose.cell(x, y);
                if (!c.grid.isDoorway(cell[0], cell[1])) continue;
                int offset = Math.abs(x - WIDTH / 2) + (y - YARD);
                if (offset >= bestOffset) continue;
                bestOffset = offset;
                best = pose.cell(x, YARD - 1);
            }
        }
        return best;
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
