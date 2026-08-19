package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShotRenderServiceTest {

    private static final float EPS = 1e-5f;

    @Test
    void boltKinematicsGrowFromMuzzleAndClampAtDeclaredLength() {
        ShotEvent shot = boltShot(0f, 0f, 10f, 0f, 1f);
        ShotFx.Bolt bolt = (ShotFx.Bolt) ShotFx.of(shot).body();

        ShotRenderService.BoltPose start = ShotRenderService.boltPose(shot, bolt);
        assertPose(start, 0f, 0f, 0f, 0f, 0f, 0f);

        shot.lifetime = 0.5f;
        ShotRenderService.BoltPose middle = ShotRenderService.boltPose(shot, bolt);
        assertPose(middle, 5f, 0f, 4f, 0f, 1f, 1f);

        shot.lifetime = 0f;
        ShotRenderService.BoltPose end = ShotRenderService.boltPose(shot, bolt);
        assertPose(end, 10f, 0f, 9f, 0f, 1f, 1f);

        ShotEvent shortShot = boltShot(2f, 3f, 2.5f, 3f, 1f);
        shortShot.lifetime = 0.5f;
        ShotRenderService.BoltPose shortMiddle = ShotRenderService.boltPose(shortShot, bolt);
        assertPose(shortMiddle, 2.25f, 3f, 2f, 3f, 0.25f, 1f);
    }

    @Test
    void boltFadeInUsesFirstTenthOfFlight() {
        ShotEvent shot = boltShot(0f, 0f, 10f, 0f, 1f);
        ShotFx.Bolt bolt = (ShotFx.Bolt) ShotFx.of(shot).body();
        shot.lifetime = 0.95f;

        ShotRenderService.BoltPose pose = ShotRenderService.boltPose(shot, bolt);

        assertEquals(0.5f, pose.fadeIn(), EPS);
        assertEquals(0.125f, ShotRenderService.boltWidthCells(pose, bolt), EPS,
                "half-grown pulse bolt should also be half width");
    }

    @Test
    void collectResolvesEveryBoltFamilyTextureWithoutGlContext() {
        SpriteAPI fakeSprite = (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> null);
        ShuttleSpriteCache cache = new ShuttleSpriteCache(fakeSprite, 1f);
        List<String> requestedPaths = new ArrayList<>();
        BattleSprites sprites = new BattleSprites() {
            @Override
            public ShuttleSpriteCache projectileSprite(String path) {
                requestedPaths.add(path);
                return cache;
            }
        };
        ShotRenderService renderer = new ShotRenderService(sprites, new ImpactFx());

        for (MarineWeapon weapon : List.of(
                MarineWeapon.PULSE_RIFLE, MarineWeapon.DMR, MarineWeapon.DRONE_PULSE)) {
            BattleSimulation sim = openArena(20, 20);
            ShotEvent shot = boltShot(5f, 5f, 15f, 5f, 1f, weapon);
            shot.lifetime = 0.5f;
            sim.postShot(shot);
            DrawList out = new DrawList();

            renderer.collect(context(sim), out);

            ShotFx.Bolt bolt = (ShotFx.Bolt) ShotFx.of(shot).body();
            assertEquals(bolt.spritePath(), requestedPaths.get(requestedPaths.size() - 1));
            assertEquals(1, out.count(RenderLayer.SHOTS));
        }
        assertEquals(3, requestedPaths.size());
    }

    private static ShotEvent boltShot(float fromX, float fromY, float toX, float toY,
                                      float lifetime) {
        return boltShot(fromX, fromY, toX, toY, lifetime, MarineWeapon.PULSE_RIFLE);
    }

    private static ShotEvent boltShot(float fromX, float fromY, float toX, float toY,
                                      float lifetime, MarineWeapon weapon) {
        return new ShotEvent(fromX, fromY, toX, toY, true, Faction.MARINE,
                lifetime, null, weapon, null, null);
    }

    private static void assertPose(ShotRenderService.BoltPose pose,
                                   float headX, float headY, float tailX, float tailY,
                                   float visibleLength, float fadeIn) {
        assertEquals(headX, pose.headX(), EPS);
        assertEquals(headY, pose.headY(), EPS);
        assertEquals(tailX, pose.tailX(), EPS);
        assertEquals(tailY, pose.tailY(), EPS);
        assertEquals(visibleLength, pose.visibleLength(), EPS);
        assertEquals(fadeIn, pose.fadeIn(), EPS);
    }

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(w, h));
    }

    private static RenderContext context(BattleSimulation sim) {
        BattleCamera camera = new BattleCamera(20, 20);
        camera.setViewport(0f, 0f, 640f, 640f, 32f);
        return new RenderContext(sim, camera, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection());
    }
}
