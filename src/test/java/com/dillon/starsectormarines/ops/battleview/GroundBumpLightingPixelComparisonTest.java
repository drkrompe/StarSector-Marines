package com.dillon.starsectormarines.ops.battleview;

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
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Asset-backed CPU oracle for S3's normal decode, Lambert term, and falloff. */
class GroundBumpLightingPixelComparisonTest {

    private static final int CELL_PX = 32;
    private static final int GRID_W = 12;
    private static final int GRID_H = 8;
    private static final int VIEW_W = GRID_W * CELL_PX;
    private static final int VIEW_H = GRID_H * CELL_PX;
    private static final Path OUT_DIR = Paths.get("build", "surface-relief");

    @Test
    void rendersDirectionalBumpLightFromRealNormalAtlas() throws Exception {
        Scene scene = buildScene();
        GroundLightService.Light light = new GroundLightService.Light(
                4f, 4f, 1.2f, 5.5f, new Color(0xFF, 0x88, 0x48), 0.95f, 1f);

        BufferedImage noLights = render(scene.color, scene.normal, new GroundLightService.Light[0]);
        BufferedImage lit = render(scene.color, scene.normal, new GroundLightService.Light[]{light});
        BufferedImage flatLit = render(scene.color, flatNormal(), new GroundLightService.Light[]{light});

        Metrics identity = compare(scene.color, noLights);
        Metrics litMetrics = compare(scene.color, lit);
        Metrics normalContribution = compare(flatLit, lit);
        assertEquals(0, identity.changedPixels,
                "an empty light list must preserve the accepted S2 image exactly");
        assertTrue(litMetrics.changedPixels > VIEW_W * VIEW_H / 8,
                "the event light should visibly affect a broad local patch");
        assertTrue(litMetrics.meanChannelDelta > 1.0,
                "the default S3 strength should survive 8-bit output quantization");
        assertTrue(normalContribution.changedPixels > VIEW_W * VIEW_H / 20,
                "derived normals must materially alter the result versus a flat normal");

        Files.createDirectories(OUT_DIR);
        BufferedImage diff = difference(scene.color, lit, 5);
        BufferedImage contact = contactSheet(scene.color, scene.normal, lit, diff,
                litMetrics, normalContribution);
        Path output = OUT_DIR.resolve("bump-lighting-comparison.png");
        ImageIO.write(contact, "PNG", output.toFile());
        Field fragmentSource = GroundParallaxPipeline.class.getDeclaredField("FRAGMENT_SRC");
        fragmentSource.setAccessible(true);
        Files.writeString(OUT_DIR.resolve("ground-relief.frag"),
                (String) fragmentSource.get(null));
        System.out.printf(Locale.ROOT,
                "[bump-lighting] changed %.2f%%, mean RGB delta %.3f, normal-driven %.2f%%; wrote %s%n",
                litMetrics.changedPercent(), litMetrics.meanChannelDelta,
                normalContribution.changedPercent(), output.toAbsolutePath());
    }

    private static Scene buildScene() throws Exception {
        TileRegistry registry = TileRegistry.installed();
        GridBlockDef block = registry.block("floors.stone");
        BufferedImage sheet = image(block.sheetPath);
        BufferedImage normalSheet = image(GroundMicroHeightSampler.derivedNormalPath(block.sheetPath));
        BufferedImage color = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
        BufferedImage normal = flatNormal();
        Graphics2D cg = color.createGraphics();
        Graphics2D ng = normal.createGraphics();
        cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        ng.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int inset = block.cellPx >= TileManifest.TILE_SIZE
                ? FixedGridTileDrawer.GROUND_INSET_PX_LARGE
                : FixedGridTileDrawer.GROUND_INSET_PX_SMALL;
        for (int gy = 0; gy < GRID_H; gy++) {
            for (int gx = 0; gx < GRID_W; gx++) {
                int[] frame = block.resolve(false, false, false, false, gx, gy);
                int sx0 = frame[0] * block.cellPx + inset;
                int sy0 = frame[1] * block.cellPx + inset;
                int sx1 = sx0 + block.cellPx - inset * 2;
                int sy1 = sy0 + block.cellPx - inset * 2;
                int dx0 = gx * CELL_PX;
                int dy0 = gy * CELL_PX;
                cg.drawImage(sheet, dx0, dy0, dx0 + CELL_PX, dy0 + CELL_PX,
                        sx0, sy0, sx1, sy1, null);
                ng.drawImage(normalSheet, dx0, dy0, dx0 + CELL_PX, dy0 + CELL_PX,
                        sx0, sy0, sx1, sy1, null);
            }
        }
        cg.dispose();
        ng.dispose();
        return new Scene(color, normal);
    }

