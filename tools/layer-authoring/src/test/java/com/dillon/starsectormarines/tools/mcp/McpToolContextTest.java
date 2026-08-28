package com.dillon.starsectormarines.tools.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Paths reaching a tool come from a model rather than from a person who typed
 * them, so the checkout boundary is enforced here rather than trusted.
 */
class McpToolContextTest {

    private static McpToolContext context(Path root) {
        return new McpToolContext(root, root.resolve("core"));
    }

    @Test
    void aRelativePathResolvesAgainstTheProject(@TempDir Path root) throws IOException {
        assertEquals(root.resolve("art-source/tilesets").normalize(),
                context(root).resolveInsideProject("art-source/tilesets"));
    }

    @Test
    void anAbsolutePathInsideTheProjectIsAccepted(@TempDir Path root) throws IOException {
        // A caller echoing back a path this server itself reported should not be
        // punished for it; every structured result carries absolute paths.
        Path inside = root.resolve("build/snapshots");

        assertEquals(inside.normalize(), context(root).resolveInsideProject(inside.toString()));
    }

    @Test
    void aTraversalOutOfTheProjectIsRefused(@TempDir Path root) {
        assertThrows(IOException.class,
                () -> context(root).resolveInsideProject("../../etc/passwd"),
                "a sheet name is not a licence to write anywhere on the machine");
    }

    @Test
    void anAbsolutePathOutsideTheProjectIsRefused(@TempDir Path root) {
        assertThrows(IOException.class,
                () -> context(root).resolveInsideProject(
                        root.getRoot().resolve("Windows").toString()));
    }

    @Test
    void aBlankPathIsRefusedRatherThanMeaningTheProjectRoot(@TempDir Path root) {
        // Silently treating "" as the root turns a forgotten argument into a
        // write at the top of the checkout.
        assertThrows(IllegalArgumentException.class,
                () -> context(root).resolveInsideProject(""));
    }
}
