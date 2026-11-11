package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarrisonTaskingTest {

    private static StakeTargets.Target stake(int point, int x, int y, int strands) {
        return new StakeTargets.Target(point, x, y, SurveyStake.Stage.RAISED, StakeTargets.By.NODE, point * 10L, strands);
    }

    private static GarrisonTasking.Order of(List<GarrisonTasking.Order> orders, int squad) {
        return orders.stream().filter(o -> o.squadId() == squad).findFirst().orElseThrow();
    }

    @Test
    void aWalkerIsAnsweredByRocketsAndAFlockByRiflesEvenWhenTheOtherIsNearer() {
        GarrisonTasking.Squad rifles = new GarrisonTasking.Squad(1, 0, 0, false);
        GarrisonTasking.Squad rockets = new GarrisonTasking.Squad(2, 20, 0, true);
        List<GarrisonTasking.Order> orders = GarrisonTasking.orders(List.of(rifles, rockets),
                List.of(new int[] {5, 0}), List.of(new int[] {25, 0}), List.of());
        assertEquals(GarrisonTasking.Duty.ARMOUR, of(orders, 2).duty(), "the rockets go for the frame");
        assertEquals(5, of(orders, 2).x());
        assertEquals(GarrisonTasking.Duty.AIR, of(orders, 1).duty(), "the rifles bring the flock down");
        assertEquals(25, of(orders, 1).x());
    }

    @Test
    void riflesAreNotSentAtAFrameAndAFarThreatIsLeftAlone() {
        List<GarrisonTasking.Order> orders = GarrisonTasking.orders(
                List.of(new GarrisonTasking.Squad(1, 0, 0, false), new GarrisonTasking.Squad(2, 0, 10, true)),
                List.of(new int[] {3, 0}, new int[] {200, 0}), List.of(new int[] {0, 200}), List.of());
        assertEquals(1, orders.size(), "only the rockets answer the near walker; nothing chases the far ones");
        assertEquals(GarrisonTasking.Duty.ARMOUR, of(orders, 2).duty());
        assertTrue(GarrisonTasking.orders(List.of(new GarrisonTasking.Squad(1, 0, 0, false)),
                List.of(new int[] {3, 0}), List.of(), List.of()).isEmpty(), "a rifle squad holds against a frame");
    }

    @Test
    void theFreeSquadsGoOneAStakeNearestFirstAndTheRestDoubleUpOnTheBiggestKill() {
        List<StakeTargets.Target> stakes = List.of(stake(1, 100, 0, 3), stake(2, 0, 100, 1));
        List<GarrisonTasking.Order> orders = GarrisonTasking.orders(List.of(
                new GarrisonTasking.Squad(1, 0, 90, false),
                new GarrisonTasking.Squad(2, 90, 0, false),
                new GarrisonTasking.Squad(3, 50, 50, true)), List.of(), List.of(), stakes);
        assertEquals(3, orders.size());
        assertEquals(100, of(orders, 2).x(), "the squad nearest the biggest kill takes it");
        assertEquals(100, of(orders, 1).y(), "the next stake gets its nearest free squad");
        assertEquals(GarrisonTasking.Duty.STAKE, of(orders, 3).duty());
        assertEquals(100, of(orders, 3).x(), "the squad left over doubles up on the first stake");
    }
}
