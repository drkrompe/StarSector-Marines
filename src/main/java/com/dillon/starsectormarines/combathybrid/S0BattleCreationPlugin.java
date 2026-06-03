package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.air.AirProvider;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleCreationPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.Random;

/**
 * Minimal, campaign-location-independent battle definition for the combat-bridge probe.
 * Selected by {@link CombatHybridCampaignPlugin} only for a tagged probe battle. Branches
 * on {@link S0BattleProbe#mode()}:
 *
 * <ul>
 *   <li><b>BASIC</b> — S0. Adds the context's fleet members as reserves
 *       ({@code addFleetMember}) so the player pilots/commands through the normal
 *       deploy flow, and installs {@link S0CompletionPlugin} for mod-owned end.</li>
 *   <li><b>SIM_COUPLED</b> — the durable bridge path. {@link #buildSimCoupledConfig} loads the
 *       real Conquest map at LARGE into one {@link BattleSimulation} and packs it into a
 *       {@link GroundBattleConfig}; a {@link CombatBridgeSession} then owns the whole vanilla-side
 *       lifecycle (spectator canvas + completion, then the backdrop + proxy mirror over the sim).
 *       This plugin only builds the config, routes the two phases to the session, and spawns the
 *       scenario carriers — the production shape.</li>
 * </ul>
 */
@DebugOnly
public class S0BattleCreationPlugin implements BattleCreationPlugin {

    private static final Logger LOG = Global.getLogger(S0BattleCreationPlugin.class);

    /** The bridge loads the real Conquest map at the LARGE (HIGH-risk siege) tier we actually play. */
    private static final MapScale SIM_GRID = MapScale.LARGE;

    /** The vanilla carriers "above" whose fighters strafe the planet's defenses. */
    private static final String[] PROXY_CARRIER_SHIPS = {"heron_Strike", "drover_Strike"};
    /** The hull behind each invisible proxy (sprite hidden; any small hull works). */
    private static final String PROXY_VARIANT = "vigilance_Standard";
    /** Fixed seed for the probe Conquest map so the backdrop is reproducible across launches. */
    private static final long SIM_MAP_SEED = 42L;

    /** Built for SIM_COUPLED: the bridge host orchestrator that owns that mode's vanilla-side lifecycle. */
    private CombatBridgeSession session;

    @Override
    public void initBattle(BattleCreationContext context, MissionDefinitionAPI loader) {
        LOG.info("S0BattleCreationPlugin SELECTED — building probe battle [mode="
                + S0BattleProbe.mode() + "]");

        if (S0BattleProbe.mode() == S0BattleProbe.Mode.SIM_COUPLED) {
            // The durable bridge path: build the sim-coupled config, then let the session
            // own both combat-side phases (definition here, engine wiring in afterDefinitionLoad).
            session = new CombatBridgeSession(buildSimCoupledConfig());
            session.defineBattle(context, loader);
        } else {
            initBasic(context, loader);
        }
    }

    private void initBasic(BattleCreationContext context, MissionDefinitionAPI loader) {
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

        loader.initMap(-9000f, 9000f, -9000f, 9000f);
        loader.setHyperspaceMode(false);

        // Requirement 2: the mod owns when this battle is considered complete.
        loader.addPlugin(new S0CompletionPlugin());
    }

    @Override
    public void afterDefinitionLoad(CombatEngineAPI engine) {
        // BASIC pilots through the normal deploy flow; S0CompletionPlugin handles everything.
        if (S0BattleProbe.mode() != S0BattleProbe.Mode.SIM_COUPLED) {
            return;
        }
        // Scenario content: the vanilla carriers "above" whose fighters strafe the planet's
        // defenses. The session owns the durable engine-side wiring (detach, backdrop, proxy
        // mirror, never-end objective).
        float halfH = SIM_GRID.height * S0BattleProbe.WORLD_UNITS_PER_CELL * 0.5f;
        spawnRow(engine, FleetSide.PLAYER, PROXY_CARRIER_SHIPS, -halfH * 0.6f, 90f, 900f);
        session.enterEngine(engine);
    }

