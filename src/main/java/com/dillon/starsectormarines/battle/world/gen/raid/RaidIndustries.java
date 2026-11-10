package com.dillon.starsectormarines.battle.world.gen.raid;

import java.util.List;
import java.util.Locale;

/**
 * Every industry a Raid can name, each its own site ({@link RaidIndustry}). A new industry is one class and one line
 * here.
 */
public final class RaidIndustries {

    /** The industries in the order the owner took them: heavy industry first, then the rest. */
    public static final List<RaidIndustry> ALL = List.of(
            new HeavyIndustryWorks(),
            new MiningWorks(),
            new RefiningWorks(),
            new LightIndustryWorks(),
            new FarmingWorks(),
            new PortWorks(),
            new MilitaryWorks());

    private RaidIndustries() { }

    /** The site for a market industry id, or null for an industry that has none. */
    public static RaidIndustry forId(String industryId) {
        if (industryId == null) return null;
        String id = industryId.toLowerCase(Locale.ROOT);
        for (RaidIndustry industry : ALL) if (industry.industryIds().contains(id)) return industry;
        return null;
    }

    /** The site by its key ({@link RaidIndustry#key()}), or null. */
    public static RaidIndustry byKey(String key) {
        if (key == null) return null;
        for (RaidIndustry industry : ALL) if (industry.key().equals(key)) return industry;
        return null;
    }
}
