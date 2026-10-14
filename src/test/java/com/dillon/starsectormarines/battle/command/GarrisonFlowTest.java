package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.GarrisonFlow.Area;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure parts of {@link GarrisonFlow}: clustering pressed blocks into areas, what an area asks for, where its squads
 * wait, and the order in which areas are answered. Hand-built inputs; no world, no battle.
 */
class GarrisonFlowTest {

    private static final int BW = 40;
    private static final int SIZE = 8;

    private static int block(int bx, int by) {
        return by * BW + bx;
    }

    private static List<Area> areas(int[][] blocks, float... pressure) {
        int[] packed = new int[blocks.length];
        for (int i = 0; i < blocks.length; i++) packed[i] = block(blocks[i][0], blocks[i][1]);
        return GarrisonFlow.areas(packed, pressure, packed.length, BW, SIZE);
    }

    @Test
    void blocksWithinTheLinkDistanceShareAnAreaAndFartherOnesDoNot() {
        List<Area> linked = areas(new int[][]{{5, 5}, {8, 5}}, 4f, 4f);
        assertEquals(1, linked.size(), "three blocks apart is within the link");
        assertEquals(8f, linked.get(0).weight(), 1e-4f);
        assertEquals(2, linked.get(0).blocks());

        List<Area> apart = areas(new int[][]{{5, 5}, {9, 5}}, 4f, 4f);
        assertEquals(2, apart.size(), "four blocks apart is not");
    }

    @Test
    void linkingIsTransitiveAlongAChain() {
        List<Area> chain = areas(new int[][]{{2, 2}, {5, 2}, {8, 2}, {11, 2}}, 2f, 2f, 2f, 2f);
        assertEquals(1, chain.size());
        assertEquals(8f, chain.get(0).weight(), 1e-4f);
    }

    @Test
    void theCentreIsPressureWeightedAndSitsInsideTheCluster() {
        List<Area> a = areas(new int[][]{{10, 10}, {12, 10}}, 9f, 1f);
        Area area = a.get(0);
        // Block (10,10) centres at cell 84, block (12,10) at 100; nine parts to one.
        assertEquals(Math.round(84 * 0.9 + 100 * 0.1), area.centreX());
        assertEquals(84, area.centreY());
    }

