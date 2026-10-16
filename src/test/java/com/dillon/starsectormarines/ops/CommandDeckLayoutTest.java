package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDeckLayoutTest {
    private static final String DOC = """
            {"size": [2000, 400], "plate": "graphics/ui/hq-command-deck/deck-plate.png",
             "figures": [{"id": "castle", "sprite": "graphics/ui/hq-command-deck/castle.png", "rect": [900, 100, 80, 200]}],
             "stations": [{"id": "castle", "label": "CASTLE_ADJUTANT", "figures": ["castle"], "rect": [0.45, 0.25, 0.04, 0.5]},
                          {"id": "comms", "label": "COMMS_INTEL", "figures": [], "rect": [0.0, 0.0, 0.1, 1.0]}],
             "overlays": {"plot": [[1, 2], [3, 4], [5, 6], [7, 8]], "screens": [[10, 20]]}}
            """;

    @Test
    void parsesFiguresStationsAndOverlays() {
        CommandDeckLayout layout = CommandDeckLayout.parse(DOC);
        assertEquals(2000f, layout.width());
        assertEquals("graphics/ui/hq-command-deck/castle.png", layout.figures().get(0).sprite());
        assertTrue(layout.station("castle").shows("castle"));
        assertEquals(List.of(), layout.station("comms").figures());
        assertEquals(8, layout.plot().length);
        assertEquals(20f, layout.screens()[1]);
    }

    @Test
    void malformedLayoutIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CommandDeckLayout.parse("{\"size\": []}"));
    }

    @Test
    void coverFillsAWiderBoxAndCropsTopAndBottom() {
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(1000f, 100f, 2000f, 400f);
        assertEquals(0.5f, fit.scale());
        assertEquals(0f, fit.offsetX());
        assertEquals(-50f, fit.offsetY());
    }

    @Test
    void coverFillsANarrowerBoxAndCropsTheSides() {
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(500f, 200f, 2000f, 400f);
        assertEquals(0.5f, fit.scale());
        assertEquals(-250f, fit.offsetX());
        assertEquals(1000f, fit.x(2500f));
    }

    @Test
    void stationRectFollowsTheFitAndIsClippedToTheBanner() {
        CommandDeckLayout layout = CommandDeckLayout.parse(DOC);
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(500f, 200f, 2000f, 400f);
        Rect castle = CompanyIntelTerminal.stationRect(layout.station("castle"), fit, layout, 500f, 200f);
        assertEquals(200f, castle.x(), 0.01f);
        assertEquals(50f, castle.y(), 0.01f);
        assertEquals(40f, castle.width(), 0.01f);
        assertEquals(100f, castle.height(), 0.01f);
        Rect comms = CompanyIntelTerminal.stationRect(layout.station("comms"), fit, layout, 500f, 200f);
        assertEquals(0f, comms.width(), 0.01f, "a station wholly cut off by the fit has no area");
    }
}
