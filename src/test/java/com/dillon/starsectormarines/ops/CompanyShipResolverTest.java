package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic between a ship's effective stats and a generatable hull.
 *
 * <p>Reading the fleet itself needs a running game, so these exercise the part
 * that does not: whatever numbers a ship reports, the facts that come out have
 * to be ones a deck can be laid on. A company ship that throws is a company
 * with nowhere to live.
 */
class CompanyShipResolverTest {

    @Test
    @DisplayName("a hull's class and purpose come from the game's own words for them")
    void hullSizeAndDesignationBecomeClassAndRole() {
        CompanyShip ship = CompanyShipResolver.shipOf(
                "CAPITAL_SHIP", "Troop Transport", 60f, 400f, 250f, 0.5f);

        assertEquals(HullClass.CAPITAL, ship.hullClass());
        assertEquals(HullRole.TROOP_TRANSPORT, ship.role());
    }

    @Test
    @DisplayName("effective stats arrive as fractions and berths are whole people")
    void fractionalStatsRoundToWholePeopleAndCargo() {
        CompanyShip ship = CompanyShipResolver.shipOf(
                "CRUISER", "Assault Carrier", 47.4f, 233.6f, 79.5f, 0.4f);

        assertEquals(47, ship.minCrew());
        assertEquals(234, ship.maxCrew());
        assertEquals(80, ship.cargo());
    }

    @Test
    @DisplayName("a ship needing more hands than she holds is read at her maximum")
    void aMinimumAboveTheMaximumIsTakenAtTheMaximum() {
        // Crew-raising mods stack, and the ship still has to generate a deck.
        CompanyShip ship = CompanyShipResolver.shipOf(
                "CRUISER", "Freighter", 300f, 120f, 400f, 0.6f);

        assertEquals(120, ship.maxCrew());
        assertEquals(120, ship.minCrew());
        assertEquals(0, ship.maxCrew() - ship.minCrew(),
                "a ship that is all crew lifts nobody");
    }

    @Test
    @DisplayName("a hull whose proportions could not be read still has proportions")
    void anUnreadableAspectFallsBackToOrdinaryProportions() {
        CompanyShip ship = CompanyShipResolver.shipOf(
                "CRUISER", "Cruiser", 40f, 200f, 100f, 0f);

        assertTrue(ship.aspect() > 0f, "a hull has a positive beam");
    }

    @Test
    @DisplayName("a hull the game did not describe is still somewhere to live")
    void unknownDescriptionsDegradeRatherThanFail() {
        CompanyShip ship = CompanyShipResolver.shipOf(null, null, 0f, 0f, 0f, 0.9f);

        assertEquals(HullClass.DESTROYER, ship.hullClass());
        assertEquals(HullRole.WARSHIP, ship.role());
        assertEquals(0, ship.minCrew());
    }
}
