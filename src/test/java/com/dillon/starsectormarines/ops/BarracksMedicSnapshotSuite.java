package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.List;

/** An officer with Field Medicine: each of their squad cards marks its medic billet, 1080p / 100%. */
public final class BarracksMedicSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "barracks-medic"; }
    @Override public String label() { return "Barracks squad medic billets"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        return UiSnapshotSuite.renderBarracksCommand(context, List.of("medic"), false, false);
    }
}
