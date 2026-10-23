package com.dillon.starsectormarines.tools.snapshot;

import java.util.List;

/** One discoverable family of deterministic authoring snapshots. */
public interface SnapshotSuite {

    String id();

    String label();

    /**
     * Whether {@code all} includes this suite. A slow suite that only answers a look question
     * (it re-renders on its own CPU model, not through the review drain) returns false and runs
     * only when named.
     */
    default boolean inAll() {
        return true;
    }

    List<SnapshotArtifact> render(SnapshotContext context) throws Exception;
}
