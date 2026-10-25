package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.vehicle.TurnAwareCorridor;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.LoadingBay;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The freight yard set piece ({@code set-piece-blocks.md}): a walled compound holding a warehouse
 * whose loading door faces into a yard, a vehicle gate opposite it onto a street, a striped loading
 * bay between the two where a hauler stands nose to the gate, freight stacked along the yard walls
 * as cover facing the gate, and a narrow side gap for people.
 *
 * <p>One authored layout in its own local frame, turned whole to one of four poses: the gates are in
 * the local bottom edge ({@code y = 0}) and open onto the street outside it, the warehouse fills the
 * local top. It comes in two {@link Size sizes}, tried largest first, so short ground gets the
 * smaller yard rather than none. Placement refuses before it changes anything; ground that holds
 * neither keeps its ordinary fill. Selection draws nothing from the layout random stream.
 */
public final class FreightYard {

    /** Width of a vehicle gate, wide enough to drive a hauler straight out. */
    public static final int GATE = 4;
    /** Width of the side gap people use. */
    static final int GAP = 2;
    /** First row of the side gap, in the yard's west wall. */
    static final int GAP_Y = 4;
    /** Rows outside a gate where a hauler leaving must be able to turn either way. */
    public static final int TURN_OUT = 4;
    private static final int WALL_HP = 80;
    /** Rows of hatched keep-clear apron before the warehouse door, across each drive. */
    static final int DOCK_ROWS = 2;
    private static final String KEEP_CLEAR = "apron.keep-clear";
    private static final BuildingShellCore.BuildingConfig HALL = new BuildingShellCore.BuildingConfig(
            GroundKind.INDOOR, "WAREHOUSE", PointOfInterest.Kind.DEPOT,
            BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingKind.INDUSTRIAL);

    /**
     * One yard's layout. Every bay has its own gate and its own straight drive; freight stands
     * two-deep in the listed columns from the listed rows, and never in a drive.
     */
    public enum Size {
        /** Two bays side by side before a bigger hall, with an island of freight between the drives. */
        LARGE(20, 26, 12, new int[]{6, 14}, new int[]{1, 2, 9, 10, 17, 18}, new int[]{1, 5, 9}),
        /** One bay: the yard an ordinary industrial compound's ground holds. */
        STANDARD(14, 18, 9, new int[]{7}, new int[]{1, 2, 11, 12}, new int[]{1, 6});

        /** Local width across the gates. */
        public final int width;
        /** Local depth, gate wall to back wall. */
        public final int depth;
        /** Rows of yard between the gate wall and the warehouse. */
        public final int yard;
        /** The column right of each bay's centre line; its gate spans {@link #GATE} cells centred there. */
        final int[] bays;
        final int[] stackCols;
        /** First rows of each two-deep run of freight. */
        final int[] stackRows;

        Size(int width, int depth, int yard, int[] bays, int[] stackCols, int[] stackRows) {
            this.width = width;
            this.depth = depth;
            this.yard = yard;
            this.bays = bays;
            this.stackCols = stackCols;
            this.stackRows = stackRows;
        }

        /** Local row a hauler's middle stands on, its tail toward the warehouse door. */
        public int standY() { return yard - 4; }

        /** Cells along the gate wall from the first gate's first cell to the last gate's last. */
        public int gateSpan() {
            return bays[bays.length - 1] - bays[0] + GATE;
        }

        /** Whether this size fits a {@code w} by {@code h} rectangle in some pose. */
        public boolean fits(int w, int h) {
            return (w >= width && h >= depth) || (w >= depth && h >= width);
        }

        /** Whether local column {@code x} is in a vehicle gate (and so in its bay's drive). */
        boolean onGate(int x) {
            for (int bay : bays) if (x >= bay - GATE / 2 && x < bay + GATE / 2) return true;
            return false;
        }

        /**
         * Whether local {@code (x, y)} holds stacked freight: two-deep runs outside every drive, broken
         * where people move between them and kept clear of the side gap.
         */
        boolean stack(int x, int y) {
            if (y < 1 || y >= yard) return false;
            boolean column = false;
            for (int c : stackCols) column |= x == c;
            if (!column || onGate(x)) return false;
            boolean run = false;
            for (int row : stackRows) run |= y == row || y == row + 1;
            if (!run) return false;
            return !(x <= 2 && y >= GAP_Y - 1 && y <= GAP_Y + GAP);
        }

        boolean isFirstRow(int y) {
            for (int row : stackRows) if (y == row) return true;
            return false;
        }
    }

    /** Where a yard stands: its size, its world corner (minimum x and y) and how many quarter turns it was given. */
    public record Pose(Size size, int left, int bottom, int turn) {
        public int width() { return (turn & 1) == 0 ? size.width : size.depth; }
        public int height() { return (turn & 1) == 0 ? size.depth : size.width; }

