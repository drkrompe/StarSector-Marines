package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.List;

/** Command administration, empty roster and 120-squad evidence at 1080p / 100%. */
public final class BarracksCommandSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "barracks-command"; }
    @Override public String label() { return "Barracks command board"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        return UiSnapshotSuite.renderBarracksCommand(context);
    }
}
