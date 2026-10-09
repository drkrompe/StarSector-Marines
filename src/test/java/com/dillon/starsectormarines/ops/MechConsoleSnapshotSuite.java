package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/** Equipment fitting and motion evidence through the production canvas at 1080p / 100%. */
public final class MechConsoleSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "mech-console"; }
    @Override public String label() { return "Mech Lab equipment console"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        return UiSnapshotSuite.renderMechConsole(context);
    }
}
