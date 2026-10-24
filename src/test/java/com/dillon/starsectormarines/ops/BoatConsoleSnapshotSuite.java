package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/** The Boat Deck's refit board through the production canvas at 1080p / 100%, without the whole UI suite. */
public final class BoatConsoleSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "boat-console"; }
    @Override public String label() { return "Boat Deck refit board"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        return UiSnapshotSuite.renderBoatConsole(context);
    }
}
