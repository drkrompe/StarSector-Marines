package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification.Demand;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification.Strength;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How hard a place is to take, derived when nobody states it.
 *
 * <p>Two facts with different jobs: the world's rating says what is there, the
 * mission's demand says what its attacker may be asked to face. Pinned on the
 * rule itself, since that is the unit, and once through the plan so the garrison
 * actually carries the answer.
 */
class FortificationDemandTest {

    private static TargetProfile world(int defenceLevel) {
        return new TargetProfile(6, 5, defenceLevel, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    /** A better-defended world is a harder place, and the ladder tops out at a citadel. */
    @Test
    void theWorldsRatingClimbsTheLadder() {
        Strength last = null;
        for (int rating = 0; rating <= 7; rating++) {
            Strength now = Strength.forDefenceRating(rating);
            if (last != null) {
                assertTrue(now.ordinal() >= last.ordinal(),
                        "rating " + rating + " is " + now + " below rating " + (rating - 1)
                                + "'s " + last);
            }
            last = now;
        }
        assertEquals(Strength.PICKET, Strength.forDefenceRating(0));
        assertEquals(Strength.CITADEL, Strength.forDefenceRating(7));
    }

    /**
     * The breakpoints are the overwatch line's, so the two readers of one fact
     * agree about where a fortified world begins.
     */
    @Test
    void theLadderBreaksWhereTheOverwatchLineDoes() {
        assertEquals(Strength.PICKET, Strength.forDefenceRating(1));
        assertEquals(Strength.GARRISON, Strength.forDefenceRating(2));
        assertEquals(Strength.STRONGHOLD, Strength.forDefenceRating(4));
    }

    /** A tier caps what the world says; it never raises it. */
    @Test
    void theTierCapsTheWorld() {
        Demand firstContract = new Demand(Strength.PICKET, 0);
        assertSame(Fortification.PICKET, firstContract.resolve(7),
                "a First Contract job against a fortress world was handed a refusal");
        Demand fullStrength = new Demand(Strength.CITADEL, 0);
        assertSame(Fortification.PICKET, fullStrength.resolve(1),
                "a ceiling raised a picket world to something it is not");
    }

    /** Risk moves the answer a rung, but never past the tier. */
    @Test
    void riskMovesARungWithinTheTier() {
        assertSame(Fortification.STRONGHOLD, new Demand(Strength.CITADEL, 1).resolve(3),
                "high risk did not raise a garrison world");
        assertSame(Fortification.PICKET, new Demand(Strength.CITADEL, -1).resolve(3),
                "low risk did not lower a garrison world");
        assertSame(Fortification.GARRISON, new Demand(Strength.GARRISON, 1).resolve(3),
                "high risk lifted a place past its tier, which is the next tier's operation");
        assertSame(Fortification.CITADEL, new Demand(Strength.CITADEL, 1).resolve(7),
                "the top of the ladder is not a place to fall off");
    }

    /** Nobody said: the world's rung stands. */
    @Test
    void anUnstatedDemandLeavesTheWorldStanding() {
        for (int rating = 0; rating <= 7; rating++) {
            assertSame(Strength.forDefenceRating(rating).fortification(),
                    Demand.UNSTATED.resolve(rating));
        }
        assertThrows(IllegalArgumentException.class, () -> new Demand(null, 0));
    }

    /** The garrison a plan derives carries the resolved answer, not a constant. */
    @Test
    void aDerivedGarrisonIsAsHardAsTheWorldAndTheMissionSay() {
        Precinct unstated = PrecinctPlan.derive(world(5), 560, 336, new Random(42L)).objective();
        assertSame(Fortification.STRONGHOLD, unstated.fortification(),
                "a rating-5 world with nothing said about the mission is a stronghold");

        Precinct capped = PrecinctPlan.derive(world(5), PrecinctPlan.Sprawl.BALANCED,
                new Demand(Strength.PICKET, 0), 560, 336, new Random(42L)).objective();
        assertSame(Fortification.PICKET, capped.fortification(),
                "the same world under a First Contract demand is still a stronghold");
        assertEquals(unstated.seedX(), capped.seedX(),
                "what the mission asks moved where the garrison is");
    }
}
