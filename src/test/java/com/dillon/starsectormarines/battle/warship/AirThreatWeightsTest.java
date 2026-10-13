package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.ShipClass.HullSize;
import com.dillon.starsectormarines.battle.warship.ShipClass.Role;
import com.dillon.starsectormarines.battle.warship.ShipClass.Shield;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The class table scales what each threat is worth; the combat capital is the row the base weights were tuned on. */
class AirThreatWeightsTest {

    private static ShipClass of(HullSize size, Role role) {
        return new ShipClass(size, role, 0, Shield.NONE);
    }

    @Test
    void aCombatCapitalScoresTheBaseWeights() {
        ShipClass capital = of(HullSize.CAPITAL, Role.COMBAT);
        assertEquals(ShipClass.DEFAULT, capital);
        assertEquals(1.5f + 1f + 1f, AirThreatWeights.score(capital, 1.5f, 1f, 2), 1e-6f);
    }

    @Test
    void aFrigateFearsOneFighterEnoughToEnterTheAirFightWhereACapitalDoesNot() {
        assertTrue(AirThreatWeights.score(of(HullSize.FRIGATE, Role.COMBAT), 0f, 0f, 1) >= WarshipDoctrine.AIR_ENTER);
        assertTrue(AirThreatWeights.score(of(HullSize.CAPITAL, Role.COMBAT), 0f, 0f, 1) < WarshipDoctrine.AIR_ENTER);
    }

    @Test
    void fighterWeightFallsAsTheHullGrows() {
        float frigate = AirThreatWeights.score(of(HullSize.FRIGATE, Role.COMBAT), 0f, 0f, 1);
        float destroyer = AirThreatWeights.score(of(HullSize.DESTROYER, Role.COMBAT), 0f, 0f, 1);
        float cruiser = AirThreatWeights.score(of(HullSize.CRUISER, Role.COMBAT), 0f, 0f, 1);
        float capital = AirThreatWeights.score(of(HullSize.CAPITAL, Role.COMBAT), 0f, 0f, 1);
        assertTrue(frigate > destroyer && destroyer > cruiser && cruiser > capital);
    }

    @Test
    void noShieldFearsAircraftMoreThanFrontMoreThanOmni() {
        float none = AirThreatWeights.score(new ShipClass(HullSize.CRUISER, Role.COMBAT, 0, Shield.NONE), 0f, 0f, 2);
        float front = AirThreatWeights.score(new ShipClass(HullSize.CRUISER, Role.COMBAT, 0, Shield.FRONT), 0f, 0f, 2);
        float omni = AirThreatWeights.score(new ShipClass(HullSize.CRUISER, Role.COMBAT, 0, Shield.OMNI), 0f, 0f, 2);
        assertTrue(none > front && front > omni, none + " > " + front + " > " + omni);
        assertEquals(none * AirThreatWeights.FRONT_SHIELD, front, 1e-6f);
        assertEquals(none * AirThreatWeights.OMNI_SHIELD, omni, 1e-6f);
    }

    @Test
    void aShieldScalesOnlyArmedCraftNotShipsOrDeclaredThreats() {
        ShipClass omni = new ShipClass(HullSize.CAPITAL, Role.COMBAT, 0, Shield.OMNI);
        assertEquals(1.5f + 1f, AirThreatWeights.score(omni, 1.5f, 1f, 0), 1e-6f);
    }

    @Test
    void aPhaseHullIsJudgedAsAFrontShield() {
        assertEquals(AirThreatWeights.score(new ShipClass(HullSize.CAPITAL, Role.COMBAT, 0, Shield.FRONT), 0f, 0f, 2),
                AirThreatWeights.score(new ShipClass(HullSize.CAPITAL, Role.COMBAT, 0, Shield.PHASE), 0f, 0f, 2), 1e-6f);
    }

    @Test
    void aCarrierCountsFightersHighAtAnySizeAndShipsAndDeclaredThreatsAsEveryoneDoes() {
        ShipClass carrier = of(HullSize.CAPITAL, Role.CARRIER);
        assertEquals(1f, AirThreatWeights.score(carrier, 0f, 0f, 1), 1e-6f);
        assertEquals(0.75f, AirThreatWeights.score(carrier, 0.25f, 0.5f, 0), 1e-6f);
        assertEquals(AirThreatWeights.score(of(HullSize.FRIGATE, Role.CARRIER), 0f, 0f, 1),
                AirThreatWeights.score(carrier, 0f, 0f, 1), 1e-6f);
    }

    @Test
    void theSwarmCapAppliesBeforeTheClassMultiplier() {
        float frigate = AirThreatWeights.score(of(HullSize.FRIGATE, Role.COMBAT), 0f, 0f, 50);
        assertEquals(AirThreatPicture.ARMED_CRAFT_CAP * 2f, frigate, 1e-6f);
        assertEquals(AirThreatPicture.ARMED_CRAFT_CAP,
                AirThreatWeights.score(of(HullSize.CAPITAL, Role.COMBAT), 0f, 0f, 50), 1e-6f);
    }

    @Test
    void aSupportShipSitsBetweenAGunShipAndACarrierOnFighters() {
        ShipClass support = of(HullSize.CRUISER, Role.SUPPORT);
        assertEquals(0.75f, AirThreatWeights.score(support, 0f, 0f, 1), 1e-6f);
    }
}
