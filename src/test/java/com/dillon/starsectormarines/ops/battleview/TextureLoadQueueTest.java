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
        assertEquals(0.4f, queue.fraction(), 1e-6);
        assertEquals("c", queue.stage());
        assertFalse(queue.done());
    }

    @Test
    void aStepCanQueueWhatItFoundStillToLoad() {
        StepClock clock = new StepClock();
        List<String> ran = new ArrayList<>();
        TextureLoadQueue[] queue = new TextureLoadQueue[1];
        List<TextureLoadQueue.Step> first = new ArrayList<>();
        first.add(new TextureLoadQueue.Step("survey", () -> {
            ran.add("survey");
            clock.now += 4;
            queue[0].append(steps(clock, ran, 4, "found-1", "found-2"));
        }));
        queue[0] = new TextureLoadQueue(first, () -> clock.now);

        queue[0].advance(4);
        assertFalse(queue[0].done(), "what the survey found is still to load");
        queue[0].advance(100);

        assertEquals(List.of("survey", "found-1", "found-2"), ran);
        assertTrue(queue[0].done());
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
        assertEquals(1f, queue.fraction());
    }

    @Test
    void aStepThatHoldsTheThreadTooLongIsReported() {
        StepClock clock = new StepClock();
        List<String> ran = new ArrayList<>();
        List<String> slow = new ArrayList<>();
        List<TextureLoadQueue.Step> steps = steps(clock, ran, 4, "quick");
        steps.addAll(steps(clock, ran, 300, "slow"));
        TextureLoadQueue queue = new TextureLoadQueue(steps, () -> clock.now)
                .onSlowStep(150, (step, nanos) -> slow.add(step.stage() + "=" + nanos));

        queue.advance(1000);

        assertEquals(List.of("slow=300"), slow);
    }

    @Test
    void anEmptyQueueIsAlreadyComplete() {
        TextureLoadQueue queue = new TextureLoadQueue(List.of(), () -> 0L);
        assertTrue(queue.done());
        assertEquals(1f, queue.fraction());
    }
}
