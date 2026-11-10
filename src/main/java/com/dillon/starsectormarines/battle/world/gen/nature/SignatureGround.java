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
 *       stock hills already are, smaller and many; an unstable world's fissures are narrow excavations, the trench
 *       contract every map has (impassable, seen and shot across). Nothing here is a new battle rule.</li>
 *   <li><b>Dressing</b> ({@link #dress}): presentation on open ground. Craters, rimmed and rimless, are marks the
 *       renderer draws a bowl at ({@link CellTopology#signatureMarks}), with ejecta thrown round them; fissure banks
 *       carry quake rubble and the ground's cracked skin ({@link CellTopology.Tag#GROUND_DAMAGED}); cryovolcanic
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
    /** Fissures an unstable world asks for, at most, and map cells per fissure. */
    static final int MAX_FISSURES = 9, CELLS_PER_FISSURE = 3800;
    /** A fissure's brush radius, cells: narrow enough to read as a crack, wide enough to close its centre cells. */
    static final double FISSURE_RADIUS = 1.15;
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
        } else if (signature == SurfaceSignature.FISSURES) {
            Random random = new Random(seed ^ 0x4649535355524553L);
            int wanted = Math.max(1, Math.min(MAX_FISSURES, width * height / CELLS_PER_FISSURE));
            for (int i = 0; i < wanted * 8; i++) out.add(fissure(random, width, height));
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

    /** A fissure: a narrow trench wandering on a slowly turning heading, as a crack runs. */
    static TerrainPlan.Stroke fissure(Random random, int width, int height) {
        double x = 10 + random.nextDouble() * (width - 20), y = 10 + random.nextDouble() * (height - 20);
        double heading = random.nextDouble() * Math.PI * 2;
        int segments = 6 + random.nextInt(7);
        List<TerrainPlan.Point> points = new ArrayList<>();
        points.add(clamp(x, y, width, height));
        for (int i = 0; i < segments; i++) {
            heading += (random.nextDouble() - .5) * 1.1;
            x += Math.cos(heading) * 2.6;
            y += Math.sin(heading) * 2.6;
            points.add(clamp(x, y, width, height));
        }
        return new TerrainPlan.Stroke(TerrainPlan.Mode.EXCAVATE, FISSURE_RADIUS, points);
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
                for (TerrainPlan.Stroke stroke : accepted)
                    if (stroke.mode() == TerrainPlan.Mode.EXCAVATE) banks(topology, width, height, stroke, open, stones, random);
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

    /** Quake rubble and cracked ground along a fissure's banks. */
    private static void banks(CellTopology topology, int width, int height, TerrainPlan.Stroke stroke, IntPredicate open,
                              RockDamage.Biome stones, Random random) {
        List<TerrainPlan.Point> pts = stroke.points();
        for (int k = 0; k + 1 < pts.size(); k++) {
            TerrainPlan.Point a = pts.get(k), b = pts.get(k + 1);
            int x0 = (int) Math.floor(Math.min(a.x(), b.x()) - 4), x1 = (int) Math.ceil(Math.max(a.x(), b.x()) + 4);
            int y0 = (int) Math.floor(Math.min(a.y(), b.y()) - 4), y1 = (int) Math.ceil(Math.max(a.y(), b.y()) + 4);
            for (int y = Math.max(0, y0); y <= Math.min(height - 1, y1); y++) {
                for (int x = Math.max(0, x0); x <= Math.min(width - 1, x1); x++) {
                    double d = segmentDistance(x + .5, y + .5, a, b);
                    double roll = random.nextDouble();
                    int i = y * width + x;
                    if (d > 4 || !open.test(i)) continue;
                    if (d <= 2.2 && roll < .45) topology.setTag(x, y, CellTopology.Tag.GROUND_DAMAGED, true);
                    if (d > 1.4 && roll > .8) stone(topology, x, y, stones, roll > .95 ? "medium-" : "small-", random);
                }
            }
        }
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
