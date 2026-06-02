package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.BattleCreationPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * Routes {@code CampaignUIAPI.startBattle(...)} to {@link S0BattleCreationPlugin},
 * but <b>only while {@link S0BattleProbe} has armed it</b>.
 *
 * <p>{@code startBattle} resolves a battle-creation plugin by polling every
 * registered {@code CampaignPlugin} via {@code pickBattleCreationPlugin} and taking
 * the highest {@link PickPriority}. Returning {@code MOD_SPECIFIC} (above the core
 * {@code CORE_GENERAL}) wins the pick — but returning {@code null} unless armed
 * means normal campaign encounters fall straight through to the core
 * {@code BattleCreationPluginImpl}. The arm flag is raised for exactly the duration
 * of one {@code startBattle} call (see {@link S0BattleProbe#launch()}).
 *
 * <p>Registered transiently each game load by {@code StarsectorMarinesModPlugin},
 * gated by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public class CombatHybridCampaignPlugin extends BaseCampaignPlugin {

    public static final String PLUGIN_ID = "marines_combathybrid";

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public boolean isTransient() {
        return true;
    }

    @Override
    public PluginPick<BattleCreationPlugin> pickBattleCreationPlugin(SectorEntityToken opponent) {
        if (!S0BattleProbe.isArmed()) return null;
        return new PluginPick<>(new S0BattleCreationPlugin(), PickPriority.MOD_SPECIFIC);
    }
}
