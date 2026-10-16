package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.setup.SmallWarForces.Commitment;
import com.dillon.starsectormarines.battle.setup.SmallWarForces.Outfit;
import com.dillon.starsectormarines.battle.setup.SmallWarForces.SplitSide;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmallWarForcesTest {
    @Test void greyhookIsAFiniteNamedTableWithDeclaredIngressAndTriggers() {
        var ids = new HashSet<String>();
        for (var group : SmallWarForces.GROUPS) {
            assertTrue(ids.add(group.id()));
            assertTrue(group.size() > 0 && group.size() <= 6);
            assertFalse(group.name().isBlank());
            assertFalse(group.ingress().isBlank());
            assertEquals(group.reserve(), group.targetSite() >= 0, "only reserves are committed against a site");
        }
        assertEquals(40, SmallWarForces.total());
        assertEquals(10, SmallWarForces.reserveTotal());
        assertTrue(SmallWarForces.GROUPS.stream().anyMatch(g -> g.commitment() == Commitment.CUSTOMS_FALLS));
        assertTrue(SmallWarForces.GROUPS.stream().anyMatch(g -> g.commitment() == Commitment.FUEL_CONTROL_FALLS));
    }

    @Test void drentObeysVossBerthFourRefusesAndThePrizeCrewsSplit() {
        for (var group : SmallWarForces.GROUPS) {
            if (group.outfit() == Outfit.DRENT_HAULAGE) assertEquals(SplitSide.WITHDRAWS, group.split());
            if (group.outfit() == Outfit.BERTH_FOUR) assertEquals(SplitSide.HOLDS, group.split());
        }
        var prize = SmallWarForces.GROUPS.stream().filter(g -> g.outfit() == Outfit.PRIZE_CREW).map(SmallWarForces.Group::split).toList();
        assertTrue(prize.contains(SplitSide.WITHDRAWS) && prize.contains(SplitSide.HOLDS));
    }

    @Test void theServiceRoadGateIsWhereDeparturesActuallyLeave() {
        assertTrue(BlackSluice.atExit(BlackSluice.EXIT_X, BlackSluice.EXIT_Y));
        assertFalse(BlackSluice.atExit(BlackSluice.PIER_INGRESS_X, BlackSluice.PIER_INGRESS_Y));
        assertFalse(BlackSluice.atExit(Float.NaN, 20));
    }
}
