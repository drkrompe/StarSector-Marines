package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.FirstRefit.Path;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.util.List;
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
