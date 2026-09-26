package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.util.ArrayList;
import java.util.List;

/** Isolated production direct-control plate, without a battle simulation or live host. */
public final class DirectControlUiSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "direct-control-ui"; }
    @Override public String label() { return "Direct-control plate"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (int state = 0; state < 3; state++) {
            try (var markup = BattleDirectControlOverlayTest.fixture(
                    context.modRoot(), state == 2, state > 0, () -> {})) {
                var document = BattleDirectControlOverlayTest.document(markup);
                artifacts.add(new SnapshotArtifact(new String[]{"select", "ready", "active"}[state]
                        + ".png", renderer.render(document, 440, 64)));
            }
        }
        return artifacts;
    }
}
