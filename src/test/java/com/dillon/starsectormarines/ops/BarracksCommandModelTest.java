package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiKey;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BarracksCommandModelTest {
    @Test void selectionSpansPagesButNotStationedSquadsAndFiltersDiscardIt() {
        var roster = new MarineRoster();
        roster.bootstrapInitialComplement(13 * MarineSquad.CAPACITY);
        var captain = new MarineCaptain("Vale", null, Rank.CAPTAIN, 0f);
        roster.add(captain);
        var squads = roster.squads().stream().filter(s -> !s.reserve()).toList();
        assertTrue(roster.bindStationing(1L, captain.id(), List.of(squads.get(0).id())));
        var model = new BarracksViewModel(new Reactor(), roster, () -> 1d).command();
        action(model, "selectAll");
        assertEquals(12, model.selected().size());
        assertFalse(model.selected().contains(squads.get(0).id()));
        action(model, "next");
        assertEquals("PAGE 2 / 2", value(model, "pageLabel"));
        assertEquals(12, model.selected().size());
        action(model, "attention");
        assertTrue(model.selected().isEmpty());
        assertEquals("PAGE 1 / 1", value(model, "pageLabel"));
    }

    @Test void realPointerFlowSavesAndAssignsProfileThenInspectsAndReturns() throws Exception {
        CompanyManagementGuide.install(new JSONObject(Files.readString(Path.of("mod", CompanyManagementGuide.PATH))));
        var reactor = new Reactor();
        var roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        var squad = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
        roster.applySoldierOutcome(Map.of(squad.memberIds().get(0), MarineSoldierStatus.KIA), 0f, 0f);
        var vm = new BarracksViewModel(reactor, roster, () -> 1d);
        var model = vm.command();
        var props = new LinkedHashMap<String, Object>();
        props.put("command", model.props()); props.put("contextLabel", "PERSONNEL COMMAND");
        props.put("squadRows", vm.squadRows()); props.put("musterRows", vm.musterRows());
        props.put("recordCells", vm.recordCells()); props.put("selectedSquadName", vm.selectedSquadName());
        props.put("selectedSquadSummary", vm.selectedSquadSummary()); props.put("quartersStatus", vm.quartersStatus());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.BARRACKS, MarineOpsPageNav.ANY_SHIP,
                () -> {}, () -> {}, model::showOverview, () -> {}, () -> {}, () -> {});
        var previous = SvgAsset.setSource(path -> Files.readString(Path.of("mod").resolve(path)));
        try {
            var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                    List.of("data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/company/shipboard-barracks.mlx"));
            loader.reload();
            try (var instance = loader.build(reactor, "shipboard-barracks", props)) {
                var document = new UiDocument(instance.root());
                instance.styles().forEach(document::addStyleSheet);
                document.theme(MarineOpsThemes.standard());
                var guide = BarracksGuideCallout.bind(document, instance, model, model::showOverview);
                document.layout(1920, 1080);
                guide.update();
                assertEquals("graphics/portraits/castle.png", value(model, "guidePortrait"));
                assertEquals("formation:" + squad.id() + ":mark", model.guideTargetId());
                click(document, instance, model.guideTargetId());
                assertEquals(1, model.selected().size(), "Squad selection works while Castle is speaking");
                assertTrue(model.guideVisible());
                guide.update();
                assertEquals("barracks-choose-donors", model.guideTargetId());
                var target = instance.requireElement(model.guideTargetId()).box().borderBox();
                var panel = instance.requireElement("barracks-guide").box().borderBox();
                assertEquals(0, panel.intersect(target).width() * panel.intersect(target).height());
                document.requestFocus(instance.requireElement("barracks-select-all"), true);
                assertEquals("barracks-select-all", document.focusedElement().id(), "Keyboard focus can reach a highlighted region");
                assertThrows(IllegalArgumentException.class,
                        () -> document.requestFocus(instance.requireElement("barracks-organization"), true));
                click(document, instance, "barracks-organization");
                assertEquals("barracks-choose-donors", model.guideTargetId(), "Darkened organization controls do not activate");
                document.keyPressed(UiKey.ESCAPE, Set.of(), false);
                instance.flush(); document.advance(0f); guide.update();
                assertFalse(model.guideVisible());
                click(document, instance, "barracks-guide-help");
                guide.update();
                assertTrue(model.guideVisible());
                assertEquals("barracks-choose-donors", model.guideTargetId(), "Reopening resumes the lesson");
                action(model, "guideNext");
                action(model, "guideNext");
                instance.flush(); document.advance(0f); guide.update();
                click(document, instance, "guide-dismiss");
                click(document, instance, "barracks-clear");

                var hints = new SpecSheetBinder(document, SpecSheetLayer.install(document), true);
                var bindings = new BarracksCardBindings();
                bindings.update(document, instance, model, hints);
                long canvasRevision = document.canvases().revision();
                click(document, instance, "barracks-select-all");
                bindings.update(document, instance, model, hints);
                assertTrue(document.canvases().revision() > canvasRevision, "Changed cards must invalidate the cached muster");
                canvasRevision = document.canvases().revision();
                bindings.update(document, instance, model, hints);
                assertEquals(canvasRevision, document.canvases().revision(), "Unchanged cards should reuse the cached muster");
                assertEquals(1, model.selected().size());
                click(document, instance, "barracks-organization");
                click(document, instance, "barracks-new-profile");
                click(document, instance, "barracks-save-profile");
                assertEquals(1, roster.armory().squadProfiles().size());
                click(document, instance, "barracks-assign-profile");
                assertEquals(roster.armory().squadProfiles().get(0).id(), squad.squadProfileId());
                click(document, instance, "command:independent:assign");
                assertNull(squad.homeCaptainId());
                click(document, instance, "formation:" + squad.id() + ":inspect");
                assertTrue(model.inspecting());
                click(document, instance, "barracks-back-command");
                assertFalse(model.inspecting());
                assertEquals(1, model.selected().size());
                var officer = new MarineCaptain("Vale", null, Rank.LIEUTENANT, 0f);
                roster.add(officer); model.refresh(); instance.flush(); document.advance(0f);
                click(document, instance, "command:" + officer.id());
                assertEquals("officer-dossier panel console-section", value(model, "dossierClasses"));
                assertTrue(instance.requireElement("officer-traits").box().borderBox().y()
                        >= instance.requireElement("officer-specialty-SALVAGE_STEWARDSHIP").box().borderBox().bottom(),
                        "Service record must follow all specialty cards without overlapping");
                var trainingBox = instance.requireElement("officer-specialty-TACTICAL_COVER-train").box().borderBox();
                assertEquals("officer-specialty-TACTICAL_COVER-train", document.elementAt(trainingBox.x() + trainingBox.width() / 2f,
                        trainingBox.y() + trainingBox.height() / 2f).id());
                click(document, instance, "officer-specialty-TACTICAL_COVER-train");
                assertEquals(1, officer.career().level(OfficerSpecialty.TACTICAL_COVER));
                assertEquals(0, officer.career().availablePoints(officer.rank()));
                click(document, instance, "officer-specialty-TACTICAL_COVER-train");
                assertEquals(1, officer.career().level(OfficerSpecialty.TACTICAL_COVER), "Disabled training cannot spend twice");
                click(document, instance, "officer-formations");
                assertEquals("hidden", value(model, "dossierClasses"));
                click(document, instance, "barracks-officer-dossier");
                assertEquals("officer-dossier panel console-section", value(model, "dossierClasses"));
            }
        } finally { SvgAsset.setSource(previous); CompanyManagementGuide.install(new JSONObject("{\"lines\":[]}")); }
    }

    private static Object value(BarracksCommandModel model, String key) { return ((Signal<?>) model.props().get(key)).get(); }
    private static void action(BarracksCommandModel model, String key) { ((Runnable) model.props().get(key)).run(); }
    private static void click(UiDocument document, MarkupInstance instance, String id) {
        var box = instance.requireElement(id).box().borderBox();
        assertTrue(box.width() > 0 && box.height() > 0, id);
        assertTrue(box.y() >= 0 && box.bottom() <= 1080, id);
        float x = box.x() + box.width() / 2f, y = box.y() + box.height() / 2f;
        assertTrue(document.pointerDown(x, y), id);
        assertTrue(document.pointerUp(x, y), id);
        instance.flush(); document.advance(0f);
    }
}
