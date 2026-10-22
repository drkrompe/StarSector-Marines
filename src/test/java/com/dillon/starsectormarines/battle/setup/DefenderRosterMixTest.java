package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.mech.MechVariant;
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
                RiskLevel.LOW, false, Float.POSITIVE_INFINITY, null, new MarketGarrison(10_000f, 0.6f, 0f, 1f));
        assertEquals(curve.totalCount, professional.totalCount, "a garrison that fills the tier fields all of it");
        assertEquals(Math.round(curve.totalCount * 0.30f), curve.eliteCount);
        assertEquals(Math.round(professional.totalCount * 0.6f), professional.eliteCount);
    }

    @Test
    void marketStrengthPlacesHeadcountInsideTheTiersBand() {
        int tier = 100;
        float fills = tier * DefenderRoster.STRENGTH_PER_DEFENDER;
        assertEquals(100, DefenderRoster.bandedTotal(tier, MarketGarrison.NONE), "no market keeps the tier count");
        assertEquals(100, DefenderRoster.bandedTotal(tier, new MarketGarrison(fills * 3f, 0.3f, 0f, 1f)),
                "the tier is the ceiling");
        assertEquals(85, DefenderRoster.bandedTotal(tier, new MarketGarrison(fills / 2f, 0.3f, 0f, 1f)));
        assertEquals(70, DefenderRoster.bandedTotal(tier, new MarketGarrison(0f, 0.3f, 0f, 1f)),
                "the weakest garrison still fields the band floor");
        assertEquals(8, DefenderRoster.bandedTotal(8, new MarketGarrison(0f, 0.3f, 0f, 1f)),
                "never below the smallest force worth generating");
    }

    @Test
    void heavyLeanKeepsAShareOfTheRiskCurvesMechs() {
        List<MechVariant> six = List.of(MechVariant.BULWARK, MechVariant.HOUND, MechVariant.WARDEN,
                MechVariant.BULWARK, MechVariant.HOUND, MechVariant.WARDEN);
        assertEquals(six, DefenderRoster.bandedMechs(six, MarketGarrison.NONE), "no market keeps them all");
        assertEquals(six, DefenderRoster.bandedMechs(six, new MarketGarrison(100f, 0.3f, 0.4f, 1f)));
        assertEquals(six.subList(0, 3), DefenderRoster.bandedMechs(six, new MarketGarrison(100f, 0.3f, 0f, 1f)),
                "an unarmoured market keeps the front half, anchor first");
        assertEquals(List.of(MechVariant.BULWARK),
                DefenderRoster.bandedMechs(List.of(MechVariant.BULWARK), new MarketGarrison(100f, 0.3f, 0f, 1f)));
        assertEquals(List.of(), DefenderRoster.bandedMechs(List.of(), new MarketGarrison(100f, 0.3f, 1f, 1f)));
    }

    @Test
    void wholeGarrisonFollowsStrength() {
        assertEquals(250, DefenderRoster.wholeGarrisonCount(
                new MarketGarrison(250 * DefenderRoster.STRENGTH_PER_DEFENDER, 0.3f, 0f, 1f)));
        assertEquals(8, DefenderRoster.wholeGarrisonCount(new MarketGarrison(4f, 0.3f, 0f, 1f)));
    }

    @Test
    void aGarrisonMetWholeFieldsItsWholeCountWhateverTheTier() {
        MarketGarrison large = new MarketGarrison(1_000f, 0.5f, 0f, 1f);
        DefenderRoster banded = DefenderRoster.forMission(MissionType.CONQUEST, OperationTier.FIRST_CONTRACT,
                RiskLevel.HIGH, false, Float.POSITIVE_INFINITY, null, large);
        DefenderRoster whole = DefenderRoster.forMission(MissionType.CONQUEST, OperationTier.FIRST_CONTRACT,
                RiskLevel.HIGH, false, Float.POSITIVE_INFINITY, null, large.asWhole());
        assertTrue(banded.totalCount < DefenderRoster.wholeGarrisonCount(large), "a tier caps a contract's share");
        assertEquals(DefenderRoster.wholeGarrisonCount(large), whole.totalCount, "an invasion meets all of it");
        assertEquals(Math.round((whole.totalCount - whole.mechVariants.size()) * 0.5f), whole.eliteCount,
                "still mixed by the garrison's quality");
    }
}
