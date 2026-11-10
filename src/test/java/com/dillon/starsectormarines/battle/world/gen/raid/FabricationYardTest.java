package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Props;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Light industry's fabrication works ({@link LightIndustryWorks}) and the yard it is dressed with
 * ({@link FabricationYard}): the process's order on the ground, and dressing that shows it without closing a way.
 * The yard is planned on a hand-drawn ground of eight halls; no map is generated.
 */
class FabricationYardTest {

    private static final int W = 70, H = 60;
    /** A road across the yard, between the shops and the finishing end. */
    private static final int ROAD_Y = 20;

    private static FabricationYard.Hall hall(RoomPurpose step, int x0, int y0, int x1, int y1, int[] door) {
        return new FabricationYard.Hall(step, x0, y0, x1, y1, List.of(door));
    }

    private static final List<FabricationYard.Hall> HALLS = List.of(
            hall(LightIndustryWorks.RECEIVING, 4, 46, 17, 53, new int[]{10, 45}),
            hall(LightIndustryWorks.STORES, 26, 46, 37, 52, new int[]{31, 45}),
            hall(LightIndustryWorks.SHOP, 4, 28, 15, 35, new int[]{9, 36}),
            hall(LightIndustryWorks.SHOP, 26, 28, 37, 35, new int[]{31, 36}),
            hall(LightIndustryWorks.ASSEMBLY, 46, 28, 63, 36, new int[]{45, 32}),
            hall(LightIndustryWorks.FINISHING, 46, 6, 57, 13, new int[]{51, 14}),
            hall(LightIndustryWorks.DISPATCH, 26, 6, 37, 12, new int[]{31, 13}),
            hall(LightIndustryWorks.CANTEEN, 4, 6, 13, 12, new int[]{8, 13}));

    private static boolean inHall(int x, int y) {
        for (FabricationYard.Hall h : HALLS) if (x >= h.x0() && x <= h.x1() && y >= h.y0() && y <= h.y1()) return true;
        return false;
    }

    private static final FabricationYard.Ground GROUND = new FabricationYard.Ground() {
        @Override public int width() { return W; }

        @Override public int height() { return H; }

        @Override public boolean open(int x, int y) { return !inHall(x, y); }

        @Override public boolean road(int x, int y) { return y == ROAD_Y || y == ROAD_Y + 1; }

        @Override public boolean building(int x, int y) { return inHall(x, y); }

        @Override public boolean inWorks(int x, int y) { return x >= 1 && y >= 1 && x < W - 1 && y < H - 1; }
    };

    /** Footprints as the art has them: trucks and mess tables 3 by 2, bin racks 2 by 1, ground stock 2 by 2. */
    private static final Function<String, int[]> FOOTPRINT = id -> {
        if (id.startsWith("doodad.parked-") || id.equals("doodad.mess-table-long")) return new int[]{3, 2};
        if (id.equals("doodad.stores-bin-rack") || id.equals("doodad.stores-cargo-loader")) return new int[]{2, 1};
        if (id.equals("machine-shop.lathe") || id.equals("machine-shop.mill") || id.endsWith("-bench")) {
            return new int[]{4, 2};
        }
        if (id.equals("main-stores.rack")) return new int[]{2, 6};
        if (id.equals("loading-bay.conveyor")) return new int[]{6, 2};
        if (id.startsWith("machine-shop.") || id.startsWith("loading-bay.insulated") || id.startsWith("main-stores.")
                || id.equals("factory-machines.coolant")) return new int[]{2, 2};
        if (id.equals("loading-bay.pallet-tug")) return new int[]{1, 3};
        return new int[]{1, 1};
    };

    private static FabricationYard.Dressing plan(long seed) {
        return FabricationYard.plan(HALLS, GROUND, seed, "line.", FOOTPRINT);
    }

    private static List<int[]> cells(Props.Placement p) {
        int[] fp = FOOTPRINT.apply(p.doodad());
        int fx = p.quarterTurns() % 2 == 0 ? fp[0] : fp[1], fy = p.quarterTurns() % 2 == 0 ? fp[1] : fp[0];
        List<int[]> out = new ArrayList<>();
        for (int y = p.y(); y < p.y() + fy; y++) for (int x = p.x(); x < p.x() + fx; x++) out.add(new int[]{x, y});
        return out;
    }

