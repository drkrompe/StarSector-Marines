package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Props;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Plot;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Use;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules the farm world's hub is laid by ({@link GrainHub}): the process in its wards, the gallery's order, the
 * fields and ponds, the spill and the haulers' queue. Pure planning; no map is generated.
 */
class GrainHubTest {

    private static Ward wardOf(FortressProgram program, Object purpose) {
        for (FortressBuilding b : program.buildings()) if (b.purpose() == purpose) return b.ward();
        return null;
    }

    /** The harvest comes in at the rear, where the farm roads enter, is worked in the yard, and leaves by the gate. */
    @Test
    void theHubStandsInTheOrderTheGrainPasses() {
        FortressProgram hub = GrainHub.program(4);
        assertEquals(Ward.REAR, wardOf(hub, GrainHub.INTAKE));
        assertEquals(Ward.REAR, wardOf(hub, GrainHub.WEIGHBRIDGE), "every load is weighed in at the intake");
        assertEquals(Ward.YARD, wardOf(hub, GrainHub.DRYER));
        assertEquals(Ward.YARD, wardOf(hub, GrainHub.SILO));
        assertEquals(Ward.YARD, wardOf(hub, GrainHub.MILL));
        assertEquals(Ward.FRONTAGE, wardOf(hub, GrainHub.COLD_STORE));
        assertEquals(Ward.FRONTAGE, wardOf(hub, GrainHub.LOADOUT));
        assertEquals(GrainHub.INTAKE, GrainHub.CHAIN.get(0));
        assertEquals(GrainHub.LOADOUT, GrainHub.CHAIN.get(GrainHub.CHAIN.size() - 1));
        assertEquals(4, hub.countOf(GrainHub.SILO), "one bin per machine");
        assertTrue(hub.detached());
        assertTrue(GrainHub.program(5).apron() > GrainHub.program(3).apron(), "more bins, more yard");
    }

    /** Farming and aquaculture both lay the grain hub, which holds one bin per machine the raid wrecks. */
    @Test
    void farmingAndAquacultureLayTheGrainHub() {
        for (String id : List.of("farming", "aquaculture")) {
            RaidIndustry industry = RaidIndustries.forId(id);
            assertInstanceOf(FarmingWorks.class, industry, id);
            assertEquals(4, industry.works(4).countOf(RoomPurpose.PARTS_CAGE), id);
            assertEquals(1, industry.works(4).countOf(GrainHub.INTAKE), id);
        }
    }

    /** The gallery runs along the battery nearest-first and passes over steps with no hall standing. */
    @Test
    void theGalleryRunsAlongTheBattery() {
        List<List<int[]>> halls = new ArrayList<>();
        for (int i = 0; i < GrainHub.CHAIN.size(); i++) halls.add(new ArrayList<>());
        halls.get(0).add(new int[]{0, 0, 9, 9});                       // intake
        halls.get(2).add(new int[]{60, 0, 69, 9});                     // bins, out of order
        halls.get(2).add(new int[]{20, 0, 29, 9});
        halls.get(2).add(new int[]{40, 0, 49, 9});
        halls.get(5).add(new int[]{80, 0, 89, 9});                     // loadout; no dryer, mill or cold store
        List<Integer> xs = new ArrayList<>();
        for (int[] hall : GrainHub.run(halls)) xs.add(hall[0]);
        assertEquals(List.of(0, 20, 40, 60, 80), xs);
    }

    /** A farm world's fields stand in crops at harvest; wild ground stays wild, and the draw repeats. */
    @Test
    void theFieldsStandInCrops() {
        List<Plot> surveyed = new ArrayList<>();
        for (int i = 0; i < 40; i++) surveyed.add(new Plot(i * 20, 0, i * 20 + 15, 15, Use.PASTURE, true, false));
        surveyed.add(new Plot(0, 30, 15, 45, Use.WILD, true, false));
        List<Plot> fields = GrainHub.crops(surveyed, 9L);
        Set<Use> uses = EnumSet.noneOf(Use.class);
        for (int i = 0; i < 40; i++) uses.add(fields.get(i).use());
        assertEquals(EnumSet.of(Use.GRAIN, Use.ROW_CROP, Use.FALLOW), uses, "grain, row crops and stubble only");
        assertEquals(Use.WILD, fields.get(40).use());
        assertEquals(fields, GrainHub.crops(surveyed, 9L));
    }

