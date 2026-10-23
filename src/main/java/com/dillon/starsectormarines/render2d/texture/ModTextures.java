package com.dillon.starsectormarines.render2d.texture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The one way the mod loads its own textures, so a fitted image reads exactly like its source.
 *
 * <p>The release and deploy builds ship images smaller than their source when they are never
 * drawn large enough to need every pixel ({@code texture-fit.md}). Everything that addresses an
 * image does it in source pixels: tileset cuts, layer layouts, frame grids, the
 * {@code u = srcPx / sheet.getWidth() * sheet.getTextureWidth()} arithmetic of every batch. So a
 * fitted sprite is handed out already sized to its logical (source) dimensions with
 * {@link SpriteAPI#setSize}; {@code getTextureWidth} still reports the shipped texture's own
 * fraction, and the ratio comes out the same. An image decoded for its pixels is scaled back up
 * to its logical size, so a slicer or a measurement reads source coordinates too.
 *
 * <p>An image the build re-encoded (an opaque PNG shipped as JPG) is loaded from the shipped
 * file while keeping its source name everywhere else.
 */
public final class ModTextures {

    public static final String MANIFEST_PATH = "data/config/marines_texture_fit.json";

    private static final Logger LOG = Global.getLogger(ModTextures.class);
    private static volatile TextureFitManifest manifest;
    /** Every fitted sprite handed out, so code holding only the sprite can ask its texel size. */
    private static final Map<SpriteAPI, TextureFitManifest.Entry> FITTED =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private ModTextures() { }

    /** The build's record of fitted images, read once; empty in a checkout. */
    public static TextureFitManifest manifest() {
        TextureFitManifest loaded = manifest;
        if (loaded != null) return loaded;
        synchronized (ModTextures.class) {
            if (manifest == null) manifest = readManifest();
            return manifest;
        }
    }

    private static TextureFitManifest readManifest() {
        String text;
        try {
            text = Global.getSettings().loadText(MANIFEST_PATH);
        } catch (Exception absent) {
            return TextureFitManifest.EMPTY;
        }
        if (text == null) return TextureFitManifest.EMPTY;
        try {
            TextureFitManifest parsed = TextureFitManifest.parse(text);
            LOG.info("ModTextures: " + parsed.size() + " fitted textures");
            return parsed;
        } catch (Exception broken) {
            LOG.error("ModTextures: unreadable " + MANIFEST_PATH + "; using every image as shipped", broken);
            return TextureFitManifest.EMPTY;
        }
    }

    /** The file that ships for {@code path}. */
    public static String file(String path) {
        return manifest().file(path);
    }

    /**
     * How many texels {@code path} really has across, given its logical width: less than the
     * logical width when the build fitted it. A copy of the image (an atlas slot) needs no more.
     */
    public static int shippedWidth(String path, int logicalWidth) {
        TextureFitManifest.Entry entry = manifest().entry(path);
        return entry == null ? logicalWidth : entry.shippedWidth();
    }

    /** {@link #shippedWidth}, down. */
    public static int shippedHeight(String path, int logicalHeight) {
        TextureFitManifest.Entry entry = manifest().entry(path);
        return entry == null ? logicalHeight : entry.shippedHeight();
    }

    /** {@link #shippedWidth} of a sprite this class handed out; {@code logicalWidth} otherwise. */
    public static int shippedWidth(SpriteAPI sprite, int logicalWidth) {
        TextureFitManifest.Entry entry = sprite == null ? null : FITTED.get(sprite);
        return entry == null ? logicalWidth : entry.shippedWidth();
    }

    /** {@link #shippedWidth(SpriteAPI, int)}, down. */
    public static int shippedHeight(SpriteAPI sprite, int logicalHeight) {
        TextureFitManifest.Entry entry = sprite == null ? null : FITTED.get(sprite);
        return entry == null ? logicalHeight : entry.shippedHeight();
    }

    /** {@code SettingsAPI.loadTexture} of the file that ships for {@code path}. */
    public static void loadTexture(String path) throws IOException {
        Global.getSettings().loadTexture(file(path));
    }

    /**
     * {@code SettingsAPI.getSprite} of {@code path}, sized to its logical dimensions if the build
     * fitted it. Null when the game has no such sprite.
     */
    public static SpriteAPI sprite(String path) {
        SpriteAPI sprite = Global.getSettings().getSprite(file(path));
        if (sprite == null) return null;
        TextureFitManifest.Entry entry = manifest().entry(path);
        if (entry != null) {
            sprite.setSize(entry.width(), entry.height());
            FITTED.put(sprite, entry);
        }
        TextureUsage.name(sprite, path, sprite.getWidth(), sprite.getHeight());
        return sprite;
    }

    /** Decodes {@code path}'s pixels at its logical size; null when ImageIO cannot read it. */
    public static BufferedImage readImage(String path) throws IOException {
        BufferedImage image;
        try (InputStream stream = Global.getSettings().openStream(file(path))) {
            image = ImageIO.read(stream);
        }
        if (image == null) return null;
        TextureFitManifest.Entry entry = manifest().entry(path);
        return entry == null ? image : toLogicalSize(image, entry.width(), entry.height());
    }

    /** {@code image} resampled to {@code width x height}; itself when already that size. */
    public static BufferedImage toLogicalSize(BufferedImage image, int width, int height) {
        if (image.getWidth() == width && image.getHeight() == height) return image;
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
