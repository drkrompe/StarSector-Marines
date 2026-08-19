package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-CPU pixel oracle for {@link GroundParallaxPipeline}'s GLSL 1.20
 * composite. It creates a representative 16:9 battle-ground FBO from the real
 * terrain assets, builds the matching macro/micro/water/shore metadata target,
 * and evaluates the shader equations with bilinear texture sampling.
 *
 * <p>The diagnostic contact sheet is written to
 * {@code build/surface-relief/parallax-pixel-comparison.png}. It contains the
 * zero-strength baseline, default and maximum dial settings, amplified diffs,
 * and the composed height target. Console metrics report changed-pixel share,
 * mean/max RGB delta, and maximum displacement in physical screen pixels.
 */
class GroundParallaxPixelComparisonTest {

    private static final int VIEW_W = 1024;
    private static final int VIEW_H = 576;
    private static final int CELL_PX = 32;
    private static final int GRID_W = VIEW_W / CELL_PX;
    private static final int GRID_H = VIEW_H / CELL_PX;
    private static final int BACKDROP_RGB = 0x182230;
    private static final float WAVE_TIME = 1.75f;
    private static final Path OUT_DIR = Paths.get("build", "surface-relief");

    @Test
    void rendersHeadlessShaderComparisonFromRealTerrainAssets() throws Exception {
        Scene scene = buildScene();
        Warp baseline = warp(scene, 0f, 0f, 0f, WAVE_TIME);
        Warp atDefault = warp(scene, GroundParallaxPipeline.DEFAULT_STRENGTH,
                GroundParallaxPipeline.DEFAULT_SURFACE_STRENGTH,
                GroundParallaxPipeline.DEFAULT_WATER_WAVE_AMPLITUDE, WAVE_TIME);
        Warp atNextWave = warp(scene, GroundParallaxPipeline.DEFAULT_STRENGTH,
                GroundParallaxPipeline.DEFAULT_SURFACE_STRENGTH,
                GroundParallaxPipeline.DEFAULT_WATER_WAVE_AMPLITUDE, WAVE_TIME + 0.5f);
        Warp atMax = warp(scene, GroundParallaxPipeline.MAX_STRENGTH,
                GroundParallaxPipeline.MAX_SURFACE_STRENGTH,
                GroundParallaxPipeline.MAX_WATER_WAVE_AMPLITUDE, WAVE_TIME);

        Metrics zeroMetrics = compare(scene.color, baseline.image);
        Metrics defaultMetrics = compare(scene.color, atDefault.image);
        Metrics animationMetrics = compare(atDefault.image, atNextWave.image);
        Metrics maxMetrics = compare(scene.color, atMax.image);

        assertEquals(0, zeroMetrics.changedPixels,
                "zero strength must be pixel-identical to the source FBO");
        assertTrue(defaultMetrics.changedPixels > VIEW_W * VIEW_H / 20,
                "default strength should measurably change real terrain pixels");
        assertTrue(defaultMetrics.meanChannelDelta > 0.05,
                "default strength should produce a non-zero aggregate color delta");
        assertTrue(maxMetrics.meanChannelDelta > defaultMetrics.meanChannelDelta * 2.0,
                "maximum dial strength should be materially stronger than default");
        assertTrue(atMax.maxDisplacementPx > atDefault.maxDisplacementPx * 3.0,
                "screen-pixel displacement should scale with the strength uniform");
        assertTrue(animationMetrics.changedPixels > VIEW_W * VIEW_H / 100,
                "advancing wave time should visibly animate the water surface");
        assertEquals(0, atDefault.waterLandCrossings,
                "water displacement must backtrack before sampling a land texel");

        Files.createDirectories(OUT_DIR);
        BufferedImage defaultDiff = difference(scene.color, atDefault.image, 16);
        BufferedImage maxDiff = difference(scene.color, atMax.image, 8);
        BufferedImage heightImage = metadataImage(scene);
        BufferedImage contact = contactSheet(scene.color, atDefault.image, atMax.image,
                defaultDiff, maxDiff, heightImage, defaultMetrics, maxMetrics,
                atDefault.maxDisplacementPx, atMax.maxDisplacementPx);
        Path output = OUT_DIR.resolve("parallax-pixel-comparison.png");
        ImageIO.write(contact, "PNG", output.toFile());

        System.out.printf(Locale.ROOT,
                "[parallax-pixel] default %.4f: changed %.2f%%, mean RGB delta %.3f, "
                        + "max channel delta %d, max displacement %.3f px%n",
                GroundParallaxPipeline.DEFAULT_STRENGTH,
                defaultMetrics.changedPercent(), defaultMetrics.meanChannelDelta,
                defaultMetrics.maxChannelDelta, atDefault.maxDisplacementPx);
        System.out.printf(Locale.ROOT,
                "[parallax-pixel] max     %.4f: changed %.2f%%, mean RGB delta %.3f, "
                        + "max channel delta %d, max displacement %.3f px%n",
                GroundParallaxPipeline.MAX_STRENGTH,
                maxMetrics.changedPercent(), maxMetrics.meanChannelDelta,
                maxMetrics.maxChannelDelta, atMax.maxDisplacementPx);
        System.out.println("[parallax-pixel] wrote " + output.toAbsolutePath());
    }

