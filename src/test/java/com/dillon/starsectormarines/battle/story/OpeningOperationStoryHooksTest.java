package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.battle.story.OpeningOperationStoryHooks.Facts;
import com.dillon.starsectormarines.battle.story.OpeningOperationStoryHooks.Result;
import com.dillon.starsectormarines.battle.story.StoryBeatLog.Audience;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OpeningOperationStoryHooksTest {
    private static Facts facts(int tick, boolean landed, boolean reached, int original, int living,
                               int waves, boolean reserve, Result result) {
        return new Facts(tick, landed, landed, reached, reached, reached,
                original, living, waves, reserve, reserve, result);
    }
    private static List<String> ids(StoryBeatLog log) {
        return log.events().stream().map(StoryBeatLog.Event::id).toList();
    }
    @Test void counterattackFiresOnceAndSkippedPresentationFramesLoseNothing() {
        var log = new StoryBeatLog();
        var hooks = new OpeningOperationStoryHooks(OpeningOperationKind.COUNTERATTACK, log);
        hooks.observe(facts(1,false,false,0,0,0,false,Result.RUNNING));
        var opening = log.events();
        for (int tick=2; tick<20; tick++) hooks.observe(facts(tick,true,true,0,0,0,true,Result.RUNNING));
        assertEquals(1, opening.size(), "An old snapshot cannot change beneath a reader");
        assertEquals(8, log.events().size());
        var visible = log.playerEventsAfter(1);
        assertEquals(5, visible.size());
        assertEquals(visible, log.playerEventsAfter(1), "One reader must not drain another reader's cues");
        assertTrue(visible.stream().allMatch(e -> e.audience() == Audience.PLAYER));
        int cursor=visible.get(visible.size()-1).sequence();
        assertTrue(log.playerEventsAfter(cursor).isEmpty(), "Private events are not player announcements");
        hooks.finish(20,Result.VICTORY);
        var victory=log.playerEventsAfter(cursor);
        assertEquals(1,victory.size());
        assertEquals("opening.counterattack.victory",victory.get(0).id());
        assertEquals(9,victory.get(0).sequence(), "Advance by sequence, never by filtered count");
        assertThrows(UnsupportedOperationException.class,()->log.events().clear());
    }
    @Test void reliefLineLossDoesNotInventAWeakenedSurvivorOrEmployerLanding() {
        var log=new StoryBeatLog();
        var hooks=new OpeningOperationStoryHooks(OpeningOperationKind.RELIEF,log);
        hooks.observe(facts(1,false,false,8,8,0,false,Result.RUNNING));
        hooks.observe(facts(2,false,false,8,0,2,false,Result.RUNNING));
        assertEquals(List.of("opening.relief.started","opening.relief.militia_line_lost",
                "opening.relief.freight_wave_arrived","opening.relief.service_wave_arrived"),ids(log));
        assertEquals(2,log.playerEventsAfter(0).size());
        hooks.observe(facts(3,true,true,8,0,2,false,Result.RUNNING));
        assertTrue(ids(log).contains("opening.relief.checkpoint_reached"));
    }
    @Test void reliefWeakenedAndLostAreSeparateFactsAndZeroOriginalLineIsNotALoss() {
        var log=new StoryBeatLog();
        var hooks=new OpeningOperationStoryHooks(OpeningOperationKind.RELIEF,log);
        hooks.observe(facts(1,false,false,0,0,0,false,Result.RUNNING));
        assertEquals(1,log.events().size());
        hooks.observe(facts(2,false,false,8,4,0,false,Result.RUNNING));
        hooks.observe(facts(3,false,false,8,0,0,false,Result.RUNNING));
        assertTrue(ids(log).containsAll(List.of("opening.relief.militia_line_weakened","opening.relief.militia_line_lost")));
    }
    @Test void terminalBeatClosesAttemptAndRetryGetsANewHistory() {
        var first=new StoryBeatLog();
        var hooks=new OpeningOperationStoryHooks(OpeningOperationKind.COUNTERATTACK,first);
        hooks.finish(0,Result.RETREAT);
        hooks.observe(facts(1,true,true,0,0,0,true,Result.VICTORY));
        hooks.finish(2,Result.DEFEAT);
        assertEquals(List.of("opening.counterattack.started","opening.counterattack.retreat"),ids(first));
        var retry=new StoryBeatLog();
        new OpeningOperationStoryHooks(OpeningOperationKind.COUNTERATTACK,retry)
                .observe(facts(1,true,false,0,0,0,false,Result.DEFEAT));
        assertTrue(ids(retry).contains("opening.counterattack.player_landed"));
        assertEquals("opening.counterattack.defeat",ids(retry).get(3));
    }
    @Test void proximityDoesNotInventEarlierStagesOrAVictory() {
        var log=new StoryBeatLog();
        var hooks=new OpeningOperationStoryHooks(OpeningOperationKind.COUNTERATTACK,log);
        hooks.observe(new Facts(1,true,false,false,false,true,0,0,0,false,false,Result.RUNNING));
        assertEquals(List.of("opening.counterattack.started","opening.counterattack.player_landed",
                "opening.counterattack.warehouse_reached"),ids(log));
    }
    @Test void journalRejectsInvalidKeysAndBackdatedFacts() {
        var log=new StoryBeatLog();
        assertThrows(IllegalArgumentException.class,()->log.emitOnce(0,"",Audience.PLAYER));
        log.emitOnce(5,"chapter.started",Audience.PLAYER);
        assertThrows(IllegalArgumentException.class,()->log.emitOnce(4,"chapter.finished",Audience.PLAYER));
    }
}
