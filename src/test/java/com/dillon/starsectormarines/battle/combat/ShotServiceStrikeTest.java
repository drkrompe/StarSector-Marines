package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The strike queue carries only rounds that can wear what they stopped in, and applies them on their flight clock. */
class ShotServiceStrikeTest {

    private static BallisticResolver.Resolution stop(StruckFeature feature, float flightTime) {
        return new BallisticResolver.Resolution(0f, 0f, 0f, flightTime, 0L, false, false,
                BallisticResolver.StopKind.WALL, List.of(), feature);
    }

    @Test
    void aRoundThatNamesNothingOrCannotWearQueuesNothing() {
        ShotService shots = new ShotService();
        shots.setStrikeFilter((feature, damage, penetration, burns) -> feature.cellX() != 9);
        shots.queueStrike(stop(null, 0.1f), 1L, 14f, 7f);
        shots.queueStrike(stop(StruckFeature.cell(9, 1), 0.1f), 1L, 14f, 7f);
        shots.queueStrike(stop(StruckFeature.cell(2, 1), 0.1f), 1L, 0f, 7f);
        shots.queueStrike(stop(StruckFeature.cell(2, 1), 0.1f), 1L, 14f, 0f);
        assertEquals(0, shots.pendingStrikeCount());
    }

    @Test
    void aStrikeArrivesWhenItsFlightTimeRunsOut() {
        ShotService shots = new ShotService();
        List<StruckFeature> arrived = new ArrayList<>();
        shots.queueStrike(stop(StruckFeature.cell(2, 1), 0.05f), 1L, 14f, 7f);
        shots.queueStrike(stop(StruckFeature.cell(3, 1), 0.15f), 1L, 14f, 7f);
        shots.tickStrikes(0.1f, strike -> arrived.add(strike.feature));
        assertEquals(List.of(StruckFeature.cell(2, 1)), arrived);
        assertEquals(1, shots.pendingStrikeCount());
        shots.tickStrikes(0.1f, strike -> arrived.add(strike.feature));
        assertEquals(List.of(StruckFeature.cell(2, 1), StruckFeature.cell(3, 1)), arrived);
        assertEquals(0, shots.pendingStrikeCount());
    }
}