    /** Mirrors the fragment shader from normalized UV through its final bilinear color lookup. */
    private static Warp warp(Scene scene, float structureStrength, float surfaceStrength,
                             float waterWaveAmplitude, float waveTime) {
        BufferedImage output = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
        float aspect = VIEW_W / (float) VIEW_H;
        double maxDisplacementSq = 0.0;
        int waterLandCrossings = 0;

        for (int y = 0; y < VIEW_H; y++) {
            float v = 1f - (y + 0.5f) / VIEW_H;
            for (int x = 0; x < VIEW_W; x++) {
                float u = (x + 0.5f) / VIEW_W;
                int index = y * VIEW_W + x;
                float macro = scene.macro[index];
                float micro = scene.micro[index];
                float water = scene.water[index];
                float shore = scene.shore[index];
                float macroMaterial = lerp(1f, GroundParallaxPipeline.WATER_MACRO_SCALE, water);
                float microMaterial = lerp(1f, GroundParallaxPipeline.WATER_MICRO_SCALE, water);
                float relief = (macro - GroundParallaxPipeline.MACRO_CENTER)
                        * structureStrength * macroMaterial
                        + GroundHeightPass.microRelief(micro) * surfaceStrength * microMaterial;

                float dx = (0.5f - u) * aspect;
                float dy = 0.5f - v;
                float invLength = invSqrt(dx * dx + dy * dy
                        + GroundParallaxPipeline.EYE_HEIGHT * GroundParallaxPipeline.EYE_HEIGHT);
                float eyeX = dx * invLength;
                float eyeY = dy * invLength;
                float baseU = relief * eyeX / aspect;
                float baseV = relief * eyeY;
                float worldX = GRID_W * 0.5f + (u - 0.5f) * GRID_W;
                float worldY = GRID_H * 0.5f + (v - 0.5f) * GRID_H;
                float waveX = (float) Math.sin(worldX * 2.15f + worldY * 0.65f
                        + waveTime * 1.35f);
                float waveY = (float) Math.cos(worldX * -0.45f + worldY * 2.40f
                        - waveTime * 1.10f);
                float shoreWaveScale = lerp(GroundParallaxPipeline.WATER_INTERIOR_WAVE_SCALE,
                        1f, shore);
                float totalU = baseU + waveX * (CELL_PX / (float) VIEW_W)
                        * waterWaveAmplitude * shoreWaveScale * water;
                float totalV = baseV + waveY * (CELL_PX / (float) VIEW_H)
                        * waterWaveAmplitude * shoreWaveScale * water;
                float sampleU = clamp01(u + totalU);
                float sampleV = clamp01(v + totalV);
                if (water > 0.5f && sampleBilinearUv(scene.water, sampleU, sampleV) < 0.5f) {
                    float halfU = clamp01(u + totalU * 0.5f);
                    float halfV = clamp01(v + totalV * 0.5f);
                    if (sampleBilinearUv(scene.water, halfU, halfV) >= 0.5f) {
                        sampleU = halfU;
                        sampleV = halfV;
                    } else {
                        sampleU = u;
                        sampleV = v;
                    }
                }
                if (water > 0.5f && sampleBilinearUv(scene.water, sampleU, sampleV) < 0.5f) {
                    waterLandCrossings++;
                }

                // Measure the post-clamp displacement the color lookup really
                // receives; raw offU/offV can overstate motion at FBO edges.
                double pixelDx = (sampleU - u) * VIEW_W;
                double pixelDy = (sampleV - v) * VIEW_H;
                maxDisplacementSq = Math.max(maxDisplacementSq,
                        pixelDx * pixelDx + pixelDy * pixelDy);
                int color = sampleBilinearUv(scene.color, sampleU, sampleV);
                float crest = (float) Math.sin(worldX * 1.70f + worldY * 0.80f
                        - waveTime * 2.20f) * 0.5f + 0.5f;
                float foam = water * shore * smoothstep(0.72f, 1f, crest)
                        * GroundParallaxPipeline.WATER_FOAM_AMOUNT
                        * (waterWaveAmplitude >= 0.0001f ? 1f : 0f);
                output.setRGB(x, y, mixRgb(color, 0xFFC2E6FF, foam));
            }
        }
        return new Warp(output, Math.sqrt(maxDisplacementSq), waterLandCrossings);
    }

