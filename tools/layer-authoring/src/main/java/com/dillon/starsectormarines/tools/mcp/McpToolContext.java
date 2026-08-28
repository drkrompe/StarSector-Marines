package com.dillon.starsectormarines.tools.mcp;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The repository a tool call acts on.
 *
 * <p>Deliberately the same two roots as {@code SnapshotContext}: a tool server
 * and a snapshot suite are both headless consumers of the same checkout, and
 * giving them different notions of "where the project is" would be a second
 * answer to a settled question.
 *
 * <p>The context is also the only authority on what a tool may write. Names and
 * paths reaching a tool come from a model rather than from a person who typed
 * them, so {@link #resolveInsideProject} exists to make "stay inside the
 * checkout" a thing a tool asks for rather than a thing each tool re-implements
 * and one of them forgets.
 */
public record McpToolContext(Path projectRoot, Path starsectorCore) {

    public McpToolContext {
        if (projectRoot == null || starsectorCore == null) {
            throw new IllegalArgumentException("project and Starsector core roots are required");
        }
        projectRoot = projectRoot.toAbsolutePath().normalize();
        starsectorCore = starsectorCore.toAbsolutePath().normalize();
    }

    public Path modRoot() {
        return projectRoot.resolve("mod");
    }

    /**
     * Resolve a caller-supplied path against the project, refusing anything that
     * escapes it.
     *
     * <p>Absolute paths are allowed as long as they land inside the checkout, so
     * a caller echoing a path this server itself reported is not punished for it.
     */
    public Path resolveInsideProject(String candidate) throws IOException {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("a path is required");
        }
        Path path = Path.of(candidate);
        Path resolved = (path.isAbsolute() ? path : projectRoot.resolve(path))
                .toAbsolutePath().normalize();
        if (!resolved.startsWith(projectRoot)) {
            throw new IOException("path escapes the project root: " + candidate);
        }
        return resolved;
    }
}
