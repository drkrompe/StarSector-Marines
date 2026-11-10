package com.dillon.starsectormarines.battle.world.gen.ruins;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules every works hall's plan keeps ({@code ruins-expedition.md}, A hall is laid out from its process), on the
 * hall sizes the ground lays: the least, the usual and the vast.
 */
class WorksHallPlanTest {

    private static final int[][] SIZES = {{32, 44}, {40, 76}, {44, 92}};
    private static final long[] SEEDS = {1L, 7L, 0xBEEFL};

    private static List<WorksHallPlan> plans() {
        List<WorksHallPlan> out = new ArrayList<>();
        for (WorksHall kind : WorksHall.values()) {
            for (int[] size : SIZES) {
                for (long seed : SEEDS) {
                    WorksHallPlan plan = kind.plan(size[0], size[1], seed);
                    if (plan != null) out.add(plan);
                }
            }
        }
        return out;
    }

    @Test
    void theHallStaysOneFloor() {
        for (WorksHallPlan plan : plans()) assertTrue(plan.oneFloor(), plan.kind() + " " + plan.width() + "x" + plan.length());
    }

    @Test
    void aHallFrameCanTravelFromDispatchToHead() {
        for (WorksHallPlan plan : plans()) assertTrue(plan.frameWay(), plan.kind() + " " + plan.width() + "x" + plan.length());
    }

    @Test
    void theMiddleWhereTheCorridorArrivesIsOpen() {
        for (WorksHallPlan plan : plans()) {
            int mx = plan.width() / 2, my = plan.length() / 2;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) assertTrue(plan.open(mx + dx, my + dy), plan.kind() + " middle");
            }
        }
    }

    @Test
    void nothingStandsOnTheEdgeWhereTheDoorsAre() {
        for (WorksHallPlan plan : plans()) {
            for (WorksHallPlan.Piece p : plan.pieces()) {
                if (p.layer() != WorksHallPlan.Layer.STAND) continue;
                assertTrue(p.x() >= 1 && p.y() >= 1 && p.x() + p.w() <= plan.width() - 1
                        && p.y() + p.h() <= plan.length() - 1, plan.kind() + " " + p);
            }
        }
    }

    @Test
    void everyPieceOfEquipmentServesAStepOfTheProcess() {
        for (WorksHallPlan plan : plans()) {
            Set<String> steps = new HashSet<>();
            for (WorksHallPlan.Station s : plan.flow()) steps.add(s.name());
            for (WorksHallPlan.Piece p : plan.pieces()) {
                if (p.layer() != WorksHallPlan.Layer.STAND) continue;
                assertTrue(steps.contains(p.station()) || WorksHallPlan.COLLAPSE.equals(p.station()),
                        plan.kind() + " " + p);
            }
        }
    }

    @Test
    void theProcessIsOneLineFromTheFeed() {
        for (WorksHallPlan plan : plans()) {
            List<WorksHallPlan.Station> flow = plan.flow();
            assertEquals("feed", flow.get(0).name(), plan.kind().name());
            for (int k = 0; k + 1 < flow.size(); k++) assertEquals(flow.get(k + 1).name(), flow.get(k).feeds());
            assertNull(flow.get(flow.size() - 1).feeds());
        }
    }

    @Test
    void theSameSeedPlansTheSameHall() {
        for (WorksHall kind : WorksHall.values()) {
            WorksHallPlan a = kind.plan(40, 76, 9L), b = kind.plan(40, 76, 9L);
            if (a == null) continue;
            assertEquals(a.pieces(), b.pieces(), kind.name());
        }
    }

    @Test
    void coverIsNeverFarAcrossTheWorkingFloor() {
        // Above the dispatch end, nobody runs more than a few cells in the open: cover comes from the work.
        for (WorksHallPlan plan : plans()) {
            assertTrue(plan.widestOpen(HallFrame.DISPATCH + 2) <= 8, plan.kind() + " " + plan.width() + "x" + plan.length()
                    + " open " + plan.widestOpen(HallFrame.DISPATCH + 2));
        }
    }
}
