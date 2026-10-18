package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.HqActionItem.Action;
import com.dillon.starsectormarines.ops.HqActionItem.Filter;
import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ops.HqActionItem.Verb;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HqActionBoardTest {

    private static HqActionItem item(String id, Kind kind, int days) {
        return new HqActionItem(id, kind, id, "", days, Action.of(Verb.OPEN, () -> { }), null, null);
    }

    private static List<String> ids(List<HqActionItem> items) {
        return items.stream().map(HqActionItem::id).toList();
    }

    @Test
    void urgentObligationsLeadThenStoryLettersSideJobsThenDistantClocks() {
        HqActionBoard board = HqActionBoard.of(List.of(
                item("term-12", Kind.OBLIGATION, 12),
                item("side", Kind.SIDE_JOB, 3),
                item("letter", Kind.LETTER, HqActionItem.NO_DEADLINE),
                item("story", Kind.STORY, HqActionItem.NO_DEADLINE),
                item("due-1", Kind.OBLIGATION, 1),
                item("due-0", Kind.OBLIGATION, 0)));

        assertEquals(List.of("due-0", "due-1", "story", "letter", "side", "term-12"), ids(board.items()));
    }

    @Test
    void storyLeadsKeepTheOrderTheyWereGatheredIn() {
        HqActionBoard board = HqActionBoard.of(List.of(
                item("corvus", Kind.STORY, HqActionItem.NO_DEADLINE),
                item("agreus", Kind.STORY, HqActionItem.NO_DEADLINE),
                item("shepherd", Kind.STORY, HqActionItem.NO_DEADLINE)));

        assertEquals(List.of("corvus", "agreus", "shepherd"), ids(board.items()),
                "several live arcs each show, none hides another");
    }

    @Test
    void filtersHoldTheirKindsAndCountTheirItems() {
        HqActionBoard board = HqActionBoard.of(List.of(
                item("due", Kind.OBLIGATION, 1),
                item("story", Kind.STORY, HqActionItem.NO_DEADLINE),
                item("letter", Kind.LETTER, HqActionItem.NO_DEADLINE),
                item("side", Kind.SIDE_JOB, 4)));

        assertEquals(List.of("story"), ids(board.shown(Filter.STORY)));
        assertEquals(List.of("due"), ids(board.shown(Filter.CONTRACTS)));
        assertEquals(List.of("letter", "side"), ids(board.shown(Filter.OTHER)));
        assertEquals(4, board.counts().get(Filter.ALL));
        assertEquals(2, board.counts().get(Filter.OTHER));
    }

    @Test
    void sideJobsAreCappedSoonestFirstAndTheRestAreCounted() {
        List<HqActionItem> gathered = new ArrayList<>();
        for (int days = 9; days >= 0; days--) gathered.add(item("side-" + days, Kind.SIDE_JOB, days));
        HqActionBoard board = HqActionBoard.of(gathered);

        assertEquals(List.of("side-0", "side-1", "side-2", "side-3"), ids(board.items()));
        assertEquals(6, board.hiddenSideJobs());
        assertEquals(10, board.counts().get(Filter.OTHER), "a hidden item still counts in its filter");
    }

    @Test
    void aSelectionSurvivesWhileItsItemIsShownAndFallsBackToTheFirst() {
        HqActionBoard board = HqActionBoard.of(List.of(
                item("due", Kind.OBLIGATION, 1),
                item("story", Kind.STORY, HqActionItem.NO_DEADLINE)));

        assertEquals("story", board.selected(Filter.ALL, "story").id());
        assertEquals("due", board.selected(Filter.ALL, "gone").id());
        assertEquals("story", board.selected(Filter.STORY, "due").id(), "a filter that hides the selection picks its own first");
        assertNull(board.selected(Filter.OTHER, "due"));
    }

    @Test
    void anActionWithNothingToDoSaysWhy() {
        Action tracked = Action.unavailable(Verb.TRACKING, "The term ends on its own.");
        assertFalse(tracked.available());
        assertEquals("The term ends on its own.", tracked.reason());
        assertTrue(Action.of(Verb.RESPOND, () -> { }).available());
    }
}
