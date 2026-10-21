package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.Global;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The creep's painted surface: a seamless greyscale value study of the growth
 * (`art-source/hive-creep/`), repeating every {@link #CELLS} map cells. Colour
 * is not in it; {@link CreepShading} maps its luminance through the palette.
 *
 * <p>Read through the game's settings when there is a game and from
 * {@code mod/} otherwise, so the GL pass and the review frames sample one
 * image. The CPU side keeps a mip chain and samples the level a pixel
 * covers, which is what the GL pass's mipmapping does at the whole-map
 * framing. If the image cannot be read, the material is flat mid-grey and the
 * creep still draws.
 */
final class CreepMaterial {

    static final String PATH = "graphics/creep/creep_material.png";
    /** Map cells one repeat of the material covers. */
    static final float CELLS = 3.2f;

    private static int size;
    private static byte[][] mips;

    private CreepMaterial() { }

    /** The second, coarser and quarter-turned sampling that breaks up the repeat: its scale against the first. */
    static final float SECOND_SCALE = 2.3f;
    /** Its offset, in repeats, so the two never line up at the origin. */
    static final float SECOND_OFFSET = 0.37f;
    /** Share of the second sampling in the surface. */
    static final float SECOND_SHARE = 0.35f;
    /** Contrast restored after the two are mixed. */
    static final float CONTRAST = 1.35f;

    /**
     * The surface luminance the creep is shaded from: the material at its own scale mixed with a coarser,
     * quarter-turned copy of itself, so the repeat does not read as a grid, then re-stretched to the contrast one
     * sampling had.
     */
    static float surface(float cellX, float cellY, float texelsPerPixel) {
        float a = sample(cellX, cellY, texelsPerPixel);
        float b = sample(cellY / SECOND_SCALE + SECOND_OFFSET * CELLS, cellX / SECOND_SCALE + SECOND_OFFSET * CELLS,
                texelsPerPixel / SECOND_SCALE);
        float l = a + (b - a) * SECOND_SHARE;
        return Math.max(0f, Math.min(1f, (l - 0.5f) * CONTRAST + 0.5f));
    }

    /** Side of level 0 in texels. */
    static synchronized int size() {
        load();
        return size;
    }

    /** Level-0 luminance bytes, row-major. */
    static synchronized byte[] luminance() {
        load();
        return mips[0];
    }

    /**
     * Luminance at a map-cell position, bilinear and repeating, from the mip level
     * whose texels best match {@code texelsPerPixel}.
     */
    static float sample(float cellX, float cellY, float texelsPerPixel) {
        byte[][] chain;
        int base;
        synchronized (CreepMaterial.class) {
            load();
            chain = mips;
            base = size;
        }
        int level = 0;
        float t = texelsPerPixel;
        while (t > 1.5f && level < chain.length - 1) {
            t *= 0.5f;
            level++;
        }
        int s = Math.max(1, base >> level);
        byte[] m = chain[level];
        float u = cellX / CELLS * s - 0.5f, v = cellY / CELLS * s - 0.5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        int ax = Math.floorMod(x0, s), bx = Math.floorMod(x0 + 1, s);
        int ay = Math.floorMod(y0, s), by = Math.floorMod(y0 + 1, s);
        float a = m[ay * s + ax] & 0xFF, b = m[ay * s + bx] & 0xFF;
        float c = m[by * s + ax] & 0xFF, d = m[by * s + bx] & 0xFF;
        return ((a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy) / 255f;
    }

    private static void load() {
        if (mips != null) return;
        BufferedImage image = null;
        try {
            image = read();
        } catch (IOException | RuntimeException ignored) {
            // Flat fallback below.
        }
        if (image == null) {
            size = 1;
            mips = new byte[][]{{(byte) 128}};
            return;
        }
        size = image.getWidth();
        byte[] level0 = new byte[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) level0[y * size + x] = (byte) (image.getRGB(x, y) & 0xFF);
        }
        int levels = 1;
        for (int s = size; s > 1; s >>= 1) levels++;
        mips = new byte[levels][];
        mips[0] = level0;
        for (int l = 1, s = size >> 1; l < levels; l++, s >>= 1) {
            byte[] prev = mips[l - 1], next = new byte[s * s];
            int ps = s * 2;
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    int sum = (prev[(2 * y) * ps + 2 * x] & 0xFF) + (prev[(2 * y) * ps + 2 * x + 1] & 0xFF)
                            + (prev[(2 * y + 1) * ps + 2 * x] & 0xFF) + (prev[(2 * y + 1) * ps + 2 * x + 1] & 0xFF);
                    next[y * s + x] = (byte) ((sum + 2) / 4);
                }
            }
            mips[l] = next;
        }
    }

    private static BufferedImage read() throws IOException {
        // A headless host may install stand-in settings that cannot open files; fall through to mod/ then.
        try {
            if (Global.getSettings() != null) {
                try (InputStream stream = Global.getSettings().openStream(PATH)) {
                    BufferedImage image = stream == null ? null : ImageIO.read(stream);
                    if (image != null) return image;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Try the working tree.
        }
        Path file = Path.of("mod").resolve(PATH);
        if (!Files.isRegularFile(file)) return null;
        return ImageIO.read(file.toFile());
    }
}
