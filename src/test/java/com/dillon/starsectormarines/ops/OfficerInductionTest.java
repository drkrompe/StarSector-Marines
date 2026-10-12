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
    private final MarineCaptain mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0f);

    private OfficerInduction begin() {
        roster.add(mira);
        roster.beginOfficerInduction(mira);
        return roster.officerInduction();
    }

    @ParameterizedTest @EnumSource(OfficerSpecialty.class)
    void everySpecialtyAdvancesOnlyAfterTrainingAndAssignment(OfficerSpecialty specialty) throws Exception {
        OfficerInduction induction = begin();
        assertEquals(Step.INTRODUCTION, induction.step(roster));
        assertFalse(induction.finish(roster));
        induction.introduce(mira);
        assertEquals(Step.SPECIALTY, induction.step(roster));
        assertTrue(mira.train(specialty));
        assertFalse(mira.train(specialty), "No extra tutorial point");
        induction = roundTrip(induction);
        assertEquals(mira.id(), induction.miraId());
        assertEquals(Step.FORMATION, induction.step(roster));
        MarineSquad squad = roster.createSquad();
        assertEquals(Step.FORMATION, induction.step(roster), "Empty formations cannot satisfy induction");
        roster.recruitToSquad(squad.id(), 12);
        assertEquals(Step.ASSIGNMENT, induction.step(roster));
        assertFalse(induction.finish(roster));
        roster.assignCaptainToSquad(mira.id(), squad.id());
        assertEquals(Step.READY, induction.step(roster));
        assertTrue(induction.finish(roster));
        induction = roundTrip(induction);
        assertTrue(induction.complete());
        assertFalse(induction.finish(roster), "Final acknowledgement is once only");
        roster.removeById(mira.id());
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
        assertEquals("command:" + mira.id(), model.guideTargetId());
        model.selectCommand(mira.id());
        assertTrue(model.guideVisible(), "Officer lesson remains visible in dossier");
        assertEquals("officer-specialty-TACTICAL_COVER-train", model.guideTargetId());
        assertEquals(Step.SPECIALTY, roster.officerInduction().step(roster));
    }

    @Test void reopeningPreservesChoicesAndLostMiraIsNotRecreated() {
        OfficerInduction induction = begin();
        induction.introduce(mira);
        mira.train(OfficerSpecialty.RECOVERY_PLANNING);
        roster.beginOfficerInduction(mira);
        assertSame(induction, roster.officerInduction());
        assertEquals(Step.FORMATION, induction.step(roster));
        mira.setStatus(Status.KIA);
        assertNull(induction.officer(roster));
        assertFalse(induction.finish(roster));
        var replacement = new MarineCaptain("Replacement", null, Rank.LIEUTENANT, 0f);
        roster.add(replacement);
        assertSame(replacement, induction.officer(roster));
        assertEquals(Step.SPECIALTY, induction.step(roster));
        assertEquals(Status.KIA, mira.status());
    }

    @Test void portraitUpgradePreservesCustomizedOfficersAndLegacyExemption() {
        mira.setPortraitSprite("graphics/portraits/portrait_mercenary01.png");
        roster.add(mira);
        var customized = new MarineCaptain("Mira Hale", "my-custom-portrait.png", Rank.CAPTAIN, 0f);
        var other = new MarineCaptain("Another Officer", "graphics/portraits/portrait_mercenary01.png", Rank.CAPTAIN, 0f);
        roster.add(customized);
        roster.add(other);
        OfficerInduction.migrateMiraPortrait(roster);
        assertEquals(OfficerInduction.MIRA_PORTRAIT, mira.portraitSprite());
        assertEquals("my-custom-portrait.png", customized.portraitSprite());
        assertEquals("graphics/portraits/portrait_mercenary01.png", other.portraitSprite());
        assertFalse(roster.officerInductionRequired());
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
