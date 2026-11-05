package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckPanelsTest {
    private static final DeckPanels.Signals QUIET = new DeckPanels.Signals(0f, 0, DeckPanels.Tone.NONE);

    @Test
    void cowlButtonsCountTheOpenActions() {
        DeckPanels.Signals two = new DeckPanels.Signals(0f, 2, DeckPanels.Tone.NONE);
        assertTrue(DeckPanels.light("actions", 1, 1, 6, 0f, two).level() > 0f);
        assertEquals(0f, DeckPanels.light("actions", 1, 2, 6, 0f, two).level());
    }

    @Test
    void financeLampsFollowTheRunwayTone() {
        DeckPanels.Signals good = new DeckPanels.Signals(0f, 0, DeckPanels.Tone.GOOD);
        DeckPanels.Signals danger = new DeckPanels.Signals(0f, 0, DeckPanels.Tone.DANGER);
        assertNotEquals(DeckPanels.light("finance", 1, 0, 6, 0f, good).rgb(), DeckPanels.light("finance", 1, 0, 6, 0f, danger).rgb());
        assertEquals(0f, DeckPanels.light("finance", 1, 0, 6, 0f, QUIET).level());
        assertEquals(DeckPanels.Tone.DANGER, DeckPanels.Tone.of("runway-value tone-danger"));
        assertEquals(DeckPanels.Tone.NONE, DeckPanels.Tone.of("runway-value tone-muted"));
    }

    @Test
    void anArrivingReportLightsTheCommsBoards() {
        DeckPanels.Signals report = new DeckPanels.Signals(1f, 0, DeckPanels.Tone.NONE);
        int lit = 0;
        for (int i = 0; i < 20; i++) if (DeckPanels.light("comms", 1, i, 20, 0.2f, report).level() > 0.9f) lit++;
        assertTrue(lit >= 6, "a wave crosses the board, lit " + lit);
    }

    @Test
    void lightsArePureFunctionsOfTime() {
        assertEquals(DeckPanels.light("idle", 7, 3, 9, 12.5f, QUIET), DeckPanels.light("idle", 7, 3, 9, 12.5f, QUIET));
    }

    @Test
    void parsesAPanelsButtons() {
        CommandDeckLayout layout = CommandDeckLayout.parse("""
                {"size": [100, 50], "plate": "p.png", "space": "s.png", "figures": [], "stations": [],
                 "overlays": {"plot": [], "screens": []},
                 "panels": [{"id": "a", "role": "comms", "buttons": [[1, 2, 3, 2, 3, 4, 1, 4]]}]}
                """);
        assertEquals("s.png", layout.space());
        CommandDeckLayout.Panel panel = layout.panels().get(0);
        assertEquals(1, panel.count());
        assertEquals(3f, panel.x(0, 2));
        assertEquals(4f, panel.y(0, 3));
    }
}
