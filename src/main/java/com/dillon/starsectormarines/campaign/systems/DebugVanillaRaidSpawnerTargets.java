package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;

import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Market selection shared by the debug raid spawners: which colony of the player's a
 * debug raid comes for, and which hostile-held market it launches from.
 *
 * <p>Both spawners want the arc to play out in minutes rather than weeks, so both
 * choices are nearest-first. The one part of that which is pure — ranking candidates
 * once they have been reduced to a distance and a same-system flag — is
 * {@link #pickNearest}, and it is the only part a test can reach; everything else here
 * reads live sector objects.
 */
@DebugOnly
public final class DebugVanillaRaidSpawnerTargets {

    private DebugVanillaRaidSpawnerTargets() {}

    /** A market reduced to what the nearest-market choice actually depends on. */
    public static final class Candidate {
        public final String marketId;
        public final boolean sameSystem;
        public final float distance;

        public Candidate(String marketId, boolean sameSystem, float distance) {
            this.marketId = marketId;
            this.sameSystem = sameSystem;
            this.distance = distance;
        }
    }

    /**
     * Index of the best candidate, or -1 when there is none. A candidate in the
     * reference system beats one that is not; then the nearest wins; then the lowest
     * market id, so a tie does not depend on economy iteration order.
     */
    public static int pickNearest(List<Candidate> candidates) {
        if (candidates == null) return -1;
        int best = -1;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (c == null || c.marketId == null) continue;
            if (best < 0 || beats(c, candidates.get(best))) best = i;
        }
        return best;
    }

    private static boolean beats(Candidate a, Candidate b) {
        if (a.sameSystem != b.sameSystem) return a.sameSystem;
        if (a.distance != b.distance) return a.distance < b.distance;
        return a.marketId.compareTo(b.marketId) < 0;
    }

    /**
     * The player-owned market a debug raid should come for: one in the player fleet's
     * current system if there is one, else the nearest in hyperspace. Null when the
     * player owns no market a raid could reach.
     */
    public static MarketAPI playerTarget() {
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        LocationAPI here = fleet != null ? fleet.getContainingLocation() : null;
        Vector2f from = fleet != null ? fleet.getLocationInHyperspace() : null;

        List<MarketAPI> markets = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            if (!isRaidable(market)) continue;
            markets.add(market);
            candidates.add(new Candidate(market.getId(),
                    here != null && market.getPrimaryEntity().getContainingLocation() == here,
                    from != null ? Misc.getDistance(from, market.getLocationInHyperspace()) : 0f));
        }
        int pick = pickNearest(candidates);
        return pick >= 0 ? markets.get(pick) : null;
    }

    /**
     * The market a raid on {@code target} should launch from: the nearest one held by a
     * faction hostile to the player, falling back to the nearest pirate market when no
     * hostile faction holds one.
     *
     * <p>Nearest rather than first-found because travel time is distance, and the point
     * of a debug spawn is an arc a tester can watch end to end.
     */
    public static MarketAPI hostileSource(MarketAPI target) {
        if (target == null || Global.getSector() == null
                || Global.getSector().getEconomy() == null) {
            return null;
        }
        FactionAPI player = Global.getSector().getFaction(Factions.PLAYER);
        MarketAPI hostile = nearestSource(target, player, false);
        return hostile != null ? hostile : nearestSource(target, player, true);
    }

    private static MarketAPI nearestSource(MarketAPI target, FactionAPI player,
                                           boolean piratesOnly) {
        LocationAPI here = target.getPrimaryEntity() != null
                ? target.getPrimaryEntity().getContainingLocation() : null;
        Vector2f from = target.getLocationInHyperspace();

        List<MarketAPI> markets = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!isRaidable(market) || market.isPlayerOwned()) continue;
            FactionAPI faction = market.getFaction();
            if (faction == null || faction == player) continue;
            if (piratesOnly) {
                if (!Factions.PIRATES.equals(faction.getId())) continue;
            } else if (player == null || !faction.isHostileTo(player)) {
                continue;
            }
            markets.add(market);
            candidates.add(new Candidate(market.getId(),
                    here != null && market.getPrimaryEntity().getContainingLocation() == here,
                    from != null ? Misc.getDistance(from, market.getLocationInHyperspace()) : 0f));
        }
        int pick = pickNearest(candidates);
        return pick >= 0 ? markets.get(pick) : null;
    }

    /** The first industry of {@code market} a punitive expedition may raid, or null. */
    public static Industry raidableIndustry(MarketAPI market) {
        if (market == null || market.getIndustries() == null) return null;
        for (Industry industry : market.getIndustries()) {
            if (industry == null || industry.getSpec() == null) continue;
            if (industry.getSpec().hasTag(Industries.TAG_UNRAIDABLE)) continue;
            return industry;
        }
        return null;
    }

    /** A market a raid can actually be aimed at or launched from. */
    private static boolean isRaidable(MarketAPI market) {
        return market != null
                && market.getId() != null
                && market.getPrimaryEntity() != null
                && market.getStarSystem() != null
                && !market.isPlanetConditionMarketOnly()
                && !market.isHidden();
    }
}
