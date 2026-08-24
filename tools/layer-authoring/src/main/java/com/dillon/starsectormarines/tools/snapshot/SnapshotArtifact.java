package com.dillon.starsectormarines.tools.snapshot;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** One PNG produced by a snapshot suite, relative to that suite's output directory. */
public record SnapshotArtifact(Path relativePath, BufferedImage image) {

    public SnapshotArtifact(String relativePath, BufferedImage image) {
        this(Path.of(relativePath), image);
    }

    public SnapshotArtifact {
        if (relativePath == null || image == null) {
            throw new IllegalArgumentException("snapshot path and image are required");
        }
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("snapshot path must be relative: " + relativePath);
        }
        relativePath = relativePath.normalize();
        if (relativePath.getNameCount() == 0 || relativePath.startsWith("..")
                || !relativePath.toString().toLowerCase().endsWith(".png")) {
            throw new IllegalArgumentException("snapshot path must be a safe relative PNG: "
                    + relativePath);
        }
    }
}
