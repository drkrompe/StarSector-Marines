package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
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
        assertPose(start, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

        shot.lifetime = 0.5f;
        ShotRenderService.BoltPose middle = ShotRenderService.boltPose(shot, bolt);
        assertPose(middle, 5f, 0f, 0f, 4f, 0f, 0f, 1f, 1f);

        shot.lifetime = 0f;
        ShotRenderService.BoltPose end = ShotRenderService.boltPose(shot, bolt);
        assertPose(end, 10f, 0f, 0f, 9f, 0f, 0f, 1f, 1f);

        ShotEvent shortShot = boltShot(2f, 3f, 2.5f, 3f, 1f);
        shortShot.lifetime = 0.5f;
        ShotRenderService.BoltPose shortMiddle = ShotRenderService.boltPose(shortShot, bolt);
        assertPose(shortMiddle, 2.25f, 3f, 0f, 2f, 3f, 0f, 0.25f, 1f);
    }

    @Test
    void boltPoseInterpolatesElevationForProjectedHighAndLowFlight() {
        ShotEvent shot = new ShotEvent(0f, 0f, 0f, 10f, 0f, 2f,
                false, Faction.MARINE, 1f,
                null, WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), null, null, 1f, false,
                BallisticResolver.StopKind.OVERSHOOT);
        shot.lifetime = 0.5f;

        ShotRenderService.BoltPose pose = ShotRenderService.boltPose(
                shot, (ShotFx.Bolt) ShotFx.of(shot).body());

        assertPose(pose, 5f, 0f, 1f, 4f, 0f, 0.8f, 1f, 1f);
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
    void projectileTracerTailFollowsTheShellAndClampsAtItsAuthoredLength() {
        WeaponDef rifle = WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID);
        ShotEvent shot = boltShot(0f, 0f, 10f, 0f, 1f, rifle);
        ShotFx fx = ShotFx.of(shot);

        ShotRenderService.TracerTailPose start = ShotRenderService.tracerTailPose(
                shot, fx, fx.tracerTail());
        assertTracerPose(start, 0f, 0f, 0f, 0f, 0f, 0f);

        shot.lifetime = 0.5f;
        ShotRenderService.TracerTailPose middle = ShotRenderService.tracerTailPose(
                shot, fx, fx.tracerTail());
        assertTracerPose(middle, 5f, 0f, 4.35f, 0f, 0.65f, 1f);
    }

    @Test
    void collectPaintsTheShortTracerUnderTheRifleProjectile() {
        SpriteAPI fakeSprite = (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> null);
        BattleSprites sprites = new BattleSprites() {
            @Override
            public ShuttleSpriteCache projectileSprite(String path) {
                return new ShuttleSpriteCache(fakeSprite, 1f);
            }
        };
        BattleSimulation sim = openArena(20, 20);
        ShotEvent shot = boltShot(5f, 5f, 15f, 5f, 1f,
                WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID));
        shot.lifetime = 0.5f;
        sim.postShot(shot);
        DrawList out = new DrawList();

        new ShotRenderService(sprites, new ImpactFx()).collect(context(sim), out);

        assertEquals(2, out.count(RenderLayer.SHOTS), "tail plus projectile sprite");
        DrawCommand tail = out.buffer(RenderLayer.SHOTS)[0];
        assertEquals(DrawCommand.Kind.LINE, tail.kind(),
                "tail paints beneath the shell");
        assertEquals(0.65f * 32f, Math.abs(tail.width() - tail.centerX()), 1e-4f,
                "authored world length survives camera projection");
        assertEquals(2f, tail.angleDegrees(), EPS, "tracer remains readable in screen pixels");
        assertEquals(DrawCommand.Kind.SPRITE, out.buffer(RenderLayer.SHOTS)[1].kind());
    }

    @Test
    void shoulderLaserLingersForOneSecondWithoutChangingItsShotClock() {
        WeaponDef laser = WeaponRegistry.require(WeaponRegistry.MECH_SHOULDER_LASER_ID);
        ShotEvent shot = new ShotEvent(3f, 5f, 17f, 5f, true, Faction.MARINE, 0.10f,
                null, null, null, laser);
        BeamFxService beams = new BeamFxService();
        beams.spawn(shot);
        DrawList out = new DrawList();
        BattleCamera camera = context(openArena(20, 20)).camera;

        beams.collect(camera, out, 1f);

        assertEquals(2, out.count(RenderLayer.SHOTS), "blue glow plus white-blue core");
        DrawCommand glow = out.buffer(RenderLayer.SHOTS)[0];
        DrawCommand core = out.buffer(RenderLayer.SHOTS)[1];
        assertEquals(12f, glow.angleDegrees(), EPS);
        assertEquals(3f, core.angleDegrees(), EPS);
        assertEquals(laser.beamStyle().glowColor().getBlue() / 255f, glow.blue(), EPS);
        assertEquals(laser.tracerColor().getBlue() / 255f, core.blue(), EPS);
        assertEquals(1f, laser.beamStyle().lifetimeSec(), EPS);
        assertEquals(0.10f, shot.lifetimeMax, EPS,
                "presentation lifetime must not alter ballistic timing");

        beams.advance(0.75f);
        DrawList late = new DrawList();
        beams.collect(camera, late, 1f);
        assertEquals(2, late.count(RenderLayer.SHOTS), "afterimage remains visible at 750 ms");

        beams.advance(0.26f);
        DrawList expired = new DrawList();
        beams.collect(camera, expired, 1f);
        assertEquals(0, expired.count(RenderLayer.SHOTS));
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

        for (WeaponDef weapon : List.of(
                WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), WeaponRegistry.require(WeaponRegistry.DMR_ID), WeaponRegistry.require(WeaponRegistry.DRONE_PULSE_ID))) {
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
        return boltShot(fromX, fromY, toX, toY, lifetime, WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID));
    }

    private static ShotEvent boltShot(float fromX, float fromY, float toX, float toY,
                                      float lifetime, WeaponDef weapon) {
        return new ShotEvent(fromX, fromY, toX, toY, true, Faction.MARINE,
                lifetime, null, weapon, null, null);
    }

    private static void assertPose(ShotRenderService.BoltPose pose,
                                   float headX, float headY, float headZ,
                                   float tailX, float tailY, float tailZ,
                                   float visibleLength, float fadeIn) {
        assertEquals(headX, pose.headX(), EPS);
        assertEquals(headY, pose.headY(), EPS);
        assertEquals(headZ, pose.headZ(), EPS);
        assertEquals(tailX, pose.tailX(), EPS);
        assertEquals(tailY, pose.tailY(), EPS);
        assertEquals(tailZ, pose.tailZ(), EPS);
        assertEquals(visibleLength, pose.visibleLength(), EPS);
        assertEquals(fadeIn, pose.fadeIn(), EPS);
    }

    private static void assertTracerPose(ShotRenderService.TracerTailPose pose,
                                         float headX, float headY,
                                         float tailX, float tailY,
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
