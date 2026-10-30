package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.WarshipSoundCue;
import com.dillon.starsectormarines.battle.audio.BattleMusicDirector;
import com.dillon.starsectormarines.battle.audio.BattleMusicFacts;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.tools.snapshot.FfmpegVideo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A look shot's soundtrack as data, for a review to mix offline: every sound the battle screen would start, written
 * per tick as a JSON line ({@code frame, id, x, y, pitch, volume, listenerX, listenerY}). The creatures' sounds come
 * from {@link SwarmAudio.Mix#hear}, {@link HarrierAudio.Mix#hear} and {@link SpireAudio.Mix#hear} and {@link BombardAudio.Mix#hear}, the bores' cues and claw-ups from
 * {@link BoreAudio.Mix#hear}, the marines' barks
 * from {@link MarineBarks.Mix#hear}, the same rules and
 * levels the game plays them at; the fire from {@link BattleShotAudio#cue}; impacts, a flak round's air-burst among
 * them, from {@link ShotImpactAudio#resolve}, at fire for a line tracer and on arrival for a round that travels, as
 * the screen plays them; the warships' cues (their guns, their rounds landing, the deck's boom and a low pass's roar)
 * as {@link WarshipAudio#play} plays them, at its pitch for each kind, a tick after the advance that raised them. The listener is the view's centre, as it is the screen's in a battle. A skitterer's work loop
 * and a Bombard haze's hiss are logged on every tick they are held, with the slot as {@code loop}; held beam loops are not logged.
 * The battle music is the screen's {@link BattleMusicDirector}, run on the same facts: each cue is a line with
 * {@code music} (the sound id, or empty for silence), its fades, the state and the reason, and each change of state
 * is printed. A look shot's director never hears a track end, so a track that runs out before the next cue leaves
 * silence in the mix; a log {@link #open}ed {@link #hearingTrackEnds} follows each track to its end and the director
 * moves on to the next, as the screen's does.
 * {@link LookReview#record} attaches one to every look shot under {@code -Dsnapshot.lookSound=true} and mixes it into
 * the MP4 ({@link BattleSoundMix}); a recording that drives its own battle opens one and calls {@link #tick} itself,
 * adding the screen's ambient bed with {@link #held} and {@link #start}.
 */
public final class BattleSoundLog {

    /** The screen's fallback for an explosive impact with no authored sound ({@code BattleScreen.SFX_NEAR_EXPLOSION}). */
    private static final String EXPLOSIVE_FALLBACK = "marines_explosion";
    /** The rate a track is decoded at to learn its length: coarse, since only the sample count is read. */
    private static final int SECONDS_PROBE_RATE = 1000;

    private final Path file;
    // Seeded, so the same battle logs the same pitches and mixes to the same soundtrack.
    private final SwarmAudio.Mix swarm = new SwarmAudio.Mix().seeded(0L);
    private final HarrierAudio.Mix harrier = new HarrierAudio.Mix();
    private final SpireAudio.Mix spire = new SpireAudio.Mix();
    private final BombardAudio.Mix bombards = new BombardAudio.Mix();
    private final BoreAudio.Mix bores = new BoreAudio.Mix();
    private final MarineBarks.Mix barks = new MarineBarks.Mix();
    private final BattleMusicDirector music = BattleMusicDirector.shipped(new Random(0L));
    private final StringBuilder pending = new StringBuilder();
    private Map<String, List<BattleSoundMix.Take>> takes;
    private final Map<String, Double> trackSeconds = new HashMap<>();
    private String playing;
    private double playingEnds = Double.MAX_VALUE;
    private int frame;

    private BattleSoundLog(Path file) {
        this.file = file;
    }

    /** Logs {@code placement}'s battle, heard from {@code ear}'s centre, to {@code file}, one line per sound. */
    public static void attach(LookReview.Placement placement, LookReview.View ear, Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        BattleSoundLog log = new BattleSoundLog(file);
        placement.beforeTick(sim -> log.tick(sim, ear.focus().apply(sim)));
    }

    /** An empty log at {@code file}, written a tick at a time by {@link #tick}. */
    public static BattleSoundLog open(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BattleSoundLog(file);
    }

    /** Follows each music track to its end (its length read from {@code modRoot}'s clips), so the next one plays. */
    public BattleSoundLog hearingTrackEnds(Path modRoot) throws IOException {
        takes = BattleSoundMix.takes(modRoot);
        return this;
    }

    /** Starts {@code id} at {@code (x, y)} on the coming tick, a sound the battle's own rules do not start. */
    public void start(String id, float x, float y, float pitch, float volume, float[] at) {
        line(pending, id, x, y, pitch, volume, at);
    }

    /** Holds loop {@code id} at {@code (x, y)} in {@code slot} through the coming tick. */
    public void held(String id, float x, float y, float pitch, float volume, int slot, float[] at) {
        loop(pending, id, x, y, pitch, volume, slot, at);
    }

    /** Logs one tick of {@code sim}, heard from {@code at} ({@code {x, y}} in map cells), before it advances. */
    public void tick(BattleSimulation sim, float[] at) {
        StringBuilder lines = new StringBuilder(pending);
        pending.setLength(0);
        BattleMusicDirector.Facts facts = BattleMusicFacts.of(sim);
        double now = frame * (double) BattleSimulation.TICK_DT;
        if (playing != null && now >= playingEnds) playing = null;
        music(lines, frame == 0 ? music.start(facts)
                : music.update(BattleSimulation.TICK_DT, BattleSimulation.TICK_DT, facts, playing));
        for (SwarmAudio.Play p : swarm.hear(sim, at[0], at[1])) {
            if (p.loop() >= 0) loop(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), p.loop(), at);
            else line(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at);
        }
        for (HarrierAudio.Play p : harrier.hear(sim, at[0], at[1]))
            line(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at);
        for (SpireAudio.Play p : spire.hear(sim, at[0], at[1]))
            line(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at);
        for (BombardAudio.Play p : bombards.hear(sim, at[0], at[1])) {
            if (p.loop() >= 0) loop(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), p.loop(), at);
            else line(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at);
        }
        for (BoreAudio.Play p : bores.hear(sim))
            line(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at);
        for (MarineBarks.Play p : barks.hear(sim))
            line(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at);
        for (WarshipSoundCue cue : sim.warships().cuesThisFrame()) {
            if (cue.kind() == WarshipSoundCue.Kind.BEAM_BURNING) continue;
            line(lines, cue.soundId(), cue.x(), cue.y(), WarshipAudio.basePitch(cue.kind()), cue.volume(), at);
        }
        for (ShotEvent shot : sim.getShotsThisFrame()) {
            BattleShotAudio.Cue cue = BattleShotAudio.cue(shot);
            if (cue.soundId() != null) line(lines, cue.soundId(), shot.fromX, shot.fromY, 1f, cue.volume(), at);
            if (!ShotFx.of(shot).travels()) impact(lines, shot, at);
        }
        for (ShotEvent shot : sim.getShotsExpiredThisFrame()) {
            if (ShotFx.of(shot).travels()) impact(lines, shot, at);
        }
        frame++;
        try {
            Files.writeString(file, lines, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void impact(StringBuilder out, ShotEvent shot, float[] at) {
        ShotImpactAudio.Cue cue = ShotImpactAudio.resolve(shot, EXPLOSIVE_FALLBACK);
        if (cue != null) line(out, cue.soundId(), shot.toX, shot.toY, 1f, cue.volume(), at);
    }

    private void music(StringBuilder out, BattleMusicDirector.Cue cue) {
        if (cue == null) return;
        if (cue.stateChange()) {
            System.out.printf(Locale.ROOT, "    [music] %.1f s: %s (%s)%n", frame * BattleSimulation.TICK_DT,
                    cue.from() == null ? "opens in " + cue.to() : cue.from() + " -> " + cue.to(), cue.reason());
        }
        if (!cue.play()) return;
        follow(cue);
        out.append(String.format(Locale.ROOT,
                "{\"frame\": %d, \"music\": \"%s\", \"fadeOut\": %d, \"fadeIn\": %d, \"loop\": %b,"
                        + " \"state\": \"%s\", \"reason\": \"%s\"}%n",
                frame, cue.track() != null ? cue.track() : "", cue.fadeOut(), cue.fadeIn(), cue.loop(), cue.to(),
                cue.reason()));
    }

    /** Notes the track {@code cue} starts and when it runs out, when this log follows tracks to their ends. */
    private void follow(BattleMusicDirector.Cue cue) {
        if (takes == null) return;
        List<BattleSoundMix.Take> options = cue.track() != null ? takes.get(cue.track()) : null;
        if (options == null || options.isEmpty()) {
            playing = null;
            playingEnds = Double.MAX_VALUE;
            return;
        }
        Path clip = options.get(0).file();
        playing = clip.getFileName().toString();
        playingEnds = cue.loop() ? Double.MAX_VALUE : frame * (double) BattleSimulation.TICK_DT
                + trackSeconds.computeIfAbsent(playing, ignored -> seconds(clip));
    }

    private static double seconds(Path clip) {
        try {
            return FfmpegVideo.decodeMono(clip, SECONDS_PROBE_RATE).length / (double) SECONDS_PROBE_RATE;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A placed loop held this tick: one line a tick while it sounds, its slot in {@code loop}. */
    private void loop(StringBuilder out, String id, float x, float y, float pitch, float volume, int slot, float[] at) {
        out.append(String.format(Locale.ROOT,
                "{\"frame\": %d, \"id\": \"%s\", \"x\": %.2f, \"y\": %.2f, \"pitch\": %.4f, \"volume\": %.3f,"
                        + " \"listenerX\": %.2f, \"listenerY\": %.2f, \"loop\": %d}%n",
                frame, id, x, y, pitch, volume, at[0], at[1], slot));
    }

    private void line(StringBuilder out, String id, float x, float y, float pitch, float volume, float[] at) {
        out.append(String.format(Locale.ROOT,
                "{\"frame\": %d, \"id\": \"%s\", \"x\": %.2f, \"y\": %.2f, \"pitch\": %.4f, \"volume\": %.3f,"
                        + " \"listenerX\": %.2f, \"listenerY\": %.2f}%n",
                frame, id, x, y, pitch, volume, at[0], at[1]));
    }
}
