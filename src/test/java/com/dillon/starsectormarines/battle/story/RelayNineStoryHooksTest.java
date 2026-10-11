package com.dillon.starsectormarines.battle.story;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState.*;
import static com.dillon.starsectormarines.battle.story.RelayNineStoryHooks.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class RelayNineStoryHooksTest {
    @Test void captureIsConfirmedAndDoesNotInventAHoldOrVictory() {
        var log = new StoryBeatLog(); var hooks = new RelayNineStoryHooks(log);
        hooks.observe(new RelayNineStoryHooks.Facts(1, true, true, List.of(), false,
                List.of(MARINE_HELD, CONTESTED, DEFENDER_HELD, DEFENDER_HELD), RUNNING));
        assertFalse(log.events().stream().anyMatch(e -> e.id().endsWith("secured")));
        hooks.observe(new RelayNineStoryHooks.Facts(120, true, true, List.of(), true,
                List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD), RUNNING));
        assertTrue(log.events().stream().anyMatch(e -> e.id().endsWith("power_yard_secured")));
        assertFalse(log.playerEventsAfter(0).stream().anyMatch(e -> e.id().endsWith("road_reserve_arrived")));
        assertFalse(log.events().stream().anyMatch(e -> e.id().endsWith("power_yard_militia_holding") || e.id().endsWith("victory")));
    }
    @Test void reversalIsRecordedOnceAndCanOnlyFollowFriendlyOwnership() {
        var log = new StoryBeatLog(); var hooks = new RelayNineStoryHooks(log);
        hooks.observe(new RelayNineStoryHooks.Facts(1, true, true, List.of(), false,
                List.of(MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD, DEFENDER_HELD), RUNNING));
        assertFalse(log.events().stream().anyMatch(e -> e.id().endsWith("retaken")));
        hooks.observe(new RelayNineStoryHooks.Facts(2, true, true, List.of(1), true,
                List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD), RUNNING));
        var reversal = new RelayNineStoryHooks.Facts(3, true, true, List.of(), true,
                List.of(MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD, DEFENDER_HELD), RUNNING);
        hooks.observe(reversal); hooks.observe(reversal);
        assertEquals(1, log.events().stream().filter(e -> e.id().endsWith("power_yard_retaken")).count());
        assertEquals(1, log.events().stream().filter(e -> e.id().endsWith("power_yard_militia_holding")).count());
        hooks.observe(new RelayNineStoryHooks.Facts(4, true, true, List.of(1, 2), true,
                List.of(MARINE_HELD, MARINE_HELD, MARINE_HELD, DEFENDER_HELD), RUNNING));
        assertEquals(1, log.events().stream().filter(e -> e.id().endsWith("power_yard_restored")).count());
        assertEquals(1, log.events().stream().filter(e -> e.id().endsWith("freight_gate_militia_holding")).count());
    }
    @Test void retreatBeforeLandingClosesTheJournal() {
        var log = new StoryBeatLog(); var hooks = new RelayNineStoryHooks(log);
        hooks.finish(0, RETREAT);
        hooks.observe(new RelayNineStoryHooks.Facts(1, true, true, List.of(1), true,
                List.of(MARINE_HELD, MARINE_HELD, MARINE_HELD, MARINE_HELD), VICTORY));
        assertEquals(List.of("supply_route.relay_nine.started", "supply_route.relay_nine.retreat"),
                log.events().stream().map(StoryBeatLog.Event::id).toList());
    }
}
