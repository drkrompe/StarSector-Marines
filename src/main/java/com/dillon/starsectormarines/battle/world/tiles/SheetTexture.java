package com.dillon.starsectormarines.battle.world.tiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Lazy-loaded handle to one tile-sheet PNG: the loaded {@link SpriteAPI}, the
 * raw content pixel dimensions, and — for sliced sheets — the
 * {@link SpriteSheetFrames} bounding boxes produced by {@link SpriteSheetSlicer}.
 *
 * <p>This is the single, path-parameterized replacement for the former
 * one-loader-class-per-sheet pattern (the deleted {@code NatureTileset} /
 * {@code UrbanTile3Tileset}) and the six copy-pasted {@code ensure*Sheet}
 * blocks that {@code BattleSprites} used to carry — every tile sheet now loads
 * through this one method.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>{@link #grid Grid}</b> — fixed-cell sheets whose source rect is
 *       computed from {@code (col, row, cellPx)} by the caller; we only need
 *       the sprite and its content dimensions. {@link #frames()} stays null.</li>
 *   <li><b>{@link #sliced Sliced}</b> — variable-width strips cut by
 *       {@link SpriteSheetSlicer}. The frame count is cross-checked against the
 *       installed {@link TileRegistry}; a mismatch means the source art and the
 *       registry have drifted, so the handle nulls itself out and the caller
 *       falls back (this is the legacy-autotile fallback the sliced sheets
 *       always had).</li>
 * </ul>
 *
 * <p>We capture content dimensions from the decoded image rather than
 * {@link SpriteAPI#getTextureWidth()}, which reports the POT-padded texture
 * size; per-tile UV math needs the content width. Loading is idempotent and
 * one-shot — a failure leaves {@link #sprite} null and {@link #isLoaded} false
 * so callers degrade without re-decoding every frame ({@link
 * com.dillon.starsectormarines.battle.world.tiles}-local note: mind the
 * {@code loadTexture}-before-dimension-query gotcha).
 */
public final class SheetTexture {

    private static final Logger LOG = Global.getLogger(SheetTexture.class);

    private final String path;
    private final boolean sliced;

    private SpriteAPI sprite;
    private SpriteSheetFrames frames;
    private int pxW;
    private int pxH;
    private boolean loadAttempted;

    private SheetTexture(String path, boolean sliced) {
        this.path = path;
        this.sliced = sliced;
    }

    /** A fixed-grid sheet — sprite + content dimensions only, no slicing. */
    public static SheetTexture grid(String path) {
        return new SheetTexture(path, false);
    }

    /** A variable-width sliced strip — also computes frames and registry-checks the count. */
    public static SheetTexture sliced(String path) {
        return new SheetTexture(path, true);
    }

    public String path()              { return path; }
    public boolean isLoaded()         { return sprite != null; }
    public SpriteAPI sprite()         { return sprite; }
    /** Sliced-sheet frame bounding boxes; null for grid sheets and on load failure. */
    public SpriteSheetFrames frames() { return frames; }
    public int pxW()                  { return pxW; }
    public int pxH()                  { return pxH; }

    /**
     * Idempotent, one-shot load. On failure — or, for a sliced sheet, on a
     * slicer-count vs. {@link TileRegistry} mismatch — {@link #sprite} (and
     * {@link #frames}) are left null so the caller can fall back without
     * retrying every frame.
     */
    public void ensureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;
        try {
            Global.getSettings().loadTexture(path);
            sprite = Global.getSettings().getSprite(path);
            if (sprite == null) {
                LOG.warn("SheetTexture: getSprite returned null for " + path);
                return;
            }
            try (InputStream stream = Global.getSettings().openStream(path)) {
                BufferedImage img = ImageIO.read(stream);
                if (img == null) {
                    LOG.warn("SheetTexture: ImageIO.read returned null for " + path);
                    sprite = null;
                    return;
                }
                pxW = img.getWidth();
                pxH = img.getHeight();
                if (!sliced) {
                    LOG.info("SheetTexture: loaded " + path + " (" + pxW + "x" + pxH + ")");
                    return;
                }
                frames = SpriteSheetSlicer.slice(img);
                TileRegistry reg = TileRegistry.installed();
                int expected = (reg == null) ? frames.frames.length
                        : (int) reg.all().stream()
                                .filter(d -> path.equals(d.sheetPath))
                                .count();
                if (reg != null && frames.frames.length != expected) {
                    LOG.warn("SheetTexture: " + path + " slicer returned " + frames.frames.length
                            + " frames but TileRegistry expects " + expected
                            + " — art and registry out of sync; nulling handle so the caller falls back");
                    sprite = null;
                    frames = null;
                    return;
                }
                LOG.info("SheetTexture: loaded " + path + " (" + pxW + "x" + pxH + "), "
                        + frames.frames.length + " frames sliced");
            }
        } catch (Exception e) {
            LOG.error("SheetTexture: failed to load " + path, e);
            sprite = null;
            frames = null;
        }
    }
}
