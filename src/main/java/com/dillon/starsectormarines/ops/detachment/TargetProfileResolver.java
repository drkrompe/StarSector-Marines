package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

/**
 * Extracts the {@link TargetProfile} for a battle from the vanilla economy — the
 * campaign → battle bridge's boundary read. Mirrors {@link DetachmentResolver}'s
 * scan-by-planet-name (and is the honest generalization of its
 * {@link DetachmentResolver#planetHasHeavyArmaments} boolean: one derived flag
 * becomes the whole structured read). Reads campaign state here so the battle
 * tier — and the procedural generator in particular — never has to.
 *
 * <p>Stateless statics. Returns {@link TargetProfile#NEUTRAL} for any battle
 * with no backing market (story ops, missing sector, unmatched name), so the
 * generator's defaults keep that map byte-identical to the pre-bridge output.
 *
 * <p>See {@code roadmap/campaign-battle-bridge/overview.md}.
 */
public final class TargetProfileResolver {

    private TargetProfileResolver() {}

    /**
     * Resolve the target world's profile by planet name (the same key
     * {@link DetachmentResolver#planetHasHeavyArmaments} uses). Falls back to
     * {@link TargetProfile#NEUTRAL} when there's nothing to read.
     */
    public static TargetProfile resolve(String targetPlanetName) {
        if (targetPlanetName == null || Global.getSector() == null) return TargetProfile.NEUTRAL;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.getPrimaryEntity() == null) continue;
            if (!targetPlanetName.equals(market.getPrimaryEntity().getName())) continue;
            return fromMarket(market);
        }
        return TargetProfile.NEUTRAL;
    }

    /** Distill a matched market into the bridge value object. */
    public static TargetProfile fromMarket(MarketAPI market) {
        return new TargetProfile(
                market.getSize(),
                Math.round(market.getStabilityValue()),
                defenseLevel(market),
                spaceportTier(market),
                market.getFactionId() != null ? market.getFactionId() : "");
    }

    /**
     * Weighted planetary-defense rating (~0–7). Ground-defense and station slots
     * each contribute their <em>upgraded</em> tier (the two industries in a slot
     * are mutually exclusive on a market), plus command + shield bonuses. This
     * is the {@link TargetProfile#defenseLevel()} the overwatch line scales off.
     */
    private static int defenseLevel(MarketAPI m) {
        int level = 0;
        if (m.hasIndustry(Industries.HEAVYBATTERIES)) level += 2;
        else if (m.hasIndustry(Industries.GROUNDDEFENSES)) level += 1;

        if (m.hasIndustry(Industries.STARFORTRESS)) level += 3;
        else if (m.hasIndustry(Industries.BATTLESTATION)) level += 2;
        else if (m.hasIndustry(Industries.ORBITALSTATION)) level += 1;

        if (m.hasIndustry(Industries.HIGHCOMMAND)) level += 1;
        if (m.hasIndustry(Industries.PLANETARYSHIELD)) level += 1;
        return level;
    }

    /** {@code 0} none, {@code 1} spaceport, {@code 2} megaport. */
    private static int spaceportTier(MarketAPI m) {
        if (m.hasIndustry(Industries.MEGAPORT)) return 2;
        if (m.hasIndustry(Industries.SPACEPORT)) return 1;
        return 0;
    }
}
