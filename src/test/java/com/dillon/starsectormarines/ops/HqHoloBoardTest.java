package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.HqActionItem.Action;
import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ops.HqActionItem.Verb;
import com.dillon.starsectormarines.ui.retained.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HqHoloBoardTest {
    private static final String DOC = """
            {"size": [2048, 413], "plate": "p.png",
             "figures": [{"id": "sabine", "sprite": "s.png", "rect": [1241.8, 138.6, 141, 245]}],
             "stations": [],
             "overlays": {"plot": [[1, 2], [3, 4], [5, 6], [7, 8]], "screens": []},
             "camera": {"focal": 990.055, "cx": 1024.0, "cy": -9.429, "height": 2.7},
             "table": [-1.9, 1.9, 5.8, 8.2]}
            """;

    private static HqActionItem item(String id, Kind kind, float[] sector) {
        return new HqActionItem(id, kind, "Item " + id, "", HqActionItem.NO_DEADLINE,
                Action.of(Verb.OPEN, () -> { }), null, sector);
    }

    @Test
    void sectorPositionLandsInsideTheDisc() {
        float[] core = HqHoloBoard.sectorPoint(new float[]{0f, 0f});
        assertEquals(0f, core[0], 1e-6f);
        assertEquals(0f, core[1], 1e-6f);
        float[] half = HqHoloBoard.sectorPoint(new float[]{HqHoloBoard.SECTOR_SPAN * 0.5f, 0f});
        assertEquals(HqHoloBoard.RADIUS * 0.5f, half[0], 1e-4f);
        // Far outside the span is clamped inside the rim, keeping its bearing.
        float[] far = HqHoloBoard.sectorPoint(new float[]{0f, -90000f});
        assertEquals(HqHoloBoard.RADIUS * HqHoloBoard.SECTOR_LIMIT, (float) Math.hypot(far[0], far[1]), 1e-4f);
        assertTrue(far[1] < 0f);
    }

    @Test
    void itemsWithoutAPositionSitOnTheRimInBoardOrder() {
        HqActionBoard board = new HqActionBoard(List.of(
                item("a", Kind.LETTER, null), item("b", Kind.STORY, new float[]{1000f, 1000f}),
                item("c", Kind.SIDE_JOB, null)), 0);
        Map<String, float[]> disc = HqHoloBoard.discPlacement(board);
        assertEquals(List.of("a", "b", "c"), List.copyOf(disc.keySet()));
        for (String id : List.of("a", "c")) {
            float[] p = disc.get(id);
            assertEquals(HqHoloBoard.RADIUS, (float) Math.hypot(p[0], p[1]), 1e-4f);
        }
        assertTrue(Math.hypot(disc.get("b")[0], disc.get("b")[1]) < HqHoloBoard.RADIUS);
        // Rim items share the near arc, left to right in board order.
        assertTrue(disc.get("a")[1] < 0f && disc.get("c")[1] < 0f);
        assertTrue(disc.get("a")[0] < disc.get("c")[0]);
    }

    @Test
    void discFloatsAboveTheTableCentre() {
        CommandDeckLayout layout = CommandDeckLayout.parse(DOC);
        float[] centre = HqHoloBoard.discPoint(layout, 0f, 0f);
        assertEquals(0f, centre[0], 1e-6f);
        assertEquals(CommandDeckLayout.TABLE_TOP + HqHoloBoard.LIFT, centre[1], 1e-5f);
        assertEquals(7.0f, centre[2], 1e-5f);
        float[] near = HqHoloBoard.discPoint(layout, 0f, -HqHoloBoard.RADIUS);
        assertTrue(near[1] > CommandDeckLayout.TABLE_TOP, "the near rim stays above the table top");
    }

    @Test
    void everyItemHasAMarkerAndLabelRect() {
        CommandDeckLayout layout = CommandDeckLayout.parse(DOC);
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(1872f, 372f, layout.width(), layout.height());
        HqActionBoard board = new HqActionBoard(List.of(
                item("a", Kind.LETTER, null), item("b", Kind.STORY, new float[]{-3000f, 2000f}),
                item("c", Kind.SIDE_JOB, null), item("d", Kind.OBLIGATION, new float[]{8000f, -6000f})), 0);
        Map<String, HqHoloBoard.Hit> hits = new HqHoloBoard(null).hits(layout, fit, board);
        assertEquals(List.of("a", "b", "c", "d"), List.copyOf(hits.keySet()));
        for (HqHoloBoard.Hit hit : hits.values()) {
            assertNotNull(hit);
            for (Rect r : List.of(hit.marker(), hit.label())) {
                assertTrue(r.width() > 0f && r.height() > 0f);
                assertTrue(r.x() >= 0f && r.x() + r.width() <= 1872f, "inside the banner: " + r);
                assertTrue(r.y() >= 0f && r.y() + r.height() <= 372f, "inside the banner: " + r);
            }
        }
        assertTrue(new HqHoloBoard(null).hits(layout, fit, null).isEmpty());
    }

    @Test
    void urgencyIsAnObligationFallingDueWithinTwoDays() {
        HqActionItem soon = new HqActionItem("x", Kind.OBLIGATION, "t", "", 2, Action.of(Verb.RESPOND, () -> { }), null, null);
        HqActionItem later = new HqActionItem("y", Kind.OBLIGATION, "t", "", 3, Action.of(Verb.RESPOND, () -> { }), null, null);
        assertTrue(HqHoloBoard.urgent(soon));
        assertTrue(!HqHoloBoard.urgent(later));
        assertTrue(HqHoloBoard.labelText(new HqActionItem("z", Kind.STORY,
                "A very long story lead title that does not fit", "", HqActionItem.NO_DEADLINE,
                Action.of(Verb.OPEN, () -> { }), null, null)).length() <= HqHoloBoard.TITLE_CHARS);
    }
}
