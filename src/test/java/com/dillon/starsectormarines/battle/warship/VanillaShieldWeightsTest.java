package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The factor read from real hull rows orders hulls the way their shields do. Skipped where the game is not installed. */
class VanillaShieldWeightsTest {

    @BeforeEach
    void install() {
        InstalledVanillaData.install();
    }

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
    }

    private static float factor(String id) throws Exception {
        List<Map<String, String>> rows = VanillaData.csv("data/hulls/ship_data.csv");
        for (Map<String, String> row : rows) {
            if (!id.equals(row.get("id"))) continue;
            ShipClass c = ShipClass.classify(row.get("designation"), row.get("designation"), row.get("hints"),
                    row.get("fighter bays"), row.get("shield type"), row.get("shield arc"), row.get("shield efficiency"));
            return AirThreatWeights.shieldFactor(c);
        }
        throw new AssertionError("no hull " + id);
    }

    @Test
    void anAllRoundEfficientShieldFearsAircraftLessThanAHalfCircleOne() throws Exception {
        float onslaught;
        try {
            onslaught = factor("onslaught");
        } catch (Exception | AssertionError e) {
            Assumptions.abort("game data not installed: " + e);
            return;
        }
        float paragon = factor("paragon");
        float conquest = factor("conquest");
        assertTrue(paragon < onslaught, paragon + " < " + onslaught);
        assertTrue(onslaught < conquest, onslaught + " < " + conquest + " (90 degree, poor-efficiency shield)");
        assertTrue(conquest < 1f);
    }
}
