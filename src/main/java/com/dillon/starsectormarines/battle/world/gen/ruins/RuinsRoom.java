package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A room off a plant's service corridors, laid from what it did for the process ({@code ruins-expedition.md}, Every
 * room serves the process), as a works hall is laid from its line. Each kind is planned in local cells with its
 * hall side north: the wall it shares its work with (the line it watched, the samples it took in), or for a store
 * its back wall, the door to the south. The ground turns the plan to face its room ({@link WorksHallPlan#turned}).
 * Every room keeps its middle open, three cells square round {@code (kx, ky)}, where its way in arrives; its pieces
 * stand against its walls; it is one floor. Its theme ({@link RoomTheme}) gives it the process's own pieces and the
 * spill that spread when the plant died.
 *
 * <ul>
 *   <li>{@link #CONTROL}: the line's feeds come in through the hall-side wall to the mimic board, the shift watched
 *   it from desk runs either side, the shift lead's desk at the back. A desk run burned out when the plant died and
 *   a ceiling panel came down.</li>
 *   <li>{@link #WORKSHOP}: spares came in from the stores to the racks, rejects from the lab to the rework stand
 *   under its hoist, reworked units out to the line on the trolley. A unit still stands half stripped; the hoist's
 *   load fell.</li>
 *   <li>{@link #LAB}: samples came in through the hatch from the line, were tested on the theme's instrument and the
 *   benches, then kept in the sample store or binned as rejects for rework. The instrument failed and its spill
 *   spread; a shelf toppled.</li>
 *   <li>{@link #CREW}: the shift came off the line to its lockers, ate at the mess table off the galley, slept in the
 *   bunks at the back. Its last stand: the table tipped as a barricade, scorch round it.</li>
 *   <li>{@link #STORE}: stock came in through the blast door and was shelved along the back wall, tallied at the
 *   terminal by the door, drawn out to the workshop. A crate split where it fell.</li>
 * </ul>
 */
public enum RuinsRoom {
    CONTROL, WORKSHOP, LAB, CREW, STORE;

    /** The chamber roles in the order the rooms nearest the works hall take them: the control room overlooks it. */
    static final List<RuinsRoom> CHAMBER_ROLES = List.of(CONTROL, WORKSHOP, LAB, CREW);

    /**
     * The role of the {@code rank}-th nearest chamber to the works hall: one control room, the nearest; the rest a
     * workshop, a lab and crew quarters in turn.
     */
    static RuinsRoom chamberRole(int rank) {
        return rank == 0 ? CONTROL : CHAMBER_ROLES.get(1 + (rank - 1) % (CHAMBER_ROLES.size() - 1));
    }

    // ---- shared pieces, each with the stand-in laid until its art ships ------------------------------------

    static final String CONTROL_DESK = "doodad.ruins-control-desk", CONTROL_DESK_BURNT = "doodad.ruins-control-desk-burnt",
            SHIFT_DESK = "doodad.ruins-shift-desk", CEILING_PANEL = "doodad.ruins-ceiling-panel",
            SAMPLE_HATCH = "doodad.ruins-sample-hatch", FUME_HOOD = "doodad.ruins-fume-hood",
            LAB_BENCH = "doodad.ruins-lab-bench", SAMPLE_STORE = "doodad.ruins-sample-store",
            REJECT_BIN = "doodad.ruins-reject-bin", SHELF_TOPPLED = "doodad.ruins-shelf-toppled",
            REWORK_STAND = "doodad.ruins-rework-stand", WORKSHOP_HOIST = "doodad.ruins-workshop-hoist",
            HOIST_FALLEN = "doodad.ruins-hoist-fallen", TOOL_WALL = "doodad.ruins-tool-wall",
            PARTS_RACK = "doodad.ruins-parts-rack", PARTS_TROLLEY = "doodad.ruins-parts-trolley",
            CREW_LOCKER = "doodad.ruins-crew-locker", CREW_LOCKER_OPEN = "doodad.ruins-crew-locker-open",
            MESS_TABLE = "doodad.mess-table-long", TABLE_TIPPED = "doodad.ruins-mess-table-tipped",
            GALLEY = "doodad.galley-prep-bench", CREW_BUNK = "doodad.ruins-crew-bunk",
            STORE_SHELF = "doodad.ruins-store-shelf", STORE_RACK = "doodad.ruins-store-rack", TALLY = "doodad.ruins-tally-terminal",
            CRATE_SPLIT = "doodad.industrial-crate-stack-damaged", TRENCH = "doodad.ruins-trench-cover",
            SCORCH = "doodad.ruins-scorch-mark", OIL = "doodad.ruins-oil-stain", CHAIR = "doodad.chair-south-green",
            STOOL = "crew-lounge.stool";

    /**
     * This kind's plan in a room {@code width} by {@code length} cells, hall side north, its middle kept open round
     * {@code (kx, ky)}; the same seed plans the same room.
     */
    public WorksHallPlan plan(WorksHall works, int width, int length, int kx, int ky, long seed) {
        Room r = new Room(works, width, length, kx, ky, seed);
        switch (this) {
            case CONTROL -> r.control();
            case WORKSHOP -> r.workshop();
            case LAB -> r.lab();
            case CREW -> r.crew();
            case STORE -> r.store();
        }
        return r.plan;
    }

    /**
     * This kind's plan as the ground lays it over {@code area}: planned hall side north round the kept cell
     * {@code keep} (map cells), then turned {@code q} quarters to face its hall.
     */
    public WorksHallPlan laid(WorksHall works, RuinsDelve.Area area, RuinsDelve.Cell keep, int q) {
        int wc = q % 2 == 0 ? area.width() : area.height(), lc = q % 2 == 0 ? area.height() : area.width();
        int[] k = unturn(q, wc, lc, keep.x() - area.left(), keep.y() - area.bottom());
        return plan(works, wc, lc, k[0], k[1], RuinsDelveStage.worksSeed(area)).turned(q);
    }

    /** One room being planned. */
    private static final class Room {
        final WorksHallPlan plan;
        final RoomTheme theme;
        final int w, l, kx, ky;
        final long seed;

        Room(WorksHall works, int w, int l, int kx, int ky, long seed) {
            this.plan = new WorksHallPlan(works == null ? WorksHall.NONE : works, w, l);
            this.theme = RoomTheme.of(works);
            this.w = w;
            this.l = l;
            this.kx = kx;
            this.ky = ky;
            this.seed = seed;
        }

        /** Whether a footprint keeps off the room's open middle. */
        boolean clearOfMiddle(int x, int y, int pw, int ph) {
            return x > kx + 1 || x + pw - 1 < kx - 1 || y > ky + 1 || y + ph - 1 < ky - 1;
        }

        /** A standing piece, only where it keeps off the middle and the room stays one floor. */
        boolean stand(String id, String standIn, int x, int y, int pw, int ph, String station) {
            if (!clearOfMiddle(x, y, pw, ph) || !plan.stand(id, standIn, x, y, pw, ph, station)) return false;
            if (plan.oneFloor()) return true;
            plan.unlay();
            return false;
        }

        boolean stand(RoomTheme.Piece piece, int x, int y, int pw, int ph, String station) {
            return stand(piece.id(), piece.standIn(), x, y, pw, ph, station);
        }

        /** The first of {@code spots} ({x, y}) a piece stands at; false when none takes it. */
        boolean standFirst(String id, String standIn, int pw, int ph, String station, int[]... spots) {
            for (int[] s : spots) if (stand(id, standIn, s[0], s[1], pw, ph, station)) return true;
            return false;
        }

        /** The theme's spill spread from {@code (sx, sy)} out to {@code reach} cells, by coordinate noise. */
        void spill(int sx, int sy, int reach) {
            if (theme.spill() == null) return;
            for (int y = 0; y < l; y++) {
                for (int x = 0; x < w; x++) {
                    double d = Math.hypot(x - sx, y - sy);
                    if (d <= reach * (.55 + .6 * noise(x, y))) plan.skin(x, y, theme.spill());
                }
            }
        }

        double noise(int x, int y) {
            long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ seed;
            h ^= h >>> 31;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 29;
            return (h >>> 11) / (double) (1L << 53);
        }

        boolean flip() { return (seed & 1) != 0; }

        // ---- the kinds ------------------------------------------------------------------------------------

        void control() {
            plan.station("feeds").station("watch").station("shift lead");
            int top = l - 1, bx = (w - 3) / 2;
            // The line's feeds come in through the hall-side wall under a trench to the mimic board.
            stand(theme.board(), bx, top, 3, 1, "feeds");
            if (clearOfMiddle(bx, top - 2, 2, 2)) plan.floor(TRENCH, null, bx, top - 2, 2, 2, "feeds");
            // The shift's desk runs either side of the board, a chair at each; the west run burned out.
            boolean burnWest = !flip();
            for (int side = 0; side < 2; side++) {
                int x0 = side == 0 ? 0 : bx + 3, run = side == 0 ? bx : w - bx - 3;
                if (run < 1) continue;
                boolean burnt = (side == 0) == burnWest;
                if (burnt) {
                    stand(CONTROL_DESK_BURNT, "console-bank.straight-destroyed", x0, top, run, 1, WorksHallPlan.COLLAPSE);
                    plan.wear(SCORCH, x0, top - 2, 2, 2);
                } else {
                    stand(CONTROL_DESK, "console-bank.straight", x0, top, run, 1, "watch");
                    stand(CHAIR, null, x0 + run / 2, top - 1, 1, 1, "watch");
                }
            }
            // The shift lead's desk at the back, the chair at it.
            int lx = flip() ? w - 2 : 0;
            if (stand(SHIFT_DESK, "doodad.desk-1", lx, 0, 2, 1, "shift lead")) {
                stand(CHAIR, null, lx + (flip() ? 0 : 1), 1, 1, 1, "shift lead");
            }
            // A ceiling panel down across the far corner; the spill seeping in through the hall wall.
            standFirst(CEILING_PANEL, "doodad.ruins-blast-rubble", 2, 1, WorksHallPlan.COLLAPSE,
                    new int[]{flip() ? 0 : w - 2, 1}, new int[]{flip() ? 0 : w - 2, 2});
            spill(burnWest ? 0 : w - 1, top, 3);
        }

        void workshop() {
            plan.station("spares").station("rework").station("to the line");
            int top = l - 1;
            boolean east = !flip();
            int sideX = east ? 0 : w - 1;
            // Spares in from the stores on racks along the side wall.
            for (int y = 0; y + 1 < l; y += 3) stand(PARTS_RACK, "colony-furnishings.shelf", sideX, y, 1, 2, "spares");
            // The rework stand under its hoist, a unit half stripped on it; the tool wall beside.
            int sx = east ? w - 3 : 0;
            boolean stood = false;
            for (int y = top - 2; y >= 0 && !stood; y--) {
                if (!clearOfMiddle(sx, y, 3, 3)) continue;
                plan.floor(REWORK_STAND, "doodad.ruins-empty-cradle", sx, y, 3, 3, "rework");
                stood = stand(theme.spare(), sx, y, 3, 3, "rework");
                if (stood) {
                    plan.overhead(WORKSHOP_HOIST, null, sx, y, 3, 3);
                    plan.wear(OIL, east ? sx - 2 : sx + 3, y, 2, 2);
                }
            }
            if (!stood) stand(theme.stock(), east ? w - 2 : 0, top - 1, 2, 1, "rework");
            int tx = east ? 1 : w - 4;
            stand(TOOL_WALL, "armory-workshop.bench-tools", tx, top, 3, 1, "rework");
            // Reworked units out to the line on the trolley by the hall-side wall.
            standFirst(PARTS_TROLLEY, "auto-shop.tool-chest", 1, 1, "to the line",
                    new int[]{(w - 1) / 2, top}, new int[]{east ? 1 : w - 2, top - 1});
            // The hoist's load fell short of the stand.
            standFirst(HOIST_FALLEN, "doodad.ruins-blast-rubble", 2, 1, WorksHallPlan.COLLAPSE,
                    new int[]{east ? w - 3 : 1, 0}, new int[]{east ? w - 2 : 0, 0});
        }

        void lab() {
            plan.station("hatch").station("test").station("sample store");
            int top = l - 1, hx = (w - 2) / 2;
            boolean east = !flip();
            // Samples in through the hatch from the line, the reject bin beside it.
            stand(SAMPLE_HATCH, "doodad.armoury-issue-counter", hx, top, 2, 1, "hatch");
            stand(REJECT_BIN, "doodad.industrial-drum-cluster", east ? hx - 1 : hx + 2, top, 1, 1, "hatch");
            // The theme's instrument in the corner, the fume hood across, the bench along the back.
            int ix = east ? w - 2 : 0;
            boolean tested = stand(theme.instrument(), ix, top - 1, 2, 2, "test");
            stand(FUME_HOOD, "doodad.medical-scrub-sink", east ? 0 : w - 2, top, 2, 1, "test");
            stand(LAB_BENCH, "armory-workshop.bench", east ? 0 : w - 3, 0, 3, 1, "test");
            // Kept samples in the store on the instrument's side wall.
            standFirst(SAMPLE_STORE, "doodad.galley-cold-store", 1, 2, "sample store",
                    new int[]{east ? w - 1 : 0, top - 4}, new int[]{east ? w - 1 : 0, 1});
            // A shelf toppled across the back corner; the instrument's spill spread from it.
            standFirst(SHELF_TOPPLED, "colony-furnishings.shelf-destroyed", 2, 1, WorksHallPlan.COLLAPSE,
                    new int[]{east ? w - 2 : 0, 0}, new int[]{east ? w - 3 : 1, 0});
            if (tested) spill(ix + 1, top - 1, 3);
        }

        void crew() {
            plan.station("lockers").station("mess").station("bunks");
            int top = l - 1;
            boolean east = !flip();
            int lockerX = east ? 0 : w - 1;
            // The shift's lockers along the side wall by the way in, one stood open.
            int n = 0;
            for (int y = 0; y + 1 < l - 2; y += 2, n++) {
                stand(n == 1 ? CREW_LOCKER_OPEN : CREW_LOCKER,
                        n == 1 ? "barracks-bunks.locker-open" : "barracks-bunks.locker", lockerX, y, 1, 2, "lockers");
            }
            // The galley on the far side wall, the mess table off it with its stools.
            int gx = east ? w - 1 : 0;
            stand(GALLEY, null, gx, 0, 1, 3, "mess");
            int mx = east ? w - 5 : 2;
            if (!stand(MESS_TABLE, null, mx, top - 2, 3, 2, "mess")) {
                // No room for the table where it stood: it lies tipped on its side as the last stand's barricade.
                standFirst(TABLE_TIPPED, "doodad.desk-1-damaged", 3, 1, WorksHallPlan.COLLAPSE,
                        new int[]{mx, top - 1}, new int[]{mx, 1});
            } else {
                stand(STOOL, null, mx + 1, top - 3, 1, 1, "mess");
                standFirst(TABLE_TIPPED, "doodad.desk-1-damaged", 3, 1, WorksHallPlan.COLLAPSE,
                        new int[]{east ? w - 4 : 1, 1}, new int[]{east ? w - 5 : 2, 1});
            }
            plan.wear(SCORCH, east ? w - 4 : 1, 2, 2, 2);
            // Bunks at the back, along the hall-side wall.
            int bx = east ? 1 : w - 2;
            for (int k = 0; k < 2; k++) {
                stand(CREW_BUNK, "doodad.military-bunk", east ? bx + k : bx - k, top - 1, 1, 2, "bunks");
            }
        }

        void store() {
            plan.station("stock").station("tally");
            int top = l - 1;
            // Stock shelved along the back wall and up the side, the theme's own stock on the shelves.
            for (int x = 0; x + 1 < w; x += 3) {
                boolean own = (x / 3) % 2 == 0;
                stand(own ? theme.stock().id() : STORE_SHELF, own ? theme.stock().standIn() : "colony-furnishings.shelf",
                        x, top, 2, 1, "stock");
            }
            stand(STORE_RACK, "colony-furnishings.shelf", w - 1, 1, 1, 2, "stock");
            // Tallied at the terminal inside the door.
            stand(TALLY, "doodad.industrial-control-console", 0, 0, 1, 1, "tally");
            // A crate split where it fell off the shelf.
            standFirst(CRATE_SPLIT, null, 1, 1, WorksHallPlan.COLLAPSE, new int[]{0, top - 1}, new int[]{w - 1, 0});
        }
    }

    /** What a plant's rooms are ranked by: its works hall, else its first hall, else the vault. */
    static RuinsDelve.Area anchor(List<RuinsDelve.PlantHall> halls, RuinsDelve.Area vault) {
        RuinsDelve.Area anchor = null;
        for (RuinsDelve.PlantHall hall : halls) {
            boolean great = hall.stage() == RuinsDelve.PlantStage.WORKS || hall.stage() == RuinsDelve.PlantStage.FOUNDRY;
            if (great) return hall.footprint();
            if (anchor == null) anchor = hall.footprint();
        }
        return anchor == null ? vault : anchor;
    }

    /** The plain chambers nearest {@code anchor} first, ties broken by position; the rank is a chamber's role. */
    static List<RuinsDelve.Area> ranked(List<RuinsDelve.Area> plain, RuinsDelve.Area anchor) {
        int ax = anchor.centreX(), ay = anchor.centreY();
        List<RuinsDelve.Area> out = new ArrayList<>(plain);
        out.sort(Comparator.comparingLong((RuinsDelve.Area a) -> (long) (a.centreX() - ax) * (a.centreX() - ax)
                + (long) (a.centreY() - ay) * (a.centreY() - ay)).thenComparingInt(RuinsDelve.Area::left)
                .thenComparingInt(RuinsDelve.Area::bottom));
        return out;
    }

    /**
     * The quarter turns from north that face a room toward a point {@code (dx, dy)} cells away: north 0, east 1,
     * south 2, west 3, by its larger axis.
     */
    static int facing(int dx, int dy) {
        return Math.abs(dx) >= Math.abs(dy) ? (dx >= 0 ? 1 : 3) : (dy >= 0 ? 0 : 2);
    }

    /**
     * Where cell {@code (ax, ay)} of a room lies in its plan before the plan, {@code wc} by {@code lc}, is turned
     * {@code q} quarters ({@link WorksHallPlan#turned}): so a room's kept middle is planned where it will land.
     */
    static int[] unturn(int q, int wc, int lc, int ax, int ay) {
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new int[]{wc - 1 - ay, ax};
            case 2 -> new int[]{wc - 1 - ax, lc - 1 - ay};
            case 3 -> new int[]{ay, lc - 1 - ax};
            default -> new int[]{ax, ay};
        };
    }

    /** The plan's pieces of every kind and theme, for the art list: each id once, in kind order. */
    static List<String> ids(WorksHall works) {
        List<String> out = new ArrayList<>();
        for (RuinsRoom kind : values()) {
            for (WorksHallPlan.Piece p : kind.plan(works, 10, 8, 4, 3, 7L).pieces()) {
                if (!out.contains(p.id())) out.add(p.id());
            }
        }
        return out;
    }
}
