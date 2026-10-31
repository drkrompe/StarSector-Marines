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

import static org.junit.jupiter.api.Assertions.*;

class OfficerInductionTest {
    private final MarineRoster roster = new MarineRoster();
    private final MarineCaptain sabine = new MarineCaptain("Morgan Hale", null, Rank.LIEUTENANT, 0f);

    private OfficerInduction begin() {
        roster.add(sabine);
        roster.beginOfficerInduction(sabine);
        return roster.officerInduction();
    }

    @ParameterizedTest @EnumSource(value = OfficerSpecialty.class, names = {"TACTICAL_COVER", "FIELD_MEDICINE"})
    void everySpecialtyAdvancesOnlyAfterTrainingAndAssignment(OfficerSpecialty specialty) throws Exception {
        OfficerInduction induction = begin();
        assertEquals(Step.INTRODUCTION, induction.step(roster));
        assertFalse(induction.finish(roster));
        induction.introduce(sabine);
        assertEquals(Step.SPECIALTY, induction.step(roster));
        assertTrue(sabine.train(specialty));
        assertFalse(sabine.train(specialty), "No extra tutorial point");
        induction = roundTrip(induction);
        assertEquals(sabine.id(), induction.foundingOfficerId());
        assertEquals(Step.FORMATION, induction.step(roster));
        MarineSquad squad = roster.createSquad();
        assertEquals(Step.FORMATION, induction.step(roster), "Empty formations cannot satisfy induction");
        roster.recruitToSquad(squad.id(), 12);
        assertEquals(Step.ASSIGNMENT, induction.step(roster));
        assertFalse(induction.finish(roster));
        roster.assignCaptainToSquad(sabine.id(), squad.id());
        assertEquals(Step.READY, induction.step(roster));
        assertTrue(induction.finish(roster));
        induction = roundTrip(induction);
        assertTrue(induction.complete());
        assertFalse(induction.finish(roster), "Final acknowledgement is once only");
        roster.removeById(sabine.id());
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
        action(model, "guideNext");
        assertTrue(roster.officerInductionRequired());
        model.resumeGuide();
        assertTrue(model.guideVisible());
        assertEquals("command:" + sabine.id(), model.guideTargetId());
        model.selectCommand(sabine.id());
        assertTrue(model.guideVisible(), "Officer lesson remains visible in dossier");
        assertEquals("officer-specialty-list", model.guideTargetId(), "the lesson points at the whole tree, not one rank");
        assertEquals(Step.SPECIALTY, roster.officerInduction().step(roster));
    }

    @Test void reopeningPreservesChoicesAndKeepsTheFoundingOfficer() {
        OfficerInduction induction = begin();
        induction.introduce(sabine);
        sabine.train(OfficerSpecialty.TACTICAL_COVER);
        roster.beginOfficerInduction(sabine);
        assertSame(induction, roster.officerInduction());
        assertEquals(Step.FORMATION, induction.step(roster));
        var other = new MarineCaptain("Another Officer", null, Rank.LIEUTENANT, 0f);
        roster.add(other);
        assertSame(sabine, induction.officer(roster), "Only the founding officer carries the lesson");
        assertTrue(sabine.founding());
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
