package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.Set;

/** Supported Starsector market-transfer implementation of throne writeback. */
public final class StarsectorThroneClaimWriteback implements ThroneClaimWriteback {

    interface SectorAccess {
        boolean available();
        boolean factionExists(String factionId);
        boolean marketExists(String marketId);
        String marketFactionId(String marketId);
        boolean marketEntitiesBelongTo(String marketId, String factionId);
        void transferMarket(String marketId, String factionId);
    }

    private final SectorAccess access;

    public StarsectorThroneClaimWriteback() {
        this(new GlobalSectorAccess());
    }

    StarsectorThroneClaimWriteback(SectorAccess access) {
        if (access == null) throw new IllegalArgumentException("access");
        this.access = access;
    }

    @Override
    public Result apply(String sourceFactionId, String resultFactionId, String marketId) {
        if (!access.available()) return Result.RETRY;
        if (!access.factionExists(sourceFactionId)
                || !access.factionExists(resultFactionId)
                || !access.marketExists(marketId)) {
            return Result.REJECTED;
        }

        String currentFactionId = access.marketFactionId(marketId);
        if (resultFactionId.equals(currentFactionId)) {
            if (access.marketEntitiesBelongTo(marketId, resultFactionId)) {
                return Result.ALREADY_APPLIED;
            }
            access.transferMarket(marketId, resultFactionId);
            return postcondition(marketId, resultFactionId)
                    ? Result.APPLIED : Result.RETRY;
        }
        if (!sourceFactionId.equals(currentFactionId)) return Result.REJECTED;

        access.transferMarket(marketId, resultFactionId);
        return postcondition(marketId, resultFactionId) ? Result.APPLIED : Result.RETRY;
    }

    private boolean postcondition(String marketId, String resultFactionId) {
        return resultFactionId.equals(access.marketFactionId(marketId))
                && access.marketEntitiesBelongTo(marketId, resultFactionId);
    }

    private static final class GlobalSectorAccess implements SectorAccess {

        @Override
        public boolean available() {
            return Global.getSector() != null && Global.getSector().getEconomy() != null;
        }

        @Override
        public boolean factionExists(String factionId) {
            SectorAPI sector = Global.getSector();
            return sector != null && sector.getFaction(factionId) != null;
        }

        @Override
        public boolean marketExists(String marketId) {
            return market(marketId) != null;
        }

        @Override
        public String marketFactionId(String marketId) {
            MarketAPI market = market(marketId);
            return market != null ? market.getFactionId() : null;
        }

        @Override
        public boolean marketEntitiesBelongTo(String marketId, String factionId) {
            MarketAPI market = market(marketId);
            if (market == null) return false;
            SectorEntityToken primary = market.getPrimaryEntity();
            if (primary != null && !factionId.equals(factionId(primary))) return false;
            Set<SectorEntityToken> connected = market.getConnectedEntities();
            if (connected == null) return true;
            for (SectorEntityToken entity : connected) {
                if (entity != null && !factionId.equals(factionId(entity))) return false;
            }
            return true;
        }

        @Override
        public void transferMarket(String marketId, String factionId) {
            MarketAPI market = market(marketId);
            if (market == null) return;
            market.setFactionId(factionId);
            SectorEntityToken primary = market.getPrimaryEntity();
            if (primary != null) primary.setFaction(factionId);
            Set<SectorEntityToken> connected = market.getConnectedEntities();
            if (connected == null) return;
            for (SectorEntityToken entity : connected) {
                if (entity != null) entity.setFaction(factionId);
            }
        }

        private static MarketAPI market(String marketId) {
            SectorAPI sector = Global.getSector();
            return sector != null && sector.getEconomy() != null
                    ? sector.getEconomy().getMarket(marketId) : null;
        }

        private static String factionId(SectorEntityToken entity) {
            FactionAPI faction = entity.getFaction();
            return faction != null ? faction.getId() : null;
        }
    }
}
