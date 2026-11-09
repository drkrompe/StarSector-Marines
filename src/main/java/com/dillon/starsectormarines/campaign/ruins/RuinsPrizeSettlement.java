package com.dillon.starsectormarines.campaign.ruins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Random;

/**
 * Pays a won expedition's vault prize into the fleet's cargo ({@code ruins-expedition.md}, The vault and its prize):
 * the draw ({@link RuinsVaultPrizes}), what it pays given the vault's power at the breach ({@link RuinsVaultPayout}),
 * and the grants ({@link RuinsPrizeGrant}) made through the game's own cargo and salvage roller. The caller pays
 * exactly once.
 */
public final class RuinsPrizeSettlement {

    private static final Logger LOG = Global.getLogger(RuinsPrizeSettlement.class);
    /** How many times a tech cache rerolls its drop group's {@code nothing} before it gives up. */
    static final int TECH_REROLLS = 16;

    private RuinsPrizeSettlement() {}

    /**
     * @param poweredAtBreach whether power reached the vault's seal before the breach was held
     * @return the grants paid; empty for no prize or no cargo
     */
    public static List<RuinsPrizeGrant> pay(CargoAPI cargo, RuinsVaultPrize prize, boolean poweredAtBreach,
                                            long sectorSeed, String marketId) {
        if (cargo == null || prize == null || marketId == null) return List.of();
        RuinsVaultPayout payout = RuinsVaultPayout.of(sectorSeed, marketId, prize, poweredAtBreach);
        List<RuinsPrizeGrant> grants = RuinsPrizeGrant.of(payout, prize, sectorSeed, marketId);
        for (RuinsPrizeGrant grant : grants) {
            switch (grant.kind()) {
                case COMMODITY -> cargo.addCommodity(grant.id(), grant.quantity());
                case SPECIAL_ITEM -> cargo.addSpecial(new SpecialItemData(grant.id(), null), 1);
                case TECH_ROLL -> cargo.addAll(techRoll(grant));
            }
        }
        LOG.info("RuinsPrizeSettlement: " + marketId + " paid " + prize.theme() + (payout.broken(prize)
                ? " broken into " + payout.techCaches().size() + " tech caches" : "") + ": " + grants);
        return grants;
    }

    /** One roll of the grant's drop group, rerolled past vanilla's {@code nothing} so a cache is never empty. */
    private static CargoAPI techRoll(RuinsPrizeGrant grant) {
        Random random = new Random(grant.seed());
        DropData drop = new DropData();
        drop.group = grant.id();
        drop.chances = 1;
        CargoAPI rolled = null;
        for (int i = 0; i < TECH_REROLLS; i++) {
            rolled = SalvageEntity.generateSalvage(random, 1f, 1f, 1f, 1f, List.of(), List.of(drop));
            if (rolled != null && !rolled.isEmpty()) return rolled;
        }
        return rolled != null ? rolled : Global.getFactory().createCargo(true);
    }
}
