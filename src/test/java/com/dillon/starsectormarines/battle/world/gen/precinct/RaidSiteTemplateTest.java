package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.raid.MiningWorks;
import com.dillon.starsectormarines.battle.world.gen.raid.RaidIndustries;
import com.dillon.starsectormarines.battle.world.gen.raid.RaidIndustry;
import com.dillon.starsectormarines.battle.world.gen.layout.MissionLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules an industry's Raid site lays by ({@code raid-mission.md}, Site templates): districts apart, the works a
 * compound graded by security, the deep end away from the landing, varied by seed and market size. Plans only; no map
 * is generated.
 */
class RaidSiteTemplateTest {

    private static final int W = 280;
    private static final int H = 168;
    private static final RaidSiteTemplate SITE = RaidSiteTemplate.forIndustry("heavyindustry");
    private static final RaidSiteTemplate MINE = RaidSiteTemplate.forIndustry("mining");

    /**
     * Each industry is its own site: every id it names resolves to it and to no other, its key finds it again, and a
     * plan laid for it says which industry it was laid for, so the generation stages can ask it to dress its works.
     */
    @Test
    void eachIndustryIsItsOwnSiteAndThePlanSaysWhich() {
        Set<String> keys = new HashSet<>();
        for (RaidIndustry industry : RaidIndustries.ALL) {
            assertTrue(keys.add(industry.key()), "two sites share " + industry.key());
            assertEquals(industry, RaidIndustries.byKey(industry.key()));
            for (String id : industry.industryIds()) assertEquals(industry, RaidIndustries.forId(id), id);
        }
        assertEquals("mining", lay(MINE, market(4, 2), 1L).raidIndustry());
        assertEquals(null, RaidSiteTemplate.forIndustry("cryosanctum"));
    }

    private static TargetProfile market(int size, int defence) {
        return new TargetProfile(size, 5, defence, 1, "independent",
                EnumSet.of(EconomicFunction.HEAVY_INDUSTRY), SurfacePalette.ROCK, SettlementLink.ROAD);
    }

    private static PrecinctPlan lay(TargetProfile profile, long seed) {
        return lay(SITE, profile, seed);
    }

    private static PrecinctPlan lay(RaidSiteTemplate site, TargetProfile profile, long seed) {
        return site.lay(profile, Fortification.Demand.UNSTATED, 4, W, H, new Random(seed));
    }

    private static Precinct named(PrecinctPlan plan, String name) {
        for (Precinct precinct : plan.precincts()) if (precinct.name().equals(name)) return precinct;
        return null;
    }

    @Test
    void onlyAnIndustryWithASiteHasOne() {
        assertNotNull(RaidSiteTemplate.forIndustry("heavyindustry"));
        assertNotNull(RaidSiteTemplate.forIndustry("orbitalworks"));
        assertEquals("mining", RaidSiteTemplate.forIndustry("mining").key());
        assertEquals("mining", RaidSiteTemplate.forIndustry("techmining").key());
        assertNull(RaidSiteTemplate.forIndustry("waystation"));
        assertNull(RaidSiteTemplate.forIndustry(null));
    }

    @Test
    void securityReadsTheMarketsDefences() {
        assertEquals(RaidSiteTemplate.Security.OPEN, RaidSiteTemplate.Security.of(market(4, 1)));
        assertEquals(RaidSiteTemplate.Security.GUARDED, RaidSiteTemplate.Security.of(market(4, 2)));
        assertEquals(RaidSiteTemplate.Security.GUARDED,
                RaidSiteTemplate.Security.of(market(4, 0).withDefences(true, false, false)));
        assertEquals(RaidSiteTemplate.Security.FORTIFIED, RaidSiteTemplate.Security.of(market(4, 5)));
        assertEquals(RaidSiteTemplate.Security.FORTIFIED,
                RaidSiteTemplate.Security.of(market(4, 1).withDefences(false, true, false)));
    }

