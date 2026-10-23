package com.dillon.starsectormarines.battle.objective.optional;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectiveBoardTest {

    private static final Reward PAY = new Reward(500, Map.of("supplies", 10), Map.of());
    private static final Cost COST = new Cost("bloom_spread", 1);

    /** Goes final with {@code status} at {@code tick}. */
    private static OptionalObjective at(String key, int tick, Status status) {
        return new OptionalObjective(key, key.toUpperCase(), "", Set.of(), Set.of(), 1, PAY, COST,
                ctx -> (c, now) -> now >= tick ? status : Status.ACTIVE);
    }

    private static OptionalObjective never(String key, Status atEnd) {
        return new OptionalObjective(key, key, "", Set.of(), Set.of(), 1, PAY, COST, ctx -> new ObjectiveRuntime() {
            @Override public Status advance(ObjectiveContext c, int tick) { return Status.ACTIVE; }
            @Override public Status end(ObjectiveContext c) { return atEnd == null ? ObjectiveRuntime.super.end(c) : atEnd; }
        });
    }

    private static long count(StoryBeatLog log, String id) {
        return log.events().stream().filter(e -> e.id().equals(id)).count();
    }

    @Test
    void finalStatusesPublishTheirBeatOnce() {
        StoryBeatLog log = new StoryBeatLog();
        ObjectiveBoard board = new ObjectiveBoard(List.of(at("wreck", 5, Status.SUCCEEDED), at("depot", 7, Status.FAILED)),
                new ObjectiveContext(null, LaidLayout.EMPTY, 1L, "cordon.", log));
        board.start(0);
        assertTrue(log.has("cordon.objective_wreck_started"));
        assertTrue(log.has("cordon.objective_depot_started"));
        for (int tick = 1; tick <= 10; tick++) board.advance(tick);
        assertEquals(1, count(log, "cordon.objective_wreck_succeeded"));
        assertEquals(5, log.tickOf("cordon.objective_wreck_succeeded"));
        assertEquals(1, count(log, "cordon.objective_depot_failed"));
        assertEquals(7, log.tickOf("cordon.objective_depot_failed"));
        board.end(12);
        assertFalse(log.has("cordon.objective_wreck_lapsed"));
        assertEquals(Status.SUCCEEDED, board.results().get(0).status());
        assertEquals(Status.FAILED, board.results().get(1).status());
    }

    @Test
    void endFinishesWhatIsStillActiveLapsedByDefault() {
        StoryBeatLog log = new StoryBeatLog();
        ObjectiveBoard board = new ObjectiveBoard(List.of(never("quiet", null), never("held", Status.SUCCEEDED)),
                new ObjectiveContext(null, LaidLayout.EMPTY, 1L, "heart.", log));
        board.start(0);
        board.advance(3);
        assertEquals(Status.ACTIVE, board.results().get(0).status());
        board.end(9);
        board.end(10);
        assertEquals(1, count(log, "heart.objective_quiet_lapsed"));
        assertEquals(1, count(log, "heart.objective_held_succeeded"));
        assertEquals(Status.LAPSED, board.results().get(0).status());
        assertEquals(Status.SUCCEEDED, board.results().get(1).status());
    }

    @Test
    void resultsKeepRollOrderAndPayOnlyWhatTheStatusEarns() {
        ObjectiveBoard board = new ObjectiveBoard(List.of(at("c", 1, Status.FAILED), at("a", 1, Status.SUCCEEDED),
                never("b", null)), new ObjectiveContext(null, null, 1L, "x.", new StoryBeatLog()));
        board.start(0);
        board.advance(1);
        board.end(2);
        List<ObjectiveResult> results = board.results();
        assertEquals(List.of("c", "a", "b"), results.stream().map(ObjectiveResult::key).toList());
        assertEquals(Reward.NONE, results.get(0).earned());
        assertEquals(COST, results.get(0).incurred());
        assertEquals(PAY, results.get(1).earned());
        assertEquals(Cost.NONE, results.get(1).incurred());
        assertEquals(Reward.NONE, results.get(2).earned());
        assertEquals(Cost.NONE, results.get(2).incurred());
    }
}
