package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.BattleMusicDirector.Cue;
import com.dillon.starsectormarines.battle.audio.BattleMusicDirector.Facts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleMusicDirectorTest {

    private static final Facts LULL = new Facts(null, false, false);
    private static final Facts CONTACT = new Facts(null, true, false);

    private final BattleMusicDirector director = new BattleMusicDirector(Map.of(
            MusicState.COMBAT, new BattleMusicPlaylist(List.of("battle_01", "battle_02"), new Random(1L)),
            MusicState.ALERT, new BattleMusicPlaylist(List.of("battle_01", "battle_02"), "_alert", new Random(1L))));

    /** Steps one-second frames, returning the state changes. */
    private List<Cue> run(int seconds, Facts facts) {
        List<Cue> changes = new ArrayList<>();
        for (int i = 0; i < seconds; i++) {
            Cue cue = director.update(1f, 1f, facts, null);
            if (cue != null && cue.stateChange()) changes.add(cue);
        }
        return changes;
    }

    @Test
    void anUndeclaredBattleOpensQuietAndRaisesToCombatOnFirstContact() {
        Cue open = director.start(LULL);
        assertEquals(MusicState.QUIET, open.to());
        assertNull(open.track());

        Cue raise = director.update(1f, 1f, CONTACT, null);
        assertEquals(MusicState.COMBAT, raise.to());
        assertTrue(raise.track().startsWith("battle_0"));
        assertEquals(BattleMusicDirector.RAISE_FADE_SECS, raise.fadeIn());
    }

    @Test
    void steadySparseFireHoldsCombatAndALongLullSettlesOnceWithoutFlapping() {
        director.start(LULL);
        List<Cue> changes = new ArrayList<>(run(1, CONTACT));
        for (int i = 0; i < 5; i++) {
            changes.addAll(run(20, LULL));
            changes.addAll(run(1, CONTACT));
        }
        assertEquals(List.of(MusicState.COMBAT), changes.stream().map(Cue::to).toList());

        List<Cue> lull = run(200, LULL);
        assertEquals(List.of(MusicState.ALERT, MusicState.QUIET), lull.stream().map(Cue::to).toList());
        assertTrue(lull.get(0).track().endsWith("_alert"));
        assertEquals(BattleMusicDirector.LOWER_FADE_OUT_SECS, lull.get(0).fadeOut());
        assertNull(lull.get(1).track());
    }

    @Test
    void pausedBattleTimeKeepsTheMusicUp() {
        director.start(LULL);
        director.update(1f, 1f, CONTACT, null);
        for (int i = 0; i < 300; i++) assertNull(director.update(0f, 1f, LULL, null));
        assertEquals(MusicState.COMBAT, director.state());
    }

    @Test
    void loweringWaitsForTheTracksFirstSecondsButRaisingDoesNot() {
        director.start(new Facts(MusicState.COMBAT, false, false));
        assertNull(director.update(1f, 1f, new Facts(null, false, true), null));
        Cue lowered = director.update(1f, BattleMusicDirector.MIN_TRACK_SECONDS, new Facts(null, false, true), null);
        assertEquals(MusicState.AFTERMATH, lowered.to());

        director.start(new Facts(MusicState.ALERT, false, false));
        Cue raised = director.update(1f, 1f, new Facts(MusicState.COMBAT, false, false), null);
        assertEquals(MusicState.COMBAT, raised.to());
    }

    @Test
    void aDeclaredStateOverridesTheHeatRule() {
        Facts quietUnderFire = new Facts(MusicState.QUIET, true, false);
        assertEquals(MusicState.QUIET, director.start(quietUnderFire).to());
        assertTrue(run(10, quietUnderFire).isEmpty());
    }

    @Test
    void statesWithoutTracksShareSilenceAndAnnounceTheChangeWithoutPlaying() {
        BattleMusicDirector silent = new BattleMusicDirector(Map.of());
        silent.start(LULL);
        Cue cue = silent.update(1f, 1f, new Facts(null, false, true), null);
        assertEquals(MusicState.AFTERMATH, cue.to());
        assertFalse(cue.play());
    }

    @Test
    void aQuietBedPlaysBeforeContactFadesOnTheRaiseAndReturnsForTheAftermath() {
        BattleMusicDirector bedded = new BattleMusicDirector(Map.of(
                MusicState.QUIET, new BattleMusicPlaylist(List.of("bed"), new Random(1L)),
                MusicState.COMBAT, new BattleMusicPlaylist(List.of("battle_01"), new Random(1L))));
        Cue open = bedded.start(LULL);
        assertEquals("bed", open.track());
        assertTrue(open.loop());
        Cue raise = bedded.update(1f, 1f, CONTACT, null);
        assertEquals("battle_01", raise.track());
        assertEquals(BattleMusicDirector.RAISE_FADE_SECS, raise.fadeOut());
        assertTrue(raise.loop());
        Cue end = bedded.update(1f, BattleMusicDirector.MIN_TRACK_SECONDS, new Facts(null, false, true), null);
        assertEquals(MusicState.AFTERMATH, end.to());
        assertEquals("bed", end.track());
    }

    @Test
    void withinAStateTheNextTrackFollowsTheEndOfTheCurrentOne() {
        String first = director.start(new Facts(MusicState.COMBAT, false, false)).track();
        Facts combat = new Facts(MusicState.COMBAT, false, false);
        assertNull(director.update(1f, 1f, combat, first + ".ogg"));
        Cue next = director.update(1f, 1f, combat, null);
        assertNotNull(next);
        assertFalse(next.stateChange());
        assertFalse(first.equals(next.track()));
    }
}
