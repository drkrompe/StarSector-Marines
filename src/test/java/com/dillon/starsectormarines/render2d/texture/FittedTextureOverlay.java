package com.dillon.starsectormarines.render2d.texture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless frames drawn from the fitted build instead of the source art: the A/B half of the
 * texture-fit proof.
 *
 * <p>With {@code -Dmarines.textureFit.overlay=<dir>} (the {@code optimizePngs} output, holding
 * {@code graphics/} and {@code data/config/marines_texture_fit.json}), every image the manifest
 * lists is read from there and resampled back to its logical size, which is what the game's
 * {@code setSize} plus bilinear sampling of the smaller texture amounts to. Every cut, frame and
 * layout then addresses it unchanged, so a raw-frame comparison against an ordinary run measures
 * the fit and nothing else. Unset, or for an image the manifest does not list, it is the
 * ordinary read.
 */
public final class FittedTextureOverlay {

    private static final Path ROOT = root();
    private static final TextureFitManifest MANIFEST = manifest(ROOT);

    private FittedTextureOverlay() { }

    private static Path root() {
        String dir = System.getProperty("marines.textureFit.overlay");
        return dir == null || dir.isBlank() ? null : Path.of(dir).toAbsolutePath().normalize();
    }

    private static TextureFitManifest manifest(Path root) {
        if (root == null) return TextureFitManifest.EMPTY;
        try {
            return TextureFitManifest.parse(Files.readString(root.resolve(ModTextures.MANIFEST_PATH)));
        } catch (Exception e) {
            throw new IllegalStateException("marines.textureFit.overlay has no readable manifest under " + root, e);
        }
    }

    /** Whether an overlay is active for this process. */
    public static boolean active() {
        return ROOT != null;
    }

    /** {@code resourcePath}'s pixels at its logical size; {@code fallback} when it was not fitted. */
    public static BufferedImage read(String resourcePath, Path fallback) throws IOException {
        TextureFitManifest.Entry entry = MANIFEST.entry(TextureUsage.normalize(resourcePath));
        if (entry == null) return ImageIO.read(fallback.toFile());
        BufferedImage fitted = ImageIO.read(ROOT.resolve(entry.file()).toFile());
        if (fitted == null) throw new IOException("Unsupported fitted image " + entry.file());
        return ModTextures.toLogicalSize(fitted, entry.width(), entry.height());
    }
}
