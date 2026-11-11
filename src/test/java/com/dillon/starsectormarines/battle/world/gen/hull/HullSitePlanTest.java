package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.nav.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullSitePlanTest {

    private static HullSitePlan west(int plough) {
        return new HullSitePlan(200, 100, Direction.W, 40, List.of(), 0, List.of(), List.of(), plough);
    }

    @Test
    void theBandRunsAlongTheHullAndOnDownItsTrench() {
        HullSitePlan plan = west(0);
        int tip = 200 - HullPlan.LAID_LENGTH / 2;
        assertTrue(plan.underBand(200, 100 + HullPlan.LAID_BREADTH / 2 + ImpactZone.BATTER - 1));
        assertTrue(plan.underBand(tip - 40, 100));
        assertFalse(plan.underBand(200, 100 + HullPlan.LAID_BREADTH / 2 + ImpactZone.BATTER + 2));
        assertFalse(plan.underBand(tip - 40 - HullPlan.LAID_BREADTH / 2 - ImpactZone.BATTER - 2, 100));
    }

    @Test
    void aWiderPloughReachesFurther() {
        int out = 100 + HullPlan.LAID_BREADTH / 2 + ImpactZone.BATTER + 10;
        assertFalse(west(0).underBand(200, out));
        assertTrue(west(64).underBand(200, out));
    }
}
