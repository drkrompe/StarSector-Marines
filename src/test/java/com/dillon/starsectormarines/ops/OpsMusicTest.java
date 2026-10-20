package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsMusicTest {

    private final List<String> calls = new ArrayList<>();
    private final MenuTheme theme = new MenuTheme(new MenuTheme.Player() {
        @Override public void suspendDefaultMusic(boolean suspended) {
            calls.add(suspended ? "suspend" : "resume");
        }

        @Override public void play(String track, int fadeOutSecs, int fadeInSecs, boolean loop) {
            calls.add("play " + track);
        }

        @Override public void stop(int fadeOutSecs) {
            calls.add("stop");
        }
    });
    private final OpsMusic music = new OpsMusic(theme);

    private static final List<String> START = List.of("suspend", "play " + MenuTheme.TRACK);

    @Test
    void movingBetweenEveryOrdinaryScreenNeverTouchesTheTrack() {
        music.entered(ScreenId.COMPANY_HQ);
        for (ScreenId screen : ScreenId.values()) {
            if (!screen.ownsMusic()) music.entered(screen);
        }
        music.entered(ScreenId.COMPANY_HQ);
        assertEquals(START, calls);
    }

    @Test
    void afterTheBattleTheIntelTrackComesBackOnce() {
        music.entered(ScreenId.SQUAD_DEPLOYMENT);
        music.entered(ScreenId.BATTLE);
        music.entered(ScreenId.RESULTS);
        music.entered(ScreenId.LOOT);
        List<String> expected = new ArrayList<>(START);
        expected.addAll(START);
        assertEquals(expected, calls);
    }

    @Test
    void closingTheDialogStopsTheTrackAndGivesTheCampaignItsMusicBack() {
        music.entered(ScreenId.SHIP_VIEW);
        music.closed();
        music.closed();
        List<String> expected = new ArrayList<>(START);
        expected.addAll(List.of("stop", "resume"));
        assertEquals(expected, calls);
    }

    @Test
    void theYardPagesHandTheTrackOnWithoutARestart() {
        Object yard = new Object();
        theme.hold(yard);
        music.entered(ScreenId.COMPANY_HQ);
        theme.release(yard);
        music.closed();
        List<String> expected = new ArrayList<>(START);
        expected.addAll(List.of("stop", "resume"));
        assertEquals(expected, calls);
    }

    @Test
    void closingDuringTheBattleLeavesItsMusicToTheBattle() {
        music.entered(ScreenId.BATTLE);
        music.closed();
        assertEquals(List.of(), calls);
    }
}