    private static Scene buildScene() throws IOException {
        TileRegistry registry = TileRegistry.installed();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        if (registry == null || mapping == null) {
            throw new IllegalStateException("global test registry bootstrap did not run");
        }

        BufferedImage color = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D clear = color.createGraphics();
        clear.setColor(new Color(BACKDROP_RGB));
        clear.fillRect(0, 0, VIEW_W, VIEW_H);
        clear.dispose();
        float[] macro = filledChannel(0.5f);
        float[] micro = filledChannel(0.5f);
        float[] water = new float[VIEW_W * VIEW_H];
        float[] shore = new float[VIEW_W * VIEW_H];
        Map<String, BufferedImage> sheets = new HashMap<>();

        for (int gy = 0; gy < GRID_H; gy++) {
            for (int gx = 0; gx < GRID_W; gx++) {
                if (isBuildingWall(gx, gy)) {
                    stampWall(color, macro, micro, water, shore, sheets, mapping, gx, gy);
                } else if (isBuildingInterior(gx, gy)) {
                    stampBlock(color, macro, micro, water, shore, sheets, registry.block("urban.floor"),
                            mapping.macroHeight(CellTopology.GroundKind.INDOOR), gx, gy, false, false);
                } else if (isSceneWater(gx, gy)) {
                    stampBlock(color, macro, micro, water, shore, sheets, registry.block("water.water"),
                            mapping.macroHeight(CellTopology.GroundKind.WATER), gx, gy, true, true);
                } else {
                    String blockId;
                    if (gy < GRID_H / 2) blockId = gx < GRID_W / 2 ? "floors.grass" : "floors.dirt";
                    else blockId = gx < GRID_W / 2 ? "floors.sand" : "floors.stone";
                    stampBlock(color, macro, micro, water, shore, sheets, registry.block(blockId),
                            0.5f, gx, gy, true, false);
                }
            }
        }
        return new Scene(color, macro, micro, water, shore);
    }