    /**
     * S3a (fan-out) + S3b (backdrop): build the {@link GroundBattleConfig} for the bridge — the real
     * <b>Conquest map at the LARGE tier</b> under the fleet. We generate the map exactly as
     * {@link com.dillon.starsectormarines.battle.setup.BattleSetup#createConquest} does —
     * biome-banded along a {@link TraversalAxis}, with buildings, doodads, roads, and the
     * pre-stamped defense-post layout — but stop at the <em>map</em>: no marines, defenders,
     * shuttles, or reinforcement (that's the battle, not the map). The defense-post turrets become
     * real targetable structures the {@link SimProxyMirror} mirrors as proxies, so the carriers'
     * fighters strafe the planet's actual defenses.
     *
     * <p>One {@link BattleSimulation}, built here in the definition phase (called once) and handed
     * to the {@link CombatBridgeSession} via the config — never rebuilt by the per-frame plugins'
     * repeated {@code init}.
     */
    private GroundBattleConfig buildSimCoupledConfig() {
        MapScale scale = SIM_GRID;
        int gridW = scale.width, gridH = scale.height;
        Random rng = new Random(SIM_MAP_SEED);
        TraversalAxis axis = rng.nextBoolean() ? TraversalAxis.SOUTH_TO_NORTH : TraversalAxis.WEST_TO_EAST;
        MapResult map = new BspCityGenerator().generate(gridW, gridH, SIM_MAP_SEED, axis, TargetProfile.NEUTRAL);

        // Build the host-agnostic map layer through the SAME path the standalone battle uses
        // (BattleSetup.buildMap) — terrain, structures, and the defense-post turrets, no
        // reimplementation to drift. No parked vehicles: this is a map-only probe and they'd be
        // invisible obstacles in the backdrop's GROUND/DOODADS/ROOFS subset.
        BattleSetup.MapBuild build = BattleSetup.buildMap(map, List.of(), map.defensePosts);
        BattleSimulation sim = build.sim();
        // The real vanilla ships above own the air — the sim runs no internal shuttle/flyby.
        sim.setAirProvider(AirProvider.EXTERNAL);
        // The defense-post structures, mirrored as proxies so the fleet strafes real defenses.
        List<Entity> targetable = build.structures();
        LOG.info("S3: loaded Conquest map [" + scale + " " + gridW + "x" + gridH + ", axis=" + axis
                + "] — " + map.defensePosts.size() + " defense posts, " + targetable.size() + " targetable structures.");

        return new GroundBattleConfig(
                sim, gridW, gridH, S0BattleProbe.WORLD_UNITS_PER_CELL,
                GroundBattleConfig.DEFAULT_SCENE_LAYERS, targetable, PROXY_VARIANT,
                GroundBattleConfig.DEFAULT_DAMAGE_SCALE);
    }

    private static void spawnRow(CombatEngineAPI engine, FleetSide side, String[] variantIds,
                                 float y, float facing, float spacing) {
        float startX = -((variantIds.length - 1) * spacing) * 0.5f;
        for (int i = 0; i < variantIds.length; i++) {
            spawnValidated(engine, side, variantIds[i], new Vector2f(startX + i * spacing, y), facing);
        }
    }

    /**
     * Spawn one ship, validating the variant id first. {@code spawnShipOrWing} resolves
     * ids eagerly and throws on a bad one — which would abort the whole launch (it runs
     * inside startBattle) — so a miss logs + skips instead.
     */
    private static void spawnValidated(CombatEngineAPI engine, FleetSide side, String id,
                                       Vector2f loc, float facing) {
        if (Global.getSettings().getVariant(id) == null) {
            LOG.warn("combat-bridge: skipping unknown variant id [" + id + "] for side " + side);
            return;
        }
        engine.getFleetManager(side).spawnShipOrWing(id, loc, facing);
    }
}
