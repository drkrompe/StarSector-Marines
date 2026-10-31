package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeartSequenceTest {

    @Test void theNthIncendiaryLineOpensSectorN() {
        WalkingBarrage.Shell shell = new WalkingBarrage.Shell(3f, 70f, 20f, 60, 3f);
        WalkingBarrage.Plan plan = new WalkingBarrage.Plan(List.of(
                new WalkingBarrage.Line(0.33f, 40f, 50f, 420),
                new WalkingBarrage.Line(0.39f, 90f, 40f, 0),
                new WalkingBarrage.Line(0.50f, 130f, 40f, 420),
                new WalkingBarrage.Line(0.57f, 170f, 40f, 0),
                new WalkingBarrage.Line(0.67f, 210f, 40f, 420)), shell, 2.5f, 0.01f);
        int[] sectors = new int[7];
        for (int line = 0; line <= 6; line++) sectors[line] = HeartSequence.sectorOpenedBy(plan, line);
        assertEquals(List.of(0, 1, 0, 2, 0, 3, 0), Arrays.stream(sectors).boxed().toList());
    }
}
