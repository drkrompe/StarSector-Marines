package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleCreationPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, campaign-location-independent battle definition for the combat-bridge probes.
 * Selected by {@link CombatHybridCampaignPlugin} only for a tagged probe battle. Branches
 * on {@link S0BattleProbe#mode()}:
 *
 * <ul>
 *   <li><b>BASIC</b> — S0. Adds the context's fleet members as reserves
 *       ({@code addFleetMember}) so the player pilots/commands through the normal
 *       deploy flow, and installs {@link S0CompletionPlugin} for mod-owned end.</li>
 *   <li><b>SPECTATOR_CANVAS</b> — S0b. Empty player fleet + zero CP (no deploy dialog,
 *       starved HUD), map sized to the sim grid, ships spawned directly in
 *       {@link #afterDefinitionLoad}, free camera + below-ships backdrop.</li>
 *   <li><b>PROXY_TARGET</b> — S2. Same spectator host, but spawns an AI carrier on the
 *       player side and a single invisible owner-1 proxy ({@link ProxyTargetPlugin}) to
 *       test whether native carrier/fighter AI engages a slaved sim avatar.</li>
 *   <li><b>SIM_COUPLED</b> — S3a. Like PROXY_TARGET, but builds one
 *       {@link BattleSimulation} of mixed turrets here and mirrors each as a proxy via
 *       {@link GroundSimBridge} — vanilla damage drains the real sim units, sim deaths
 *       despawn the proxies. The sim is built outside the combat plugin on purpose.</li>
 * </ul>
 */
@DebugOnly
public class S0BattleCreationPlugin implements BattleCreationPlugin {

    private static final Logger LOG = Global.getLogger(S0BattleCreationPlugin.class);

    /** Canvas demo grid (the MEDIUM mission tier). */
    private static final MapScale CANVAS_GRID = MapScale.MEDIUM;

    /** Throwaway stock variants — must be real ids from data/variants (validated before spawn). */
    private static final String[] CANVAS_PLAYER_SHIPS = {"vigilance_Standard", "brawler_Assault"};
    private static final String[] CANVAS_ENEMY_SHIPS = {"tempest_Attack", "shrike_Attack"};
    /** S2: carriers on the player side whose fighters should engage the proxy. */
    private static final String[] PROXY_CARRIER_SHIPS = {"heron_Strike", "drover_Strike"};
    /** S2/S3a: the hull behind each invisible proxy (sprite hidden; any small hull works). */
    private static final String PROXY_VARIANT = "vigilance_Standard";
    /** S3a: cell gap between adjacent turrets in the demo row (≥ marker width at scale 50). */
    private static final int SIM_TURRET_CELL_SPACING = 6;
    /** S3b: fixed seed for the probe cityscape so the backdrop is reproducible across launches. */
    private static final long SIM_MAP_SEED = 42L;

    private boolean canvas;

    @Override
    public void initBattle(BattleCreationContext context, MissionDefinitionAPI loader) {
        canvas = S0BattleProbe.isCanvasMode(S0BattleProbe.mode());
        LOG.info("S0BattleCreationPlugin SELECTED — building probe battle [mode="
                + S0BattleProbe.mode() + "]");

        if (canvas) {
            initCanvas(context, loader);
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

    private void initCanvas(BattleCreationContext context, MissionDefinitionAPI loader) {
        // Both sides AI, zero command points -> no player flagship, empty HUD.
        loader.initFleet(FleetSide.PLAYER, "ISS", context.getPlayerGoal(), true, 0);
        loader.initFleet(FleetSide.ENEMY, "", context.getOtherGoal(), true, 0);

        // No addFleetMember: ships are spawned directly in afterDefinitionLoad, so
        // there are no reserves and the deploy dialog never appears.

        float halfW = CANVAS_GRID.width * S0BattleProbe.WORLD_UNITS_PER_CELL * 0.5f;
        float halfH = CANVAS_GRID.height * S0BattleProbe.WORLD_UNITS_PER_CELL * 0.5f;
        // Pad the map past the plate so the free camera can pan beyond the edges.
        loader.initMap(-halfW * 1.5f, halfW * 1.5f, -halfH * 1.5f, halfH * 1.5f);
        loader.setHyperspaceMode(false);

        loader.addPlugin(new S0CompletionPlugin());
        loader.addPlugin(new SpectatorCanvasPlugin());
    }

    @Override
    public void afterDefinitionLoad(CombatEngineAPI engine) {
        if (!canvas) {
            return; // BASIC: S0CompletionPlugin handles everything.
        }

        // Spectator: detach the camera's owner so no ship is player-piloted.
        engine.setPlayerShipExternal(null);
        // Placeholder grid plate under the ships (verified fact 10). SIM_COUPLED draws
        // the real ground scene instead (GroundSceneBackdrop), so skip the grid there.
        if (S0BattleProbe.mode() != S0BattleProbe.Mode.SIM_COUPLED) {
            engine.addLayeredRenderingPlugin(new CanvasBackdropRenderer(
                    CANVAS_GRID.width, CANVAS_GRID.height, S0BattleProbe.WORLD_UNITS_PER_CELL));
        }

        float halfH = CANVAS_GRID.height * S0BattleProbe.WORLD_UNITS_PER_CELL * 0.5f;
        if (S0BattleProbe.mode() == S0BattleProbe.Mode.SIM_COUPLED) {
            setupSimCoupled(engine, halfH);
        } else if (S0BattleProbe.mode() == S0BattleProbe.Mode.PROXY_TARGET) {
            setupProxyTarget(engine, halfH);
        } else {
            float spawnY = halfH * 0.6f;
            spawnRow(engine, FleetSide.PLAYER, CANVAS_PLAYER_SHIPS, -spawnY, 90f, 600f);
            spawnRow(engine, FleetSide.ENEMY, CANVAS_ENEMY_SHIPS, spawnY, 270f, 600f);
        }
    }

    /**
     * S3a (fan-out) + S3b (backdrop): AI carriers vs a row of invisible proxies, each
     * backed by a real turret in <b>one</b> {@link BattleSimulation} that also renders
     * its terrain + structures under the ships ({@link GroundSceneBackdrop}). The sim
     * is a real generated cityscape ({@link BspCityGenerator}), built here — outside
     * the combat plugins — so the engine's repeated {@code init} can't rebuild it, and
     * both the bridge and the backdrop reference the same instance. Turrets sit on a
     * short row through the grid center (which projects to world origin), so the
     * markers spread across the city for the fighters to chew through.
     */
    private void setupSimCoupled(CombatEngineAPI engine, float halfH) {
        spawnRow(engine, FleetSide.PLAYER, PROXY_CARRIER_SHIPS, -halfH * 0.6f, 90f, 900f);

        int gridW = CANVAS_GRID.width, gridH = CANVAS_GRID.height;
        MapResult map = new BspCityGenerator().generate(gridW, gridH, SIM_MAP_SEED);
        BattleSimulation sim = new BattleSimulation(map.grid, map.topology);
        sim.setBuildings(map.buildings);

        // A short row of mixed-kind turrets centered on the grid (cell cx → world 0).
        int cx = gridW / 2, cy = gridH / 2;
        TurretKind[] kinds = {TurretKind.VULCAN, TurretKind.ARBALEST, TurretKind.HEPHAESTUS,
                TurretKind.DUAL_FLAK, TurretKind.HEAVY_MG};
        List<Unit> turrets = new ArrayList<>();
        for (int i = 0; i < kinds.length; i++) {
            int dx = (i - kinds.length / 2) * SIM_TURRET_CELL_SPACING;
            MapTurret t = new MapTurret("bridge_turret_" + i, Faction.DEFENDER, kinds[i], cx + dx, cy);
            sim.addUnit(t);
            turrets.add(t);
        }
        // Keep the all-DEFENDER sim from auto-completing (a completed sim early-returns
        // from advance() and would strand the death events).
        sim.addObjective(new NeverEndObjective(Faction.DEFENDER));

        // The real ground scene under the ships, and the proxy/damage coupling — both
        // over the same sim. Backdrop is below-ships; the bridge is an every-frame plugin.
        engine.addLayeredRenderingPlugin(new GroundSceneBackdrop(
                sim, gridW, gridH, S0BattleProbe.WORLD_UNITS_PER_CELL));
        engine.addPlugin(new GroundSimBridge(
                sim, turrets, gridW, gridH, S0BattleProbe.WORLD_UNITS_PER_CELL, PROXY_VARIANT));
    }

    /** S2: AI carrier(s) on the player side + one invisible slaved proxy on the enemy side. */
    private void setupProxyTarget(CombatEngineAPI engine, float halfH) {
        spawnRow(engine, FleetSide.PLAYER, PROXY_CARRIER_SHIPS, -halfH * 0.6f, 90f, 900f);

        Vector2f anchor = new Vector2f(0f, halfH * 0.4f);
        ShipAPI proxy = spawnValidated(engine, FleetSide.ENEMY, PROXY_VARIANT, anchor, 270f);
        if (proxy != null) {
            engine.addPlugin(new ProxyTargetPlugin(proxy, anchor));
        } else {
            LOG.warn("S2: proxy variant [" + PROXY_VARIANT + "] missing; no proxy spawned.");
        }
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
     * inside startBattle) — so a miss logs + returns null instead.
     */
    private static ShipAPI spawnValidated(CombatEngineAPI engine, FleetSide side, String id,
                                          Vector2f loc, float facing) {
        if (Global.getSettings().getVariant(id) == null) {
            LOG.warn("combat-bridge: skipping unknown variant id [" + id + "] for side " + side);
            return null;
        }
        return engine.getFleetManager(side).spawnShipOrWing(id, loc, facing);
    }
}
