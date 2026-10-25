package com.dillon.starsectormarines.tools.snapshot;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

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
            long renderStart = System.nanoTime();
            List<SnapshotArtifact> artifacts = suite.render(context);
            long renderNanos = System.nanoTime() - renderStart;
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
            long writeStart = System.nanoTime();
            Path rawRoot = rawFrameRoot();
            // Each artifact is its own file and its own encoder, so they are written side by side; the order of
            // `outputs` stays the planned order whatever finishes first.
            List<Callable<Void>> writes = new ArrayList<>(planned.size());
            for (PlannedArtifact item : planned) {
                writes.add(() -> {
                    Path output = item.output();
                    if (output.getParent() != null) Files.createDirectories(output.getParent());
                    write(item.artifact(), output);
                    if (rawRoot != null && !item.artifact().frames().isEmpty()) {
                        RawFrameDump.write(rawRoot.resolve(suite.id()).resolve(item.artifact().relativePath()),
                                item.artifact().frames());
                    }
                    return null;
                });
            }
            writeAll(writes);
            for (PlannedArtifact item : planned) outputs.add(item.output());
            System.out.printf(Locale.ROOT, "  [%s] rendered in %.1fs, wrote %d artifact(s) in %.1fs%n",
                    suite.id(), renderNanos / 1e9, planned.size(), (System.nanoTime() - writeStart) / 1e9);
        }
    }

    /**
     * Opt-in lossless copy of every artifact's frames ({@code -Dsnapshot.rawFrames=<dir>}), so two runs can be
     * compared pixel for pixel rather than through the GIF palette. Null when not asked for.
     */
    private static Path rawFrameRoot() {
        String raw = System.getProperty(RawFrameDump.PROPERTY);
        return raw == null || raw.isBlank() ? null : Path.of(raw).toAbsolutePath().normalize();
    }

    private static void writeAll(List<Callable<Void>> writes) throws IOException {
        if (SnapshotWorkers.threads() <= 1) {
            for (Callable<Void> write : writes) {
                try {
                    write.call();
                } catch (IOException | RuntimeException failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }
            return;
        }
        try {
            SnapshotWorkers.runAll(writes);
        } catch (IllegalStateException wrapped) {
            if (wrapped.getCause() instanceof IOException io) throw io;
            throw wrapped;
        }
    }

    private static void write(SnapshotArtifact artifact, Path output) throws IOException {
        if (artifact.video()) {
            Files.move(artifact.encoded(), output, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (!artifact.animated()) {
            PngStillWriter.write(artifact.image(), output);
            return;
        }
        AnimatedGifWriter.write(output, artifact.frames(), artifact.frameDelayMillis(), artifact.palette());
    }

    private record PlannedArtifact(Path output, SnapshotArtifact artifact) {
    }
}
