package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Layer;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.LayerKind;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.SideRoom;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsSleepersTest {

    private static final float DT = 0.1f;

    /** Steps the rule {@code seconds} long and returns the bores in the order they opened. */
    private static List<Integer> run(RuinsSleepers.Stir stir, float seconds, float[] heard,
                                     boolean breaching, boolean breached) {
        List<Integer> opened = new ArrayList<>();
        for (int i = 0; i < Math.round(seconds / DT); i++) opened.addAll(stir.advance(DT, heard, breaching, breached));
        return opened;
    }

    @Test
    void timeBelowOpensTheBoresOneByOne() {
        RuinsSleepers.Stir stir = new RuinsSleepers.Stir(3);
        float[] quiet = new float[3];
        assertEquals(List.of(), run(stir, RuinsSleepers.Stir.threshold(0) - 1f, quiet, false, false));
        assertEquals(List.of(0), run(stir, 2f, quiet, false, false));
        assertEquals(List.of(1, 2), run(stir, RuinsSleepers.Stir.threshold(2), quiet, false, false));
    }

    @Test
    void gunfireOpensOnlyTheBoreThatHearsIt() {
        RuinsSleepers.Stir stir = new RuinsSleepers.Stir(2);
        // Loud enough every step to reach the second bore's threshold well before time alone would open the first.
        float[] nearSecond = {0f, RuinsSleepers.Stir.threshold(1) / RuinsSleepers.SHOT_STIR / 50f};
        assertEquals(List.of(1), run(stir, 5f, nearSecond, false, false));
        assertFalse(stir.awake(0));
    }

    @Test
    void aRousedBoreOpensOnTheNextStepAndOnlyIt() {
        RuinsSleepers.Stir stir = new RuinsSleepers.Stir(3);
        stir.rouse(2);
        assertEquals(List.of(2), stir.advance(DT, new float[3], false, false));
    }

    @Test
    void aBreachUnderWayStirsFasterAndItsEndOpensEveryBore() {
        RuinsSleepers.Stir calm = new RuinsSleepers.Stir(1);
        RuinsSleepers.Stir breaching = new RuinsSleepers.Stir(1);
        run(calm, 5f, new float[1], false, false);
        run(breaching, 5f, new float[1], true, false);
        assertTrue(breaching.stir(0) > calm.stir(0));

        RuinsSleepers.Stir stir = new RuinsSleepers.Stir(4);
        assertEquals(List.of(0, 1, 2, 3), stir.advance(DT, new float[4], false, true));
    }

    @Test
    void theHeavyStrainsComeOnlyAtTheLargerGrades() {
        BoreTier previous = null;
        for (RuinsGrade grade : RuinsGrade.values()) {
            BoreTier ceiling = RuinsSleepers.ceiling(grade);
            if (previous != null) assertTrue(ceiling.compareTo(previous) >= 0, grade + " grows less than the grade below");
            previous = ceiling;
        }
        assertTrue(RuinsSleepers.ceiling(RuinsGrade.SCATTERED).compareTo(BoreTier.MAW) < 0);
        assertEquals(BoreTier.MAW, RuinsSleepers.ceiling(RuinsGrade.VAST));
    }

    @Test
    void boresSleepInTheDeeperLayersApart() {
        List<Layer> layers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int left = 20 + i * 40;
            Area ground = new Area(left, 0, left + 39, 60);
            List<SideRoom> rooms = List.of(
                    new SideRoom("a", new Area(left + 2, 2, left + 8, 8), new Cell(left + 5, 9), new Cell(left + 5, 5)),
                    new SideRoom("b", new Area(left + 2, 50, left + 8, 58), new Cell(left + 5, 49), new Cell(left + 5, 54)));
            layers.add(new Layer(RuinsDelve.layerName(i), i, LayerKind.BREACHED_WALL, ground, left, left + 39,
                    List.of(), rooms, null));
        }
        RuinsDelve delve = new RuinsDelve(3, new Area(0, 0, 19, 60), List.of(), null, layers, null, List.of(),
                null, null, null);

        List<int[]> sites = RuinsSleepers.sites(delve, 5);
        assertEquals(5, sites.size());
        for (int i = 0; i < sites.size(); i++) {
            assertFalse(layers.get(0).ground().contains(sites.get(i)[0], sites.get(i)[1]), "a bore under the landing layer");
            for (int j = 0; j < i; j++) {
                int apart = Math.abs(sites.get(i)[0] - sites.get(j)[0]) + Math.abs(sites.get(i)[1] - sites.get(j)[1]);
                assertTrue(apart >= RuinsSleepers.BORE_SPACING, "bores " + i + " and " + j + " crowd");
            }
        }
    }

    @Test
    void oncePressureKeepsTheTimeOnlyNoiseAndTheBreachStirTheBores() {
        RuinsSleepers.Stir stir = new RuinsSleepers.Stir(2);
        stir.timeStirs = false;
        assertTrue(stir.advance(RuinsSleepers.WAKE_BASE * 2f, new float[2], false, false).isEmpty());
        assertEquals(List.of(0, 1), stir.advance(DT, new float[2], false, true));
    }

    @Test
    void aPushWakesTheNearestClosedBoreFirstThenSurgesTheNearestOpenOne() {
        float[][] at = {{0f, 0f}, {10f, 0f}, {50f, 0f}, null};
        boolean[] awake = {false, true, false, false};
        assertEquals(0, RuinsSleepers.pick(at, awake, new boolean[4], 12f, 0f), "closed first, though an open one is nearer");
        assertEquals(2, RuinsSleepers.pick(at, awake, new boolean[]{true, false, false, false}, 12f, 0f));
        assertEquals(1, RuinsSleepers.pick(at, awake, new boolean[]{true, false, true, false}, 12f, 0f),
                "with none closed left, the open one");
        assertEquals(-1, RuinsSleepers.pick(at, awake, new boolean[]{true, true, true, false}, 12f, 0f),
                "a bore not yet on the ground is never picked");
    }
}
