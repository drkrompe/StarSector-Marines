package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.OpenSquare;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The market square set piece ({@code set-piece-blocks.md}): an open paved square framed by shopfronts whose
 * doors face it, crossed by rows of market stalls that give low cover, entered by street mouths and alleys.
 *
 * <p>One authored layout in its own local frame, turned whole to one of four poses. The square's front is
 * the local bottom edge ({@code y = 0}) and always opens onto a street. Shops stand down each side; the back
 * is either a row of shops or a second street running through. Which of those, and whether an alley
 * breaks each side row, is the layout's {@link Mouths}: the ways a fight can enter the square, the lever that
 * makes it harder or easier to hold.
 *
 * <p>A street the square stands across stays a street: the road reservation and the road either side of it
 * may cross the square's open ground and are left exactly as they were, and no stall stands on them. A shop
 * the reservation runs through is left out, and the street it would have blocked is one more way in.
 * Placement refuses before it changes anything; a host that cannot hold the square keeps its ordinary
 * fill. Selection draws nothing from the layout random stream.
 */
public final class MarketSquare {

    /** Local width across the front. */
    public static final int WIDTH = 24;
    /** Local depth, front to back. */
    public static final int DEPTH = 12;
    /** Depth of each side row of shops, measured across the square. */
    static final int SIDE = 6;
    /** Depth of the back shop where the back is a frontage. */
    static final int BACK = 5;
    /**
     * First local row of the alley through each side row: the middle of the side where the back is a street,
     * and the corner in front of the back row where it is a frontage.
     */
    static final int ALLEY_Y = DEPTH - BACK - 2;
    /** Width of an alley. */
    static final int ALLEY = 2;
    /** Cells of the square left clear along the front and every shop frontage, for mouths and doors. */
    static final int AISLE = 2;
    /** Side of the bandstand at the square's centre. */
    static final int MONUMENT = 4;
    /** Fewest shops that must stand for the square to be framed at all. */
    static final int LEAST_SHOPS = 4;
    /** How far past its host's ground the square may reach: across the street round it, to its far kerb. */
    static final int MARGIN = 3;

    private static final BuildingShellCore.BuildingConfig SHOP = new BuildingShellCore.BuildingConfig(
            GroundKind.TILE, "COMMERCIAL", PointOfInterest.Kind.RESIDENTIAL,
            BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.COMMERCIAL,
            new RoomPurpose[]{RoomPurpose.SHOP_FLOOR, RoomPurpose.STOCKROOM},
            CommercialPartitionStrategy.DEFAULT);

    /** The ways into the square the layout opens, before any street running through a shop's place. */
    public enum Mouths {
        /** The front street only: shops on three sides. */
        ONE(false, false),
        /** A street through, front and back: shops down both sides. */
        TWO(true, false),
        /** The front street and an alley through each side row: shops on three sides. */
        THREE(false, true),
        /** A street through and an alley each side. */
        FOUR(true, true);

        /** Whether the back is a street rather than a frontage. */
        final boolean through;
        /** Whether an alley breaks each side row. */
        final boolean alleys;

        Mouths(boolean through, boolean alleys) {
            this.through = through;
            this.alleys = alleys;
        }

        /** Mouths this layout opens. */
        public int count() { return 1 + (through ? 1 : 0) + (alleys ? 2 : 0); }

        /** Local rows of open square in front of the back, all of them where the back is a street. */
        int squareDepth() { return through ? DEPTH : DEPTH - BACK; }
    }

    /** The default preference: a square framed on three sides first, a street through it after. */
    public static final List<Mouths> DEFAULT_PREFERENCE = List.of(Mouths.THREE, Mouths.ONE, Mouths.FOUR, Mouths.TWO);

    /**
     * A preference led by one layout picked from {@code key} without a random draw, then the
     * {@link #DEFAULT_PREFERENCE}: a host whose ground holds every layout varies the mouths from place to place.
     */
    public static List<Mouths> preference(int key) {
        List<Mouths> out = new ArrayList<>();
        out.add(Mouths.values()[Math.floorMod(key, Mouths.values().length)]);
        for (Mouths mouths : DEFAULT_PREFERENCE) if (!out.contains(mouths)) out.add(mouths);
        return out;
    }

    /**
     * Whether a planned lot of this size holds the square in some pose, reaching at most a kerb cell onto the
     * street either side.
     */
    public static boolean fitsParcel(int width, int height) {
        return (width >= WIDTH - 2 && height >= DEPTH - 2) || (height >= WIDTH - 2 && width >= DEPTH - 2);
    }

