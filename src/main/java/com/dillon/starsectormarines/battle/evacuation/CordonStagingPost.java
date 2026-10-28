package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.logistics.ResupplyCache;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The evac cordon as the colony defence force's <b>staging post</b> ({@code colony-breach-nouns.md}, Evac cordon;
 * owner, 2026-10-28: "Should this be a staging point the available APDF has set up? We the mercs are brought in as
 * the muscle"): inside the ring, behind the line, three <b>stations</b> the defence force set up before the company
 * was called in.
 *
 * <ul>
 *   <li>The <b>field HQ</b>: a tactical table, its consoles, a radar dish and a generator.</li>
 *   <li>The <b>aid station</b>: treatment tables, cots, a trolley and a screen, where the survivors are seen to on
 *   their way to the lift.</li>
 *   <li>The <b>supply dump</b>: crate and pallet stacks and fuel drums, with the company's {@link ResupplyCache} on
 *   the ground in front of it, so falling back to the post re-arms the company.</li>
 * </ul>
 *
 * <p>The stations stand on the side of the ring away from the threat, the HQ straight back and the other two either
 * side of it, the dump on the side the company's own way in comes through. Each is set down whole where it fits:
 * inside the trench by a walkway, clear of the lift and its craft, of the line's landing points and of every lane in
 * through a gate. A station with nowhere to stand is left out. Pieces are closed to movement and open to sight.
 *
 * <p>Planning ({@link #plan}) is pure, over the predicate the battle answers, so it is tested without a battle.
 */
public final class CordonStagingPost {

    /**
     * A station's piece: its art, its cell offset from the station's anchor (the piece's low corner), its footprint,
     * and the quarter turns its art is drawn at.
     */
    record Piece(String doodad, int dx, int dy, int width, int height, int turns) {
        Piece(String doodad, int dx, int dy, int width, int height) {
            this(doodad, dx, dy, width, height, 0);
        }

        /** This piece with the whole station turned a quarter about its anchor. */
        Piece turned() {
            return new Piece(doodad, -(dy + height - 1), dx, height, width, turns + 1);
        }
    }

    /** One station: its name and pieces, and how many cells in front of it (toward the lift) its cache stands; 0 for none. */
    record Station(String name, List<Piece> pieces, int cacheReach) {
        /** This station turned a quarter about its anchor, so a long one stands along a side of the ring. */
        Station turned() {
            List<Piece> turned = new ArrayList<>();
            for (Piece p : pieces) turned.add(p.turned());
            return new Station(name, turned, cacheReach);
        }
    }

    /**
     * A station as set down: the station as it stands (turned or not), the anchor its pieces are offset from, and its
     * cache's cell or null.
     */
    record Placed(Station station, int anchorX, int anchorY, int[] cache) { }

    static final Station HQ = new Station("field HQ", List.of(
            new Piece("doodad.military-command-console", -1, 1, 1, 1),
            new Piece("doodad.military-command-console", 1, 1, 1, 1),
            new Piece("doodad.military-radar-dish", -3, 0, 1, 1),
            new Piece("doodad.military-tactical-table", 0, 0, 1, 1),
            new Piece("doodad.industrial-generator", -2, -1, 1, 1),
            new Piece("doodad.parked-armored-apc", 2, -1, 3, 2)), 0);
    static final Station AID = new Station("aid station", List.of(
            new Piece("doodad.medical-treatment-table", -2, 0, 2, 1),
            new Piece("doodad.medical-treatment-table", -2, 2, 2, 1),
            new Piece("doodad.medical-privacy-screen", -3, 1, 1, 1),
            new Piece("doodad.medical-supply-trolley", 0, 1, 1, 1),
            new Piece("doodad.medical-ward-bed-head-n", 1, 0, 1, 2),
            new Piece("doodad.medical-ward-bed-head-n", 2, 0, 1, 2),
            new Piece("doodad.medical-ward-bed-head-n", 3, 0, 1, 2),
            new Piece("doodad.medical-ward-bed-head-n", 4, 0, 1, 2)), 0);
    static final Station DUMP = new Station("supply dump", List.of(
            new Piece("doodad.industrial-crate-stack", -2, 0, 1, 1),
            new Piece("doodad.industrial-crate-stack", -1, 0, 1, 1),
            new Piece("doodad.industrial-crate-stack", 0, 0, 1, 1),
            new Piece("doodad.industrial-crate-stack", 1, 0, 1, 1),
            new Piece("doodad.industrial-pallet-stack", -2, 1, 1, 1),
            new Piece("doodad.industrial-drum-cluster", -1, 1, 1, 1),
            new Piece("doodad.industrial-drum-cluster", 0, 1, 1, 1),
            new Piece("doodad.industrial-pallet-stack", 1, 1, 1, 1),
            new Piece("doodad.industrial-fluid-tank", 2, 0, 1, 1),
            new Piece("doodad.industrial-fluid-tank", 2, 1, 1, 1)), 2);

    /** Stock the dump hands the company: three air-dropped caches' worth, the post being where the ammunition is. */
    static final int DUMP_STOCK = 3 * ResupplyCache.DEFAULT_STOCK;
    /** Rings out from the lift a station's anchor may stand on. */
    static final int NEAREST = 7, FARTHEST = 12;
    /** The deepest ring a piece may stand on: a walkway of two cells is kept inside the trench. */
    static final int INNERMOST_TRENCH_WALK = PickupCordonWorks.TRENCH - 3;
    /**
     * Half-extents round the lift kept for its craft, which sets down lengthwise along the axis it flies in on, from
     * the nearest map edge: as long as the evac craft's art, near eighteen cells, and as wide.
     */
    static final int CRAFT_HALF_WIDTH = 5, CRAFT_HALF_LENGTH = 10;
    /** Cells round each landing point of the line kept clear for its sorties. */
    static final int LANDING_CLEARANCE = 3;
    /** Cells either side of a lane kept clear, so the way from each gate to the lift stays a road. */
    static final int LANE_CLEARANCE = 2;
    /** Degrees either side of straight back the two flank stations stand. */
    static final double FLANK = Math.toRadians(55);

    private CordonStagingPost() {}

    /**
     * Sets the stations down round {@code (liftX, liftY)}: the HQ straight away from the threat, the dump on the
     * flank nearer the company's way in ({@code padX}, {@code padY}), the aid station on the other.
     *
     * @param open      whether a piece may stand on a cell
     * @param lanes     the ways in through the gates, each a list of {x, y} cells, kept clear
     * @param formation the line's landing points as {x, y} pairs, kept clear
     * @param craftAlongY whether the evac craft sets down lengthwise north-south (it flies in from the north or south
     *                    edge) rather than east-west
     */
    static List<Placed> plan(int liftX, int liftY, PickupCordonWorks.Open open, List<List<int[]>> lanes,
                             int[] formation, int threatX, int threatY, int padX, int padY, boolean craftAlongY) {
        double back = Math.atan2(liftY - threatY, liftX - threatX);
        double toPad = Math.atan2(padY - liftY, padX - liftX);
        double left = back + FLANK, right = back - FLANK;
        boolean dumpLeft = Math.abs(off(toPad, left)) <= Math.abs(off(toPad, right));
        Set<Long> kept = new HashSet<>();
        for (List<int[]> lane : lanes) {
            for (int[] c : lane) {
                if (PickupCordonWorks.ring(c[0] - liftX, c[1] - liftY) > PickupCordonWorks.WALL) break;
                for (int y = c[1] - LANE_CLEARANCE; y <= c[1] + LANE_CLEARANCE; y++)
                    for (int x = c[0] - LANE_CLEARANCE; x <= c[0] + LANE_CLEARANCE; x++) kept.add(PickupCordonWorks.key(x, y));
            }
        }
        for (int p = 0; p + 1 < formation.length; p += 2) {
            for (int y = formation[p + 1] - LANDING_CLEARANCE; y <= formation[p + 1] + LANDING_CLEARANCE; y++)
                for (int x = formation[p] - LANDING_CLEARANCE; x <= formation[p] + LANDING_CLEARANCE; x++)
                    kept.add(PickupCordonWorks.key(x, y));
        }
        List<Placed> placed = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        place(HQ, back, back, liftX, liftY, craftAlongY, open, kept, taken, placed);
        place(dumpLeft ? DUMP : AID, left, back, liftX, liftY, craftAlongY, open, kept, taken, placed);
        place(dumpLeft ? AID : DUMP, right, back, liftX, liftY, craftAlongY, open, kept, taken, placed);
        return placed;
    }

    /**
     * Sets one station down at the anchor nearest {@code bearing} where it fits whole, if any, and never on the half
     * of the ring facing the threat ({@code back} is straight away from it): a station the rear has no room for is
     * left out rather than put in the line's way.
     */
    private static void place(Station station, double bearing, double back, int liftX, int liftY, boolean craftAlongY,
                              PickupCordonWorks.Open open, Set<Long> kept, Set<Long> taken, List<Placed> placed) {
        List<int[]> anchors = new ArrayList<>();
        for (int dy = -FARTHEST; dy <= FARTHEST; dy++) {
            for (int dx = -FARTHEST; dx <= FARTHEST; dx++) {
                int r = PickupCordonWorks.ring(dx, dy);
                if (r < NEAREST || r > FARTHEST) continue;
                if (Math.abs(off(Math.atan2(dy, dx), back)) > Math.PI / 2) continue;
                anchors.add(new int[]{liftX + dx, liftY + dy});
            }
        }
        anchors.sort(Comparator.<int[]>comparingDouble(a -> Math.abs(off(Math.atan2(a[1] - liftY, a[0] - liftX), bearing)))
                .thenComparingInt(a -> Math.abs(PickupCordonWorks.ring(a[0] - liftX, a[1] - liftY) - (NEAREST + FARTHEST) / 2))
                .thenComparingInt(a -> a[1]).thenComparingInt(a -> a[0]));
        for (int[] a : anchors) {
            Station standing = station;
            List<Long> cells = cells(standing, a[0], a[1], liftX, liftY, craftAlongY, open, kept, taken);
            if (cells == null) {
                standing = station.turned();
                cells = cells(standing, a[0], a[1], liftX, liftY, craftAlongY, open, kept, taken);
            }
            if (cells == null) continue;
            int[] cache = null;
            if (station.cacheReach() > 0) {
                // In front of the station, toward the lift, along whichever axis the lift lies further on.
                boolean across = Math.abs(liftX - a[0]) >= Math.abs(liftY - a[1]);
                int cx = a[0] + (across ? station.cacheReach() * Integer.signum(liftX - a[0]) : 0);
                int cy = a[1] + (across ? 0 : station.cacheReach() * Integer.signum(liftY - a[1]));
                if (!open.at(cx, cy) || cells.contains(PickupCordonWorks.key(cx, cy))) continue;
                cache = new int[]{cx, cy};
            }
            // A cell's margin round every piece, so two stations never share a wall.
            for (long cell : cells) {
                int x = (int) (cell >> 32), y = (int) cell;
                for (int my = y - 1; my <= y + 1; my++)
                    for (int mx = x - 1; mx <= x + 1; mx++) taken.add(PickupCordonWorks.key(mx, my));
            }
            placed.add(new Placed(standing, a[0], a[1], cache));
            return;
        }
    }

    /** The cells a station's pieces take at an anchor, or null where any of them may not stand. */
    private static List<Long> cells(Station station, int ax, int ay, int liftX, int liftY, boolean craftAlongY,
                                    PickupCordonWorks.Open open, Set<Long> kept, Set<Long> taken) {
        List<Long> cells = new ArrayList<>();
        for (Piece p : station.pieces()) {
            for (int y = ay + p.dy(); y < ay + p.dy() + p.height(); y++) {
                for (int x = ax + p.dx(); x < ax + p.dx() + p.width(); x++) {
                    long k = PickupCordonWorks.key(x, y);
                    if (!open.at(x, y) || kept.contains(k) || taken.contains(k)) return null;
                    if (PickupCordonWorks.ring(x - liftX, y - liftY) > INNERMOST_TRENCH_WALK) return null;
                    int along = craftAlongY ? Math.abs(y - liftY) : Math.abs(x - liftX);
                    int across = craftAlongY ? Math.abs(x - liftX) : Math.abs(y - liftY);
                    if (across <= CRAFT_HALF_WIDTH && along <= CRAFT_HALF_LENGTH) return null;
                    cells.add(k);
                }
            }
        }
        return cells;
    }

    /** Lays the planned stations on the live map before the first tick, and the dump's cache for the company. */
    static void lay(BattleSimulation sim, List<Placed> placed) {
        TileRegistry registry = TileRegistry.installed();
        List<RedoubtWorks.Piece> pieces = new ArrayList<>();
        for (Placed s : placed) {
            for (Piece p : s.station().pieces()) {
                DoodadDef def = registry == null ? null : registry.doodad(p.doodad());
                if (def == null) continue;
                pieces.add(new RedoubtWorks.Piece(s.anchorX() + p.dx(), s.anchorY() + p.dy(), p.doodad(), p.turns(),
                        p.width(), p.height()));
            }
            if (s.cache() != null) sim.addResupplyCache(new ResupplyCache(s.cache()[0], s.cache()[1], Faction.MARINE,
                    DUMP_STOCK));
        }
        if (!pieces.isEmpty()) {
            RedoubtWorks.lay(sim, new RedoubtWorks.Plan(0, 0, 0, 0, pieces, List.of(), -1, -1, -1, -1, 0));
        }
    }

    /** {@code a - b} as an angle in {@code (-PI, PI]}. */
    private static double off(double a, double b) {
        double d = a - b;
        while (d <= -Math.PI) d += 2 * Math.PI;
        while (d > Math.PI) d -= 2 * Math.PI;
        return d;
    }
}