    /** Harder security walls the works harder and clears more ground before it; the operation's ceiling caps the wall. */
    @Test
    void securityGradesTheWorksWallAndBuffer() {
        Fortification open = RaidSiteTemplate.worksWall(RaidSiteTemplate.Security.OPEN, Fortification.Demand.UNSTATED);
        Fortification guarded = RaidSiteTemplate.worksWall(RaidSiteTemplate.Security.GUARDED,
                Fortification.Demand.UNSTATED);
        Fortification fortified = RaidSiteTemplate.worksWall(RaidSiteTemplate.Security.FORTIFIED,
                Fortification.Demand.UNSTATED);
        assertTrue(open.square() && guarded.square() && fortified.square(), "a works is a surveyed square compound");
        assertTrue(open.glacis() < guarded.glacis() && guarded.glacis() < fortified.glacis());
        assertTrue(open.wallHp() < guarded.wallHp() && guarded.wallHp() < fortified.wallHp());
        assertTrue(open.gates() > fortified.gates(), "a fenced works has more ways in than a fortified one");

        Fortification capped = RaidSiteTemplate.worksWall(RaidSiteTemplate.Security.FORTIFIED,
                new Fortification.Demand(Fortification.Strength.PICKET, 0));
        assertEquals(Fortification.PICKET.wallHp(), capped.wallHp());
        assertEquals(RaidSiteTemplate.Security.FORTIFIED.buffer(), capped.glacis(),
                "the ceiling caps the wall, not the ground the market clears");
    }

    /** The works is the raid's objective: a walled compound of machine halls, one per machine. */
    @Test
    void theWorksIsTheObjectiveAndHoldsTheMachines() {
        PrecinctPlan plan = SITE.lay(market(5, 3), Fortification.Demand.UNSTATED, 4, W, H, new Random(1L));
        Precinct works = plan.objective();
        assertEquals(RaidSiteTemplate.WORKS, works.name());
        assertEquals(Precinct.Boundary.WALLED, works.boundary());
        assertEquals(4, works.program().countOf(RoomPurpose.PARTS_CAGE));
        assertEquals(Standoff.INSET, plan.standoff());
    }

    /**
     * Districting: in every orientation the homes stand between the landing edge and the deep end, the industrial
     * quarter between the homes and the works, and the works and the garrison both at the deep end, apart from each
     * other.
     */
    @Test
    void theHomesStandBetweenTheLandingAndTheWorks() {
        for (String id : List.of("heavyindustry", "mining", "refining", "lightindustry", "farming", "spaceport",
                "militarybase")) {
            theHomesStandBetweenTheLandingAndTheWorks(RaidSiteTemplate.forIndustry(id));
        }
    }

    private static void theHomesStandBetweenTheLandingAndTheWorks(RaidSiteTemplate site) {
        Set<String> orientations = new HashSet<>();
        for (long seed = 0; seed < 24; seed++) {
            PrecinctPlan plan = lay(site, market(5, 3), seed);
            boolean fromWest = plan.attackerFrom().x0() == 0f;
            orientations.add(fromWest + "/" + (named(plan, RaidSiteTemplate.WORKS).seedY() > H / 2));
            int edge = fromWest ? 0 : W - 1;
            int homes = Math.abs(named(plan, RaidSiteTemplate.HOMES).seedX() - edge);
            int works = Math.abs(named(plan, RaidSiteTemplate.WORKS).seedX() - edge);
            int garrison = Math.abs(named(plan, RaidSiteTemplate.GARRISON).seedX() - edge);
            int industry = Math.abs(named(plan, RaidSiteTemplate.INDUSTRY).seedX() - edge);
            assertTrue(homes < works && homes < garrison,
                    "seed " + seed + ": the homes are not between the landing and the works");
            assertTrue(homes < industry && industry < works,
                    "seed " + seed + ": the industrial quarter does not stand between the homes and the works");
            Precinct yardsPlace = named(plan, RaidSiteTemplate.YARDS);
            if (yardsPlace != null) {
                int yards = Math.abs(yardsPlace.seedX() - edge);
                assertTrue(yards > works, "seed " + seed + ": the yards are not beyond the works");
            }
            assertTrue((named(plan, RaidSiteTemplate.WORKS).seedY() > H / 2)
                            != (named(plan, RaidSiteTemplate.GARRISON).seedY() > H / 2),
                    "seed " + seed + ": the works and the garrison share a flank");
        }
        assertEquals(4, orientations.size(), "the seed mirrors the site both ways");
    }

    @Test
    void theSameSeedLaysTheSameSite() {
        PrecinctPlan a = lay(market(5, 3), 7L), b = lay(market(5, 3), 7L);
        assertEquals(a.precincts().size(), b.precincts().size());
        for (int i = 0; i < a.precincts().size(); i++) {
            assertEquals(a.precincts().get(i).name(), b.precincts().get(i).name());
            assertEquals(a.precincts().get(i).seedX(), b.precincts().get(i).seedX());
            assertEquals(a.precincts().get(i).seedY(), b.precincts().get(i).seedY());
        }
        assertEquals(a.attackerFrom(), b.attackerFrom());
    }

