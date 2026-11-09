package com.dillon.starsectormarines.campaign.ruins;

import com.dillon.starsectormarines.campaign.bloom.RavBloomRules;
import com.dillon.starsectormarines.campaign.bloom.SectorBloomWorld;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a live ruins site's occupant ({@link RuinsOccupants}) from the sector: the save's seed, the market, its ruins
 * grade, its reservoir fact ({@link RavBloomRules#hasReservoir(long, String, boolean, boolean)}) and its system's
 * tags. The mission factory and the briefing both read it here, so they agree, and it is recomputed every time.
 */
public final class SectorRuinsOccupants {

    private SectorRuinsOccupants() {}

    /** The site's occupant; null when the market has no ruins. */
    public static RuinsOccupant of(MarketAPI market) {
        if (market == null || market.getId() == null) return null;
        RuinsGrade grade = gradeOf(market);
        if (grade == null) return null;
        long seed = SectorBloomWorld.sectorSeed();
        boolean reservoir = RavBloomRules.hasReservoir(seed, market.getId(), true, Misc.hasFarmland(market));
        StarSystemAPI system = market.getStarSystem();
        return RuinsOccupants.draw(seed, market.getId(), grade, reservoir, system != null ? system.getTags() : null);
    }

    /** The market's ruins grade, the largest if several conditions name one; null when it has no ruins. */
    public static RuinsGrade gradeOf(MarketAPI market) {
        if (market == null || market.getConditions() == null) return null;
        List<String> ids = new ArrayList<>();
        for (MarketConditionAPI condition : market.getConditions()) ids.add(condition.getId());
        return RuinsGrade.of(ids);
    }
}
