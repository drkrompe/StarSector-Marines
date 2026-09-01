package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineRosterScriptTest {

    @Test
    void startingCompanyHasOnlyStructuralReserveUntilPlayerFoundsAssets() {
        MarineRosterScript script = new MarineRosterScript();

        script.ensureStartingCompany();
        script.ensureStartingCompany();

        MarineRoster roster = script.roster();
        assertEquals(0, roster.activeSoldiers().size());
        assertEquals(0, roster.squads().stream().filter(squad -> !squad.reserve()).count());
        assertTrue(roster.squads().stream().anyMatch(MarineSquad::reserve));
        assertEquals(0, roster.mechBay().activeDeployment().size());
    }
}
