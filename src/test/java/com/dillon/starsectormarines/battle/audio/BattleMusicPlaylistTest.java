package com.dillon.starsectormarines.battle.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BattleMusicPlaylistTest {

    @Test
    void playsEveryTrackBeforeRepeatingAndDoesNotRepeatAtShuffleBoundary() {
        List<String> tracks = List.of("battle_01", "battle_02", "battle_03");
        BattleMusicPlaylist playlist = new BattleMusicPlaylist(tracks, new Random(4L));
        List<String> played = new ArrayList<>();

        String track = playlist.start();
        for (int i = 0; i < tracks.size() * 2; i++) {
            played.add(track);
            assertNull(playlist.advance("sounds/music/" + track + ".ogg"));
            track = playlist.advance("nothing");
        }

        assertEquals(tracks.stream().sorted().toList(),
                played.subList(0, tracks.size()).stream().sorted().toList());
        assertEquals(tracks.stream().sorted().toList(),
                played.subList(tracks.size(), tracks.size() * 2).stream().sorted().toList());
        for (int i = 1; i < played.size(); i++) {
            assertNotEquals(played.get(i - 1), played.get(i));
        }
    }

    @Test
    void waitsUntilRequestedTrackActuallyStartsBeforeAdvancing() {
        BattleMusicPlaylist playlist = new BattleMusicPlaylist(
                List.of("battle_01", "battle_02"), new Random(2L));
        String selected = playlist.start();

        assertNull(playlist.advance("nothing"));
        assertNull(playlist.advance("sounds/music/campaign.ogg"));
        assertNull(playlist.advance("SOUNDS\\MUSIC\\" + selected.toUpperCase() + ".OGG"));

        String next = playlist.advance("nothing");
        assertNotEquals(selected, next);
    }

    @Test
    void stopIgnoresLateMusicStateAndNextBattleStartsOnAnotherTrack() {
        BattleMusicPlaylist playlist = new BattleMusicPlaylist(
                List.of("battle_01", "battle_02", "battle_03"), new Random(9L));
        String first = playlist.start();
        assertNull(playlist.advance(first + ".ogg"));

        playlist.stop();

        assertNull(playlist.advance("nothing"));
        assertNotEquals(first, playlist.start());
    }
}