    private static void stampWall(BufferedImage color, float[] macro, float[] micro,
                                  float[] water, float[] shore,
                                  Map<String, BufferedImage> sheets,
                                  GenMappingRegistry mapping, int gx, int gy) throws IOException {
        boolean north = isBuildingWall(gx, gy - 1);
        boolean south = isBuildingWall(gx, gy + 1);
        boolean east = isBuildingWall(gx + 1, gy);
        boolean west = isBuildingWall(gx - 1, gy);
        TileManifest.TileFrame frame = TileManifest.pickWallTile(north, south, east, west);
        if (frame != null) {
            BufferedImage sheet = sheet(sheets, TileManifest.SHEET);
            stampColor(color, sheet, frame.col * TileManifest.TILE_SIZE,
                    frame.row * TileManifest.TILE_SIZE,
                    TileManifest.TILE_SIZE, TileManifest.TILE_SIZE, gx, gy);
        }
        fillChannels(macro, micro, water, shore,
                mapping.wallMacroHeight(), 0.5f, 0f, 0f, gx, gy);
    }

    private static void stampBlock(BufferedImage color, float[] macro, float[] micro,
                                   float[] water, float[] shore,
                                   Map<String, BufferedImage> sheets, GridBlockDef block,
                                   float macroHeight, int gx, int gy,
                                   boolean derived, boolean waterSurface) throws IOException {
        float waterValue = waterSurface ? 1f : 0f;
        float shoreValue = waterSurface ? sceneShoreFactor(gx, gy) : 0f;
        int[] frame = block.resolve(false, false, false, false, gx, gy);
        if (frame == null) {
            fillChannels(macro, micro, water, shore,
                    macroHeight, 0.5f, waterValue, shoreValue, gx, gy);
            return;
        }
        int inset = block.cellPx >= TileManifest.TILE_SIZE
                ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE
                : FixedGridTileDrawer.GROUND_INSET_PX_SMALL;
        int srcX = frame[0] * block.cellPx + inset;
        int srcY = frame[1] * block.cellPx + inset;
        int srcW = block.cellPx - inset * 2;
        int srcH = block.cellPx - inset * 2;
        stampColor(color, sheet(sheets, block.sheetPath), srcX, srcY, srcW, srcH, gx, gy);
        if (!derived) {
            fillChannels(macro, micro, water, shore,
                    macroHeight, 0.5f, waterValue, shoreValue, gx, gy);
            return;
        }
        BufferedImage heightSheet = sheet(sheets,
                GroundMicroHeightSampler.derivedHeightPath(block.sheetPath));
        fillChannels(macro, micro, water, shore,
                macroHeight, 0.5f, waterValue, shoreValue, gx, gy);
        stampDerivedHeight(micro, heightSheet, srcX, srcY, srcW, srcH, gx, gy);
    }

    private static void stampColor(BufferedImage target, BufferedImage source,
                                   int srcX, int srcY, int srcW, int srcH, int gx, int gy) {
        int dstX = gx * CELL_PX;
        int dstY = gy * CELL_PX;
        for (int py = 0; py < CELL_PX; py++) {
            double sy = srcY + (py + 0.5) * srcH / CELL_PX - 0.5;
            for (int px = 0; px < CELL_PX; px++) {
                double sx = srcX + (px + 0.5) * srcW / CELL_PX - 0.5;
                int sample = sampleBilinear(source, sx, sy);
                int x = dstX + px;
                int y = dstY + py;
                target.setRGB(x, y, alphaOver(sample, target.getRGB(x, y)));
            }
        }
    }

    private static void stampDerivedHeight(float[] target, BufferedImage source,
                                           int srcX, int srcY, int srcW, int srcH,
                                           int gx, int gy) {
        int dstX = gx * CELL_PX;
        int dstY = gy * CELL_PX;
        for (int py = 0; py < CELL_PX; py++) {
            double sy = srcY + (py + 0.5) * srcH / CELL_PX - 0.5;
            for (int px = 0; px < CELL_PX; px++) {
                double sx = srcX + (px + 0.5) * srcW / CELL_PX - 0.5;
                float micro = ((sampleBilinear(source, sx, sy) >>> 16) & 0xFF) / 255f;
                target[(dstY + py) * VIEW_W + dstX + px] = micro;
            }
        }
    }

