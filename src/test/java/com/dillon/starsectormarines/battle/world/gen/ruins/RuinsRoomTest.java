package com.dillon.starsectormarines.battle.world.gen.ruins;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsRoomTest {

    /** The sizes a chamber and a store come in, turned or not. */
    private static final int[][] SIZES = {{5, 4}, {6, 5}, {5, 6}, {8, 6}, {10, 8}, {8, 10}};
    private static final WorksHall[] THEMES = {WorksHall.COLD, WorksHall.CORE, WorksHall.NONE, WorksHall.FORGE};

    private interface Check { void on(String at, WorksHallPlan plan, int kx, int ky); }

    private static List<String> every(Check check) {
        List<String> bad = new ArrayList<>();
        for (RuinsRoom kind : RuinsRoom.values()) {
            for (WorksHall works : THEMES) {
                for (int[] s : SIZES) {
                    for (long seed : new long[]{1, 2, 0xBEEF}) {
                        int kx = (s[0] - 1) / 2, ky = (s[1] - 1) / 2;
                        try {
                            check.on(kind + " " + works + " " + s[0] + "x" + s[1] + " seed " + seed,
                                    kind.plan(works, s[0], s[1], kx, ky, seed), kx, ky);
                        } catch (AssertionError e) {
                            bad.add(e.getMessage());
                        }
                    }
                }
            }
        }
        return bad;
    }

    @Test
    void everyRoomIsOneFloorWithItsMiddleOpen() {
        List<String> bad = every((at, plan, kx, ky) -> {
            assertTrue(plan.oneFloor(), at + ": not one floor");
            for (int y = ky - 1; y <= ky + 1; y++) {
                for (int x = kx - 1; x <= kx + 1; x++) {
                    assertTrue(!plan.in(x, y) || plan.open(x, y), at + ": middle closed at " + x + "," + y);
                }
            }
        });
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void everyStandingPieceServesAStepOfTheRoomOrItsCollapse() {
        List<String> bad = every((at, plan, kx, ky) -> {
            List<String> steps = new ArrayList<>();
            for (WorksHallPlan.Station s : plan.flow()) steps.add(s.name());
            for (WorksHallPlan.Piece p : plan.pieces()) {
                if (p.layer() != WorksHallPlan.Layer.STAND) continue;
                assertTrue(steps.contains(p.station()) || WorksHallPlan.COLLAPSE.equals(p.station()),
                        at + ": " + p.id() + " serves " + p.station());
            }
            assertTrue(plan.pieces().stream().anyMatch(p -> p.layer() == WorksHallPlan.Layer.STAND
                    && !WorksHallPlan.COLLAPSE.equals(p.station())), at + ": nothing stands in it");
        });
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void theNearestChamberIsTheControlRoomAndTheRestTakeTheOtherRolesInTurn() {
        assertEquals(RuinsRoom.CONTROL, RuinsRoom.chamberRole(0));
        assertEquals(List.of(RuinsRoom.WORKSHOP, RuinsRoom.LAB, RuinsRoom.CREW, RuinsRoom.WORKSHOP),
                List.of(RuinsRoom.chamberRole(1), RuinsRoom.chamberRole(2), RuinsRoom.chamberRole(3),
                        RuinsRoom.chamberRole(4)));
    }

    @Test
    void aRoomFacesItsHallAndItsKeptCellLandsWhereItWasPlanned() {
        assertEquals(1, RuinsRoom.facing(9, 2), "east");
        assertEquals(2, RuinsRoom.facing(1, -5), "south");
        for (int q = 0; q < 4; q++) {
            // A 6x4 plan: a piece on the cell the kept middle maps back to lands on the room's own cell.
            int wc = q % 2 == 0 ? 6 : 4, lc = q % 2 == 0 ? 4 : 6;
            int[] k = RuinsRoom.unturn(q, wc, lc, 3, 1);
            WorksHallPlan plan = new WorksHallPlan(WorksHall.NONE, wc, lc);
            plan.stand("x", null, k[0], k[1], 1, 1, null);
            WorksHallPlan.Piece p = plan.turned(q).pieces().get(0);
            assertArrayEquals(new int[]{3, 1}, new int[]{p.x(), p.y()}, "quarter " + q);
        }
    }

    @Test
    void aTurnCarriesTheNorthWallToTheSideItFaces() {
        WorksHallPlan plan = new WorksHallPlan(WorksHall.NONE, 6, 4);
        plan.stand("board", null, 1, 3, 3, 1, null);
        WorksHallPlan east = plan.turned(1);
        assertEquals(4, east.width());
        WorksHallPlan.Piece p = east.pieces().get(0);
        assertArrayEquals(new int[]{3, 2, 1, 3}, new int[]{p.x(), p.y(), p.w(), p.h()});
    }
}
