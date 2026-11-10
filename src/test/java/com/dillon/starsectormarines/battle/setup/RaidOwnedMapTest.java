package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementZoning;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.ops.OperationTier;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which owned map a Raid plays, and what ground it is laid against ({@code raid-mission.md}, The owned map). */
class RaidOwnedMapTest {

    /** Every industry a Raid site is laid for, one or more per site. */
    private static final List<String> SITE_INDUSTRIES = List.of("heavyindustry", "orbitalworks", "mining",
            "techmining", "refining", "fuelprod", "lightindustry", "farming", "aquaculture", "spaceport", "megaport",
            "militarybase", "patrolhq", "highcommand");

    @BeforeAll
    static void pools() throws Exception {
        Path mod = Path.of("mod");
        if (!StoryMapPools.installed()) {
            StoryMapPools.loadBuiltins(path -> new JSONObject(Files.readString(mod.resolve(path))));
        }
    }

    @Test
    void aWorksRaidPlaysItsSitesOwnMapAndEverySiteHasOne() {
        Set<StoryMap> maps = new HashSet<>();
        for (String industry : SITE_INDUSTRIES) {
            StoryMap map = RaidOwnedMap.mapFor(industry, RaidOwnedMap.MACHINES);
            assertEquals(RaidSiteTemplate.forIndustry(industry).key(), RaidOwnedMap.site(map).key(), industry);
            maps.add(map);
        }
        assertEquals(7, maps.size(), "one owned map per site");
        assertTrue(maps.stream().allMatch(RaidOwnedMap::isRaid));
    }

    @Test
    void aRaidForValuablesOrOnAnIndustryWithNoSitePlaysTheGenericMap() {
        assertSame(StoryMap.RAID, RaidOwnedMap.mapFor(null, 0));
        assertSame(StoryMap.RAID, RaidOwnedMap.mapFor("heavyindustry", 0), "no works, no site");
        assertSame(StoryMap.RAID, RaidOwnedMap.mapFor("population", RaidOwnedMap.MACHINES));
        assertNull(RaidOwnedMap.site(StoryMap.RAID));
        assertTrue(RaidOwnedMap.isRaid(StoryMap.RAID));
        assertTrue(!RaidOwnedMap.isRaid(StoryMap.COLONY_BREACH));
    }

    @Test
    void theReferenceMarketIsAGuardedTownWithASpaceportAndTheSitesIndustry() {
        TargetProfile reference = RaidOwnedMap.reference(StoryMap.RAID_MINING, SurfacePalette.ARID);
        assertSame(RaidSiteTemplate.Security.GUARDED, RaidSiteTemplate.Security.of(reference));
        // A Raid's target is a point of interest, which a remote map has none of.
        assertSame(PrecinctPlan.Sprawl.BALANCED, SettlementZoning.sprawlFor(reference.marketSize()));
        assertTrue(reference.functions().contains(EconomicFunction.MINING));
        assertTrue(reference.functions().contains(EconomicFunction.SPACEPORT));
        assertSame(SurfacePalette.ARID, reference.surface());
        // The generic map keeps industrial ground for a works on an industry with no site.
        assertTrue(RaidOwnedMap.reference(StoryMap.RAID, SurfacePalette.ARID).functions()
                .contains(EconomicFunction.HEAVY_INDUSTRY));
        assertThrows(IllegalArgumentException.class, () -> RaidOwnedMap.reference(StoryMap.HEART_BURN, SurfacePalette.ARID));
    }

    @Test
    void theGroundIsTheMapsWhateverTheRaidsTier() {
        RaidOwnedMap.Ground ground = RaidOwnedMap.Ground.of(StoryMap.RAID_FARMING, SurfacePalette.ARID);
        assertSame(RaidOwnedMap.TIER, ground.tier());
        assertSame(RaidOwnedMap.SCALE, ground.scale());
        assertEquals(RaidOwnedMap.MACHINES, ground.machines());
        for (OperationTier tier : OperationTier.values()) {
            assertEquals(RaidOwnedMap.MACHINES, BattleSetup.raidMachines(tier), tier.name());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new RaidOwnedMap.Ground(StoryMap.CORDON_LINE, SurfacePalette.ARID, ground.reference(), RaidOwnedMap.TIER, RaidOwnedMap.SCALE, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new RaidOwnedMap.Ground(StoryMap.RAID, SurfacePalette.ARID, ground.reference(), RaidOwnedMap.TIER, RaidOwnedMap.SCALE, 0));
    }

    @Test
    void everyCampaignRollPlaysTheOwnedSeedAndOnlyThatSeedIsOwned() {
        for (StoryMap map : StoryMap.values()) {
            if (!RaidOwnedMap.isRaid(map)) continue;
            long owned = StoryMapPools.pool(map).owned().seed();
            String industry = map == StoryMap.RAID ? null : industryOf(map);
            int machines = map == StoryMap.RAID ? 0 : RaidOwnedMap.MACHINES;
            assertEquals(owned, BattleSetup.raidSeed(industry, machines, 987_654L), map.id);
            assertNotNull(BattleSetup.ownedRaidEntry(map, owned), map.id);
            assertNull(BattleSetup.ownedRaidEntry(map, owned + 1), map.id + ": another seed is unvetted");
        }
    }

    private static String industryOf(StoryMap map) {
        for (String industry : SITE_INDUSTRIES) {
            if (RaidOwnedMap.mapFor(industry, RaidOwnedMap.MACHINES) == map) return industry;
        }
        throw new AssertionError(map + " has no industry");
    }
}
