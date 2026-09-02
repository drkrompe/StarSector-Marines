package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.AbsentDefenceGrade.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbsentDefenceGradeTest {

    @Test
    void bandsFollowVanillasOwnForecastEdges() {
        assertEquals(Outcome.HELD, AbsentDefenceGrade.grade(0f));
        assertEquals(Outcome.HELD, AbsentDefenceGrade.grade(0.3299f));
        assertEquals(Outcome.HELD_WITH_LOSSES, AbsentDefenceGrade.grade(0.33f));
        assertEquals(Outcome.HELD_WITH_LOSSES, AbsentDefenceGrade.grade(0.6599f));
        assertEquals(Outcome.OVERRUN, AbsentDefenceGrade.grade(0.66f));
        assertEquals(Outcome.OVERRUN, AbsentDefenceGrade.grade(1f));
    }

    @Test
    void effectivenessMirrorsTheRatioVanillaResolvesTheGroundHalfWith() {
        assertEquals(0.25f, AbsentDefenceGrade.raidEffectiveness(100f, 300f), 1e-4f);
        assertEquals(0.5f, AbsentDefenceGrade.raidEffectiveness(200f, 200f), 1e-4f);
        // The denominator floor: a market with no defences at all still divides by one.
        assertEquals(0.5f, AbsentDefenceGrade.raidEffectiveness(0.5f, 0f), 1e-4f);
    }

    @Test
    void anUnopposedRaidStillGradesAsOverrun() {
        assertEquals(Outcome.OVERRUN,
                AbsentDefenceGrade.grade(AbsentDefenceGrade.raidEffectiveness(900f, 100f)));
    }

    @Test
    void heldCasualtiesAreHalfWhatTheRaidsEffectivenessWouldHaveTaken() {
        assertEquals(2, AbsentDefenceGrade.casualties(12, Outcome.HELD, 0.3f));
        assertEquals(3, AbsentDefenceGrade.casualties(12, Outcome.HELD_WITH_LOSSES, 0.5f));
        assertEquals(0, AbsentDefenceGrade.casualties(12, Outcome.HELD, 0f));
    }

    @Test
    void anOverrunGarrisonIsBeatenRatherThanAnnihilated() {
        assertEquals(9, AbsentDefenceGrade.casualties(12, Outcome.OVERRUN, 0.75f));
        assertEquals(11, AbsentDefenceGrade.casualties(12, Outcome.OVERRUN, 1f));
        // An effectiveness outside [0, 1] is clamped rather than trusted.
        assertEquals(11, AbsentDefenceGrade.casualties(12, Outcome.OVERRUN, 5f));
    }

    @Test
    void anEmptyDetachmentAndAMissingGradeCostNothing() {
        assertEquals(0, AbsentDefenceGrade.casualties(0, Outcome.OVERRUN, 1f));
        assertEquals(0, AbsentDefenceGrade.casualties(-3, Outcome.OVERRUN, 1f));
        assertEquals(0, AbsentDefenceGrade.casualties(12, null, 1f));
    }

    /** An unestimated attacker is zero strength, which grades as nothing happening. */
    @Test
    void aZeroStrengthAttackerScoresNothing() {
        assertEquals(0f, AbsentDefenceGrade.raidEffectiveness(0f, 400f), 1e-4f);
        assertEquals(Outcome.HELD, AbsentDefenceGrade.grade(
                AbsentDefenceGrade.raidEffectiveness(0f, 400f)));
        assertEquals(0, AbsentDefenceGrade.casualties(12, Outcome.HELD, 0f));
    }
}
