package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.unit.SwarmStage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The age mix a bore's surges draw stages from, and how the director plans a surge from it. */
class SwarmAgeMixTest {

    @Test
    void feedingAgesTheBroodAndTheSharesStayWhole() {
        for (BoreTier tier : BoreTier.values()) {
            int[] previous = SwarmAgeMix.weights(tier, 0f);
            for (int fed = 0; fed <= 40; fed++) {
                int[] w = SwarmAgeMix.weights(tier, fed);
                assertEquals(100, w[0] + w[1] + w[2], tier + " fed " + fed);
                for (int share : w) assertTrue(share >= 0, tier + " fed " + fed);
                assertTrue(w[2] <= SwarmAgeMix.ELDER_CAP, tier + " fed " + fed);
                assertTrue(w[0] <= previous[0] && w[2] >= previous[2], "feeding never makes the brood younger");
                previous = w;
            }
        }
        assertEquals(0, SwarmAgeMix.weights(BoreTier.FISSURE, 0f)[2], "an unfed fissure throws no elders");
        assertTrue(SwarmAgeMix.weights(BoreTier.FISSURE, 12f)[2] > 0, "a fed fissure does");
    }

    @Test
    void aDrawIsKeyedSoTheSameBodyIsAlwaysTheSameAge() {
        int[] mix = SwarmAgeMix.weights(BoreTier.BORE, 3f);
        for (int i = 0; i < 50; i++) {
            long roll = SwarmAgeMix.roll(7L, 2, 4, i);
            assertEquals(SwarmAgeMix.pick(mix, roll), SwarmAgeMix.pick(mix, SwarmAgeMix.roll(7L, 2, 4, i)));
        }
        assertEquals(SwarmStage.ADULT, SwarmAgeMix.pick(new int[]{0, 100, 0}, 12345L));
    }

    @Test
    void aFedBoreSendsFewerOlderBodiesLeaderFirst() {
        SwarmReinforcementSystem director = new SwarmReinforcementSystem(new CivilianEvacuationTracker());
        SwarmBore fed = new SwarmBore(1, false, 1L, 0, 0, 0, 1f);
        fed.feed(20f, 0);
        List<SwarmReinforcementSystem.Planned> pulse = director.plan(fed, 0, 20f, true, false);
        float weight = 0f;
        for (SwarmReinforcementSystem.Planned body : pulse) weight += body.weight();
        assertTrue(weight >= 20f, "the pulse fills its budget in weight");
        assertTrue(pulse.size() < 20, "older bodies weigh more, so fewer come");
        assertTrue(pulse.stream().anyMatch(b -> b.stage() == SwarmStage.ELDER));
        assertEquals(SwarmStage.ELDER, pulse.get(0).stage(), "the pack leader leaves the mouth first");
    }
}
