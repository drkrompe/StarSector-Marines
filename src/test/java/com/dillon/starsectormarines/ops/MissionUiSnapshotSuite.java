package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/** Focused mission previews, always at 1920 x 1080 with 100% UI scale. */
public final class MissionUiSnapshotSuite implements SnapshotSuite {
    @Override
    public String id() { return "mission-ui"; }

    @Override
    public String label() { return "Mission UI (1080p, 100%)"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        return UiSnapshotSuite.renderMissionPreviews(context);
    }
}
