package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aircraft on the ground at its own field is on the map, so it is drawn.
 *
 * <p>The whole of what a runway buys over a vertical lift is a minute of
 * ground movement in the open. That is only worth anything if somebody can see
 * it happening — an aircraft that vanishes from the taxiway and reappears in
 * the air has exactly the cost profile of the vertical lift the strip was
 * built to replace.
 */
class AircraftOnTheGroundIsDrawnTest {

    private static final int W = 40;
    private static final int H = 30;

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

    private static int hullDraws(BattleSimulation sim) {
        BattleCamera camera = new BattleCamera(W, H);
        camera.setViewport(0f, 0f, 800f, 600f, 20f);
        RenderContext ctx = new RenderContext(sim, camera, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
        DrawList out = new DrawList();
        new ShuttleRenderSystem(spritesWithHull()).collect(ctx, out);
        int hulls = 0;
        for (int i = 0; i < out.count(RenderLayer.SHUTTLES); i++) {
            DrawCommand command = out.buffer(RenderLayer.SHUTTLES)[i];
            if (command.kind() == DrawCommand.Kind.SPRITE) hulls++;
        }
        return hulls;
    }

    /**
     * Every phase between leaving a berth and being finished with one puts the
     * aircraft on the map.
     *
     * <p>Each one asked separately rather than as a set, because the failure
     * this is for is one phase being left out of a hand-written list — which is
     * exactly what it was.
     */
    @Test
    void anAircraftIsDrawnForEveryPhaseItSpendsOnTheGround() {
        ShuttleState[] onTheGround = {
                ShuttleState.LOADING, ShuttleState.TAXI_OUT, ShuttleState.HOLDING_SHORT,
                ShuttleState.TAKEOFF_ROLL, ShuttleState.LANDING_ROLL, ShuttleState.TAXI_IN };
        for (ShuttleState phase : onTheGround) {
            BattleSimulation sim = openSim();
            long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                    20f, 20f, 2f, 2f, 2f, 2f, 0f);
            ShuttleMission mission = sim.world().mission(craft);
            mission.state = phase;
            sim.world().kinematics(craft).teleport(20f, 15f, 0f);

            assertEquals(1, hullDraws(sim), "nothing drawn while " + phase);
        }
    }

    /** A craft that has not launched yet, or is done, is not on the map. */
    @Test
    void anAircraftOffTheMapIsNotDrawn() {
        for (ShuttleState phase : new ShuttleState[]{ ShuttleState.PENDING, ShuttleState.GONE }) {
            BattleSimulation sim = openSim();
            long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                    20f, 20f, 2f, 2f, 2f, 2f, 5f);
            sim.world().mission(craft).state = phase;

            assertEquals(0, hullDraws(sim), "drawn while " + phase);
        }
    }

    /** And the flying phases keep drawing, which is what already worked. */
    @Test
    void anAircraftInTheAirIsStillDrawn() {
        BattleSimulation sim = openSim();
        long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                20f, 20f, 2f, 2f, 2f, 2f, 0f);
        sim.world().mission(craft).state = ShuttleState.INCOMING;
        assertTrue(hullDraws(sim) > 0, "a flying aircraft stopped being drawn");
    }
}
