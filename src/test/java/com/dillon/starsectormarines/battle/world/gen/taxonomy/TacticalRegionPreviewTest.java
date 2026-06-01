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

    @Test
    void renderConquestTaxonomy() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();
        for (long seed : CONQUEST_SEEDS) {
            MapResult map = gen.generate(CONQUEST_W, CONQUEST_H, seed, TraversalAxis.SOUTH_TO_NORTH);
            TacticalRegionMap regions = gen.getLastTacticalRegions();
            writePair(map, regions, seed, "taxonomy-conquest", CONQUEST_CELL_PX);
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
            writePair(map, regions, seed, "taxonomy-legacy", CELL_PX);
            printSummary(seed, regions, false);
        }
    }

    private static void writePair(MapResult map, TacticalRegionMap regions, long seed,
                                  String prefix, int cellPx) throws Exception {
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

        // Ring defensible pockets on the kind image: high enclosure, few mouths,
        // not a trivial sliver — the regions a defender wants to hold.
        gk.setStroke(new BasicStroke(2f));
        gk.setColor(Color.WHITE);
        for (TacticalRegion r : regions.regions()) {
            if (r.enclosure >= 0.6f && r.openingCount <= 2 && r.area >= 12) {
                int x0 = r.left * cellPx, y0 = (h - 1 - r.bottom) * cellPx;
                gk.drawRect(x0, y0, r.width() * cellPx - 1, r.height() * cellPx - 1);
            }
        }

        labelStrip(gk, "seed=" + seed + " kind  regions=" + regions.size(), w, h, cellPx);
        labelStrip(gh, "seed=" + seed + " heat (green=cover red=exposed)", w, h, cellPx);
        gk.dispose();
        gh.dispose();
        ImageIO.write(kindImg, "PNG", OUT_DIR.resolve(String.format("%s-%04d-kind.png", prefix, (int) seed)).toFile());
        ImageIO.write(heatImg, "PNG", OUT_DIR.resolve(String.format("%s-%04d-heat.png", prefix, (int) seed)).toFile());
        System.out.println("  wrote " + prefix + "-" + String.format("%04d", (int) seed) + "-{kind,heat}.png");
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
