package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlliedGarrisonSizeTest {

    /**
     * The reference colony: size 5, no defence industry, stability 6 — vanilla's
     * {@code 200 * (0.25 + 0.6 * 0.75)} — which is the number
     * {@link AlliedGarrisonSize#STRENGTH_PER_SQUAD} was chosen against.
     */
    private static final float PLAIN_SIZE_FIVE = 140f;

    /** The same colony with Heavy Batteries: vanilla's x3 on the same stat. */
    private static final float HEAVY_BATTERIES_SIZE_FIVE = 420f;

    @Test
    void aStrongerMarketTurnsOutMoreOfItsOwn() {
        int previous = 0;
        for (float strength = 0f; strength <= 600f; strength += 25f) {
            int squads = AlliedGarrisonSize.squads(market(5, strength, 0f));
            assertTrue(squads >= previous,
                    "squads must not fall as defence strength rises, at " + strength);
            previous = squads;
        }
    }

    /** A plain colony fields three fireteams; this is what the constant is for. */
    @Test
    void aPlainColonyFieldsThreeSquads() {
        assertEquals(3, AlliedGarrisonSize.squads(market(5, PLAIN_SIZE_FIVE, 0f)));
    }

    /** A well-defended one runs into the cap rather than past it. */
    @Test
    void aWellDefendedColonyCapsRatherThanFieldingABattalion() {
        assertEquals(AlliedGarrisonSize.MAX_SQUADS,
                AlliedGarrisonSize.squads(market(5, HEAVY_BATTERIES_SIZE_FIVE, 0f)));
        // A size-10 capital behind a star fortress and a shield is far past it.
        assertEquals(AlliedGarrisonSize.MAX_SQUADS,
                AlliedGarrisonSize.squads(market(10, 6_000f, 0f)));
    }

    /**
     * The whole point of the subtraction: the company's stationed detachment is
     * inside vanilla's defender strength, so it must come back out before the
     * number becomes a militia, or those marines turn out twice.
     */
    @Test
    void theCompanysOwnStationedStrengthComesBackOutFirst() {
        int unstationed = AlliedGarrisonSize.squads(market(6, 300f, 0f));
        int stationed = AlliedGarrisonSize.squads(market(6, 300f, 150f));
        assertEquals(7, unstationed);
        assertEquals(3, stationed);
        assertTrue(stationed < unstationed);
    }

    /** A colony defended entirely by the company still fields its own floor. */
    @Test
    void aMarketWhoseWholeDefenceIsTheCompanyFieldsTheFloor() {
        assertEquals(AlliedGarrisonSize.MIN_SQUADS,
                AlliedGarrisonSize.squads(market(5, 200f, 200f)));
        // And an over-subtraction never goes negative.
        assertEquals(AlliedGarrisonSize.MIN_SQUADS,
                AlliedGarrisonSize.squads(market(5, 200f, 500f)));
    }

    /** The numbers doctrine multiplies the market's own strength. */
    @Test
    void theNumbersDoctrineMultipliesWhatTurnsOut() {
        TargetProfile plain = market(5, PLAIN_SIZE_FIVE, 0f);
        assertEquals(3, AlliedGarrisonSize.squads(plain, 1f));
        assertEquals(2, AlliedGarrisonSize.squads(plain, 0.5f));
        assertEquals(6, AlliedGarrisonSize.squads(plain, 2f));
        // The one-argument form is the doctrine-neutral case, spelled out.
        assertEquals(AlliedGarrisonSize.squads(plain, 1f),
                AlliedGarrisonSize.squads(plain));
    }

    /** The cap binds the multiplier too — doctrine cannot field a battalion. */
    @Test
    void theCapBindsTheMultiplier() {
        assertEquals(AlliedGarrisonSize.MAX_SQUADS,
                AlliedGarrisonSize.squads(market(5, PLAIN_SIZE_FIVE, 0f), 10f));
    }

    /** And so does the floor: a doctrine of nothing is still a garrison. */
    @Test
    void theFloorBindsTheMultiplier() {
        assertEquals(AlliedGarrisonSize.MIN_SQUADS,
                AlliedGarrisonSize.squads(market(5, PLAIN_SIZE_FIVE, 0f), 0f));
        assertEquals(AlliedGarrisonSize.MIN_SQUADS,
                AlliedGarrisonSize.squads(market(5, PLAIN_SIZE_FIVE, 0f), -3f));
        assertEquals(AlliedGarrisonSize.MIN_SQUADS,
                AlliedGarrisonSize.squads(market(5, PLAIN_SIZE_FIVE, 0f), Float.NaN));
    }

    /** No market, no garrison: nobody owns the ground, so nobody defends it. */
    @Test
    void noMarketFieldsNobody() {
        assertEquals(0, AlliedGarrisonSize.squads(TargetProfile.NEUTRAL));
        assertEquals(0, AlliedGarrisonSize.squads(TargetProfile.NEUTRAL, 3f));
        assertEquals(0, AlliedGarrisonSize.squads(null));
        // Market size is what says a market is there, not defence strength.
        assertEquals(0, AlliedGarrisonSize.squads(market(0, 400f, 0f)));
    }

    /** A colony with a garrison of nobody is a colony that was already taken. */
    @Test
    void everyRealMarketKeepsAtLeastOneSquad() {
        assertEquals(1, AlliedGarrisonSize.squads(market(1, 0f, 0f)));
        assertEquals(1, AlliedGarrisonSize.squads(market(3, 35f, 0f)));
    }

    /**
     * Stability is inside {@link TargetProfile#groundDefence()} already, since
     * vanilla writes it onto the same stat {@code getDefenderStr} reads. This is
     * the same colony at stability 3 and 10, and nothing here scales it again.
     */
    @Test
    void stabilityArrivesAlreadyApplied() {
        assertEquals(2, AlliedGarrisonSize.squads(market(5, 200f * 0.475f, 0f)));
        assertEquals(4, AlliedGarrisonSize.squads(market(5, 200f, 0f)));
    }

    private static TargetProfile market(int size, float groundDefence,
                                        float stationedStrength) {
        return new TargetProfile(size, 6, 0, 0, "hegemony",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.ROCK, SettlementLink.ROAD,
                groundDefence, stationedStrength);
    }
}
