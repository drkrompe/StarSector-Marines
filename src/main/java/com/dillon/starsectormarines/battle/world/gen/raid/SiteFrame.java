package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.StormWind;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;

/**
 * How a Raid site was laid, for an industry's own pieces ({@link RaidIndustry#sitePieces}): the places as laid, the
 * map, the seed's turn of the template ({@code mirrorX}: the company lands from the east; {@code mirrorY}: the works
 * stands south), the market's security, and the storm bank's wind or null where the town has none.
 */
public record SiteFrame(PrecinctPlan plan, boolean mirrorX, boolean mirrorY, int width, int height,
                        RaidSiteTemplate.Security security, StormWind.Side bankWind) {

    /** The works compound as laid. */
    public Precinct works() { return place(RaidSiteTemplate.WORKS); }

    /** The garrison as laid, or null on a market with no defence. */
    public Precinct garrison() { return place(RaidSiteTemplate.GARRISON); }

    /** A place by its name, or null. */
    public Precinct place(String name) {
        for (Precinct p : plan.precincts()) if (name.equals(p.name())) return p;
        return null;
    }
}
