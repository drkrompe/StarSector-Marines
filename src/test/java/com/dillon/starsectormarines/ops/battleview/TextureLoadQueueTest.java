package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureLoadQueueTest {

    /** A clock that moves by a fixed amount each time a step runs. */
    private static final class StepClock {
        long now;
    }

    private static List<TextureLoadQueue.Step> steps(StepClock clock, List<String> ran, long costEach, String... labels) {
        List<TextureLoadQueue.Step> out = new ArrayList<>();
        for (String label : labels) {
            out.add(new TextureLoadQueue.Step(label, () -> { ran.add(label); clock.now += costEach; }));
        }
        return out;
    }

    @Test
    void aFrameStopsOnceItsBudgetIsSpent() {
        StepClock clock = new StepClock();
        List<String> ran = new ArrayList<>();
        TextureLoadQueue queue = new TextureLoadQueue(steps(clock, ran, 4, "a", "b", "c", "d", "e"), () -> clock.now);

        queue.advance(8);

        assertEquals(List.of("a", "b"), ran);
        assertEquals(0.4f, queue.progress(), 1e-6);
        assertEquals("c", queue.label());
        assertFalse(queue.done());
    }

    @Test
    void aStepLargerThanTheBudgetStillRunsAlone() {
        StepClock clock = new StepClock();
        List<String> ran = new ArrayList<>();
        TextureLoadQueue queue = new TextureLoadQueue(steps(clock, ran, 100, "a", "b"), () -> clock.now);

        queue.advance(8);
        queue.advance(8);

        assertEquals(List.of("a", "b"), ran);
        assertTrue(queue.done());
        assertEquals(1f, queue.progress());
    }

    @Test
    void anEmptyQueueIsAlreadyComplete() {
        TextureLoadQueue queue = new TextureLoadQueue(List.of(), () -> 0L);
        assertTrue(queue.done());
        assertEquals(1f, queue.progress());
    }
}
