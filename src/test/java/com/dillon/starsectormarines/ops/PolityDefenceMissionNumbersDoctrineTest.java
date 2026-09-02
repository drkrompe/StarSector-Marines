package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.polity.PolityDoctrine;
import com.dillon.starsectormarines.campaign.polity.PolityDoctrineLedger;
import com.dillon.starsectormarines.campaign.systems.VanillaRaidGarrisonSystem.RaidThreat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The polity's numbers axis reaching a battle. The multiplier is carried by the
 * mission ({@code PolityDefenceMissionFactory} pins that end); this pins the one
 * caller that decides what it is — the colony's own defence rows, and nothing else.
 */
class PolityDefenceMissionNumbersDoctrineTest {

    @Test
    void aPolityThatSpentNoPointsFieldsItsOwnMarketsStrength() {
        CampaignState state = new CampaignState();

        assertEquals(1f, mission(state).alliedGarrisonStrengthMult);
    }

    @Test
    void pointsSpentOnNumbersRideEveryDefenceRow() {
        CampaignState state = new CampaignState();
        PolityDoctrineLedger.write(state, PolityDoctrine.of(0, 2, 1));

        assertEquals(PolityDoctrine.of(0, 2, 1).numbersMultiplier(),
                mission(state).alliedGarrisonStrengthMult);
        assertEquals(1.5f, mission(state).alliedGarrisonStrengthMult);
    }

    /** An overspent save is clamped on the way through, like every other doctrine read. */
    @Test
    void anOverspentSaveStillProducesALegalMultiplier() {
        CampaignState state = new CampaignState();
        state.polityDoctrineNumbers = 99;

        assertEquals(1.5f, mission(state).alliedGarrisonStrengthMult);
    }

    private static Mission mission(CampaignState state) {
        List<Mission> missions = MarineOpsContext.polityDefenceMissions(state,
                List.of(new RaidThreat(88L, 3, 5, 60f)), 3, "Jangala", "player");
        assertEquals(1, missions.size());
        return missions.get(0);
    }
}