    /** The works is its process: receiving and stores by the feeders at the rear, dispatch by the haul gate. */
    @Test
    void theWorksStandsInTheOrderOfItsProcess() {
        FortressProgram works = new LightIndustryWorks().works(4);
        assertEquals(4, works.countOf(LightIndustryWorks.SHOP), "one shop per machine");
        assertEquals(LightIndustryWorks.DISPATCH_SHEDS, works.countOf(LightIndustryWorks.DISPATCH));
        assertEquals(1, works.countOf(LightIndustryWorks.CANTEEN));
        assertTrue(works.detached(), "each step is a hall of its own, for the parts line to run between");
        for (FortressBuilding b : works.buildings()) {
            if (b.purpose() == LightIndustryWorks.RECEIVING || b.purpose() == LightIndustryWorks.STORES) {
                assertEquals(Ward.REAR, b.ward(), b.purpose() + " stands by the feeders' gates");
            }
            if (b.purpose() == LightIndustryWorks.DISPATCH) assertEquals(Ward.FRONTAGE, b.ward(), "dispatch by the haul gate");
        }
        assertTrue(new LightIndustryWorks().works(5).apron() > new LightIndustryWorks().works(3).apron(), "more shops, more yard");
        assertEquals(LightIndustryWorks.PROCESS.size(), EnumSet.copyOf(LightIndustryWorks.PROCESS).size(),
                "every step is its own room, so the finished map tells them apart");
    }

    /** Light industry's site lays the fabrication works, every shop kept when it is fitted to the map. */
    @Test
    void theLightIndustrySiteLaysTheFabricationWorks() {
        RaidSiteTemplate site = RaidSiteTemplate.forIndustry("lightindustry");
        TargetProfile market = new TargetProfile(4, 5, 3, 1, "independent",
                EnumSet.of(EconomicFunction.HEAVY_INDUSTRY), SurfacePalette.ROCK, SettlementLink.ROAD);
        PrecinctPlan plan = site.lay(market, Fortification.Demand.UNSTATED, 4, 280, 168, new Random(3L));
        assertEquals("light-industry", plan.raidIndustry());
        assertEquals(4, plan.objective().program().countOf(LightIndustryWorks.SHOP), "fitted with every shop kept");
        assertEquals(1, plan.objective().program().countOf(LightIndustryWorks.FINISHING));
        assertEquals(1, plan.objective().program().countOf(LightIndustryWorks.CANTEEN));
    }

    /**
     * The parts line runs hall to hall in the order of the work, over the yard and never through a hall, and ends at
     * every hall downstream of receiving.
     */
    @Test
    void thePartsLineLinksTheStepsInOrder() {
        FabricationYard.Dressing d = plan(1L);
        assertFalse(d.line().isEmpty());
        for (Props.Placement p : d.line()) assertFalse(inHall(p.x(), p.y()), "the line never runs through a hall");
        for (FabricationYard.Hall h : HALLS) {
            if (h.step() == LightIndustryWorks.RECEIVING || h.step() == LightIndustryWorks.CANTEEN) continue;
            boolean fed = false;
            for (Props.Placement p : d.line()) {
                if (!p.doodad().equals("line.drive")) continue;
                int gx = Math.max(h.x0() - p.x(), Math.max(0, p.x() - h.x1()));
                int gy = Math.max(h.y0() - p.y(), Math.max(0, p.y() - h.y1()));
                fed |= gx + gy == 1;
            }
            assertTrue(fed, h.step() + " at " + h.x0() + "," + h.y0() + " is fed by the line");
        }
        for (Props.Placement leg : d.legs()) {
            assertFalse(GROUND.road(leg.x(), leg.y()), "no leg on a road");
            assertFalse(FabricationYard.nearDoor(doors(), leg.x(), leg.y(), FabricationYard.DOOR_CLEAR), "no leg at a door");
        }
    }

