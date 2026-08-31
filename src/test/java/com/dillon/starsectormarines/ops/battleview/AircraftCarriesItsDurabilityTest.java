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
 * An aircraft shows what it has left, wherever it is.
 *
 * <p>An aircraft used to carry a durability bar only while it was a parked grid
 * unit, because the bar sweep walked the unit roster and an airborne craft is
 * not on it. So the whole airborne part of its life — the part somebody is
 * shooting at — showed nothing, and a machine limping home looked exactly like
 * one that had not been touched.
 *
 * <p>Asserted on the emitted {@link DrawList} rather than on a rendered frame:
 * a bar is a handful of quads that a sparsely-sampled review GIF may never
 * catch, and the question here is whether the pass emits it at all.
 */
class AircraftCarriesItsDurabilityTest {

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

    /** Quads emitted into the shuttle layer — the bar's rows and frame. */
    private static int barQuads(BattleSimulation sim) {
        BattleCamera camera = new BattleCamera(W, H);
        camera.setViewport(0f, 0f, 800f, 600f, 20f);
        RenderContext ctx = new RenderContext(sim, camera, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
        DrawList out = new DrawList();
        new ShuttleRenderSystem(spritesWithHull()).collect(ctx, out);
        int quads = 0;
        for (int i = 0; i < out.count(RenderLayer.SHUTTLES); i++) {
            DrawCommand command = out.buffer(RenderLayer.SHUTTLES)[i];
            if (command.kind() == DrawCommand.Kind.SOLID_RECT) quads++;
        }
        return quads;
    }

    private static long flying(BattleSimulation sim, ShuttleState phase, float hp) {
        long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                20f, 20f, 2f, 2f, 2f, 2f, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        mission.state = phase;
        sim.world().setHp(craft, hp);
        sim.world().kinematics(craft).teleport(20f, 15f, 0f);
        return craft;
    }

    /**
     * Every phase the aircraft is on the map for carries a bar.
     *
     * <p>Asked phase by phase rather than as a set, because the failure this
     * guards is a hand-written list of phases missing one — which is exactly
     * how the airborne states came to be left out in the first place.
     */
    @Test
    void anAircraftCarriesItsBarInEveryPhaseItIsOnTheMap() {
        ShuttleState[] onTheMap = {
                ShuttleState.TAXI_OUT, ShuttleState.HOLDING_SHORT, ShuttleState.TAKEOFF_ROLL,
                ShuttleState.DEPARTING, ShuttleState.INCOMING, ShuttleState.RETURNING,
                ShuttleState.ATTACK_RUN, ShuttleState.REPOSITION,
                ShuttleState.LANDING_ROLL, ShuttleState.TAXI_IN };
        for (ShuttleState phase : onTheMap) {
            BattleSimulation sim = openSim();
            flying(sim, phase, 40f);
            assertTrue(barQuads(sim) > 0, "no durability drawn while " + phase);
        }
    }

    /** A craft that is not on the map draws no bar, because it draws nothing. */
    @Test
    void anAircraftOffTheMapCarriesNoBar() {
        for (ShuttleState phase : new ShuttleState[]{ ShuttleState.PENDING, ShuttleState.GONE }) {
            BattleSimulation sim = openSim();
            long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                    20f, 20f, 2f, 2f, 2f, 2f, 5f);
            sim.world().mission(craft).state = phase;
            assertEquals(0, barQuads(sim), "a bar was drawn while " + phase);
        }
    }

    /**
     * A damaged aircraft draws a different bar from a whole one.
     *
     * <p>The count alone would pass for a bar frozen at full, so this compares
     * the emitted geometry: the structure row is narrower when there is less of
     * it left. Compared as a total span rather than by reading one quad, since
     * which quad is the row is the decorator's business.
     */
    @Test
    void aDamagedAircraftDrawsLessOfItsBar() {
        assertTrue(rowSpan(24f) < rowSpan(90f),
                "a badly damaged aircraft drew as much bar as a healthy one");
    }

    /** Total width of the coloured rows emitted for an aircraft at {@code hp}. */
    private static float rowSpan(float hp) {
        BattleSimulation sim = openSim();
        flying(sim, ShuttleState.INCOMING, hp);
        BattleCamera camera = new BattleCamera(W, H);
        camera.setViewport(0f, 0f, 800f, 600f, 20f);
        RenderContext ctx = new RenderContext(sim, camera, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
        DrawList out = new DrawList();
        new ShuttleRenderSystem(spritesWithHull()).collect(ctx, out);
        float span = 0f;
        for (int i = 0; i < out.count(RenderLayer.SHUTTLES); i++) {
            DrawCommand command = out.buffer(RenderLayer.SHUTTLES)[i];
            if (command.kind() == DrawCommand.Kind.SOLID_RECT) span += command.width();
        }
        return span;
    }
}
