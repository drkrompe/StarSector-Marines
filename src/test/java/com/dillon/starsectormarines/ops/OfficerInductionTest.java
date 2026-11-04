package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.OfficerInduction;
import com.dillon.starsectormarines.marine.OfficerInduction.Step;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OfficerInductionTest {
    private final MarineRoster roster = new MarineRoster();
    private final MarineCaptain morgan = new MarineCaptain("Morgan Hale", null, Rank.LIEUTENANT, 0f);

    private OfficerInduction begin() {
        roster.add(morgan);
        roster.beginOfficerInduction(morgan);
        return roster.officerInduction();
    }

    @ParameterizedTest @EnumSource(value = OfficerSpecialty.class, names = {"TACTICAL_COVER", "FIELD_MEDICINE"})
    void everySpecialtyAdvancesOnlyAfterTrainingAndAssignment(OfficerSpecialty specialty) throws Exception {
        OfficerInduction induction = begin();
        assertEquals(Step.INTRODUCTION, induction.step(roster));
        assertFalse(induction.finish(roster));
        induction.introduce(morgan);
        assertEquals(Step.SPECIALTY, induction.step(roster));
        assertTrue(morgan.train(specialty));
        assertFalse(morgan.train(specialty), "No extra tutorial point");
        induction = roundTrip(induction);
        assertEquals(morgan.id(), induction.foundingOfficerId());
        assertEquals(Step.FORMATION, induction.step(roster));
        MarineSquad squad = roster.createSquad();
        assertEquals(Step.FORMATION, induction.step(roster), "Empty formations cannot satisfy induction");
        roster.recruitToSquad(squad.id(), 12);
        assertEquals(Step.ASSIGNMENT, induction.step(roster));
        assertFalse(induction.finish(roster));
        roster.assignCaptainToSquad(morgan.id(), squad.id());
        assertEquals(Step.READY, induction.step(roster));
        assertTrue(induction.finish(roster));
        induction = roundTrip(induction);
        assertTrue(induction.complete());
        assertFalse(induction.finish(roster), "Final acknowledgement is once only");
        roster.removeById(morgan.id());
        assertEquals(Step.COMPLETE, induction.step(roster), "Later losses never re-lock a veteran company");
    }

    @Test void requiredLessonCannotBeDismissedOrUnlockAnyOrdinaryLaunchAndOldCompaniesStayExempt() {
        assertFalse(MissionLaunch.requiresOfficerInduction(roster, MissionSource.STORY));
        begin();
        var model = new BarracksViewModel(new Reactor(), roster, () -> 0d).command();
        model.dismissGuide();
        assertTrue(model.guideVisible(), "Required induction cannot be dismissed");
        for (MissionSource source : MissionSource.values()) {
            assertEquals(!source.isDebug(), MissionLaunch.requiresOfficerInduction(roster, source));
            if (!source.isDebug()) assertThrows(IllegalStateException.class,
                    () -> MissionLaunch.requireOfficerInductionComplete(roster, source));
        }
        assertNull(model.guideCallout().action(), "Ready shows only where pressing it does something");
        assertNull(model.guideCallout().corner(), "Required induction has no set-aside");
        assertTrue(roster.officerInductionRequired());
        model.resumeGuide();
        assertTrue(model.guideVisible());
        assertEquals("command:" + morgan.id(), model.guideTargetId());
        model.selectCommand(morgan.id());
        assertTrue(model.guideVisible(), "Officer lesson remains visible in dossier");
        assertEquals("officer-specialty-list", model.guideTargetId(), "the lesson points at the whole tree, not one rank");
        assertEquals(Step.SPECIALTY, roster.officerInduction().step(roster));
        assertNull(model.guideCallout().action());
    }

    @Test void onlyReadyHasAReadyButtonAndLeavingTheDossierIsAllFormations() {
        OfficerInduction induction = begin();
        var model = new BarracksViewModel(new Reactor(), roster, () -> 0d).command();
        model.selectCommand(morgan.id());
        morgan.train(OfficerSpecialty.TACTICAL_COVER);
        assertEquals(Step.FORMATION, induction.step(roster));
        assertEquals("command:all", model.guideTargetId(), "The rail's first entry is the way back to the board");
        assertNull(model.guideCallout().action());
        model.selectCommand("all");
        assertEquals("barracks-raise-squad", model.guideTargetId());

        var squad = roster.createSquad();
        roster.recruitToSquad(squad.id(), 12);
        roster.assignCaptainToSquads(morgan.id(), Set.of(squad.id()));
        assertEquals(Step.READY, induction.step(roster));
        var ready = model.guideCallout();
        assertNull(ready.target(), "Ready is on the callout; no other control is the next step");
        assertTrue(ready.regions().isEmpty());
        ready.action().action().run();
        assertTrue(induction.complete());
        assertNull(model.guideCallout());
    }

    @Test void reopeningPreservesChoicesAndKeepsTheFoundingOfficer() {
        OfficerInduction induction = begin();
        induction.introduce(morgan);
        morgan.train(OfficerSpecialty.TACTICAL_COVER);
        roster.beginOfficerInduction(morgan);
        assertSame(induction, roster.officerInduction());
        assertEquals(Step.FORMATION, induction.step(roster));
        var other = new MarineCaptain("Another Officer", null, Rank.LIEUTENANT, 0f);
        roster.add(other);
        assertSame(morgan, induction.officer(roster), "Only the founding officer carries the lesson");
        assertTrue(morgan.founding());
        assertFalse(other.founding());
    }

    private static void action(BarracksCommandModel model, String key) {
        ((Runnable) model.props().get(key)).run();
    }
    private static OfficerInduction roundTrip(OfficerInduction value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(value); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (OfficerInduction) in.readObject();
        }
    }
}
