package com.dillon.starsectormarines.battle.world.gen.taxonomy;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dev tool dressed as a test — the gut-check for the {@link TacticalRegionMap}
 * structural-taxonomy artifact. Generates the same map batch
 * {@code BspMapPreviewTest} uses and renders, per seed, two PNGs:
 * <ul>
 *   <li><b>{@code …-kind.png}</b> — each region filled by {@link RegionKind}
 *       (id-jittered so adjacent same-kind regions still read apart), with
 *       defensible pockets (high enclosure + ≤2 mouths) ringed in white.</li>
 *   <li><b>{@code …-heat.png}</b> — each region filled by a cover/exposure
 *       split: green = cover-rich, red = exposed killing ground.</li>
 * </ul>
 * Also prints a per-seed attribute summary (region count by kind + the most
 * enclosed pockets) so the segmentation is auditable from the console too.
 *
 * <p>Output: {@code build/map-previews/taxonomy-*.png}. Run via:
 * <pre>gradlew :test --tests "*TacticalRegionPreviewTest*"</pre>
 */
public class TacticalRegionPreviewTest {

    private static final int GRID_W = 80, GRID_H = 80, CELL_PX = 8;
    private static final long[] SEEDS = { 1L, 42L, 100L, 777L, 1234L, 9999L };

    private static final int CONQUEST_W = 240, CONQUEST_H = 160, CONQUEST_CELL_PX = 5;
    private static final long[] CONQUEST_SEEDS = { 1L, 42L, 100L, 777L };

    private static final Path OUT_DIR = Paths.get("build/map-previews");

    private static final Map<RegionKind, Color> KIND_COLORS = new EnumMap<>(RegionKind.class);
    static {
        KIND_COLORS.put(RegionKind.STREET,            new Color(110, 110, 120));
        KIND_COLORS.put(RegionKind.PLAZA,             new Color(200, 180, 150));
        KIND_COLORS.put(RegionKind.COURTYARD,         new Color( 70,  90, 130));
        KIND_COLORS.put(RegionKind.OPEN_GROUND,       new Color( 90, 150,  80));
        KIND_COLORS.put(RegionKind.RUBBLE,            new Color(120,  90,  80));
        KIND_COLORS.put(RegionKind.BUILDING_INTERIOR, new Color(225, 210, 175));
        KIND_COLORS.put(RegionKind.OTHER,             new Color(255,   0, 255));
    }

    // --- overwatch-corner scoring (the positional read; see structural-taxonomy.md) ---
    // A defender turret wants cover at its back + a long field of fire OUT over
    // low-cover ground — NOT a high-enclosure pocket (whose walls box the arc in).
    // These thresholds are display heuristics, tuned by eyeballing the overlay.

    /** {E, W, N, S} as {dx, dy}; reach grid index matches this order. */
    private static final int[][] DIRS = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
    /** Minimum outward open-run (cells) before a position counts as a real field of fire. */
    private static final int MIN_OVERWATCH_REACH = 6;
    /** Reach past this contributes nothing extra to the score — beyond ~14 cells it's all "long field of fire"; corner quality should win, not raw sightline length (which otherwise lets map-spanning edge cells dominate). */
    private static final int OVERWATCH_REACH_CAP = 14;
    /** Forward region must be at most this cover-dense — a killing ground, not another building. */
    private static final float FWD_COVER_MAX = 0.45f;
    /** Non-max suppression radius: keep only the strongest pick within this Manhattan range. */
    private static final int OVERWATCH_SEP = 8;
    /** Cap markers drawn per map so the overlay stays readable. */
    private static final int OVERWATCH_MAX_MARKERS = 18;

    /** One candidate corner-tower position: cell + outward firing direction + score. */
    private record Overwatch(int x, int y, int dx, int dy, float score) {}

