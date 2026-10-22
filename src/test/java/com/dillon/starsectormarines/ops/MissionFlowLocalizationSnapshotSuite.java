package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/** Mission previews (briefing, deployment, results, salvage) and the battle HUD, 1920x1080 at 100% in both languages. */
public final class MissionFlowLocalizationSnapshotSuite implements SnapshotSuite {
    public String id() { return "mission-flow-localization"; }
    public String label() { return "Mission flow and battle HUD / English and expanded copy"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        var result = new ArrayList<SnapshotArtifact>();
        for (boolean expanded : List.of(false, true)) {
            try (var strings = new HeadlessStrings(context.modRoot(), text -> expanded ? OpeningLocalizationSnapshotSuite.expand(text) : text)) {
                var frames = new ArrayList<>(UiSnapshotSuite.renderMissionPreviews(context));
                frames.addAll(new HudCompositionSnapshotSuite().render(context));
                for (var frame : frames) {
                    result.add(new SnapshotArtifact((expanded ? "expanded/" : "english/") + frame.relativePath(), frame.image()));
                }
            }
        }
        return result;
    }
}
