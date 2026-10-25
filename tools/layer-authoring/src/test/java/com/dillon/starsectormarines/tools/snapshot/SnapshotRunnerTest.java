package com.dillon.starsectormarines.tools.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotRunnerTest {

    @Test
    void writesSuiteScopedPngsAndHonorsReplacementPolicy(@TempDir Path temporary)
            throws Exception {
        SnapshotSuite suite = suite("sample", List.of(
                new SnapshotArtifact("first.png", image(Color.RED)),
                new SnapshotArtifact("nested/second.png", image(Color.BLUE))));
        SnapshotContext context = new SnapshotContext(temporary, temporary);
        Path output = temporary.resolve("snapshots");
        SnapshotRunner runner = new SnapshotRunner();

        List<Path> paths = runner.create(context, List.of(suite), output, false);
        assertEquals(List.of(output.resolve("sample/first.png").toAbsolutePath(),
                output.resolve("sample/nested/second.png").toAbsolutePath()), paths);
        assertTrue(paths.stream().allMatch(Files::isRegularFile));
        assertThrows(FileAlreadyExistsException.class,
                () -> runner.create(context, List.of(suite), output, false));
        assertEquals(2, runner.create(context, List.of(suite), output, true).size());
    }

    @Test
    void movesAnEncodedVideoIntoPlace(@TempDir Path temporary) throws Exception {
        Path encoded = Files.write(temporary.resolve("encoded.tmp"), new byte[] {1, 2, 3});
        SnapshotSuite suite = suite("video", List.of(SnapshotArtifact.video("look/shot.mp4", encoded, List.of())));

        new SnapshotRunner().create(new SnapshotContext(temporary, temporary), List.of(suite),
                temporary.resolve("snapshots"), true);
        assertEquals(3, Files.size(temporary.resolve("snapshots/video/look/shot.mp4")));
        assertFalse(Files.exists(encoded));
        assertThrows(IllegalArgumentException.class,
                () -> SnapshotArtifact.video("look/shot.gif", encoded, List.of()));
    }

    @Test
    void validatesEveryArtifactBeforeWritingAnyOutput(@TempDir Path temporary) {
        BufferedImage image = image(Color.WHITE);
        SnapshotSuite duplicate = suite("duplicate", List.of(
                new SnapshotArtifact("same.png", image),
                new SnapshotArtifact("same.png", image)));
        Path output = temporary.resolve("snapshots");

        assertThrows(Exception.class, () -> new SnapshotRunner().create(
                new SnapshotContext(temporary, temporary), List.of(duplicate), output, true));
        assertFalse(Files.exists(output.resolve("duplicate/same.png")));
        assertThrows(IllegalArgumentException.class,
                () -> new SnapshotArtifact("../escape.png", image));
    }

    @Test
    void writesEachSuiteBeforeRenderingTheNextAndAttemptsAllOfThem(@TempDir Path temporary) throws Exception {
        Path output = temporary.resolve("snapshots");
        SnapshotSuite first = suite("first", List.of(new SnapshotArtifact("a.png", image(Color.RED))));
        SnapshotSuite second = new SnapshotSuite() {
            @Override public String id() { return "second"; }
            @Override public String label() { return "second"; }
            @Override public List<SnapshotArtifact> render(SnapshotContext context) {
                // Memory stays bounded by one suite only if the previous one is already on disk.
                assertTrue(Files.isRegularFile(output.resolve("first/a.png")));
                return List.of(new SnapshotArtifact("b.png", image(Color.BLUE)));
            }
        };
        SnapshotSuite failing = new SnapshotSuite() {
            @Override public String id() { return "failing"; }
            @Override public String label() { return "failing"; }
            @Override public List<SnapshotArtifact> render(SnapshotContext context) {
                throw new IllegalStateException("boom");
            }
        };

        assertThrows(IllegalStateException.class, () -> new SnapshotRunner().create(
                new SnapshotContext(temporary, temporary), List.of(first, failing, second), output, true));
        assertTrue(Files.isRegularFile(output.resolve("first/a.png")));
        assertTrue(Files.isRegularFile(output.resolve("second/b.png")));
    }

    private static SnapshotSuite suite(String id, List<SnapshotArtifact> artifacts) {
        return new SnapshotSuite() {
            @Override public String id() { return id; }
            @Override public String label() { return id; }
            @Override public List<SnapshotArtifact> render(SnapshotContext context) {
                return artifacts;
            }
        };
    }

    private static BufferedImage image(Color color) {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, color.getRGB());
        }
        return image;
    }
}
