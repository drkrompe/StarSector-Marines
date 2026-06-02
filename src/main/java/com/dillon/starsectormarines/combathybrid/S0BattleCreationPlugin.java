package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.fs.starfarer.api.campaign.BattleCreationPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Minimal, campaign-location-independent battle definition for the S0 probes.
 * Selected by {@link CombatHybridCampaignPlugin} only while {@link S0BattleProbe}
 * has armed it. Branches on {@link S0BattleProbe#mode()}:
 *
 * <ul>
 *   <li><b>BASIC</b> — S0. Adds the context's fleet members as reserves
 *       ({@code addFleetMember}) so the player pilots/commands through the normal
 *       deploy flow, and installs {@link S0CompletionPlugin} for mod-owned end.</li>
 *   <li><b>SPECTATOR_CANVAS</b> — S0b. Fields both sides as AI ({@code useDefaultAI})
 *       with zero command points, spawns ships <em>directly</em> in
 *       {@link #afterDefinitionLoad} via the fleet manager (so the deploy dialog
 *       never appears — verified fact 11), sizes the vanilla map to the sim grid at
 *       {@link S0BattleProbe#WORLD_UNITS_PER_CELL}, and installs the
 *       {@link SpectatorCanvasPlugin} + below-ships {@link CanvasBackdropRenderer}.</li>
 * </ul>
 */
@DebugOnly
public class S0BattleCreationPlugin implements BattleCreationPlugin {

    /** Canvas demo grid (the MEDIUM mission tier). */
    private static final MapScale CANVAS_GRID = MapScale.MEDIUM;

    /** Stock variants spawned for the spectator canvas (throwaway; roster isn't the point of S0b). */
    private static final String[] CANVAS_PLAYER_SHIPS = {"wolf_Standard", "lasher_Standard"};
    private static final String[] CANVAS_ENEMY_SHIPS = {"hound_Standard", "vigilance_Standard"};

    private boolean spectator;

    @Override
    public void initBattle(BattleCreationContext context, MissionDefinitionAPI loader) {
        spectator = S0BattleProbe.mode() == S0BattleProbe.Mode.SPECTATOR_CANVAS;

        if (spectator) {
            initSpectatorCanvas(context, loader);
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

    private void initSpectatorCanvas(BattleCreationContext context, MissionDefinitionAPI loader) {
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
        if (!spectator) {
            return; // BASIC: S0CompletionPlugin handles everything.
        }

        float halfH = CANVAS_GRID.height * S0BattleProbe.WORLD_UNITS_PER_CELL * 0.5f;
        float spawnY = halfH * 0.6f;
        float spacing = 600f;

        spawnRow(engine, FleetSide.PLAYER, CANVAS_PLAYER_SHIPS, -spawnY, 90f, spacing);
        spawnRow(engine, FleetSide.ENEMY, CANVAS_ENEMY_SHIPS, spawnY, 270f, spacing);

        // Spectator: detach the camera's owner so no ship is player-piloted.
        engine.setPlayerShipExternal(null);

        // Backdrop plate under the ships (verified fact 10).
        engine.addLayeredRenderingPlugin(new CanvasBackdropRenderer(
                CANVAS_GRID.width, CANVAS_GRID.height, S0BattleProbe.WORLD_UNITS_PER_CELL));
    }

    private static void spawnRow(CombatEngineAPI engine, FleetSide side, String[] variantIds,
                                 float y, float facing, float spacing) {
        float startX = -((variantIds.length - 1) * spacing) * 0.5f;
        for (int i = 0; i < variantIds.length; i++) {
            float x = startX + i * spacing;
            engine.getFleetManager(side).spawnShipOrWing(variantIds[i], new Vector2f(x, y), facing);
        }
    }
}
