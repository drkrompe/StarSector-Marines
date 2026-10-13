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

    private static ShipClass shielded(Shield type, float arc, float efficiency) {
        return new ShipClass(HullSize.CAPITAL, Role.COMBAT, 0, type, arc, efficiency);
    }

    @Test
    void noShieldIsTheIdentityAndAPerfectAllRoundScreenTakesTheMostOff() {
        assertEquals(1f, AirThreatWeights.shieldFactor(ShipClass.DEFAULT), 1e-6f);
        assertEquals(1f - AirThreatWeights.MAX_RELIEF, AirThreatWeights.shieldFactor(shielded(Shield.OMNI, 360f, 0.6f)), 1e-6f);
    }

    @Test
    void coverageIsTheArcOverTheFullCircle() {
        assertEquals(0.5f, AirThreatWeights.coverage(180f), 1e-6f);
        assertEquals(1f, AirThreatWeights.coverage(720f), 1e-6f);
        assertEquals(0f, AirThreatWeights.coverage(-5f), 1e-6f);
    }

    @Test
    void absorptionFallsAsEfficiencyWorsensAndIsFullAtPointEightOrBetter() {
        assertEquals(1f, AirThreatWeights.absorption(0.8f), 1e-6f);
        assertEquals(1f, AirThreatWeights.absorption(0.3f), 1e-6f);
        assertEquals(0.8f, AirThreatWeights.absorption(1f), 1e-6f);
        assertTrue(AirThreatWeights.absorption(1.4f) < AirThreatWeights.absorption(1f));
    }

    @Test
    void aWiderOrBetterShieldFearsAircraftLess() {
        float narrow = AirThreatWeights.shieldFactor(shielded(Shield.FRONT, 90f, 1f));
        float wide = AirThreatWeights.shieldFactor(shielded(Shield.FRONT, 270f, 1f));
        float efficient = AirThreatWeights.shieldFactor(shielded(Shield.FRONT, 90f, 0.6f));
        assertTrue(1f > narrow && narrow > wide);
        assertTrue(narrow > efficient);
        assertEquals(1f - 0.4f * 0.5f * 0.8f, AirThreatWeights.shieldFactor(shielded(Shield.FRONT, 180f, 1f)), 1e-6f);
    }

    @Test
    void aShieldScalesOnlyArmedCraftNotShipsOrDeclaredThreats() {
        ShipClass omni = shielded(Shield.OMNI, 360f, 0.6f);
        assertEquals(1.5f + 1f, AirThreatWeights.score(omni, 1.5f, 1f, 0), 1e-6f);
        assertEquals(AirThreatWeights.shieldFactor(omni) * AirThreatPicture.ARMED_CRAFT_WEIGHT,
                AirThreatWeights.score(omni, 0f, 0f, 1), 1e-6f);
    }

    @Test
    void aPhaseHullCountsAsAHalfUptimeScreenWhateverItsColumnsSay() {
        float expected = 1f - AirThreatWeights.MAX_RELIEF * AirThreatWeights.PHASE_UPTIME;
        assertEquals(expected, AirThreatWeights.shieldFactor(shielded(Shield.PHASE, 90f, 0f)), 1e-6f);
        assertEquals(expected, AirThreatWeights.shieldFactor(new ShipClass(HullSize.CAPITAL, Role.COMBAT, 0, Shield.PHASE)), 1e-6f);
    }

    @Test
    void classifyReadsTheShieldColumnsAndDefaultsBlankOnesByType() {
        ShipClass conquest = ShipClass.classify("CAPITAL_SHIP", "Battlecruiser", "", "", "OMNI", "90", "1.4");
        assertEquals(90f, conquest.shieldArc());
        assertEquals(1.4f, conquest.shieldEfficiency(), 1e-6f);
        assertEquals(new ShipClass(HullSize.CAPITAL, Role.COMBAT, 0, Shield.FRONT),
                ShipClass.classify("CAPITAL_SHIP", "", "", "", "FRONT", "", ""));
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
