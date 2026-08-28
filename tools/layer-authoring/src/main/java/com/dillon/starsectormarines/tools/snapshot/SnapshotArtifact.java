package com.dillon.starsectormarines.tools.snapshot;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * One file produced by a snapshot suite, relative to that suite's output
 * directory. A {@code .png} artifact carries exactly one frame; a {@code .gif}
 * artifact carries a sequence played at {@link #frameDelayMillis}.
 *
 * <p>Animation belongs in the artifact rather than in the suites because
 * {@link SnapshotRunner} exclusively owns filesystem output. A suite that
 * wanted to record a played battle would otherwise have to write its own file
 * behind the runner's back, which is the one thing the runner's charter
 * forbids.
 */
public record SnapshotArtifact(Path relativePath, List<BufferedImage> frames,
                               int frameDelayMillis) {

    /** Default GIF pacing — eight frames a second, slow enough to read a battlefield. */
    public static final int DEFAULT_FRAME_DELAY_MILLIS = 125;

    public SnapshotArtifact(String relativePath, BufferedImage image) {
        this(Path.of(relativePath), image);
    }

    public SnapshotArtifact(Path relativePath, BufferedImage image) {
        this(relativePath, List.of(image), DEFAULT_FRAME_DELAY_MILLIS);
    }

    /** Animated artifact; the path must end in {@code .gif}. */
    public static SnapshotArtifact animation(String relativePath,
                                             List<BufferedImage> frames,
                                             int frameDelayMillis) {
        return new SnapshotArtifact(Path.of(relativePath), frames, frameDelayMillis);
    }

    public SnapshotArtifact {
        if (relativePath == null || frames == null) {
            throw new IllegalArgumentException("snapshot path and frames are required");
        }
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("snapshot path must be relative: " + relativePath);
        }
        relativePath = relativePath.normalize();
        if (relativePath.getNameCount() == 0 || relativePath.startsWith("..")) {
            throw new IllegalArgumentException("snapshot path must be a safe relative path: "
                    + relativePath);
        }
        String name = relativePath.toString().toLowerCase();
        boolean animated = name.endsWith(".gif");
        if (!animated && !name.endsWith(".png")) {
            throw new IllegalArgumentException("snapshot path must be a .png or .gif: "
                    + relativePath);
        }
        // List.copyOf rejects null elements itself; asking the resulting
        // immutable list whether it contains null would throw instead of
        // answering.
        frames = List.copyOf(frames);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("snapshot artifact has no frames: " + relativePath);
        }
        if (!animated && frames.size() != 1) {
            throw new IllegalArgumentException("a PNG artifact carries exactly one frame: "
                    + relativePath);
        }
        if (animated && frameDelayMillis < 10) {
            throw new IllegalArgumentException("GIF frame delay must be at least 10ms: "
                    + relativePath);
        }
    }

    /** True when this artifact is a multi-frame animation the runner writes as a GIF. */
    public boolean animated() {
        return relativePath.toString().toLowerCase().endsWith(".gif");
    }

    /** The single frame of a still artifact, or the first frame of an animation. */
    public BufferedImage image() {
        return frames.get(0);
    }
}
