package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A works hall laid out from its process ({@code ruins-expedition.md}, A hall is laid out from its process), in the
 * hall's own cells before it meets the ground: {@code (0, 0)} is the dispatch end's west corner, the head is to the
 * north at {@code length - 1}. The plan is a pure function of the hall's size and a seed, so its rules are proved
 * here and {@link RuinsDelveStage} only stands what it says.
 *
 * <p>Laying follows the hall's layering order:
 * <ol>
 *   <li><b>The flow.</b> {@link #flow()}: the hall's stations in process order, each fed by the one before, the first
 *   fed from the intake and the last dispatching to storage.</li>
 *   <li><b>The floor.</b> {@link #zone(int, int)}: rail beds and lanes where material moves, painted bays, grating over
 *   the trenches; the rest the ground derives from the work (deck plate under it, walkways, worn aisles).</li>
 *   <li><b>The structure</b>, {@link Layer#OVERHEAD} pieces over the movers.</li>
 *   <li><b>The equipment</b>, {@link Layer#STAND} pieces, each belonging to its station.</li>
 *   <li><b>The collapse</b>, pieces whose station is {@link #COLLAPSE}: work stopped mid-step.</li>
 *   <li><b>Cover</b> comes from all of the above, never from props placed as cover.</li>
 * </ol>
 * The theme's environment lies over the floor: a skin by cell ({@link #skin(int, int)}), shallow water to wade and
 * cells cut out as a drop (melt, a shaft, a sump).
 */
public final class WorksHallPlan {

    /** The collapse's station: pieces that tell where the work stopped. */
    public static final String COLLAPSE = "collapse";
    /** A special zone's floor unless the hall names its own ({@link #specialFloor()}); the floor under shallow water. */
    public static final String RAISED_FLOOR = "ruins-ground.raised-floor", WET_FLOOR = "ruins-ground.wet-floor";
    /** The hall's structure: overheads and the runs they follow. */
    public static final String STRUCTURE = "structure";
    /** The hall's wear: stains, drains and marks along the traffic. */
    public static final String WEAR = "wear";

    /** Least clear width of the hall's main ways: a derelict hall frame (radius 1.25) passes along them. */
    public static final int FRAME_WAY = 3;

    /** How a piece meets the floor. */
    public enum Layer {
        /** Equipment: blocks walking, is drawn and gives its cover. */
        STAND,
        /** Floor kit (a rail length, a bay marking, a bed): walked on, drawn under what stands on it, kept clear. */
        FLOOR,
        /** Drawn over a piece already standing (a melt surface, a unit on its bed); blocks nothing. */
        OVER,
        /** Wear on the floor (a stain, a drain): walked on, drawn under everything, never kept clear. */
        WEAR,
        /** Structure overhead (a runway, a tray): drawn above the people under it, hidden under the roof. */
        OVERHEAD
    }

    /** A floor zone a plan marks itself; the rest the ground derives from the work. */
    public enum Zone {
        /** Left to the ground: deck plate under the work, a walkway on the long walls, worn aisle elsewhere. */
        DERIVED,
        /** A rail bed or marked lane, where material moves. */
        LANE,
        /** A painted bay round a station. */
        BAY,
        /** Deck plate whatever stands on it. */
        PLATE,
        /** A special floor a station sits on (a raised floor, a stage, a clean room). */
        SPECIAL
    }

    /** What a drop cut from the hall's floor is. */
    public enum Drop {
        /** A forge's melt. */
        MELT,
        /** A bore shaft: dry, deep, rock-walled. */
        SHAFT,
        /** A sump of chemical sludge. */
        SLUDGE
    }

    /**
     * One piece of a hall: art {@code id} with a {@code standIn} from the game's existing sets until it ships, in
     * {@code w} by {@code h} cells at {@code (x, y)} from the hall's south-west corner, its art turned {@code turns}
     * quarters clockwise from how it was drawn (a room's pieces turn with the room, {@link #turned}).
     */
    public record Piece(String id, String standIn, int x, int y, int w, int h, Layer layer, String station, int turns) {
        /** A piece drawn as its art faces, unturned. */
        public Piece(String id, String standIn, int x, int y, int w, int h, Layer layer, String station) {
            this(id, standIn, x, y, w, h, layer, station, 0);
        }

        public boolean contains(int cx, int cy) { return cx >= x && cy >= y && cx < x + w && cy < y + h; }
    }

    /** One step of the process: what it does, and the station it hands on to (null for dispatch to storage). */
    public record Station(String name, String feeds) {}

    private final WorksHall kind;
    private final int width, length;
    private final List<Piece> pieces = new ArrayList<>();
    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final Zone[] zones;
    private final String[] skins;
    private final boolean[] water, drop;
    private Drop dropKind;
    private String specialFloor = RAISED_FLOOR;

    WorksHallPlan(WorksHall kind, int width, int length) {
        if (width < 1 || length < 1) throw new IllegalArgumentException("a hall of " + width + "x" + length);
        this.kind = kind;
        this.width = width;
        this.length = length;
        zones = new Zone[width * length];
        Arrays.fill(zones, Zone.DERIVED);
        skins = new String[width * length];
        water = new boolean[width * length];
        drop = new boolean[width * length];
    }

    public WorksHall kind() { return kind; }

    public int width() { return width; }

    public int length() { return length; }

    /** Every piece in laying order: floor kit and wear first, then the equipment, then overlays and overheads. */
    public List<Piece> pieces() { return Collections.unmodifiableList(pieces); }

    /** The stations in process order, feed to dispatch. */
    public List<Station> flow() { return List.copyOf(stations.values()); }

    public Zone zone(int x, int y) { return in(x, y) ? zones[at(x, y)] : Zone.DERIVED; }

    /** The surface the hall's {@link Zone#SPECIAL} floor wears. */
    public String specialFloor() { return specialFloor; }

    /** Lays the hall's {@link Zone#SPECIAL} floor in {@code surface}. */
    void specialFloor(String surface) { specialFloor = surface; }

    /** The environment's skin at a cell, a surface id over the zone's; null for none. */
    public String skin(int x, int y) { return in(x, y) ? skins[at(x, y)] : null; }

    /** Whether a cell is shallow water: walked at a wade. */
    public boolean water(int x, int y) { return in(x, y) && water[at(x, y)]; }

    /** Whether a cell is cut out of the floor as the hall's drop. */
    public boolean drop(int x, int y) { return in(x, y) && drop[at(x, y)]; }

    /** What the hall's drop is; null when it cuts none. */
    public Drop dropKind() { return dropKind; }

    /** Whether a cell is open to walk once every standing piece and the drop are in. */
    public boolean open(int x, int y) {
        if (!in(x, y) || drop[at(x, y)]) return false;
        for (Piece p : pieces) if (p.layer() == Layer.STAND && p.contains(x, y)) return false;
        return true;
    }

    /**
     * This plan turned {@code quarters} quarter turns clockwise, so what faced north faces east after one: its
     * pieces, zones, skins, water and drop moved with it, a piece's footprint turned with its cells. A room is
     * planned with its hall side north and turned to face its hall ({@link RuinsRoom}).
     */
    public WorksHallPlan turned(int quarters) {
        int q = Math.floorMod(quarters, 4);
        if (q == 0) return this;
        boolean swap = q % 2 == 1;
        WorksHallPlan out = new WorksHallPlan(kind, swap ? length : width, swap ? width : length);
        out.stations.putAll(stations);
        out.dropKind = dropKind;
        out.specialFloor = specialFloor;
        for (int y = 0; y < length; y++) {
            for (int x = 0; x < width; x++) {
                int[] t = turn(q, x, y, 1, 1);
                int i = at(x, y), j = out.at(t[0], t[1]);
                out.zones[j] = zones[i];
                out.skins[j] = skins[i];
                out.water[j] = water[i];
                out.drop[j] = drop[i];
            }
        }
        for (Piece p : pieces) {
            int[] t = turn(q, p.x(), p.y(), p.w(), p.h());
            out.pieces.add(new Piece(p.id(), p.standIn(), t[0], t[1], t[2], t[3], p.layer(), p.station(),
                    (p.turns() + q) % 4));
        }
        return out;
    }

    /** A footprint at {@code (x, y)}, {@code pw} by {@code ph}, after {@code q} clockwise quarter turns. */
    private int[] turn(int q, int x, int y, int pw, int ph) {
        return switch (q) {
            case 1 -> new int[]{y, width - x - pw, ph, pw};
            case 2 -> new int[]{width - x - pw, length - y - ph, pw, ph};
            default -> new int[]{length - y - ph, x, ph, pw};
        };
    }

    // ---- building (package, for the theme planners) ------------------------------------------

    boolean in(int x, int y) { return x >= 0 && y >= 0 && x < width && y < length; }

    private int at(int x, int y) { return y * width + x; }

    /** Opens station {@code name}, fed by the station opened before it. */
    WorksHallPlan station(String name) {
        Station last = null;
        for (Station s : stations.values()) last = s;
        if (last != null) stations.put(last.name(), new Station(last.name(), name));
        stations.put(name, new Station(name, null));
        return this;
    }

    /** Equipment of {@code station}, where it fits inside the hall. */
    boolean stand(String id, String standIn, int x, int y, int w, int h, String station) {
        if (!fits(x, y, w, h)) return false;
        for (int cy = y; cy < y + h; cy++) {
            for (int cx = x; cx < x + w; cx++) if (!open(cx, cy)) return false;
        }
        pieces.add(new Piece(id, standIn, x, y, w, h, Layer.STAND, station));
        return true;
    }

    /** Takes back the piece laid last. */
    void unlay() {
        if (!pieces.isEmpty()) pieces.remove(pieces.size() - 1);
    }

    void floor(String id, String standIn, int x, int y, int w, int h, String station) {
        if (fits(x, y, w, h)) pieces.add(new Piece(id, standIn, x, y, w, h, Layer.FLOOR, station));
    }

    void over(String id, int x, int y, int w, int h, String station) {
        if (fits(x, y, w, h)) pieces.add(new Piece(id, null, x, y, w, h, Layer.OVER, station));
    }

    void wear(String id, int x, int y, int w, int h) {
        if (fits(x, y, w, h)) pieces.add(new Piece(id, null, x, y, w, h, Layer.WEAR, WEAR));
    }

    void overhead(String id, String standIn, int x, int y, int w, int h) {
        if (fits(x, y, w, h)) pieces.add(new Piece(id, standIn, x, y, w, h, Layer.OVERHEAD, STRUCTURE));
    }

    void zone(int l, int b, int r, int t, Zone zone) {
        for (int y = Math.max(0, b); y <= Math.min(length - 1, t); y++) {
            for (int x = Math.max(0, l); x <= Math.min(width - 1, r); x++) zones[at(x, y)] = zone;
        }
    }

    void skin(int x, int y, String surface) {
        if (in(x, y)) skins[at(x, y)] = surface;
    }

    void water(int l, int b, int r, int t) {
        for (int y = Math.max(0, b); y <= Math.min(length - 1, t); y++) {
            for (int x = Math.max(0, l); x <= Math.min(width - 1, r); x++) water[at(x, y)] = true;
        }
    }

    void water(int x, int y, boolean wet) {
        if (in(x, y)) water[at(x, y)] = wet;
    }

    /** Cuts a box of the floor as the hall's drop, keeping off the hall's edge cells. */
    void drop(Drop kind, int l, int b, int r, int t) {
        dropKind = kind;
        for (int y = Math.max(1, b); y <= Math.min(length - 2, t); y++) {
            for (int x = Math.max(1, l); x <= Math.min(width - 2, r); x++) drop[at(x, y)] = true;
        }
    }

    private boolean fits(int x, int y, int w, int h) { return x >= 0 && y >= 0 && x + w <= width && y + h <= length; }

    // ---- rules ------------------------------------------------------------------------------

    /** Whether the open floor is one floor, four-connected. */
    public boolean oneFloor() {
        return reach() == countOpen();
    }

    /**
     * Whether a derelict hall frame (radius 1.25, {@link #FRAME_WAY} cells clear) can travel the hall from its
     * dispatch end to its head: there is a run of {@code FRAME_WAY}-square open blocks, four-connected, from a block
     * touching the south edge to one touching the north edge.
     */
    public boolean frameWay() {
        int k = FRAME_WAY;
        int bw = width - k + 1, bl = length - k + 1;
        if (bw < 1 || bl < 1) return false;
        boolean[] clear = new boolean[bw * bl];
        for (int y = 0; y < bl; y++) {
            for (int x = 0; x < bw; x++) {
                boolean ok = true;
                for (int dy = 0; dy < k && ok; dy++) for (int dx = 0; dx < k && ok; dx++) ok = open(x + dx, y + dy);
                clear[y * bw + x] = ok;
            }
        }
        boolean[] seen = new boolean[bw * bl];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < bw; x++) {
            if (clear[x]) {
                seen[x] = true;
                queue.add(new int[]{x, 0});
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[1] == bl - 1) return true;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] s : steps) {
                int nx = c[0] + s[0], ny = c[1] + s[1];
                if (nx < 0 || ny < 0 || nx >= bw || ny >= bl) continue;
                int i = ny * bw + nx;
                if (seen[i] || !clear[i]) continue;
                seen[i] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return false;
    }

    /**
     * The farthest any open cell from row {@code fromRow} north lies from a standing piece, by chessboard steps: how
     * far a marine runs in the open.
     */
    public int widestOpen(int fromRow) {
        int worst = 0;
        for (int y = Math.max(0, fromRow); y < length; y++) {
            for (int x = 0; x < width; x++) {
                if (!open(x, y)) continue;
                int d = 0;
                while (d < Math.max(width, length) && !standingWithin(x, y, d)) d++;
                worst = Math.max(worst, d);
            }
        }
        return worst;
    }

    private boolean standingWithin(int x, int y, int d) {
        for (Piece p : pieces) {
            if (p.layer() != Layer.STAND) continue;
            int dx = Math.max(0, Math.max(p.x() - x, x - (p.x() + p.w() - 1)));
            int dy = Math.max(0, Math.max(p.y() - y, y - (p.y() + p.h() - 1)));
            if (Math.max(dx, dy) <= d) return true;
        }
        return false;
    }

    private int countOpen() {
        int n = 0;
        for (int y = 0; y < length; y++) for (int x = 0; x < width; x++) if (open(x, y)) n++;
        return n;
    }

    private int reach() {
        boolean[] openCell = new boolean[width * length];
        int[] first = null;
        for (int y = 0; y < length; y++) {
            for (int x = 0; x < width; x++) {
                openCell[at(x, y)] = open(x, y);
                if (first == null && openCell[at(x, y)]) first = new int[]{x, y};
            }
        }
        if (first == null) return 0;
        boolean[] seen = new boolean[width * length];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(first);
        seen[at(first[0], first[1])] = true;
        int n = 0;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            n++;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] s : steps) {
                int nx = c[0] + s[0], ny = c[1] + s[1];
                if (!in(nx, ny) || seen[at(nx, ny)] || !openCell[at(nx, ny)]) continue;
                seen[at(nx, ny)] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return n;
    }
}
