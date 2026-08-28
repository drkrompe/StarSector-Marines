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

/** Renders selected suites and exclusively owns their filesystem output. */
public final class SnapshotRunner {

    public List<Path> create(SnapshotContext context,
                             Collection<? extends SnapshotSuite> suites,
                             Path outputRoot, boolean replaceExisting) throws Exception {
        if (context == null || suites == null || outputRoot == null) {
            throw new IllegalArgumentException("snapshot context, suites, and output root required");
        }
        Path root = outputRoot.toAbsolutePath().normalize();
        List<PlannedArtifact> planned = new ArrayList<>();
        Set<Path> claimed = new LinkedHashSet<>();
        for (SnapshotSuite suite : suites) {
            if (suite == null) throw new IllegalArgumentException("snapshot suite may not be null");
            Path suiteRoot = root.resolve(suite.id()).normalize();
            if (!suiteRoot.startsWith(root)) {
                throw new IOException("snapshot suite escapes output root: " + suite.id());
            }
            List<SnapshotArtifact> artifacts = suite.render(context);
            if (artifacts == null) {
                throw new IllegalStateException("snapshot suite '" + suite.id()
                        + "' returned no artifact list");
            }
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
        }
        List<Path> outputs = new ArrayList<>(planned.size());
        for (PlannedArtifact item : planned) {
            Path output = item.output();
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            write(item.artifact(), output);
            outputs.add(output);
        }
        return List.copyOf(outputs);
    }

    private static void write(SnapshotArtifact artifact, Path output) throws IOException {
        if (!artifact.animated()) {
            if (!ImageIO.write(artifact.image(), "PNG", output.toFile())) {
                throw new IOException("no PNG writer available for " + output);
            }
            return;
        }
        try (AnimatedGifWriter gif =
                     new AnimatedGifWriter(output, artifact.frameDelayMillis())) {
            for (BufferedImage frame : artifact.frames()) gif.append(frame);
        }
    }

    private record PlannedArtifact(Path output, SnapshotArtifact artifact) {
    }
}
