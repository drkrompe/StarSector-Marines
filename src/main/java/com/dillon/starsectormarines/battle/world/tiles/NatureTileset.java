package com.dillon.starsectormarines.battle.world.tiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Lazy-loaded handle to {@code graphics/tilesets/nature-tiles.png}. Runs
 * {@link SpriteSheetSlicer} over the PNG on first {@link #ensureLoaded} and
 * caches the resulting frame bounding boxes alongside the SpriteAPI handle so
 * subsequent draws can reuse both.
 *
 * <p>Unlike the {@code Floors_Tiles} / {@code Water_tiles} sheets — both
 * fixed-grid — this sheet has variable-width cells separated by alpha gutters,
 * so we can't compute frame bounds from a constant cell size. The slicer
 * handles the variable widths the same way it does for AI-generated unit
 * sprites.
 *
 * <p>Frame index ↔ semantic tile is the registry's ingest order for this sheet.
 * The slicer is expected to return exactly the number of tiles the
 * {@link TileRegistry} has for {@link #SHEET_PATH}; a mismatch means the source
 * art and the registry are out of sync, which the in-game tileset debug screen
 * surfaces visually.
 */
public final class NatureTileset {

    private static final Logger LOG = Global.getLogger(NatureTileset.class);

    public static final String SHEET_PATH = "graphics/tilesets/nature-tiles.png";

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
                LOG.warn("NatureTileset: getSprite returned null for " + SHEET_PATH);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(SHEET_PATH)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("NatureTileset: ImageIO.read returned null for " + SHEET_PATH);
                    sheet = null;
                    return;
                }
                sheetPxW = img.getWidth();
                sheetPxH = img.getHeight();
                frames = SpriteSheetSlicer.slice(img);
                TileRegistry r = TileRegistry.installed();
                int expected = (r == null) ? frames.frames.length
                        : (int) r.all().stream()
                                .filter(d -> SHEET_PATH.equals(d.sheetPath))
                                .count();
                if (r != null && frames.frames.length != expected) {
                    LOG.warn("NatureTileset: slicer returned " + frames.frames.length
                            + " frames but TileRegistry expects " + expected
                            + " tiles for this sheet — art and registry are out of sync");
                } else {
                    LOG.info("NatureTileset: loaded " + SHEET_PATH
                            + " (" + sheetPxW + "x" + sheetPxH + "), sliced into "
                            + frames.frames.length + " frames");
                }
            }
        } catch (Exception e) {
            LOG.error("NatureTileset: failed to load " + SHEET_PATH, e);
            sheet = null;
            frames = null;
        }
    }
}
