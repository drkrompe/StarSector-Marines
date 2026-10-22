package com.dillon.starsectormarines.campaign.bloom;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.intel.RavBloomIntel;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The live sector behind {@link RavBloomSystem}: reads markets off the economy and pulls vanilla's
 * own levers, so what a bloom did to a market (a size lost, industries disrupted, unrest) stands
 * on the market without our state. Only the {@link RavBloomCondition} reads the ledger back.
 */
public final class SectorBloomWorld implements BloomWorld {

    private static final Logger LOG = Global.getLogger(SectorBloomWorld.class);

    /** Days a dark bloom's bites keep the colony's workings shut. */
    private static final float DISRUPTION_DAYS = 90f;
    private static final int UNREST_ON_DARK = 2;
    /** Incoming population weight read as full inflow. */
    private static final float INFLOW_FULL = 20f;

    @Override
    public long seed() {
        SectorAPI sector = Global.getSector();
        String seed = sector != null ? sector.getSeedString() : null;
        return seed != null ? RavBloomRules.mix(seed.hashCode(), seed.length()) : 0L;
    }

    @Override
    public List<BloomMarket> markets() {
        List<BloomMarket> out = new ArrayList<>();
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getEconomy() == null) return out;
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
        Vector2f core = coreCentre(markets);
        for (MarketAPI market : markets) {
            BloomMarket read = read(market, core);
            if (read != null) out.add(read);
        }
        out.sort(Comparator.comparing(m -> m.id));
        return out;
    }

    @Override
    public BloomMarket market(String marketId) {
        MarketAPI market = live(marketId);
        if (market == null) return null;
        return read(market, coreCentre(Global.getSector().getEconomy().getMarketsCopy()));
    }

    @Override
    public boolean hegemonyAlive() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getEconomy() == null) return false;
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (RavBloomRules.HEGEMONY.equals(market.getFactionId())) return true;
        }
        return false;
    }

    @Override
    public void erupted(RavBloom bloom) {
        MarketAPI market = live(bloom.marketId);
        if (market == null) return;
        if (!market.hasCondition(RavBloomCondition.ID)) market.addCondition(RavBloomCondition.ID);
        market.reapplyConditions();
        RavBloomIntel.notice(bloom, RavBloomIntel.Notice.ERUPTED);
    }

    @Override
    public void wentDark(RavBloom bloom, boolean disrupt) {
        MarketAPI market = live(bloom.marketId);
        if (market == null) return;
        if (disrupt) {
            disruptWorkings(market);
            RecentUnrest.get(market).add(UNREST_ON_DARK, Strings.get("ravBloomUnrestReason"));
        }
        market.reapplyConditions();
        RavBloomIntel.notice(bloom, RavBloomIntel.Notice.DARK);
    }

    @Override
    public void starved(RavBloom bloom) {
        MarketAPI market = live(bloom.marketId);
        if (market != null) market.reapplyConditions();
        RavBloomIntel.notice(bloom, RavBloomIntel.Notice.STARVED);
    }

    @Override
    public boolean reduceSize(String marketId) {
        MarketAPI market = live(marketId);
        if (market == null || market.getSize() <= RavBloomRules.MIN_SIZE) return false;
        CoreImmigrationPluginImpl.reduceMarketSize(market);
        disruptWorkings(market);
        LOG.info("Rav Bloom: " + marketId + " reduced to size " + market.getSize());
        return true;
    }

    @Override
    public void decivilize(String marketId) {
        MarketAPI market = live(marketId);
        if (market == null) return;
        LOG.info("Rav Bloom: " + marketId + " decivilised");
        DecivTracker.decivilize(market, false, true);
    }

    @Override
    public void ended(RavBloom bloom, int unrest) {
        MarketAPI market = live(bloom.marketId);
        if (market != null) {
            if (market.hasCondition(RavBloomCondition.ID)) market.removeCondition(RavBloomCondition.ID);
            if (unrest > 0) RecentUnrest.get(market).add(unrest, Strings.get("ravBloomUnrestReason"));
            market.reapplyConditions();
        }
        RavBloomIntel.notice(bloom, RavBloomIntel.Notice.ENDED);
    }

    @Override
    public void reputation(String factionId, int delta, RavBloom bloom) {
        SectorAPI sector = Global.getSector();
        if (sector == null || factionId == null || delta == 0) return;
        FactionAPI faction = sector.getFaction(factionId);
        if (faction == null || faction.isPlayerFaction()) return;
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = delta / 100f;
        if (delta < 0) impact.limit = RepLevel.INHOSPITABLE;
        sector.adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact, null, null, true, false), factionId);
    }

    private static void disruptWorkings(MarketAPI market) {
        for (Industry industry : market.getIndustries()) {
            if (workings(industry.getId())) industry.setDisrupted(DISRUPTION_DAYS, true);
        }
    }

    private static boolean workings(String industryId) {
        return Industries.MINING.equals(industryId) || Industries.REFINING.equals(industryId)
                || Industries.FARMING.equals(industryId) || Industries.AQUACULTURE.equals(industryId);
    }

    private static MarketAPI live(String marketId) {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getEconomy() == null || marketId == null) return null;
        MarketAPI market = sector.getEconomy().getMarket(marketId);
        return market != null && market.getPrimaryEntity() != null
                && !market.isPlanetConditionMarketOnly() ? market : null;
    }

    private static BloomMarket read(MarketAPI market, Vector2f core) {
        if (market == null || market.getPrimaryEntity() == null
                || market.isPlanetConditionMarketOnly() || market.getId() == null) {
            return null;
        }
        boolean workings = false;
        for (Industry industry : market.getIndustries()) workings |= workings(industry.getId());
        StarSystemAPI system = market.getStarSystem();
        Vector2f at = market.getLocationInHyperspace();
        float perLy = Misc.getUnitsPerLightYear();
        float x = at != null ? at.x / perLy : 0f;
        float y = at != null ? at.y / perLy : 0f;
        float fromCore = core != null && at != null ? Vector2f.sub(at, core, null).length() / perLy : 0f;
        return new BloomMarket(market.getId(), market.getName(),
                system != null ? system.getId() : null, market.getFactionId(), market.getSize(),
                (int) market.getDaysInExistence(), market.isHidden(), market.isPlayerOwned(),
                Misc.hasRuins(market), Misc.hasFarmland(market), workings, isCore(system),
                x, y, fromCore, traffic(market), inflow(market));
    }

    /** Accessibility, plus the port: where the cargo, and whatever rides in it, comes through. */
    private static float traffic(MarketAPI market) {
        float traffic = Math.max(0f, market.getAccessibilityMod().computeEffective(0f));
        if (market.hasIndustry(Industries.MEGAPORT)) traffic += 0.5f;
        else if (market.hasIndustry(Industries.SPACEPORT)) traffic += 0.25f;
        return traffic;
    }

    /** Growth pressure from immigration, 0 to 1: incoming weight against what reads as fast growth. */
    private static float inflow(MarketAPI market) {
        if (market.getIncoming() == null) return 0f;
        float incoming = market.getIncoming().getWeightValue();
        return Math.max(0f, Math.min(1f, incoming / INFLOW_FULL));
    }

    private static boolean isCore(StarSystemAPI system) {
        return system != null && (system.hasTag(Tags.THEME_CORE) || system.hasTag(Tags.THEME_CORE_POPULATED));
    }

    /** The centroid of the core systems' markets; the sector's origin when it has none. */
    private static Vector2f coreCentre(List<MarketAPI> markets) {
        float x = 0f;
        float y = 0f;
        int n = 0;
        for (MarketAPI market : markets) {
            if (!isCore(market.getStarSystem()) || market.getLocationInHyperspace() == null) continue;
            x += market.getLocationInHyperspace().x;
            y += market.getLocationInHyperspace().y;
            n++;
        }
        return n == 0 ? new Vector2f() : new Vector2f(x / n, y / n);
    }
}
