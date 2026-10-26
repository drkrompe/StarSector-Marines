package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.audio.MusicState;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Facts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DustLedgerMusicTest {

    @Test
    void declaresQuietUntilTheAlarmAndCombatFromIt() {
        DustLedgerProgress progress = new DustLedgerProgress(60f, 10f);
        progress.advance(30f, Facts.quiet());
        assertEquals(MusicState.QUIET, progress.musicState());

        progress.advance(1f, Facts.quiet().withSighted());
        assertEquals(MusicState.COMBAT, progress.musicState());
    }
}
