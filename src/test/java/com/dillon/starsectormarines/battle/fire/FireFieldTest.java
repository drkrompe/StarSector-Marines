package com.dillon.starsectormarines.battle.fire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireFieldTest {

    private static final int W = 40, H = 20;

    /** Grass everywhere except the columns given, which are bare. */
    private static int[] grassWithBreak(int fromX, int toX) {
        int[] fuel = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            fuel[y * W + x] = x >= fromX && x <= toX ? 0 : FireFuel.GRASS.bit;
        return fuel;
    }

    private static FireField field(int[] fuel, long seed) {
        return new FireField(W, H, seed, idx -> fuel[idx]);
    }

    private static void lightWestEdge(FireField f) {
        for (int y = 0; y < H; y++) f.addHeat(0, y, 2000);
    }

    private static void run(FireField f, int from, int to) {
        for (int t = from; t < to; t++) f.tick(t);
    }

    private static boolean anyTouched(FireField f, int x) {
        for (int y = 0; y < H; y++) if (f.stateAt(y * W + x) != FireField.UNBURNT) return true;
        return false;
    }

    @Test
    void aGrassFireRunsAcrossAndBurnsOut() {
        int[] fuel = grassWithBreak(-1, -1);
        FireField f = field(fuel, 3L);
        lightWestEdge(f);
        run(f, 0, 30 * 120);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            assertEquals(FireField.BURNT, f.stateAt(y * W + x), "every grass cell burnt: " + x + "," + y);
        assertFalse(f.isActive(), "and the fire is out");
    }

    @Test
    void aTwoCellBreakHoldsAGrassFireAndAOneCellGapDoesNot() {
        FireField held = field(grassWithBreak(20, 21), 3L);
        lightWestEdge(held);
        run(held, 0, 30 * 120);
        assertTrue(anyTouched(held, 19), "the fire reached the break");
        assertFalse(anyTouched(held, 22), "and nothing beyond it caught");

        FireField leaked = field(grassWithBreak(20, 20), 3L);
        lightWestEdge(leaked);
        run(leaked, 0, 30 * 120);
        assertTrue(anyTouched(leaked, 21), "a front throws heat over a one-cell furrow");
    }

    @Test
    void aCrownFireThrowsHeatFurtherThanAGrassFire() {
        int[] fuel = grassWithBreak(20, 21);
        for (int y = 0; y < H; y++) for (int x = 12; x < 20; x++) fuel[y * W + x] = FireFuel.CROWN.bit | FireFuel.CREEP.bit;
        FireField f = field(fuel, 3L);
        lightWestEdge(f);
        run(f, 0, 30 * 180);
        assertTrue(anyTouched(f, 22), "a burning crown lights grass over a two-cell break");
    }

    @Test
    void windCarriesItDownwindAndHoldsItUpwind() {
        int[] fuel = grassWithBreak(-1, -1);
        FireField f = field(fuel, 5L);
        f.setWind(1f, 0f, 1f);
        f.addHeat(20, 10, 3000);
        run(f, 0, 30 * 20);
        int east = 0, west = 0;
        for (int x = 0; x < W; x++) {
            boolean t = f.stateAt(10 * W + x) != FireField.UNBURNT;
            if (!t) continue;
            if (x > 20) east++;
            if (x < 20) west++;
        }
        assertTrue(east > west * 2, "the run downwind outpaces the run upwind: " + east + " vs " + west);
    }

    @Test
    void sameSeedSameBurn() {
        int[] fuel = grassWithBreak(18, 18);
        FireField a = field(fuel, 11L), b = field(fuel, 11L);
        for (FireField f : new FireField[]{a, b}) { lightWestEdge(f); run(f, 0, 30 * 40); }
        for (int i = 0; i < W * H; i++) {
            assertEquals(a.stateAt(i), b.stateAt(i));
            assertEquals(a.intensityAt(i), b.intensityAt(i));
            assertEquals(a.heatAt(i), b.heatAt(i));
        }
    }

    @Test
    void burntGroundRefusesCreepForAWhile() {
        int[] fuel = new int[W * H];
        fuel[5 * W + 5] = FireFuel.GRASS.bit;
        FireField f = field(fuel, 1L);
        f.addHeat(5, 5, 1000);
        run(f, 0, 30 * 10);
        int idx = 5 * W + 5;
        assertEquals(FireField.BURNT, f.stateAt(idx));
        assertTrue(f.refusesCreep(idx));
        run(f, 30 * 10, 30 * 10 + FireField.CREEP_REFUSAL_TICKS);
        assertFalse(f.refusesCreep(idx), "the refusal runs out");
        assertFalse(f.refusesCreep(0), "unburnt ground refuses nothing");
    }

    @Test
    void aCellBurnsForItsLoad() {
        int[] fuel = new int[W * H];
        fuel[0] = FireFuel.GRASS.bit;
        FireField f = field(fuel, 1L);
        f.addHeat(0, 0, 1000);
        int caught = -1, out = -1;
        for (int t = 0; t < 30 * 30; t++) {
            f.tick(t);
            if (caught < 0 && f.stateAt(0) == FireField.BURNING) caught = t;
            if (out < 0 && f.stateAt(0) == FireField.BURNT) out = t;
        }
        assertEquals(FireFuel.GRASS.load * FireField.STEP_TICKS, out - caught);
    }

    @Test
    void anIgniterLightsWhatIsRoundItUntilItGoesOut() {
        int[] fuel = new int[W * H];
        fuel[10 * W + 11] = FireFuel.BRUSH.bit;
        FireField f = field(fuel, 1L);
        f.putIgniter(1L, 10, 10, 400, 30 * 5);
        run(f, 0, 30 * 30);
        assertEquals(FireField.BURNT, f.stateAt(10 * W + 11), "a wreck lit the brush beside it");
        assertFalse(f.isActive());
    }
}
