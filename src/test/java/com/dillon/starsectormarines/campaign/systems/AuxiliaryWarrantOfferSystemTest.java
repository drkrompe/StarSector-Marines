package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AuxiliaryWarrantProgress;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.marine.MilitaryWarrants;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The offer is a record in the correspondence, never a grant. */
class AuxiliaryWarrantOfferSystemTest {

    @Test
    void offersOnlyAfterTheArcSettlesAndGrantsNothing() throws Exception {
        CampaignState state = new CampaignState();
        AuxiliaryWarrantOfferSystem system = new AuxiliaryWarrantOfferSystem(faction -> RepLevel.FAVORABLE);

        system.tick(state, 1);
        assertEquals(AuxiliaryWarrantProgress.Stage.NONE, state.auxiliaryWarrant().stage(), "no Agreus record yet");

        state.agreusArc = new AgreusArcProgress();
        system.tick(state, 2);
        assertEquals(AuxiliaryWarrantProgress.Stage.NONE, state.auxiliaryWarrant().stage(), "arc not settled");

        Field settled = AgreusArcProgress.class.getDeclaredField("smallWarSettled");
        settled.setAccessible(true);
        settled.setBoolean(state.agreusArc, true);
        system.tick(state, 3);
        assertEquals(AuxiliaryWarrantProgress.Stage.OFFERED, state.auxiliaryWarrant().stage());
        assertEquals(3, state.auxiliaryWarrant().offeredDay());
        assertTrue(MilitaryWarrants.held(state).isEmpty());

        system.tick(state, 4);
        assertEquals(3, state.auxiliaryWarrant().offeredDay(), "offered once");
    }

    @Test
    void aCompanyBelowTheLicenceThresholdIsNotOffered() throws Exception {
        CampaignState state = new CampaignState();
        state.agreusArc = new AgreusArcProgress();
        Field settled = AgreusArcProgress.class.getDeclaredField("smallWarSettled");
        settled.setAccessible(true);
        settled.setBoolean(state.agreusArc, true);
        new AuxiliaryWarrantOfferSystem(faction -> RepLevel.HOSTILE).tick(state, 5);
        assertEquals(AuxiliaryWarrantProgress.Stage.NONE, state.auxiliaryWarrant().stage());
    }
}
