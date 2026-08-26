package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineRosterScriptTest {

    @Test
    void startingCompanyIsAvailableBeforeAnyCompanySurfaceOpens() {
        MarineRosterScript script = new MarineRosterScript();

        script.ensureStartingCompany();
        script.ensureStartingCompany();

        MarineRoster roster = script.roster();
        assertEquals(MarineSquad.CAPACITY, roster.activeSoldiers().size());
        assertEquals(1, roster.squads().stream().filter(squad -> !squad.reserve()).count());
        assertTrue(roster.squads().stream().anyMatch(MarineSquad::reserve));
    }
}
