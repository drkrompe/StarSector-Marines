package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.List;

/**
 * Barracks' way back to work at 1080p / 100%: quiet, a planet with work, a mission in progress,
 * and the looks put to the owner for the calling state (primary as shipped, gold, pulse).
 */
public final class BarracksMissionCallSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "barracks-mission-call"; }
    @Override public String label() { return "Barracks mission call"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        return UiSnapshotSuite.renderBarracksCommand(context, List.of("call-quiet", "call-work", "call-briefing",
                "call-work-gold", "call-briefing-gold", "call-work-pulse", "call-briefing-pulse"), false, false);
    }
}
