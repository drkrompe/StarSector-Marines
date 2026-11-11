package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.derelict.FallenHullSite;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.HaulRoad;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.LatticePoint;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.Stake;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.StakeStage;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Apron;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Interior;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The ground's hull site read as the hull's rules read it ({@code fallen-hull-nouns.md}, The ground). */
class LongSurveyGroundTest {

    @Test
    void theRulesSiteTakesTheHullWithItsTrenchTheBayMouthAndRoadsRunningOutward() {
        Area footprint = new Area(20, 10, 40, 20), trench = new Area(0, 12, 19, 18), bay = new Area(30, 11, 34, 14);
        // Two aprons: the stern's off the trench end, the bay's below the bay door. The mouth is the bay's.
        Apron stern = apron("stern", new Area(14, 14, 19, 16), 12, 15);
        Apron bayApron = apron("bay", new Area(30, 4, 34, 9), 32, 6);
        Interior interior = new Interior("hull", footprint, 0, List.of(new Area(20, 14, 20, 16), new Area(30, 10, 34, 10)),
                List.of(stern, bayApron), List.of(), List.of(), List.of());
        List<LatticePoint> lattice = List.of(new LatticePoint(0, 1, 0, 1, new Cell(70, 15)),
                new LatticePoint(1, 0, -1, 1, new Cell(30, -20)));
        List<HaulRoad> roads = List.of(new HaulRoad(0, List.of(new Cell(70, 15), new Cell(60, 15), new Cell(41, 15))),
                new HaulRoad(1, List.of(new Cell(30, -20))));
        HullSite site = new HullSite(footprint, Direction.E, interior, new Area(36, 12, 40, 18), new Cell(38, 15), bay,
                trench, List.of(), List.of(footprint), lattice, roads, List.of(new Stake(0, StakeStage.RAISED)), List.of(), 100,
                new BitSet(), new BitSet());

        FallenHullSite rules = LongSurveyGround.rulesSite(site, List.of(), List.of(), 25f);

        assertEquals(new FallenHullSite.Rect(0, 10, 40, 20), rules.hull(), "the root zone takes the trench in");
        assertEquals(38, rules.forgeX());
        assertEquals(15, rules.forgeY());
        assertEquals(32, rules.mouthX(), "the mouth is the bay apron's berth");
        assertEquals(6, rules.mouthY());
        assertEquals(LongSurveyGround.LINK_CELLS, rules.linkCells());
        assertEquals(LongSurveyGround.LINK_CELLS / 2, rules.perimeterCells(), "half a link off the hull's plates");
        assertEquals(2, rules.lattice().size());
        assertEquals(70, rules.lattice().get(0).x());
        assertEquals(1, rules.haulRoads().size(), "a one-cell road is no road");
        assertArrayEquals(new int[] {41, 15, 60, 15, 70, 15}, rules.haulRoads().get(0), "from the hull outward");
        assertEquals(List.of(new FallenHullSite.StartingStake(0, SurveyStake.Stage.RAISED)), rules.stakes());
        assertEquals(2, rules.egress().size());
        assertEquals(25f, rules.feedstock());
    }

    private static Apron apron(String name, Area ground, int x, int y) {
        LandingArea lift = new LandingArea(name, new LandingPad(x, y, 1, 1, LandingPad.Approach.WEST),
                new LandingPad(x, y + 3, 1, 1, LandingPad.Approach.WEST));
        return new Apron(name, ground, ground, lift);
    }
}
