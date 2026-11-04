package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.ArmorIssueResolver;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.FirstRefit.Path;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.EquipmentTestFixtures;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import java.nio.file.Files;
import java.io.File;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FirstRefitModelTest {
    private HeadlessStrings strings;
    @BeforeEach void strings() throws Exception { strings = new HeadlessStrings(new File("mod").toPath()); }
    @AfterEach void closeStrings() { strings.close(); }

    @Test void lessonRequiresRealSelectionAndShowsExactlyTwoChoicesThenActualIssue() {
        MarineRoster roster = new MarineRoster(); roster.ensureActiveSoldiers(12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var model = new FirstRefitModel(new Reactor(), roster, EquipmentIssueResources.UNLIMITED, () -> { });
        assertEquals(true, model.row().get().get("chooseDisabled"));
        assertEquals(2, ((List<?>) model.row().get().get("paths")).size());
        assertEquals(true, model.row().get().get("chooseDisabled"));
        model.selectPath(Path.FORWARD_SCREEN);
        assertEquals(3, ((List<?>) model.inspection().get("teams")).size());
        assertFalse(roster.firstRefit().claimed(), "Comparing learns nothing");
        assertEquals(false, model.row().get().get("chooseDisabled"));
        model.choose();
        assertTrue(roster.firstRefit().complete());
    }

    @Test void takingAPackageFitsEveryLineSquadAtHome() {
        var roster = new MarineRoster();
        var first = roster.createSquad(); roster.recruitToSquad(first.id(), 12);
        var second = roster.createSquad(); roster.recruitToSquad(second.id(), 12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var model = new FirstRefitModel(new Reactor(), roster, EquipmentIssueResources.UNLIMITED, () -> { });
        model.selectPath(Path.STEADY_LINE);
        model.choose();
        assertTrue(roster.firstRefit().complete());
        var issued = FirstRefit.weapons(Path.STEADY_LINE);
        for (var squad : List.of(first, second)) {
            var members = roster.manningMemberIds(squad);
            for (int i = 0; i < members.size(); i++)
                assertEquals(issued.get(i).primaryId(), roster.soldierById(members.get(i)).primaryDef().id);
        }
    }

    @SuppressWarnings("unchecked")
    @Test void inspectionTracksTheSelectedBilletAndPathWithoutIssuingEquipment() {
        var roster = new MarineRoster();
        var squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var model = new FirstRefitModel(new Reactor(), roster, EquipmentIssueResources.UNLIMITED, () -> { });
        model.selectPath(Path.STEADY_LINE);
        var steady = FirstRefit.weapons(Path.STEADY_LINE).get(0);
        assertEquals(SpecSheets.weapon(steady.primaryDef(), steady.grade()), model.sheet(true, 0));
        model.selectPath(Path.FORWARD_SCREEN);
        var current = roster.soldierById(roster.manningMemberIds(squad).get(0));
        assertEquals(SpecSheets.weapon(current.primaryDef(), current.primaryGrade()), model.sheet(false, 0));
        var forward = FirstRefit.weapons(Path.FORWARD_SCREEN).get(0);
        assertEquals(SpecSheets.weapon(forward.primaryDef(), forward.grade()), model.sheet(true, 0));
        String proposedSuit = ArmorIssueResolver.issueSheet(
                SquadEquipmentDoctrines.armorPlanById(Path.FORWARD_SCREEN.armor)).issueId(0);
        assertEquals(IntegralSystemCopy.carried(MarineArmorCatalogRegistry.require(proposedSuit)), model.sheet(true, 3) != null,
                "The proposed suit exposes its integral system exactly when it carries one");
        assertFalse(roster.firstRefit().complete());
        EquipmentTestFixtures.issueLauncher(roster.soldierById(roster.manningMemberIds(squad).get(11)));
        model.selectBillet(11);
        assertNotNull(model.sheet(false, 2), "The current launcher must be disclosed before removal");
        assertNull(model.sheet(true, 2));
        // The squad view's special line still names the launcher being taken away.
        assertEquals(model.sheet(false, 2),
                LoadoutInspection.partSheet(model.inspection(), 11, LoadoutInspection.SPECIAL));
        roster.applySoldierOutcome(Map.of(roster.manningMemberIds(squad).get(11), MarineSoldierStatus.KIA), 10f, 5f);
        model.selectBillet(11);
        assertNull(model.sheet(false, 0));
        assertNotNull(model.sheet(true, 0));
        assertTrue(model.row().get().get("selectedName").toString().contains("plan only"));
        assertFalse(roster.firstRefit().complete());
    }

    @Test void choosingFitsTheSquadAndEndsTheOffer() {
        MarineRoster roster = new MarineRoster(); roster.ensureActiveSoldiers(12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        int[] left = {0};
        var model = new FirstRefitModel(new Reactor(), roster, EquipmentIssueResources.UNLIMITED, () -> left[0]++);
        String squad = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow().id();
        model.selectPath(Path.STEADY_LINE);
        assertEquals(0, left[0], "Comparing does not leave");
        model.choose();
        assertEquals(squad, roster.firstRefit().issuedSquadId());
        assertEquals(1, left[0]);
    }

    @Test void aClaimFromATwoStepSaveOnlyNeedsTheFitting() {
        MarineRoster roster = new MarineRoster(); roster.ensureActiveSoldiers(12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.firstRefit().claim(roster, Path.FORWARD_SCREEN);
        var model = new FirstRefitModel(new Reactor(), roster, EquipmentIssueResources.UNLIMITED, () -> { });
        model.choose();
        assertTrue(roster.firstRefit().complete());
        assertEquals(Path.FORWARD_SCREEN, roster.firstRefit().chosenPath());
    }

    @Test void fittingDoesNotRequireAPayment() {
        MarineRoster roster = new MarineRoster(); roster.ensureActiveSoldiers(12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        EquipmentIssueResources resources = new EquipmentIssueResources() {
            public EquipmentTemplateCost available() { throw new AssertionError("Fitting must not read cargo"); }
            public boolean spend(EquipmentTemplateCost cost) { throw new AssertionError("Fitting must not spend cargo"); }
        };
        var model = new FirstRefitModel(new Reactor(), roster, resources, () -> { });
        model.selectPath(Path.STEADY_LINE); model.choose();
        assertTrue(roster.firstRefit().complete());
    }

}
