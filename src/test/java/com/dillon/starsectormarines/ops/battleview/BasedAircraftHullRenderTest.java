package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.AirfieldSystem;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the unit collector emits for a hardstand before and after its aircraft
 * burns.
 *
 * <p>Asserts on the collected {@code DrawList} rather than on pixels: the
 * shuttle hull is a vanilla asset that does not ship in {@code mod/}, so the
 * headless Java2D drain cannot load it and no snapshot suite can photograph
 * this. The command carries the tint, the size, the bearing and the position,
 * which is everything the drain is given.
 */
class BasedAircraftHullRenderTest {

    private static final int W = 30;
    private static final int H = 30;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * A {@link BattleSprites} that has the hull loaded. The collector only
     * passes the token through to the drain, so an inert proxy stands in for
     * the texture a running game would have.
     */
    private static BattleSprites spritesWithHull() {
        SpriteAPI token = (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "hull";
                    default -> null;
                });
        EnumMap<ShuttleType, ShuttleSpriteCache> loaded = new EnumMap<>(ShuttleType.class);
        loaded.put(ShuttleType.AEROSHUTTLE, new ShuttleSpriteCache(token, 1f));
        return new BattleSprites() {
            @Override
            public EnumMap<ShuttleType, ShuttleSpriteCache> shuttleSprites() {
                return loaded;
            }
        };
    }

    private static List<DrawCommand> hullSprites(BattleSimulation sim) {
        BattleCamera camera = new BattleCamera(W, H);
        camera.setViewport(0f, 0f, 800f, 600f, 20f);
        RenderContext ctx = new RenderContext(sim, camera, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
        DrawList out = new DrawList();
        new UnitRenderService(spritesWithHull()).collect(ctx, out);
        List<DrawCommand> sprites = new ArrayList<>();
        for (int i = 0; i < out.count(RenderLayer.UNITS); i++) {
            DrawCommand command = out.buffer(RenderLayer.UNITS)[i];
            if (command.kind() == DrawCommand.Kind.SPRITE) sprites.add(command);
        }
        return sprites;
    }

    private static AirfieldService.Berth berth(BattleSimulation sim) {
        return sim.getAirfieldService().addBerth(
                LandingPad.garrison(12, 14, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 90f);
    }

    /** The aircraft on its stand draws in its own colours. */
    @Test
    void aParkedAircraftDrawsUntinted() {
        BattleSimulation sim = openSim();
        berth(sim);
        new AirfieldSystem(Faction.DEFENDER).tick(1f / 30f, sim, sim.getAirfieldService());

        List<DrawCommand> hulls = hullSprites(sim);

        assertEquals(1, hulls.size(), "one aircraft, one hull");
        assertEquals(1f, hulls.get(0).red(), 1e-4f);
        assertEquals(1f, hulls.get(0).green(), 1e-4f);
        assertEquals(1f, hulls.get(0).blue(), 1e-4f);
    }

    /**
     * Burned on its stand, the same hull keeps being drawn, charred.
     *
     * <p>The position, size and bearing have to match the aircraft that was
     * standing there — a hulk that landed anywhere else reads as a second
     * object rather than as the wreck of the first.
     */
    @Test
    void aBurnedAircraftLeavesACharredHullWhereItStood() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, sim.getAirfieldService());
        DrawCommand parked = hullSprites(sim).get(0);
        float x = parked.centerX();
        float y = parked.centerY();
        float width = parked.width();
        float angle = parked.angleDegrees();

        sim.applyDamage(berth.airframeId, 100_000f, 100_000f);
        system.tick(1f / 30f, sim, sim.getAirfieldService());

        List<DrawCommand> hulls = hullSprites(sim);
        assertEquals(1, hulls.size(), "the wreck replaces the aircraft, it does not double it");
        DrawCommand wreck = hulls.get(0);
        assertEquals(x, wreck.centerX(), 1e-3f);
        assertEquals(y, wreck.centerY(), 1e-3f);
        assertEquals(width, wreck.width(), 1e-3f);
        assertEquals(angle, wreck.angleDegrees(), 1e-3f);
        assertTrue(wreck.red() < 0.5f && wreck.green() < 0.5f && wreck.blue() < 0.5f,
                "burnt, not merely shaded: " + wreck.red() + "," + wreck.green()
                        + "," + wreck.blue());
        assertTrue(wreck.red() > 0.05f,
                "scorched panel, not a hole in the apron");
    }
}
