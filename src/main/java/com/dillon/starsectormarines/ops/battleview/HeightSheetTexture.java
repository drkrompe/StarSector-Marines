package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Lazy-loaded, CPU-side raster handle to one S1-derived {@code <sheet>_height.png}.
 * Read via the same sanctioned {@code SettingsAPI.openStream + ImageIO} path
 * {@code battle.world.tiles.SheetTexture} uses for the color sheets (bundled mod
 * asset, not save-data — the {@code java.io.File}/nio script-sandbox restriction
 * doesn't apply here).
 *
 * <p>One-shot, idempotent load; a missing file (S1 deliberately didn't bake
 * every sheet — see {@code roadmap/surface-relief/stories/s1-derivation-pipeline.md})
 * or a decode failure just leaves {@link #averageHeight} returning the
 * "unavailable" sentinel forever, so {@link GroundMicroHeightSampler} falls
 * back to macro-only for that sheet without retrying every frame.
 */
final class HeightSheetTexture {

    private static final Logger LOG = Global.getLogger(HeightSheetTexture.class);

    private final String path;
    private boolean loadAttempted;
    private BufferedImage img;

    HeightSheetTexture(String path) {
        this.path = path;
    }

    /** In-memory seam for unit tests; production always loads by mod-relative path. */
    HeightSheetTexture(BufferedImage img) {
        this.path = "<memory>";
        this.img = img;
        this.loadAttempted = true;
    }

    /**
     * Average red-channel value (height sheets are grayscale, R=G=B) over the
     * given source rect, normalized to {@code [0,1]}; {@code -1} if the sheet
     * isn't available or the rect is degenerate. The rect is clamped to the
     * image bounds — callers don't need to pre-validate against sheet size.
     */
    float averageHeight(int srcX, int srcY, int srcW, int srcH) {
        ensureLoaded();
        if (img == null) return -1f;

        int w = img.getWidth();
        int h = img.getHeight();
        int x0 = clamp(srcX, 0, w - 1);
        int y0 = clamp(srcY, 0, h - 1);
        int x1 = clamp(srcX + Math.max(1, srcW), x0 + 1, w);
        int y1 = clamp(srcY + Math.max(1, srcH), y0 + 1, h);
        int rw = x1 - x0;
        int rh = y1 - y0;
        if (rw <= 0 || rh <= 0) return -1f;

        // Bulk read (one native call) rather than per-pixel getRGB(x,y) —
        // GroundMicroHeightSampler caches the result per cell, but the FIRST
        // pass over the grid still touches every cell once, so this matters.
        int[] pixels = new int[rw * rh];
        img.getRGB(x0, y0, rw, rh, pixels, 0, rw);
        long sum = 0;
        for (int p : pixels) sum += (p >> 16) & 0xFF;
        return (sum / (float) pixels.length) / 255f;
    }

    private void ensureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;
        try (InputStream in = Global.getSettings().openStream(path)) {
            BufferedImage decoded = ImageIO.read(in);
            if (decoded == null) {
                LOG.warn("HeightSheetTexture: ImageIO.read returned null for " + path);
                return;
            }
            img = decoded;
        } catch (Exception e) {
            // Expected for sheets S1 didn't bake (e.g. the mixed wall/floor
            // sheet, deliberately skipped) -- not an error, just no micro data.
            LOG.info("HeightSheetTexture: no derived height sheet at " + path
                    + " -- micro height stays macro-only for it (" + e + ")");
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
