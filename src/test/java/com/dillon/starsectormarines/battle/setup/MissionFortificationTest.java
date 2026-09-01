package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification.Strength;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tier ladder and the fortification ladder climb together.
 *
 * <p>What is pinned is the shape rather than any rung: a bigger operation may
 * be asked to take a harder place, never a softer one, and the top of one
 * ladder reaches the top of the other, so a full-strength siege is not capped
 * below the citadel it was raised for.
 */
class MissionFortificationTest {

    @Test
    void aBiggerOperationMayFaceAHarderPlace() {
        Strength last = null;
        for (OperationTier tier : OperationTier.values()) {
            Strength ceiling = MissionFortification.demand(tier, RiskLevel.MEDIUM).ceiling();
            if (last != null) {
                assertTrue(ceiling.ordinal() >= last.ordinal(),
                        tier + " may face " + ceiling + ", softer than the tier below's " + last);
            }
            last = ceiling;
        }
        assertEquals(Strength.PICKET,
                MissionFortification.demand(OperationTier.FIRST_CONTRACT, RiskLevel.MEDIUM).ceiling(),
                "the opening ladder is not the place for anything but a screen");
        assertEquals(Strength.CITADEL,
                MissionFortification.demand(OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM).ceiling(),
                "the operation conquest lives at cannot face the place conquest is about");
    }

    /** Risk is variance either way, and nothing else about the demand. */
    @Test
    void riskIsARungEitherWay() {
        Fortification.Demand low = MissionFortification.demand(OperationTier.VETERAN, RiskLevel.LOW);
        Fortification.Demand high = MissionFortification.demand(OperationTier.VETERAN, RiskLevel.HIGH);
        assertEquals(-1, low.variance());
        assertEquals(1, high.variance());
        assertEquals(low.ceiling(), high.ceiling(), "risk moved the ceiling, which is the tier's");
    }

    /** Paths with no mission behind them read as an ordinary job with no surprise. */
    @Test
    void nothingKnownIsAnOrdinaryJob() {
        Fortification.Demand demand = MissionFortification.demand(null, null);
        assertEquals(MissionFortification.demand(OperationTier.ESTABLISHED, RiskLevel.MEDIUM), demand);
    }
}
