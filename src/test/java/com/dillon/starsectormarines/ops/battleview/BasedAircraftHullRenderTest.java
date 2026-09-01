package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.AirfieldSystem;
import com.dillon.starsectormarines.battle.air.Airframe;
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
import java.util.LinkedHashMap;
import java.util.Map;
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
 * this. The commands carry the tint, the size, the bearing, the position and —
 * for a wreck — which part of the hull each piece is cut from, which is
 * everything the drain is given.
 */
class BasedAircraftHullRenderTest {

    private static final int W = 30;
    private static final int H = 30;
    /** Stand-in hull image size. Not square, and divisible by neither grid axis — the awkward case. */
    private static final int HULL_PX_W = 82;
    private static final int HULL_PX_H = 66;

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
        Map<Airframe, ShuttleSpriteCache> loaded = new LinkedHashMap<>();
        loaded.put(ShuttleType.AEROSHUTTLE, new ShuttleSpriteCache(
                token, HULL_PX_W / (float) HULL_PX_H, HULL_PX_W, HULL_PX_H));
        return new BattleSprites() {
            @Override
            public Map<Airframe, ShuttleSpriteCache> airframeSprites() {
                return loaded;
            }
        };
    }

    private static List<DrawCommand> hullDraws(BattleSimulation sim, DrawCommand.Kind kind) {
        return hullDraws(sim, kind, 20f);
    }

    private static List<DrawCommand> hullDraws(BattleSimulation sim, DrawCommand.Kind kind,
                                               float cellPx) {
        BattleCamera camera = new BattleCamera(W, H);
        camera.setViewport(0f, 0f, 800f, 600f, cellPx);
        RenderContext ctx = new RenderContext(sim, camera, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
        DrawList out = new DrawList();
        new UnitRenderService(spritesWithHull()).collect(ctx, out);
        List<DrawCommand> matching = new ArrayList<>();
        for (int i = 0; i < out.count(RenderLayer.UNITS); i++) {
            DrawCommand command = out.buffer(RenderLayer.UNITS)[i];
            if (command.kind() == kind) matching.add(command);
        }
        return matching;
    }

    private static AirfieldService.Berth berth(BattleSimulation sim) {
        return sim.getAirfieldService().addBerth(
                LandingPad.garrison(12, 14, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 90f);
    }

    private static BattleSimulation withBurnedAircraft() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim);
        AirfieldSystem system = new AirfieldSystem();
        system.tick(1f / 30f, sim, sim.getAirfieldService());
        sim.applyDamage(berth.airframeId, 100_000f, 100_000f);
        system.tick(1f / 30f, sim, sim.getAirfieldService());
        return sim;
    }

    /** The aircraft on its stand is one whole hull, drawn in its own colours. */
    @Test
    void aParkedAircraftDrawsAsOneUntintedHull() {
        BattleSimulation sim = openSim();
        berth(sim);
        new AirfieldSystem().tick(1f / 30f, sim, sim.getAirfieldService());

        List<DrawCommand> hulls = hullDraws(sim, DrawCommand.Kind.SPRITE);

        assertEquals(1, hulls.size(), "one aircraft, one hull");
        assertEquals(1f, hulls.get(0).red(), 1e-4f);
        assertEquals(1f, hulls.get(0).green(), 1e-4f);
        assertEquals(1f, hulls.get(0).blue(), 1e-4f);
    }

    /**
     * A burned aircraft is drawn as pieces of its own sprite, and those pieces
     * account for the whole of it.
     *
     * <p>The strongest thing this can be asked. Every pixel of the hull image
     * is drawn exactly once across the emitted strips: no part of the aircraft
     * is missing from the wreck, and no part of it appears twice.
     */
    @Test
    void theWreckIsCutFromTheWholeHullAndNothingElse() {
        List<DrawCommand> strips = hullDraws(withBurnedAircraft(), DrawCommand.Kind.SHEET_QUAD);

        assertTrue(strips.size() > 3, "a torn hull is more than three rectangles: " + strips.size());
        int[][] drawn = new int[HULL_PX_W][HULL_PX_H];
        for (DrawCommand strip : strips) {
            for (int x = strip.sourceX(); x < strip.sourceX() + strip.sourceWidth(); x++) {
                for (int y = strip.sourceY(); y < strip.sourceY() + strip.sourceHeight(); y++) {
                    drawn[x][y]++;
                }
            }
        }
        for (int x = 0; x < HULL_PX_W; x++) {
            for (int y = 0; y < HULL_PX_H; y++) {
                assertEquals(1, drawn[x][y], "hull pixel (" + x + "," + y + ") drawn " + drawn[x][y] + " times");
            }
        }
    }

    /** Every piece of it is charred, and none of it is drawn whole. */
    @Test
    void everyPieceOfTheWreckIsCharred() {
        BattleSimulation sim = withBurnedAircraft();

        assertEquals(0, hullDraws(sim, DrawCommand.Kind.SPRITE).size(),
                "a broken hull is never drawn as one sprite");
        for (DrawCommand strip : hullDraws(sim, DrawCommand.Kind.SHEET_QUAD)) {
            assertTrue(strip.red() < 0.5f && strip.green() < 0.5f && strip.blue() < 0.5f,
                    "burnt, not merely shaded: " + strip.red() + "," + strip.green()
                            + "," + strip.blue());
            assertTrue(strip.red() > 0.05f, "scorched panel, not a hole in the apron");
        }
    }

    /**
     * However far away the camera is, the wreck is the size of the aircraft.
     *
     * <p>Drawn small enough, the whole tear fits inside a couple of pixels, and
     * there the fixed pixel of overlap each strip carries to close its seams is
     * not a hairline fix but several times the strip itself. Unbounded it
     * tripled every piece and fused the tears shut: a review frame of a burnt
     * airfield showed three dark blobs where three broken aircraft should have
     * been, which is the one thing a picture of a raid has to get right.
     */
    @Test
    void theWreckIsTheSizeOfTheAircraftAtEveryZoom() {
        for (float cellPx : new float[]{2f, 6f, 20f, 64f}) {
            BattleSimulation parked = openSim();
            berth(parked);
            new AirfieldSystem().tick(1f / 30f, parked, parked.getAirfieldService());
            DrawCommand aircraft = hullDraws(parked, DrawCommand.Kind.SPRITE, cellPx).get(0);

            // Every strip is one lattice row tall, so the row height of the
            // aircraft's own drawn hull is what each of them should measure.
            float row = aircraft.height() / HullBreakup.GRID;
            for (DrawCommand strip : hullDraws(withBurnedAircraft(), DrawCommand.Kind.SHEET_QUAD, cellPx)) {
                assertTrue(strip.height() <= row * 1.6f,
                        "at " + cellPx + "px per cell a strip is " + strip.height()
                                + "px tall where a row of the hull is " + row + "px");
            }
        }
    }

    /**
     * It settled where it stood.
     *
     * <p>The pieces have shifted, and the aircraft has not moved: every strip
     * lands within a hull's length of where the intact aircraft was drawn.
     * Debris thrown across the apron would be a different event.
     */
    @Test
    void theWreckLiesWhereTheAircraftWasParked() {
        BattleSimulation parked = openSim();
        berth(parked);
        new AirfieldSystem().tick(1f / 30f, parked, parked.getAirfieldService());
        DrawCommand aircraft = hullDraws(parked, DrawCommand.Kind.SPRITE).get(0);
        float hullPx = aircraft.height();

        for (DrawCommand strip : hullDraws(withBurnedAircraft(), DrawCommand.Kind.SHEET_QUAD)) {
            float dx = strip.centerX() - aircraft.centerX();
            float dy = strip.centerY() - aircraft.centerY();
            assertTrue(Math.sqrt(dx * dx + dy * dy) < hullPx,
                    "a piece landed " + Math.sqrt(dx * dx + dy * dy) + "px away, hull is " + hullPx);
        }
    }
}
