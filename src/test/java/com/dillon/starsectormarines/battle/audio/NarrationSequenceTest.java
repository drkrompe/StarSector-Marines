package com.dillon.starsectormarines.battle.audio;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NarrationSequenceTest {
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
