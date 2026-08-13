package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.MoralChoiceRecorder;
import com.dillon.starsectormarines.campaign.MoralChoiceSource;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.marine.Trait;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptainTraitDriftSystemTest {

    @Test
    void grantsIdealistAtServiceAndWitnessThresholdExactlyOnce() {
        CampaignState state = new CampaignState();
        MarineCaptain captain = captain(0f, Status.ACTIVE);
        record(state, 1L, 15, 0, 20, 10);
        record(state, 2L, 15, 0, 20, 20);
        record(state, 3L, 15, 0, 20, 30);
        CaptainTraitDriftSystem system = system(captain);

        system.tick(state, 89);
        assertNull(captain.moralOutlookTrait());

        system.tick(state, 90);
        system.tick(state, 91);

        assertEquals(Trait.IDEALIST, captain.moralOutlookTrait());
        assertEquals(90, captain.moralOutlookDay());
        assertEquals(1, captain.commendations().size());
        assertEquals(1L, captain.traits().stream()
                .filter(trait -> trait == Trait.IDEALIST).count());
    }

    @Test
    void grantsCynicalToInjuredAndGarrisonedCaptains() {
        CampaignState state = new CampaignState();
        record(state, 1L, -5, 0, -10, 10);
        record(state, 2L, -5, 0, -10, 20);
        record(state, 3L, -5, 0, -10, 30);
        MarineCaptain injured = captain(0f, Status.INJURED);
        MarineCaptain garrisoned = captain(0f, Status.GARRISONED);
        CaptainTraitDriftSystem system = new CaptainTraitDriftSystem(
                () -> List.of(injured, garrisoned));

        system.tick(state, 90);

        assertEquals(Trait.CYNICAL, injured.moralOutlookTrait());
        assertEquals(Trait.CYNICAL, garrisoned.moralOutlookTrait());
    }

    @Test
    void excludesPreRecruitmentChoicesAndRequiresThreeWitnessedRows() {
        CampaignState state = new CampaignState();
        MarineCaptain captain = captain(50.5f, Status.ACTIVE);
        record(state, 1L, 15, 0, 20, 10);
        record(state, 2L, 15, 0, 20, 20);
        record(state, 3L, 15, 0, 20, 30);
        record(state, 4L, 15, 0, 20, 60);
        record(state, 5L, 15, 0, 20, 70);
        CaptainTraitDriftSystem system = system(captain);

        system.tick(state, 141);

        assertNull(captain.moralOutlookTrait());

        record(state, 6L, 15, 0, 20, 80);
        system.tick(state, 142);
        assertEquals(Trait.IDEALIST, captain.moralOutlookTrait());
    }

    @Test
    void institutionalismOnlyAndMixedEvidenceStayUnresolved() {
        CampaignState political = new CampaignState();
        MarineCaptain politicalCaptain = captain(0f, Status.ACTIVE);
        record(political, 1L, 0, 0, 0, 10, 40);
        record(political, 2L, 0, 0, 0, 20, 40);
        record(political, 3L, 0, 0, 0, 30, 40);

        system(politicalCaptain).tick(political, 90);

        assertNull(politicalCaptain.moralOutlookTrait());
        assertNull(CaptainTraitDriftSystem.classify(50, -20, 20));
        assertNull(CaptainTraitDriftSystem.classify(-50, 20, -20));
    }

    @Test
    void kiaCaptainsNeverDrift() {
        CampaignState state = new CampaignState();
        MarineCaptain captain = captain(0f, Status.KIA);
        record(state, 1L, 15, 0, 20, 10);
        record(state, 2L, 15, 0, 20, 20);
        record(state, 3L, 15, 0, 20, 30);

        assertFalse(CaptainTraitDriftSystem.evaluateCaptain(state, captain, 120));
        assertNull(captain.moralOutlookTrait());
    }

    @Test
    void classificationUsesInclusiveMirroredBoundaries() {
        assertEquals(Trait.IDEALIST,
                CaptainTraitDriftSystem.classify(10, 10, 25));
        assertEquals(Trait.CYNICAL,
                CaptainTraitDriftSystem.classify(-10, -10, -25));
        assertNull(CaptainTraitDriftSystem.classify(9, 9, 27));
        assertNull(CaptainTraitDriftSystem.classify(-9, -9, -27));
        assertNull(CaptainTraitDriftSystem.classify(30, 30, -16));
        assertNull(CaptainTraitDriftSystem.classify(-30, -30, 16));
    }

    private static CaptainTraitDriftSystem system(MarineCaptain captain) {
        return new CaptainTraitDriftSystem(() -> List.of(captain));
    }

    private static MarineCaptain captain(float createdAtDay, Status status) {
        MarineCaptain captain = new MarineCaptain(
                "Witness", null, Rank.PRIVATE, createdAtDay);
        captain.setStatus(status);
        return captain;
    }

    private static void record(CampaignState state, long id, int mercy,
                               int integrity, int stewardship, int happenedDay) {
        record(state, id, mercy, integrity, stewardship, happenedDay, 0);
    }

    private static void record(CampaignState state, long id, int mercy,
                               int integrity, int stewardship, int happenedDay,
                               int institutionalism) {
        assertEquals(MoralChoiceRecorder.Result.RECORDED,
                MoralChoiceRecorder.record(state, MoralChoiceSource.CIVILIAN_RESCUE_SAVED,
                        id, mercy, integrity, stewardship, institutionalism,
                        happenedDay, happenedDay));
    }
}
