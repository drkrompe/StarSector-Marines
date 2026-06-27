package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.combathybrid.bridge.GroundBattleConfig;
import com.dillon.starsectormarines.combathybrid.bridge.GroundSceneBackdrop;
import com.dillon.starsectormarines.combathybrid.bridge.SimProxyMirror;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;

/**
 * Orchestrates the vanilla-combat-side lifecycle of one sim-coupled ground battle. Given a
 * {@link GroundBattleConfig} (whose sim is already built via {@code BattleSetup.buildMap}), it
 * composes the durable bridge adapters and the session-policy plugins across the two combat-side
 * phases of a {@code BattleCreationPlugin}:
 *
 * <ul>
 *   <li><b>{@link #defineBattle}</b> — battle-definition phase ({@code MissionDefinitionAPI}):
 *       spectator-canvas fleet/map setup (both sides AI, zero command points → no flagship, empty
 *       HUD; map sized to the sim grid) plus the completion ({@link S0CompletionPlugin}) and
 *       free-camera ({@link SpectatorCanvasPlugin}) policy plugins.</li>
 *   <li><b>{@link #enterEngine}</b> — engine-ready phase ({@code afterDefinitionLoad}): detach the
 *       player ship and install the ground-scene backdrop ({@link GroundSceneBackdrop}) + the proxy
 *       mirror ({@link SimProxyMirror}) over the live sim.</li>
 * </ul>
 *
 * <p><b>Thin orchestrator, not a god class.</b> Every behavior lives in a delegate — this session
 * holds no lifecycle logic of its own, it only wires the delegates to one config so the creation
 * plugin's mode branch collapses to {@code session.defineBattle(...)} / {@code session.enterEngine(
 * ...)}. The player-fleet stash/restore handshake stays campaign-side ({@link PlayerFleetStash},
 * invoked by the launcher and {@link SpectatorCanvasPlugin}); the spawn of the vanilla ships
 * "above" is scenario content the caller owns, not session lifecycle.
 *
 * <p>Durable host core (see {@code roadmap/vanilla-combat-bridge/production-architecture.md}).
 * Reachable only via the dev probe today; production triggers it through the mission flow once that
 * seam lands.
 */
@DebugOnly
public final class CombatBridgeSession {

    /** Map padding past the grid plate so the free camera can pan beyond the edges. */
    private static final float MAP_PAD = 1.5f;

    private final GroundBattleConfig config;

    public CombatBridgeSession(GroundBattleConfig config) {
        this.config = config;
    }

    /** Battle-definition phase: spectator-canvas fleets + map + completion/camera policy plugins. */
    public void defineBattle(BattleCreationContext context, MissionDefinitionAPI loader) {
        // Both sides AI, zero command points -> no player flagship, empty HUD.
        loader.initFleet(FleetSide.PLAYER, "ISS", context.getPlayerGoal(), true, 0);
        loader.initFleet(FleetSide.ENEMY, "", context.getOtherGoal(), true, 0);
        // No addFleetMember: combatants are spawned directly in afterDefinitionLoad, so there are
        // no reserves and the deploy dialog never appears.

        float halfW = config.gridW() * config.worldUnitsPerCell() * 0.5f;
        float halfH = config.gridH() * config.worldUnitsPerCell() * 0.5f;
        loader.initMap(-halfW * MAP_PAD, halfW * MAP_PAD, -halfH * MAP_PAD, halfH * MAP_PAD);
        loader.setHyperspaceMode(false);

        loader.addPlugin(new S0CompletionPlugin());
        // Frame the whole ground plate at start, a touch wider than the map, regardless of
        // cell density — the initial view derives from the map's world size, not a constant.
        loader.addPlugin(new SpectatorCanvasPlugin(
                config.gridW() * config.worldUnitsPerCell() * 1.3f));
    }

    /** Engine-ready phase: detach the player ship, install the backdrop + proxy mirror over the sim. */
    public void enterEngine(CombatEngineAPI engine) {
        engine.setPlayerShipExternal(null);   // spectator: no ship is player-piloted
        // The sim is a live Conquest battle with its own win conditions — it governs its own
        // completion exactly as the standalone flow does; no never-end pin needed.
        // The backdrop owns the ground renderer + its FX/sound presentation; the proxy mirror
        // ticks the sim each frame, then drives the backdrop's presentation off that fresh tick
        // (so shot/impact/death events are read in the same frame they're produced).
        GroundSceneBackdrop backdrop = new GroundSceneBackdrop(config);
        engine.addLayeredRenderingPlugin(backdrop);
        engine.addPlugin(new SimProxyMirror(config, backdrop));
        // S3c (parked): a harmless one-shot ASSAULT order nudges the carriers onto the band.
        engine.addPlugin(new CarrierEngagementPlugin(config, FleetSide.PLAYER));
        // S3d foundation: press L in-combat to take over one carrier and fly it down to the band.
        engine.addPlugin(new CarrierDescentPlugin(config, FleetSide.PLAYER));
        // Ground-control-mode probe: press X to toggle the cursor reveal disk (fade player ships
        // near the cursor so the ground scene shows through). See stories/ground-control-mode.md.
        engine.addPlugin(new SeeThroughPlugin());
    }
}
