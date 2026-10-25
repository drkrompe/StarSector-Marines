package com.dillon.starsectormarines.tools.snapshot;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * One file produced by a snapshot suite, relative to that suite's output
 * directory. A {@code .png} artifact carries exactly one frame; a {@code .gif}
 * artifact carries a sequence played at {@link #frameDelayMillis}; an {@code .mp4}
 * artifact is a video the suite has already encoded ({@link #encoded}, see
 * {@link FfmpegVideo}), which the runner moves into place, its frames empty unless
 * a lossless copy was asked for ({@link RawFrameDump}).
 *
 * <p>Animation belongs in the artifact rather than in the suites because
 * {@link SnapshotRunner} exclusively owns filesystem output. A suite that
 * wanted to record a played battle would otherwise have to write its own file
 * behind the runner's back, which is the one thing the runner's charter
 * forbids.
 */
public record SnapshotArtifact(Path relativePath, List<BufferedImage> frames,
                               int frameDelayMillis, AnimatedGifWriter.Palette palette,
                               Path encoded) {

    /** Default GIF pacing — eight frames a second, slow enough to read a battlefield. */
    public static final int DEFAULT_FRAME_DELAY_MILLIS = 125;

    public SnapshotArtifact(Path relativePath, List<BufferedImage> frames, int frameDelayMillis) {
        this(relativePath, frames, frameDelayMillis, AnimatedGifWriter.Palette.COMPACT);
    }

    public SnapshotArtifact(Path relativePath, List<BufferedImage> frames,
                            int frameDelayMillis, AnimatedGifWriter.Palette palette) {
        this(relativePath, frames, frameDelayMillis, palette, null);
    }

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

    /** Colour-sensitive UI/art review; retains shading at the cost of a larger GIF. */
    public static SnapshotArtifact colorAnimation(String relativePath,
                                                  List<BufferedImage> frames,
                                                  int frameDelayMillis) {
        return new SnapshotArtifact(Path.of(relativePath), frames, frameDelayMillis,
                AnimatedGifWriter.Palette.ADAPTIVE);
    }

    /**
     * A video already encoded to {@code encoded}, written as {@code relativePath} (which must end in {@code .mp4});
     * {@code rawFrames} are its frames for {@link RawFrameDump}, empty when no lossless copy was asked for.
     */
    public static SnapshotArtifact video(String relativePath, Path encoded, List<BufferedImage> rawFrames) {
        if (encoded == null) throw new IllegalArgumentException("a video artifact needs its encoded file");
        return new SnapshotArtifact(Path.of(relativePath), rawFrames, DEFAULT_FRAME_DELAY_MILLIS,
                AnimatedGifWriter.Palette.COMPACT, encoded);
    }

    public SnapshotArtifact {
        if (relativePath == null || frames == null || palette == null) {
            throw new IllegalArgumentException("snapshot path, frames and palette are required");
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
        boolean video = name.endsWith(".mp4");
        if (!animated && !video && !name.endsWith(".png")) {
            throw new IllegalArgumentException("snapshot path must be a .png, .gif or .mp4: "
                    + relativePath);
        }
        if (video != (encoded != null)) {
            throw new IllegalArgumentException("an .mp4 artifact, and only one, carries an encoded file: "
                    + relativePath);
        }
        // List.copyOf rejects null elements itself; asking the resulting
        // immutable list whether it contains null would throw instead of
        // answering.
        // A compressed list is kept as it is: copying it would inflate every frame at once.
        if (!(frames instanceof CompressedFrameList)) frames = List.copyOf(frames);
        if (frames.isEmpty() && !video) {
            throw new IllegalArgumentException("snapshot artifact has no frames: " + relativePath);
        }
        if (!animated && !video && frames.size() != 1) {
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

    /** True when this artifact is an encoded video the runner moves into place. */
    public boolean video() {
        return encoded != null;
    }

    /** The single frame of a still artifact, or the first frame of an animation. */
    public BufferedImage image() {
        return frames.get(0);
    }
}
