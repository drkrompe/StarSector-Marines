package com.dillon.starsectormarines.battle.story;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ColdAccountStoryHooksTest {
    @Test void patrolContactPrecedesActualLandingAndApproachAndNeverRepeats() {
        var log = new StoryBeatLog();
        var hooks = new ColdAccountStoryHooks(log);
        hooks.observe(1, false, true, false);
        assertEquals(ColdAccountStoryHooks.PATROL_EXCHANGE.stream()
                .map(key -> ColdAccountStoryHooks.PREFIX + key).toList(),
                log.events().stream().map(StoryBeatLog.Event::id).toList());
        hooks.observe(20, true, false, false);
        hooks.observe(40, true, true, false);
        hooks.observe(60, true, true, false);
        assertEquals(6, log.events().size());
        assertEquals(List.of(1, 1, 1, 1, 20, 40),
                log.events().stream().map(StoryBeatLog.Event::tick).toList());
        assertTrue(log.events().stream().allMatch(e -> e.audience() == StoryBeatLog.Audience.PLAYER));
    }

    @Test void securedArchiveClosesUnobservedApproachFacts() {
        var log = new StoryBeatLog();
        var hooks = new ColdAccountStoryHooks(log);
        hooks.observe(1, false, false, false);
        hooks.observe(2, true, true, true);
        hooks.observe(3, true, true, false);
        assertEquals(4, log.events().size());
    }
}
