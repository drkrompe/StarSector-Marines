package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * A look shot's soundtrack as data, for a review to mix offline: every sound the battle screen would start, written
 * per tick as a JSON line ({@code frame, id, x, y, pitch, volume, listenerX, listenerY}). The creatures' sounds come
 * from {@link SwarmAudio.Mix#hear}, {@link HarrierAudio.Mix#hear} and {@link SpireAudio.Mix#hear}, the same rules and
 * levels the game plays them at; the fire from {@link BattleShotAudio#cue}; impacts, a flak round's air-burst among
 * them, from {@link ShotImpactAudio#resolve}, at fire for a line tracer and on arrival for a round that travels, as
 * the screen plays them. The listener is the view's centre, as it is the screen's in a battle. Held beam loops are
 * not logged.
 */
public final class BattleSoundLog {

    /** The screen's fallback for an explosive impact with no authored sound ({@code BattleScreen.SFX_NEAR_EXPLOSION}). */
    private static final String EXPLOSIVE_FALLBACK = "marines_explosion";

    private final Path file;
    private final SwarmAudio.Mix swarm = new SwarmAudio.Mix();
    private final HarrierAudio.Mix harrier = new HarrierAudio.Mix();
    private final SpireAudio.Mix spire = new SpireAudio.Mix();
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

    private void tick(BattleSimulation sim, float[] at) {
        StringBuilder lines = new StringBuilder();
        for (SwarmAudio.Play p : swarm.hear(sim, at[0], at[1]))
            line(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at);
        for (HarrierAudio.Play p : harrier.hear(sim, at[0], at[1]))
            line(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at);
        for (SpireAudio.Play p : spire.hear(sim, at[0], at[1]))
            line(lines, p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at);
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

    private void line(StringBuilder out, String id, float x, float y, float pitch, float volume, float[] at) {
        out.append(String.format(Locale.ROOT,
                "{\"frame\": %d, \"id\": \"%s\", \"x\": %.2f, \"y\": %.2f, \"pitch\": %.4f, \"volume\": %.3f,"
                        + " \"listenerX\": %.2f, \"listenerY\": %.2f}%n",
                frame, id, x, y, pitch, volume, at[0], at[1]));
    }
}