    /** Where a square stands: its world corner (minimum x and y) and how many quarter turns it was given. */
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

    /**
     * A chosen square: where it stands, the layout it was laid for, and which of the layout's {@link #shops}
     * stand. A shop the road reservation runs through is left out.
     */
    public record Placement(Pose pose, Mouths mouths, boolean[] standing) {

        /** Whether local {@code (x, y)} is under a shop that stands. */
        boolean built(int x, int y) {
            int shop = shopAt(mouths, x, y);
            return shop >= 0 && standing[shop];
        }
    }

    /** A local rectangle, inclusive, and the local step its frontage faces. */
    record Rect(int x0, int y0, int x1, int y1, int faceX, int faceY) { }

    /** One opening on the square's edge: its local cells, inclusive, and the local step out of the square. */
    record Opening(int x0, int y0, int x1, int y1, int outX, int outY) { }

    private MarketSquare() { }

    /** Whether local {@code (x, y)} is under a shop in this layout. */
    static boolean shop(Mouths mouths, int x, int y) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= DEPTH) return false;
        if (!mouths.through && y >= DEPTH - BACK) return true;
        if (x < SIDE || x >= WIDTH - SIDE) return !(mouths.alleys && y >= ALLEY_Y && y < ALLEY_Y + ALLEY);
        return false;
    }

    /** Which of the layout's {@link #shops} covers local {@code (x, y)}, or -1 for open square. */
    static int shopAt(Mouths mouths, int x, int y) {
        if (!shop(mouths, x, y)) return -1;
        List<Rect> shops = shops(mouths);
        for (int i = 0; i < shops.size(); i++) {
            Rect r = shops.get(i);
            if (x >= r.x0() && x <= r.x1() && y >= r.y0() && y <= r.y1()) return i;
        }
        return -1;
    }

    /**
     * The layout's shops, each facing the square. Where the back is a street, two stand down each side. Where it
     * is a frontage, one stands down each side as far as the back row, the back shop spans the square, and a
     * corner shop at each end of the back row fronts the street outside, so no frontage runs into a wall.
     */
    static List<Rect> shops(Mouths mouths) {
        List<Rect> out = new ArrayList<>();
        if (mouths.through) {
            int split = mouths.alleys ? ALLEY_Y : DEPTH / 2;
            int resume = mouths.alleys ? ALLEY_Y + ALLEY : DEPTH / 2;
            out.add(new Rect(0, 0, SIDE - 1, split - 1, 1, 0));
            out.add(new Rect(0, resume, SIDE - 1, DEPTH - 1, 1, 0));
            out.add(new Rect(WIDTH - SIDE, 0, WIDTH - 1, split - 1, -1, 0));
            out.add(new Rect(WIDTH - SIDE, resume, WIDTH - 1, DEPTH - 1, -1, 0));
            return out;
        }
        int side = mouths.alleys ? ALLEY_Y : DEPTH - BACK;
        out.add(new Rect(0, 0, SIDE - 1, side - 1, 1, 0));
        out.add(new Rect(WIDTH - SIDE, 0, WIDTH - 1, side - 1, -1, 0));
        out.add(new Rect(SIDE, DEPTH - BACK, WIDTH - SIDE - 1, DEPTH - 1, 0, -1));
        out.add(new Rect(0, DEPTH - BACK, SIDE - 1, DEPTH - 1, -1, 0));
        out.add(new Rect(WIDTH - SIDE, DEPTH - BACK, WIDTH - 1, DEPTH - 1, 1, 0));
        return out;
    }

    /** The layout's openings: the front, the back where it is a street, and an alley through each side row. */
    static List<Opening> openings(Mouths mouths) {
        List<Opening> out = new ArrayList<>();
        out.add(new Opening(SIDE, 0, WIDTH - SIDE - 1, 0, 0, -1));
        if (mouths.through) out.add(new Opening(SIDE, DEPTH - 1, WIDTH - SIDE - 1, DEPTH - 1, 0, 1));
        if (mouths.alleys) {
            out.add(new Opening(0, ALLEY_Y, 0, ALLEY_Y + ALLEY - 1, -1, 0));
            out.add(new Opening(WIDTH - 1, ALLEY_Y, WIDTH - 1, ALLEY_Y + ALLEY - 1, 1, 0));
        }
        return out;
    }

    /**
     * Local rows the stall rows stand on: the counters' row, with the goods behind it on the next. Kept off
     * the aisles along the front and the back, so no stall stands in a mouth or before the back shop's door.
     */
    static int[] stallRows(Mouths mouths) {
        int depth = mouths.squareDepth();
        return hasMonument(mouths) ? new int[]{AISLE, depth - AISLE - 2} : new int[]{(depth - 2) / 2};
    }

    /**
     * First local column of the two three-cell stalls in each row, kept off the aisles along the side
     * frontages; the street through, where there is one, runs between them.
     */
    static int[] stallColumns() {
        return new int[]{SIDE + AISLE, WIDTH - SIDE - AISLE - 3};
    }

    /** Whether local {@code (x, y)} is under a stall of this layout, before any is left off a street. */
    static boolean stall(Mouths mouths, int x, int y) {
        for (int row : stallRows(mouths)) {
            if (y != row && y != row + 1) continue;
            for (int column : stallColumns()) if (x >= column && x < column + 3) return true;
        }
        return false;
    }

    /** Whether the square is deep enough for the bandstand between its two stall rows. */
    static boolean hasMonument(Mouths mouths) {
        return mouths.squareDepth() >= 2 * AISLE + 2 * 2 + MONUMENT;
    }

    /** Whether local {@code (x, y)} is under the bandstand of this layout, before it is left off a street. */
    static boolean monument(Mouths mouths, int x, int y) {
        if (!hasMonument(mouths)) return false;
        int x0 = WIDTH / 2 - MONUMENT / 2, y0 = mouths.squareDepth() / 2 - MONUMENT / 2;
        return x >= x0 && x < x0 + MONUMENT && y >= y0 && y < y0 + MONUMENT;
    }

    /**
     * The best placement over the host's ground, the inclusive world box {@code left..right, top..bottom} and
     * up to {@link #MARGIN} cells round it, trying the layouts in the order asked, or null. Every footprint
     * cell must be the host's own ground ({@code owned}) and every opening must give onto road; a shop the
     * road reservation runs through is left out, and at least {@link #LEAST_SHOPS} must stand. Within the
     * first layout that fits anywhere, the square with the most shops standing wins, then one whose front faces
     * the reserved road, then the one covering most of the host's own leaves ({@code member}), then the first
     * found.
     */
    public static Placement choose(GenContext c, int boxLeft, int boxTop, int boxRight, int boxBottom,
                                   BiPredicate<Integer, Integer> owned, BiPredicate<Integer, Integer> member,
                                   List<Mouths> preference) {
        boolean[][] reserved = c.get(BspKeys.ROAD_RESERVATION);
        boolean[][] road = c.get(BspKeys.ROAD_CELLS);
        if (road == null) return null;
        for (Mouths mouths : preference) {
            Placement best = null;
            int bestScore = 0;
            for (int turn = 0; turn < 4; turn++) {
                int w = (turn & 1) == 0 ? WIDTH : DEPTH, h = (turn & 1) == 0 ? DEPTH : WIDTH;
                for (int bottom = boxTop - MARGIN; bottom + h - 1 <= boxBottom + MARGIN; bottom++) {
                    for (int left = boxLeft - MARGIN; left + w - 1 <= boxRight + MARGIN; left++) {
                        Pose pose = new Pose(left, bottom, turn);
                        boolean[] standing = new boolean[shops(mouths).size()];
                        int score = score(c, pose, mouths, owned, member, reserved, road, standing);
                        if (score > bestScore) {
                            bestScore = score;
                            best = new Placement(pose, mouths, standing);
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** How well the square stands here, or 0 where it may not, filling in which shops stand. Reads the map only. */
    static int score(GenContext c, Pose pose, Mouths mouths, BiPredicate<Integer, Integer> owned,
                     BiPredicate<Integer, Integer> member, boolean[][] reserved, boolean[][] road,
                     boolean[] standing) {
        int right = pose.left + pose.width() - 1, top = pose.bottom + pose.height() - 1;
        if (pose.left < 1 || pose.bottom < 1 || right >= c.width - 1 || top >= c.height - 1) return 0;
        Arrays.fill(standing, true);
        int covered = 0;
        for (int y = 0; y < DEPTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int[] cell = pose.cell(x, y);
                if (!owned.test(cell[0], cell[1])) return 0;
                int shop = shopAt(mouths, x, y);
                if (shop >= 0 && reserved != null && reserved[cell[0]][cell[1]]) standing[shop] = false;
                if (member.test(cell[0], cell[1])) covered++;
            }
        }
        int shops = 0;
        for (boolean stands : standing) if (stands) shops++;
        if (shops < LEAST_SHOPS) return 0;
        for (LandingPad pad : c.landingPads) {
            if (pose.left <= pad.right() && right >= pad.left() && pose.bottom <= pad.top() && top >= pad.bottom())
                return 0;
        }
        for (Opening opening : openings(mouths)) {
            for (int[] local : cells(opening)) {
                int[] out = pose.cell(local[0] + opening.outX(), local[1] + opening.outY());
                if (!c.grid.inBounds(out[0], out[1]) || !road[out[0]][out[1]]) return 0;
            }
        }
        int score = 1 + covered + shops * 2 * WIDTH * DEPTH;
        for (int x = SIDE; x < WIDTH - SIDE; x++) {
            int[] out = pose.cell(x, -1);
            if (reserved != null && reserved[out[0]][out[1]]) return score + WIDTH * DEPTH;
        }
        return score;
    }

    /** Every footprint cell the placement covers, as a world mask. */
    public static boolean[][] footprint(Placement placement, int width, int height) {
        boolean[][] mask = new boolean[width][height];
        for (int y = 0; y < DEPTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int[] cell = placement.pose().cell(x, y);
                mask[cell[0]][cell[1]] = true;
            }
        }
        return mask;
    }

    /** Lays the square, which {@link #choose} returned. Publishes its shops and the square. */
    public static OpenSquare place(GenContext c, Placement placement) {
        Pose pose = placement.pose();
        boolean[][] road = c.get(BspKeys.ROAD_CELLS);
        int right = pose.left + pose.width() - 1, top = pose.bottom + pose.height() - 1;
        c.doodads.removeIf(d -> d.cellX <= right && d.cellX + d.footprintCellsX - 1 >= pose.left
                && d.cellY <= top && d.cellY + d.footprintCellsY - 1 >= pose.bottom);
        // The street the square stands across stays as it was; everything else is paved or built on.
        for (int y = 0; y < DEPTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int[] cell = pose.cell(x, y);
                boolean shop = placement.built(x, y);
                if (!shop && road[cell[0]][cell[1]]) continue;
                c.grid.setWalkableFloor(cell[0], cell[1]);
                c.grid.openAllEdges(cell[0], cell[1]);
                c.topology.setFixture(cell[0], cell[1], false);
                c.topology.setGroundKind(cell[0], cell[1], shop ? GroundKind.TILE : GroundKind.BRICK);
                c.markMadeGround(cell[0], cell[1]);
            }
        }
        List<Rect> shops = shops(placement.mouths());
        for (int i = 0; i < shops.size(); i++) {
            if (!placement.standing()[i]) continue;
            Rect rect = shops.get(i);
            int[] a = pose.cell(rect.x0(), rect.y0()), b = pose.cell(rect.x1(), rect.y1());
            BlockLeaf leaf = new BlockLeaf(Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), false);
            PointOfInterest poi = BuildingShellCore.carve(leaf, c.grid, c.topology, c.doodads, c.rng, SHOP,
                    new BuildingPlacement(side(pose.step(rect.faceX(), rect.faceY())), false));
            if (poi != null) c.pois.add(poi);
        }
        furnish(c, placement, road);
        return publish(c, placement, road);
    }

    /** Stall rows across the square, planters at the front corners, and the bandstand where the square is deep enough. */
    private static void furnish(GenContext c, Placement placement, boolean[][] road) {
        TileRegistry registry = TileRegistry.installed();
        DoodadDef counter = registry.doodad("doodad.galley-servery-counter");
        DoodadDef crate = registry.doodad("doodad.crate");
        DoodadDef box = registry.doodad("doodad.box");
        DoodadDef fish = registry.doodad("origin-freight.seafood-crates-clean");
        DoodadDef planter = registry.doodad("crew-lounge.planter");
        DoodadDef bandstand = registry.doodad("origin-park.bandstand-clean");
        int[] rows = stallRows(placement.mouths());
        int[] columns = stallColumns();
        for (int r = 0; r < rows.length; r++) {
            for (int s = 0; s < columns.length; s++) {
                int x0 = columns[s], y0 = rows[r];
                if (((r + s) & 1) == 0) {
                    // A produce stall: a counter of three facing the front, stock behind it either side of the stallholder.
                    for (int i = 0; i < 3; i++) prop(c, placement, road, x0 + i, y0, 1, counter, true);
                    prop(c, placement, road, x0, y0 + 1, 1, crate, true);
                    prop(c, placement, road, x0 + 2, y0 + 1, 1, box, true);
                } else {
                    // A fish stall: iced crates, and a counter at its end.
                    prop(c, placement, road, x0, y0, 2, fish, true);
                    prop(c, placement, road, x0 + 2, y0, 1, counter, true);
                }
            }
        }
        prop(c, placement, road, SIDE, 0, 1, planter, true);
        prop(c, placement, road, WIDTH - SIDE - 1, 0, 1, planter, true);
        if (hasMonument(placement.mouths())) {
            int depth = placement.mouths().squareDepth();
            prop(c, placement, road, WIDTH / 2 - MONUMENT / 2, depth / 2 - MONUMENT / 2, MONUMENT, bandstand, false);
        }
    }

    /**
     * Stands a square prop of {@code size} cells at local {@code (x, y)} where every cell it covers is open
     * square off the street and still free; otherwise leaves it out.
     */
    private static void prop(GenContext c, Placement placement, boolean[][] road, int x, int y, int size,
                             DoodadDef def, boolean seeThrough) {
        if (def == null) return;
        Pose pose = placement.pose();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                if (x + dx >= WIDTH || y + dy >= DEPTH || placement.built(x + dx, y + dy)) return;
                int[] cell = pose.cell(x + dx, y + dy);
                if (road[cell[0]][cell[1]] || !c.grid.isWalkable(cell[0], cell[1])
                        || c.topology.isFixture(cell[0], cell[1])) return;
                minX = Math.min(minX, cell[0]);
                minY = Math.min(minY, cell[1]);
            }
        }
        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                int[] cell = pose.cell(x + dx, y + dy);
                c.grid.setWalkable(cell[0], cell[1], false);
                c.grid.setSeeThrough(cell[0], cell[1], seeThrough);
                c.topology.setFixture(cell[0], cell[1], true);
            }
        }
        c.doodads.add(new Doodad(minX, minY, def));
    }

    private static OpenSquare publish(GenContext c, Placement placement, boolean[][] road) {
        Pose pose = placement.pose();
        int depth = placement.mouths().squareDepth();
        int[] centre = pose.cell(WIDTH / 2, depth / 2);
        int[] a = pose.cell(SIDE, 0), b = pose.cell(WIDTH - SIDE - 1, depth - 1);
        List<OpenSquare.Mouth> out = new ArrayList<>();
        for (Opening opening : mouths(placement, road)) {
            int[] from = pose.cell(opening.x0(), opening.y0()), to = pose.cell(opening.x1(), opening.y1());
            int[] step = pose.step(opening.outX(), opening.outY());
            out.add(new OpenSquare.Mouth(from[0], from[1], to[0], to[1], step[0], step[1]));
        }
        OpenSquare square = new OpenSquare(centre[0], centre[1], Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), out);
        c.openSquares.add(square);
        return square;
    }

    /**
     * The layout's openings, and every outer edge of a shop left out where the ground outside is road: the
     * street that ran through the shop's place comes into the square there.
     */
    static List<Opening> mouths(Placement placement, boolean[][] road) {
        List<Opening> out = new ArrayList<>(openings(placement.mouths()));
        List<Rect> shops = shops(placement.mouths());
        for (int i = 0; i < shops.size(); i++) {
            if (placement.standing()[i]) continue;
            Rect r = shops.get(i);
            if (r.x0() == 0) edge(out, placement, road, new Opening(0, r.y0(), 0, r.y1(), -1, 0));
            if (r.x1() == WIDTH - 1) edge(out, placement, road, new Opening(WIDTH - 1, r.y0(), WIDTH - 1, r.y1(), 1, 0));
            if (r.y0() == 0) edge(out, placement, road, new Opening(r.x0(), 0, r.x1(), 0, 0, -1));
            if (r.y1() == DEPTH - 1) edge(out, placement, road, new Opening(r.x0(), DEPTH - 1, r.x1(), DEPTH - 1, 0, 1));
        }
        return out;
    }

    private static void edge(List<Opening> out, Placement placement, boolean[][] road, Opening edge) {
        for (int[] local : cells(edge)) {
            int[] cell = placement.pose().cell(local[0] + edge.outX(), local[1] + edge.outY());
            if (road[cell[0]][cell[1]]) {
                out.add(edge);
                return;
            }
        }
    }

    private static List<int[]> cells(Opening opening) {
        List<int[]> out = new ArrayList<>();
        for (int y = opening.y0(); y <= opening.y1(); y++) {
            for (int x = opening.x0(); x <= opening.x1(); x++) out.add(new int[]{x, y});
        }
        return out;
    }

    private static BuildingPlacement.Side side(int[] step) {
        if (step[0] < 0) return BuildingPlacement.Side.LEFT;
        if (step[0] > 0) return BuildingPlacement.Side.RIGHT;
        return step[1] < 0 ? BuildingPlacement.Side.TOP : BuildingPlacement.Side.BOTTOM;
    }
}
