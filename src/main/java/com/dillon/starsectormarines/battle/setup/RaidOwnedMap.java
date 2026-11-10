package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The map a generic Raid owns ({@code raid-mission.md}, The owned map): one seed per Raid site, laid out against a
 * fixed <b>reference market</b> on the pool's home palette, never against the market being raided, and skinned for
 * the raided world afterwards ({@code PlanetSkin}). The owner: "All of these should use a single seed, that we own and
 * make good. They should be skinned for the planet and make sense for the planet."
 *
 * <p>The raided market keeps everything that is not ground: who defends, how many (the tier's share of its
 * garrison), their air, mechs and emplacement crews. The reference market decides the ground: the town's size and
 * districts, the works compound's wall and glacis, the map's size and the works' machines.
 *
 * <p>Each value here is the owner's pick ({@code raid-owned-reference} a, {@code raid-owned-sites} a,
 * {@code raid-owned-scale} a); a different one changes this class and re-screens the seeds.
 */
public final class RaidOwnedMap {

    private RaidOwnedMap() { }

    /** The reference market's size: a town's streets and an outskirts suburb ({@code RaidSiteTemplate}). */
    public static final int MARKET_SIZE = 6;
    /** The reference market's defence rating: guarded security, a wall and gatehouses round the works. */
    public static final int DEFENCE_LEVEL = 3;
    /** The reference market's stability: an ordinary, quiet colony. Read by nothing that lays ground today. */
    public static final int STABILITY = 5;
    /** The reference market's owner. Only the emplacements' style reads it; the raided market's faction defends. */
    public static final String FACTION = "independent";
    /** The tier the owned map's fortification is read at, whatever tier the raid is. */
    public static final OperationTier TIER = OperationTier.ESTABLISHED;
    /**
     * The owned map's size, whatever tier the raid is: 400x240, the owner's pick ({@code raid-owned-scale} a) after
     * finding 280x168 too small to skirt the base.
     */
    public static final MapScale SCALE = MapScale.VAST;
    /** The risk the map's fortification ceiling is read at. */
    public static final RiskLevel RISK = RiskLevel.MEDIUM;
    /** Machines in the works on every owned map: one hall each, so the count is the map's, not the tier's. */
    public static final int MACHINES = 4;

    /** Each site's map, keyed by an industry the site is laid for. */
    private static final Map<StoryMap, String> SITE_INDUSTRY = Map.of(
            StoryMap.RAID_HEAVY_INDUSTRY, "heavyindustry",
            StoryMap.RAID_MINING, "mining",
            StoryMap.RAID_REFINING, "refining",
            StoryMap.RAID_LIGHT_INDUSTRY, "lightindustry",
            StoryMap.RAID_FARMING, "farming",
            StoryMap.RAID_SPACEPORT, "spaceport",
            StoryMap.RAID_MILITARY, "militarybase");

    /** Each site's own economic function, added to the reference market's base. */
    private static final Map<StoryMap, EconomicFunction> SITE_FUNCTION = Map.of(
            StoryMap.RAID_HEAVY_INDUSTRY, EconomicFunction.HEAVY_INDUSTRY,
            StoryMap.RAID_MINING, EconomicFunction.MINING,
            StoryMap.RAID_REFINING, EconomicFunction.REFINING,
            StoryMap.RAID_LIGHT_INDUSTRY, EconomicFunction.HEAVY_INDUSTRY,
            StoryMap.RAID_FARMING, EconomicFunction.AGRICULTURE,
            StoryMap.RAID_SPACEPORT, EconomicFunction.SPACEPORT,
            StoryMap.RAID_MILITARY, EconomicFunction.MILITARY);

    /**
     * The ground an owned Raid map is laid on: the map and the home palette it is generated on, against
     * {@link RaidOwnedMap#reference} at {@link #TIER}'s fortification, {@link #SCALE} and {@link #MACHINES}.
     */
    public record Ground(StoryMap map, SurfacePalette home) {
        public Ground {
            if (!isRaid(map)) throw new IllegalArgumentException(map + " is not a Raid map");
        }

        /** {@code map}'s owned ground on {@code home}. */
        public static Ground of(StoryMap map, SurfacePalette home) { return new Ground(map, home); }

        /** The reference market the map is laid against. */
        public TargetProfile reference() { return RaidOwnedMap.reference(map, home); }

        /** The tier the map's fortification is read at. */
        public OperationTier tier() { return TIER; }

        /** The map's size. */
        public MapScale scale() { return SCALE; }

        /** Machines in the works. */
        public int machines() { return MACHINES; }

        /** The site this ground is laid as, or {@code null} for the generic map. */
        public RaidSiteTemplate site() { return RaidOwnedMap.site(map); }
    }

    /** Whether {@code map} is a Raid's owned map. */
    public static boolean isRaid(StoryMap map) {
        return map == StoryMap.RAID || SITE_INDUSTRY.containsKey(map);
    }

    /**
     * The owned map a raid plays: its industry's site's map when the raid wrecks a works ({@code machines > 0}) on an
     * industry with a site, else the generic map. A raid for valuables, or on an industry with no site, plays
     * {@link StoryMap#RAID}.
     */
    public static StoryMap mapFor(String industryId, int machines) {
        RaidSiteTemplate site = machines > 0 ? RaidSiteTemplate.forIndustry(industryId) : null;
        if (site == null) return StoryMap.RAID;
        for (Map.Entry<StoryMap, String> e : SITE_INDUSTRY.entrySet()) {
            if (RaidSiteTemplate.forIndustry(e.getValue()).key().equals(site.key())) return e.getKey();
        }
        throw new IllegalStateException("Raid site " + site.key() + " owns no map");
    }

    /** The site {@code map} is laid as, or {@code null} for the generic map. */
    public static RaidSiteTemplate site(StoryMap map) {
        String industry = SITE_INDUSTRY.get(map);
        return industry == null ? null : RaidSiteTemplate.forIndustry(industry);
    }

    /**
     * The reference market {@code map} is laid against, on {@code home}: size {@value #MARKET_SIZE}, guarded, a
     * spaceport and a market, the site's own industry, and heavy industry on the generic map so a works on an
     * industry with no site still has industrial ground to stand on. No climate, basin, signature or garrison: the
     * skin and the raided market supply those.
     */
    public static TargetProfile reference(StoryMap map, SurfacePalette home) {
        if (!isRaid(map)) throw new IllegalArgumentException(map + " is not a Raid map");
        Set<EconomicFunction> functions = EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.COMMERCE,
                EconomicFunction.SPACEPORT);
        functions.add(map == StoryMap.RAID ? EconomicFunction.HEAVY_INDUSTRY : SITE_FUNCTION.get(map));
        return new TargetProfile(MARKET_SIZE, STABILITY, DEFENCE_LEVEL, 1, FACTION, functions, home,
                SettlementLink.ROAD).withDefences(true, false, false);
    }
}
