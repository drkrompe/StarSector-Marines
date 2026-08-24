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
