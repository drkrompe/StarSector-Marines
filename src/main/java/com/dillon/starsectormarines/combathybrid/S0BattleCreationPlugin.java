package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.campaign.BattleCreationPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;

/**
 * Minimal, campaign-location-independent battle definition for the S0 probe.
 *
 * <p>Selected by {@link CombatHybridCampaignPlugin} only while {@link S0BattleProbe}
 * has armed it, so normal campaign encounters keep the core
 * {@code BattleCreationPluginImpl}. Unlike the core plugin this does <em>not</em>
 * read the player fleet's containing location (no nebula / corona / asteroid /
 * planet scraping), so it works against the synthetic throwaway fleets
 * {@code S0BattleProbe} builds — the rosters come straight from the
 * {@link BattleCreationContext}, which is where requirement 1 (a chosen subset of
 * the player fleet) is enforced.
 *
 * <p>The one piece of real cross-engine work it does: install
 * {@link S0CompletionPlugin}, which is how the mod takes ownership of when the
 * battle ends (requirement 2).
 */
@DebugOnly
public class S0BattleCreationPlugin implements BattleCreationPlugin {

    private static final float HALF_MAP = 9000f;

    @Override
    public void initBattle(BattleCreationContext context, MissionDefinitionAPI loader) {
        CampaignFleetAPI player = context.getPlayerFleet();
        CampaignFleetAPI enemy = context.getOtherFleet();

        // useDefaultAI=false for the player (we pilot / command), true for the enemy.
        loader.initFleet(FleetSide.PLAYER, "ISS", context.getPlayerGoal(), false, 5);
        loader.initFleet(FleetSide.ENEMY, "", context.getOtherGoal(), true, 5);

        for (FleetMemberAPI m : player.getFleetData().getMembersListCopy()) {
            loader.addFleetMember(FleetSide.PLAYER, m);
        }
        for (FleetMemberAPI m : enemy.getFleetData().getMembersListCopy()) {
            loader.addFleetMember(FleetSide.ENEMY, m);
        }

        loader.initMap(-HALF_MAP, HALF_MAP, -HALF_MAP, HALF_MAP);
        loader.setHyperspaceMode(false);

        // Requirement 2: the mod owns when this battle is considered complete.
        loader.addPlugin(new S0CompletionPlugin());
    }

    @Override
    public void afterDefinitionLoad(CombatEngineAPI engine) {
        // Nothing extra; S0CompletionPlugin (added above) drives end-of-combat control.
    }
}