    /** A bigger market is a bigger town and, from the threshold, an outskirts beyond the works. */
    @Test
    void theMarketsSizeGrowsTheTown() {
        assertEquals(GrownTrunkPlan.Profile.hamlet().junctionBudget,
                RaidSiteTemplate.homesGrowth(market(4, 3)).junctionBudget);
        assertEquals(GrownTrunkPlan.Profile.town().junctionBudget,
                RaidSiteTemplate.homesGrowth(market(RaidSiteTemplate.TOWN_FROM, 3)).junctionBudget);
        assertNull(named(lay(market(RaidSiteTemplate.OUTSKIRTS_FROM - 1, 3), 3L), RaidSiteTemplate.OUTSKIRTS));
        assertNotNull(named(lay(market(RaidSiteTemplate.OUTSKIRTS_FROM, 3), 3L), RaidSiteTemplate.OUTSKIRTS));
    }

    /** An undefended market has no garrison; its works still stands, fenced. */
    @Test
    void anUndefendedMarketHasNoGarrison() {
        PrecinctPlan plan = lay(market(4, 0), 2L);
        assertNull(named(plan, RaidSiteTemplate.GARRISON));
        assertEquals(RaidSiteTemplate.WORKS, plan.objective().name());
        assertFalse(plan.precincts().isEmpty());
    }

    /** Each hall stands as its own building, on yard enough to stand apart on. */
    @Test
    void aWorksProgramIsItsHallsStandingApart() {
        FortressProgram works = FortressProgram.works(5);
        assertEquals(5, works.countOf(RoomPurpose.PARTS_CAGE));
        assertEquals(0, works.airfields());
        assertTrue(works.detached());
        assertTrue(works.apron() > FortressProgram.works(3).apron(), "more halls, more yard");
        assertFalse(FortressProgram.garrison().detached(), "a garrison's buildings still corner into each other");
    }

    /** A mine's works is its processing chain: a mill line per machine, standing apart, and one of each other step. */
    @Test
    void aMinesWorksIsItsProcessingChain() {
        Precinct works = lay(MINE, market(5, 3), 1L).objective();
        assertEquals(RaidSiteTemplate.WORKS, works.name());
        assertEquals(Precinct.Boundary.WALLED, works.boundary());
        assertEquals(4, works.program().countOf(RoomPurpose.PARTS_CAGE));
        assertTrue(works.program().detached());
        for (RoomPurpose step : List.of(MiningWorks.RECEIVING, MiningWorks.STOCKPILE,
                MiningWorks.SMELTER, MiningWorks.LOADOUT)) {
            assertEquals(1, new MiningWorks().works(4).countOf(step), "one " + step + " in the processing chain");
        }
        assertTrue(new MiningWorks().works(5).apron() > new MiningWorks().works(3).apron(), "more mills, more yard");
        // The ore comes in from the pit at the deep end and leaves by the gate the attacker faces.
        for (FortressBuilding b : new MiningWorks().works(4).buildings()) {
            if (b.purpose() == MiningWorks.RECEIVING) assertEquals(Ward.REAR, b.ward(), "receiving faces the pit");
            if (b.purpose() == MiningWorks.LOADOUT) assertEquals(Ward.FRONTAGE, b.ward(), "loadout by the gate");
        }
    }

    /** Every industry a raid can name has its site, and each site's works holds one machine per machine asked for. */
    @Test
    void everyIndustryHasASiteWhoseWorksHoldsTheMachines() {
        Set<String> keys = new HashSet<>();
        for (String id : List.of("heavyindustry", "orbitalworks", "mining", "techmining", "refining", "fuelprod",
                "lightindustry", "farming", "aquaculture", "spaceport", "megaport", "militarybase", "patrolhq",
                "highcommand")) {
            RaidSiteTemplate site = RaidSiteTemplate.forIndustry(id);
            assertNotNull(site, id);
            keys.add(site.key());
            Precinct works = lay(site, market(5, 3), 4L).objective();
            assertEquals(RaidSiteTemplate.WORKS, works.name(), id);
            assertEquals(4, works.program().countOf(RoomPurpose.PARTS_CAGE), id);
            assertTrue(works.program().detached(), id);
        }
        assertEquals(7, keys.size(), "seven sites, one per kind of industry");
        assertEquals(PrecinctCharacter.FARMSTEAD, RaidSiteTemplate.forIndustry("farming").industryCharacter(),
                "a farm stands among farmsteads, not sheds");
    }

