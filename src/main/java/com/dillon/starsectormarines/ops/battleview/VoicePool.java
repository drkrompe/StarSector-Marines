package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;

/**
 * How a crowd of bodies is heard (owner, 2026-10-24): several voices of one sound play at once, up to the sound's
 * voice count, so many bodies doing one thing are a thicker sound than one rather than the same single clip. Each
 * voice is quieter the more of its sound are already sounding ({@link #layerGain}), so a crowd is louder than one
 * body, but slowly, and does not drown out the fire around it. Two voices of one sound never start on the same
 * tick: the second is held a tick or two, so a burst that kills three is a ragged triple cry rather than one clip
 * three times over. One body does not repeat a sound inside its own gap.
 *
 * <p>Runs on simulation ticks, not wall time, so the same battle sounds the same and the rules are testable without
 * a sound player. Presentation only; one pool per battle per family of sounds.
 *
 * @param <S> the family's sounds
 * @param <P> what a held voice needs to start later: where it is, whose voice
 */
final class VoicePool<S extends Enum<S>, P> {

    /** Ticks a voice that could not start beside another of its sound is held, at most. */
    static final int HOLD_TICKS = 2;

    /** How a sound layers: voices at once, how long each counts as sounding, and one body's own gap (0 for none). */
    record Layering(int voices, float voiceSeconds, float bodyGapSeconds) { }

    /** What became of an offered voice. */
    enum Admit { START, HOLD, DROP }

    /** A voice waiting for its tick. */
    record Held<S, P>(S sound, P payload, int atTick, int heldFor) { }

    private final Function<S, Layering> layering;
    /** Start times, in simulation seconds, of each sound's voices still sounding, oldest first. */
    private final EnumMap<S, ArrayDeque<Float>> sounding;
    private final EnumMap<S, Integer> startedTick;
    private final Long2DoubleOpenHashMap lastByBody = new Long2DoubleOpenHashMap();
    private final List<Held<S, P>> held = new ArrayList<>();
    private int holds;

    VoicePool(Class<S> type, Function<S, Layering> layering) {
        this.layering = layering;
        this.sounding = new EnumMap<>(type);
        this.startedTick = new EnumMap<>(type);
    }

    /**
     * Offers a voice of {@code sound} by {@code body} ({@code 0} for none) at {@code tick}. {@link Admit#START} with a
     * voice free (the start is recorded; play it at {@link #gain}); {@link Admit#HOLD} when another of its sound
     * already started this tick, and the pool keeps {@code payload} for a later tick unless it has waited
     * {@code heldFor} ticks already; {@link Admit#DROP} when all its voices are sounding or the body made it inside
     * its own gap.
     */
    Admit offer(S sound, long body, int tick, P payload, int heldFor) {
        Layering l = layering.apply(sound);
        float seconds = tick * BattleSimulation.TICK_DT;
        ArrayDeque<Float> voices = sounding.computeIfAbsent(sound, ignored -> new ArrayDeque<>());
        while (!voices.isEmpty() && seconds - voices.peekFirst() >= l.voiceSeconds()) voices.pollFirst();
        if (voices.size() >= l.voices()) return Admit.DROP;
        long key = (long) sound.ordinal() << 56 ^ body;
        if (body != 0L && l.bodyGapSeconds() > 0f && lastByBody.containsKey(key)
                && seconds - lastByBody.get(key) < l.bodyGapSeconds()) return Admit.DROP;
        if (Integer.valueOf(tick).equals(startedTick.get(sound))) {
            int wait = 1 + (holds++ & 1);
            if (heldFor + wait <= HOLD_TICKS) held.add(new Held<>(sound, payload, tick + wait, heldFor + wait));
            return Admit.HOLD;
        }
        voices.addLast(seconds);
        startedTick.put(sound, tick);
        if (body != 0L) lastByBody.put(key, seconds);
        return Admit.START;
    }

    /** The level of the voice just started, against one alone: {@link #layerGain} of the voices now sounding. */
    float gain(S sound) {
        ArrayDeque<Float> voices = sounding.get(sound);
        return layerGain(voices == null ? 0 : voices.size());
    }

    /** Voices of {@code sound} sounding as of its last start, that one included. */
    int sounding(S sound) {
        ArrayDeque<Float> voices = sounding.get(sound);
        return voices == null ? 0 : voices.size();
    }

    /** Takes the held voices whose tick has come, to be offered again. */
    List<Held<S, P>> due(int tick) {
        List<Held<S, P>> out = new ArrayList<>();
        held.removeIf(h -> {
            if (h.atTick() > tick) return false;
            out.add(h);
            return true;
        });
        return out;
    }

    /** Forgets everything: a battle replayed from an earlier tick. */
    void clear() {
        sounding.clear();
        startedTick.clear();
        lastByBody.clear();
        held.clear();
    }

    /**
     * The level of the {@code n}th voice of a sound sounding at once, against one alone: {@code n^-1/4}, so {@code n}
     * layered voices carry about {@code sqrt(n)} times one's power. A crowd grows louder with its size, but slowly.
     */
    static float layerGain(int n) {
        return n <= 1 ? 1f : (float) Math.pow(n, -0.25);
    }
}
