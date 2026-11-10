package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The autoplant's final assembly line: light craft built in the open, nose to tail down one straight line of stations
 * from the assembly hall toward the dispatch sheds, each a stage further on than the last, as the bomber plants of
 * an old war built them. The first station holds the engine section on its jig, the next the aft two thirds, then
 * the whole hull being fitted out, and the last a finished craft waiting to be flown out. A raider reads the works'
 * product, and which way its work runs, from one glance at the yard.
 *
 * <p>The craft are vanilla civilian light hulls ({@link #MODELS}), drawn from the game's own sprites at true scale,
 * about four hull pixels a cell, as a landed ship is ({@code landed-ships-true-scale.md}). One model per works: a
 * line builds one craft. Each station is the hull's own footprint along the line and its width across, with a work
 * strip ({@link #STRIP}) along one side for what that stage is being fed; {@link #GAP} cells between stations and
 * {@link FabricationYard#LANE} round the line keep it a way through the works, not a wall.
 *
 * <p>Vanilla light industry makes domestic and luxury goods, not hulls; the autoplant is the owner's direction
 * (raid-light-industry autoplant pick), its craft the light civilian hulls a planet's shops could build.
 */
public final class AssemblyLine {

    /** A stage of the build, and the sprite crop that shows it. */
    public enum Stage {
        /** The engine section alone on its jig. */
        AFT("aft"),
        /** The aft two thirds built. */
        HALF("half"),
        /** The whole hull, being fitted out. */
        FITTING("hull"),
        /** Finished, waiting to fly out. */
        FINISHED("hull");

        final String crop;

        Stage(String crop) {
            this.crop = crop;
        }
    }

    /**
     * A craft the line builds: its sprite name in the {@code autoplant.<name>-<crop>} doodads, its width and length in
     * cells, and the length of its aft section and of its aft two thirds.
     */
    public record Model(String name, int width, int length, int aft, int half) {
        String art(Stage stage) {
            return "autoplant." + name + "-" + stage.crop;
        }

        int length(Stage stage) {
            return switch (stage) {
                case AFT -> aft;
                case HALF -> half;
                default -> length;
            };
        }

        String nose() {
            return "autoplant." + name + "-nose";
        }
    }

    /** The light civilian hulls the line may build; matches the {@code autoplant-*.tileset.json} crops. */
    public static final List<Model> MODELS = List.of(
            new Model("aeroshuttle", 20, 16, 6, 11),
            new Model("mudskipper", 16, 21, 8, 13));

    /** Stations on one line at most. UNTUNED. */
    static final int MAX_STATIONS = 5;
    /** Cells between one station and the next. UNTUNED. */
    static final int GAP = 3;
    /** Cells of work strip beside each hull, for what its stage is fed. */
    static final int STRIP = 2;

    /**
     * One station: its stage; the hull piece's anchor, footprint and quarter turns; and the station's inclusive cell
     * bounds, the work strip included, with the strip's own bounds.
     */
    public record Station(Stage stage, String art, int x, int y, int fx, int fy, int turns,
                          int x0, int y0, int x1, int y1, int sx0, int sy0, int sx1, int sy1) { }

    private AssemblyLine() { }

    /** The model a works builds, drawn from its seed. */
    static Model model(SplittableRandom rng) {
        return MODELS.get(rng.nextInt(MODELS.size()));
    }

    /**
     * Plans the line on its ground: the longest straight run of stations that fits, nearest the assembly hall, then,
     * where a road or a building cut it short, the next longest in what is left, and so on to {@link #MAX_STATIONS}
     * stations, as a line carries on across the road it meets. The stations are staged in the order the work passes
     * them, from the one nearest {@code head} to the one nearest {@code tail}, each craft's nose toward the end of its
     * run the work goes on to. Marks every cell the line and its lanes take in {@code taken}. Empty when not one
     * station fits.
     *
     * @param ground the line's own ground, {@code {x0, y0, x1, y1}} inclusive, or null for the works'
     * @param head   the hall the line starts from (assembly), or null
     * @param tail   the hall the finished craft go out by (dispatch), or null
     */
    public static List<Station> plan(FabricationYard.Ground g, int[] ground, boolean[] taken, List<int[]> doors,
                                     FabricationYard.Hall head, FabricationYard.Hall tail, Model m) {
        List<int[]> slots = new ArrayList<>(); // {x0, y0, axis, dir}
        int axis = -1, dir = 0;
        while (slots.size() < MAX_STATIONS) {
            // Every run lies along the first's axis and heading where it can, so the line keeps one heading across
            // a road.
            List<int[]> run = run(g, ground, taken, doors, head, tail, m, MAX_STATIONS - slots.size(), axis, dir);
            if (run.isEmpty() && axis >= 0) run = run(g, ground, taken, doors, head, tail, m,
                    MAX_STATIONS - slots.size(), -1, 0);
            if (run.isEmpty()) break;
            if (axis < 0) {
                axis = run.get(0)[2];
                dir = run.get(0)[3];
            }
            slots.addAll(run);
        }
        // In the order the work passes: nearest the head and furthest from the tail first.
        int length = m.length(), across = m.width() + STRIP;
        slots.sort((p, q) -> Float.compare(progress(p, length, across, head, tail),
                progress(q, length, across, head, tail)));
        List<Station> out = new ArrayList<>();
        for (int k = 0; k < slots.size(); k++) out.add(station(slots.get(k), stage(k, slots.size()), m));
        return out;
    }

    /**
     * The longest run of at most {@code most} stations that fits, nearest the hall it starts from, as slots
     * {@code {x0, y0, axis, dir}}: {@code axis} 0 along x, 1 along y; {@code dir} 1 when the work runs up the axis.
     * Only along {@code only} when it is not negative, and heading {@code heading} when it is not zero. Marks the run and its lanes taken; empty when none fits.
     */
    private static List<int[]> run(FabricationYard.Ground g, int[] ground, boolean[] taken, List<int[]> doors,
                                   FabricationYard.Hall head, FabricationYard.Hall tail, Model m, int most,
                                   int only, int heading) {
        int w = g.width(), h = g.height();
        int[] core = new int[(w + 1) * (h + 1)], margin = new int[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean in = ground == null ? g.inWorks(x, y)
                        : x >= ground[0] && y >= ground[1] && x <= ground[2] && y <= ground[3];
                boolean clear = in && !g.building(x, y) && !g.road(x, y);
                boolean free = clear && !taken[y * w + x] && g.open(x, y)
                        && !FabricationYard.nearDoor(doors, x, y, FabricationYard.DOOR_CLEAR);
                int i = (y + 1) * (w + 1) + x + 1;
                core[i] = (free ? 0 : 1) + core[i - 1] + core[i - w - 1] - core[i - w - 2];
                margin[i] = (clear ? 0 : 1) + margin[i - 1] + margin[i - w - 1] - margin[i - w - 2];
            }
        }
        int across = m.width() + STRIP, lane = FabricationYard.LANE, pitch = m.length() + GAP;
        for (int n = most; n >= 1; n--) {
            int along = n * m.length() + (n - 1) * GAP;
            int[] best = null;
            float bestScore = Float.MAX_VALUE;
            for (int axis = 0; axis < 2; axis++) {
                if (only >= 0 && axis != only) continue;
                int rw = axis == 0 ? along : across, rh = axis == 0 ? across : along;
                for (int y = lane; y + rh + lane <= h; y++) {
                    for (int x = lane; x + rw + lane <= w; x++) {
                        if (sum(core, w, x, y, rw, rh) != 0) continue;
                        if (sum(margin, w, x - lane, y - lane, rw + 2 * lane, rh + 2 * lane) != 0) continue;
                        float lowX = axis == 0 ? x : x + rw / 2f, lowY = axis == 0 ? y + rh / 2f : y;
                        float highX = axis == 0 ? x + rw : lowX, highY = axis == 0 ? lowY : y + rh;
                        float up = dist(head, lowX, lowY) + dist(tail, highX, highY);
                        float down = dist(head, highX, highY) + dist(tail, lowX, lowY);
                        float score = Math.min(up, down);
                        if (score < bestScore) {
                            bestScore = score;
                            best = new int[]{x, y, axis, heading != 0 ? heading : up <= down ? 1 : -1, rw, rh};
                        }
                    }
                }
            }
            if (best == null) continue;
            int x = best[0], y = best[1], axis = best[2], rw = best[4], rh = best[5];
            for (int yy = Math.max(0, y - lane); yy < Math.min(h, y + rh + lane); yy++) {
                for (int xx = Math.max(0, x - lane); xx < Math.min(w, x + rw + lane); xx++) taken[yy * w + xx] = true;
            }
            List<int[]> out = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                out.add(axis == 0 ? new int[]{x + k * pitch, y, 0, best[3]} : new int[]{x, y + k * pitch, 1, best[3]});
            }
            return out;
        }
        return List.of();
    }

    /** How far down the line a slot stands: nearer the head and further from the tail is earlier. */
    private static float progress(int[] slot, int length, int across, FabricationYard.Hall head,
                                  FabricationYard.Hall tail) {
        float cx = slot[0] + (slot[2] == 0 ? length : across) / 2f;
        float cy = slot[1] + (slot[2] == 0 ? across : length) / 2f;
        return dist(head, cx, cy) - dist(tail, cx, cy);
    }

    /** The station a slot {@code {x0, y0, axis, dir}} holds at {@code stage}. */
    private static Station station(int[] slot, Stage stage, Model m) {
        int x = slot[0], y = slot[1], axis = slot[2], dir = slot[3];
        int across = m.width() + STRIP, l = m.length(stage);
        // The piece stands at the station's aft end, so every station's tail lines up down the line.
        int aft = dir > 0 ? 0 : m.length() - l;
        if (axis == 0) {
            int x1 = x + m.length() - 1, y1 = y + across - 1;
            return new Station(stage, m.art(stage), x + aft, y, l, m.width(), dir > 0 ? 3 : 1,
                    x, y, x1, y1, x, y + m.width(), x1, y1);
        }
        int x1 = x + across - 1, y1 = y + m.length() - 1;
        return new Station(stage, m.art(stage), x, y + aft, m.width(), l, dir > 0 ? 0 : 2,
                x, y, x1, y1, x + m.width(), y, x1, y1);
    }

    /**
     * The stage of the {@code k}-th of {@code n} stations from the head: spread evenly over the build, so the first
     * is always the engine section on its jig and the last always finished; a lone station is a hull being fitted.
     */
    static Stage stage(int k, int n) {
        Stage[] all = Stage.values();
        if (n == 1) return Stage.FITTING;
        int last = all.length - 1;
        return all[Math.round(k * (float) last / (n - 1))];
    }

    /** What a station's work strip holds: what that stage is being fed. Empty for a finished craft. */
    static List<String> feed(Stage stage) {
        return switch (stage) {
            case AFT -> List.of("doodad.industrial-cable-reel", "machine-shop.bar-stock", "doodad.industrial-drum-cluster");
            case HALF -> List.of("doodad.industrial-crate-stack", "machine-shop.electrical", "doodad.industrial-crate-stack");
            case FITTING -> List.of("doodad.stores-cargo-loader", "machine-shop.electrical", "doodad.industrial-cable-reel");
            case FINISHED -> List.of();
        };
    }

    /** Whether a cell of a hull piece's footprint is under the hull: inside the ellipse its footprint inscribes. */
    static boolean underHull(Station s, int x, int y) {
        float rx = s.fx() / 2f, ry = s.fy() / 2f;
        float u = (x + 0.5f - s.x() - rx) / (rx * 0.92f), v = (y + 0.5f - s.y() - ry) / (ry * 0.92f);
        return u * u + v * v <= 1f;
    }

    /** The first hall of {@code step}, or null. */
    static FabricationYard.Hall hall(List<FabricationYard.Hall> halls, RoomPurpose step) {
        for (FabricationYard.Hall hall : halls) if (hall.step() == step) return hall;
        return null;
    }

    private static int sum(int[] s, int w, int x, int y, int rw, int rh) {
        int x1 = x + rw, y1 = y + rh, W = w + 1;
        return s[y1 * W + x1] - s[y * W + x1] - s[y1 * W + x] + s[y * W + x];
    }

    private static float dist(FabricationYard.Hall hall, float x, float y) {
        if (hall == null) return 0f;
        float dx = Math.max(0, Math.max(hall.x0() - x, x - hall.x1()));
        float dy = Math.max(0, Math.max(hall.y0() - y, y - hall.y1()));
        return dx + dy;
    }
}
