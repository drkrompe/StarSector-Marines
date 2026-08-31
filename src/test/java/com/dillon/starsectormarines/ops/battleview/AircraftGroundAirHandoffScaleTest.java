package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirfieldSystem;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A parked airframe is drawn by two different collectors depending on which
 * side of a launch it is on: {@code UnitRenderService} while it stands on its
 * berth, {@code ShuttleRenderSystem} once it is an air entity. They are the
 * same object at the seam between them — a launch reads the berth's hull one
 * frame and the air entity's the next — so the drawn size must agree exactly
 * at {@code altitudeT == 0}, or every launch and recovery pops.
 *
 * <p>Both paths derive their scale from {@code AirAppearance.GROUND_SCALE}
 * now; this pins that the two collectors still land on the same number,
 * rather than re-pinning the constant itself, which belongs to
 * {@code AirAppearanceScaleTest}.
 */
class AircraftGroundAirHandoffScaleTest {

    private static final int W = 30;
    private static final int H = 30;
    private static final float CELL_PX = 20f;
    private static final float EPS = 1e-3f;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /** A sprite token that draws, without a game to load one from. */
    private static BattleSprites spritesWithHull() {
        SpriteAPI token = (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "hull";
                    default -> null;
                });
        Map<Airframe, ShuttleSpriteCache> loaded = new LinkedHashMap<>();
        loaded.put(ShuttleType.AEROSHUTTLE, new ShuttleSpriteCache(token, 1f, 64, 64));
        return new BattleSprites() {
            @Override
            public Map<Airframe, ShuttleSpriteCache> airframeSprites() {
                return loaded;
            }
        };
    }

    private static BattleCamera camera() {
        BattleCamera cam = new BattleCamera(W, H);
        cam.setViewport(0f, 0f, 800f, 600f, CELL_PX);
        return cam;
    }

    private static DrawCommand onlyHullSprite(DrawList out, RenderLayer layer) {
        DrawCommand found = null;
        for (int i = 0; i < out.count(layer); i++) {
            DrawCommand command = out.buffer(layer)[i];
            if (command.kind() != DrawCommand.Kind.SPRITE) continue;
            if (found != null) throw new AssertionError("more than one hull sprite on " + layer);
            found = command;
        }
        if (found == null) throw new AssertionError("no hull sprite drawn on " + layer);
        return found;
    }

    @Test
    void aBerthedHullAndAGroundedAirEntityDrawTheSameSize() {
        BattleSprites sprites = spritesWithHull();

        // The berth: a based aircraft standing on its pad, drawn by
        // UnitRenderService.sweepBasedAircraft's emitHull.
        BattleSimulation berthedSim = openSim();
        berthedSim.getAirfieldService().addBerth(
                LandingPad.garrison(12, 14, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 90f);
        new AirfieldSystem(Faction.DEFENDER).tick(1f / 30f, berthedSim, berthedSim.getAirfieldService());
        RenderContext berthCtx = new RenderContext(berthedSim, camera(), null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
        DrawList berthOut = new DrawList();
        new UnitRenderService(sprites).collect(berthCtx, berthOut);
        DrawCommand berthedHull = onlyHullSprite(berthOut, RenderLayer.UNITS);

        // The air entity: the same hull, on the ground at altitudeT == 0,
        // drawn by ShuttleRenderSystem. Spawn seeds altitudeT at 1 (cruise) —
        // it only comes down through AirSystem's own tick — so this sets it
        // directly rather than trying to walk the state machine down to 0.
        BattleSimulation airSim = openSim();
        long craft = airSim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                20f, 20f, 2f, 2f, 2f, 2f, 0f);
        ShuttleMission mission = airSim.world().mission(craft);
        mission.state = ShuttleState.LOADING;
        airSim.world().kinematics(craft).teleport(20f, 20f, 0f);
        airSim.world().setAltitudeT(craft, 0f);
        RenderContext airCtx = new RenderContext(airSim, camera(), null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
        DrawList airOut = new DrawList();
        new ShuttleRenderSystem(sprites).collect(airCtx, airOut);
        DrawCommand grounded = onlyHullSprite(airOut, RenderLayer.SHUTTLES);

        assertEquals(grounded.height(), berthedHull.height(), EPS,
                "a launch or a recovery pops the drawn hull size");
    }
}
