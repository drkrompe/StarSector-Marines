package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPayout;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

import java.util.function.Function;

/**
 * The results screen's line for a Ruins vault prize ({@code ruins-expedition.md}, The vault and its prize): what came
 * out of the vault on a win, whole or broken by the vault's power at the breach, or that it stayed sealed.
 */
final class RuinsPrizeWording {

    private RuinsPrizeWording() {}

    /** The line for an outcome's prize, or null when the battle had none. */
    static String of(MissionOutcome outcome) {
        if (outcome == null || outcome.ruinsPrize == null) return null;
        return of(outcome.ruinsPrize, outcome.ruinsVaultPowered, outcome.victory, RuinsPrizeWording::itemName);
    }

    /**
     * @param powered  whether power reached the vault's seal when the breach was held
     * @param won      whether the haul boarded, which is when the prize comes out
     * @param itemName the game's name for a core or colony item id
     */
    static String of(RuinsVaultPrize prize, boolean powered, boolean won, Function<String, String> itemName) {
        if (!won) return Strings.get("resultsScreenPrizeSealed");
        return switch (prize.family()) {
            case GOODS -> Strings.format("resultsScreenPrizeGoods", RuinsVaultPayout.GOODS_BUNDLES);
            case TECH -> Strings.format("resultsScreenPrizeTech", RuinsVaultPayout.TECH_CACHES);
            case AI_CORE, COLONY_ITEM -> powered ? itemName.apply(prize.itemId())
                    : Strings.format("resultsScreenPrizeBroken", itemName.apply(prize.itemId()), prize.stakes() + 1);
        };
    }

    private static String itemName(String id) {
        SettingsAPI settings = Global.getSettings();
        if (settings == null || id == null) return id;
        try {
            if (settings.getSpecialItemSpec(id) != null) return settings.getSpecialItemSpec(id).getName();
        } catch (RuntimeException notAnItem) {
            // A core is a commodity, not a special item.
        }
        try {
            if (settings.getCommoditySpec(id) != null) return settings.getCommoditySpec(id).getName();
        } catch (RuntimeException unknown) {
            // Fall back to the id.
        }
        return id;
    }
}
