package com.dillon.starsectormarines.battle.world.tiles;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Dev tool dressed as a test. Generates a small zone, then skins each cell
 * with the actual {@link NatureTile} sprite art so visual issues (wrong
 * frame index, bad overlay placement, sheet drift after a re-export) surface
 * without launching the game.
 *
 * <p>Parallel in spirit to {@link com.dillon.starsectormarines.battle.mapgen.bsp.BspMapPreviewTest}:
 * generate → render → write PNG. The difference is scope — this runs on a
 * small zone (~24x16 cells) using the real PNG tiles via
 * {@link SpriteSheetSlicer}, instead of full-city dimensions using flat color
 * quads. Iteration loop is: edit the slicer / enum / placement rules →
 * {@code gradlew :test --tests "*NatureZonePreviewTest*"} → eyeball the PNG.
 *
 * <p>Output: {@code build/zone-previews/nature-zone-NNNN.png} (one per seed)
 * plus a contact sheet at {@code build/zone-previews/nature-zone-contact.png}.
 * Also fails loudly if the slicer doesn't return exactly
 * {@link NatureTile#values() NatureTile.values().length} frames — that's
 * almost always a sheet/art drift bug.
 */
public class NatureZonePreviewTest {

    private static final Path SHEET = Paths.get("mod/graphics/tilesets/nature-tiles.png");
    private static final Path OUT_DIR = Paths.get("build/zone-previews");

    private static final int ZONE_W = 24;
    private static final int ZONE_H = 16;
    private static final long[] SEEDS = { 1L, 42L, 100L, 777L };

    // Inset is now owned by SlicedTileDrawer.DEFAULT_GROUND_INSET_PX so the
    // test and the eventual in-game wiring share one source of truth. The
    // drawer also handles the ground-only rule — overlays (plants, rocks)
    // automatically pass through at inset=0 because their Kind isn't GROUND.

    private static final Color CHECKER_A = new Color(0x18, 0x1F, 0x2A);
    private static final Color CHECKER_B = new Color(0x22, 0x2A, 0x36);
    private static final Color LABEL_BG  = new Color(0, 0, 0, 200);
    private static final Color LABEL_FG  = new Color(0xE0, 0xE8, 0xF4);

    @Test
    void slicerReturnsExpectedFrameCount() throws Exception {
        BufferedImage sheetImg = ImageIO.read(Files.newInputStream(SHEET));
        assertNotNull(sheetImg, "failed to load " + SHEET);
        SpriteSheetFrames sliced = SpriteSheetSlicer.slice(sheetImg);

        // Always write the debug overlay first — even on assertion failure
        // we want a visual record of what the slicer detected so the user
        // can eyeball which frame got split or merged.
        Files.createDirectories(OUT_DIR);
        Path debugPath = OUT_DIR.resolve("nature-slicer-debug.png");
        ImageIO.write(renderSlicerDebug(sheetImg, sliced), "PNG", debugPath.toFile());
        System.out.println("  wrote " + debugPath.toAbsolutePath());

        assertEquals(NatureTile.values().length, sliced.frames.length,
                () -> "slicer detected " + sliced.frames.length
                        + " frames but NatureTile expects " + NatureTile.values().length
                        + " — sheet art and enum are out of sync"
                        + " (see " + debugPath + " for the per-frame bounding boxes)");
    }

    /**
     * Renders the source sheet with each detected frame's bounding box outlined
     * and its index labeled. Lets you eyeball which frames the slicer split
     * or merged when the count doesn't match the enum.
     */
    private static BufferedImage renderSlicerDebug(BufferedImage sheet, SpriteSheetFrames sliced) {
        int scale = 2;
        int padTop = 24;
        BufferedImage img = new BufferedImage(
                sheet.getWidth() * scale,
                sheet.getHeight() * scale + padTop,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x10, 0x14, 0x1C));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("slicer-debug — " + sliced.frames.length + " frames detected (enum expects "
                + NatureTile.values().length + ")", 8, 16);

        // Source sheet scaled up.
        g.drawImage(sheet, 0, padTop,
                sheet.getWidth() * scale, sheet.getHeight() * scale + padTop, null);

        // Per-frame bounding box + index label.
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < sliced.frames.length; i++) {
            SpriteSheetFrames.Frame f = sliced.frames[i];
            int sx = f.x * scale;
            int sy = f.y * scale + padTop;
            int sw = f.w * scale;
            int sh = f.h * scale;
            g.setColor(new Color(0x6A, 0xFF, 0x88, 220));
            g.drawRect(sx, sy, sw, sh);
            g.setColor(Color.BLACK);
            g.drawString(Integer.toString(i), sx + 3, sy + 14);
            g.setColor(new Color(0xFF, 0xE0, 0x70));
            g.drawString(Integer.toString(i), sx + 2, sy + 13);
        }
        g.dispose();
        return img;
    }

    /**
     * Renders each parsed frame in slicer order through the same
     * {@link SlicedTileDrawer} the zone preview uses, with the
     * {@link NatureTile} name and index labelled. Lets you cross-reference
     * "which frame is the odd tile I'm seeing in the zone" without having
     * to count bounding boxes in {@code nature-slicer-debug.png}.
     *
     * <p>Cells follow the in-game render rule (ground tiles get the inset,
     * overlays don't) — so the gallery's per-frame appearance matches
     * what'll actually show up in a zone.
     *
     * <p>Output: {@code build/zone-previews/nature-tile-gallery.png}.
     */
    @Test
    void renderTileGallery() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage sheetImg = ImageIO.read(Files.newInputStream(SHEET));
        assertNotNull(sheetImg, "failed to load " + SHEET);
        SpriteSheetFrames sliced = SpriteSheetSlicer.slice(sheetImg);

        int cellW = 0, cellH = 0;
        for (SpriteSheetFrames.Frame f : sliced.frames) {
            if (f.w > cellW) cellW = f.w;
            if (f.h > cellH) cellH = f.h;
        }

        int cols = 7; // matches the ground-tile run width; overlay rows wrap
        int rows = (sliced.frames.length + cols - 1) / cols;
        int labelH = 32;
        int cellPad = 6;
        int titleH = 36;

        int displayW = cellW + cellPad * 2;
        int displayH = cellH + labelH + cellPad * 2;
        int imgW = cols * displayW;
        int imgH = titleH + rows * displayH;

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(new Color(0x12, 0x18, 0x22));
        g.fillRect(0, 0, imgW, imgH);

        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("nature-tiles.png — " + sliced.frames.length
                        + " frames parsed, " + NatureTile.values().length + " enum entries",
                12, 24);

        SlicedTileDrawer drawer = new SlicedTileDrawer(sliced);
        TileSink sink = new Graphics2DTileSink(g, sheetImg);

        for (int i = 0; i < sliced.frames.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int x0 = col * displayW + cellPad;
            int y0 = titleH + row * displayH + cellPad;

            // Checker backdrop so transparency in the tile reads.
            for (int cy = 0; cy < cellH; cy += 8) {
                for (int cx = 0; cx < cellW; cx += 8) {
                    g.setColor(((cx / 8 + cy / 8) % 2 == 0) ? CHECKER_A : CHECKER_B);
                    g.fillRect(x0 + cx, y0 + cy,
                            Math.min(8, cellW - cx), Math.min(8, cellH - cy));
                }
            }

            NatureTile tile = NatureTile.byFrame(i);
            if (tile != null) {
                drawer.draw(sink, tile,
                        x0 + cellW / 2f, y0 + cellH / 2f, cellW, cellH, 1f);
            } else {
                // Frame parsed but no enum entry — render the raw bbox so a
                // sheet-vs-enum drift is visible. Bypasses the drawer's
                // inset rule on purpose: we want to see what the slicer
                // actually found, unmodified.
                SpriteSheetFrames.Frame f = sliced.frames[i];
                sink.drawSlice(f.x, f.y, f.w, f.h,
                        x0 + cellW / 2f, y0 + cellH / 2f, cellW, cellH, 1f);
            }

            // Cell border.
            g.setColor(new Color(0x4A, 0x6B, 0x8C));
            g.drawRect(x0, y0, cellW, cellH);

            // Index + enum name on the first label line, Kind + raw bbox on the second.
            g.setColor(LABEL_FG);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String topLabel = tile != null
                    ? (i + ": " + tile.name())
                    : (i + ": (no enum mapping)");
            g.drawString(topLabel, x0, y0 + cellH + 14);

            SpriteSheetFrames.Frame f = sliced.frames[i];
            g.setColor(new Color(0xA8, 0xC0, 0xE0));
            String bottomLabel = (tile != null ? tile.kind.name() + " · " : "")
                    + f.w + "x" + f.h + "px";
            g.drawString(bottomLabel, x0, y0 + cellH + 26);
        }

        g.dispose();
        Path out = OUT_DIR.resolve("nature-tile-gallery.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    @Test
    void renderZoneBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage sheetImg = ImageIO.read(Files.newInputStream(SHEET));
        assertNotNull(sheetImg, "failed to load " + SHEET);
        SpriteSheetFrames sliced = SpriteSheetSlicer.slice(sheetImg);

        BufferedImage[] perSeed = new BufferedImage[SEEDS.length];
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            Zone zone = generateZone(seed);
            BufferedImage img = renderZone(zone, sheetImg, sliced, seed);
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("nature-zone-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
        }
        BufferedImage contact = composeContactSheet(perSeed, 2);
        Path contactPath = OUT_DIR.resolve("nature-zone-contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    // ---- generation -------------------------------------------------------

    /** Per-cell base + optional overlay. Mirrors what a real mapgen layer would emit. */
    private static final class Zone {
        final NatureTile[][] base;     // [x][y]
        final NatureTile[][] overlay;  // [x][y], nullable
        Zone(NatureTile[][] base, NatureTile[][] overlay) {
            this.base = base;
            this.overlay = overlay;
        }
    }

    /**
     * Hand-shaped zone for tile iteration. Quadrant-based ground (grass, dirt,
     * sand, water) so every ground type appears with neighbors on every side,
     * then overlays scattered by per-cell hash so the same seed reproduces.
     *
     * <p>Placement obeys {@link NatureTile#canOverlay} — plants only on grass,
     * rocks on any non-water surface. Density is intentionally high enough
     * that overlay clusters appear in every render but low enough that the
     * underlying ground stays readable.
     */
    private static Zone generateZone(long seed) {
        Random rng = new Random(seed);
        NatureTile[][] base = new NatureTile[ZONE_W][ZONE_H];
        NatureTile[][] overlay = new NatureTile[ZONE_W][ZONE_H];

        NatureTile[] grassPool = { NatureTile.GRASS_1, NatureTile.GRASS_2 };
        NatureTile[] dirtPool  = { NatureTile.DIRT_1,  NatureTile.DIRT_2  };
        NatureTile[] sandPool  = { NatureTile.SAND };
        NatureTile[] waterPool = { NatureTile.WATER_1, NatureTile.WATER_2 };

        int halfX = ZONE_W / 2;
        int halfY = ZONE_H / 2;
        for (int x = 0; x < ZONE_W; x++) {
            for (int y = 0; y < ZONE_H; y++) {
                boolean west  = x < halfX;
                boolean south = y < halfY;
                NatureTile[] pool;
                if (west &&  south) pool = sandPool;
                else if (west)      pool = grassPool;
                else if (south)     pool = waterPool;
                else                pool = dirtPool;
                base[x][y] = pool[rng.nextInt(pool.length)];
            }
        }

        // Plant overlays — only on grass cells. Density chosen so a typical
        // grass quadrant gets ~25% coverage, enough to spot misplacement.
        NatureTile[] plantPool = {
                NatureTile.SHRUB_1, NatureTile.SHRUB_2,
                NatureTile.GRASS_TUFT_1, NatureTile.GRASS_TUFT_2,
                NatureTile.SHRUB_3,
        };
        // Rock overlays — valid on any non-water surface. Lower density than
        // plants so the two layers are visually distinct in the output.
        NatureTile[] rockPool = {
                NatureTile.ROCKS_SMALL_1, NatureTile.ROCKS_SMALL_2, NatureTile.ROCKS_SMALL_3,
                NatureTile.ROCK_MEDIUM_1, NatureTile.ROCK_MEDIUM_2,
                NatureTile.ROCK_LARGE_1,  NatureTile.ROCK_LARGE_2,
        };
        for (int x = 0; x < ZONE_W; x++) {
            for (int y = 0; y < ZONE_H; y++) {
                NatureTile b = base[x][y];
                // Try plant first; if it can't overlay this base, fall through to rock.
                if (rng.nextFloat() < 0.25f) {
                    NatureTile p = plantPool[rng.nextInt(plantPool.length)];
                    if (p.canOverlay(b)) {
                        overlay[x][y] = p;
                        continue;
                    }
                }
                if (rng.nextFloat() < 0.12f) {
                    NatureTile r = rockPool[rng.nextInt(rockPool.length)];
                    if (r.canOverlay(b)) {
                        overlay[x][y] = r;
                    }
                }
            }
        }
        return new Zone(base, overlay);
    }

    // ---- rendering --------------------------------------------------------

    private static BufferedImage renderZone(Zone zone, BufferedImage sheet,
                                             SpriteSheetFrames sliced, long seed) {
        // Uniform cell = the widest × tallest frame across the whole sheet.
        // Every cell in the output is exactly this size; each frame's raw
        // bbox is bilinear-stretched to fill it (the AI-generated source has
        // enough detail to "make up" the stretch — sharper than NEAREST for
        // non-integer ratios, no seams from per-frame aspect drift).
        int cellW = 0, cellH = 0;
        for (SpriteSheetFrames.Frame f : sliced.frames) {
            if (f.w > cellW) cellW = f.w;
            if (f.h > cellH) cellH = f.h;
        }

        int imgW = ZONE_W * cellW;
        int imgH = ZONE_H * cellH + 28; // bottom strip for seed label

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // BILINEAR — every tile renders at the uniform cell size regardless
        // of its source bbox, so source/dst ratios are non-integer. NEAREST
        // there produces stair-step pixel-skip artifacts at edges (the
        // "tiny edge" seam that motivated this normalization).
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Route every tile through SlicedTileDrawer so the test exercises
        // the same source-rect math the in-game renderer will use. Overlay
        // tiles (plants, rocks) automatically pass through at inset=0 — the
        // drawer applies the ground-only inset rule from their Kind.
        SlicedTileDrawer drawer = new SlicedTileDrawer(sliced);
        TileSink sink = new Graphics2DTileSink(g, sheet);

        // Pass 1 — checker backdrop so transparency in the art reads.
        for (int x = 0; x < ZONE_W; x++) {
            for (int y = 0; y < ZONE_H; y++) {
                g.setColor(((x + y) % 2 == 0) ? CHECKER_A : CHECKER_B);
                g.fillRect(x * cellW, (ZONE_H - 1 - y) * cellH, cellW, cellH);
            }
        }

        // Pass 2 — base ground tiles.
        for (int x = 0; x < ZONE_W; x++) {
            for (int y = 0; y < ZONE_H; y++) {
                stampTile(drawer, sink, zone.base[x][y], x, y, cellW, cellH);
            }
        }

        // Pass 3 — overlay tiles (plants, rocks).
        for (int x = 0; x < ZONE_W; x++) {
            for (int y = 0; y < ZONE_H; y++) {
                NatureTile o = zone.overlay[x][y];
                if (o != null) stampTile(drawer, sink, o, x, y, cellW, cellH);
            }
        }

        // Pass 4 — overlay rule violations (defensive — shouldn't happen, but
        // if generateZone ever drifts from canOverlay, paint a red X so it's
        // obvious in the PNG).
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 60, 60));
        for (int x = 0; x < ZONE_W; x++) {
            for (int y = 0; y < ZONE_H; y++) {
                NatureTile o = zone.overlay[x][y];
                if (o == null) continue;
                if (o.canOverlay(zone.base[x][y])) continue;
                int sx = x * cellW;
                int sy = (ZONE_H - 1 - y) * cellH;
                g.drawLine(sx, sy, sx + cellW, sy + cellH);
                g.drawLine(sx + cellW, sy, sx, sy + cellH);
            }
        }

        // Pass 5 — bottom label strip.
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(LABEL_BG);
        g.fillRect(0, ZONE_H * cellH, imgW, 28);
        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        String label = String.format("nature-zone seed=%d  %dx%d cells @ %dx%dpx uniform  (NW=grass NE=dirt SW=sand SE=water)",
                seed, ZONE_W, ZONE_H, cellW, cellH);
        g.drawString(label, 8, ZONE_H * cellH + 19);

        g.dispose();
        return img;
    }

    /**
     * Translate grid coordinates into uniform-cell centers and hand off to
     * the shared drawer. The drawer routes through {@link TileSink} so the
     * source-rect computation (inset rule, frame lookup) is the same one
     * that will run in-game; only the {@code drawImage} call inside
     * {@link Graphics2DTileSink} is test-specific.
     */
    private static void stampTile(SlicedTileDrawer drawer, TileSink sink,
                                  NatureTile tile,
                                  int gridX, int gridY, int cellW, int cellH) {
        float dstCx = gridX * cellW + cellW / 2f;
        float dstCy = (ZONE_H - 1 - gridY) * cellH + cellH / 2f;
        drawer.draw(sink, tile, dstCx, dstCy, cellW, cellH, 1f);
    }

    private static BufferedImage composeContactSheet(BufferedImage[] tiles, int cols) {
        int rows = (tiles.length + cols - 1) / cols;
        int tileW = tiles[0].getWidth();
        int tileH = tiles[0].getHeight();
        int gap = 12;
        BufferedImage sheet = new BufferedImage(
                cols * tileW + (cols + 1) * gap,
                rows * tileH + (rows + 1) * gap,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x10, 0x14, 0x1C));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < tiles.length; i++) {
            int cx = i % cols;
            int cy = i / cols;
            int x = gap + cx * (tileW + gap);
            int y = gap + cy * (tileH + gap);
            g.drawImage(tiles[i], x, y, null);
        }
        g.dispose();
        return sheet;
    }
}
