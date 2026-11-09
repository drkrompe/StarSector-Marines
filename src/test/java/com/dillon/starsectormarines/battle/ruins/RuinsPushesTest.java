package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Aim;
import com.dillon.starsectormarines.battle.ruins.RuinsPushes.Kind;
import com.dillon.starsectormarines.battle.ruins.RuinsPushes.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsPushesTest {

    @Test
    void aHuntWalksAtTheCompanysLastNoiseWhereverItStands() {
        Order order = RuinsPushes.order(Aim.HUNT, 5f, 5f, 40, 40, 20, 22);
        assertEquals(new Order(Kind.ATTACK_MOVE, 20, 22), order);
    }

    @Test
    void aWayBackPushWalksToItsStandThenHoldsIt() {
        assertEquals(new Order(Kind.ATTACK_MOVE, 30, 30), RuinsPushes.order(Aim.WAY_BACK, 10f, 10f, 30, 30, 0, 0));
        assertEquals(new Order(Kind.DEFEND, 30, 30), RuinsPushes.order(Aim.WAY_BACK, 33f, 28f, 30, 30, 0, 0));
    }

    @Test
    void anOrderIsReissuedOnlyWhenItsKindOrGoalMoves() {
        Order last = new Order(Kind.ATTACK_MOVE, 20, 20);
        assertTrue(RuinsPushes.changed(null, last));
        assertFalse(RuinsPushes.changed(last, new Order(Kind.ATTACK_MOVE, 22, 21)));
        assertTrue(RuinsPushes.changed(last, new Order(Kind.ATTACK_MOVE, 23, 20)));
        assertTrue(RuinsPushes.changed(last, new Order(Kind.DEFEND, 20, 20)));
    }

    @Test
    void aPushComesUpOnTheStandableCellFarthestFromTheCompany() {
        List<int[]> candidates = List.of(new int[]{10, 10}, new int[]{50, 10}, new int[]{90, 10});
        assertArrayEquals(new int[]{90, 10}, RuinsPushes.wakeCell(candidates, 0f, 10f, (x, y) -> true));
        assertArrayEquals(new int[]{50, 10}, RuinsPushes.wakeCell(candidates, 0f, 10f, (x, y) -> x != 90));
        assertNull(RuinsPushes.wakeCell(candidates, 0f, 10f, (x, y) -> false));
    }
}
