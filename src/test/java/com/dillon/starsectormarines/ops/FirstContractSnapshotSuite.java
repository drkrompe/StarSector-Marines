package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.util.ArrayList;
import java.util.List;

/** First-work guidance through the production HQ at 1920x1080, 100%. */
public final class FirstContractSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "first-contract"; }
    @Override public String label() { return "Reliable first-contract guidance"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> frames = new ArrayList<>();
        MarineRoster roster = new MarineRoster();
        for (String state : List.of("fresh-company", "first-squad", "follow-up", "completed")) {
            if (state.equals("first-squad")) roster.bootstrapInitialComplement(12);
            if (state.equals("follow-up")) roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
            if (state.equals("completed")) roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
            frames.add(new SnapshotArtifact("hq-" + state + ".png", UiSnapshotSuite.renderCompanyHq(
                    context, renderer, 1920, 1080, 1f, CompanyHqViewModel.openingPreview(roster))));
        }
        return frames;
    }
}
