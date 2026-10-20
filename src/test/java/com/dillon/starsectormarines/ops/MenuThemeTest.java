package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The opening's music decisions, on the same theme the Marine operations dialog plays: which
 * calls each transition makes. The sound itself can only be heard in a live game.
 */
class MenuThemeTest {

    private final List<String> calls = new ArrayList<>();
    private final MenuTheme theme = new MenuTheme(new MenuTheme.Player() {
        @Override public void suspendDefaultMusic(boolean suspended) {
            calls.add(suspended ? "suspend" : "resume");
        }

        @Override public void play(String track, int fadeOutSecs, int fadeInSecs, boolean loop) {
            calls.add("play " + track + (loop ? " looping" : ""));
        }

        @Override public void stop(int fadeOutSecs) {
            calls.add("stop");
        }
    });

    private static final String START_SUSPEND = "suspend";
    private static final String START_PLAY = "play " + MenuTheme.TRACK + " looping";

    @Test
    void aPrologueStartsTheLoopingThemeOnceAndRestoresDefaultMusicOnClose() {
        Object prologue = new Object();
        theme.hold(prologue);
        theme.hold(prologue); // every position change re-attaches
        theme.release(prologue);
        assertEquals(List.of(START_SUSPEND, START_PLAY, "stop", "resume"), calls);
        assertFalse(theme.playing());
    }

    @Test
    void overlappingHoldersShareOneTrack() {
        Object yard = new Object();
        Object port = new Object();
        Object ops = new Object();
        theme.hold(yard);
        theme.hold(port);
        theme.release(yard);
        theme.hold(ops);
        theme.release(ops);
        assertEquals(List.of(START_SUSPEND, START_PLAY), calls, "No restart and no gap while anyone holds it");
        theme.release(port);
        assertEquals(List.of(START_SUSPEND, START_PLAY, "stop", "resume"), calls);
    }

    @Test
    void releasingSomethingThatNeverHeldItChangesNothing() {
        Object yard = new Object();
        theme.hold(yard);
        theme.release(new Object());
        assertTrue(theme.playing());
        assertEquals(List.of(START_SUSPEND, START_PLAY), calls);
    }

    @Test
    void theArrivalSilencesTheThemeWithoutRestoringDefaultMusicAndTheNextPageBringsItBack() {
        Object holder = new Object();
        theme.hold(holder);
        theme.silence();
        theme.silence();
        assertEquals(List.of(START_SUSPEND, START_PLAY, "stop"), calls, "The arrival owns suspension while it plays");
        Object yard = new Object();
        theme.hold(yard);
        assertEquals(List.of(START_SUSPEND, START_PLAY, "stop", START_SUSPEND, START_PLAY), calls);
    }

    @Test
    void silencingWhenNothingPlaysDoesNothing() {
        theme.silence();
        assertEquals(List.of(), calls);
    }

    @Test
    void aBattleDisplacesTheTrackAndTheNextHoldRestartsIt() {
        Object ops = new Object();
        theme.hold(ops);
        theme.displaced();
        theme.hold(ops);
        assertEquals(List.of(START_SUSPEND, START_PLAY, START_SUSPEND, START_PLAY), calls);
    }

    @Test
    void aLoadMidStoryForgetsEveryHolderAndNeverLeavesMusicSuspended() {
        Object prologue = new Object();
        theme.hold(prologue);
        theme.reset();
        assertEquals(List.of(START_SUSPEND, START_PLAY, "stop", "resume"), calls);
        theme.release(prologue);
        assertEquals(4, calls.size(), "A late dismissal after the load is a no-op");
        theme.hold(new Object());
        assertTrue(theme.playing(), "A fresh holder after the load starts again");
    }

    @Test
    void aLoadWithNothingPlayingStillLiftsSuspension() {
        theme.reset();
        assertEquals(List.of("resume"), calls);
    }
}
