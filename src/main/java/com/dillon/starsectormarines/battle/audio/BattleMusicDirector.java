package com.dillon.starsectormarines.battle.audio;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Chooses the battle's {@link MusicState} from published battle facts and says what the host
 * should play. Presentation only: it reads facts after the simulation advances and never writes
 * to it.
 *
 * <p>A mission's declared state wins; a finished battle is in aftermath. Otherwise the heat rule
 * holds: quiet until first contact, combat on contact and for {@link #COMBAT_HOLD_SECONDS} of
 * battle time after the last one, then alert, then quiet after {@link #QUIET_AFTER_SECONDS}.
 * The heat clock runs on battle time, so a paused battle keeps its music.
 *
 * <p>Raising the music crossfades at once. Lowering fades slowly and never cuts a track in its
 * first {@link #MIN_TRACK_SECONDS}: the change waits until the track has played that long.
 *
 * <p>Each state has its own {@link BattleMusicPlaylist}, so each keeps the no-repeat shuffle.
 * A state without one falls back: aftermath to quiet, alert to quiet, and quiet to silence.
 */
public final class BattleMusicDirector {

    /** The combat state's tracks: sound ids in {@code sounds.json}. */
    public static final List<String> COMBAT_TRACKS = List.of(
            "marines_battle_music_02", "marines_battle_music_03", "marines_battle_music_04",
            "marines_battle_music_05", "marines_battle_music_06", "marines_battle_music_07",
            "marines_battle_music_08", "marines_battle_music_09", "marines_battle_music_10");
    /**
     * The alert state plays the combat files as {@code <id>_alert}, declared at reduced volume, until an
     * alert bed is sourced.
     */
    public static final String ALERT_SUFFIX = "_alert";

    /** Battle seconds without contact before combat music lowers to alert. UNTUNED. */
    public static final float COMBAT_HOLD_SECONDS = 30f;
    /** Battle seconds without contact before the music lowers to quiet. UNTUNED. */
    public static final float QUIET_AFTER_SECONDS = 90f;
    /** Real seconds a track plays before a lowering change may replace it. UNTUNED. */
    public static final float MIN_TRACK_SECONDS = 20f;
    /** Whole-second fades: the raise is quick, the lowering slow. */
    static final int RAISE_FADE_SECS = 1;
    static final int LOWER_FADE_OUT_SECS = 6;
    static final int LOWER_FADE_IN_SECS = 3;
    static final int OPEN_FADE_SECS = 2;

    /**
     * One frame's facts: the mission's declared state ({@code null} for the heat rule), whether
     * the company fired or was fired on this frame, and whether the battle is over.
     */
    public record Facts(MusicState declared, boolean contact, boolean finished) { }

    /**
     * A decision for the host. {@code from} is {@code null} when the battle opens and equals
     * {@code to} for the next track within a state. When {@code play} is set the host calls
     * {@code playCustomMusic(fadeOut, fadeIn, track)}; a {@code null} track is silence.
     */
    public record Cue(MusicState from, MusicState to, String reason, boolean play, String track,
                      int fadeOut, int fadeIn) {
        public boolean stateChange() { return from != to; }
    }

    private final Map<MusicState, BattleMusicPlaylist> playlists;
    private MusicState state = MusicState.QUIET;
    private BattleMusicPlaylist active;
    private boolean contacted;
    private float sinceContact;
    private float trackAge;
    private String reason;

    /** Playlists by state; a state left out uses its fallback. */
    public BattleMusicDirector(Map<MusicState, BattleMusicPlaylist> playlists) {
        this.playlists = playlists.isEmpty() ? new EnumMap<>(MusicState.class) : new EnumMap<>(playlists);
    }

    /**
     * The music the mod ships: combat and reduced-volume alert. Quiet and aftermath have no tracks yet, so
     * they are silence under the ambient loops.
     */
    public static BattleMusicDirector shipped(Random random) {
        return new BattleMusicDirector(Map.of(
                MusicState.COMBAT, new BattleMusicPlaylist(COMBAT_TRACKS, random),
                MusicState.ALERT, new BattleMusicPlaylist(COMBAT_TRACKS, ALERT_SUFFIX, random)));
    }

    public MusicState state() { return state; }

    /** Opens a battle: picks the opening state and its first track, or silence. */
    public Cue start(Facts facts) {
        stop();
        contacted = false;
        sinceContact = 0f;
        observe(0f, facts);
        state = choose(facts);
        active = resolve(state);
        trackAge = 0f;
        String track = active != null ? active.start() : null;
        return new Cue(null, state, reason, true, track, OPEN_FADE_SECS, OPEN_FADE_SECS);
    }

    /**
     * Advances one frame. {@code battleDt} is battle time (zero while paused), {@code realDt}
     * wall time for the track age. Returns {@code null} while nothing changes.
     */
    public Cue update(float battleDt, float realDt, Facts facts, String currentMusicId) {
        observe(battleDt, facts);
        trackAge += realDt;
        MusicState wanted = choose(facts);
        if (wanted != state) {
            boolean raise = wanted.raises(state);
            if (raise || active == null || trackAge >= MIN_TRACK_SECONDS) return enter(wanted, raise);
        }
        if (active == null) return null;
        String next = active.advance(currentMusicId);
        if (next == null) return null;
        trackAge = 0f;
        return new Cue(state, state, "next track", true, next, 0, 0);
    }

    public void stop() {
        if (active != null) active.stop();
        active = null;
    }

    private void observe(float battleDt, Facts facts) {
        sinceContact += battleDt;
        if (facts.contact()) {
            contacted = true;
            sinceContact = 0f;
        }
    }

    private MusicState choose(Facts facts) {
        if (facts.finished()) {
            reason = "the battle is over";
            return MusicState.AFTERMATH;
        }
        if (facts.declared() != null) {
            reason = "the mission declares " + facts.declared().name().toLowerCase(Locale.ROOT);
            return facts.declared();
        }
        if (facts.contact()) {
            reason = "contact: the company fired or was fired on";
            return MusicState.COMBAT;
        }
        if (!contacted) {
            reason = "no contact yet";
            return MusicState.QUIET;
        }
        if (sinceContact < COMBAT_HOLD_SECONDS) {
            reason = "contact within " + (int) COMBAT_HOLD_SECONDS + " s";
            return MusicState.COMBAT;
        }
        if (sinceContact < QUIET_AFTER_SECONDS) {
            reason = "no contact for " + (int) COMBAT_HOLD_SECONDS + " s";
            return MusicState.ALERT;
        }
        reason = "no contact for " + (int) QUIET_AFTER_SECONDS + " s";
        return MusicState.QUIET;
    }

    private Cue enter(MusicState wanted, boolean raise) {
        MusicState from = state;
        state = wanted;
        BattleMusicPlaylist next = resolve(wanted);
        // Two states sharing a playlist (or both silent) keep what is playing.
        if (next == active) return new Cue(from, wanted, reason, false, null, 0, 0);
        if (active != null) active.stop();
        active = next;
        trackAge = 0f;
        String track = next != null ? next.start() : null;
        return raise
                ? new Cue(from, wanted, reason, true, track, RAISE_FADE_SECS, RAISE_FADE_SECS)
                : new Cue(from, wanted, reason, true, track, LOWER_FADE_OUT_SECS, LOWER_FADE_IN_SECS);
    }

    private BattleMusicPlaylist resolve(MusicState s) {
        BattleMusicPlaylist own = playlists.get(s);
        if (own != null || s == MusicState.QUIET) return own;
        return s == MusicState.COMBAT ? null : resolve(MusicState.QUIET);
    }
}
