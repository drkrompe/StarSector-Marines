package com.dillon.starsectormarines.battle.command.trace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SquadDensityProbeTest {

    @Test
    void chainedSquadsFormOneClusterEvenWhenEndsAreFarApart() {
        List<float[]> points = List.of(
                new float[] {0, 0}, new float[] {5, 0}, new float[] {10, 0},
                new float[] {100, 100});
        assertArrayEquals(new int[] {3, 3},
                SquadDensityProbe.clusters(points, 6f));
    }

    @Test
    void isolatedSquadsAreNeitherLinkedNorClustered() {
        List<float[]> points = List.of(
                new float[] {0, 0}, new float[] {50, 0});
        assertArrayEquals(new int[] {0, 1},
                SquadDensityProbe.clusters(points, 6f));
    }

    @Test
    void noSquadsMeansNothing() {
        assertArrayEquals(new int[] {0, 0},
                SquadDensityProbe.clusters(List.of(), 6f));
    }
}
