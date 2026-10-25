package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;

/**
 * Brushing soft ground: the last rung before a move order is given up.
 *
 * <p><b>Hard and soft ground.</b> A vehicle never crosses hard ground: a wall, a building, a barricade, water it does
 * not float on, a tree's trunk, a boulder, another hull, the map edge. Soft ground is what a driver shoves past: growth
 * (a closed {@link CellTag#CRUSHABLE} natural cell: a young or a dead tree, never a lone boulder, which carries the tag
 * but is no natural solid), a standing tree's outer crown for a medium or heavy hull ({@link Props#outerCrown}), and an
 * industrial fence ({@link Props#fence}). For a hull that already crushes growth ({@link HeavyTracks}) growth is open
 * ground in the view it moves over, not soft.
 *
 * <p><b>Brushing.</b> A hull that brushes may reach into soft ground by at most {@link #DEPTH_CELLS} at any point of
 * its footprint. That is two checks with the footprint test that already exists, not a new geometry: the whole
 * footprint clears the hull's ground with its soft ground opened ({@link #softView}), so nothing hard is touched at all,
 * and the footprint inset by the depth on every side clears the real ground, so soft ground is touched no deeper. A
 * brushing hull crawls ({@link #CRAWL_FRACTION}), takes no damage, and leaves the ground as it was: no vehicle edits the
 * battlefield but the blade and the tracks.
 *
 * <p><b>Where.</b> Only a move order's controller, from {@link #START_SECONDS} of braked failure while the order is
 * still within its give-up time, and a move order's route proof, once the strict proof has refused it. Dispatch,
 * docking, the departure turnaround and allied command's choice of stand never brush, and no clearance mask opens soft
 * ground: a plan only possible by brushing is not one to prefer.
 *
 * <p><b>The views are kept.</b> Each ground a hull moves over (the live grid, the tracks' crush view, the skirt's
 * hover view) has a soft view per hull weight, caught up with its source's change log cell by cell as
 * {@link HeavyTracks} keeps its own.
 */
public final class SoftBrush {

    /**
     * Control switch: brushing is on unless it reads {@code false}, when an order whose every legal answer failed is
     * given up as before. Remove it with the losing path once The Burn's vehicle-order evidence has been read against
     * it.
     */
    public static final String PROPERTY = "battle.vehicle.softBrush";

    /** How far, in cells, a brushing footprint may reach into soft ground. */
    public static final float DEPTH_CELLS = .3f;

    /** The share of top speed a brushing hull crawls at. */
    public static final float CRAWL_FRACTION = .3f;

    /** Seconds of braked failure after which a move order's hull brushes; the order is given up at eight. */
    public static final float START_SECONDS = 6f;

    /** What the map's props say of a closed cell. */
    public interface Props {
        /** A piece of industrial fence stands on the cell. */
        boolean fence(int x, int y);

        /** A standing tree holds the cell with its crown, not its trunk. */
        boolean outerCrown(int x, int y);
    }

    /** The prop reading of a map without fences or trees. */
    public static final Props NO_PROPS = new Props() {
        @Override public boolean fence(int x, int y) { return false; }
        @Override public boolean outerCrown(int x, int y) { return false; }
    };

    /** Soft views kept at once: a few grounds (live, crush, hover, a blade's) by two hull weights. */
    private static final int KEPT = 8;
    private final Props props;
    private final NavigationGrid[] sources = new NavigationGrid[KEPT];
    private final boolean[] crowned = new boolean[KEPT];
    private final NavigationGrid[] views = new NavigationGrid[KEPT];
    private final long[] caughtUp = new long[KEPT];
    private int oldest;

