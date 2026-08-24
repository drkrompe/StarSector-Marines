package com.dillon.starsectormarines.tools.snapshot;

import java.nio.file.Path;

/** Stable repository and game-resource roots supplied to every snapshot suite. */
public record SnapshotContext(Path projectRoot, Path starsectorCore) {

    public SnapshotContext {
        if (projectRoot == null || starsectorCore == null) {
            throw new IllegalArgumentException("project and Starsector core roots are required");
        }
        projectRoot = projectRoot.toAbsolutePath().normalize();
        starsectorCore = starsectorCore.toAbsolutePath().normalize();
    }

    public Path modRoot() {
        return projectRoot.resolve("mod");
    }
}
