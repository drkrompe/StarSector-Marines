package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelCameraTest {

    private static final float STEP = 0.2f;

    private static DuelCamera camera() {
        return new DuelCamera(1920, 1080, 400, 300, 70f);
    }

    private static List<DuelCamera.Subject> pair(float ax, float bx, float y) {
        return List.of(new DuelCamera.Subject(ax, y, 6f), new DuelCamera.Subject(bx, y, 6f));
    }

    private static void assertHolds(DuelCamera.View v, DuelCamera c, List<DuelCamera.Subject> subjects) {
        float cellPx = (float) Math.floor(1920 / v.cellsAcross());
        float halfX = 1920f / cellPx * 0.5f;
        float halfY = (1080f - BattleReviewFrameRenderer.HEADER_HEIGHT) / cellPx * 0.5f;
        for (DuelCamera.Subject s : subjects) {
            assertTrue(s.x() - s.radius() >= v.centerX() - halfX && s.x() + s.radius() <= v.centerX() + halfX,
                    "x in frame: " + s + " " + v);
            assertTrue(s.y() - s.radius() >= v.centerY() - halfY && s.y() + s.radius() <= v.centerY() + halfY,
                    "y in frame: " + s + " " + v);
        }
    }

    @Test void framesThePairCloserThanTheMap() {
        DuelCamera c = camera();
        List<DuelCamera.Subject> s = pair(150f, 230f, 150f);
        DuelCamera.View v = c.update(s, 0f);
        assertTrue(v.cellsAcross() < c.maxAcross() * 0.6f, "closer than the whole map: " + v);
        assertTrue(v.cellsAcross() > 92f, "keeps the pair and its padding: " + v);
        assertHolds(v, c, s);
    }

    @Test void neverLosesAHullThatOutrunsTheSpring() {
        DuelCamera c = camera();
        c.update(pair(180f, 220f, 150f), 0f);
        for (int i = 0; i < 40; i++) {
            List<DuelCamera.Subject> s = pair(180f - 4f * i, 220f + 3f * i, 150f + 2f * i);
            assertHolds(c.update(s, STEP), c, s);
        }
    }

    @Test void aSmallJitterDoesNotMoveTheFrame() {
        DuelCamera c = camera();
        DuelCamera.View first = c.update(pair(150f, 230f, 150f), 0f);
        DuelCamera.View last = first;
        for (int i = 0; i < 20; i++) {
            float wobble = (i % 2 == 0 ? 1.5f : -1.5f);
            last = c.update(pair(150f + wobble, 230f - wobble, 150f + wobble), STEP);
        }
        assertEquals(first.centerX(), last.centerX(), 0.01f);
        assertEquals(first.centerY(), last.centerY(), 0.01f);
        assertEquals(first.cellsAcross(), last.cellsAcross(), 0.01f);
    }

    @Test void zoomsInOnlyWhenClearlyCloserAndWithoutOvershoot() {
        DuelCamera c = camera();
        float wide = c.update(pair(100f, 300f, 150f), 0f).cellsAcross();
        // Slightly closer: inside the hysteresis band, the target holds.
        float held = wide;
        for (int i = 0; i < 30; i++) held = c.update(pair(110f, 290f, 150f), STEP).cellsAcross();
        assertEquals(wide, held, 0.01f);
        // Much closer: it eases in monotonically.
        float previous = held;
        for (int i = 0; i < 60; i++) {
            float now = c.update(pair(185f, 215f, 150f), STEP).cellsAcross();
            assertTrue(now <= previous + 1e-3f, "monotone zoom-in");
            previous = now;
        }
        assertTrue(previous < wide * 0.7f, "closed in: " + previous);
    }

    @Test void staysInsideTheMapAndWithinItsLimits() {
        DuelCamera c = camera();
        DuelCamera.View v = c.update(List.of(new DuelCamera.Subject(5f, 5f, 3f)), 0f);
        assertTrue(v.cellsAcross() >= 70f, "minimum width held");
        assertTrue(v.centerX() - v.cellsAcross() / 2f >= -0.01f);
        assertTrue(v.centerY() - v.cellsDown(c.aspect()) / 2f >= -0.01f);
        DuelCamera.View wide = c.update(pair(0f, 400f, 150f), STEP);
        assertTrue(wide.cellsAcross() <= c.maxAcross() + 1e-3f, "never wider than the map");
    }

    @Test void theSameInputsGiveTheSameViews() {
        DuelCamera a = camera();
        DuelCamera b = camera();
        for (int i = 0; i < 25; i++) {
            List<DuelCamera.Subject> s = pair(120f + 3f * i, 260f - i, 140f + i);
            assertEquals(a.update(s, STEP), b.update(s, STEP));
        }
    }
}
