package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.WaylineNarration.Line;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoryNarrationQueueTest {
    private static Line line(String event, int priority, String... supersedes) {
        return new Line(event, "chapter", event, "Subtitle", 4, priority, 10, List.of(supersedes));
    }

    private static StoryBeatLog.Event event(int sequence, String id) {
        return new StoryBeatLog.Event(sequence, sequence, id, StoryBeatLog.Audience.PLAYER);
    }

    @Test
    void publicProjectionDoesNotRepeatOrLeakPrivateEvents() {
        Line start = line("start", 10), hidden = line("hidden", 100);
        StoryNarrationQueue queue = new StoryNarrationQueue(new WaylineNarration(List.of(start, hidden)), "chapter");
        List<StoryBeatLog.Event> events = List.of(event(1, "start"),
                new StoryBeatLog.Event(3, 2, "hidden", StoryBeatLog.Audience.OBSERVER_ONLY));
        queue.advance(0, events, false);
        assertEquals(start, queue.poll());
        assertNull(queue.poll());
        queue.advance(0, events, false);
        assertNull(queue.poll());
        assertEquals(3, queue.cursor());
    }

    @Test
    void newFactsReplaceOnlyQueuedObsoleteOrders() {
        Line weak = line("weak", 20), lost = line("lost", 50, "weak"), landed = line("landed", 10);
        StoryNarrationQueue queue = new StoryNarrationQueue(new WaylineNarration(List.of(weak, lost, landed)), "chapter");
        queue.advance(0, List.of(event(1, "weak")), false);
        queue.advance(1, List.of(event(2, "landed"), event(3, "lost")), false);
        assertEquals(lost, queue.poll());
        assertEquals(landed, queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void expiryCompletionAndDetachDiscardQueuedInstructions() {
        Line start = line("start", 10);
        WaylineNarration catalog = new WaylineNarration(List.of(start));
        StoryNarrationQueue queue = new StoryNarrationQueue(catalog, "chapter");
        queue.advance(0, List.of(event(1, "start")), false);
        queue.advance(11, List.of(), false);
        assertNull(queue.poll());
        queue = new StoryNarrationQueue(catalog, "chapter");
        queue.advance(0, List.of(event(1, "start")), true);
        assertNull(queue.poll());
        queue = new StoryNarrationQueue(catalog, "chapter");
        queue.advance(0, List.of(event(1, "start")), false);
        queue.clearPending();
        queue.advance(0, List.of(event(1, "start")), false);
        assertNull(queue.poll());
    }

    @Test
    void recurringWorkDoesNotPlayHistoricChapterDialogue() {
        StoryNarrationQueue queue = new StoryNarrationQueue(new WaylineNarration(List.of(line("start", 10))), null);
        queue.advance(0, List.of(event(1, "start")), false);
        assertNull(queue.poll());
    }
}
