package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.StormBank;
import com.dillon.starsectormarines.battle.world.gen.StormWind;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import java.util.EnumSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Raid site on a desert storm world banks against the wind ({@code RaidSiteTemplate.lay}, {@link StormBank}). */
class RaidSiteStormBankTest {

    private static final int W = 280;
    private static final int H = 168;

    private static TargetProfile market(SurfacePalette surface, Climate climate) {
        return new TargetProfile(5, 5, 3, 1, "independent", EnumSet.of(EconomicFunction.HEAVY_INDUSTRY), surface,
                SettlementLink.ROAD).withClimate(climate);
    }

    private static PrecinctPlan lay(String industry, TargetProfile profile, long seed) {
        return RaidSiteTemplate.forIndustry(industry).lay(profile, Fortification.Demand.UNSTATED, 4, W, H,
                new Random(seed));
    }

    private static Climate storms(StormWind.Side wind) {
        return new Climate(EnumSet.of(Climate.Hazard.STORMS), wind);
    }

    /** Whether the plan's landscape holds or keeps the cell from growth. */
    private static boolean banked(PrecinctPlan plan, int x, int y) {
        boolean[][] held = RidgePlanner.heldGround(plan.landscape().held(), W, H, 0);
        if (held[x][y]) return true;
        for (Landscape.Track t : plan.landscape().kept()) if (t.contains(x, y)) return true;
        return false;
    }

    @Test
    void everyPlaceIsSeededLeewardAndTheCompanyNeverLandsOnTheWindwardSide() {
        for (StormWind.Side wind : StormWind.Side.values()) {
            for (long seed = 1; seed <= 6; seed++) {
                PrecinctPlan plan = lay("heavyindustry", market(SurfacePalette.ARID, storms(wind)), seed);
                assertFalse(plan.landscape().held().isEmpty(), wind + " seed " + seed + ": the bank is planned");
                for (Precinct p : plan.precincts()) {
                    assertFalse(banked(plan, p.seedX(), p.seedY()), wind + " seed " + seed + ": " + p.name()
                            + " is seeded on the bank's ground");
                }
                MapPlacement landing = plan.attackerFrom();
                boolean landsWindward = switch (wind) {
                    case WEST -> landing.x0() == 0f;
                    case EAST -> landing.x1() == 1f;
                    default -> false;
                };
                assertFalse(landsWindward, wind + " seed " + seed + ": the landing is on the windward side");
            }
        }
    }

    @Test
    void aMinesPitStopsShortOfTheBank() {
        for (StormWind.Side wind : StormWind.Side.values()) {
            for (long seed = 1; seed <= 6; seed++) {
                PrecinctPlan plan = lay("mining", market(SurfacePalette.ARID, storms(wind)), seed);
                Site pit = (Site) plan.layout().find(RaidSiteTemplate.PIT);
                assertNotNull(pit, wind + " seed " + seed);
                int depth = StormBank.depth(wind, W, H);
                boolean clear = switch (wind) {
                    case WEST -> pit.x0() >= depth;
                    case EAST -> pit.x1() <= W - 1 - depth;
                    case SOUTH -> pit.y0() >= depth;
                    case NORTH -> pit.y1() <= H - 1 - depth;
                };
                assertTrue(clear, wind + " seed " + seed + ": the pit reaches the bank's ground");
            }
        }
    }

    @Test
    void aWorldWithoutDesertStormsIsLaidAsItWas() {
        for (long seed = 1; seed <= 6; seed++) {
            PrecinctPlan calm = lay("heavyindustry", market(SurfacePalette.ARID, Climate.NONE), seed);
            PrecinctPlan frozen = lay("heavyindustry", market(SurfacePalette.FROZEN, storms(StormWind.Side.EAST)), seed);
            assertTrue(calm.landscape().held().isEmpty() && frozen.landscape().held().isEmpty());
            assertEquals(calm.attackerFrom(), frozen.attackerFrom(), "seed " + seed);
        }
    }

    @Test
    void theBankKeepsTheOrientationDrawsSoAWindAcrossTheLandingLeavesTheMapsMirrorAlone() {
        for (long seed = 1; seed <= 6; seed++) {
            PrecinctPlan calm = lay("heavyindustry", market(SurfacePalette.ARID, Climate.NONE), seed);
            PrecinctPlan north = lay("heavyindustry", market(SurfacePalette.ARID, storms(StormWind.Side.NORTH)), seed);
            assertEquals(calm.attackerFrom().x0() == 0f, north.attackerFrom().x0() == 0f,
                    "seed " + seed + ": a north wind changes no mirror");
        }
    }
}
