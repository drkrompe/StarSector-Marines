package com.dillon.starsectormarines.ops.intel;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a {@link PlanetAPI} into a {@link PlanetIntel} snapshot.
 *
 * <p>Defense score is a weighted sum of military + orbital structure tiers, adjusted by
 * stability. The bucketing into {@link DefenseLevel} is intentionally chunky — five tiers
 * map cleanly to {@code RiskLevel} buckets downstream. The exact weights are balanced for
 * playtest, not theoretical purity.
 */
public final class IntelReader {

    /** Score thresholds for each tier (inclusive lower bound). */
    private static final int LIGHT_MIN    = 1;
    private static final int MODERATE_MIN = 4;
    private static final int HEAVY_MIN    = 8;
    private static final int FORTRESS_MIN = 13;

    private IntelReader() {}

    public static PlanetIntel read(PlanetAPI planet) {
        if (planet == null) return PlanetIntel.EMPTY;
        MarketAPI market = planet.getMarket();
        if (market == null) return PlanetIntel.EMPTY;

        String factionId = market.getFactionId();
        int size = market.getSize();
        float stability = market.getStabilityValue();

        List<String> conditions = new ArrayList<>();
        for (MarketConditionAPI c : market.getConditions()) {
            if (c != null && c.getId() != null) conditions.add(c.getId());
        }

        List<IndustryEntry> industries = new ArrayList<>();
        for (Industry ind : market.getIndustries()) {
            if (ind == null) continue;
            SpecialItemData special = ind.getSpecialItem();
            industries.add(new IndustryEntry(
                    ind.getId(),
                    ind.getCurrentName(),
                    ind.isDisrupted(),
                    special != null ? special.getId() : null));
        }

        int defenseScore = scoreDefense(market, stability);
        DefenseLevel defenseLevel = bucket(defenseScore);

        return new PlanetIntel(factionId, size, stability, conditions, industries,
                defenseLevel, defenseScore);
    }

    /**
     * Weighted sum of military + orbital fortifications. Stability nudges the result
     * — a destabilized market projects less force than its structure list would suggest.
     */
    private static int scoreDefense(MarketAPI market, float stability) {
        int score = 0;

        // Ground military
        if (market.hasIndustry(Industries.PATROLHQ))        score += 1;
        if (market.hasIndustry(Industries.MILITARYBASE))    score += 3;
        if (market.hasIndustry(Industries.HIGHCOMMAND))     score += 6;
        if (market.hasIndustry(Industries.GROUNDDEFENSES))  score += 2;
        if (market.hasIndustry(Industries.HEAVYBATTERIES))  score += 4;
        if (market.hasIndustry(Industries.PLANETARYSHIELD)) score += 3;

        // Orbital fortifications — count any variant in each tier (low/mid/high all weigh the same).
        if (hasAnyVariant(market, Industries.ORBITALSTATION,
                Industries.ORBITALSTATION_MID, Industries.ORBITALSTATION_HIGH))   score += 2;
        if (hasAnyVariant(market, Industries.BATTLESTATION,
                Industries.BATTLESTATION_MID, Industries.BATTLESTATION_HIGH))     score += 4;
        if (hasAnyVariant(market, Industries.STARFORTRESS,
                Industries.STARFORTRESS_MID, Industries.STARFORTRESS_HIGH))       score += 6;

        // Lion's Guard adds elite garrison on top of regular military.
        if (market.hasIndustry(Industries.LIONS_GUARD))     score += 3;

        // Stability adjustment — low stability projects less; very high adds a small bonus.
        if (stability < 3f)      score -= 2;
        else if (stability > 7f) score += 1;

        return Math.max(0, score);
    }

    private static boolean hasAnyVariant(MarketAPI market, String... ids) {
        for (String id : ids) if (market.hasIndustry(id)) return true;
        return false;
    }

    private static DefenseLevel bucket(int score) {
        if (score >= FORTRESS_MIN) return DefenseLevel.FORTRESS;
        if (score >= HEAVY_MIN)    return DefenseLevel.HEAVY;
        if (score >= MODERATE_MIN) return DefenseLevel.MODERATE;
        if (score >= LIGHT_MIN)    return DefenseLevel.LIGHT;
        return DefenseLevel.UNDEFENDED;
    }
}
