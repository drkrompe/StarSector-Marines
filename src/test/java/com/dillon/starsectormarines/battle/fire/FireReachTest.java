package com.dillon.starsectormarines.battle.fire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireReachTest {

    private static final int W = 40, H = 20;

    /** Grass everywhere except the columns given, which are bare. */
    private static FireField grassWithBreak(int fromX, int toX) {
        int[] fuel = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            fuel[y * W + x] = x >= fromX && x <= toX ? 0 : FireFuel.GRASS.bit;
        return new FireField(W, H, 1L, idx -> fuel[idx]);
    }

    private static FireField field(int fuelMask) {
        return new FireField(W, H, 1L, idx -> fuelMask);
    }

    @Test
    void aFireLitInTheMeadowYouStandInComesToYou() {
        FireReach reach = FireReach.of(grassWithBreak(-1, -1), 20, 10);
        assertTrue(reach.threatens(17, 10));
    }

    @Test
    void aMeadowFireComesNoFurtherThanGrassCarriesIt() {
        // Lit two cells round, carried at most 115% of grass's two cells more: reached to under five, threatening six.
        FireReach reach = FireReach.of(grassWithBreak(-1, -1), 20, 10);
        assertTrue(reach.threatens(17, 10));
        assertFalse(reach.threatens(13, 10), "a flamer seven cells back from a meadow fire is out of its way");
    }

    @Test
    void creepCarriesAFireAsFarAsItStands() {
        FireReach reach = FireReach.of(field(FireFuel.DEAD_CREEP.bit), 20, 10);
        assertTrue(reach.threatens(20 + FireReach.RADIUS, 10));
    }

    @Test
    void theReachCoversEveryCellTheFireBurns() {
        for (int fuel : new int[]{FireFuel.GRASS.bit, FireFuel.GRASS.bit | FireFuel.BRUSH.bit,
                FireFuel.GRASS.bit | FireFuel.CROWN.bit}) {
            FireField f = field(fuel);
            FireReach reach = FireReach.of(f, 20, 10);
            f.addHeat(20.5f, 10.5f, 1.3f, 2200);
            for (int t = 0; t < 30 * 90; t++) f.tick(t);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
                if (f.stateAt(y * W + x) != FireField.UNBURNT)
                    assertTrue(reach.threatens(x, y), "fuel " + fuel + ": burnt at " + x + "," + y + " outside the reach");
        }
    }

    @Test
    void aBreakWiderThanHeatCrossesShieldsTheFarSide() {
        // Bare from 10 to 14: five cells, wider than the jump, so the reach stops at 15.
        FireReach reach = FireReach.of(grassWithBreak(10, 14), 18, 10);
        assertTrue(reach.threatens(14, 10), "the cell beside reached ground is threatened");
        assertFalse(reach.threatens(13, 10));
        assertFalse(reach.threatens(9, 10), "fuel beyond the break is not reached");
    }

    @Test
    void aGapHeatCrossesDoesNotShield() {
        // Dead creep carries a fire as far as it stands, so only the gap decides: lit two cells round, so at 15, heat
        // crosses the two bare cells to the creep three cells further on.
        int[] fuel = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            fuel[y * W + x] = x == 13 || x == 14 ? 0 : FireFuel.DEAD_CREEP.bit;
        FireReach reach = FireReach.of(new FireField(W, H, 1L, idx -> fuel[idx]), 17, 10);
        assertTrue(reach.threatens(11, 10));
    }

    @Test
    void bareGroundLitOnReachesNothing() {
        FireReach reach = FireReach.of(grassWithBreak(0, W - 1), 20, 10);
        assertFalse(reach.threatens(20, 10));
    }
}