    /** A pond keeps a dry bund inside its plot, so the ground round it is never cut off; a small plot holds none. */
    @Test
    void aPondKeepsItsBund() {
        Plot plot = new Plot(10, 20, 29, 35, Use.GRAIN, true, false);
        List<int[]> water = GrainHub.pond(plot);
        assertFalse(water.isEmpty());
        for (int[] c : water) {
            assertTrue(c[0] >= plot.x0() + GrainHub.BUND && c[0] <= plot.x1() - GrainHub.BUND, "x " + c[0]);
            assertTrue(c[1] >= plot.y0() + GrainHub.BUND && c[1] <= plot.y1() - GrainHub.BUND, "y " + c[1]);
        }
        assertTrue(GrainHub.pond(new Plot(0, 0, 5, 5, Use.GRAIN, true, false)).isEmpty());
    }

    /** Spilt grain lies on the yard round a building, never on it and never beyond its reach. */
    @Test
    void grainIsSpiltRoundTheBuildingOnly() {
        int[] box = {20, 20, 35, 27};
        BiPredicate<Integer, Integer> yard = (x, y) -> x != 36; // a wall along one side
        List<int[]> spill = GrainHub.spill(box, yard, new Random(1));
        assertFalse(spill.isEmpty());
        for (int[] c : spill) {
            boolean inside = c[0] >= box[0] && c[0] <= box[2] && c[1] >= box[1] && c[1] <= box[3];
            assertFalse(inside, "on the building");
            assertTrue(c[0] >= box[0] - GrainHub.SPILL_REACH && c[0] <= box[2] + GrainHub.SPILL_REACH);
            assertTrue(c[1] >= box[1] - GrainHub.SPILL_REACH && c[1] <= box[3] + GrainHub.SPILL_REACH);
            assertTrue(c[0] != 36, "on a cell that is not yard");
        }
    }

