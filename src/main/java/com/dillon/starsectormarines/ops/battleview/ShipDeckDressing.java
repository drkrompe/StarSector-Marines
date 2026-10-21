package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The founding hull's Deficit dressing: storage camped along the passages,
 * grime at the wall foot, patched and buckled panels, hanging conduit, worn
 * furniture and dead screens laid over a deck whose form is already fixed.
 *
 * <p>Presentation only. Nothing here enters the simulation: no doodad is added
 * to the map, no cell changes walkability, cover or sight, and the deck the crew
 * walks is the deck generation made. The whole Deficit plan is derived once from
 * the deck and its seed, and a {@link CoreDressing} only chooses which cores'
 * pieces are drawn, so restoring one core never moves another's dressing and the
 * same cells are dirty on every visit.
 *
 * <p>Placement laws, each tested on a hand-made grid:
 * <ul>
 *   <li>Only walkable floor is dressed. Sealed compartments, offline berths,
 *       shut range lanes and reserved voids are not walkable, and wrecked rooms
 *       carry their own condition art, so neither is touched.</li>
 *   <li>No piece covers a doorway or the cells round it, a berth footprint, or
 *       the deck inside a bay aperture.</li>
 *   <li>Lanes stay readable. Anything that looks like it stands on the deck is
 *       an <em>edge</em> piece: it hugs a straight run of bulkhead, its art sits
 *       in the wall-side strip of its cells, and the cell inboard of it stays
 *       clear of other edge pieces. Stored cargo lines only passages wide enough to
 *       keep two clear cells past it. Mid-lane pieces are flat patches only.</li>
 *   <li>Firing ranges are never dressed: their lanes are shut deck.</li>
 * </ul>
 */
final class ShipDeckDressing {

    /** Where a piece sits. Both draw beneath the deck's own fixtures. */
    enum Placement { FLOOR, EDGE }

