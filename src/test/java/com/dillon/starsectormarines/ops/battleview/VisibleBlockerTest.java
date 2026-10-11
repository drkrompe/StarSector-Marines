package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VisibleBlockerTest {
    @Test void solidPropsUseTheSameRuleWithoutAnyTreeIdentity() {
        try (var sim = VisibleBlockerScene.create(TreeCanopy.OAK, 0)) {
            var solid = new DoodadDef("custom.obstacle", "custom.png", 0, 0,
                    DoodadCover.HEAVY, 1, 5, 5);
            assertTrue(DoodadRenderSystem.hasObservedSurface(new Doodad(8, 6, solid), sim.getGrid(), sim.getFogOfWar()));
            assertFalse(DoodadRenderSystem.hasObservedSurface(new Doodad(18, 6, solid), sim.getGrid(), sim.getFogOfWar()));
            var hollow = new DoodadDef("custom.hollow", "custom.png", 0, 0, DoodadCover.HEAVY, 1,
                    5, 5, null, 64, List.of("....#", "....#", "....#", "....#", "....#"));
            assertFalse(DoodadRenderSystem.hasObservedSurface(new Doodad(8, 6, hollow),
                    sim.getGrid(), sim.getFogOfWar()), "seeing empty cells must not admit the far surface");
        }
    }
    @Test void fellingRecastsStationaryObserversAndRevealsTheNextSurface() {
        try (var sim = VisibleBlockerScene.create(TreeCanopy.OAK, 0)) {
            assertFalse(sim.getFogOfWar().isCellRevealed(16, 8));
            assertTrue(sim.damageCell(10, 8, 200));
            VisibleBlockerScene.settle(sim);
            assertTrue(sim.getFogOfWar().isCellRevealed(16, 8));
            assertEquals(FogOfWarService.VIS_VISIBLE, sim.getFogOfWar().getUnitVisibility(1));
            assertTrue(DoodadRenderSystem.hasObservedSurface(sim.getDoodads().get(1),
                    sim.getGrid(), sim.getFogOfWar()));
            assertFalse(sim.getFogOfWar().isCellRevealed(24, 8), "the next tree still casts its own shadow");
        }
    }

    @Test void visibleCrownPaintDoesNotRevealHiddenCellsOrTheNextTree() {
        for (TreeCanopy canopy : TreeCanopy.values()) for (int turns = 0; turns < 4; turns++)
            try (var sim = VisibleBlockerScene.create(canopy, turns)) {
                var fog = sim.getFogOfWar(); var grid = sim.getGrid();
                assertTrue(DoodadRenderSystem.hasObservedSurface(sim.getDoodads().get(0), grid, fog));
                assertFalse(DoodadRenderSystem.hasObservedSurface(sim.getDoodads().get(1), grid, fog));
                int row = 6 + canopy.height(turns) / 2;
                assertFalse(fog.isCellRevealed(10, row), "canopy paint must not broaden the reveal bitmap");
                assertFalse(fog.isCellRevealed(16, row));
                assertEquals(FogOfWarService.VIS_HIDDEN, fog.getUnitVisibility(1));
                assertFalse(grid.hasLineOfSight(4, row, 16, row));
            }
    }

    @Test void collectedCrownUsesItsActualDamagedSpriteAndStopsAfterFellingOrSmoke() {
        try (var sim = VisibleBlockerScene.create(TreeCanopy.OAK, 1)) {
            SpriteAPI sheet = (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                    new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> null);
            BattleSprites sprites = new BattleSprites() {
                @Override public SpriteAPI extraTerrainSheet(String path) { return sheet; }
            };
            var system = new DoodadRenderSystem(sprites);
            BattleCamera camera = new BattleCamera(26, 18);
            camera.setViewport(0, 0, 832, 576, 32);
            var ctx = new RenderContext(sim, camera, null, .75f, 0, false, new HighlightOverlay(), new Selection());
            var out = new DrawList();
            system.collectVisibleSurfaces(ctx, out);
            assertEquals(1, out.count(RenderLayer.FOG));
            assertEquals(90, out.buffer(RenderLayer.FOG)[0].angleDegrees());
            boolean[] reveal = sim.getFogOfWar().cellRevealedArray().clone();
            sim.damageCell(10, 8, 1);
            assertTrue(sim.getDoodads().get(0).definitionId().endsWith("-damaged"));
            out = new DrawList(); system.collectVisibleSurfaces(ctx, out);
            assertEquals(1, out.count(RenderLayer.FOG));
            assertArrayEquals(reveal, sim.getFogOfWar().cellRevealedArray());
            // A smoke curtain hides the whole near edge; clear-air sight must not light the crown.
            for (int y = 0; y < 18; y++) sim.getGrid().addTransientOpacityAt(sim.getGrid().index(6, y));
            VisibleBlockerScene.settle(sim);
            out = new DrawList(); system.collectVisibleSurfaces(ctx, out);
            assertEquals(0, out.count(RenderLayer.FOG));
            sim.damageCell(10, 8, 199);
            assertTrue(sim.getDoodads().get(0).definitionId().endsWith("-destroyed"));
            assertFalse(DoodadRenderSystem.hasObservedSurface(sim.getDoodads().get(0),
                    sim.getGrid(), sim.getFogOfWar()));
        }
    }
}
