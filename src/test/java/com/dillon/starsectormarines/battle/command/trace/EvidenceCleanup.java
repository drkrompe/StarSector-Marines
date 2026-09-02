package com.dillon.starsectormarines.battle.command.trace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * How a commander-evidence harness tidies up without ever replacing the
 * failure that got it there.
 *
 * <p>A {@code finally} block that throws discards the exception already in
 * flight, and evidence cleanup is exactly the kind of block that throws: the
 * staging tree holds a {@code review.gif}, Windows refuses to delete a file a
 * writer still has open, and the resulting "the process cannot access the
 * file" is what the run reports instead of the assertion or the simulation
 * failure that ended it. One thirteen-minute Conquest run was lost that way,
 * and the report said nothing whatever about what had actually failed.
 *
 * <p>So a harness names the throwable it is already carrying. A cleanup
 * failure is attached to that one as suppressed, and is thrown on its own
 * only when the run had otherwise succeeded — the case where it is the news.
 */
final class EvidenceCleanup {

    private EvidenceCleanup() { }

    /**
     * Deletes {@code root} and everything under it. A path that is not there
     * is not a failure, and neither is a null one.
     */
    static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Deletes {@code root} beneath a failure that is already in flight.
     *
     * <p>With a {@code primary} in hand a cleanup failure is added to it as a
     * suppressed exception and this returns normally, so the caller's own
     * throw survives. With no primary — the run succeeded and only the tidying
     * did not — the cleanup failure is thrown, because then it is the only
     * news there is.
     *
     * @param primary the throwable the caller is about to rethrow, or null
     * @param root    the tree to remove
     */
    static void deleteTreeQuietlyAfter(Throwable primary, Path root)
            throws IOException {
        try {
            deleteTree(root);
        } catch (IOException cleanupFailure) {
            if (primary == null) throw cleanupFailure;
            primary.addSuppressed(cleanupFailure);
        }
    }
}