    private static BufferedImage render(BufferedImage color, BufferedImage normal,
                                        GroundLightService.Light[] lights) {
        BufferedImage output = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < VIEW_H; y++) {
            float worldY = (VIEW_H - y - 0.5f) / CELL_PX;
            for (int x = 0; x < VIEW_W; x++) {
                float worldX = (x + 0.5f) / CELL_PX;
                int source = color.getRGB(x, y);
                int encoded = normal.getRGB(x, y);
                float nx = (((encoded >>> 16) & 0xFF) / 255f) * 2f - 1f;
                float ny = -((((encoded >>> 8) & 0xFF) / 255f) * 2f - 1f);
                float nz = ((encoded & 0xFF) / 255f) * 2f - 1f;
                float inverseNormal = inverseLength(nx, ny, nz);
                nx *= inverseNormal;
                ny *= inverseNormal;
                nz *= inverseNormal;

                float addR = 0f;
                float addG = 0f;
                float addB = 0f;
                for (GroundLightService.Light light : lights) {
                    float lx = light.x - worldX;
                    float ly = light.y - worldY;
                    float distance = (float) Math.sqrt(lx * lx + ly * ly);
                    float radial = clamp01(1f - distance / Math.max(light.radius, 0.001f));
                    float inverseLight = inverseLength(lx, ly, light.height);
                    float lambert = Math.max(0f,
                            nx * lx * inverseLight + ny * ly * inverseLight
                                    + nz * light.height * inverseLight);
                    float amount = light.effectiveIntensity() * radial * radial * lambert;
                    addR += light.color.getRed() / 255f * amount;
                    addG += light.color.getGreen() / 255f * amount;
                    addB += light.color.getBlue() / 255f * amount;
                }

                float r = ((source >>> 16) & 0xFF) / 255f;
                float g = ((source >>> 8) & 0xFF) / 255f;
                float b = (source & 0xFF) / 255f;
                r += addR * (0.18f + r * 0.82f) * GroundParallaxPipeline.DEFAULT_LIGHTING_STRENGTH;
                g += addG * (0.18f + g * 0.82f) * GroundParallaxPipeline.DEFAULT_LIGHTING_STRENGTH;
                b += addB * (0.18f + b * 0.82f) * GroundParallaxPipeline.DEFAULT_LIGHTING_STRENGTH;
                output.setRGB(x, y, (source & 0xFF000000)
                        | unitByte(r) << 16 | unitByte(g) << 8 | unitByte(b));
            }
        }
        return output;
    }

    private static BufferedImage flatNormal() {
        BufferedImage image = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(128, 128, 255));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        g.dispose();
        return image;
    }

    private static BufferedImage image(String path) throws Exception {
        BufferedImage image = ImageIO.read(Paths.get("mod", path.split("/")).toFile());
        if (image == null) throw new IllegalStateException("ImageIO returned null for " + path);
        return image;
    }

    private static Metrics compare(BufferedImage baseline, BufferedImage candidate) {
        long changed = 0;
        long delta = 0;
        for (int y = 0; y < VIEW_H; y++) {
            for (int x = 0; x < VIEW_W; x++) {
                int a = baseline.getRGB(x, y);
                int b = candidate.getRGB(x, y);
                int dr = Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF));
                int dg = Math.abs(((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF));
                int db = Math.abs((a & 0xFF) - (b & 0xFF));
                if (dr != 0 || dg != 0 || db != 0) changed++;
                delta += dr + dg + db;
            }
        }
        return new Metrics(changed, delta / (double) (VIEW_W * VIEW_H * 3L));
    }

    private static BufferedImage difference(BufferedImage baseline, BufferedImage candidate, int gain) {
        BufferedImage image = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < VIEW_H; y++) {
            for (int x = 0; x < VIEW_W; x++) {
                int a = baseline.getRGB(x, y);
                int b = candidate.getRGB(x, y);
                int r = Math.min(255, Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF)) * gain);
                int g = Math.min(255, Math.abs(((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF)) * gain);
                int bl = Math.min(255, Math.abs((a & 0xFF) - (b & 0xFF)) * gain);
                image.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | bl);
            }
        }
        return image;
    }

    private static BufferedImage contactSheet(BufferedImage color, BufferedImage normal,
                                               BufferedImage lit, BufferedImage diff,
                                               Metrics litMetrics, Metrics normalMetrics) {
        int labelH = 30;
        BufferedImage contact = new BufferedImage(VIEW_W * 2, (VIEW_H + labelH) * 2,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = contact.createGraphics();
        g.setColor(new Color(0x10151D));
        g.fillRect(0, 0, contact.getWidth(), contact.getHeight());
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        drawPanel(g, color, 0, 0, labelH, "source / no lights = pixel-identical");
        drawPanel(g, normal, 1, 0, labelH, "composed derived normals");
        drawPanel(g, lit, 0, 1, labelH,
                String.format(Locale.ROOT, "event light | mean delta %.3f", litMetrics.meanChannelDelta));
        drawPanel(g, diff, 1, 1, labelH,
                String.format(Locale.ROOT, "abs diff x5 | normals affect %.2f%%", normalMetrics.changedPercent()));
        g.dispose();
        return contact;
    }

    private static void drawPanel(Graphics2D g, BufferedImage image, int column, int row,
                                  int labelH, String label) {
        int x = column * VIEW_W;
        int y = row * (VIEW_H + labelH);
        g.drawImage(image, x, y, null);
        g.setColor(new Color(0xDCE8F8));
        g.drawString(label, x + 8, y + VIEW_H + 20);
    }

    private static float inverseLength(float x, float y, float z) {
        return 1f / (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static int unitByte(float value) {
        return Math.round(clamp01(value) * 255f);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private record Scene(BufferedImage color, BufferedImage normal) {}

    private record Metrics(long changedPixels, double meanChannelDelta) {
        double changedPercent() {
            return changedPixels * 100.0 / (VIEW_W * VIEW_H);
        }
    }
}
