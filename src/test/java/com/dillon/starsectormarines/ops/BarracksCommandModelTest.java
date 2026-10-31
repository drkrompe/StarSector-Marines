package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.TestStores;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
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
    @Test void raisingASquadFillsOnlyFreeCommandSlots() {
        var roster = new MarineRoster();
        var lieutenant = new MarineCaptain("Hale", null, Rank.LIEUTENANT, 0f);
        roster.add(lieutenant);
        TestStores stores = TestStores.forSquads(80);
        var model = new BarracksViewModel(new Reactor(), roster, () -> 1d, EquipmentIssueResources.UNLIMITED,
                stores, () -> 0, id -> new PersonnelDrawResult(0, 0)).command();
        model.selectCommand(lieutenant.id());
        for (int i = 0; i < lieutenant.rank().squadCommandCap(); i++) model.raiseSquad();
        assertEquals(lieutenant.rank().squadCommandCap(), roster.squads().stream()
                .filter(s -> lieutenant.id().equals(s.homeCaptainId())).count());
        int squads = roster.squads().size();
        model.raiseSquad();
        assertEquals(squads, roster.squads().size(), "A full command raises nothing");
        assertEquals(lieutenant.rank().squadCommandCap(), stores.spends, "A refused raise pays no bill");
        assertTrue(raiseTile(model).get("founderDisabled") instanceof Boolean b && b);

        var captain = new MarineCaptain("Vale", null, Rank.CAPTAIN, 0f);
        roster.add(captain);
        model.selectCommand("all");
        model.raiseSquad();
        assertEquals(captain.id(), roster.squads().get(roster.squads().size() - 1).homeCaptainId(),
                "All formations raises into the first officer with a free slot");
    }

    @Test void aCardsWrenchFillsOnlyThatSquad() {
        var roster = new MarineRoster();
        roster.bootstrapInitialComplement(2 * MarineSquad.CAPACITY);
        var squads = roster.squads().stream().filter(s -> !s.reserve()).toList();
        for (var squad : squads) {
            roster.applySoldierOutcome(Map.of(roster.manningMemberIds(squad).get(0), MarineSoldierStatus.KIA), 0f, 0f);
        }
        roster.recruitToSquad(roster.reserveSquad().id(), 2);
        var model = new BarracksViewModel(new Reactor(), roster, () -> 1d, EquipmentIssueResources.UNLIMITED,
                new TestStores(), () -> 0, id -> new PersonnelDrawResult(roster.fillVacanciesFromReserve(id), 0)).command();
        model.fill(Set.of(squads.get(1).id()));
        assertEquals(1, roster.vacancies(squads.get(0)));
        assertEquals(0, roster.vacancies(squads.get(1)));
    }

    @Test void raiseTileIsTheLastTileAndUnaffordableStoresDisableIt() {
        var roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        var model = new BarracksViewModel(new Reactor(), roster, () -> 1d).command();
        var tile = raiseTile(model);
        assertEquals("barracks-raise-squad", tile.get("founderId"));
        assertEquals(true, tile.get("founderDisabled"));
        int before = roster.squads().size();
        model.raiseSquad();
        assertEquals(before, roster.squads().size(), "No stores, no squad");
    }

    @Test void selectionSpansPagesButNotStationedSquadsAndClearDiscardsIt() {
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
        action(model, "clear");
        assertTrue(model.selected().isEmpty());
    }

    @Test void realPointerFlowFillsVacanciesThenInspectsAndReturns() throws Exception {
        CompanyManagementGuide.install(new JSONObject(Files.readString(Path.of("mod", CompanyManagementGuide.PATH))));
        var reactor = new Reactor();
        var roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        var squad = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
        roster.applySoldierOutcome(Map.of(squad.memberIds().get(0), MarineSoldierStatus.KIA), 0f, 0f);
        roster.recruitToSquad(roster.reserveSquad().id(), 1); // so Fill Vacancies is a live, focusable order
        var vm = new BarracksViewModel(reactor, roster, () -> 1d);
        var model = vm.command();
        var props = new LinkedHashMap<String, Object>();
        props.put("command", model.props()); props.put("contextLabel", "PERSONNEL COMMAND");
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.BARRACKS, MarineOpsPageNav.ANY_SHIP,
                () -> {}, () -> {}, model::showOverview, () -> {}, () -> {}, () -> {});
        var previous = SvgAsset.setSource(path -> Files.readString(Path.of("mod").resolve(path)));
        try {
            var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                    List.of("data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/armory/armory-card-chip.mlx", "data/ui/components/company/shipboard-barracks.mlx"));
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
                assertEquals("barracks-fill-selected", model.guideTargetId());
                var target = instance.requireElement(model.guideTargetId()).box().borderBox();
                var panel = instance.requireElement("barracks-guide").box().borderBox();
                assertEquals(0, panel.intersect(target).width() * panel.intersect(target).height());
                document.requestFocus(instance.requireElement("barracks-fill-selected"), true);
                assertEquals("barracks-fill-selected", document.focusedElement().id(), "Keyboard focus can reach a highlighted region");
                assertThrows(IllegalArgumentException.class,
                        () -> document.requestFocus(instance.requireElement("barracks-equipment"), true));
                click(document, instance, "barracks-equipment");
                assertEquals("barracks-fill-selected", model.guideTargetId(), "Darkened controls do not activate");
                document.keyPressed(UiKey.ESCAPE, Set.of(), false);
                instance.flush(); document.advance(0f); guide.update();
                assertFalse(model.guideVisible());
                click(document, instance, "barracks-guide-help");
                guide.update();
                assertTrue(model.guideVisible());
                assertEquals("barracks-fill-selected", model.guideTargetId(), "Reopening resumes the lesson");
                action(model, "guideNext");
                action(model, "guideNext");
                instance.flush(); document.advance(0f); guide.update();
                click(document, instance, "guide-dismiss");
                click(document, instance, "barracks-select-all");
                assertTrue(model.selected().isEmpty(), "With every squad in view selected, the toggle deselects");

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
                assertTrue(((List<?>) value(model, "commands")).stream()
                        .noneMatch(row -> "command:independent".equals(((Map<?, ?>) row).get("id"))),
                        "The rail offers officers only; there is no acting-NCO command card");
                String[] loadout = {null};
                model.navigation(id -> loadout[0] = id, () -> { }, false);
                click(document, instance, "formation:" + squad.id() + ":loadout");
                assertEquals(squad.id(), loadout[0], "Loadout opens that squad's equipment workspace");
                assertEquals(1, model.selected().size());
                var officer = new MarineCaptain("Vale", null, Rank.LIEUTENANT, 0f);
                roster.add(officer); model.refresh(); instance.flush(); document.advance(0f);
                click(document, instance, "command:" + officer.id());
                assertEquals("officer-dossier panel console-section", value(model, "dossierClasses"));
                assertTrue(instance.requireElement("officer-traits").box().borderBox().y()
                        >= instance.requireElement("officer-specialty-FIELD_MEDICINE-rank-3").box().borderBox().bottom(),
                        "Service record must follow all specialty cards without overlapping");
                var trainingBox = instance.requireElement("officer-specialty-TACTICAL_COVER-rank-1").box().borderBox();
                assertEquals("officer-specialty-TACTICAL_COVER-rank-1", document.elementAt(trainingBox.x() + trainingBox.width() / 2f,
                        trainingBox.y() + trainingBox.height() / 2f).id());
                click(document, instance, "officer-specialty-TACTICAL_COVER-rank-1");
                assertEquals(1, officer.career().level(OfficerSpecialty.TACTICAL_COVER));
                assertEquals(0, officer.career().availablePoints(officer.rank()));
                click(document, instance, "officer-specialty-TACTICAL_COVER-rank-1");
                assertEquals(1, officer.career().level(OfficerSpecialty.TACTICAL_COVER), "Disabled training cannot spend twice");
                click(document, instance, "officer-formations");
                assertEquals("hidden", value(model, "dossierClasses"));
                click(document, instance, "barracks-officer-dossier");
                assertEquals("officer-dossier panel console-section", value(model, "dossierClasses"));
            }
        } finally { SvgAsset.setSource(previous); CompanyManagementGuide.install(new JSONObject("{\"lines\":[]}")); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> raiseTile(BarracksCommandModel model) {
        var tiles = (List<Map<String, Object>>) value(model, "squads");
        assertEquals(model.cards().size() + 1, tiles.size(), "The raise tile trails the last page");
        return tiles.get(tiles.size() - 1);
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
