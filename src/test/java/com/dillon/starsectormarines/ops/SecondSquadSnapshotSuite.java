package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Actual assignment controls and preparation overlay at 1920x1080 / 100% only. */
public final class SecondSquadSnapshotSuite implements SnapshotSuite {
    public String id() { return "second-squad"; }
    public String label() { return "Second squad preparation and direct command assignment"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        var frames = new ArrayList<SnapshotArtifact>();
        var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        var roster = new MarineRoster();
        var first = roster.createSquad(); roster.recruitToSquad(first.id(), 12);
        var mission = SecondSquadPreparationTest.junction();
        var props = BriefingViewModel.campaignPreviewProps(false);
        props.put("missionTitle", mission.name);
        props.put("missionKicker", "CAPTURE / HOLD  /  LOW RISK");
        BriefingViewModel.putDebugDrawer(props, false, false, () -> {});
        BriefingViewModel.putForceComparison(props, OperationTier.FIRST_CONTRACT, RiskLevel.LOW, 12, 24);
        props.put("rewardValue", "12,000 credits");
        BriefingViewModel.putFactionIdentity(props, "employer", "Wayline / Independents", null);
        BriefingViewModel.putFactionIdentity(props, "opposition", "Local raiders", null);
        props.put("missionRows", List.of(BriefingViewModel.info("requirements", "Requires", "Two squads / at least four ready marines each", "")));
        props.put("missionFlavor", "Mara Venn: Wayline has its depot back, but both outlying roads are cut. Open the junction so we can put people and freight through again.");
        props.put("preparationClasses", "mission-preparation");
        boolean[] armory = {false}, barracks = {false}, replay = {false};
        props.put("prepareArmory", (Runnable) () -> armory[0] = true);
        props.put("prepareBarracks", (Runnable) () -> barracks[0] = true);
        props.put("prepareReplay", (Runnable) () -> replay[0] = true);
        var loader = loader(context, "data/ui/components/missions/mission-briefing.mlx");
        try (var markup = loader.reloadAndBuild(new Reactor(), "mission-briefing", props)) {
            var doc = PreparationGuide.document(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(doc::addStyleSheet);
            BriefingViewModel.applyVisibility(markup, props);
            var guide = new PreparationGuide(doc, () -> SecondSquadPreparation.lesson(mission, roster,
                    SquadFoundingResources.NONE, replay[0], () -> { roster.acknowledgeSecondSquadPreparation(); replay[0] = false; }),
                    () -> {}, loader(context, "data/ui/components/company/preparation-guide.mlx"));
            renderer.renderRelative(doc, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            guide.update();
            frames.add(frame(renderer, doc, "01-preparation-reminder.png"));
            click(doc, markup, "mission-prepare-armory");
            click(doc, markup, "mission-prepare-barracks");
            if (!armory[0] || !barracks[0] || roster.secondSquadPreparationSeen()) throw new AssertionError("Preparation route or saved reminder failed");
            var button = find(doc.root(), "preparation-continue");
            var box = button.box().borderBox();
            doc.pointerDown(box.x() + box.width()/2, box.y() + box.height()/2);
            doc.pointerUp(box.x() + box.width()/2, box.y() + box.height()/2);
            guide.update();
            if (!roster.secondSquadPreparationSeen()) throw new AssertionError("Acknowledgement did not save: " + box + " hit " + doc.elementAt(box.x() + box.width()/2, box.y() + box.height()/2));
            frames.add(frame(renderer, doc, "02-briefing-preparation-routes.png"));
            click(doc, markup, "mission-prepare-replay"); guide.update();
            if (!replay[0]) throw new AssertionError("Reminder replay failed");
        }
        var second = roster.createSquad(); roster.recruitToSquad(second.id(), 12);
        var mira = new MarineCaptain("Mira Hale", "graphics/portraits/mira-hale.png", Rank.LIEUTENANT, 0f);
        roster.add(mira); roster.assignCaptainToSquad(mira.id(), first.id());
        roster.dismissManagementGuide();
        var reactor = new Reactor();
        var vm = new BarracksViewModel(reactor, roster, () -> 0d, new EquipmentIssueResources() {
            public EquipmentTemplateCost available() { return new EquipmentTemplateCost(72, 24, 12, 48); }
            public boolean spend(EquipmentTemplateCost cost) { return false; }
            public String commodityName(String id) { return id; }
            public String commodityIcon(String id) { return ""; }
        }, () -> 12, id -> new PersonnelDrawResult(0, 0));
        var model = vm.command();
        var properties = new LinkedHashMap<String, Object>();
        properties.put("command", model.props()); properties.put("contextLabel", "PERSONNEL COMMAND");
        properties.put("squadRows", vm.squadRows()); properties.put("musterRows", vm.musterRows());
        properties.put("recordCells", vm.recordCells()); properties.put("selectedSquadName", vm.selectedSquadName());
        properties.put("selectedSquadSummary", vm.selectedSquadSummary()); properties.put("quartersStatus", vm.quartersStatus());
        Runnable noop = () -> {};
        MarineOpsPageNav.put(properties, MarineOpsPageNav.Page.BARRACKS, MarineOpsPageNav.ANY_SHIP, noop, noop, noop, noop, noop, noop);
        loader = loader(context, "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/company/shipboard-barracks.mlx");
        try (var markup = loader.reloadAndBuild(reactor, "shipboard-barracks", properties)) {
            var doc = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(doc::addStyleSheet);
            var bindings = new BarracksCardBindings();
            var hints = new SpecSheetBinder(doc, SpecSheetLayer.install(doc), true);
            bindings.update(doc, markup, vm, hints);
            frame(renderer, doc, "layout.png");
            click(doc, markup, "formation:" + second.id() + ":mark");
            bindings.update(doc, markup, vm, hints);
            frames.add(frame(renderer, doc, "03-select-squads.png"));
            click(doc, markup, "command:" + mira.id() + ":assign");
            if (!mira.id().equals(second.homeCaptainId()) || model.selected().size() != 1) throw new AssertionError("Direct assignment lost selection or failed");
            bindings.update(doc, markup, vm, hints);
            frames.add(frame(renderer, doc, "04-assigned-portraits.png"));
            click(doc, markup, "command:independent:assign");
            if (second.homeCaptainId() != null) throw new AssertionError("Independent action failed");
            bindings.update(doc, markup, vm, hints);
            frames.add(frame(renderer, doc, "05-independent.png"));
            for (int i = 0; i < 2; i++) { var s = roster.createSquad(); roster.recruitToSquad(s.id(), 12); }
            model.refresh(); markup.flush(); doc.advance(0f);
            click(doc, markup, "barracks-select-all");
            bindings.update(doc, markup, vm, hints);
            frames.add(frame(renderer, doc, "06-capacity-blocked.png"));
            click(doc, markup, "command:" + mira.id() + ":assign");
            if (second.homeCaptainId() != null) throw new AssertionError("Over-capacity assignment changed roster");
            // The first-command spotlight must teach the same direct button.
            roster.beginOfficerInduction(mira); roster.officerInduction().introduce(mira);
            mira.train(OfficerSpecialty.TACTICAL_COVER);
            roster.assignCaptainToSquads(null, model.selected());
            model.resumeGuide(); model.toggle(first.id()); markup.flush(); doc.advance(0f);
            var guide = BarracksGuideCallout.bind(doc, markup, model, noop);
            guide.update();
            frames.add(frame(renderer, doc, "07-first-command-guide.png"));
            click(doc, markup, model.guideTargetId()); guide.update();
            if (!mira.id().equals(first.homeCaptainId())) throw new AssertionError("Guided direct assignment failed");
        }
        return frames;
    }
    private static MarkupLoader loader(SnapshotContext context, String... files) {
        return new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(files));
    }
    private static SnapshotArtifact frame(HeadlessUiRenderer renderer, UiDocument doc, String name) {
        doc.advance(0.3f);
        return new SnapshotArtifact(name, renderer.renderRelative(doc, 1920, 1080, 1f,
                MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT));
    }
    private static void click(UiDocument doc, MarkupInstance markup, String id) {
        var box = markup.requireElement(id).box().borderBox();
        if (box.width() <= 0 || box.height() <= 0 || box.bottom() > doc.viewport().height()) throw new AssertionError("Invisible control " + id);
        doc.pointerDown(box.x() + box.width()/2, box.y() + box.height()/2);
        doc.pointerUp(box.x() + box.width()/2, box.y() + box.height()/2);
        markup.flush(); doc.advance(0f);
    }
    private static UiElement find(UiElement root, String id) {
        if (root.id().equals(id)) return root;
        for (var child : root.children()) { var found = find(child, id); if (found != null) return found; }
        return null;
    }
}
