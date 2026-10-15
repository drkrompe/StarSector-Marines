package com.dillon.starsectormarines.tools.snapshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders selected suites and exclusively owns their filesystem output.
 *
 * <p>Output is written suite by suite: a suite's artifacts are validated together and written before the
 * next suite is rendered, so memory is bounded by the largest single suite rather than by every frame of
 * every suite at once. A suite whose artifacts fail validation writes nothing; suites already written
 * before it stay written. A suite that throws does not stop the suites after it: every selected suite is
 * attempted, and the first failure is rethrown at the end with the others attached as suppressed, so one
 * stale suite cannot hide what the rest would have shown.
 */
public final class SnapshotRunner {

    public List<Path> create(SnapshotContext context,
                             Collection<? extends SnapshotSuite> suites,
                             Path outputRoot, boolean replaceExisting) throws Exception {
        if (context == null || suites == null || outputRoot == null) {
            throw new IllegalArgumentException("snapshot context, suites, and output root required");
        }
        Path root = outputRoot.toAbsolutePath().normalize();
        List<Path> outputs = new ArrayList<>();
        Set<Path> claimed = new LinkedHashSet<>();
        Exception failure = null;
        for (SnapshotSuite suite : suites) {
            if (suite == null) throw new IllegalArgumentException("snapshot suite may not be null");
            try {
                renderAndWrite(context, suite, root, replaceExisting, claimed, outputs);
            } catch (Exception | AssertionError thrown) {
                Exception wrapped = thrown instanceof Exception e ? e
                        : new IllegalStateException("snapshot suite '" + suite.id() + "' failed", thrown);
                System.err.println("snapshot suite '" + suite.id() + "' failed: " + wrapped);
                if (failure == null) failure = wrapped; else failure.addSuppressed(wrapped);
            }
        }
        if (failure != null) throw failure;
        return List.copyOf(outputs);
    }

    private static void renderAndWrite(SnapshotContext context, SnapshotSuite suite, Path root,
                                       boolean replaceExisting, Set<Path> claimed, List<Path> outputs)
            throws Exception {
        {
            Path suiteRoot = root.resolve(suite.id()).normalize();
            if (!suiteRoot.startsWith(root)) {
                throw new IOException("snapshot suite escapes output root: " + suite.id());
            }
            List<SnapshotArtifact> artifacts = suite.render(context);
            if (artifacts == null) {
                throw new IllegalStateException("snapshot suite '" + suite.id()
                        + "' returned no artifact list");
            }
            List<PlannedArtifact> planned = new ArrayList<>();
            for (SnapshotArtifact artifact : artifacts) {
                Path output = suiteRoot.resolve(artifact.relativePath()).normalize();
                if (!output.startsWith(suiteRoot)) {
                    throw new IOException("snapshot artifact escapes suite directory: "
                            + artifact.relativePath());
                }
                if (!claimed.add(output)) {
                    throw new IOException("duplicate snapshot output " + output);
                }
                if (!replaceExisting && Files.exists(output)) {
                    throw new FileAlreadyExistsException(output.toString());
                }
                planned.add(new PlannedArtifact(output, artifact));
            }
            for (PlannedArtifact item : planned) {
                Path output = item.output();
                if (output.getParent() != null) Files.createDirectories(output.getParent());
                write(item.artifact(), output);
                outputs.add(output);
            }
        }
    }

    private static void write(SnapshotArtifact artifact, Path output) throws IOException {
        if (!artifact.animated()) {
            if (!ImageIO.write(artifact.image(), "PNG", output.toFile())) {
                throw new IOException("no PNG writer available for " + output);
            }
            return;
        }
        try (AnimatedGifWriter gif =
                     new AnimatedGifWriter(output, artifact.frameDelayMillis(), artifact.palette())) {
            for (BufferedImage frame : artifact.frames()) gif.append(frame);
        }
    }

    private record PlannedArtifact(Path output, SnapshotArtifact artifact) {
    }
}
