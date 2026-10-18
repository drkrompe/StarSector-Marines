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

    @Test
    void bodiesFloatAboveTheDiscAndProjectInsideTheBanner() {
        CommandDeckLayout layout = CommandDeckLayout.parse(DOC);
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(1872f, 372f, layout.width(), layout.height());
        HoloSpace space = new HoloSpace(layout, fit);
        HqActionBoard board = new HqActionBoard(List.of(
                item("a", Kind.LETTER, null), item("b", Kind.STORY, new float[]{-3000f, 2000f}),
                item("d", Kind.OBLIGATION, new float[]{8000f, -6000f})), 0);
        Map<String, float[]> disc = HqHoloBoard.discPlacement(board);
        for (HqActionItem it : board.items()) {
            float[] l = disc.get(it.id());
            float height = HqHoloBoard.bodyHeight(it);
            assertTrue(height > 0f, "a body floats above its foot");
            float[] foot = HqHoloBoard.discPoint(layout, l[0], l[1]);
            float[] body = HqHoloBoard.bodyPoint(layout, l[0], l[1], height);
            assertEquals(foot[0], body[0], 1e-6f);
            assertEquals(foot[2], body[2], 1e-6f);
            assertTrue(body[1] > foot[1] + 0.05f, "clear of the disc plane: " + it.id());
            // On screen the body sits above its foot (y down), and inside the banner.
            float[] s = space.screen(body);
            float[] f = space.screen(foot);
            assertTrue(s[1] < f[1]);
            assertTrue(s[0] > 0f && s[0] < 1872f && s[1] > 0f && s[1] < 372f, "on the banner: " + it.id());
        }
        // Heights are stable per item: the same id floats at the same height every call.
        assertEquals(HqHoloBoard.bodyHeight(board.items().get(1)), HqHoloBoard.bodyHeight(board.items().get(1)));
    }

    @Test
    void holoSpaceMatchesTheDiscAndShrinksWithDistance() {
        CommandDeckLayout layout = CommandDeckLayout.parse(DOC);
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(1872f, 372f, layout.width(), layout.height());
        HoloSpace space = new HoloSpace(layout, fit);
        float[] viaSpace = space.deck(0.4f, -0.3f, 0f);
        float[] viaBoard = HqHoloBoard.discPoint(layout, 0.4f, -0.3f);
        for (int k = 0; k < 3; k++) assertEquals(viaBoard[k], viaSpace[k], 1e-5f);
        // The dome's crown is above the disc's centre and in front of the camera.
        float[] crown = space.deck(0f, 0f, HoloVolume.DOME);
        assertTrue(crown[1] > HqHoloBoard.discPoint(layout, 0f, 0f)[1]);
        assertTrue(crown[2] > 0f);
        assertTrue(space.pixelsPerMetre(5f) > space.pixelsPerMetre(9f), "nearer is larger");
        assertTrue(space.nearness(5f) > space.nearness(9f), "nearer is brighter");
    }

    @Test
    void hitBoxesHoldTheBodyAndIgnoreTime() {
        CommandDeckLayout layout = CommandDeckLayout.parse(DOC);
        CommandDeckLayout.Fit fit = CommandDeckLayout.Fit.cover(1872f, 372f, layout.width(), layout.height());
        HqActionBoard board = new HqActionBoard(List.of(
                item("b", Kind.STORY, new float[]{-3000f, 2000f}), item("c", Kind.SIDE_JOB, null)), 0);
        HoloSpace space = new HoloSpace(layout, fit);
        Map<String, float[]> disc = HqHoloBoard.discPlacement(board);
        Map<String, HqHoloBoard.Hit> hits = new HqHoloBoard(null).hits(layout, fit, board);
        for (HqActionItem it : board.items()) {
            float[] l = disc.get(it.id());
            float[] body = HqHoloBoard.bodyPoint(layout, l[0], l[1], HqHoloBoard.bodyHeight(it));
            float[] s = space.screen(body);
            Rect m = hits.get(it.id()).marker();
            float reach = HoloBodies.radius(it.kind()) * HoloBodies.reach(it.kind()) * space.pixelsPerMetre(body[2]);
            assertTrue(m.x() <= s[0] - reach && m.x() + m.width() >= s[0] + reach, "covers the body: " + m);
            assertTrue(m.y() <= s[1] - reach && m.y() + m.height() >= s[1] + reach, "covers the body: " + m);
        }
        // The dome turns but the bodies hold their plot positions, so a later board gives the same boxes.
        assertEquals(hits.get("b").marker(), new HqHoloBoard(null).hits(layout, fit, board).get("b").marker());
    }

    @Test
    void lanesJoinEveryBodyOnce() {
        List<float[]> points = List.of(new float[]{0f, 0f, 0f}, new float[]{1f, 0f, 0f},
                new float[]{0f, 0f, 3f}, new float[]{1.2f, 0f, 0.1f});
        List<int[]> lanes = HoloVolume.network(points);
        assertEquals(points.size() - 1, lanes.size());
        boolean[] reached = new boolean[points.size()];
        reached[0] = true;
        for (int[] lane : lanes) {
            assertTrue(reached[lane[0]], "grown from the tree");
            reached[lane[1]] = true;
        }
        for (boolean r : reached) assertTrue(r);
        assertTrue(HoloVolume.network(List.of(new float[]{0f, 0f, 0f})).isEmpty());
    }
}
