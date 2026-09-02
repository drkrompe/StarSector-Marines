package com.dillon.starsectormarines.battle.command.trace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The rule a commander-evidence harness cleans up under: a failed tidy-up is
 * news only when nothing else failed.
 *
 * <p>The undeletable tree is made the way the real one is — a file with a
 * handle still open on it, which Windows refuses to unlink. That is the exact
 * shape of the fault this guards: a {@code review.gif} left open by a capture
 * whose own run had already failed.
 */
class EvidenceCleanupTest {

    @Test
    void aCleanupFailureRidesBeneathTheFailureThatGotThere(@TempDir Path temp)
            throws Exception {
        Path root = temp.resolve("staging");
        Path held = heldFile(root);
        IllegalStateException primary =
                new IllegalStateException("the run failed");

        try (RandomAccessFile lock = new RandomAccessFile(held.toFile(), "rw")) {
            assertThrows(IOException.class, () -> EvidenceCleanup.deleteTree(root),
                    "the fixture must actually be undeletable");
            EvidenceCleanup.deleteTreeQuietlyAfter(primary, root);
            lock.length();
        }

        assertEquals(1, primary.getSuppressed().length,
                "the cleanup failure belongs to the primary, not above it");
        assertInstanceOf(IOException.class, primary.getSuppressed()[0]);
    }

    @Test
    void aCleanupFailureIsTheNewsWhenTheRunSucceeded(@TempDir Path temp)
            throws Exception {
        Path root = temp.resolve("staging");
        Path held = heldFile(root);

        try (RandomAccessFile lock = new RandomAccessFile(held.toFile(), "rw")) {
            assertThrows(IOException.class,
                    () -> EvidenceCleanup.deleteTreeQuietlyAfter(null, root));
            lock.length();
        }
    }

    @Test
    void aTreeThatDeletesLeavesNothingBehind(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("staging");
        Files.createDirectories(root.resolve("traces"));
        Files.writeString(root.resolve("traces/run.jsonl"), "trace\n");
        IllegalStateException primary = new IllegalStateException("the run failed");

        EvidenceCleanup.deleteTreeQuietlyAfter(primary, root);

        assertFalse(Files.exists(root));
        assertEquals(0, primary.getSuppressed().length);
    }

    @Test
    void aMissingTreeIsNotAFailure(@TempDir Path temp) throws Exception {
        EvidenceCleanup.deleteTree(temp.resolve("never-created"));
        EvidenceCleanup.deleteTree(null);
        EvidenceCleanup.deleteTreeQuietlyAfter(null, temp.resolve("never-created"));
    }

    private static Path heldFile(Path root) throws IOException {
        Files.createDirectories(root);
        Path held = root.resolve("review.gif");
        Files.writeString(held, "frames");
        return held;
    }
}