    public SoftBrush(Props props) {
        this.props = props;
    }

    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY));
    }

    /** Whether closed cell {@code (x, y)} of {@code grid} is soft ground to {@code type}. See the class rule. */
    public static boolean soft(NavigationGrid grid, int x, int y, VehicleType type, Props props) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y)) return false;
        if (grid.hasTag(x, y, CellTag.NATURAL_SOLID)) {
            if (grid.hasTag(x, y, CellTag.CRUSHABLE)) return true;
            return crowns(type) && props.outerCrown(x, y);
        }
        return props.fence(x, y);
    }

    private static boolean crowns(VehicleType type) {
        return type.weight() != VehicleType.Weight.LIGHT;
    }

    /** {@code motion} with every cell soft to {@code type} open, caught up with every change {@code motion} logged. */
    NavigationGrid softView(NavigationGrid motion, VehicleType type) {
        boolean crowns = crowns(type);
        int slot = -1;
        for (int i = 0; i < KEPT && slot < 0; i++) if (sources[i] == motion && crowned[i] == crowns) slot = i;
        if (slot < 0) {
            // A ground not kept, or one replaced since (the skirt's view is copied afresh): the oldest view goes.
            slot = oldest;
            oldest = (oldest + 1) % KEPT;
            sources[slot] = null;
        }
        long now = motion.changeCount();
        NavigationGrid view = views[slot];
        if (sources[slot] != motion || !motion.hasCaughtUpFrom(caughtUp[slot])) {
            view = motion.copyRoutingTopology();
            for (int y = 0; y < motion.getHeight(); y++) {
                for (int x = 0; x < motion.getWidth(); x++) {
                    if (soft(motion, x, y, type, props)) view.setWalkable(x, y, true);
                }
            }
            views[slot] = view;
            sources[slot] = motion;
            crowned[slot] = crowns;
        } else {
            for (long sequence = caughtUp[slot]; sequence < now; sequence++) {
                int cell = motion.changedCellAt(sequence);
                int x = cell % motion.getWidth(), y = cell / motion.getWidth();
                view.syncRoutingCell(motion, cell, soft(motion, x, y, type, props) ? CellTag.WALKABLE.mask() : 0L);
            }
        }
        caughtUp[slot] = now;
        return view;
    }

    /**
     * Whether a footprint {@code length} by {@code width} at the pose may stand there brushing: whole, it clears
     * {@code soft}; inset by {@link #DEPTH_CELLS} from {@code insetLength} by {@code insetWidth}, it clears
     * {@code motion}. Route proof passes its slackened footprint whole and the exact one to inset.
     */
    static boolean fits(float x, float y, float facing, float length, float width,
                        float insetLength, float insetWidth, NavigationGrid motion, NavigationGrid soft) {
        return VehicleFootprint.isPoseFeasible(x, y, facing, length, width, soft)
                && VehicleFootprint.isPoseFeasible(x, y, facing, inset(insetLength), inset(insetWidth), motion);
    }

    /** One tick's move from {@code from} to {@code to} swept brushing: the shorter proven travel of the two checks. */
    VehicleTerrainMotion.Result sweep(Pose from, Pose to, VehicleType type, NavigationGrid motion,
                                      boolean allowOffMap) {
        return sweep(from, to, type.visualLengthCells, type.visualWidthCells, motion, softView(motion, type),
                allowOffMap);
    }

    static VehicleTerrainMotion.Result sweep(Pose from, Pose to, float length, float width,
                                             NavigationGrid motion, NavigationGrid soft, boolean allowOffMap) {
        VehicleTerrainMotion.Result whole = VehicleTerrainMotion.sweep(from, to, length, width, soft, allowOffMap);
        if (whole.fraction() <= 0f) return whole;
        VehicleTerrainMotion.Result inset = VehicleTerrainMotion.sweep(from, to, inset(length), inset(width),
                motion, allowOffMap);
        return inset.fraction() < whole.fraction() ? inset : whole;
    }

    /**
     * {@link NeutralPivot#clearDirection} brushing: the way a hull at the pose can turn on the spot from
     * {@code fromDeg} to {@code toDeg} with both checks clear the whole turn, or {@code 0}.
     */
    int clearDirection(float x, float y, float fromDeg, float toDeg, VehicleType type, NavigationGrid motion) {
        int whole = NeutralPivot.clearDirection(x, y, fromDeg, toDeg,
                type.visualLengthCells, type.visualWidthCells, softView(motion, type));
        if (whole == 0) return 0;
        int inset = NeutralPivot.clearDirection(x, y, fromDeg, toDeg,
                inset(type.visualLengthCells), inset(type.visualWidthCells), motion);
        return inset == whole ? whole : 0;
    }

    private static float inset(float extent) {
        return Math.max(.05f, extent - 2f * DEPTH_CELLS);
    }
}
