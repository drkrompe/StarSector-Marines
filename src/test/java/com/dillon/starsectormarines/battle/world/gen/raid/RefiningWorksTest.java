package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refinery's site laws ({@link RefiningWorks}): its works runs in the order the stock passes, and its tank farm
 * stands between the works and its industrial quarter, off the haul road, wherever the seed turns the site.
 */
class RefiningWorksTest {

    private static final int W = 280;
    private static final int H = 168;

    @Test
    void theWorksRunsInTheOrderTheStockPasses() {
        FortressProgram program = new RefiningWorks().works(4);
        assertEquals(4, program.countOf(RefiningWorks.UNIT_ROOM), "a unit per machine");
        for (FortressBuilding building : program.buildings()) {
            if (building.purpose() == RefiningWorks.RECEIVING_ROOM) {
                assertEquals(Ward.REAR, building.ward(), "the pipelines land at the deep end");
            }
            if (building.purpose() == RefiningWorks.PUMP_ROOM) {
                assertEquals(Ward.FRONTAGE, building.ward(), "the product leaves toward the town");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new RefiningWorks().works(0));
    }

    /** Canonical frame: works deep in the east, its quarter toward the homes in the west, the farm between them. */
    @Test
    void theFarmStandsBetweenTheWorksAndItsQuarterOffTheHaulRoad() {
        int reach = 30;
        Site farm = RefiningWorks.tankFarm(220, 100, 110, 95, reach, false, false, W, H);
        assertEquals(SitePurpose.GROUND, farm.purpose());
        assertTrue(farm.x1() <= 220 - reach - RefiningWorks.FARM_GAP, "the farm keeps off the works' claim");
        assertTrue(farm.x0() >= 110 + RefiningWorks.FARM_INDUSTRY_GAP, "the farm keeps off its quarter's seed");
        assertTrue(farm.y0() >= 100 + RefiningWorks.FARM_ROAD_GAP, "the farm keeps off the haul road's rows");
        assertTrue(farm.y1() <= H - 1 - RefiningWorks.FARM_MARGIN, "the farm stops short of the map's edge");
    }

    @Test
    void theFarmTakesTheSideOfTheHaulRoadWithTheRoom() {
        // Seeds high on the map leave the room on the inner side, but never past the middle's band.
        Site farm = RefiningWorks.tankFarm(220, 150, 110, 150, 30, false, false, W, H);
        assertTrue(farm.y1() <= 150 - RefiningWorks.FARM_ROAD_GAP);
        assertTrue(farm.y0() >= Math.round(RefiningWorks.FARM_INNER * H), "the farm reaches the garrison's ground");
    }

    @Test
    void aMirroredSiteMirrorsItsFarm() {
        Site canonical = RefiningWorks.tankFarm(220, 100, 110, 95, 30, false, false, W, H);
        Site mirrored = RefiningWorks.tankFarm(W - 1 - 220, H - 1 - 100, W - 1 - 110, H - 1 - 95, 30, true, true, W, H);
        assertEquals(W - 1 - canonical.x1(), mirrored.x0());
        assertEquals(W - 1 - canonical.x0(), mirrored.x1());
        assertEquals(H - 1 - canonical.y1(), mirrored.y0());
        assertEquals(H - 1 - canonical.y0(), mirrored.y1());
    }

    /** Laid through the template: a refining raid holds its farm on the layout, fed from off the map, with no yards. */
    @Test
    void aRefiningRaidHoldsItsTankFarm() {
        RaidSiteTemplate refinery = RaidSiteTemplate.forIndustry("refining");
        assertEquals("refining", RaidSiteTemplate.forIndustry("fuelprod").key());
        TargetProfile market = new TargetProfile(5, 5, 3, 1, "independent",
                EnumSet.of(EconomicFunction.REFINING), SurfacePalette.ROCK, SettlementLink.ROAD);
        int reach = RaidSiteTemplate.worksReach(RefiningWorks.WORKS_FIT, RaidSiteTemplate.Security.of(market), W, H);
        for (long seed = 0; seed < 8; seed++) {
            PrecinctPlan plan = refinery.lay(market, Fortification.Demand.UNSTATED, 4, W, H, new Random(seed));
            Site farm = (Site) plan.layout().find(RefiningWorks.TANK_FARM);
            assertNotNull(farm, "seed " + seed);
            Precinct works = null;
            for (Precinct p : plan.precincts()) {
                if (RaidSiteTemplate.WORKS.equals(p.name())) works = p;
                assertTrue(!RaidSiteTemplate.YARDS.equals(p.name()), "the farm is a refinery's yards");
            }
            assertNotNull(works);
            boolean deepEast = plan.attackerFrom().x0() == 0f;
            assertTrue(deepEast ? farm.x1() < works.seedX() - reach : farm.x0() > works.seedX() + reach,
                    "seed " + seed + ": the farm stands before the works, toward the town");
            int feeders = 0;
            for (Piece piece : plan.layout().pieces()) {
                if (piece instanceof Road && piece.name().startsWith(RaidSiteTemplate.FEEDER)) feeders++;
            }
            assertEquals(2, feeders, "seed " + seed + ": crude and volatiles come in from two edges");
        }
        assertNull(RaidSiteTemplate.forIndustry("heavyindustry")
                .lay(market, Fortification.Demand.UNSTATED, 4, W, H, new Random(1)).layout()
                .find(RefiningWorks.TANK_FARM));
    }
}
