package com.dillon.starsectormarines.battle.audio;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NarrationSequenceTest {
    @Test void currentIdentityFollowsTurnsCompletionAndReplay() {
        var sequence = new NarrationSequence(new NarrationPlayer(line -> null));
        var mara = new WaylineNarration.Line("mara", "chapter", "briefing", "One", 1, 1, 10, List.of());
        var castle = new WaylineNarration.Line("castle", "chapter", "briefing.castle", "Two", 1, 1, 10,
                List.of(), "CASTLE / COMPANY OPERATIONS", "graphics/portraits/castle.png");
        sequence.start(List.of(mara, castle));
        sequence.advance(0);
        assertEquals(mara, sequence.current());
        sequence.advance(2);
        assertEquals(castle, sequence.current());
        sequence.advance(2);
        assertNull(sequence.current());
        assertFalse(sequence.busy());
        sequence.start(List.of(mara, castle));
        sequence.advance(0);
        assertEquals(mara, sequence.current());
        sequence.stop();
        assertNull(sequence.current());
        assertFalse(sequence.busy());
    }
    @Test void mutedConversationFinishesTurnsAndStopDiscardsPendingSpeech() {
        var heard = new ArrayList<String>();
        var sequence = new NarrationSequence(new NarrationPlayer(line -> { heard.add(line.id()); return null; }));
        var first = new WaylineNarration.Line("first", "chapter", "one", "One", 2, 1, 10, List.of());
        var second = new WaylineNarration.Line("second", "chapter", "two", "Two", 2, 1, 10, List.of());
        sequence.start(List.of(first, second));
        sequence.advance(0);
        sequence.advance(1);
        assertEquals(List.of("first"), heard);
        sequence.advance(2);
        assertEquals(second, sequence.current());
        sequence.stop();
        sequence.advance(100);
        assertFalse(sequence.busy());
        assertNull(sequence.current());
        sequence.start(List.of(first, second));
        sequence.advance(0);
        sequence.stop();
        sequence.advance(100);
        assertEquals(List.of("first", "second", "first"), heard);
    }
}
