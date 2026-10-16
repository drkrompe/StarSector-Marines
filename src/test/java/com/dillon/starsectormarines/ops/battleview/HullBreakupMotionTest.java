package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirCrashImpact;
import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.air.engine.HullPivotResolver;
import com.dillon.starsectormarines.battle.air.GroundWreck;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class HullBreakupMotionTest {
    private static final AirCrashImpact IMPACT = new AirCrashImpact(20, 20, 37, 12, 4, 25, 10, 123);

    @Test
    void sectionsStopSpinningAtDifferentTimes() {
        var pieces = HullBreakup.of(123).pieces();
        boolean separateStops = false;
        for (float t = .1f; t < 5f; t += .05f) {
            int stopped = 0;
            for (HullBreakup.Piece piece : pieces)
                if (HullBreakupMotion.pose(IMPACT, piece, t, 4).spinDegrees()
                        == HullBreakupMotion.pose(IMPACT, piece, 20, 4).spinDegrees()) stopped++;
            if (stopped > 0 && stopped < pieces.size()) separateStops = true;
        }
        assertTrue(separateStops, "individual sections must not all stop rotating together");
    }
    @Test
    void contactIsIntactAndSectionsThenMoveIndependentlyToFiniteRest() {
        var pieces = HullBreakup.of(IMPACT.seed()).pieces();
        for (HullBreakup.Piece piece : pieces) {
            assertEquals(new HullBreakupMotion.Pose(0, 0, 0), HullBreakupMotion.pose(IMPACT, piece, 0, 4));
            assertEquals(HullBreakupMotion.pose(IMPACT, piece, 20, 4),
                    HullBreakupMotion.pose(IMPACT, piece, 2000, 4));
        }
        assertNotEquals(HullBreakupMotion.pose(IMPACT, pieces.get(0), .5f, 4),
                HullBreakupMotion.pose(IMPACT, pieces.get(1), .5f, 4));
        // Sampling intervening frames cannot change a later pose.
        var expected = HullBreakupMotion.pose(IMPACT, pieces.get(2), 1.25f, 4);
        for (float t = 0; t < 1.25f; t += .017f) HullBreakupMotion.pose(IMPACT, pieces.get(2), t, 4);
        assertEquals(expected, HullBreakupMotion.pose(IMPACT, pieces.get(2), 1.25f, 4));
    }

    @Test
    void rotatingImpactRotatesTheWholeIndependentMotion() {
        var piece = HullBreakup.of(123).pieces().get(1);
        AirCrashImpact zero = new AirCrashImpact(0, 0, 0, 12, 4, 25, 0, 123);
        AirCrashImpact quarter = new AirCrashImpact(0, 0, 90, -4, 12, 25, 0, 123);
        var a = HullBreakupMotion.pose(zero, piece, .7f, 4);
        var b = HullBreakupMotion.pose(quarter, piece, .7f, 4);
        assertEquals(-a.y(), b.x(), .00001f);
        assertEquals(a.x(), b.y(), .00001f);
        assertEquals(a.spinDegrees(), b.spinDegrees());
    }

    @Test
    void persistentWreckUsesIdenticalCommandsDespiteDifferentFinalAnchor() {
        SpriteAPI sprite = (SpriteAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> null);
        ShuttleSpriteCache cache = new ShuttleSpriteCache(sprite, .61f, 208, 344);
        BattleCamera camera = new BattleCamera(100, 100);
        camera.setViewport(0, 0, 800, 800, 8);
        DrawList contact = new DrawList();
        HullWreckRenderer.emit(contact, camera, cache, FighterProfile.THUNDER, IMPACT, 10, 1);
        float length = HullFootprintResolver.visualLengthCells("thunder") * AirAppearance.GROUND_SCALE;
        float[] pivot = HullPivotResolver.pivotOffset("thunder");
        double angle = Math.toRadians(IMPACT.facingDegrees());
        for (int i = 0; i < contact.count(RenderLayer.UNITS); i++) {
            DrawCommand command = contact.buffer(RenderLayer.UNITS)[i];
            float x = ((command.sourceX() + command.sourceWidth() * .5f) / cache.pxW - .5f)
                    * length * cache.aspect + pivot[0] * AirAppearance.GROUND_SCALE;
            float y = (.5f - (command.sourceY() + command.sourceHeight() * .5f) / cache.pxH)
                    * length + pivot[1] * AirAppearance.GROUND_SCALE;
            assertEquals(camera.cellToScreenX(IMPACT.x() + (float) (x * Math.cos(angle) - y * Math.sin(angle))),
                    command.centerX(), .0001f);
            assertEquals(camera.cellToScreenY(IMPACT.y() + (float) (x * Math.sin(angle) + y * Math.cos(angle))),
                    command.centerY(), .0001f);
            assertEquals(IMPACT.facingDegrees(), command.angleDegrees());
        }
        GroundWreck wreck = new GroundWreck(45, 25, 72, FighterProfile.THUNDER, IMPACT);
        DrawList sliding = new DrawList(), settled = new DrawList();
        HullWreckRenderer.emit(sliding, camera, cache, FighterProfile.THUNDER, IMPACT, 11, 1);
        HullWreckRenderer.emit(settled, camera, cache, wreck.airframe, wreck.crashImpact, 11, 1);
        assertTrue(sliding.count(RenderLayer.UNITS) > 20);
        assertEquals(sliding.count(RenderLayer.UNITS), settled.count(RenderLayer.UNITS));
        for (int i = 0; i < sliding.count(RenderLayer.UNITS); i++) {
            DrawCommand a = sliding.buffer(RenderLayer.UNITS)[i], b = settled.buffer(RenderLayer.UNITS)[i];
            assertEquals(a.centerX(), b.centerX()); assertEquals(a.centerY(), b.centerY());
            assertEquals(a.width(), b.width()); assertEquals(a.height(), b.height());
            assertEquals(a.angleDegrees(), b.angleDegrees());
            assertEquals(a.sourceX(), b.sourceX()); assertEquals(a.sourceY(), b.sourceY());
            assertEquals(a.sourceWidth(), b.sourceWidth()); assertEquals(a.sourceHeight(), b.sourceHeight());
        }
    }
}
