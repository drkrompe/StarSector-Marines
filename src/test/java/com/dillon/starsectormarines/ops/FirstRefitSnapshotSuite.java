package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Production lesson markup and real issue, exclusively 1920x1080 at 100% UI scale. */
public final class FirstRefitSnapshotSuite implements SnapshotSuite {
    public String id() { return "first-refit"; }
    public String label() { return "A Proper Issue / two equipment paths"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (var strings = new HeadlessStrings(context.modRoot())) { return renderLocalized(context); }
    }
    private List<SnapshotArtifact> renderLocalized(SnapshotContext context) throws Exception {
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        MarineRoster roster = new MarineRoster();
        var squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var reactor = new Reactor();
        EquipmentIssueResources resources = new EquipmentIssueResources() {
            public EquipmentTemplateCost available() { throw new AssertionError("Fitting must not read cargo"); }
            public boolean spend(EquipmentTemplateCost cost) { throw new AssertionError("Fitting must not spend cargo"); }
        };
        String[] inspected = {null};
        var model = new FirstRefitModel(reactor, roster, resources, id -> inspected[0] = id);
        List<SnapshotArtifact> frames = new ArrayList<>();
        frames.add(resultsFrame(context, roster, false));
        frames.add(resultsFrame(context, roster, true));
        frames.add(frame(context, reactor, model, "01-wayline-technology-choice.png", null));
        frames.add(frame(context, reactor, model, "02-choose-squad.png", "refit-squad:" + squad.id()));
        frames.add(frame(context, reactor, model, "02b-choose-path.png", "refit-path:STEADY_LINE"));
        frames.add(frame(context, reactor, model, "03-steady-line-free-fitting.png", null));
        if (roster.firstRefit().complete()) throw new AssertionError("Inspection fitted equipment without confirmation");
        model.refresh();
        frames.add(frame(context, reactor, model, "04-steady-line-review.png", "refit-path:FORWARD_SCREEN"));
        frames.add(frame(context, reactor, model, "05-forward-screen-review.png", null));
        frames.add(frame(context, reactor, model, "05a-current-weapon-spec.png", null, "refit-current-item:0"));
        frames.add(frame(context, reactor, model, "05a-proposed-weapon-spec.png", null, "refit-proposed-item:0"));
        frames.add(frame(context, reactor, model, "05a-scout-system-spec.png", "refit-billet:11", "refit-proposed-item:1:system"));
        frames.add(frame(context, reactor, model, "05a-special-removal.png", "refit-billet:0"));
        if (roster.firstRefit().claimed()) throw new AssertionError("Comparison granted technology");
        frames.add(frame(context, reactor, model, "05a-accept-forward-screen.png", "refit-claim"));
        if (!roster.firstRefit().claimed()) throw new AssertionError("Accept control did not claim reward");
        var members = roster.manningMemberIds(squad);
        roster.applySoldierOutcome(Map.of(members.get(0), MarineSoldierStatus.WIA,
                members.get(10), MarineSoldierStatus.KIA, members.get(11), MarineSoldierStatus.MIA), 10f, 5f);
        model.refresh();
        frames.add(frame(context, reactor, model, "05b-wounded-understrength-issue.png", "refit-billet:11"));
        frames.add(frame(context, reactor, model, "05c-vacant-billet.png", "refit-issue"));
        if (roster.soldierById(members.get(0)).status() != MarineSoldierStatus.WIA || roster.manningCount(squad) != 10)
            throw new AssertionError("Equipment issue changed personnel readiness");
        if (!roster.firstRefit().complete()) throw new AssertionError("Issue did not complete");
        frames.add(frame(context, reactor, model, "06-issued.png", "refit-inspect"));
        if (!squad.id().equals(inspected[0])) throw new AssertionError("Inspect did not route to the issued squad");
        return frames;
    }
    private SnapshotArtifact resultsFrame(SnapshotContext context, MarineRoster roster, boolean showPrompt) throws Exception {
        var props = ResultsScreen.previewProps(true, true);
        var outcome = MissionOutcome.builder().victory(true).missionSource(MissionSource.STORY)
                .missionId(OpeningOperationKind.RELIEF.missionId).build();
        props.put("missionName", "Hold Until Relieved");
        ResultsScreen.putStoryDebrief(props, outcome);
        boolean[] opened = {false};
        ResultsScreen.putFirstRefit(props, outcome, roster, () -> opened[0] = true);
        var reactor = new Reactor();
        var prompt = new DebriefRewardPrompt(reactor, () -> opened[0] = true, () -> {});
        if (showPrompt) { prompt.present(outcome, roster); props.put("rewardPrompt", prompt.row()); }
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), ResultsScreen.COMPONENT_PATHS);
        try (var markup = loader.reloadAndBuild(reactor, ResultsScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            var image = renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            prompt.bind(document, markup, () -> {});
            var box = markup.requireElement(showPrompt ? "reward-open" : "results-refit-open").box().borderBox();
            if (box.width() > 320) throw new AssertionError("Reward action must stay compact");
            document.pointerDown(box.x() + box.width()/2, box.y() + box.height()/2);
            document.pointerUp(box.x() + box.width()/2, box.y() + box.height()/2);
            if (!opened[0]) throw new AssertionError("Debrief upgrade button did not open the lesson");
            return new SnapshotArtifact(showPrompt ? "00a-free-technology-prompt.png" : "00-debrief-upgrade-available.png", image);
        }
    }
    private SnapshotArtifact frame(SnapshotContext context, Reactor reactor, FirstRefitModel model, String file, String clickAfter) throws Exception {
        return frame(context, reactor, model, file, clickAfter, null);
    }
    private SnapshotArtifact frame(SnapshotContext context, Reactor reactor, FirstRefitModel model, String file, String clickAfter, String hover) throws Exception {
        var props = new LinkedHashMap<String, Object>();
        props.put("lesson", model.row()); props.put("backToSquads", (Runnable) () -> { });
        Runnable noop = () -> { };
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.ARMORY, MarineOpsPageNav.ANY_SHIP, noop, noop, noop, noop, noop, noop);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(
                "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/armory/armory-weapon-tech-card.mlx", "data/ui/components/armory/first-refit.mlx"));
        loader.reload();
        try (var markup = loader.build(reactor, "first-refit", props)) {
            var document = PreparationGuide.document(markup.root()).theme(MarineOpsThemes.standard());
            var guide = new PreparationGuide(document, model::guide, () -> {},
                    new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                            List.of("data/ui/components/company/preparation-guide.mlx")));
            markup.styles().forEach(document::addStyleSheet);
            var binder = new SpecSheetBinder(document, SpecSheetLayer.install(document));
            FleetArmoryScreen.bindFirstRefitSheets(binder, markup, model);
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            var image = renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            guide.update();
            image = renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            if (file.equals("04-steady-line-review.png")) {
                verifyGrid(document, markup, model);
                if (markup.requireElement("refit-claim").box().borderBox().width() > 320)
                    throw new AssertionError("Lesson accept button must stay compact");
            }
            if (hover != null) {
                var target = markup.requireElement(hover);
                var box = target.box().borderBox();
                document.pointerMoved(box.x() + box.width()/2, box.y() + box.height()/2);
                binder.update(); document.advance(0.3f);
                if (binder.openTarget() != target) throw new AssertionError("Spec sheet did not open: " + hover);
                image = renderer.renderRelative(document, 1920, 1080, 1f,
                        MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            }
            if (clickAfter != null) {
                var box = markup.requireElement(clickAfter).box().borderBox();
                if (box.width() <= 0 || box.height() <= 0 || box.bottom() > document.viewport().height())
                    throw new AssertionError("Control is not visible: " + clickAfter);
                document.pointerDown(box.x() + box.width() / 2, box.y() + box.height() / 2);
                document.pointerUp(box.x() + box.width() / 2, box.y() + box.height() / 2);
                markup.flush();
                if (clickAfter.startsWith("refit-billet:")) {
                    var selected = markup.requireElement(clickAfter);
                    if (!selected.hasClass("refit-selected")) throw new AssertionError("Billet selection did not update: " + clickAfter);
                    // Existing bindings must describe the new selection without rebuilding the document.
                    var target = markup.requireElement("refit-proposed-item:0");
                    var targetBox = target.box().borderBox();
                    document.pointerMoved(targetBox.x() + targetBox.width()/2, targetBox.y() + targetBox.height()/2);
                    binder.update();
                    if (binder.openTarget() != target) throw new AssertionError("Selection lost its live spec-sheet binding");
                }
            }
            binder.clear();
            return new SnapshotArtifact(file, image);
        }
    }
    /** All twelve real controls must remain visible and select their own billet. */
    private void verifyGrid(UiDocument document, MarkupInstance markup, FirstRefitModel model) {
        int selected = 0;
        var area = markup.requireElement("refit-billets").box().borderBox();
        for (int i = 0; i < 12; i++) {
            var cell = markup.requireElement("refit-billet:" + i);
            if (cell.hasClass("refit-selected")) selected = i;
            var box = cell.box().borderBox();
            var column = markup.requireElement("refit-billet:" + (i / 4 * 4)).box().borderBox();
            var row = markup.requireElement("refit-billet:" + (i % 4)).box().borderBox();
            if (Math.abs(box.x() - column.x()) > 0.1f || Math.abs(box.y() - row.y()) > 0.1f
                    || box.bottom() > area.bottom() || box.right() > area.right() || box.width() < 90f)
                throw new AssertionError("Billet is not a visible square in the 3x4 grid: " + i);
        }
        for (int i = 0; i < 12; i++) {
            var box = markup.requireElement("refit-armor-icon:" + i).box().borderBox();
            document.pointerDown(box.x() + box.width()/2, box.y() + box.height()/2);
            document.pointerUp(box.x() + box.width()/2, box.y() + box.height()/2);
            markup.flush();
            String position = new String[]{"Alpha", "Bravo", "Charlie"}[i / 4] + " " + (i % 4 + 1);
            if (!markup.requireElement("refit-billet:" + i).hasClass("refit-selected")
                    || !model.row().get().get("selectedName").toString().startsWith(position + " / "))
                throw new AssertionError("Equipment square did not select its own comparison: " + i);
        }
        model.selectBillet(selected);
        markup.flush();
    }

}