    private static void fillChannels(float[] macro, float[] micro, float[] water, float[] shore,
                                     float macroValue, float microValue,
                                     float waterValue, float shoreValue, int gx, int gy) {
        int dstX = gx * CELL_PX;
        int dstY = gy * CELL_PX;
        for (int py = 0; py < CELL_PX; py++) {
            int row = (dstY + py) * VIEW_W + dstX;
            for (int px = 0; px < CELL_PX; px++) {
                int index = row + px;
                macro[index] = macroValue;
                micro[index] = microValue;
                water[index] = waterValue;
                shore[index] = shoreValue;
            }
        }
    }

    private static float[] filledChannel(float value) {
        float[] channel = new float[VIEW_W * VIEW_H];
        java.util.Arrays.fill(channel, value);
        return channel;
    }

    private static boolean isBuildingWall(int x, int y) {
        if (x < 21 || x > 30 || y < 2 || y > 11) return false;
        return x == 21 || x == 30 || y == 2 || y == 11;
    }

    private static boolean isBuildingInterior(int x, int y) {
        return x > 21 && x < 30 && y > 2 && y < 11;
    }

    private static boolean isWater(int x, int y) {
        double dx = (x - 5.5) / 5.0;
        double dy = (y - 8.5) / 6.0;
        return dx * dx + dy * dy < 1.0;
    }

    private static boolean isSceneWater(int x, int y) {
        return x >= 0 && x < GRID_W && y >= 0 && y < GRID_H
                && !isBuildingWall(x, y) && !isBuildingInterior(x, y) && isWater(x, y);
    }

