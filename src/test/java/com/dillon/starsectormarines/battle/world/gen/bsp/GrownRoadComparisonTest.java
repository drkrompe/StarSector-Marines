package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Side-by-side spike evidence for {@link GrownTrunkPlan}: the stock BSP road
 * plan against the grown junction-graph plan at its three density profiles,
 * rendered through the shared {@link MapPreviewRenderer} at the same seeds so
 * the road-graph overlay is directly comparable across generators.
 *
 * <p>This is a dev tool, not a correctness check — it writes images and does
 * not assert connectivity or road-graph shape. A generator that throws for a
 * given seed still gets a tile: a solid dark placeholder captioned with the
 * exception message, so one bad seed never blocks the picture for the rest.
 *
 * <p>Output: one {@code build/map-previews/grown-seed-NNNN.png} per seed (a
 * single row of 4 tiles: stock, grown:city, grown:town, grown:hamlet) plus
 * the combined {@code build/map-previews/grown-comparison.png} stacking all
 * seeds' rows. Run via:
 * <pre>
 *   gradlew :test --tests "*GrownRoadComparisonTest*"
 * </pre>
 */
public class GrownRoadComparisonTest {

    private static final long[] SEEDS = { 1L, 42L, 100L, 777L };
    private static final int GRID_W = 80;
    private static final int GRID_H = 80;
    private static final int CELL_PX = 8;

    private static final Path OUT_DIR = Paths.get("build/map-previews");

    /** One column of the comparison: a label plus how to mint a freshly-configured generator for it. */
    private static final class Variant {
        final String label;
        final Supplier<BspCityGenerator> factory;

        Variant(String label, Supplier<BspCityGenerator> factory) {
            this.label = label;
            this.factory = factory;
        }
    }

    private static final List<Variant> VARIANTS = List.of(
            new Variant("stock", BspCityGenerator::new),
            new Variant("grown:city",
                    () -> new BspCityGenerator().useGrownRoads(GrownTrunkPlan.Profile.city())),
            new Variant("grown:town",
                    () -> new BspCityGenerator().useGrownRoads(GrownTrunkPlan.Profile.town())),
            new Variant("grown:hamlet",
                    () -> new BspCityGenerator().useGrownRoads(GrownTrunkPlan.Profile.hamlet())));

    @Test
    void renderGrownRoadComparison() throws Exception {
        Files.createDirectories(OUT_DIR);

        List<BufferedImage> allTiles = new ArrayList<>();
        for (long seed : SEEDS) {
            BufferedImage[] rowTiles = new BufferedImage[VARIANTS.size()];
            for (int i = 0; i < VARIANTS.size(); i++) {
                BufferedImage tile = renderVariantTile(VARIANTS.get(i), seed);
                rowTiles[i] = tile;
                allTiles.add(tile);
            }

            BufferedImage row = MapPreviewRenderer.composeContactSheet(rowTiles, VARIANTS.size());
            Path rowOut = OUT_DIR.resolve(String.format("grown-seed-%04d.png", (int) seed));
            ImageIO.write(row, "PNG", rowOut.toFile());
            System.out.println("  wrote " + rowOut.toAbsolutePath());
        }

        BufferedImage combined = MapPreviewRenderer.composeContactSheet(
                allTiles.toArray(new BufferedImage[0]), VARIANTS.size());
        Path combinedOut = OUT_DIR.resolve("grown-comparison.png");
        ImageIO.write(combined, "PNG", combinedOut.toFile());
        System.out.println("  wrote " + combinedOut.toAbsolutePath());
    }

    /**
     * Generates and renders one (seed, variant) tile. Mirrors the overlay
     * arguments {@code BspMapPreviewTest.renderPreviewBatch} passes: district
     * map for zoning, no biome map (legacy/city mode), compounds, and the
     * tactical graph. Never throws — a generation or render failure becomes a
     * captioned placeholder tile instead, since a spike must still produce
     * the whole picture even when one cell of it is broken.
     */
    private static BufferedImage renderVariantTile(Variant variant, long seed) {
        try {
            BspCityGenerator gen = variant.factory.get();
            MapResult map = gen.generate(GRID_W, GRID_H, seed);
            String caption = String.format("seed=%d  %-12s POIs=%d doodads=%d",
                    seed, variant.label, map.pointsOfInterest.size(), map.doodads.size());
            return MapPreviewRenderer.renderMap(map, caption, gen.getLastDistrictMap(), null,
                    gen.getLastCompounds(), gen.getLastTacticalMap(), CELL_PX);
        } catch (Throwable t) {
            String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            String caption = String.format("seed=%d  %-12s FAILED: %s", seed, variant.label, message);
            System.out.println("  " + caption);
            return blankTile(caption);
        }
    }

    /** Solid dark placeholder tile, sized to match a normal render, captioned with the failure. */
    private static BufferedImage blankTile(String caption) {
        int imgW = GRID_W * CELL_PX;
        int imgH = GRID_H * CELL_PX + 24;
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(new Color(35, 20, 20));
        g.fillRect(0, 0, imgW, imgH);

        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, GRID_H * CELL_PX, imgW, 24);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString(caption, 6, GRID_H * CELL_PX + 16);

        g.dispose();
        return img;
    }
}
