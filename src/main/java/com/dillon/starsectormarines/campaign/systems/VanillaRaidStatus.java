package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.systems.StationingLapseSystem.DefenderStrengthSource;
import com.dillon.starsectormarines.campaign.systems.StationingLapseSystem.RaidStatusSource;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;

/**
 * Reads live vanilla state about one raided market: whether the raid the defence was
 * armed from has landed, and what ground strength vanilla counted for the defenders.
 *
 * <p>Landing is read off {@code MemFlags.RECENTLY_RAIDED} keyed by the raiding faction,
 * which is the flag {@code MarketCMD} sets when a raid actually resolves on the ground.
 * <b>An accepted edge:</b> a flag left by an <em>earlier</em> raid by the same faction
 * reads as a landing here. Vanilla's own {@code doGenericRaid} skips a market that is
 * already flagged, so in the common case the second error cancels the first — the raid
 * this defence was armed from would not have landed anyway.
 *
 * <p>Defender strength comes from {@code MarketCMD.getDefenderStr}, the same number
 * vanilla itself resolved the ground half with, so the stationed detachment's modifier
 * is already in it.
 */
public final class VanillaRaidStatus implements RaidStatusSource, DefenderStrengthSource {

    @Override
    public RaidStatus status(String marketId, String attackerFactionId) {
        MarketAPI market = VanillaRaidScan.market(marketId);
        if (market == null || attackerFactionId == null) return RaidStatus.REPELLED;
        if (Misc.flagHasReason(market.getMemoryWithoutUpdate(),
                MemFlags.RECENTLY_RAIDED, attackerFactionId)) {
            return RaidStatus.LANDED;
        }
        return VanillaRaidScan.anyLiveRaidTargeting(marketId, attackerFactionId)
                ? RaidStatus.LIVE : RaidStatus.REPELLED;
    }

    @Override
    public float defenderStrength(String marketId) {
        MarketAPI market = VanillaRaidScan.market(marketId);
        return market != null ? MarketCMD.getDefenderStr(market) : 0f;
    }
}
