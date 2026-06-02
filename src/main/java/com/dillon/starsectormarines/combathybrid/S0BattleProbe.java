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

    /** Which probe battle to launch. */
    public enum Mode {
        /** S0: player-piloted battle from a chosen fleet subset (requirements 1 + 2). */
        BASIC,
        /** S0b: spectator canvas — no player ship, free cam, below-ships backdrop, no deploy dialog. */
        SPECTATOR_CANVAS,
        /** S2: spectator canvas + an AI carrier vs an invisible slaved proxy (proxy/avatar pattern). */
        PROXY_TARGET,
        /** S3a: like PROXY_TARGET, but the proxy is backed by a live sim turret — the first real coupling. */
        SIM_COUPLED
    }

    /** Modes that use the spectator-canvas host (empty player fleet, free cam, starved HUD). */
    public static boolean isCanvasMode(Mode m) {
        return m == Mode.SPECTATOR_CANVAS || m == Mode.PROXY_TARGET || m == Mode.SIM_COUPLED;
    }

    /** How many of the player's combat-ready ships the probe fields. */
    public static final int PLAYER_SUBSET_SIZE = 2;

    /**
     * World units per sim cell when projecting the grid into vanilla combat. Lowered
     * from the original 50 after the S3b playtest: at 50 the ground cells read too large
     * relative to the spacecraft. At 20 the LARGE 240×160 Conquest map is 4800×3200 world
     * units and ships tower over individual tiles, which reads right. Both the backdrop
     * and the proxies derive from this constant, so they stay locked at any value. The
     * right number is ultimately per-use (overhead-air vs on-the-ground) — this is the
     * working default the canvas is built around.
     */
    public static final float WORLD_UNITS_PER_CELL = 20f;

    /** Variant used if the player has no combat ships, so the probe still demos.
     *  Must be a real variant id from data/variants. */
    private static final String FALLBACK_PLAYER_VARIANT = "vigilance_Standard";

    private static final String[] ENEMY_VARIANTS = {"vigilance_Standard", "vigilance_Strike"};

    /**
     * Raised for the duration of a single {@link #launch()} so
     * {@link CombatHybridCampaignPlugin} wins the battle-creation pick for our
     * probe battle and only our probe battle. {@code volatile} for visibility;
     * the game loop is single-threaded so no further synchronization is needed.
     */
    /**
     * Memory flag set on the synthetic enemy fleet. {@link CombatHybridCampaignPlugin}
     * matches on it in {@code pickBattleCreationPlugin} to recognize a probe battle.
     *
     * <p>Replaces an earlier "armed for the duration of the {@code startBattle} call"
     * boolean — that never matched, because {@code startBattle} resolves the
     * battle-creation plugin on a <em>later</em> frame (after {@code launch()} has
     * returned and reset the flag), so the core plugin always won and our spectator
     * path never ran. Tagging the fleet has no timing window: the opponent fleet
     * carries the marker until the battle is built.
     */
    public static final String PROBE_FLAG = "$marines_combathybrid_probe";

    private static volatile Mode mode = Mode.BASIC;

    private S0BattleProbe() {}

    /** Which battle the armed {@link S0BattleCreationPlugin} should build. */
    public static Mode mode() {
        return mode;
    }

    /** S0b entry point — launch the spectator canvas instead of the basic battle. */
    public static void launchSpectatorCanvas() {
        launch(Mode.SPECTATOR_CANVAS);
    }

    /** S2 entry point — spectator canvas with an AI carrier vs an invisible slaved proxy. */
    public static void launchProxyTarget() {
        launch(Mode.PROXY_TARGET);
    }

    /** S3a entry point — proxy target backed by a live sim turret (the first real coupling). */
    public static void launchSimCoupled() {
        launch(Mode.SIM_COUPLED);
    }

    /**
     * Builds the rosters and hands them to {@code startBattle}. Must be called on
     * the campaign map (not from inside an open dialog/menu); the input listener
     * guarantees that. {@code startBattle} resolves the battle-creation plugin
     * synchronously within this call, so disarming in {@code finally} is safe and
     * also self-heals if {@code startBattle} declines to launch.
     */
    public static void launch() {
        launch(Mode.BASIC);
    }

    private static void launch(Mode requested) {
        if (Global.getSector() == null) return;
        mode = requested;
        boolean spectator = isCanvasMode(requested);

        // startBattle sources the player's deployable ships from the REAL player fleet,
        // ignoring the context's player fleet (an empty context fleet still showed the
        // deploy picker). So for a true spectator we detach the real fleet's members for
        // the duration of the battle (restored on combat end by PlayerFleetStash), and
        // pass the now-empty real fleet as the context fleet. The owner-0 combatants the
        // player watches are spawned directly by S0BattleCreationPlugin.
        CampaignFleetAPI player;
        if (spectator) {
            PlayerFleetStash.stashAndScheduleRestore();
            player = Global.getSector().getPlayerFleet();
        } else {
            player = buildPlayerSubset();
        }
        CampaignFleetAPI enemy = buildEnemyFleet();

        if (!spectator && player.getFleetData().getNumMembers() == 0) {
            LOG.warn("S0 probe: no player ships to field; aborting launch.");
            return;
        }

        BattleCreationContext ctx =
                new BattleCreationContext(player, FleetGoal.ATTACK, enemy, FleetGoal.ATTACK);
        ctx.setPlayerCommandPoints(spectator ? 0 : 5);
        ctx.aiRetreatAllowed = false;   // keep the enemy fighting so completion control is demonstrable
        ctx.objectivesAllowed = false;  // bare arena; S0BattleCreationPlugin skips objective gen anyway
        ctx.fightToTheLast = true;

        LOG.info("S0 probe: launching vanilla combat [" + mode + "] — player="
                + player.getFleetData().getNumMembers()
                + " vs enemy=" + enemy.getFleetData().getNumMembers());
        Global.getSector().getCampaignUI().startBattle(ctx);
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
        fleet.getMemoryWithoutUpdate().set(PROBE_FLAG, true);
        for (String variantId : ENEMY_VARIANTS) {
            // createFleetMember resolves the variant lazily, so a bad id would crash
            // later during battle build with an opaque trace — validate up front.
            if (Global.getSettings().getVariant(variantId) == null) {
                LOG.warn("S0 probe: skipping unknown enemy variant id [" + variantId + "]");
                continue;
            }
            fleet.getFleetData().addFleetMember(
                    f.createFleetMember(FleetMemberType.SHIP, variantId));
        }
        fleet.setCommander(f.createPerson());
        return fleet;
    }
}
