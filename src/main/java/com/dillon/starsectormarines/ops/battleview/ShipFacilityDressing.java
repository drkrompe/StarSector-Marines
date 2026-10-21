package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.FacilityDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.FacilityDressing.Facility;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * The Second Wind's facilities that take no compartment of their own, dressed
 * into the rooms that carry them: grow trays in the provisions store, the
 * wardroom's screened officers' end and the briefing corner (screen, plot
 * table, holo-tank) at the dining end of the mess, and racking in the hold.
 *
 * <p>Presentation only, the sibling of {@link ShipDeckDressing}. Nothing here
 * enters the simulation: no doodad is added to the map, no cell changes
 * walkability, cover or sight, and the crew's work is where generation put it.
 * The whole plan, every tier of every facility, is derived once from the deck
 * and its seed, and a {@link FacilityDressing} only chooses which tiers are
 * drawn, so buying a tier adds pieces and never moves one already standing.
 *
 * <p>Placement laws, each tested on a hand-made grid:
 * <ul>
 *   <li>A piece stands only on walkable, unfurnished floor of its own room, never
 *       on a work point or a seat, and never within two cells of a doorway.</li>
 *   <li>A wall piece backs onto bulkhead along its whole length and leaves the
 *       row inboard of it open floor; a free-standing piece keeps a ring of open
 *       floor all round it.</li>
 *   <li>Every piece, all tiers standing, leaves every work point and every cell
 *       of the room that could be walked to from its doors still walkable to
 *       around it. The crew walk the deck generation made, so this is what keeps
 *       them from ever needing to pass through a rack to reach their work.</li>
 *   <li>A wrecked room carries its own condition art and is not dressed.</li>
 * </ul>
 */
final class ShipFacilityDressing {

    // Grow trays: a shelf with a planter in each cell. A purpose-made hydroponic tray is missing art.
    static final String TRAY = "ship-utility.shelf";
    static final String PLANT = "crew-lounge.planter";
    // The wardroom: a bookcase screen, a sideboard, a wall screen for the staff college.
    static final String SCREEN = "crew-lounge.books";
    static final String SIDEBOARD = "crew-lounge.refreshments";
    static final String WALL_SCREEN = "crew-lounge.media";
    // The briefing room: a display on the end bulkhead and a lectern beside it, then the plot table in
    // the briefing corner, then the holo-tank's processor and console beside it. A plot table sized for
    // the mess's end and the tank's projection are missing art; the card table and utility units stand in.
    static final String DISPLAY = "briefing-room.display";
    static final String LECTERN = "briefing-room.lectern";
    static final String PLOT_TABLE = "crew-lounge.card-table";
    static final String[] HOLO_TANK = {"ship-utility.server", "ship-utility.console"};
    // The hold: a tally desk, stowed pallets, then racking with cargo in its bays.
    static final String TALLY = "main-stores.tally";
    static final String PALLET = "main-stores.pallet";
    static final String RACK = "main-stores.rack";
    static final String[] BAY_LOADS = {"main-stores.cartons", "main-stores.cases", "main-stores.drums"};
    static final String SHELF_LOAD = "ship-utility.parts";

    /** Trays a tier adds in total: Restored, Improved I, Improved II. */
    static final int[] TRAYS = {3, 6, 10};
    /** Racks the hold stands at Improved I and Improved II. */
    static final int[] RACKS = {1, 3};

    /** One placed piece and the facility tier that shows it. */
    record Piece(Facility facility, int tier, DoodadDef def, int x, int y, int quarterTurns) {
        Doodad doodad() { return new Doodad(x, y, def, quarterTurns, false); }
    }

    /** A room the plan may dress: its purpose, bounds and doorways. */
    record Room(int id, RoomPurpose purpose, int left, int top, int right, int bottom, List<int[]> doors) {
        int width() { return right - left + 1; }
        int height() { return bottom - top + 1; }
    }

    /**
     * What a deck looks like to the planner: walkability, bulkheads, which room
     * each cell is in, what is already furnished and where the work is.
     */
    record Deck(int width, int height, boolean[] walkable, boolean[] wall, int[] region,
                boolean[] furnished, boolean[] work, List<Room> rooms, List<Doodad> doodads) {
        static final int NONE = -1;
        int index(int x, int y) { return y * width + x; }
        boolean in(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }
    }

