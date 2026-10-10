package com.dillon.starsectormarines.battle.story;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState.*;
import static com.dillon.starsectormarines.battle.story.JunctionStoryHooks.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class JunctionStoryHooksTest {
    @Test void captureIsConfirmedAndDoesNotInventAHoldOrVictory() {
        var log = new StoryBeatLog(); var hooks = new JunctionStoryHooks(log);
        hooks.observe(new JunctionStoryHooks.Facts(1, true, true, false, false,
                List.of(MARINE_HELD, CONTESTED, DEFENDER_HELD, DEFENDER_HELD), RUNNING));
        assertFalse(log.events().stream().anyMatch(e -> e.id().endsWith("secured")));
        hooks.observe(new JunctionStoryHooks.Facts(120, true, true, false, true,
                List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD), RUNNING));
        assertTrue(log.events().stream().anyMatch(e -> e.id().endsWith("weigh_station_secured")));
        assertFalse(log.playerEventsAfter(0).stream().anyMatch(e -> e.id().endsWith("reserve_committed")));
        assertFalse(log.events().stream().anyMatch(e -> e.id().endsWith("militia_hold_established") || e.id().endsWith("victory")));
    }
    @Test void reversalIsRecordedOnceAndCanOnlyFollowFriendlyOwnership() {
        var log = new StoryBeatLog(); var hooks = new JunctionStoryHooks(log);
        hooks.observe(new JunctionStoryHooks.Facts(1, true, true, false, false,
                List.of(MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD, DEFENDER_HELD), RUNNING));
        assertFalse(log.events().stream().anyMatch(e -> e.id().endsWith("retaken")));
        hooks.observe(new JunctionStoryHooks.Facts(2, true, true, true, true,
                List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD), RUNNING));
        var reversal = new JunctionStoryHooks.Facts(3, true, true, false, true,
                List.of(MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD, DEFENDER_HELD), RUNNING);
        hooks.observe(reversal); hooks.observe(reversal);
        assertEquals(1, log.events().stream().filter(e -> e.id().endsWith("weigh_station_retaken")).count());
        assertEquals(1, log.events().stream().filter(e -> e.id().endsWith("militia_hold_established")).count());
    }
    @Test void retreatBeforeLandingClosesTheJournal() {
        var log = new StoryBeatLog(); var hooks = new JunctionStoryHooks(log);
        hooks.finish(0, RETREAT);
        hooks.observe(new JunctionStoryHooks.Facts(1, true, true, true, true,
                List.of(MARINE_HELD, MARINE_HELD, MARINE_HELD, MARINE_HELD), VICTORY));
        assertEquals(List.of("supply_route.junction.started", "supply_route.junction.retreat"),
                log.events().stream().map(StoryBeatLog.Event::id).toList());
    }
}
