package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import org.lwjgl.util.vector.Vector2f;

import java.util.Comparator;
import java.util.List;

/**
 * The one place order for the opening: where Second Wind's yard is and where Zoey Venn keeps her office
 * ({@code early-operation-nouns.md}). Jangala first, then the rest of Corvus, then an Independent world, then
 * whatever is nearest Corvus; market id breaks the last tie so the answer never depends on enumeration order.
 * A yard also needs a spaceport, counted whether or not it is disrupted today: the start port is chosen once
 * and kept, and a temporary disruption is the yard's business when the order is placed, not the story's.
 *
 * <p>A re-pick after that port is lost prefers the start port's own system above everything else, so the company,
 * which starts with no fuel, never has to cross hyperspace while a qualifying port remains where it already is.
 */
public final class OpeningPort {
    public static final String JANGALA = "jangala";
    public static final String CORVUS = "Corvus";

    public enum Use { YARD, OFFICE }

    private OpeningPort() { }

    public static MarketAPI resolve(SectorAPI sector, Use use) {
        return resolve(sector, use, null);
    }

    /** {@code home}: the start port's system, ranked ahead of the ordinary order; null for none. */
    public static MarketAPI resolve(SectorAPI sector, Use use, StarSystemAPI home) {
        if (sector == null || sector.getEconomy() == null) return null;
        FactionAPI player = sector.getPlayerFaction();
        StarSystemAPI corvus = sector.getStarSystem(CORVUS);
        Vector2f anchor = home != null ? home.getLocation() : corvus == null ? null : corvus.getLocation();
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
        return markets == null ? null : markets.stream().filter(m -> eligible(m, player, use))
                .min(Comparator.comparingInt((MarketAPI m) -> home != null && system(m) == home ? 0 : 1)
                        .thenComparing(order(anchor))).orElse(null);
    }

    /** A visible, populated, non-player planetary market whose owner is not hostile; a yard adds a spaceport. */
    public static boolean eligible(MarketAPI market, FactionAPI player, Use use) {
        if (market == null || market.isHidden() || market.getSize() <= 0 || market.isPlayerOwned()
                || market.isPlanetConditionMarketOnly() || market.getPlanetEntity() == null) return false;
        if (market.getFaction() == null || player != null && market.getFaction().isHostileTo(player)) return false;
        return use != Use.YARD || hasSpaceport(market);
    }

    public static boolean hasSpaceport(MarketAPI market) {
        return market.getIndustries() != null && market.getIndustries().stream()
                .anyMatch(i -> i != null && i.getSpec() != null && i.getSpec().hasTag("spaceport"));
    }

    static Comparator<MarketAPI> order(Vector2f corvus) {
        return Comparator.comparingInt(OpeningPort::tier)
                .thenComparingDouble(m -> distance(m, corvus))
                .thenComparing(MarketAPI::getId);
    }

    /** 0 Jangala, 1 elsewhere in Corvus, 2 an Independent world, 3 anything else. */
    static int tier(MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        if (JANGALA.equals(planet.getId())) return 0;
        StarSystemAPI system = planet.getStarSystem();
        if (system != null && CORVUS.equals(system.getBaseName())) return 1;
        return Factions.INDEPENDENT.equals(market.getFactionId()) ? 2 : 3;
    }

    private static StarSystemAPI system(MarketAPI market) {
        return market.getPlanetEntity().getStarSystem();
    }

    private static double distance(MarketAPI market, Vector2f corvus) {
        StarSystemAPI system = system(market);
        Vector2f at = system == null ? null : system.getLocation();
        if (corvus == null || at == null) return Double.MAX_VALUE;
        return Math.hypot(at.x - corvus.x, at.y - corvus.y);
    }
}