    private static float sceneShoreFactor(int x, int y) {
        int radius = GroundHeightPass.SHORE_RADIUS_CELLS;
        int nearest = radius + 1;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distance = Math.abs(dx) + Math.abs(dy);
                if (distance == 0 || distance > radius) continue;
                if (!isSceneWater(x + dx, y + dy)) nearest = Math.min(nearest, distance);
            }
        }
        return nearest <= radius ? (radius - nearest + 1f) / radius : 0f;
    }

    private static BufferedImage sheet(Map<String, BufferedImage> sheets, String path) throws IOException {
        BufferedImage cached = sheets.get(path);
        if (cached != null) return cached;
        Path file = Paths.get("mod", path.split("/"));
        BufferedImage loaded = ImageIO.read(file.toFile());
        if (loaded == null) throw new IOException("ImageIO returned null for " + file);
        sheets.put(path, loaded);
        return loaded;
    }

    private static Metrics compare(BufferedImage baseline, BufferedImage candidate) {
        long changed = 0;
        long absoluteChannelDelta = 0;
        int maxChannelDelta = 0;
        for (int y = 0; y < VIEW_H; y++) {
            for (int x = 0; x < VIEW_W; x++) {
                int a = baseline.getRGB(x, y);
                int b = candidate.getRGB(x, y);
                int dr = Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF));
                int dg = Math.abs(((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF));
                int db = Math.abs((a & 0xFF) - (b & 0xFF));
                if (dr != 0 || dg != 0 || db != 0) changed++;
                absoluteChannelDelta += dr + dg + db;
                maxChannelDelta = Math.max(maxChannelDelta, Math.max(dr, Math.max(dg, db)));
            }
        }
        return new Metrics(changed,
                absoluteChannelDelta / (double) (VIEW_W * VIEW_H * 3L), maxChannelDelta);
    }

    private static BufferedImage difference(BufferedImage baseline, BufferedImage candidate, int gain) {
        BufferedImage diff = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < VIEW_H; y++) {
            for (int x = 0; x < VIEW_W; x++) {
                int a = baseline.getRGB(x, y);
                int b = candidate.getRGB(x, y);
                int r = Math.min(255, Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF)) * gain);
                int g = Math.min(255, Math.abs(((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF)) * gain);
                int bl = Math.min(255, Math.abs((a & 0xFF) - (b & 0xFF)) * gain);
                diff.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | bl);
            }
        }
        return diff;
    }

    private static BufferedImage metadataImage(Scene scene) {
        BufferedImage image = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < VIEW_H; y++) {
            for (int x = 0; x < VIEW_W; x++) {
                int index = y * VIEW_W + x;
                int r = Math.round(clamp01(scene.macro[index]) * 255f);
                int g = Math.round(clamp01(scene.micro[index]) * 255f);
                int b = Math.round(clamp01(Math.max(scene.water[index], scene.shore[index])) * 255f);
                image.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    private static BufferedImage contactSheet(
            BufferedImage baseline, BufferedImage atDefault, BufferedImage atMax,
            BufferedImage defaultDiff, BufferedImage maxDiff, BufferedImage height,
            Metrics defaultMetrics, Metrics maxMetrics,
            double defaultDisplacement, double maxDisplacement) {
        int panelW = VIEW_W / 2;
        int panelH = VIEW_H / 2;
        int labelH = 34;
        BufferedImage contact = new BufferedImage(panelW * 3, (panelH + labelH) * 2,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = contact.createGraphics();
        g.setColor(new Color(0x10151D));
        g.fillRect(0, 0, contact.getWidth(), contact.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        drawPanel(g, baseline, 0, 0, panelW, panelH, labelH, "baseline / strength 0");
        drawPanel(g, atDefault, 1, 0, panelW, panelH, labelH,
                String.format(Locale.ROOT, "default %.4f | %.3f px | mean %.3f",
                        GroundParallaxPipeline.DEFAULT_STRENGTH,
                        defaultDisplacement, defaultMetrics.meanChannelDelta));
        drawPanel(g, atMax, 2, 0, panelW, panelH, labelH,
                String.format(Locale.ROOT, "max %.4f | %.3f px | mean %.3f",
                        GroundParallaxPipeline.MAX_STRENGTH,
                        maxDisplacement, maxMetrics.meanChannelDelta));
        drawPanel(g, defaultDiff, 0, 1, panelW, panelH, labelH,
                String.format(Locale.ROOT, "default abs diff x16 | %.2f%% changed",
                        defaultMetrics.changedPercent()));
        drawPanel(g, maxDiff, 1, 1, panelW, panelH, labelH,
                String.format(Locale.ROOT, "max abs diff x8 | %.2f%% changed",
                        maxMetrics.changedPercent()));
        drawPanel(g, height, 2, 1, panelW, panelH, labelH,
                "metadata: R macro / G micro / B water+shore");
        g.dispose();
        return contact;
    }

    private static void drawPanel(Graphics2D g, BufferedImage image, int column, int row,
                                  int panelW, int panelH, int labelH, String label) {
        int x = column * panelW;
        int y = row * (panelH + labelH);
        g.drawImage(image, x, y, panelW, panelH, null);
        g.setColor(new Color(0xDCE8F8));
        g.drawString(label, x + 10, y + panelH + 22);
    }

    private static int sampleBilinearUv(BufferedImage image, float u, float v) {
        return sampleBilinear(image, u * image.getWidth() - 0.5,
                (1.0 - v) * image.getHeight() - 0.5);
    }

    private static float sampleBilinearUv(float[] channel, float u, float v) {
        double x = u * VIEW_W - 0.5;
        double y = (1.0 - v) * VIEW_H - 0.5;
        int rawX0 = (int) Math.floor(x);
        int rawY0 = (int) Math.floor(y);
        int x0 = clamp(rawX0, 0, VIEW_W - 1);
        int y0 = clamp(rawY0, 0, VIEW_H - 1);
        int x1 = clamp(rawX0 + 1, 0, VIEW_W - 1);
        int y1 = clamp(rawY0 + 1, 0, VIEW_H - 1);
        float tx = (float) (x - Math.floor(x));
        float ty = (float) (y - Math.floor(y));
        float top = lerp(channel[y0 * VIEW_W + x0], channel[y0 * VIEW_W + x1], tx);
        float bottom = lerp(channel[y1 * VIEW_W + x0], channel[y1 * VIEW_W + x1], tx);
        return lerp(top, bottom, ty);
    }

    private static int sampleBilinear(BufferedImage image, double x, double y) {
        int rawX0 = (int) Math.floor(x);
        int rawY0 = (int) Math.floor(y);
        int x0 = clamp(rawX0, 0, image.getWidth() - 1);
        int y0 = clamp(rawY0, 0, image.getHeight() - 1);
        int x1 = clamp(rawX0 + 1, 0, image.getWidth() - 1);
        int y1 = clamp(rawY0 + 1, 0, image.getHeight() - 1);
        double tx = x - Math.floor(x);
        double ty = y - Math.floor(y);
        int c00 = image.getRGB(x0, y0);
        int c10 = image.getRGB(x1, y0);
        int c01 = image.getRGB(x0, y1);
        int c11 = image.getRGB(x1, y1);
        int a = bilerp(c00 >>> 24, c10 >>> 24, c01 >>> 24, c11 >>> 24, tx, ty);
        int r = bilerp(c00 >>> 16 & 0xFF, c10 >>> 16 & 0xFF,
                c01 >>> 16 & 0xFF, c11 >>> 16 & 0xFF, tx, ty);
        int g = bilerp(c00 >>> 8 & 0xFF, c10 >>> 8 & 0xFF,
                c01 >>> 8 & 0xFF, c11 >>> 8 & 0xFF, tx, ty);
        int b = bilerp(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, tx, ty);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int bilerp(int c00, int c10, int c01, int c11, double tx, double ty) {
        double top = c00 + (c10 - c00) * tx;
        double bottom = c01 + (c11 - c01) * tx;
        return clamp((int) Math.round(top + (bottom - top) * ty), 0, 255);
    }

    private static int alphaOver(int source, int destination) {
        int alpha = source >>> 24;
        if (alpha == 255) return source;
        if (alpha == 0) return destination;
        int inverse = 255 - alpha;
        int r = (((source >>> 16) & 0xFF) * alpha + ((destination >>> 16) & 0xFF) * inverse) / 255;
        int g = (((source >>> 8) & 0xFF) * alpha + ((destination >>> 8) & 0xFF) * inverse) / 255;
        int b = ((source & 0xFF) * alpha + (destination & 0xFF) * inverse) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int mixRgb(int source, int target, float amount) {
        amount = clamp01(amount);
        int a = source >>> 24;
        int r = Math.round(lerp((source >>> 16) & 0xFF, (target >>> 16) & 0xFF, amount));
        int g = Math.round(lerp((source >>> 8) & 0xFF, (target >>> 8) & 0xFF, amount));
        int b = Math.round(lerp(source & 0xFF, target & 0xFF, amount));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float invSqrt(float value) { return 1f / (float) Math.sqrt(value); }
    private static float clamp01(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static final class Scene {
        final BufferedImage color;
        final float[] macro;
        final float[] micro;
        final float[] water;
        final float[] shore;

        Scene(BufferedImage color, float[] macro, float[] micro, float[] water, float[] shore) {
            this.color = color;
            this.macro = macro;
            this.micro = micro;
            this.water = water;
            this.shore = shore;
        }
    }

    private static final class Warp {
        final BufferedImage image;
        final double maxDisplacementPx;
        final int waterLandCrossings;

        Warp(BufferedImage image, double maxDisplacementPx, int waterLandCrossings) {
            this.image = image;
            this.maxDisplacementPx = maxDisplacementPx;
            this.waterLandCrossings = waterLandCrossings;
        }
    }

    private static final class Metrics {
        final long changedPixels;
        final double meanChannelDelta;
        final int maxChannelDelta;

        Metrics(long changedPixels, double meanChannelDelta, int maxChannelDelta) {
            this.changedPixels = changedPixels;
            this.meanChannelDelta = meanChannelDelta;
            this.maxChannelDelta = maxChannelDelta;
        }

        double changedPercent() { return changedPixels * 100.0 / (VIEW_W * VIEW_H); }
    }
}
