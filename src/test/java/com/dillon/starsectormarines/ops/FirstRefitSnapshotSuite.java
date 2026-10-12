package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import java.util.Map;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Production lesson markup and real issue, exclusively 1920x1080 at 100% UI scale. */
public final class FirstRefitSnapshotSuite implements SnapshotSuite {
    public String id() { return "first-refit"; }
    public String label() { return "A Proper Issue / two equipment paths"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        MarineRoster roster = new MarineRoster();
        var squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var reactor = new Reactor();
        EquipmentTemplateCost[] stock = {EquipmentTemplateCost.ZERO};
        EquipmentIssueResources resources = new EquipmentIssueResources() {
            public String commodityIcon(String id) { return switch (id) {
                case Commodities.SUPPLIES -> "graphics/icons/cargo/supplies.png";
                case Commodities.HAND_WEAPONS -> "graphics/icons/cargo/heavyweapons.png";
                case Commodities.HEAVY_MACHINERY -> "graphics/icons/cargo/heavymachinery.png";
                default -> "";
            }; }
            public String commodityName(String id) { return switch (id) {
                case Commodities.SUPPLIES -> "Supplies";
                case Commodities.HAND_WEAPONS -> "Heavy armaments";
                case Commodities.HEAVY_MACHINERY -> "Heavy machinery";
                default -> id;
            }; }
            public EquipmentTemplateCost available() { return stock[0]; }
            public boolean spend(EquipmentTemplateCost cost) {
                if (!stock[0].covers(cost)) return false;
                stock[0] = new EquipmentTemplateCost(stock[0].supplies() - cost.supplies(),
                        stock[0].heavyArmaments() - cost.heavyArmaments(), stock[0].heavyMachinery() - cost.heavyMachinery(), stock[0].food() - cost.food());
                return true;
            }
        };
        String[] inspected = {null};
        var model = new FirstRefitModel(reactor, roster, resources, id -> inspected[0] = id);
        List<SnapshotArtifact> frames = new ArrayList<>();
        frames.add(resultsFrame(context, roster));
        frames.add(frame(context, reactor, model, "01-wayline-consignment.png", "refit-claim"));
        if (!roster.firstRefit().claimed()) throw new AssertionError("Accept control did not claim reward");
        frames.add(frame(context, reactor, model, "02-choose-squad.png", "refit-squad:" + squad.id()));
        frames.add(frame(context, reactor, model, "02b-choose-path.png", "refit-path:STEADY_LINE"));
        frames.add(frame(context, reactor, model, "03-steady-line-shortfall.png", "refit-issue"));
        if (roster.firstRefit().complete()) throw new AssertionError("Disabled issue spent missing materials");
        stock[0] = new EquipmentTemplateCost(200, 100, 100, 0);
        model.refresh();
        frames.add(frame(context, reactor, model, "04-steady-line-review.png", "refit-path:FORWARD_SCREEN"));
        frames.add(frame(context, reactor, model, "05-forward-screen-review.png", null));
        var members = roster.manningMemberIds(squad);
        roster.applySoldierOutcome(Map.of(members.get(0), MarineSoldierStatus.WIA,
                members.get(10), MarineSoldierStatus.KIA, members.get(11), MarineSoldierStatus.MIA), 10f, 5f);
        model.refresh();
        frames.add(frame(context, reactor, model, "05b-wounded-understrength-issue.png", "refit-issue"));
        if (roster.soldierById(members.get(0)).status() != MarineSoldierStatus.WIA || roster.manningCount(squad) != 10)
            throw new AssertionError("Equipment issue changed personnel readiness");
        if (!roster.firstRefit().complete()) throw new AssertionError("Issue did not complete");
        frames.add(frame(context, reactor, model, "06-issued.png", "refit-inspect"));
        if (!squad.id().equals(inspected[0])) throw new AssertionError("Inspect did not route to the issued squad");
        return frames;
    }
    private SnapshotArtifact resultsFrame(SnapshotContext context, MarineRoster roster) throws Exception {
        var props = ResultsScreen.previewProps(true, true);
        var outcome = MissionOutcome.builder().victory(true).missionSource(MissionSource.STORY)
                .missionId(OpeningOperationKind.RELIEF.missionId).build();
        props.put("missionName", "Hold Until Relieved");
        ResultsScreen.putStoryDebrief(props, outcome);
        boolean[] opened = {false};
        ResultsScreen.putFirstRefit(props, outcome, roster, () -> opened[0] = true);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), ResultsScreen.COMPONENT_PATHS);
        try (var markup = loader.reloadAndBuild(new Reactor(), ResultsScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            var image = renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            var box = markup.requireElement("results-refit-open").box().borderBox();
            document.pointerDown(box.x() + box.width()/2, box.y() + box.height()/2);
            document.pointerUp(box.x() + box.width()/2, box.y() + box.height()/2);
            if (!opened[0]) throw new AssertionError("Debrief upgrade button did not open the lesson");
            return new SnapshotArtifact("00-debrief-upgrade-available.png", image);
        }
    }
    private SnapshotArtifact frame(SnapshotContext context, Reactor reactor, FirstRefitModel model, String file, String clickAfter) throws Exception {
        var props = new LinkedHashMap<String, Object>();
        props.put("lesson", model.row()); props.put("backToSquads", (Runnable) () -> { });
        Runnable noop = () -> { };
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.ARMORY, MarineOpsPageNav.ANY_SHIP, noop, noop, noop, noop, noop, noop);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(
                "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/armory/first-refit.mlx"));
        loader.reload();
        try (var markup = loader.build(reactor, "first-refit", props)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            FleetArmoryScreen.bindFirstRefitPreviews(document, markup, new HeadlessArmoryPreviewRenderer(context.modRoot()).assets());
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            var image = renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            if (clickAfter != null) {
                var box = markup.requireElement(clickAfter).box().borderBox();
                if (box.width() <= 0 || box.height() <= 0 || box.bottom() > document.viewport().height())
                    throw new AssertionError("Control is not visible: " + clickAfter);
                document.pointerDown(box.x() + box.width() / 2, box.y() + box.height() / 2);
                document.pointerUp(box.x() + box.width() / 2, box.y() + box.height() / 2);
                markup.flush();
            }
            return new SnapshotArtifact(file, image);
        }
    }
}
