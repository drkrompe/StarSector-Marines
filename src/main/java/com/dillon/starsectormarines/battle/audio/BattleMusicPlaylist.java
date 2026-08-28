package com.dillon.starsectormarines.battle.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Owns the non-repeating order and end-of-track state for battle music.
 *
 * <p>Starsector exposes the current music file rather than a completion callback. A newly
 * requested track therefore has to be observed playing before a different current-file value
 * can mean that it ended. This also prevents the outgoing campaign track from being mistaken
 * for the end of the battle track during the initial crossfade.
 */
public final class BattleMusicPlaylist {

    private final List<String> tracks;
    private final Random random;
    private final List<String> remaining = new ArrayList<>();
    private String lastTrack;
    private String currentTrack;
    private boolean active;
    private boolean currentTrackObserved;

    public BattleMusicPlaylist(List<String> tracks, Random random) {
        if (tracks == null || tracks.isEmpty()) {
            throw new IllegalArgumentException("Battle music playlist requires at least one track");
        }
        Set<String> uniqueTracks = new HashSet<>(tracks);
        if (uniqueTracks.size() != tracks.size() || uniqueTracks.contains(null)
                || uniqueTracks.contains("")) {
            throw new IllegalArgumentException("Battle music playlist tracks must be non-empty and unique");
        }
        this.tracks = List.copyOf(tracks);
        this.random = random;
    }

    /** Starts or resumes the shuffled playlist and returns the sound-set id to play. */
    public String start() {
        active = true;
        currentTrackObserved = false;
        currentTrack = takeNext();
        return currentTrack;
    }

    /**
     * Observes Starsector's current music file and returns the next sound-set id after the
     * requested track has played and ended. Returns {@code null} while no change is needed.
     */
    public String advance(String currentMusicId) {
        if (!active) return null;
        if (isCurrentTrack(currentMusicId)) {
            currentTrackObserved = true;
            return null;
        }
        if (!currentTrackObserved) return null;

        currentTrackObserved = false;
        currentTrack = takeNext();
        return currentTrack;
    }

    public void stop() {
        active = false;
        currentTrack = null;
        currentTrackObserved = false;
    }

    private String takeNext() {
        if (remaining.isEmpty()) refill();
        String next = remaining.remove(0);
        lastTrack = next;
        return next;
    }

    private void refill() {
        remaining.addAll(tracks);
        Collections.shuffle(remaining, random);
        if (remaining.size() > 1 && remaining.get(0).equals(lastTrack)) {
            Collections.swap(remaining, 0, 1);
        }
    }

    private boolean isCurrentTrack(String currentMusicId) {
        if (currentTrack == null || currentMusicId == null) return false;
        String normalized = currentMusicId.replace('\\', '/').toLowerCase(Locale.ROOT);
        String expectedId = currentTrack.toLowerCase(Locale.ROOT);
        return normalized.equals(expectedId) || normalized.endsWith('/' + expectedId + ".ogg")
                || normalized.equals(expectedId + ".ogg");
    }
}