    /**
     * Everything set down stands on open works ground off the roads, the doors and the line, overlaps nothing, and
     * leaves a lane beyond it.
     */
    @Test
    void theDressingNeverClosesAWay() {
        FabricationYard.Dressing d = plan(2L);
        assertFalse(d.props().isEmpty());
        boolean[] used = new boolean[W * H];
        for (Props.Placement p : d.line()) used[p.y() * W + p.x()] = true;
        for (int[] c : d.paths()) used[c[1] * W + c[0]] = true;
        for (Props.Placement p : d.props()) {
            for (int[] c : cells(p)) {
                assertTrue(GROUND.open(c[0], c[1]) && !GROUND.road(c[0], c[1]), p.doodad() + " on open ground");
                assertFalse(FabricationYard.nearDoor(doors(), c[0], c[1], FabricationYard.DOOR_CLEAR), p.doodad() + " off the doors");
                assertFalse(used[c[1] * W + c[0]], p.doodad() + " overlaps nothing at " + c[0] + "," + c[1]);
                used[c[1] * W + c[0]] = true;
            }
        }
    }

    /** A lane too narrow for anything to stand against the wall and leave a way past is refused. */
    @Test
    void nothingStandsInANarrowGap() {
        boolean[] taken = new boolean[W * H];
        // Between the two shops (x 16..25) a prop against shop A's east wall leaves eight cells: it fits.
        assertTrue(FabricationYard.fits(GROUND, taken, List.of(), 16, 30, 1, 1, 3));
        // Two cells short of shop B, its lane would run into B's wall.
        assertFalse(FabricationYard.fits(GROUND, taken, List.of(), 23, 30, 2, 1, 3), "its lane runs into shop B");
    }

    /** The receiving dock's face is full of the feeding shops' trucks. */
    @Test
    void theDockQueuesTheFeedersTrucks() {
        assertEquals(FabricationYard.MAX_TRUCKS, trucksAtReceiving(plan(4L)));
    }

    /** The shifts' paths run from the canteen's door to each shop's. */
    @Test
    void theShiftsWalkFromTheCanteenToTheShops() {
        FabricationYard.Dressing d = plan(5L);
        for (FabricationYard.Hall h : HALLS) {
            if (h.step() != LightIndustryWorks.SHOP) continue;
            int[] door = h.doors().get(0);
            boolean reached = false;
            for (int[] c : d.paths()) reached |= c[0] == door[0] && c[1] == door[1];
            assertTrue(reached, "a path reaches the shop's door at " + door[0] + "," + door[1]);
        }
    }

    /** The stock yards' loads follow the work: cartons by receiving, cases by dispatch, totes among the shops. */
    @Test
    void theStockFollowsTheWork() {
        assertEquals("main-stores.cartons", FabricationYard.loadFor(HALLS, 10, 42));
        assertEquals("main-stores.cases", FabricationYard.loadFor(HALLS, 31, 16));
        assertEquals("loading-bay.insulated-tote", FabricationYard.loadFor(HALLS, 20, 31));
    }

    /** A load stands on a base set down at its corner: cartons and cases on pallets, totes on the roller line. */
    @Test
    void everyLoadStandsOnItsBase() {
        FabricationYard.Dressing d = plan(6L);
        assertFalse(d.loads().isEmpty());
        for (Props.Placement load : d.loads()) {
            boolean based = false;
            for (Props.Placement p : d.props()) based |= p.x() == load.x() && p.y() == load.y();
            assertTrue(based, load.doodad() + " at " + load.x() + "," + load.y() + " has its base");
        }
    }

    @Test
    void theSameSeedDressesTheSameYard() {
        FabricationYard.Dressing a = plan(9L), b = plan(9L);
        assertEquals(a.props(), b.props());
        assertEquals(a.line(), b.line());
        assertNotNull(a.paths());
    }

    private static int trucksAtReceiving(FabricationYard.Dressing d) {
        FabricationYard.Hall dock = HALLS.get(0);
        int n = 0;
        for (Props.Placement p : d.props()) {
            if (!p.doodad().startsWith("doodad.parked-")) continue;
            if (p.x() >= dock.x0() - 3 && p.x() <= dock.x1() + 3 && p.y() >= dock.y0() - 3 && p.y() <= dock.y1() + 3) n++;
        }
        return n;
    }

    private static List<int[]> doors() {
        List<int[]> out = new ArrayList<>();
        for (FabricationYard.Hall h : HALLS) out.addAll(h.doors());
        return out;
    }
}
