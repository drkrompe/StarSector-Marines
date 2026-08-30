package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.OrdnanceDelivery;
import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drawn round: what reaches the {@link DrawList}, and when the ground is
 * told the round got there.
 */
final class OrdnanceTraceFxServiceTest {

    private static final float EPS = 1e-3f;

    @Test
    void aBeamCoversItsWholePathTheInstantItIsReleased() {
        OrdnanceTraceFxService service = new OrdnanceTraceFxService(null);
        service.spawn(release(OrdnanceDelivery.BEAM, 4f, 4f, 12f, 4f));
        service.advance(0f);

        DrawCommand line = onlyLine(service);
        BattleCamera camera = camera();
        assertEquals(camera.cellToScreenX(4f), line.centerX(), EPS);
        assertEquals(camera.cellToScreenX(12f), line.width(), EPS);
        assertEquals(1f, line.alpha(), EPS);
    }

    @Test
    void aShellGrowsOutOfTheMuzzleAndCapsAtItsOwnLength() {
        OrdnanceTraceFxService service = new OrdnanceTraceFxService(null);
        OrdnanceFx.Streak streak = (OrdnanceFx.Streak) OrdnanceFx.of(OrdnanceDelivery.SHELL).trace();
        service.spawn(release(OrdnanceDelivery.SHELL, 0f, 0f, 20f, 0f));

        // A tenth of the way down a twenty-cell path is two cells of travel,
        // which is less than the streak is long — so the tail is still parked
        // at the muzzle.
        service.advance(streak.flightSeconds() * 0.1f);
        DrawCommand early = onlyLine(service);
        BattleCamera camera = camera();
        assertEquals(camera.cellToScreenX(0f), early.centerX(), EPS);

        // Most of the way down, the tail has left the muzzle and trails the
        // head by exactly the declared length.
        service.advance(streak.flightSeconds() * 0.8f);
        DrawCommand late = onlyLine(service);
        float tailCells = screenToCells(camera, late.centerX());
        float headCells = screenToCells(camera, late.width());
        assertEquals(streak.lengthCells(), headCells - tailCells, 1e-2f);
    }

    @Test
    void arrivalIsPublishedOnceWhenTheRoundGetsThere() {
        OrdnanceTraceFxService service = new OrdnanceTraceFxService(null);
        OrdnanceRelease bomb = release(OrdnanceDelivery.BOMB, 0f, 0f, 6f, 0f);
        float fall = OrdnanceFx.of(OrdnanceDelivery.BOMB).trace().flightSeconds();
        service.spawn(bomb);

        service.advance(fall * 0.5f);
        assertTrue(service.arrivalsThisFrame().isEmpty(),
                "the blast may not precede the bomb");

        service.advance(fall * 0.6f);
        assertEquals(1, service.arrivalsThisFrame().size());
        assertSame(bomb, service.arrivalsThisFrame().get(0));

        service.advance(0.001f);
        assertTrue(service.arrivalsThisFrame().isEmpty(), "an arrival fires exactly once");
    }

    @Test
    void aRoundIsGoneOnceItHasFinishedFading() {
        OrdnanceTraceFxService service = new OrdnanceTraceFxService(null);
        service.spawn(release(OrdnanceDelivery.SHELL, 0f, 0f, 10f, 0f));
        assertEquals(1, service.liveCount());

        service.advance(OrdnanceFx.of(OrdnanceDelivery.SHELL).lifetimeSeconds() + 0.01f);
        assertEquals(0, service.liveCount());
    }

    @Test
    void theLiveListIsBounded() {
        OrdnanceTraceFxService service = new OrdnanceTraceFxService(null);
        for (int i = 0; i < OrdnanceTraceFxService.MAX_LIVE + 40; i++) {
            service.spawn(release(OrdnanceDelivery.SHELL, 0f, 0f, 10f, 0f));
        }
        assertEquals(OrdnanceTraceFxService.MAX_LIVE, service.liveCount());
    }

    private static DrawCommand onlyLine(OrdnanceTraceFxService service) {
        DrawList out = new DrawList();
        service.collect(camera(), out, 1f);
        DrawCommand found = null;
        int lines = 0;
        DrawCommand[] buffer = out.buffer(RenderLayer.SHOTS);
        for (int i = 0, n = out.count(RenderLayer.SHOTS); i < n; i++) {
            if (buffer[i].kind() != DrawCommand.Kind.LINE) continue;
            found = buffer[i];
            lines++;
        }
        assertEquals(1, lines, "expected exactly one drawn round");
        assertNotNull(found);
        return found;
    }

    private static OrdnanceRelease release(OrdnanceDelivery delivery,
                                           float fromX, float fromY, float toX, float toY) {
        return new OrdnanceRelease(1L, delivery, fromX, fromY, toX, toY, 1.2f,
                Faction.DEFENDER, /*flightTimeSec*/ 0f);
    }

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(40, 40);
        camera.setViewport(0f, 0f, 640f, 640f, 32f);
        return camera;
    }

    private static float screenToCells(BattleCamera camera, float screenX) {
        return camera.panCellX() + (screenX - (camera.vpX() + camera.vpW() * 0.5f))
                / camera.cellPxSize();
    }

    /**
     * A bomb's picture stays in the air exactly as long as the bomb does.
     *
     * <p>The two halves of a gun run were built separately: one gave ordnance a
     * real flight time — a bomb falls for about a second and a half — and the
     * other drew a falling body for a fixed 0.35s. Composed, the drawn bomb
     * landed a second before the explosion it was supposed to cause. The
     * simulation's own number wins whenever it has one.
     */
    @Test
    void aFallingBodyIsDrawnForAsLongAsTheRoundIsActuallyInTheAir() {
        OrdnanceTraceFxService fx = new OrdnanceTraceFxService(null);
        float realFall = 1.429f;
        fx.spawn(new OrdnanceRelease(1L, OrdnanceDelivery.BOMB, 0f, 0f, 10f, 0f,
                1.2f, Faction.DEFENDER, realFall));

        // The composition's own fall is far shorter, so a service reading the
        // preset would have called this arrived long ago.
        fx.advance(0.5f);
        assertTrue(fx.arrivalsThisFrame().isEmpty(),
                "the bomb arrived while it was still falling");

        fx.advance(realFall);
        assertEquals(1, fx.arrivalsThisFrame().size(),
                "the bomb never arrived at the time the simulation said it would");
    }

    /** With no stated flight time the composition still decides, as before. */
    @Test
    void aRoundWithNoStatedFlightTimeFallsBackToItsComposition() {
        OrdnanceTraceFxService fx = new OrdnanceTraceFxService(null);
        fx.spawn(new OrdnanceRelease(1L, OrdnanceDelivery.BOMB, 0f, 0f, 10f, 0f,
                1.2f, Faction.DEFENDER, /*flightTimeSec*/ 0f));
        fx.advance(OrdnanceFx.of(OrdnanceDelivery.BOMB).trace().flightSeconds() + 0.01f);
        assertEquals(1, fx.arrivalsThisFrame().size(),
                "a round with no stated flight time never arrived");
    }
}
