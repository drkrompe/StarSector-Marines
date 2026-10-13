package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import org.json.JSONObject;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Real opening controls and spotlight clicks, with expanded copy, at 1920x1080 / 100%. */
public final class OpeningLocalizationSnapshotSuite implements SnapshotSuite {
    public String id() { return "opening-localization"; }
    public String label() { return "Opening localization / English and expanded copy"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.modRoot().getParent());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()),
                context.modRoot(), context.starsectorCore());
        var result = new ArrayList<SnapshotArtifact>();
        for (boolean expanded : List.of(false, true)) {
            try (var strings = new HeadlessStrings(context.modRoot(), text -> expanded ? expand(text) : text)) {
                var frames = new ArrayList<>(new CompanyPrologueSnapshotSuite().renderPages(context));
                frames.addAll(UiSnapshotSuite.renderOfficerInduction(context, renderer, 1f));
                frames.addAll(UiSnapshotSuite.renderCampaignGantries(context));
                for (var frame : frames) {
                    result.add(new SnapshotArtifact((expanded ? "expanded/" : "english/") + frame.relativePath(), frame.image()));
                }
            }
        }
        return result;
    }

    /** Add 50% length without touching numbered arguments, choice syntax or printf formats. */
    static String expand(String text) {
        if (text.isBlank()) return text;
        int extra = Math.max(2, (int) Math.ceil(text.length() * .5));
        return text + " " + "extended ".repeat(extra / 9 + 1).substring(0, extra).stripTrailing();
    }
}