    private final List<Piece> pieces;
    private final Map<FacilityDressing, List<Doodad>> drawn = new HashMap<>();

    private ShipFacilityDressing(List<Piece> pieces) {
        this.pieces = List.copyOf(pieces);
    }

    /** Every piece of every tier. */
    List<Piece> pieces() { return pieces; }

    /** The pieces a ship with these tiers bought shows. */
    List<Piece> pieces(FacilityDressing state) {
        List<Piece> shown = new ArrayList<>();
        for (Piece piece : pieces) if (piece.tier() <= state.tier(piece.facility())) shown.add(piece);
        return shown;
    }

    /** Render placements for a state; built once per state and kept. */
    synchronized List<Doodad> doodads(FacilityDressing state) {
        return drawn.computeIfAbsent(state, s -> pieces(s).stream().map(Piece::doodad).toList());
    }

    /** The production deck, read through the registry the deck was furnished from. */
    static ShipFacilityDressing of(CellTopology topology, NavigationGrid grid, DeckGraph graph,
                                   List<Doodad> doodads, List<FixtureTask> tasks, long seed) {
        TileRegistry registry = TileRegistry.installed();
        Function<String, DoodadDef> lookup = id -> registry == null ? null : registry.doodad(id);
        return plan(read(topology, grid, graph, doodads, tasks), seed, lookup);
    }