    /**
     * A mine holds its pit's ground beside the head works, at the deep end and on the works' flank, clear of the
     * works' claim, in every orientation; no other site holds one.
     */
    @Test
    void aMineHoldsItsPitBesideTheHeadWorks() {
        for (long seed = 0; seed < 16; seed++) {
            PrecinctPlan plan = lay(MINE, market(5, 3), seed);
            Site pit = (Site) plan.layout().find(RaidSiteTemplate.PIT);
            assertNotNull(pit, "seed " + seed);
            assertEquals(SitePurpose.GROUND, pit.purpose(), "nothing grows on the pit");
            Precinct works = named(plan, RaidSiteTemplate.WORKS);
            boolean fromWest = plan.attackerFrom().x0() == 0f;
            int reach = RaidSiteTemplate.worksReach(RaidSiteTemplate.MINE_FIT,
                    RaidSiteTemplate.Security.of(market(5, 3)), W, H);
            if (fromWest) assertTrue(pit.x0() > works.seedX() + reach, "seed " + seed + ": the pit overlaps the works");
            else assertTrue(pit.x1() < works.seedX() - reach, "seed " + seed + ": the pit overlaps the works");
            assertTrue(pit.x1() - pit.x0() >= 20, "seed " + seed + ": a pit too narrow to read");
            boolean worksNorth = works.seedY() > H / 2;
            int inner = Math.round(RaidSiteTemplate.PIT_INNER * H);
            assertTrue(worksNorth ? pit.y0() >= inner : pit.y1() <= H - inner,
                    "seed " + seed + ": the pit reaches the garrison's flank");
            assertNull(named(plan, RaidSiteTemplate.YARDS), "the pit is a mine's deep end");
        }
        assertNull(lay(SITE, market(5, 3), 1L).layout().find(RaidSiteTemplate.PIT));
    }

    /**
     * The chokepoint's tells: every site runs a haul road from the works to its industry and feeders off the map
     * from the works' far gates, two of them, or one where a mine's pit is the deep end.
     */
    @Test
    void theWorksIsFedFromOffTheMap() {
        for (long seed = 0; seed < 8; seed++) {
            MissionLayout layout = lay(SITE, market(5, 3), seed).layout();
            assertNotNull(layout.find(RaidSiteTemplate.HAUL_ROAD));
            assertEquals(2, feeders(layout), "seed " + seed);
            assertEquals(1, feeders(lay(MINE, market(5, 3), seed).layout()), "seed " + seed);
        }
    }

    private static int feeders(MissionLayout layout) {
        int n = 0;
        for (Piece piece : layout.pieces()) if (piece instanceof Road && piece.name().startsWith(RaidSiteTemplate.FEEDER)) n++;
        return n;
    }

    /** The hub is fed by more sites the bigger the market, and always by more than one. */
    @Test
    void aBiggerMarketFeedsTheHubFromMoreSites() {
        assertTrue(RaidSiteTemplate.feedersFor(3) >= 2);
        assertTrue(RaidSiteTemplate.feedersFor(1) >= 2, "a hub with one feeder is not a hub");
        for (int size = 3; size < 9; size++) {
            assertTrue(RaidSiteTemplate.feedersFor(size + 1) > RaidSiteTemplate.feedersFor(size), "size " + size);
        }
        Set<String> keys = new HashSet<>();
        for (String id : List.of("heavyindustry", "mining", "refining", "lightindustry", "farming", "spaceport",
                "militarybase")) {
            keys.add(RaidSiteTemplate.forIndustry(id).briefingKey());
        }
        assertEquals(7, keys.size(), "every site has its own briefing line");
    }

    /** A mine's ore yards are sparser than heavy industry's quarter. */
    @Test
    void aMinesYardsAreSparserThanAnIndustrialQuarter() {
        assertTrue(MINE.industryJunctions() < SITE.industryJunctions());
    }

    /** The homes' streets reach less far than a settlement's, so the town keeps to its district. */
    @Test
    void theHomesStreetsAreShort() {
        GrownTrunkPlan.Profile homes = RaidSiteTemplate.homesGrowth(market(7, 3));
        assertTrue(homes.armLenHiFrac < GrownTrunkPlan.Profile.town().armLenHiFrac);
        assertEquals(RaidSiteTemplate.HOMES_ARM_HI, homes.armLenHiFrac);
    }
}
