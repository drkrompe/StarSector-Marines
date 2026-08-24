package com.dillon.starsectormarines.battle.turret.preview;

import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared retained-renderer adapter for deterministic turret catalog strips. */
public final class HeadlessTurretCatalogPreviewRenderer {

    private final Path modRoot;
    private final Path coreRoot;
    private final HeadlessUiRenderer renderer;
    private final Map<String, TurretCatalogPreviewDocument.Sprite> sprites =
            new LinkedHashMap<>();

    public HeadlessTurretCatalogPreviewRenderer(Path modRoot, Path coreRoot) {
        if (modRoot == null || coreRoot == null) {
            throw new IllegalArgumentException("mod and Starsector core roots are required");
        }
        this.modRoot = modRoot.toAbsolutePath().normalize();
        this.coreRoot = coreRoot.toAbsolutePath().normalize();
        renderer = new HeadlessUiRenderer(this.modRoot, this.coreRoot);
    }

    public RenderedPreview render(TurretMountDef mount) {
        TurretCatalogPreviewDocument.Preview preview =
                TurretCatalogPreviewDocument.create(mount, this::sprite);
        BufferedImage image = renderer.render(preview.document(),
                TurretCatalogPreviewDocument.STRIP_WIDTH,
                TurretCatalogPreviewDocument.STRIP_HEIGHT);
        return new RenderedPreview(image, preview.slotContributions());
    }

    /** Mod assets override vanilla assets, matching Starsector's resolution order. */
    public Path resolveAsset(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IOException("asset path may not be blank");
        }
        Path modAsset = safeResolve(modRoot, relativePath);
        if (Files.isRegularFile(modAsset)) return modAsset;
        Path coreAsset = safeResolve(coreRoot, relativePath);
        if (Files.isRegularFile(coreAsset)) return coreAsset;
        throw new IOException("Preview asset not found in mod or Starsector core: "
                + relativePath);
    }

    private TurretCatalogPreviewDocument.Sprite sprite(String sourcePath) {
        return sprites.computeIfAbsent(sourcePath, path -> {
            try {
                Path asset = resolveAsset(path);
                BufferedImage image = ImageIO.read(asset.toFile());
                if (image == null) throw new IOException("Unsupported preview image: " + asset);
                return new TurretCatalogPreviewDocument.Sprite(
                        path, null, image.getWidth(), image.getHeight());
            } catch (IOException failure) {
                throw new IllegalStateException("Could not load preview asset " + path, failure);
            }
        });
    }

    private static Path safeResolve(Path root, String relativePath) throws IOException {
        Path resolved = root.resolve(relativePath.replace('/', File.separatorChar)).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Preview asset escapes its root: " + relativePath);
        }
        return resolved;
    }

    public record RenderedPreview(
            BufferedImage image,
            Map<FxSlot, Integer> slotContributions) {

        public RenderedPreview {
            if (image == null || slotContributions == null) {
                throw new IllegalArgumentException("preview image and contributions are required");
            }
            slotContributions = Map.copyOf(slotContributions);
        }
    }
}