    /**
     * One dressing kind: what it shows, the art it asks for, and the stand-in
     * that has shipped since before it, so a sheet that has not been packed yet
     * leaves a worse-looking deck rather than a bare one.
     */
    enum Kind {
        // The company camps in its passages: storage stands along both walls, the lane stays clear.
        // First in the queue, so a passage wall goes to storage before smaller pieces claim it.
        CRATE_STACK(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.crate-stack", null, 5, true),
        PALLET(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.pallet", null, 5, true),
        DUFFELS(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.duffels", null, 5, true),
        AMMO_CANS(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.ammo-cans", null, 5, true),
        NETTED_CARGO(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.netted-cargo", null, 5, true),
        BEDROLL_CAMP(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.bedroll-camp", null, 5, true),
        // Single pieces fill the gaps the long stacks leave.
        CRATE(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.crate", null, 4, true),
        AMMO_PAIR(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.ammo-pair", null, 5, true),
        DUFFEL(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.duffel", null, 5, true),
        DRUM(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.drum", null, 6, true),
        CABLE_COIL(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.cable-coil", null, 8, true),
        TOOLBOX(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.toolbox", null, 9, true),
        DEAD_PANEL(ShipCore.POWER, Placement.EDGE, "ship-dressing.dead-panel", null, 13, false),
        CONDUIT(ShipCore.POWER, Placement.EDGE, "ship-dressing.conduit", null, 17, false),
        WALL_GRIME(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-hold.wall-grime", null, 9, false),
        GRILLE(ShipCore.LIFE_SUPPORT, Placement.EDGE, "ship-dressing.grille", null, 19, false),
        BUCKLED_PANEL(ShipCore.STRUCTURE, Placement.EDGE, "ship-dressing.buckled-panel", null, 15, false),
        BRACING(ShipCore.STRUCTURE, Placement.EDGE, "ship-dressing.bracing", null, 23, false),
        WALL_PATCH(ShipCore.STRUCTURE, Placement.EDGE, "ship-hold.wall-patch", null, 11, false),
        PATCH(ShipCore.STRUCTURE, Placement.FLOOR, "ship-dressing.hull-patch", "ship-wear.patch", 26, true),
        CABLE_SPILL(ShipCore.COMPUTE, Placement.FLOOR, "ship-dressing.cable-spill", null, 0, false),
        CABLE_RUN(ShipCore.COMPUTE, Placement.EDGE, "ship-dressing.cable-run", null, 21, false);

        final ShipCore core;
        final Placement placement;
        final String art;
        final String standIn;
        /** One anchor in this many eligible cells, before overlap; zero places only beside a console. */
        final int rarity;
        /**
         * Passages only: patched plating and stored cargo belong in the corridors.
         * A passage edge piece also leaves two clear cells inboard of it, so storage
         * only lines a passage wide enough to keep a lane past it.
         */
        final boolean passagesOnly;

        Kind(ShipCore core, Placement placement, String art, String standIn, int rarity, boolean passagesOnly) {
            this.core = core; this.placement = placement; this.art = art; this.standIn = standIn;
            this.rarity = rarity; this.passagesOnly = passagesOnly;
        }
    }

    /**
     * One placed piece. {@code wallDx, wallDy} name the bulkhead an edge piece
     * hugs (zero for a floor stain); {@code quarterTurns} turns its canonical
     * north-facing art onto that wall.
     */
    record Piece(Kind kind, DoodadDef def, int x, int y, int quarterTurns, boolean mirrored,
                 int wallDx, int wallDy) {
        Doodad doodad() { return new Doodad(x, y, def, quarterTurns, mirrored); }
    }

    /** What a deck looks like to the planner: a handful of per-cell facts, no simulation. */
    record Deck(int width, int height, boolean[] walkable, boolean[] wall, int[] region,
                boolean[] excludedRegion, List<int[]> doors, List<int[]> keepClear, List<Doodad> consoles) {
        static final int OUTSIDE = -2, PASSAGE = -1;
        int index(int x, int y) { return y * width + x; }
        boolean in(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }
    }

    private final List<Piece> pieces;
    private final List<Doodad> consoles;
    private final boolean[] hazeFloor;
    final int width, height;
    private final Map<CoreDressing, List<Doodad>> drawn = new HashMap<>();
    /** Each fitted piece's worn twin, where its sheet has one. */
    private Map<Doodad, Doodad> worn = Map.of();

    private ShipDeckDressing(List<Piece> pieces, List<Doodad> consoles, boolean[] hazeFloor, int width, int height) {
        this.pieces = List.copyOf(pieces);
        this.consoles = List.copyOf(consoles);
        this.hazeFloor = hazeFloor;
        this.width = width; this.height = height;
    }

    /** Every piece of the full Deficit plan, whatever is restored. */
    List<Piece> pieces() { return pieces; }

    /** The pieces a ship in this condition shows, in paint order. */
    List<Piece> pieces(CoreDressing state) {
        List<Piece> shown = new ArrayList<>();
        for (Piece piece : pieces) if (!state.restored(piece.kind().core)) shown.add(piece);
        return shown;
    }

    /** Render placements for a state; built once per state and kept. */
    synchronized List<Doodad> doodads(CoreDressing state) {
        return drawn.computeIfAbsent(state, s -> pieces(s).stream().map(Piece::doodad).toList());
    }

    /** The art a fitted piece shows while Life support is at Deficit: its worn twin, or itself. */
    Doodad worn(Doodad placed) {
        return worn.getOrDefault(placed, placed);
    }

    /**
     * Pairs each placement with its {@code <id>-worn} twin, set on the same
     * cells, turn and mirror. A twin is art only: the placement keeps its own
     * footprint, cover and blocking.
     */
    static Map<Doodad, Doodad> wornTwins(List<Doodad> placed, Function<String, DoodadDef> registry) {
        Map<Doodad, Doodad> twins = new IdentityHashMap<>();
        for (Doodad doodad : placed) {
            if (doodad.definitionId() == null) continue;
            DoodadDef def = registry.apply(doodad.definitionId() + "-worn");
            if (def != null) twins.put(doodad, new Doodad(doodad.cellX, doodad.cellY, def,
                    doodad.quarterTurns, doodad.mirrored));
        }
        return twins;
    }

    /** Screens and terminals that read as dead while Compute is at Deficit. */
    List<Doodad> deadConsoles(CoreDressing state) { return state.compute() ? List.of() : consoles; }

    /** Whether dead air hangs over this cell while Life support is at Deficit. */
    boolean hazeFloor(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && hazeFloor[y * width + x];
    }

    /** The production deck, read through the registry the deck was furnished from. */
    static ShipDeckDressing of(CellTopology topology, NavigationGrid grid, DeckGraph graph,
                               List<Gantry> gantries, List<Doodad> doodads, long seed) {
        TileRegistry registry = TileRegistry.installed();
        Function<String, DoodadDef> lookup = id -> registry == null ? null : registry.doodad(id);
        ShipDeckDressing dressing = plan(read(topology, grid, graph, gantries, doodads), seed, lookup);
        dressing.worn = wornTwins(doodads, lookup);
        return dressing;
    }

    static Deck read(CellTopology topology, NavigationGrid grid, DeckGraph graph,
                     List<Gantry> gantries, List<Doodad> doodads) {
        int width = topology.getWidth(), height = topology.getHeight();
        boolean[] walkable = new boolean[width * height];
        boolean[] wall = new boolean[width * height];
        int[] region = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y * width + x;
            wall[i] = topology.isWall(x, y);
            boolean floor = !wall[i] && topology.getGroundKind(x, y) != GroundKind.VOID;
            walkable[i] = floor && grid.isWalkable(x, y);
            region[i] = floor ? Deck.PASSAGE : Deck.OUTSIDE;
        }
        int maxId = 0;
        List<int[]> doors = new ArrayList<>();
        List<int[]> keepClear = new ArrayList<>();
        if (graph != null) {
            for (var room : graph.compartments()) maxId = Math.max(maxId, room.id());
        }
        boolean[] excluded = new boolean[maxId + 1];
        if (graph != null) {
            for (var room : graph.compartments()) {
                for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                    if (x >= 0 && y >= 0 && x < width && y < height && room.contains(x, y)
                            && region[y * width + x] != Deck.OUTSIDE) region[y * width + x] = room.id();
                for (var door : room.doors()) doors.add(new int[]{door.x(), door.y()});
                excluded[room.id()] = graph.damaged(room.id()) || room.purpose() == RoomPurpose.FIRING_RANGE;
            }
            for (BayAperture aperture : graph.apertures()) {
                int half = aperture.widthCells() / 2 + 2;
                keepClear.add(new int[]{Math.round(aperture.centerX()) - half, Math.round(aperture.centerY()) - half,
                        Math.round(aperture.centerX()) + half, Math.round(aperture.centerY()) + half});
            }
        }
        for (Gantry gantry : gantries)
            keepClear.add(new int[]{gantry.left(), gantry.bottom(), gantry.right(), gantry.top()});
        List<Doodad> consoles = new ArrayList<>();
        for (Doodad doodad : doodads) if (isScreen(doodad.definitionId())) consoles.add(doodad);
        return new Deck(width, height, walkable, wall, region, excluded, doors, keepClear, consoles);
    }

    /** Fixtures whose job is a screen. Unpowered processing leaves them dark. */
    static boolean isScreen(String id) {
        if (id == null) return false;
        for (String word : new String[]{"console", "terminal", "display", "server", "monitor", "helm",
                "systems", "plot", "tactical-map", "data-cabinet", "engineering.control"})
            if (id.contains(word)) return true;
        return false;
    }

    static ShipDeckDressing plan(Deck deck, long seed, Function<String, DoodadDef> registry) {
        int width = deck.width(), height = deck.height();
        boolean[] eligible = new boolean[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = deck.index(x, y);
            int region = deck.region()[i];
            eligible[i] = deck.walkable()[i] && region != Deck.OUTSIDE
                    && (region < 0 || region >= deck.excludedRegion().length || !deck.excludedRegion()[region]);
        }
        boolean[] haze = eligible.clone();
        for (int[] door : deck.doors()) clear(eligible, deck, door[0] - 1, door[1] - 1, door[0] + 1, door[1] + 1);
        for (int[] rect : deck.keepClear()) clear(eligible, deck, rect[0], rect[1], rect[2], rect[3]);

        Map<Kind, DoodadDef> defs = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            DoodadDef def = registry.apply(kind.art);
            if (def == null && kind.standIn != null) def = registry.apply(kind.standIn);
            if (def != null) defs.put(kind, def);
        }
        boolean[] used = new boolean[width * height];
        // An edge piece keeps the cell inboard of it free of other edge pieces, so
        // two pieces never face each other across a two-abreast passage.
        boolean[] inboard = new boolean[width * height];
        List<Piece> pieces = new ArrayList<>();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (!eligible[deck.index(x, y)]) continue;
            for (Kind kind : Kind.values()) {
                DoodadDef def = defs.get(kind);
                if (def == null || kind.rarity == 0) continue;
                long roll = hash(seed, x, y, kind.ordinal());
                if (Math.floorMod(roll, kind.rarity) != 0) continue;
                Piece piece = kind.placement == Placement.FLOOR
                        ? floor(deck, eligible, used, kind, def, x, y, roll)
                        : edge(deck, eligible, used, inboard, kind, def, x, y, roll);
                if (piece != null) { pieces.add(piece); break; }
            }
        }
        // Cables pulled down off the overhead gather at the screens they fed.
        DoodadDef spill = defs.get(Kind.CABLE_SPILL);
        if (spill != null) for (Doodad console : deck.consoles()) {
            long roll = hash(seed, console.cellX, console.cellY, 101);
            if (Math.floorMod(roll, 2) != 0) continue;
            int[][] around = {{console.footprintCellsX, 0}, {-spill.footprintCellsX, 0},
                    {0, console.footprintCellsY}, {0, -spill.footprintCellsY}};
            for (int k = 0; k < around.length; k++) {
                int[] at = around[(int) Math.floorMod(roll >>> 8, around.length) + k & 3];
                Piece piece = floor(deck, eligible, used, Kind.CABLE_SPILL, spill,
                        console.cellX + at[0], console.cellY + at[1], roll);
                if (piece != null) { pieces.add(piece); break; }
            }
        }
        return new ShipDeckDressing(pieces, deck.consoles(), haze, width, height);
    }

    private static void clear(boolean[] eligible, Deck deck, int x0, int y0, int x1, int y1) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                if (deck.in(x, y)) eligible[deck.index(x, y)] = false;
    }

    private static Piece floor(Deck deck, boolean[] eligible, boolean[] used, Kind kind, DoodadDef def,
                               int x, int y, long roll) {
        int turns = (int) Math.floorMod(roll >>> 16, 4);
        boolean turned = (turns & 1) == 1;
        int across = turned ? def.footprintCellsY : def.footprintCellsX;
        int down = turned ? def.footprintCellsX : def.footprintCellsY;
        int region = deck.in(x, y) ? deck.region()[deck.index(x, y)] : Deck.OUTSIDE;
        if (kind.passagesOnly && region != Deck.PASSAGE) return null;
        for (int dy = 0; dy < down; dy++) for (int dx = 0; dx < across; dx++) {
            int cx = x + dx, cy = y + dy;
            if (!deck.in(cx, cy)) return null;
            int i = deck.index(cx, cy);
            // A stain stays inside one room or one passage: it never crosses a threshold.
            if (!eligible[i] || used[i] || deck.region()[i] != region) return null;
        }
        mark(used, deck, x - 1, y - 1, x + across, y + down);
        return new Piece(kind, def, x, y, turns, ((roll >>> 20) & 1) == 1, 0, 0);
    }

    /**
     * An edge piece runs along a straight bulkhead: its canonical art has its
     * wall at the top of the frame and is turned onto the wall it found. Every cell of it must have that wall beside it, and the cells just
     * past each end must too, so it never reaches round a corner or into a door.
     */
    private static Piece edge(Deck deck, boolean[] eligible, boolean[] used, boolean[] inboard,
                              Kind kind, DoodadDef def, int x, int y, long roll) {
        int length = def.footprintCellsX, depth = def.footprintCellsY;
        if (kind.passagesOnly && deck.region()[deck.index(x, y)] != Deck.PASSAGE) return null;
        int lane = kind.passagesOnly ? 2 : 1;
        // North, east, south, west walls in turn, starting from a stable roll.
        int[][] walls = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int start = (int) Math.floorMod(roll >>> 12, 4);
        for (int k = 0; k < 4; k++) {
            int side = (start + k) & 3;
            int wx = walls[side][0], wy = walls[side][1];
            // Run along the wall: perpendicular to its outward normal.
            int ax = wy != 0 ? 1 : 0, ay = wx != 0 ? 1 : 0;
            boolean ok = true;
            for (int t = -1; t <= length && ok; t++) {
                int cx = x + ax * t, cy = y + ay * t;
                if (!deck.in(cx + wx, cy + wy) || !deck.wall()[deck.index(cx + wx, cy + wy)]) { ok = false; break; }
                if (t < 0 || t == length) continue;
                for (int d = 0; d < depth && ok; d++) {
                    int px = cx - wx * d, py = cy - wy * d;
                    if (!deck.in(px, py)) { ok = false; break; }
                    int i = deck.index(px, py);
                    ok = eligible[i] && !used[i] && !inboard[i];
                }
                for (int d = depth; d < depth + lane && ok; d++) {
                    int ix = cx - wx * d, iy = cy - wy * d;
                    ok = deck.in(ix, iy) && deck.walkable()[deck.index(ix, iy)];
                }
            }
            if (!ok) continue;
            int x0 = Math.min(x, x + ax * (length - 1) - wx * (depth - 1));
            int y0 = Math.min(y, y + ay * (length - 1) - wy * (depth - 1));
            int x1 = Math.max(x, x + ax * (length - 1) - wx * (depth - 1));
            int y1 = Math.max(y, y + ay * (length - 1) - wy * (depth - 1));
            mark(used, deck, x0 - ax, y0 - ay, x1 + ax, y1 + ay);
            for (int t = 0; t < length; t++) for (int d = depth; d <= depth + 1; d++) {
                int ix = x + ax * t - wx * d, iy = y + ay * t - wy * d;
                if (deck.in(ix, iy)) inboard[deck.index(ix, iy)] = true;
            }
            // Canonical art has its wall at the top of the frame. A deck draws cell
            // y upward on screen, so an unturned piece faces +y; a half turn puts it
            // on the -y wall, and each side round from there is one more quarter turn.
            return new Piece(kind, def, x0, y0, (side + 2) & 3, ((roll >>> 24) & 1) == 1, wx, wy);
        }
        return null;
    }

    private static void mark(boolean[] used, Deck deck, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++)
            if (deck.in(x, y)) used[deck.index(x, y)] = true;
    }

    /** SplitMix64 over the seed, the cell and a salt: stable, and independent of every RNG stream. */
    static long hash(long seed, int x, int y, int salt) {
        long z = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