    @Test
    void theResultDoesNotDependOnTheOrderContactsArrivedIn() {
        Random random = new Random(7);
        int n = 30;
        int[] bx = new int[n];
        int[] by = new int[n];
        float[] p = new float[n];
        for (int i = 0; i < n; i++) {
            bx[i] = random.nextInt(36);
            by[i] = random.nextInt(20);
            p[i] = 1 + random.nextInt(9);
        }
        // Distinct blocks only: the caller dedupes.
        List<Integer> order = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < n; i++) if (seen.add(block(bx[i], by[i]))) order.add(i);
        List<Area> reference = run(order, bx, by, p);
        for (int trial = 0; trial < 5; trial++) {
            Collections.shuffle(order, random);
            assertEquals(reference, run(order, bx, by, p));
        }
    }

    private static List<Area> run(List<Integer> order, int[] bx, int[] by, float[] p) {
        int[] blocks = new int[order.size()];
        float[] pressure = new float[order.size()];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = block(bx[order.get(i)], by[order.get(i)]);
            pressure[i] = p[order.get(i)];
        }
        return GarrisonFlow.areas(blocks, pressure, blocks.length, BW, SIZE);
    }

    @Test
    void areasComeHeaviestFirstAndAreCapped() {
        int[][] blocks = new int[GarrisonFlow.MAX_AREAS + 3][];
        float[] pressure = new float[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = new int[]{i * 4, 3};
            pressure[i] = i + 1;
        }
        List<Area> found = areas(blocks, pressure);
        assertEquals(GarrisonFlow.MAX_AREAS, found.size());
        for (int i = 1; i < found.size(); i++) assertTrue(found.get(i - 1).weight() >= found.get(i).weight());
        assertEquals(blocks.length, found.get(0).weight(), 1e-4f, "the heaviest block leads");
    }

    @Test
    void noBlocksIsNoAreas() {
        assertTrue(GarrisonFlow.areas(new int[0], new float[0], 0, BW, SIZE).isEmpty());
    }

    @Test
    void demandFollowsWeightAndUnmannedPlacesWithinACap() {
        assertEquals(0, GarrisonFlow.demand(GarrisonFlow.AREA_START_WEIGHT - 0.01f, 3), "too faint to answer");
        assertEquals(1, GarrisonFlow.demand(GarrisonFlow.AREA_START_WEIGHT, 0));
        assertEquals(1, GarrisonFlow.demand(GarrisonFlow.WEIGHT_PER_SQUAD, 0));
        assertEquals(2, GarrisonFlow.demand(GarrisonFlow.WEIGHT_PER_SQUAD + 0.1f, 0));
        assertEquals(2, GarrisonFlow.demand(3f, 1), "a pressed place nobody stands on is one more");
        assertEquals(GarrisonFlow.MAX_PER_AREA, GarrisonFlow.demand(1000f, 9), "capped");
    }

    @Test
    void theStagingCellIsTowardTheDefendersRearOnTheSameLateralLine() {
        ConquestTrackLayout south = new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, 200, 300);
        int[] cell = GarrisonFlow.desiredStage(south, 80, 100, 0);
        assertArrayEquals(new int[]{80, 100 + GarrisonFlow.STANDOFF_CELLS}, cell);

        ConquestTrackLayout east = new ConquestTrackLayout(TraversalAxis.WEST_TO_EAST, 300, 200);
        assertArrayEquals(new int[]{100 + GarrisonFlow.STANDOFF_CELLS, 80}, GarrisonFlow.desiredStage(east, 100, 80, 0));
    }

    @Test
    void theStagingCellIsClampedToTheMapAndSquadsAreSpreadAroundIt() {
        ConquestTrackLayout south = new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, 200, 300);
        assertEquals(299, GarrisonFlow.desiredStage(south, 80, 295, 0)[1], "no further than the last forward cell");
        int[] first = GarrisonFlow.desiredStage(south, 80, 100, 0);
        int[] second = GarrisonFlow.desiredStage(south, 80, 100, 1);
        assertTrue(first[0] != second[0] || first[1] != second[1], "two squads do not share one cell");
    }

    @Test
    void snapFindsTheNearestAcceptableCellOrNone() {
        int[] same = GarrisonFlow.snap(10, 10, 5, (x, y) -> true);
        assertArrayEquals(new int[]{10, 10}, same);
        int[] aside = GarrisonFlow.snap(10, 10, 5, (x, y) -> x >= 12);
        assertNotNull(aside);
        assertEquals(12, aside[0]);
        assertEquals(10, aside[1], "the smallest ring that has one, straight across");
        assertNull(GarrisonFlow.snap(10, 10, 3, (x, y) -> x >= 20));
    }

    @Test
    void everyAreaGetsAPicketBeforeAnyGetsASecond() {
        // Area 0 is heaviest and wants three; areas 1 and 2 want one each and have nobody.
        int[] order = GarrisonFlow.sendOrder(new int[]{3, 1, 1}, new int[]{0, 0, 0}, 3);
        assertArrayEquals(new int[]{0, 1, 2}, order);
    }

    @Test
    void anAreaWithNobodyLocalIsServedBeforeOneThatNeedsMore() {
        // Area 0 already has one local and wants two more; area 1 has none and wants one.
        int[] order = GarrisonFlow.sendOrder(new int[]{2, 1}, new int[]{1, 0}, 3);
        assertArrayEquals(new int[]{1, 0, 0}, order);
    }

    @Test
    void massingFollowsInRoundsAndIsBounded() {
        int[] order = GarrisonFlow.sendOrder(new int[]{3, 2}, new int[]{0, 0}, 4);
        assertArrayEquals(new int[]{0, 1, 0, 1}, order);
        assertEquals(0, GarrisonFlow.sendOrder(new int[]{3}, new int[]{0}, 0).length);
        assertEquals(0, GarrisonFlow.sendOrder(new int[]{0, 0}, new int[]{0, 0}, 5).length, "nothing unmet, nothing sent");
    }

    @Test
    void theStagedShareOfThePoolIsBoundedButNeverZero() {
        assertEquals(1, GarrisonFlow.stagedCap(1));
        assertEquals(1, GarrisonFlow.stagedCap(2));
        assertEquals(4, GarrisonFlow.stagedCap(10));
        assertEquals(24, GarrisonFlow.stagedCap(60));
    }

    @Test
    void aPostBelongsToTheNearestAreaWithinReachThatIsStillWarm() {
        List<Area> found = new ArrayList<>();
        found.add(new Area(100, 100, 6f, 3, 0));
        found.add(new Area(130, 100, 6f, 3, 1));
        found.add(new Area(400, 400, 6f, 3, 2));
        assertEquals(0, GarrisonFlow.matchingArea(found, 110, 100));
        assertEquals(1, GarrisonFlow.matchingArea(found, 125, 100));
        assertEquals(-1, GarrisonFlow.matchingArea(found, 250, 250), "nothing within reach");

        List<Area> cooled = new ArrayList<>();
        cooled.add(new Area(100, 100, GarrisonFlow.AREA_STOP_WEIGHT - 0.1f, 1, 0));
        assertEquals(-1, GarrisonFlow.matchingArea(cooled, 100, 100), "below the stop weight an area no longer keeps a post");
    }

    @Test
    void hysteresisLeavesABandBetweenWantingASquadAndKeepingOne() {
        assertTrue(GarrisonFlow.AREA_STOP_WEIGHT < GarrisonFlow.AREA_START_WEIGHT);
        List<Area> between = new ArrayList<>();
        float weight = (GarrisonFlow.AREA_START_WEIGHT + GarrisonFlow.AREA_STOP_WEIGHT) / 2f;
        between.add(new Area(100, 100, weight, 1, 0));
        assertEquals(0, GarrisonFlow.demand(weight, 0), "too faint to stage anybody new");
        assertEquals(0, GarrisonFlow.matchingArea(between, 100, 100), "but a squad already waiting there stays");
    }
}
