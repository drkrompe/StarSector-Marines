package com.dillon.starsectormarines.battle.world.tiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Lazy-loaded handle to {@code graphics/tilesets/urban-tileset-3.png}.
 * Mirrors {@link NatureTileset} — the sheet is a single-row strip with
 * variable-width cells separated by alpha gutters, so we route through
 * {@link SpriteSheetSlicer} on first {@link #ensureLoaded} and cache the
 * resulting frame bounding boxes alongside the SpriteAPI handle for reuse.
 *
 * <p>Frame index ↔ semantic tile is the {@link UrbanTile3} enum's
 * declaration order. The slicer is expected to return exactly
 * {@link UrbanTile3#values() UrbanTile3.values().length} frames; a mismatch
 * means the source art and the enum have drifted, which the in-game
 * tileset debug screen surfaces visually.
 */
public final class UrbanTile3Tileset {

    private static final Logger LOG = Global.getLogger(UrbanTile3Tileset.class);

    public static final String SHEET_PATH = "graphics/tilesets/urban-tileset-3.png";

    private SpriteAPI sheet;
    private SpriteSheetFrames frames;
    private int sheetPxW;
    private int sheetPxH;
    private boolean loadAttempted;

    public boolean isLoaded() { return sheet != null && frames != null; }
    public SpriteAPI sheet() { return sheet; }
    public SpriteSheetFrames frames() { return frames; }
    public int sheetPxW() { return sheetPxW; }
    public int sheetPxH() { return sheetPxH; }

    /** Bounding box for {@code tile}, or {@code null} if the sheet failed to load or the slicer produced fewer frames than expected. */
    public SpriteSheetFrames.Frame frameOf(UrbanTile3 tile) {
        if (frames == null) return null;
        int idx = tile.frameIndex();
        if (idx < 0 || idx >= frames.frames.length) return null;
        return frames.frames[idx];
    }

    /**
     * Idempotent load — calling more than once is a no-op. Failure leaves
     * {@link #sheet} / {@link #frames} null and {@link #isLoaded} false so
     * callers can fall back without retrying every frame.
     */
    public void ensureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;
        try {
            Global.getSettings().loadTexture(SHEET_PATH);
            sheet = Global.getSettings().getSprite(SHEET_PATH);
            if (sheet == null) {
                LOG.warn("UrbanTile3Tileset: getSprite returned null for " + SHEET_PATH);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(SHEET_PATH)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("UrbanTile3Tileset: ImageIO.read returned null for " + SHEET_PATH);
                    sheet = null;
                    return;
                }
                sheetPxW = img.getWidth();
                sheetPxH = img.getHeight();
                frames = SpriteSheetSlicer.slice(img);
                int expected = UrbanTile3.values().length;
                if (frames.frames.length != expected) {
                    LOG.warn("UrbanTile3Tileset: slicer returned " + frames.frames.length
                            + " frames but UrbanTile3 expects " + expected
                            + " — sheet art and enum are out of sync");
                } else {
                    LOG.info("UrbanTile3Tileset: loaded " + SHEET_PATH
                            + " (" + sheetPxW + "x" + sheetPxH + "), sliced into "
                            + frames.frames.length + " frames");
                }
            }
        } catch (Exception e) {
            LOG.error("UrbanTile3Tileset: failed to load " + SHEET_PATH, e);
            sheet = null;
            frames = null;
        }
    }
}
