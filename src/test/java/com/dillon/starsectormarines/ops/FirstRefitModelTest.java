package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.FirstRefit.Path;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

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
        model.claim();
        assertEquals(2, ((List<?>) model.row().get().get("paths")).size());
        assertEquals(true, model.row().get().get("disabled"));
        model.selectSquad(roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow().id());
        assertEquals(true, model.row().get().get("disabled"));
        model.selectPath(Path.FORWARD_SCREEN);
        assertEquals(12, ((List<?>) model.row().get().get("billets")).size());
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
        model.claim(); model.selectSquad(squad.id()); model.selectPath(Path.STEADY_LINE);
        assertEquals(SpecSheets.weapon(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.MILSPEC), model.sheet(true, 0));
        model.selectPath(Path.FORWARD_SCREEN);
        var current = roster.soldierById(roster.manningMemberIds(squad).get(0));
        assertEquals(SpecSheets.weapon(current.primaryDef(), current.primaryGrade()), model.sheet(false, 0));
        assertEquals(SpecSheets.weapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.MILSPEC), model.sheet(true, 0));
        assertNotNull(model.sheet(true, 3), "The proposed scout suit exposes its integral system");
        assertFalse(roster.firstRefit().complete());
        model.selectBillet(11);
        assertNotNull(model.sheet(false, 2), "The current launcher must be disclosed before removal");
        assertNull(model.sheet(true, 2));
        var special = ((List<Map<String, Object>>) model.row().get().get("proposedItems")).get(2);
        assertTrue(special.get("kind").toString().contains("REMOVE"));
        assertTrue(special.get("subtitle").toString().contains("no material refund"));
        roster.applySoldierOutcome(Map.of(roster.manningMemberIds(squad).get(11), MarineSoldierStatus.KIA), 10f, 5f);
        model.selectBillet(11);
        assertNull(model.sheet(false, 0));
        assertNotNull(model.sheet(true, 0));
        assertTrue(model.row().get().get("selectedName").toString().contains("plan only"));
        assertFalse(roster.firstRefit().complete());
    }

    @Test void lostCargoAtCommitDoesNotCompleteLessonAndShowsExactShortfall() {
        MarineRoster roster = new MarineRoster(); roster.ensureActiveSoldiers(12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        EquipmentIssueResources resources = new EquipmentIssueResources() {
            public EquipmentTemplateCost available() { return new EquipmentTemplateCost(999, 999, 999, 999); }
            public boolean spend(EquipmentTemplateCost cost) { return false; }
        };
        var model = new FirstRefitModel(new Reactor(), roster, resources, id -> { });
        model.claim(); model.selectSquad(roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow().id());
        model.selectPath(Path.STEADY_LINE); model.issue();
        assertFalse(roster.firstRefit().complete());
        assertTrue(model.row().get().get("status").toString().contains("Nothing has been spent"));
    }
}
