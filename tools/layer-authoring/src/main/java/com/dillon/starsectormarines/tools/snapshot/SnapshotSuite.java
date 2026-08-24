package com.dillon.starsectormarines.tools.snapshot;

import java.util.List;

/** One discoverable family of deterministic authoring snapshots. */
public interface SnapshotSuite {

    String id();

    String label();

    List<SnapshotArtifact> render(SnapshotContext context) throws Exception;
}
