package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.FirstRefit.Path;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.EquipmentTestFixtures;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import java.nio.file.Files;
import java.io.File;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FirstRefitModelTest {
    @Test void lessonRequiresRealSelectionAndShowsExactlyTwoChoicesThenActualIssue() {
        MarineRoster roster = new MarineRoster(); roster.ensureActiveSoldiers(12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var model = new FirstRefitModel(new Reactor(), roster, EquipmentIssueResources.UNLIMITED, id -> { });
        assertEquals(true, model.row().get().get("disabled"));
        assertEquals(2, ((List<?>) model.row().get().get("paths")).size());
        assertEquals(true, model.row().get().get("disabled"));
        model.selectSquad(roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow().id());
        assertEquals(true, model.row().get().get("disabled"));
        model.selectPath(Path.FORWARD_SCREEN);
        assertEquals(12, ((List<?>) model.row().get().get("billets")).size());
        assertEquals(true, model.row().get().get("disabled"));
        model.issue();
        assertFalse(roster.firstRefit().complete());
        model.claim();
        assertEquals(false, model.row().get().get("disabled"));
        model.issue();
        assertTrue(roster.firstRefit().complete());
        assertEquals("4 / 4  KIT ISSUED", model.row().get().get("progress"));
    }

    @SuppressWarnings("unchecked")
    @Test void inspectionTracksTheSelectedBilletAndPathWithoutIssuingEquipment() {
        var roster = new MarineRoster();
        var squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var model = new FirstRefitModel(new Reactor(), roster, EquipmentIssueResources.UNLIMITED, id -> { });
        model.selectSquad(squad.id()); model.selectPath(Path.STEADY_LINE);
        assertEquals(SpecSheets.weapon(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.MILSPEC), model.sheet(true, 0));
        model.selectPath(Path.FORWARD_SCREEN);
        var current = roster.soldierById(roster.manningMemberIds(squad).get(0));
        assertEquals(SpecSheets.weapon(current.primaryDef(), current.primaryGrade()), model.sheet(false, 0));
        assertEquals(SpecSheets.weapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.MILSPEC), model.sheet(true, 0));
        assertNotNull(model.sheet(true, 3), "The proposed scout suit exposes its integral system");
        assertFalse(roster.firstRefit().complete());
        EquipmentTestFixtures.issueLauncher(roster.soldierById(roster.manningMemberIds(squad).get(11)));
        model.selectBillet(11);
        assertNotNull(model.sheet(false, 2), "The current launcher must be disclosed before removal");
        assertNull(model.sheet(true, 2));
        var special = ((List<Map<String, Object>>) model.row().get().get("proposedItems")).get(2);
        assertTrue(special.get("kind").toString().contains("REMOVE"));
        assertTrue(special.get("subtitle").toString().contains("for free"));
        roster.applySoldierOutcome(Map.of(roster.manningMemberIds(squad).get(11), MarineSoldierStatus.KIA), 10f, 5f);
        model.selectBillet(11);
        assertNull(model.sheet(false, 0));
        assertNotNull(model.sheet(true, 0));
        assertTrue(model.row().get().get("selectedName").toString().contains("plan only"));
        assertFalse(roster.firstRefit().complete());
    }

    @Test void fittingDoesNotRequireAPayment() {
        MarineRoster roster = new MarineRoster(); roster.ensureActiveSoldiers(12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        EquipmentIssueResources resources = new EquipmentIssueResources() {
            public EquipmentTemplateCost available() { throw new AssertionError("Fitting must not read cargo"); }
            public boolean spend(EquipmentTemplateCost cost) { throw new AssertionError("Fitting must not spend cargo"); }
        };
        var model = new FirstRefitModel(new Reactor(), roster, resources, id -> { });
        model.selectSquad(roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow().id());
        model.selectPath(Path.STEADY_LINE); model.claim(); model.issue();
        assertTrue(roster.firstRefit().complete());
    }
    @Test void machineShopButtonOpensFittingWithoutWorkshopRestoration() throws Exception {
        var context = new MarineOpsContext(null);
        context.goTo(ScreenId.SHIP_VIEW);
        var props = new LinkedHashMap<String, Object>();
        props.put("equipmentAction", ShipViewScreen.equipmentAction(context));
        var survey = ShipConditionModel.workshopOnly();
        survey.put("classes", "hidden");
        props.put("survey", survey);
        props.put("workshop", WorkshopRestorationModel.Row.hidden());
        props.put("roomTitle", "COMPANY SHIP / WALK THE SHIP");
        props.put("roomCopy", "Select a room to inspect. Right-drag to pan; wheel to zoom.");
        props.put("contextLabel", "COMPANY SHIP / CONDITION");
        Runnable noop = () -> {};
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_VIEW, MarineOpsPageNav.ANY_SHIP,
                noop, noop, noop, noop, noop, noop);
        var mod = new File("mod").toPath();
        var loader = new MarkupLoader(file -> Files.readString(mod.resolve(file)), List.of(
                "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/company/ship-view.mlx"));
        try (var markup = loader.reloadAndBuild(new Reactor(), "ship-view", props)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            document.layout(1920, 1080);
            var box = markup.requireElement("machine-shop-equipment").box().borderBox();
            assertTrue(box.width() > 300 && box.right() <= 1920 && box.bottom() <= 1080);
            document.pointerDown(box.x() + box.width()/2, box.y() + box.height()/2);
            document.pointerUp(box.x() + box.width()/2, box.y() + box.height()/2);
            assertEquals(ScreenId.FLEET_ARMORY, context.getCurrentScreen());
        }
    }

}
