package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.FactoryAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import org.apache.log4j.Logger;

/**
 * S0 probe entry point — launches a vanilla combat instance straight from the
 * campaign map (triggered by {@link CombatHybridInputListener}).
 *
 * <p><b>Requirement 1 (a chosen subset of the player fleet):</b> the player side is
 * a synthetic throwaway {@link CampaignFleetAPI} built from copies of the first
 * {@link #PLAYER_SUBSET_SIZE} combat-ready ships in the real player fleet. Copies
 * (not the real members) so a probe battle can't damage the player's actual ships
 * or feed losses back to the campaign. Because the roster is whatever we put in
 * this fleet, we have full programmatic control over which ships are in play —
 * that is the capability being de-risked.
 *
 * <p><b>Requirement 2 (own when the battle ends):</b> see {@link S0CompletionPlugin},
 * installed by {@link S0BattleCreationPlugin}.
 *
 * <p>Throwaway dev scaffolding; gated by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public final class S0BattleProbe {

    private static final Logger LOG = Global.getLogger(S0BattleProbe.class);

    /** How many of the player's combat-ready ships the probe fields. */
    public static final int PLAYER_SUBSET_SIZE = 2;

    /** Variant used if the player has no combat ships, so the probe still demos. */
    private static final String FALLBACK_PLAYER_VARIANT = "wolf_Standard";

    private static final String[] ENEMY_VARIANTS = {"lasher_Standard", "hound_Standard"};

    /**
     * Raised for the duration of a single {@link #launch()} so
     * {@link CombatHybridCampaignPlugin} wins the battle-creation pick for our
     * probe battle and only our probe battle. {@code volatile} for visibility;
     * the game loop is single-threaded so no further synchronization is needed.
     */
    private static volatile boolean armed = false;

    private S0BattleProbe() {}

    public static boolean isArmed() {
        return armed;
    }

    /**
     * Builds the rosters and hands them to {@code startBattle}. Must be called on
     * the campaign map (not from inside an open dialog/menu); the input listener
     * guarantees that. {@code startBattle} resolves the battle-creation plugin
     * synchronously within this call, so disarming in {@code finally} is safe and
     * also self-heals if {@code startBattle} declines to launch.
     */
    public static void launch() {
        if (Global.getSector() == null) return;

        CampaignFleetAPI player = buildPlayerSubset();
        CampaignFleetAPI enemy = buildEnemyFleet();

        if (player.getFleetData().getNumMembers() == 0) {
            LOG.warn("S0 probe: no player ships to field; aborting launch.");
            return;
        }

        BattleCreationContext ctx =
                new BattleCreationContext(player, FleetGoal.ATTACK, enemy, FleetGoal.ATTACK);
        ctx.setPlayerCommandPoints(5);
        ctx.aiRetreatAllowed = false;   // keep the enemy fighting so completion control is demonstrable
        ctx.objectivesAllowed = false;  // bare arena; S0BattleCreationPlugin skips objective gen anyway
        ctx.fightToTheLast = true;

        armed = true;
        try {
            LOG.info("S0 probe: launching vanilla combat — player subset="
                    + player.getFleetData().getNumMembers()
                    + " vs enemy=" + enemy.getFleetData().getNumMembers());
            Global.getSector().getCampaignUI().startBattle(ctx);
        } finally {
            armed = false;
        }
    }

    /** Synthetic player fleet from copies of the first N combat-ready real ships. */
    private static CampaignFleetAPI buildPlayerSubset() {
        FactoryAPI f = Global.getFactory();
        CampaignFleetAPI fleet = f.createEmptyFleet(Global.getSector().getPlayerFaction(), false);

        CampaignFleetAPI src = Global.getSector().getPlayerFleet();
        int added = 0;
        if (src != null) {
            for (FleetMemberAPI m : src.getFleetData().getCombatReadyMembersListCopy()) {
                if (added >= PLAYER_SUBSET_SIZE) break;
                if (m.isFighterWing()) continue;
                fleet.getFleetData().addFleetMember(
                        f.createFleetMember(FleetMemberType.SHIP, m.getVariant()));
                added++;
            }
        }
        if (added == 0) {
            LOG.info("S0 probe: player fleet had no combat ship; fielding fallback "
                    + FALLBACK_PLAYER_VARIANT + ".");
            fleet.getFleetData().addFleetMember(
                    f.createFleetMember(FleetMemberType.SHIP, FALLBACK_PLAYER_VARIANT));
        }
        fleet.setCommander(f.createPerson());
        return fleet;
    }

    /** Synthetic enemy fleet — a couple of cheap frigates to fight. */
    private static CampaignFleetAPI buildEnemyFleet() {
        FactoryAPI f = Global.getFactory();
        CampaignFleetAPI fleet = f.createEmptyFleet(Factions.HEGEMONY, "Test Opposition", true);
        for (String variantId : ENEMY_VARIANTS) {
            fleet.getFleetData().addFleetMember(
                    f.createFleetMember(FleetMemberType.SHIP, variantId));
        }
        fleet.setCommander(f.createPerson());
        return fleet;
    }
}
