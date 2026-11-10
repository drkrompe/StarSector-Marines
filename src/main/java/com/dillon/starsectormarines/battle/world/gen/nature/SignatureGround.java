package com.dillon.starsectormarines.battle.world.gen.nature;

import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RockDamage;
import com.dillon.starsectormarines.battle.world.model.SurfaceSignature;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * What a planet type's {@link SurfaceSignature} lays on its wild ground at generation ({@code mapgen-nouns.md},
 * Surface signatures). Two halves, both GL-free:
 *
 * <ul>
 *   <li><b>Strokes</b> ({@link #strokes}): terrain the stock hills' plan may take beside its own hills, under the same
 *       fit and detour proof. A bombarded world's crater rims are open crescents of raised rock, as a barren world's
 *       stock hills already are, smaller and many. Nothing here is a new battle rule.</li>
 *   <li><b>Dressing</b> ({@link #dress}): presentation on open ground. Craters, rimmed and rimless, are marks the
 *       renderer draws a bowl at ({@link CellTopology#signatureMarks}), with ejecta thrown round them; an unstable
 *       world's fissures are cracks at a marine's scale ({@link #cracks}), drawn and walked over, their banks
 *       carrying quake rubble and the ground's cracked skin ({@link CellTopology.Tag#GROUND_DAMAGED}); cryovolcanic
 *       vents are marks the renderer draws a mound at and breathes vapour from. Every stone is a passable field stone
 *       with no cover, so movement, sight and cover are the map's own.</li>
 * </ul>
 *
 * <p>Each signature reads its own random stream from the map's seed, so a world with no signature, and every other
 * stream on a world with one, is the map it was.
 */
public final class SignatureGround {

    private SignatureGround() { }

    /** Crater rims a bombarded world asks for, at most, and map cells per rim. */
    static final int MAX_RIMS = 14, CELLS_PER_RIM = 2600;
    /** Rimless craters, scars with ejecta, at most, and map cells per crater. */
    static final int MAX_SCARS = 90, CELLS_PER_SCAR = 400;
    /** Cracks an unstable world carries at most, and map cells per crack. */
    static final int MAX_CRACKS = 18, CELLS_PER_CRACK = 1200;
    /**
     * The widest a crack opens, cells, at a marine's scale: most are a boot wide at their widest, a hairline
     * elsewhere, and one in {@link #SPLIT_ODDS} is a wider split. Owner: cracks a band wide read as impassable.
     */
    static final float CRACK_WIDTH = .28f, SPLIT_WIDTH = .55f;
    static final int SPLIT_ODDS = 5;
    /** Cryovolcanic vents at most, map cells per vent, and the least distance between two, cells. */
    static final int MAX_VENTS = 18, CELLS_PER_VENT = 1500;
    static final float VENT_SPACING = 11f;
    /** A rimmed crater's bowl, as a share of its rim's radius. */
    static final double RIMMED_BOWL = .55;

    /** One crater: its centre and radius in cells, and whether it carries a rim stroke. */
    public record Crater(double x, double y, double radius, boolean rimmed) { }

    /** The candidate strokes a signature adds to the stock hills, in the order they should be tried. */
    public static List<TerrainPlan.Stroke> strokes(SurfaceSignature signature, int width, int height, long seed) {
        List<TerrainPlan.Stroke> out = new ArrayList<>();
        if (signature == SurfaceSignature.CRATER_FIELD) {
            for (Crater crater : craters(width, height, seed)) if (crater.rimmed()) out.add(rim(crater, width, height, seed));
        }
        return out;
    }

    /** A bombarded world's craters, rimmed first; deterministic in the seed. */
    public static List<Crater> craters(int width, int height, long seed) {
        Random random = new Random(seed ^ 0x4352415445525321L);
        List<Crater> out = new ArrayList<>();
        int rims = Math.max(1, Math.min(MAX_RIMS, width * height / CELLS_PER_RIM));
        int scars = Math.max(2, Math.min(MAX_SCARS, width * height / CELLS_PER_SCAR));
        // Many more candidates than rims: most of a settled map is built, and only open country takes one.
        for (int i = 0; i < rims * 8; i++)
            out.add(new Crater(8 + random.nextDouble() * (width - 16), 8 + random.nextDouble() * (height - 16),
                    3 + random.nextDouble() * 3.5, true));
        for (int i = 0; i < scars; i++)
            out.add(new Crater(4 + random.nextDouble() * (width - 8), 4 + random.nextDouble() * (height - 8),
                    1.2 + random.nextDouble() * 2.2, false));
        return out;
    }

    /**
     * A crater's rim: an arc of raised rock round its centre, two-thirds of the way round at most, so the bowl is
     * always open on one side and never seals a movement island.
     */
    static TerrainPlan.Stroke rim(Crater crater, int width, int height, long seed) {
        int turn = (int) Math.floorMod((long) (crater.x() * 31 + crater.y() * 17) ^ seed, 360L);
        double start = Math.toRadians(turn), sweep = Math.toRadians(150 + Math.floorMod(turn * 7, 90));
        List<TerrainPlan.Point> points = new ArrayList<>();
        int steps = 7;
        for (int i = 0; i <= steps; i++) {
            double angle = start + sweep * i / steps;
            points.add(clamp(crater.x() + Math.cos(angle) * crater.radius(),
                    crater.y() + Math.sin(angle) * crater.radius(), width, height));
        }
        return new TerrainPlan.Stroke(TerrainPlan.Mode.RAISE, Math.max(1, crater.radius() * .34), points);
    }

    /** The sweep of a rim stroke, radians: open by at least a third of the way round. */
    static double rimSweep(TerrainPlan.Stroke rim, Crater crater) {
        TerrainPlan.Point a = rim.points().get(0), b = rim.points().get(rim.points().size() - 1);
        double from = Math.atan2(a.y() - crater.y(), a.x() - crater.x());
        double to = Math.atan2(b.y() - crater.y(), b.x() - crater.x());
        double sweep = Math.floorMod(Math.round(Math.toDegrees(to - from)), 360L);
        return Math.toRadians(sweep);
    }

    private static TerrainPlan.Point clamp(double x, double y, int width, int height) {
        return new TerrainPlan.Point(Math.max(0, Math.min(width - 1, x)), Math.max(0, Math.min(height - 1, y)));
    }

    /**
     * Presentation on open ground after the strokes have been stamped.
     *
     * @param accepted the signature strokes the hills' plan took
     * @param open     whether a cell (row-major index) is open wild ground, clear of every reservation
     * @param stones   the field-stone set lying on this world
     */
    public static void dress(CellTopology topology, int width, int height, long seed, SurfaceSignature signature,
                             List<TerrainPlan.Stroke> accepted, IntPredicate open, RockDamage.Biome stones) {
        switch (signature) {
            case CRATER_FIELD -> {
                Random random = new Random(seed ^ 0x454a454354414cL);
                List<float[]> marks = new ArrayList<>();
                for (Crater crater : craters(width, height, seed)) {
                    if (crater.rimmed() ? accepted.stream().noneMatch(s -> sameRim(s, crater, width, height, seed))
                            : !openDisc(crater, width, height, open)) continue;
                    // A rimmed crater's bowl sits inside its rim, clear of the raised rock; a rimless one is all bowl.
                    double bowl = crater.rimmed() ? crater.radius() * RIMMED_BOWL : crater.radius();
                    marks.add(new float[] {(float) crater.x(), (float) crater.y(), (float) bowl});
                    ejecta(topology, width, height, crater, open, stones, random);
                }
                topology.setSignatureMarks(flatten(marks));
            }
            case FISSURES -> {
                Random random = new Random(seed ^ 0x5155414b45L);
                List<List<float[]>> cracks = cracks(width, height, seed, open);
                for (List<float[]> crack : cracks) {
                    List<TerrainPlan.Point> line = new ArrayList<>();
                    for (float[] p : crack) line.add(new TerrainPlan.Point(p[0], p[1]));
                    banks(topology, width, height, line, 1.2, .9, open, stones, random);
                }
                topology.setSignatureMarks(encode(cracks));
            }
            case CRYO_VENTS -> {
                float[] vents = vents(width, height, seed, open);
                float[] marks = new float[vents.length / 2 * 3];
                for (int i = 0, j = 0; i + 1 < vents.length; i += 2, j += 3) {
                    marks[j] = vents[i];
                    marks[j + 1] = vents[i + 1];
                    marks[j + 2] = 1f;
                }
                topology.setSignatureMarks(marks);
            }
            default -> { }
        }
    }

    private static boolean sameRim(TerrainPlan.Stroke stroke, Crater crater, int width, int height, long seed) {
        return stroke.mode() == TerrainPlan.Mode.RAISE && stroke.equals(rim(crater, width, height, seed));
    }

    /** Whether a rimless crater's whole bowl lies on open ground: its decal is drawn over nothing built. */
    private static boolean openDisc(Crater crater, int width, int height, IntPredicate open) {
        double r = crater.radius() * 1.4;
        for (int y = (int) Math.floor(crater.y() - r); y <= Math.ceil(crater.y() + r); y++)
            for (int x = (int) Math.floor(crater.x() - r); x <= Math.ceil(crater.x() + r); x++) {
                if (Math.hypot(x + .5 - crater.x(), y + .5 - crater.y()) > r) continue;
                if (x < 0 || y < 0 || x >= width || y >= height || !open.test(y * width + x)) return false;
            }
        return true;
    }

    private static float[] flatten(List<float[]> marks) {
        float[] out = new float[marks.size() * 3];
        for (int i = 0; i < marks.size(); i++) System.arraycopy(marks.get(i), 0, out, i * 3, 3);
        return out;
    }

    /**
     * Ejecta thrown out round a crater, thinning with distance. The bowl itself is the renderer's crater decal
     * ({@code SignatureRenderSystem}); the ground keeps its own material under it.
     */
    private static void ejecta(CellTopology topology, int width, int height, Crater crater, IntPredicate open,
                               RockDamage.Biome stones, Random random) {
        double r = crater.radius(), floor = crater.rimmed() ? r * .8 : r, reach = r * 2.4;
        int x0 = (int) Math.floor(crater.x() - reach), x1 = (int) Math.ceil(crater.x() + reach);
        int y0 = (int) Math.floor(crater.y() - reach), y1 = (int) Math.ceil(crater.y() + reach);
        for (int y = Math.max(0, y0); y <= Math.min(height - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(width - 1, x1); x++) {
                int i = y * width + x;
                double d = Math.hypot(x + .5 - crater.x(), y + .5 - crater.y());
                // Draw every cell's chance, open or not, so what is reserved never moves the rest of the throw.
                double roll = random.nextDouble();
                if (!open.test(i) || d <= floor) continue;
                if (d <= reach && roll < .22 * (1 - (d - floor) / (reach - floor))) {
                    stone(topology, x, y, stones, roll < .1 ? "medium-" : "small-", random);
                }
            }
        }
    }

    /**
     * Quake rubble and cracked ground along a fissure's banks: stones from {@code rubbleFrom} cells out, the ground's
     * cracked skin within {@code crackedTo}.
     */
    private static void banks(CellTopology topology, int width, int height, List<TerrainPlan.Point> pts,
                              double rubbleFrom, double crackedTo, IntPredicate open, RockDamage.Biome stones,
                              Random random) {
        for (int k = 0; k + 1 < pts.size(); k++) {
            TerrainPlan.Point a = pts.get(k), b = pts.get(k + 1);
            int x0 = (int) Math.floor(Math.min(a.x(), b.x()) - 4), x1 = (int) Math.ceil(Math.max(a.x(), b.x()) + 4);
            int y0 = (int) Math.floor(Math.min(a.y(), b.y()) - 4), y1 = (int) Math.ceil(Math.max(a.y(), b.y()) + 4);
            for (int y = Math.max(0, y0); y <= Math.min(height - 1, y1); y++) {
                for (int x = Math.max(0, x0); x <= Math.min(width - 1, x1); x++) {
                    double d = segmentDistance(x + .5, y + .5, a, b);
                    double roll = random.nextDouble();
                    int i = y * width + x;
                    if (d > 3 || !open.test(i)) continue;
                    if (d <= crackedTo && roll < .45) topology.setTag(x, y, CellTopology.Tag.GROUND_DAMAGED, true);
                    if (d > rubbleFrom && roll > .95) stone(topology, x, y, stones, roll > .95 ? "medium-" : "small-", random);
                }
            }
        }
    }

    /**
     * An unstable world's cracks: each a jagged line wandering on a slowly turning heading, opening widest in its
     * middle and closing to nothing at its ends, with a few hairline branches. Every point lies on open ground; a
     * crack that would cross anything reserved is not drawn. Each point is {@code {x, y, width}} in cells.
     * Deterministic in the seed.
     */
    public static List<List<float[]>> cracks(int width, int height, long seed, IntPredicate open) {
        Random random = new Random(seed ^ 0x435241434b53L);
        int wanted = Math.max(2, Math.min(MAX_CRACKS, width * height / CELLS_PER_CRACK));
        List<List<float[]>> out = new ArrayList<>();
        // Start on open ground: on a settled map most cells are built, and a crack begins only where the ground is.
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < width * height; i++) if (open.test(i)) starts.add(i);
        if (starts.isEmpty()) return out;
        int mains = 0;
        for (int attempt = 0; attempt < wanted * 12 && mains < wanted; attempt++) {
            float widest = (random.nextInt(SPLIT_ODDS) == 0 ? SPLIT_WIDTH : CRACK_WIDTH) * (.6f + random.nextFloat() * .4f);
            int start = starts.get(random.nextInt(starts.size()));
            List<float[]> main = openPart(crackLine(random, start % width + .5f, start / width + .5f,
                    random.nextDouble() * Math.PI * 2, 16 + random.nextInt(16), 1.5, widest), width, height, open);
            if (main == null) continue;
            List<List<float[]>> set = new ArrayList<>();
            set.add(main);
            // Branches off the crack, and twigs off some branches: a crack spreads by splitting, not by widening.
            int branches = 2 + random.nextInt(3);
            for (int b = 0; b < branches; b++) {
                List<float[]> branch = branch(random, main, 5 + random.nextInt(7), .55f, width, height, open);
                if (branch == null) continue;
                set.add(branch);
                if (random.nextBoolean()) {
                    List<float[]> twig = branch(random, branch, 3 + random.nextInt(4), .5f, width, height, open);
                    if (twig != null) set.add(twig);
                }
            }
            out.addAll(set);
            mains++;
        }
        return out;
    }

    /** A branch leaving {@code from} at one of its inner points, narrower than it there and closing away from it. */
    private static List<float[]> branch(Random random, List<float[]> from, int steps, float share, int width,
                                        int height, IntPredicate open) {
        if (from.size() < 4) return null;
        int at = 1 + random.nextInt(from.size() - 2);
        float[] root = from.get(at), next = from.get(at + 1);
        double along = Math.atan2(next[1] - root[1], next[0] - root[0]);
        double off = (random.nextBoolean() ? 1 : -1) * (.45 + random.nextDouble() * .6);
        List<float[]> branch = openPart(crackLine(random, root[0], root[1], along + off, steps, .9, root[2] * share),
                width, height, open);
        if (branch != null) branch.get(0)[2] = root[2] * share;
        return branch;
    }

    /** The shortest a crack or branch may be, in points, once cut short where it would leave open ground. */
    static final int MIN_CRACK_POINTS = 4;

    /**
     * A line up to the first point off open ground, closing to nothing there; null if too little of it is open.
     * A crack stops where the ground it runs in stops, so it never runs under a road or a wall.
     */
    private static List<float[]> openPart(List<float[]> line, int width, int height, IntPredicate open) {
        int n = 0;
        while (n < line.size() && onOpen(line.get(n), width, height, open)) n++;
        if (n < MIN_CRACK_POINTS) return null;
        List<float[]> part = new ArrayList<>(line.subList(0, n));
        part.get(n - 1)[2] = 0f;
        return part;
    }

    /** One jagged line: a slowly turning heading, a fine sideways jitter, and a width swelling then closing. */
    private static List<float[]> crackLine(Random random, double x, double y, double heading, int steps, double step,
                                           float widest) {
        List<float[]> pts = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double jitter = (random.nextDouble() - .5) * .5;
            float w = (float) (widest * Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.15 + .02)), .7));
            pts.add(new float[] {(float) (x - Math.sin(heading) * jitter), (float) (y + Math.cos(heading) * jitter),
                    Math.max(0f, w)});
            heading += (random.nextDouble() - .5) * .9;
            x += Math.cos(heading) * step;
            y += Math.sin(heading) * step;
        }
        pts.get(pts.size() - 1)[2] = 0f;
        return pts;
    }

    private static boolean onOpen(float[] p, int width, int height, IntPredicate open) {
        int x = (int) Math.floor(p[0]), y = (int) Math.floor(p[1]);
        return x >= 1 && y >= 1 && x < width - 1 && y < height - 1 && open.test(y * width + x);
    }

    /** Cracks as marks: each line a header {@code {-1, points, 0}} and then its points' {@code {x, y, width}}. */
    static float[] encode(List<List<float[]>> lines) {
        int n = 0;
        for (List<float[]> line : lines) n += 3 + line.size() * 3;
        float[] out = new float[n];
        int o = 0;
        for (List<float[]> line : lines) {
            out[o++] = -1f;
            out[o++] = line.size();
            out[o++] = 0f;
            for (float[] p : line) {
                out[o++] = p[0];
                out[o++] = p[1];
                out[o++] = p[2];
            }
        }
        return out;
    }

    static double segmentDistance(double px, double py, TerrainPlan.Point a, TerrainPlan.Point b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), len2 = dx * dx + dy * dy;
        double t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, ((px - a.x()) * dx + (py - a.y()) * dy) / len2));
        return Math.hypot(px - a.x() - t * dx, py - a.y() - t * dy);
    }

    private static void stone(CellTopology topology, int x, int y, RockDamage.Biome stones, String size, Random random) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null || topology.getNatureOverlayIndex(x, y) >= 0) return;
        TileDef def = registry.tile(stones.stone(size + (1 + random.nextInt(2))));
        // Only a stone a marine walks over and takes no cover behind: ejecta is the ground's look, not its shape.
        if (def != null && def.passable && def.cover.level() <= 0) topology.setNatureOverlayIndex(x, y, def.index);
    }

    /**
     * Where a cryovolcanic world's vents breathe: on open ground, spaced at least {@link #VENT_SPACING} cells apart,
     * as {@code {x, y}} pairs at cell centres. Deterministic in the seed.
     */
    public static float[] vents(int width, int height, long seed, IntPredicate open) {
        Random random = new Random(seed ^ 0x43525956454e5453L);
        int wanted = Math.max(2, Math.min(MAX_VENTS, width * height / CELLS_PER_VENT));
        List<float[]> placed = new ArrayList<>();
        for (int attempt = 0; attempt < wanted * 20 && placed.size() < wanted; attempt++) {
            int x = 3 + random.nextInt(Math.max(1, width - 6)), y = 3 + random.nextInt(Math.max(1, height - 6));
            if (!openAround(x, y, width, height, open)) continue;
            float cx = x + .5f, cy = y + .5f;
            boolean far = true;
            for (float[] p : placed) if (Math.hypot(p[0] - cx, p[1] - cy) < VENT_SPACING) { far = false; break; }
            if (far) placed.add(new float[] {cx, cy});
        }
        float[] out = new float[placed.size() * 2];
        for (int i = 0; i < placed.size(); i++) { out[i * 2] = placed.get(i)[0]; out[i * 2 + 1] = placed.get(i)[1]; }
        return out;
    }

    /** The vent's mound needs its cell and the two rings round it open. */
    private static boolean openAround(int x, int y, int width, int height, IntPredicate open) {
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            int xx = x + dx, yy = y + dy;
            if (xx < 0 || yy < 0 || xx >= width || yy >= height || !open.test(yy * width + xx)) return false;
        }
        return true;
    }
}
