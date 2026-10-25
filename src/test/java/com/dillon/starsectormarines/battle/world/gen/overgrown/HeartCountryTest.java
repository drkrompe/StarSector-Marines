package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartCountryTest {

    private static Landscape.Brook straight(int x) {
        return new Landscape.Brook(List.of(new int[]{x, 0}, new int[]{x, 100}, new int[]{x, 200}, new int[]{x, 299}),
                5, 46, 5, 10);
    }

    @Test
    void theBrookBendsRoundTheHollowOnItsOwnSide() {
        int cx = 300, cy = 150;
        Landscape.Brook bent = HeartCountry.roundHollow(straight(250), cx, cy);
        long r2 = (long) HeartCountry.BROOK_HOLLOW_CLEARANCE * HeartCountry.BROOK_HOLLOW_CLEARANCE;
        for (int[] c : bent.line()) {
            long dx = c[0] - cx, dy = c[1] - cy;
            assertTrue(dx * dx + dy * dy >= r2 - 2L * HeartCountry.BROOK_HOLLOW_CLEARANCE,
                    "brook cell " + c[0] + "," + c[1] + " is inside the hollow's clearance");
            assertTrue(c[0] <= 250, "the brook bends west, the side it ran");
        }
        assertEquals(0, bent.path().get(0)[1]);
        assertEquals(299, bent.path().get(bent.path().size() - 1)[1]);
    }

    @Test
    void aBrookClearOfTheHollowIsLeftAsItWas() {
        Landscape.Brook brook = straight(100);
        Landscape.Brook kept = HeartCountry.roundHollow(brook, 300, 150);
        assertEquals(brook.path().size(), kept.path().size());
        for (int i = 0; i < brook.path().size(); i++) {
            assertEquals(brook.path().get(i)[0], kept.path().get(i)[0]);
            assertEquals(brook.path().get(i)[1], kept.path().get(i)[1]);
        }
    }

    @Test
    void aLaneThatMeetsTheKnollTurnsAlongItsFootToTheGate() {
        Precinct town = new Precinct(HeartCountry.SETTLEMENT, 193, 233, GrownTrunkPlan.Profile.hamlet(), null,
                Precinct.Boundary.OPEN);
        Landscape.Knoll knoll = HeartCountry.knoll(HeartCountry.Density.RURAL);
        int foot = 233 - knoll.halfHeight() - knoll.ring() - 1;
        List<Landscape.Track> turned = HeartCountry.laneTracks(183, 268, town, knoll);
        assertEquals(2, turned.size());
        assertEquals(foot, turned.get(0).y1(), "the lane stops a cell short of the knoll's ground");
        Landscape.Track along = turned.get(1);
        assertEquals(foot, along.y1());
        assertTrue(along.x0() <= 183 && along.x1() >= 193, "the turn reaches the gate's line");

        List<Landscape.Track> clear = HeartCountry.laneTracks(84, 268, town, knoll);
        assertEquals(List.of(new Landscape.Track(84, 0, 86, 268)), clear);
    }
}
