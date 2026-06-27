package com.dillon.starsectormarines.combathybrid.probe;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.air.AirProvider;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.combathybrid.bridge.GroundBattleConfig;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.combathybrid.bridge.GroundSceneBackdrop;
import com.dillon.starsectormarines.combathybrid.bridge.SimProxyMirror;
import com.dillon.starsectormarines.combathybrid.host.CombatBridgeSession;
import com.dillon.starsectormarines.combathybrid.host.S0CompletionPlugin;
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

/**
 * Minimal, campaign-location-independent battle definition for the combat-bridge probe.
 * Selected by {@link CombatHybridCampaignPlugin} only for a tagged probe battle. Branches
 * on {@link S0BattleProbe#mode()}:
 *
 * <ul>
 *   <li><b>BASIC</b> — S0. Adds the context's fleet members as reserves
 *       ({@code addFleetMember}) so the player pilots/commands through the normal
 *       deploy flow, and installs {@link S0CompletionPlugin} for mod-owned end.</li>
 *   <li><b>SIM_COUPLED</b> — the durable bridge path. {@link #buildSimCoupledConfig} builds a live
 *       Conquest battle at LARGE into one {@link BattleSimulation} and packs it into a
 *       {@link GroundBattleConfig}; a {@link CombatBridgeSession} then owns the whole vanilla-side
 *       lifecycle (spectator canvas + completion, then the backdrop + proxy mirror over the sim).
 *       This plugin only builds the config, routes the two phases to the session, and spawns the
 *       scenario carriers — the production shape.</li>
 * </ul>
 */
@DebugOnly
public class S0BattleCreationPlugin implements BattleCreationPlugin {

    private static final Logger LOG = Global.getLogger(S0BattleCreationPlugin.class);

    /**
     * Bridge battlefield size, in cells — deliberately decoupled from the standalone {@link MapScale}
     * tiers (via the explicit-dimensions {@link BattleSetup#createConquestBuild} overload) so we can
     * push the ground scene bigger under the fleet without enlarging — and paying the world-sized
     * decal-FBO cost of — standalone HIGH-risk battles. 2× the LARGE tier (240×160) today; the bridge
     * renders no decals, so that FBO wall doesn't apply. The live ceilings are sim CPU (flat-A* path
     * length, an O(W×H) zone rebuild ~3–4 ms on each wall-break) + per-cell arrays (~8 MB). Dial
     * freely; if the generator or defender density misbehaves at this size, scale back. See the
     * large-map-scaling design doc for the tiled-decal-FBO + camera-residency plan that unblocks
     * going bigger (and bringing decals over).
     */
    private static final int BRIDGE_GRID_W = 480;
    private static final int BRIDGE_GRID_H = 320;
    /** Risk tier for the coupled battle — drives defender roster density, not map size (which is the
     *  explicit {@link #BRIDGE_GRID_W}×{@link #BRIDGE_GRID_H} above). */
    private static final RiskLevel SIM_RISK = RiskLevel.HIGH;

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
        float halfH = BRIDGE_GRID_H * S0BattleProbe.WORLD_UNITS_PER_CELL * 0.5f;
        spawnRow(engine, FleetSide.PLAYER, PROXY_CARRIER_SHIPS, -halfH * 0.6f, 90f, 900f);
        session.enterEngine(engine);
    }

    /**
     * Build the {@link GroundBattleConfig} for the bridge — a <b>live Conquest battle</b> under the
     * fleet, not a static map. We call {@link BattleSetup#createConquestBuild} (the same factory the
     * standalone screen and the campaign mission flow use), so defenders, manned guardposts, marines
     * arriving via internal shuttles, objectives, and the reinforcement layer all run as a real
     * battle that the backdrop renders below the ships.
     *
     * <p><b>Air stays {@link AirProvider#INTERNAL}</b> (the default): the sim owns its own shuttles
     * and flyby. The vanilla carriers' air-to-ground is <em>additive</em> pressure on a
     * self-contained battle — unifying air ownership (EXTERNAL + the external-landing handoff) is
     * S3d's job. The targetable tier is the defense-post structures (turrets + drone hubs) that
     * {@link SimProxyMirror} mirrors as proxies; defender/marine infantry are never directly proxied
     * (architecture Decision 2 — infantry take area damage, not lock-on).
     *
     * <p>One {@link BattleSimulation}, built here in the definition phase (called once) and handed
     * to the {@link CombatBridgeSession} via the config — never rebuilt by the per-frame plugins'
     * repeated {@code init}.
     */
    private GroundBattleConfig buildSimCoupledConfig() {
        int gridW = BRIDGE_GRID_W, gridH = BRIDGE_GRID_H;

        BattleSetup.MapBuild build = BattleSetup.createConquestBuild(
                SIM_MAP_SEED, simManifest(), false, SIM_RISK, TargetProfile.NEUTRAL, gridW, gridH);
        BattleSimulation sim = build.sim();
        List<Entity> targetable = build.structures();
        LOG.info("S3: live Conquest battle [" + gridW + "x" + gridH + "] — "
                + targetable.size() + " targetable structures, INTERNAL air.");

        return new GroundBattleConfig(
                sim, gridW, gridH, S0BattleProbe.WORLD_UNITS_PER_CELL,
                GroundBattleConfig.DEFAULT_SCENE_LAYERS, targetable, PROXY_VARIANT,
                GroundBattleConfig.DEFAULT_DAMAGE_SCALE);
    }

    /** A small fixed marine drop manifest for the probe battle — four single-cycle Aeroshuttles. */
    private static List<ShuttleAssignment> simManifest() {
        return List.of(
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));
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
