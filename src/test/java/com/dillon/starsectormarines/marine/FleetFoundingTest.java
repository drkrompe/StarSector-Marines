package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.dillon.starsectormarines.campaign.refit.FleetFacilities;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A company on fleet facilities founds with no ship to choose (`fleet-facilities.md`). */
class FleetFoundingTest {
    private final Map<String, Object> saved = new HashMap<>();

    @BeforeEach void install() { CampaignBackend.useSavedData(() -> saved); }

    @AfterEach void restore() { CampaignBackend.useSavedData(() -> null); }

    private OfficerInduction.Step firstStep() {
        MarineRoster roster = new MarineRoster();
        MarineCaptain morgan = new MarineCaptain("Morgan Hale", null, Rank.LIEUTENANT, 0f);
        roster.add(morgan);
        roster.beginCompanyPreparation(morgan);
        return roster.officerInduction().step(roster);
    }

    @Test void aFleetCompanyFoundsWithoutAShipStep() {
        assertTrue(FleetFacilities.inUse());
        assertNotEquals(OfficerInduction.Step.SHIP, firstStep());
    }

    @Test void theSecondWindStartStillChoosesItsShip() {
        saved.put(CerberusCompanyStart.SHIP_KEY, "second-wind");
        assertFalse(FleetFacilities.inUse());
        assertEquals(OfficerInduction.Step.SHIP, firstStep());
    }
}