    static Deck read(CellTopology topology, NavigationGrid grid, DeckGraph graph,
                     List<Doodad> doodads, List<FixtureTask> tasks) {
        int width = topology.getWidth(), height = topology.getHeight();
        boolean[] walkable = new boolean[width * height];
        boolean[] wall = new boolean[width * height];
        int[] region = new int[width * height];
        Arrays.fill(region, Deck.NONE);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = y * width + x;
            wall[i] = topology.isWall(x, y);
            walkable[i] = !wall[i] && grid.isWalkable(x, y);
        }
        List<Room> rooms = new ArrayList<>();
        if (graph != null) for (var room : graph.compartments()) {
            if (graph.damaged(room.id()) || graph.reserved(room.id())) continue;
            List<int[]> doors = new ArrayList<>();
            for (var door : room.doors()) doors.add(new int[]{door.x(), door.y()});
            rooms.add(new Room(room.id(), room.purpose(), room.left(), room.top(), room.right(), room.bottom(), doors));
            for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                if (x >= 0 && y >= 0 && x < width && y < height && room.contains(x, y)) region[y * width + x] = room.id();
        }
        boolean[] furnished = new boolean[width * height];
        for (Doodad d : doodads) {
            // Flat wear and trim on the deck is not furniture; anything with cover or height is.
            if (d.cover == 0 && d.ballisticHalfHeight == 0) continue;
            for (int dy = 0; dy < d.footprintCellsY; dy++) for (int dx = 0; dx < d.footprintCellsX; dx++) {
                int x = d.cellX + dx, y = d.cellY + dy;
                if (x >= 0 && y >= 0 && x < width && y < height) furnished[y * width + x] = true;
            }
        }
        boolean[] work = new boolean[width * height];
        for (FixtureTask task : tasks)
            if (task.cellX() >= 0 && task.cellY() >= 0 && task.cellX() < width && task.cellY() < height)
                work[task.cellY() * width + task.cellX()] = true;
        return new Deck(width, height, walkable, wall, region, furnished, work, rooms, doodads);
    }

    static ShipFacilityDressing plan(Deck deck, long seed, Function<String, DoodadDef> registry) {
        Planner planner = new Planner(deck, seed, registry);
        planner.room(RoomPurpose.LOADING_BAY).ifPresent(planner::hydroponics);
        planner.room(RoomPurpose.MESS_HALL).ifPresent(planner::mess);
        planner.room(RoomPurpose.STOCKROOM).ifPresent(planner::holds);
        return new ShipFacilityDressing(planner.pieces);
    }

    /** Bulkhead directions: north (-y), east (+x), south (+y), west (-x). */
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** One rectangle a piece could stand on, and the bulkhead it backs onto (-1 for free-standing). */
    private record Spot(int x, int y, int w, int h, int side) {
        double cx() { return x + w / 2.0; }
        double cy() { return y + h / 2.0; }
    }

    private static final class Planner {
        final Deck deck;
        final long seed;
        final Function<String, DoodadDef> registry;
        final boolean[] claimed;
        final List<Piece> pieces = new ArrayList<>();
        Room room;
        boolean[] doorZone;
        boolean[] reachable;

        Planner(Deck deck, long seed, Function<String, DoodadDef> registry) {
            this.deck = deck;
            this.seed = seed;
            this.registry = registry;
            this.claimed = new boolean[deck.width() * deck.height()];
        }

        /** The largest room of a purpose, the room the refit reveal flies to. */
        Optional<Room> room(RoomPurpose purpose) {
            return deck.rooms().stream().filter(r -> r.purpose() == purpose)
                    .max(Comparator.comparingInt((Room r) -> r.width() * r.height()).thenComparingInt(r -> -r.id()));
        }

        private void enter(Room room) {
            this.room = room;
            doorZone = new boolean[claimed.length];
            for (int[] door : room.doors())
                for (int y = door[1] - 2; y <= door[1] + 2; y++) for (int x = door[0] - 2; x <= door[0] + 2; x++)
                    if (deck.in(x, y)) doorZone[deck.index(x, y)] = true;
            reachable = reach();
        }

        // --- Hydroponics: grow trays along the provisions store's bulkheads, away from the hatch ---

        void hydroponics(Room store) {
            enter(store);
            DoodadDef tray = registry.apply(TRAY), plant = registry.apply(PLANT);
            if (tray == null) return;
            int placed = 0;
            for (int tier = 1; tier <= FacilityDressing.TOP; tier++) {
                for (; placed < TRAYS[tier - 1]; placed++) {
                    Spot spot = best(wallSpots(tray), s -> -doorDistance(s) * 4 + touching(s, TRAY) * -6);
                    if (spot == null) break;
                    Doodad shelf = wallPiece(Facility.HYDROPONICS, tier, tray, spot);
                    if (plant != null) for (int k = 0; k < Math.max(spot.w(), spot.h()); k++) {
                        int px = spot.w() >= spot.h() ? spot.x() + k : spot.x();
                        int py = spot.w() >= spot.h() ? spot.y() : spot.y() + k;
                        layer(Facility.HYDROPONICS, tier, plant, px, py, shelf.quarterTurns);
                    }
                }
            }
        }

        // --- The mess: the briefing screen on the far bulkhead, the wardroom screened off beside it ---

        void mess(Room mess) {
            enter(mess);
            boolean alongX = mess.width() >= mess.height();
            // The far end is the dining end: away from the galley, or away from the hatch on a mess without one.
            double centre = alongX ? (mess.left() + mess.right() + 1) / 2.0 : (mess.top() + mess.bottom() + 1) / 2.0;
            double galley = mean(id -> id.startsWith("mess-galley.") && !id.equals("mess-galley.table")
                    && !id.equals("mess-galley.stool") && !id.equals("mess-galley.meals"), alongX);
            if (Double.isNaN(galley)) galley = doorMean(alongX);
            boolean farHigh = Double.isNaN(galley) || galley < centre;
            int farSide = alongX ? (farHigh ? 1 : 3) : (farHigh ? 2 : 0);
            // The wardroom takes the half of the beam away from the hatches.
            double acrossCentre = alongX ? (mess.top() + mess.bottom() + 1) / 2.0 : (mess.left() + mess.right() + 1) / 2.0;
            double doorsAcross = doorMean(!alongX);
            boolean wardHigh = Double.isNaN(doorsAcross) || doorsAcross < acrossCentre;

            DoodadDef display = registry.apply(DISPLAY);
            Spot shown = null;
            if (display != null) {
                shown = best(sided(wallSpots(display), farSide), s -> Math.abs(across(s, alongX) - acrossCentre));
                if (shown != null) wallPiece(Facility.BRIEFING, 1, display, shown);
            }
            DoodadDef lectern = registry.apply(LECTERN);
            if (lectern != null) {
                Spot anchor = shown;
                Spot spot = best(sided(wallSpots(lectern), farSide), s -> anchor == null
                        ? Math.abs(across(s, alongX) - acrossCentre)
                        : Math.abs(across(s, alongX) - across(anchor, alongX)) + (inHalf(s, alongX, acrossCentre, wardHigh) ? 50 : 0));
                if (spot != null) wallPiece(Facility.BRIEFING, 1, lectern, spot);
            }

            // Improved I: the plot table in the briefing corner, against the end bulkhead by the display.
            DoodadDef plot = registry.apply(PLOT_TABLE);
            Spot table = null;
            if (plot != null) {
                Spot anchor = shown;
                table = best(sided(wallSpots(plot), farSide), s -> (inHalf(s, alongX, acrossCentre, wardHigh) ? 50 : 0)
                        + (anchor == null ? 0 : Math.abs(across(s, alongX) - across(anchor, alongX))));
                if (table == null) table = best(wallSpots(plot), s -> -3 * farness(s, alongX, farHigh)
                        + (inHalf(s, alongX, acrossCentre, wardHigh) ? 50 : 0));
                if (table != null) wallPiece(Facility.BRIEFING, 2, plot, table);
            }
            // Improved II: the holo-tank built on it, its processor by the table and its console by the processor.
            Spot near = table != null ? table : shown;
            for (String id : HOLO_TANK) {
                DoodadDef unit = registry.apply(id);
                if (unit == null) continue;
                Spot by = near;
                Spot spot = best(wallSpots(unit), s -> by == null ? -farness(s, alongX, farHigh)
                        : Math.abs(s.cx() - by.cx()) + Math.abs(s.cy() - by.cy()));
                if (spot == null) continue;
                wallPiece(Facility.BRIEFING, 3, unit, spot);
                near = spot;
            }

            // Restored: a bookcase screen across the lane in front of the last tables, in the wardroom's half.
            DoodadDef screen = registry.apply(SCREEN);
            if (screen != null) {
                int lane = officersLane(alongX, farHigh);
                int screens = 0;
                if (lane != Integer.MIN_VALUE) {
                    for (Spot spot : laneSpots(screen, lane, alongX)) {
                        if (screens == 2) break;
                        if (!inHalf(spot, alongX, acrossCentre, wardHigh) || !fits(spot, false) || !keepsWay(spot)) continue;
                        claim(spot);
                        pieces.add(new Piece(Facility.WARDROOM, 1, screen, spot.x(), spot.y(), alongX ? 0 : 1));
                        screens++;
                    }
                }
                // A mess without a lane before its last tables screens its end bulkhead instead.
                if (screens == 0) {
                    Spot spot = best(sided(wallSpots(screen), farSide),
                            s -> inHalf(s, alongX, acrossCentre, wardHigh) ? -corner(s, alongX, wardHigh) : 1e6);
                    if (spot != null) wallPiece(Facility.WARDROOM, 1, screen, spot);
                }
            }
            // Improved I: a sideboard against the end bulkhead, a planter in the corner.
            DoodadDef sideboard = registry.apply(SIDEBOARD);
            if (sideboard != null) {
                Spot spot = best(wallSpots(sideboard), s -> (inHalf(s, alongX, acrossCentre, wardHigh) ? 0 : 1e6)
                        + (s.side() == farSide ? 0 : 20) - corner(s, alongX, wardHigh) - farness(s, alongX, farHigh));
                if (spot != null) wallPiece(Facility.WARDROOM, 2, sideboard, spot);
            }
            DoodadDef plant = registry.apply(PLANT);
            if (plant != null) {
                Spot spot = best(wallSpots(plant), s -> (inHalf(s, alongX, acrossCentre, wardHigh) ? 0 : 1e6)
                        - corner(s, alongX, wardHigh) - 3 * farness(s, alongX, farHigh));
                if (spot != null) wallPiece(Facility.WARDROOM, 2, plant, spot);
            }
            // Improved II: the staff college's wall screen on the long bulkhead of the officers' end.
            DoodadDef media = registry.apply(WALL_SCREEN);
            int longSide = alongX ? (wardHigh ? 2 : 0) : (wardHigh ? 1 : 3);
            if (media != null) {
                Spot spot = best(sided(wallSpots(media), longSide), s -> -farness(s, alongX, farHigh));
                if (spot != null) wallPiece(Facility.WARDROOM, 3, media, spot);
            }
            if (plant != null) {
                Spot spot = best(wallSpots(plant), s -> (inHalf(s, alongX, acrossCentre, wardHigh) ? 0 : 1e6)
                        - 3 * farness(s, alongX, farHigh));
                if (spot != null) wallPiece(Facility.WARDROOM, 3, plant, spot);
            }
        }

        /**
         * The lane between the last rank of tables and the one before it, as
         * an along-the-room coordinate, or {@link Integer#MIN_VALUE} when the
         * dining floor has fewer than two ranks.
         */
        private int officersLane(boolean alongX, boolean farHigh) {
            TreeSet<Integer> ranks = new TreeSet<>();
            for (Doodad d : deck.doodads()) {
                if (!"mess-galley.table".equals(d.definitionId()) || !inRoom(d.cellX, d.cellY)) continue;
                for (int k = 0; k < (alongX ? d.footprintCellsX : d.footprintCellsY); k++)
                    ranks.add((alongX ? d.cellX : d.cellY) + k);
            }
            if (ranks.isEmpty()) return Integer.MIN_VALUE;
            // Walk in from the far end past the last rank's own cells to the first gap.
            Integer at = farHigh ? ranks.last() : ranks.first();
            while (at != null && ranks.contains(at)) at = farHigh ? at - 1 : at + 1;
            if (at == null) return Integer.MIN_VALUE;
            Integer before = farHigh ? ranks.lower(at) : ranks.higher(at);
            return before == null ? Integer.MIN_VALUE : at;
        }

        /** Places for a piece standing across the room on one along-the-room line, nearest the far bulkhead first. */
        private List<Spot> laneSpots(DoodadDef def, int lane, boolean alongX) {
            int length = Math.max(def.footprintCellsX, def.footprintCellsY);
            List<Spot> spots = new ArrayList<>();
            if (alongX) for (int y = room.top(); y + length - 1 <= room.bottom(); y++) spots.add(new Spot(lane, y, 1, length, -1));
            else for (int x = room.left(); x + length - 1 <= room.right(); x++) spots.add(new Spot(x, lane, length, 1, -1));
            return spots;
        }

        // --- The hold: a tally desk and stowed pallets, then racking ---

        void holds(Room hold) {
            enter(hold);
            DoodadDef tally = registry.apply(TALLY), pallet = registry.apply(PALLET), rack = registry.apply(RACK);
            DoodadDef tray = registry.apply(TRAY), parts = registry.apply(SHELF_LOAD);
            boolean dressed = false;
            if (tally != null) {
                Spot spot = best(wallSpots(tally), s -> doorDistance(s));
                if (spot != null) { wallPiece(Facility.HOLDS, 1, tally, spot); dressed = true; }
            }
            if (pallet != null) {
                Spot spot = best(wallSpots(pallet), s -> doorDistance(s) * 0.5 + touching(s, TALLY) * -4);
                if (spot != null) {
                    wallPiece(Facility.HOLDS, 1, pallet, spot);
                    load(Facility.HOLDS, 1, spot, 0);
                    dressed = true;
                }
            }
            if (!dressed) shelves(1, tray, parts, 2);
            int racks = 0;
            for (int tier = 2; tier <= FacilityDressing.TOP; tier++) {
                int before = racks;
                if (rack != null) for (; racks < RACKS[tier - 2]; racks++) {
                    List<Spot> spots = new ArrayList<>(wallSpots(rack));
                    spots.addAll(islandSpots(rack));
                    // Racks stand back from the hatch, along a bulkhead before out on the floor, in rows.
                    Spot spot = best(spots, s -> -doorDistance(s) + (s.side() < 0 ? 6 : 0) - touchingAisle(s) * 3);
                    if (spot == null) break;
                    if (spot.side() >= 0) wallPiece(Facility.HOLDS, tier, rack, spot);
                    else {
                        claim(spot);
                        pieces.add(new Piece(Facility.HOLDS, tier, rack, spot.x(), spot.y(), spot.w() > spot.h() ? 1 : 0));
                    }
                    for (int bay = 0; bay < 3; bay++) {
                        int bx = spot.w() > spot.h() ? spot.x() + bay * 2 : spot.x();
                        int by = spot.w() > spot.h() ? spot.y() : spot.y() + bay * 2;
                        load(Facility.HOLDS, tier, new Spot(bx, by, 2, 2, -1), racks + bay + tier);
                    }
                }
                // Too little floor for a rack: a tier still racks something, in shelving along the bulkheads.
                if (racks == before) shelves(tier, tray, parts, 3);
            }
        }

        private void shelves(int tier, DoodadDef tray, DoodadDef parts, int count) {
            if (tray == null) return;
            for (int k = 0; k < count; k++) {
                Spot spot = best(wallSpots(tray), s -> -doorDistance(s) - touching(s, TRAY) * 6);
                if (spot == null) return;
                Doodad shelf = wallPiece(Facility.HOLDS, tier, tray, spot);
                if (parts != null) pieces.add(new Piece(Facility.HOLDS, tier, parts, spot.x(), spot.y(), shelf.quarterTurns));
            }
        }

        /** Cargo laid in a two-by-two bay. */
        private void load(Facility facility, int tier, Spot bay, int pick) {
            DoodadDef def = registry.apply(BAY_LOADS[Math.floorMod(ShipDeckDressing.hash(seed, bay.x(), bay.y(), pick), BAY_LOADS.length)]);
            if (def == null) def = registry.apply(BAY_LOADS[0]);
            if (def != null) pieces.add(new Piece(facility, tier, def, bay.x(), bay.y(), 0));
        }

        // --- Placement ---

        /** Every place a piece fits against a bulkhead, its length along it. */
        private List<Spot> wallSpots(DoodadDef def) {
            int length = Math.max(def.footprintCellsX, def.footprintCellsY);
            int depth = Math.min(def.footprintCellsX, def.footprintCellsY);
            List<Spot> spots = new ArrayList<>();
            for (int side = 0; side < 4; side++) {
                int w = SIDES[side][0] == 0 ? length : depth, h = SIDES[side][0] == 0 ? depth : length;
                for (int y = room.top(); y + h - 1 <= room.bottom(); y++)
                    for (int x = room.left(); x + w - 1 <= room.right(); x++) {
                        Spot spot = new Spot(x, y, w, h, side);
                        if (backed(spot) && fits(spot, true)) spots.add(spot);
                    }
            }
            return spots;
        }

        /** Every place a piece stands free on the floor with open deck all round it. */
        private List<Spot> islandSpots(DoodadDef def) {
            int length = Math.max(def.footprintCellsX, def.footprintCellsY);
            int depth = Math.min(def.footprintCellsX, def.footprintCellsY);
            List<Spot> spots = new ArrayList<>();
            for (int turn = 0; turn < 2; turn++) {
                int w = turn == 0 ? length : depth, h = turn == 0 ? depth : length;
                for (int y = room.top(); y + h - 1 <= room.bottom(); y++)
                    for (int x = room.left(); x + w - 1 <= room.right(); x++) {
                        Spot spot = new Spot(x, y, w, h, -1);
                        if (fits(spot, false) && ringed(spot)) spots.add(spot);
                    }
            }
            return spots;
        }

        private static List<Spot> sided(List<Spot> spots, int side) {
            return spots.stream().filter(s -> s.side() == side).toList();
        }

        /** The whole of the piece's back is bulkhead. */
        private boolean backed(Spot s) {
            int dx = SIDES[s.side()][0], dy = SIDES[s.side()][1];
            for (int y = s.y(); y < s.y() + s.h(); y++) for (int x = s.x(); x < s.x() + s.w(); x++) {
                boolean edge = dx > 0 ? x == s.x() + s.w() - 1 : dx < 0 ? x == s.x() : dy > 0 ? y == s.y() + s.h() - 1 : y == s.y();
                if (!edge) continue;
                int bx = x + dx, by = y + dy;
                if (!deck.in(bx, by) || !deck.wall()[deck.index(bx, by)]) return false;
            }
            return true;
        }

        /**
         * Every cell is open, unfurnished, unworked, unclaimed floor of this room
         * away from its doors; a wall piece also needs open floor along its front.
         */
        private boolean fits(Spot s, boolean front) {
            for (int y = s.y(); y < s.y() + s.h(); y++) for (int x = s.x(); x < s.x() + s.w(); x++)
                if (!open(x, y) || deck.work()[deck.index(x, y)] || doorZone[deck.index(x, y)]) return false;
            if (!front) return true;
            int dx = -SIDES[s.side()][0], dy = -SIDES[s.side()][1];
            for (int y = s.y(); y < s.y() + s.h(); y++) for (int x = s.x(); x < s.x() + s.w(); x++) {
                int fx = x + dx, fy = y + dy;
                if (fx >= s.x() && fx < s.x() + s.w() && fy >= s.y() && fy < s.y() + s.h()) continue;
                if (!open(fx, fy)) return false;
            }
            return true;
        }

        /** Open deck on every side of a free-standing piece, corners included. */
        private boolean ringed(Spot s) {
            for (int y = s.y() - 1; y <= s.y() + s.h(); y++) for (int x = s.x() - 1; x <= s.x() + s.w(); x++) {
                if (x >= s.x() && x < s.x() + s.w() && y >= s.y() && y < s.y() + s.h()) continue;
                if (!open(x, y)) return false;
            }
            return true;
        }

        private boolean open(int x, int y) {
            if (!inRoom(x, y)) return false;
            int i = deck.index(x, y);
            return deck.walkable()[i] && !deck.furnished()[i] && !claimed[i];
        }

        private boolean inRoom(int x, int y) {
            return deck.in(x, y) && deck.region()[deck.index(x, y)] == room.id();
        }

        /** The best-scoring spot that still leaves the room's way through; ties by the deck's seed. */
        private Spot best(List<Spot> spots, ToDoubleFunction<Spot> score) {
            List<Spot> order = new ArrayList<>(spots);
            order.sort(Comparator.comparingDouble(score)
                    .thenComparingLong(s -> ShipDeckDressing.hash(seed, s.x(), s.y(), s.w() * 7 + s.h() + room.id())));
            for (Spot spot : order) if (fits(spot, spot.side() >= 0) && keepsWay(spot)) return spot;
            return null;
        }

        /** Whether standing this piece leaves every reachable cell of the room still reachable around it. */
        private boolean keepsWay(Spot s) {
            claim(s);
            boolean[] after = reach();
            boolean kept = true;
            for (int i = 0; i < reachable.length && kept; i++)
                if (reachable[i] && !after[i] && !claimed[i]) kept = false;
            unclaim(s);
            return kept;
        }

        /** The room's walkable cells reached from its doorways, with the claimed pieces standing. */
        private boolean[] reach() {
            boolean[] seen = new boolean[claimed.length];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            for (int[] door : room.doors()) {
                if (!deck.in(door[0], door[1])) continue;
                seen[deck.index(door[0], door[1])] = true;
                queue.add(door);
            }
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                for (int[] step : SIDES) {
                    int x = c[0] + step[0], y = c[1] + step[1];
                    if (!inRoom(x, y)) continue;
                    int i = deck.index(x, y);
                    if (seen[i] || !deck.walkable()[i] || claimed[i]) continue;
                    seen[i] = true;
                    queue.add(new int[]{x, y});
                }
            }
            for (int[] door : room.doors()) if (deck.in(door[0], door[1])) seen[deck.index(door[0], door[1])] = false;
            return seen;
        }

        private void claim(Spot s) { mark(s, true); }
        private void unclaim(Spot s) { mark(s, false); }

        private void mark(Spot s, boolean value) {
            for (int y = s.y(); y < s.y() + s.h(); y++) for (int x = s.x(); x < s.x() + s.w(); x++)
                claimed[deck.index(x, y)] = value;
        }

        /**
         * Stand a piece against its bulkhead. Canonical art has its wall at the
         * top of the frame and its length across it; art drawn long the other way
         * takes one more quarter turn so its length still runs along the wall.
         */
        private Doodad wallPiece(Facility facility, int tier, DoodadDef def, Spot s) {
            claim(s);
            int turns = (s.side() + 2) & 3;
            if (def.footprintCellsY > def.footprintCellsX) turns = (turns + 1) & 3;
            Piece piece = new Piece(facility, tier, def, s.x(), s.y(), turns);
            pieces.add(piece);
            return piece.doodad();
        }

        /** Something set on a piece already standing, sharing its cells. */
        private void layer(Facility facility, int tier, DoodadDef def, int x, int y, int turns) {
            pieces.add(new Piece(facility, tier, def, x, y, turns));
        }

        // --- Scores ---

        private double doorDistance(Spot s) {
            double best = 1e9;
            for (int[] door : room.doors()) best = Math.min(best, Math.abs(s.cx() - door[0] - .5) + Math.abs(s.cy() - door[1] - .5));
            return best == 1e9 ? 0 : best;
        }

        /** Cells of earlier pieces of this art beside this spot, so pieces gather into runs. */
        private int touching(Spot s, String id) {
            int count = 0;
            for (Piece piece : pieces) {
                Doodad d = piece.doodad();
                if (!id.equals(d.definitionId())) continue;
                boolean apartX = d.cellX > s.x() + s.w() || d.cellX + d.footprintCellsX < s.x();
                boolean apartY = d.cellY > s.y() + s.h() || d.cellY + d.footprintCellsY < s.y();
                if (!apartX && !apartY) count++;
            }
            return count;
        }

        /** Racks already standing two cells off this one along its length, so they read as an aisle. */
        private int touchingAisle(Spot s) {
            int count = 0;
            for (Piece piece : pieces) {
                Doodad d = piece.doodad();
                if (!RACK.equals(d.definitionId())) continue;
                boolean parallel = (d.footprintCellsX > d.footprintCellsY) == (s.w() > s.h());
                if (!parallel) continue;
                int gapX = Math.max(d.cellX - (s.x() + s.w()), s.x() - (d.cellX + d.footprintCellsX));
                int gapY = Math.max(d.cellY - (s.y() + s.h()), s.y() - (d.cellY + d.footprintCellsY));
                if (Math.max(gapX, gapY) <= 3) count++;
            }
            return count;
        }

        private double across(Spot s, boolean alongX) { return alongX ? s.cy() : s.cx(); }

        private boolean inHalf(Spot s, boolean alongX, double centre, boolean high) {
            return high ? across(s, alongX) >= centre : across(s, alongX) <= centre;
        }

        /** How far into its half's outer edge a spot sits, across the beam. */
        private double corner(Spot s, boolean alongX, boolean high) {
            return high ? across(s, alongX) : -across(s, alongX);
        }

        /** How close to the far bulkhead a spot sits, along the room. */
        private double farness(Spot s, boolean alongX, boolean farHigh) {
            double along = alongX ? s.cx() : s.cy();
            return farHigh ? along : -along;
        }

        /** The mean along- or across-the-room coordinate of this room's furniture of some kind. */
        private double mean(Predicate<String> kind, boolean alongX) {
            double sum = 0; int count = 0;
            for (Doodad d : deck.doodads()) {
                if (d.definitionId() == null || !kind.test(d.definitionId()) || !inRoom(d.cellX, d.cellY)) continue;
                sum += alongX ? d.cellX + d.footprintCellsX / 2.0 : d.cellY + d.footprintCellsY / 2.0;
                count++;
            }
            return count == 0 ? Double.NaN : sum / count;
        }

        private double doorMean(boolean xAxis) {
            double sum = 0;
            for (int[] door : room.doors()) sum += (xAxis ? door[0] : door[1]) + .5;
            return room.doors().isEmpty() ? Double.NaN : sum / room.doors().size();
        }
    }
}