        /** World cell of local {@code (x, y)}. */
        public int[] cell(int x, int y) {
            return switch (turn & 3) {
                case 0 -> new int[]{left + x, bottom + y};
                case 1 -> new int[]{left + size.depth - 1 - y, bottom + x};
                case 2 -> new int[]{left + size.width - 1 - x, bottom + size.depth - 1 - y};
                default -> new int[]{left + y, bottom + size.width - 1 - x};
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
     * The best pose of the largest size the ground holds, or null. The footprint lies inside the
     * rectangle {@code left..right, top..bottom}, every cell of it {@code owned} (the claim's own
     * ground) and not reserved for the road; every gate must open onto street a hauler can reach and
     * turn in. The gates facing the most reserved road win, then the most street, then the first found.
     */
    public static Pose choose(GenContext c, int left, int top, int right, int bottom,
                              BiPredicate<Integer, Integer> owned) {
        return choose(c, left, top, right, bottom, owned, c.get(BspKeys.ROAD_CELLS), c.get(BspKeys.ROAD_RESERVATION));
    }

    /**
     * {@link #choose(GenContext, int, int, int, int, BiPredicate)} over a given street mask and road
     * reservation, so a plan can ask before the streets are published.
     */
    public static Pose choose(GenContext c, int left, int top, int right, int bottom,
                              BiPredicate<Integer, Integer> owned, boolean[][] road, boolean[][] reserved) {
        for (Size size : Size.values()) {
            Pose best = null;
            int bestScore = 0;
            for (int turn = 0; turn < 4; turn++) {
                int w = (turn & 1) == 0 ? size.width : size.depth, h = (turn & 1) == 0 ? size.depth : size.width;
                for (int y = top; y + h - 1 <= bottom; y++) {
                    for (int x = left; x + w - 1 <= right; x++) {
                        Pose pose = new Pose(size, x, y, turn);
                        int score = score(c, pose, owned, reserved, road);
                        if (score > bestScore) {
                            bestScore = score;
                            best = pose;
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** How well the gates meet the street, or 0 where the yard may not stand there. Read-only. */
    static int score(GenContext c, Pose pose, BiPredicate<Integer, Integer> owned,
                     boolean[][] reserved, boolean[][] road) {
        Size size = pose.size;
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
        for (int x = 0; x < size.width; x++) {
            if (!size.onGate(x)) continue;
            for (int out = 1; out <= 2; out++) {
                int[] cell = pose.cell(x, -out);
                if (!c.grid.inBounds(cell[0], cell[1]) || !c.grid.isWalkable(cell[0], cell[1])) return 0;
            }
            int[] mouth = pose.cell(x, -1);
            if (road == null || !road[mouth[0]][mouth[1]]) return 0;
            score += reserved != null && reserved[mouth[0]][mouth[1]] ? 4 : 1;
        }
        for (int bay : size.bays) {
            // A hauler leaving by a gate turns into the street outside it, so the street has to hold one turning.
            int[] l = pose.cell(bay - 1, -TURN_OUT), r = pose.cell(bay, -TURN_OUT);
            if (!c.grid.inBounds(l[0], l[1]) || !c.grid.inBounds(r[0], r[1])) return 0;
            if (!TurnAwareCorridor.canSetOutFrom((l[0] + r[0]) / 2f + .5f, (l[1] + r[1]) / 2f + .5f,
                    VehicleType.CARGO_CARRIER, c.grid)) return 0;
        }
        return score;
    }

    /** Lays the yard at {@code pose}, which {@link #choose} returned. Publishes its warehouse and one bay per gate. */
    public static List<LoadingBay> place(GenContext c, Pose pose) {
        Size size = pose.size;
        TileRegistry registry = TileRegistry.installed();
        int right = pose.left + pose.width() - 1, top = pose.bottom + pose.height() - 1;
        c.doodads.removeIf(d -> d.cellX <= right && d.cellX + d.footprintCellsX - 1 >= pose.left
                && d.cellY <= top && d.cellY + d.footprintCellsY - 1 >= pose.bottom);
        for (int y = 0; y < size.depth; y++) {
            for (int x = 0; x < size.width; x++) {
                int[] cell = pose.cell(x, y);
                c.grid.setWalkableFloor(cell[0], cell[1]);
                c.grid.openAllEdges(cell[0], cell[1]);
                c.topology.setFixture(cell[0], cell[1], false);
                c.topology.setGroundKind(cell[0], cell[1], y < size.yard ? GroundKind.DIRT : GroundKind.INDOOR);
                c.markMadeGround(cell[0], cell[1]);
            }
        }
        // The wall round the whole compound, broken by the vehicle gates and the side gap.
        for (int y = 0; y < size.depth; y++) {
            for (int x = 0; x < size.width; x++) {
                boolean ring = x == 0 || x == size.width - 1 || y == 0 || y == size.depth - 1;
                if (!ring) continue;
                int[] cell = pose.cell(x, y);
                if ((y == 0 && size.onGate(x)) || (x == 0 && y >= GAP_Y && y < GAP_Y + GAP)) {
                    c.topology.setGroundKind(cell[0], cell[1], GroundKind.STRIPED);
                    continue;
                }
                c.grid.setWalkable(cell[0], cell[1], false);
                c.grid.setWallHp(cell[0], cell[1], WALL_HP);
                c.topology.setGroundKind(cell[0], cell[1], GroundKind.INDOOR);
            }
        }
        // Each bay and its drive: striped from its gate to the warehouse.
        for (int y = 1; y < size.yard; y++) {
            for (int x = 1; x < size.width - 1; x++) {
                if (!size.onGate(x)) continue;
                int[] cell = pose.cell(x, y);
                c.topology.setGroundKind(cell[0], cell[1], GroundKind.STRIPED);
            }
        }
        // Hatched keep-clear before the warehouse door, across each drive: the dock nobody parks on.
        int keepClear = c.topology.surfaceIndex(KEEP_CLEAR);
        for (int y = size.yard - DOCK_ROWS; y < size.yard; y++) {
            for (int x = 1; x < size.width - 1; x++) {
                if (!size.onGate(x)) continue;
                int[] cell = pose.cell(x, y);
                c.topology.setSurface(cell[0], cell[1], keepClear);
            }
        }
        // Freight outside the drives: cover facing the gates. The first stack's front is a consignment of lobster
        // in pressurised crates.
        DoodadDef crates = registry.doodad("doodad.industrial-crate-stack");
        DoodadDef pallets = registry.doodad("doodad.industrial-pallet-stack");
        DoodadDef lobster = registry.doodad("doodad.lobster-crate");
        boolean consignment = lobster != null;
        for (int y = 1; y < size.yard; y++) {
            for (int x = 1; x < size.width - 1; x++) {
                if (!size.stack(x, y)) continue;
                boolean front = size.isFirstRow(y);
                solid(c, pose.cell(x, y), front && consignment ? lobster : front ? crates : pallets);
                if (front) consignment = false;
            }
        }

        BuildingPlacement.Side frontage = side(pose.step(0, -1));
        int[] a = pose.cell(1, size.yard), b = pose.cell(size.width - 2, size.depth - 2);
        BlockLeaf hall = new BlockLeaf(Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), false);
        PointOfInterest poi = BuildingShellCore.carve(hall, c.grid, c.topology, c.doodads, c.rng, HALL,
                BuildingPlacement.frontOnly(frontage));
        if (poi != null) c.pois.add(poi);
        if (size == Size.LARGE) stageAlongBackWall(c, pose, pallets);
        // A grey slat canopy over the warehouse's loading door.
        int[] in = pose.step(0, 1);
        StreetDressing.doorCanopies(c.grid, c.doodads, 1, size.width - 2, x -> pose.cell(x, size.yard), in[0], in[1]);

        int[] nose = pose.step(0, -1);
        float heading = (float) Math.toDegrees(Math.atan2(nose[1], nose[0]));
        List<LoadingBay> bays = new ArrayList<>(size.bays.length);
        for (int middle : size.bays) {
            // The hauler stands on the bay's centre line, between its two middle cells.
            int[] l = pose.cell(middle - 1, size.standY()), r = pose.cell(middle, size.standY());
            int[] door = door(c, pose, middle);
            int[] gate = pose.cell(middle, -1), side = pose.cell(-1, GAP_Y), turn = pose.cell(middle, -TURN_OUT);
            LoadingBay bay = new LoadingBay((l[0] + r[0]) / 2f + .5f, (l[1] + r[1]) / 2f + .5f,
                    heading, door[0], door[1], gate[0], gate[1], side[0], side[1], turn[0], turn[1]);
            c.loadingBays.add(bay);
            bays.add(bay);
        }
        return bays;
    }

    /** The yard cell in front of the warehouse door nearest the bay, or the bay's own end where none was cut. */
    private static int[] door(GenContext c, Pose pose, int middle) {
        Size size = pose.size;
        int[] best = pose.cell(middle, size.yard - 1);
        int bestOffset = Integer.MAX_VALUE;
        for (int y = size.yard; y <= size.yard + 2; y++) {
            for (int x = 1; x < size.width - 1; x++) {
                int[] cell = pose.cell(x, y);
                if (!c.grid.isDoorway(cell[0], cell[1])) continue;
                int offset = Math.abs(x - middle) + (y - size.yard);
                if (offset >= bestOffset) continue;
                bestOffset = offset;
                best = pose.cell(x, size.yard - 1);
            }
        }
        return best;
    }

    /**
     * Pallets staged along the bigger hall's back wall, every other cell, each with a free aisle cell in front of it.
     * The warehouse furnishing spaces its racks for a hall of any size, which in this one leaves too few places to
     * work stores for a crew to be sent there at all.
     */
    private static void stageAlongBackWall(GenContext c, Pose pose, DoodadDef pallets) {
        Size size = pose.size;
        int y = size.depth - 3;
        for (int x = 3; x < size.width - 3; x += 2) {
            int[] cell = pose.cell(x, y), aisle = pose.cell(x, y - 1);
            if (!free(c, cell) || !free(c, aisle)) continue;
            solid(c, cell, pallets);
        }
    }

    private static boolean free(GenContext c, int[] cell) {
        return c.grid.isWalkable(cell[0], cell[1]) && !c.grid.isDoorway(cell[0], cell[1])
                && !c.topology.isFixture(cell[0], cell[1]);
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