    /**
     * The haulers queue along the intake's wall on the asked side, a gap off it, apart from each other, each on yard
     * with walkable ground all round it, so the queue never closes a way.
     */
    @Test
    void theHaulersQueueAlongTheIntake() {
        int[] box = {20, 20, 39, 27};
        BiPredicate<Integer, Integer> open = (x, y) -> !(x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3]);
        for (Side side : Side.values()) {
            List<Props.Placement> queue = GrainHub.haulerQueue(box, side, open, open, GrainHub.QUEUE);
            assertFalse(queue.isEmpty(), side.name());
            assertTrue(queue.size() <= GrainHub.QUEUE);
            Set<Long> taken = new HashSet<>();
            for (Props.Placement p : queue) {
                boolean alongX = p.quarterTurns() % 2 == 0;
                int fw = alongX ? GrainHub.HAULER_LONG : GrainHub.HAULER_SHORT;
                int fh = alongX ? GrainHub.HAULER_SHORT : GrainHub.HAULER_LONG;
                for (int dx = -1; dx <= fw; dx++) {
                    for (int dy = -1; dy <= fh; dy++) {
                        int x = p.x() + dx, y = p.y() + dy;
                        assertTrue(open.test(x, y), side + ": a hauler or its ring on the intake");
                        boolean inside = dx >= 0 && dy >= 0 && dx < fw && dy < fh;
                        if (inside) assertTrue(taken.add(((long) x << 32) | y), side + ": two haulers overlap");
                    }
                }
                // On the asked side of the intake.
                if (side.dx > 0) assertTrue(p.x() > box[2]);
                if (side.dx < 0) assertTrue(p.x() + fw - 1 < box[0]);
                if (side.dy > 0) assertTrue(p.y() > box[3]);
                if (side.dy < 0) assertTrue(p.y() + fh - 1 < box[1]);
            }
            for (Props.Placement p : queue) {
                // No hauler's ring is another's footprint: each keeps its own way round.
                boolean alongX = p.quarterTurns() % 2 == 0;
                int fw = alongX ? GrainHub.HAULER_LONG : GrainHub.HAULER_SHORT;
                int fh = alongX ? GrainHub.HAULER_SHORT : GrainHub.HAULER_LONG;
                int ringOnHauler = 0;
                for (int dx = -1; dx <= fw; dx++) {
                    for (int dy = -1; dy <= fh; dy++) {
                        boolean inside = dx >= 0 && dy >= 0 && dx < fw && dy < fh;
                        if (!inside && taken.contains(((long) (p.x() + dx) << 32) | (p.y() + dy))) ringOnHauler++;
                    }
                }
                assertEquals(0, ringOnHauler, side + ": haulers parked nose to tail with no gap");
            }
        }
        BiPredicate<Integer, Integer> walled = (x, y) -> false;
        assertTrue(GrainHub.haulerQueue(box, Side.NORTH, walled, walled, GrainHub.QUEUE).isEmpty(),
                "no room, no queue");
    }

    /**
     * A leg house's bins stand off its walls on yard, never on each other, each with walkable ground all round it, and
     * no more than asked.
     */
    @Test
    void theBinsStandRoundTheLegHouse() {
        int[] box = {30, 30, 37, 37};
        BiPredicate<Integer, Integer> open = (x, y) -> !(x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3]);
        List<Props.Placement> bins = GrainHub.bins(box, open, open, GrainHub.BINS_PER_HOUSE);
        assertEquals(GrainHub.BINS_PER_HOUSE, bins.size());
        int b = GrainHub.BIN_SIDE;
        Set<Long> footprints = new HashSet<>();
        for (Props.Placement p : bins) {
            assertEquals(GrainHub.BIN, p.doodad());
            for (int dx = 0; dx < b; dx++) {
                for (int dy = 0; dy < b; dy++) {
                    assertTrue(footprints.add(((long) (p.x() + dx) << 32) | (p.y() + dy)), "two bins overlap");
                }
            }
        }
        for (Props.Placement p : bins) {
            for (int dx = -1; dx <= b; dx++) {
                for (int dy = -1; dy <= b; dy++) {
                    int x = p.x() + dx, y = p.y() + dy;
                    assertTrue(open.test(x, y), "a bin or its ring on the house");
                    boolean ring = dx < 0 || dy < 0 || dx == b || dy == b;
                    if (ring) assertFalse(footprints.contains(((long) x << 32) | y), "bins with no way between");
                }
            }
        }
        assertTrue(GrainHub.bins(box, (x, y) -> false, open, 3).isEmpty(), "no yard, no bins");
    }

    /** A pivot field grows in rings inside the pivot's reach, which stays inside the plot, and grass beyond it. */
    @Test
    void aPivotFieldGrowsInRings() {
        Plot plot = new Plot(10, 10, 31, 29, Use.GRAIN, true, false);
        int[] centre = GrainHub.pivotCentre(plot);
        int r = GrainHub.pivotRadius(plot);
        assertTrue(centre[0] - r > plot.x0() && centre[0] + r < plot.x1(), "reach past the plot's sides");
        assertTrue(centre[1] - r > plot.y0() && centre[1] + r < plot.y1(), "reach past the plot's ends");
        assertEquals(GroundKind.GRASS, GrainHub.pivotGround(plot, plot.x0(), plot.y0()), "a corner is under crop");
        Set<GroundKind> along = EnumSet.noneOf(GroundKind.class);
        for (int x = centre[0] + 1; x <= centre[0] + r; x++) along.add(GrainHub.pivotGround(plot, x, centre[1]));
        assertEquals(EnumSet.of(GroundKind.SAND, GroundKind.DIRT), along, "no crop rings and wheel tracks");
    }

    /**
     * Country fields lie wholly on free ground, keep their headland from each other, run their rows along their long
     * side, and leave the mask as it was.
     */
    @Test
    void longFieldsTileTheOpenCountry() {
        boolean[][] free = new boolean[70][40];
        for (int x = 0; x < 70; x++) for (int y = 0; y < 40; y++) free[x][y] = !(x >= 25 && x <= 30);
        List<Plot> plots = GrainHub.countryPlots(free, new int[][]{{20, 12}, {8, 20}}, 2);
        assertFalse(plots.isEmpty());
        for (Plot p : plots) {
            int fw = p.x1() - p.x0() + 1, fh = p.y1() - p.y0() + 1;
            assertEquals(fw >= fh, p.rowsAlongX(), "rows across the field");
            for (int x = p.x0(); x <= p.x1(); x++) for (int y = p.y0(); y <= p.y1(); y++) assertTrue(free[x][y]);
            for (Plot q : plots) {
                if (p == q) continue;
                boolean apart = p.x1() + 2 < q.x0() || q.x1() + 2 < p.x0() || p.y1() + 2 < q.y0() || q.y1() + 2 < p.y0();
                assertTrue(apart, "two fields with no headland between");
            }
        }
        assertTrue(plots.stream().anyMatch(p -> p.x1() - p.x0() + 1 == 20), "the larger field first");
        assertTrue(plots.stream().anyMatch(p -> p.x1() - p.x0() + 1 == 8), "the smaller fields fill in");
        assertFalse(free[25][0] || !free[0][0], "the mask changed");
    }
}