    @Test
    void renderConquestTaxonomy() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();
        for (long seed : CONQUEST_SEEDS) {
            MapResult map = gen.generate(CONQUEST_W, CONQUEST_H, seed, TraversalAxis.SOUTH_TO_NORTH);
            TacticalRegionMap regions = gen.getLastTacticalRegions();
            writePair(map, regions, TraversalAxis.SOUTH_TO_NORTH, seed, "taxonomy-conquest", CONQUEST_CELL_PX);
            printSummary(seed, regions, true);
        }
    }

    @Test
    void renderLegacyTaxonomy() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();
        for (long seed : SEEDS) {
            MapResult map = gen.generate(GRID_W, GRID_H, seed);
            TacticalRegionMap regions = gen.getLastTacticalRegions();
            writePair(map, regions, null, seed, "taxonomy-legacy", CELL_PX);
            printSummary(seed, regions, false);
        }
    }

    private static void writePair(MapResult map, TacticalRegionMap regions, TraversalAxis axis,
                                  long seed, String prefix, int cellPx) throws Exception {
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        BufferedImage kindImg = blank(w, h, cellPx);
        BufferedImage heatImg = blank(w, h, cellPx);
        Graphics2D gk = kindImg.createGraphics();
        Graphics2D gh = heatImg.createGraphics();
        gk.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        gh.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx = x * cellPx, sy = (h - 1 - y) * cellPx;
                TacticalRegion r = regions.regionAt(x, y);
                if (r == null) {
                    gk.setColor(Color.BLACK);
                    gh.setColor(Color.BLACK);
                } else {
                    gk.setColor(kindColor(r));
                    gh.setColor(heatColor(r));
                }
                gk.fillRect(sx, sy, cellPx, cellPx);
                gh.fillRect(sx, sy, cellPx, cellPx);
            }
        }

        // White rings = HOLDABLE POCKETS (high enclosure, few mouths) — the
        // garrison / fallback read. NB: a pocket is the wrong place for a turret
        // (walls box the arc); the overwatch arrows below are the turret read.
        gk.setStroke(new BasicStroke(2f));
        gk.setColor(Color.WHITE);
        for (TacticalRegion r : regions.regions()) {
            if (r.enclosure >= 0.6f && r.openingCount <= 2 && r.area >= 12) {
                int x0 = r.left * cellPx, y0 = (h - 1 - r.bottom) * cellPx;
                gk.drawRect(x0, y0, r.width() * cellPx - 1, r.height() * cellPx - 1);
            }
        }

        // Orange arrows = OVERWATCH CORNERS — cover at back, firing out over
        // low-cover ground. The defender-turret read. Drawn on both images;
        // on the heat image each arrow should sit at a green/red boundary
        // pointing into the red (the killing ground it commands).
        List<Overwatch> overwatch = findOverwatch(map, regions, axis, w, h);
        drawOverwatch(gk, overwatch, h, cellPx);
        drawOverwatch(gh, overwatch, h, cellPx);

        labelStrip(gk, "seed=" + seed + " kind  regions=" + regions.size()
                + "  (white=hold pocket, orange=overwatch corner)", w, h, cellPx);
        labelStrip(gh, "seed=" + seed + " heat (green=cover red=exposed)  orange=overwatch", w, h, cellPx);
        gk.dispose();
        gh.dispose();
        ImageIO.write(kindImg, "PNG", OUT_DIR.resolve(String.format("%s-%04d-kind.png", prefix, (int) seed)).toFile());
        ImageIO.write(heatImg, "PNG", OUT_DIR.resolve(String.format("%s-%04d-heat.png", prefix, (int) seed)).toFile());
        System.out.println("  wrote " + prefix + "-" + String.format("%04d", (int) seed)
                + "-{kind,heat}.png  overwatchCorners=" + overwatch.size());
    }

    /** Region kind color, jittered ±18 per channel by id so neighbors read apart. */
    private static Color kindColor(TacticalRegion r) {
        Color base = KIND_COLORS.getOrDefault(r.kind, Color.MAGENTA);
        int j = (r.id * 37) % 37 - 18;
        return new Color(clamp(base.getRed() + j), clamp(base.getGreen() + j), clamp(base.getBlue() + j));
    }

    /** Cover→exposure heat: cover-rich regions green, exposed regions red, blended by coverDensity. */
    private static Color heatColor(TacticalRegion r) {
        float cover = Math.max(0f, Math.min(1f, r.coverDensity));
        int red = clamp((int) (230 * (1 - cover)) + 25);
        int green = clamp((int) (210 * cover) + 25);
        return new Color(red, green, 40);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static BufferedImage blank(int w, int h, int cellPx) {
        return new BufferedImage(w * cellPx, h * cellPx + 20, BufferedImage.TYPE_INT_ARGB);
    }

    private static void labelStrip(Graphics2D g, String text, int w, int h, int cellPx) {
        g.setColor(new Color(0, 0, 0, 220));
        g.fillRect(0, h * cellPx, w * cellPx, 20);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString(text, 5, h * cellPx + 14);
    }

    /**
     * Score every walkable cell as a candidate corner-tower and return the
     * strongest, non-max-suppressed set. A cell scores in direction {@code d}
     * when the cell <em>behind</em> it (opposite {@code d}) is non-walkable
     * (cover at back), the open-run ahead in {@code d} is long (a real field of
     * fire), and the region immediately ahead is low-cover (a killing ground,
     * not another building). Corner positions — a perpendicular neighbor also
     * walled — score higher. The result is the per-cell max over the four
     * directions, then greedy non-max suppression at {@link #OVERWATCH_SEP}.
     */
    private static List<Overwatch> findOverwatch(MapResult map, TacticalRegionMap regions,
                                                 TraversalAxis axis, int w, int h) {
        // In conquest mode, a defender overwatch fires TOWARD the attacker edge
        // (decreasing depth): south for SOUTH_TO_NORTH, west for WEST_TO_EAST.
        // Restricting the firing direction to attacker-ward both orients the
        // markers correctly and distributes them (they land at the attacker-
        // facing edge of each open field, not all along the largest one). Legacy
        // maps have no attacker edge, so all four directions stay in play.
        int adx = 0, ady = 0;
        if (axis == TraversalAxis.SOUTH_TO_NORTH) ady = -1;
        else if (axis == TraversalAxis.WEST_TO_EAST) adx = -1;
        boolean restrictDir = adx != 0 || ady != 0;

        int[][][] reach = computeOpenRuns(map, w, h); // [dir][x][y], dir order == DIRS
        List<Overwatch> picks = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!map.grid.isWalkable(x, y)) continue;
                float best = 0f;
                int bestDx = 0, bestDy = 0;
                for (int d = 0; d < DIRS.length; d++) {
                    int dx = DIRS[d][0], dy = DIRS[d][1];
                    if (restrictDir && (dx != adx || dy != ady)) continue;
                    // Cover at back: the cell opposite the firing direction is a
                    // REAL wall/building (in-bounds non-walkable). The map edge
                    // (OOB) does NOT count — a tower mounted against the map
                    // boundary firing inward isn't a tactical backstop, and
                    // those cells' map-spanning sightlines would dominate.
                    int bx = x - dx, by = y - dy;
                    boolean backCover = bx >= 0 && bx < w && by >= 0 && by < h && !map.grid.isWalkable(bx, by);
                    if (!backCover) continue;
                    int run = reach[d][x][y];
                    if (run < MIN_OVERWATCH_REACH) continue;
                    // Forward ground must be a low-cover killing field, not a building.
                    TacticalRegion fwd = regions.regionAt(x + dx, y + dy);
                    if (fwd != null && fwd.coverDensity > FWD_COVER_MAX) continue;
                    // Cap reach so corner quality wins over raw sightline length.
                    boolean corner = perpWalled(map, x, y, dx, dy);
                    float score = Math.min(run, OVERWATCH_REACH_CAP) * (corner ? 1.25f : 1f);
                    if (score > best) { best = score; bestDx = dx; bestDy = dy; }
                }
                if (best > 0f) picks.add(new Overwatch(x, y, bestDx, bestDy, best));
            }
        }
        // Greedy non-max suppression: strongest first, drop anything within OVERWATCH_SEP.
        picks.sort(Comparator.comparingDouble((Overwatch o) -> o.score).reversed());
        List<Overwatch> kept = new ArrayList<>();
        for (Overwatch o : picks) {
            boolean near = false;
            for (Overwatch k : kept) {
                if (Math.abs(k.x - o.x) + Math.abs(k.y - o.y) < OVERWATCH_SEP) { near = true; break; }
            }
            if (!near) kept.add(o);
            if (kept.size() >= OVERWATCH_MAX_MARKERS) break;
        }
        return kept;
    }

    /** True when a cell perpendicular to the firing axis is also non-walkable — i.e. the position is a corner, not a flat wall. */
    private static boolean perpWalled(MapResult map, int x, int y, int dx, int dy) {
        // Perpendicular axis: swap components.
        return nonWalkable(map, x + dy, y + dx) || nonWalkable(map, x - dy, y - dx);
    }

    private static boolean nonWalkable(MapResult map, int x, int y) {
        return x < 0 || x >= map.grid.getWidth() || y < 0 || y >= map.grid.getHeight() || !map.grid.isWalkable(x, y);
    }

    /** Open-run grids: {@code [dir][x][y]} = walkable cells ahead in {@code DIRS[dir]} before a wall (excluding self). */
    private static int[][][] computeOpenRuns(MapResult map, int w, int h) {
        int[][] east = new int[w][h], west = new int[w][h], north = new int[w][h], south = new int[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!map.grid.isWalkable(x, y)) continue;
                west[x][y]  = (x > 0 && map.grid.isWalkable(x - 1, y)) ? west[x - 1][y] + 1 : 0;
                south[x][y] = (y > 0 && map.grid.isWalkable(x, y - 1)) ? south[x][y - 1] + 1 : 0;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                if (!map.grid.isWalkable(x, y)) continue;
                east[x][y]  = (x < w - 1 && map.grid.isWalkable(x + 1, y)) ? east[x + 1][y] + 1 : 0;
                north[x][y] = (y < h - 1 && map.grid.isWalkable(x, y + 1)) ? north[x][y + 1] + 1 : 0;
            }
        }
        return new int[][][] { east, west, north, south }; // order matches DIRS {E, W, N, S}
    }

    /** Orange dot at the position + a line in the firing direction (+ arrowhead). */
    private static void drawOverwatch(Graphics2D g, List<Overwatch> picks, int gridH, int cellPx) {
        int len = Math.max(cellPx * 3, 16);
        for (Overwatch o : picks) {
            int cx = o.x * cellPx + cellPx / 2;
            int cy = (gridH - 1 - o.y) * cellPx + cellPx / 2;
            int tx = cx + o.dx * len;
            int ty = cy - o.dy * len; // image-y is flipped relative to grid-y
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(255, 150, 40));
            g.drawLine(cx, cy, tx, ty);
            // Arrowhead: two short barbs back from the tip.
            int hx = -o.dx, hy = o.dy; // back along the shaft (image space)
            int px = -o.dy, py = -o.dx; // perpendicular in image space
            g.drawLine(tx, ty, tx + (hx + px) * cellPx / 2, ty + (hy + py) * cellPx / 2);
            g.drawLine(tx, ty, tx + (hx - px) * cellPx / 2, ty + (hy - py) * cellPx / 2);
            g.setColor(new Color(255, 200, 80));
            g.fillOval(cx - 3, cy - 3, 6, 6);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1f));
            g.drawOval(cx - 3, cy - 3, 6, 6);
        }
    }

    private static void printSummary(long seed, TacticalRegionMap regions, boolean conquest) {
        Map<RegionKind, Integer> byKind = new EnumMap<>(RegionKind.class);
        for (TacticalRegion r : regions.regions()) {
            byKind.merge(r.kind, 1, Integer::sum);
        }
        System.out.println("seed=" + seed + (conquest ? " [conquest]" : " [legacy]")
                + " regions=" + regions.size() + " byKind=" + byKind);
        List<TacticalRegion> pockets = regions.regions().stream()
                .filter(r -> r.enclosure >= 0.6f && r.openingCount <= 2 && r.area >= 12)
                .sorted(Comparator.comparingDouble((TacticalRegion r) -> r.enclosure).reversed())
                .limit(5).toList();
        for (TacticalRegion r : pockets) {
            System.out.println("    pocket " + r);
        }
    }
}
