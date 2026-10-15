package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Defender infantry is mixed through every squad, and a market's garrison quality sets how thickly. */
class DefenderRosterMixTest {

    private static final GroundRosterProfile.ForceTier ELITE = GroundRosterProfile.ForceTier.ELITE;

    @Test
    void elitesAreSpreadSoEverySquadMixes() {
        List<GroundRosterProfile.ForceTier> order = DefenderRoster.infantryOrder(4, 12);
        assertEquals(16, order.size());
        assertEquals(4, order.stream().filter(t -> t == ELITE).count());
        // Squads of four drawn from the front each take exactly one elite.
        for (int squad = 0; squad < 4; squad++) {
            long elites = order.subList(squad * 4, squad * 4 + 4).stream().filter(t -> t == ELITE).count();
            assertEquals(1, elites, "squad " + squad);
        }
    }

    @Test
    void centredSpreadGivesTheFirstShortSquadItsElite() {
        List<GroundRosterProfile.ForceTier> order = DefenderRoster.infantryOrder(3, 7);
        assertTrue(order.subList(0, 3).contains(ELITE));
    }

    @Test
    void degenerateCountsAreAllOneTier() {
        assertEquals(List.of(), DefenderRoster.infantryOrder(0, 0));
        assertTrue(DefenderRoster.infantryOrder(5, 0).stream().allMatch(t -> t == ELITE));
        assertTrue(DefenderRoster.infantryOrder(0, 5).stream().noneMatch(t -> t == ELITE));
    }

    @Test
    void marketQualityReplacesTheRiskCurve() {
        DefenderRoster curve = DefenderRoster.forMission(MissionType.ASSAULT, OperationTier.ESTABLISHED,
                RiskLevel.LOW, false, Float.POSITIVE_INFINITY, null, MarketGarrison.NONE);
        DefenderRoster professional = DefenderRoster.forMission(MissionType.ASSAULT, OperationTier.ESTABLISHED,
                RiskLevel.LOW, false, Float.POSITIVE_INFINITY, null, new MarketGarrison(300f, 0.6f, 0f, 1f));
        assertEquals(curve.totalCount, professional.totalCount, "quality changes the mix, not the headcount");
        assertEquals(Math.round(curve.totalCount * 0.30f), curve.eliteCount);
        assertEquals(Math.round(professional.totalCount * 0.6f), professional.eliteCount);
    }
}
