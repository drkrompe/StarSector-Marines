package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.control.ShipCommandService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mission's visit: the quiet clock that ends it, and the player gates it closes whatever the hull's side. */
class WarshipVisitTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    @Test
    void aVisitEndsOnlyAfterADwellOfQuietOnEnd() throws Exception {
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.MARINE, 0f, 0f, 0f);
        wolf.visit(10f);
        assertFalse(wolf.tickVisit(9f, false));
        assertFalse(wolf.tickVisit(5f, true), "a hostile hull over the battle starts the quiet again");
        assertEquals(0f, wolf.visitQuietSeconds(), 1e-6f);
        assertFalse(wolf.tickVisit(9.5f, false));
        assertTrue(wolf.tickVisit(0.5f, false));
    }

    @Test
    void aHullOnAVisitIsNobodysToCommandEvenOnThePlayersSide() throws Exception {
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.MARINE, 0f, 0f, 0f);
        assertEquals(ShipCommandService.Notice.NONE, ShipCommandService.eligibility(wolf, null, Faction.MARINE));
        assertTrue(WarshipPilot.eligible(wolf, Faction.MARINE));
        wolf.visit(10f);
        assertTrue(wolf.missionOwned());
        assertEquals(ShipCommandService.Notice.UNAVAILABLE, ShipCommandService.eligibility(wolf, null, Faction.MARINE));
        assertFalse(WarshipPilot.eligible(wolf, Faction.MARINE));
    }
}
