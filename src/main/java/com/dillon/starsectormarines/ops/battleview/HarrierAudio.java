package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFall;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLift;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierWingbeat;
import com.dillon.starsectormarines.battle.evacuation.flier.SwarmFliers;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.fs.starfarer.api.Global;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Harrier's sound (`rav-harrier.md`), read off its packs rather than posted by them: the flier's sim and art
 * stay silent, and everything here is presentation.
 * <ul>
 *   <li><b>Wingbeats</b> on each downstroke of every flier within earshot of the listener
 *       ({@link HarrierWingbeat#phase}).</li>
 *   <li>A <b>screech</b> as each member goes in: the moment its wings start to fold for the dive.</li>
 *   <li>The <b>bite</b> on each strike the pack lands, the <b>snatch</b> when a strike lifts a marine and the
 *       <b>landing</b> when the throw comes down.</li>
 *   <li><b>Pain</b> when a flier loses health and lives (flak), the <b>death cry</b> when it is killed, and a
 *       <b>thump</b> when its fall reaches the ground.</li>
 * </ul>
 * Every clip is an existing Rav take played a little higher and quicker through the pitch argument (the
 * Matriarch's rule in {@link SwarmAudio}, the other way): a lighter, smaller creature. The one take of its own,
 * the wingbeat, is cut from one.
 *
 * <p>A pack is heard as a pack by <b>layering</b> (owner, 2026-10-24): several voices of one sound play at once, up
 * to the sound's voice count, so ten fliers going in are a thicker cry than one rather than the same single clip.
 * Each voice is quieter the more of its sound are already sounding ({@link #layerGain}), so the layers sum to a
 * sound that grows with the pack, by about 3 dB at five voices, without drowning out the fire around it; one flier
 * alone plays at the sound's own level, which is set low for the same reason. Two voices of one sound never start
 * on the same tick: the second is held a tick or two, so a burst that kills three is a ragged triple cry rather than
 * one clip played three times over, and one flier does not repeat a sound inside its own gap. Timing and pitch
 * jitter run on simulation time and a seed, so the same battle sounds the same (a review mixes its soundtrack from
 * {@link Mix#hear}) and the rules are testable without a sound player.
 */
public final class HarrierAudio {

    static final String PREFIX = SwarmAudio.PREFIX;
    static final float PITCH_JITTER = 0.06f;
    /** Wingbeats further from the listener than this are not drawn. */
    static final float WINGBEAT_RANGE_CELLS = 18f;
    /** Ticks a voice that could not start beside another of its sound is held, at most. */
    static final int HOLD_TICKS = 2;
    /** Health lost below this is a graze and draws no pain sound. */
    static final float PAIN_THRESHOLD = 1f;

    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /**
     * One sound: the level one voice of it plays at alone, how many of its voices may sound at once and how long each
     * counts as sounding, the shortest gap before one flier repeats it, and the pitch its take plays at.
     *
     * <p>The borrowed takes differ by some 25 dB (a Spitter's death shriek is far hotter than a Runner's cry), so each
     * level is set from its takes' measured loudness: one voice of a cry, a bite or a death lands near -24 LUFS, a
     * snatch or a throw a little over, a fall's thump and a wingbeat under. The wingbeat is its own take, the
     * loudest 0.28 s of a Runner's chitin clatter levelled up ({@code harrier_wingbeat}): the whole clatter is too
     * quiet and too long for a stroke a third of a second.
     */
    enum Sound {
        WINGBEAT("harrier_wingbeat", 0.55f, 6, 0.3f, 0.3f, 1.15f),
        SCREECH("runner_chitter", 0.45f, 5, 1.2f, 2f, 1.2f),
        STRIKE("runner_attack", 0.27f, 5, 0.8f, 0.5f, 1.12f),
        LIFT("runner_impale", 0.35f, 2, 1.3f, 1f, 1.15f),
        THROW_LANDING("brute_charge_hit_body", 0.4f, 2, 1f, 0f, 1.2f),
        PAIN("runner_pain", 0.14f, 4, 0.6f, 0.4f, 1.2f),
        DEATH("spitter_death", 0.16f, 5, 1.3f, 0f, 0.9f),
        FALL_THUMP("brute_footfall", 0.3f, 4, 0.7f, 0f, 1.3f);

        final String id;
        final float volume;
        final int voices;
        final float voiceSeconds;
        /** Shortest gap between two plays by one flier; 0 for sounds no flier repeats. */
        final float flierGapSeconds;
        final float pitch;

        Sound(String clip, float volume, int voices, float voiceSeconds, float flierGapSeconds, float pitch) {
            this.id = PREFIX + clip;
            this.volume = volume;
            this.voices = voices;
            this.voiceSeconds = voiceSeconds;
            this.flierGapSeconds = flierGapSeconds;
            this.pitch = pitch;
        }
    }

    /** A sound to start, where, and at what pitch and level (jitter included). */
    public record Play(Sound sound, float x, float y, float pitch, float volume) {
        public String soundId() { return sound.id; }
    }

    /** Per battle: what was last seen of each flier, and which voices of each sound are sounding or held. */
    public static final class Mix {
        private int tick = -1;
        /** Start times, in simulation seconds, of each sound's voices still sounding, oldest first. */
        private final EnumMap<Sound, ArrayDeque<Float>> sounding = new EnumMap<>(Sound.class);
        private final EnumMap<Sound, Integer> startedTick = new EnumMap<>(Sound.class);
        private final Long2DoubleOpenHashMap lastByFlier = new Long2DoubleOpenHashMap();
        /** Voices that could not start beside another of their sound, waiting a tick or two. */
        private final List<Held> held = new ArrayList<>();
        // Readings by flier, as float bits; absent reads NaN.
        private final Long2IntOpenHashMap wing = readings();
        private final Long2IntOpenHashMap fold = readings();
        private final Long2IntOpenHashMap health = readings();
        private final Long2ObjectOpenHashMap<float[]> lifts = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<float[]> falls = new Long2ObjectOpenHashMap<>();
        private int events;
        private int played;

        private record Held(Sound sound, float x, float y, int atTick, int heldFor) { }

        /** A downstroke heard this tick, waiting to be sorted nearest first. */
        private record Beat(float distanceSquared, float x, float y, long flier) { }

        private static Long2IntOpenHashMap readings() {
            Long2IntOpenHashMap map = new Long2IntOpenHashMap();
            map.defaultReturnValue(Float.floatToRawIntBits(Float.NaN));
            return map;
        }

        /**
         * How {@code sound} by {@code flier} ({@code 0} for none) fares at {@code tick}: {@link Admit#START} with a
         * free voice, {@link Admit#HOLD} when another of its sound already started this tick, {@link Admit#DROP}
         * when all its voices are sounding or the flier made it inside its own gap. A start is recorded.
         */
        Admit admit(Sound sound, long flier, int tick) {
            float seconds = tick * BattleSimulation.TICK_DT;
            ArrayDeque<Float> voices = sounding.computeIfAbsent(sound, ignored -> new ArrayDeque<>());
            while (!voices.isEmpty() && seconds - voices.peekFirst() >= sound.voiceSeconds) voices.pollFirst();
            if (voices.size() >= sound.voices) return Admit.DROP;
            long key = (long) sound.ordinal() << 56 ^ flier;
            if (flier != 0L && sound.flierGapSeconds > 0f && lastByFlier.containsKey(key)
                    && seconds - lastByFlier.get(key) < sound.flierGapSeconds) return Admit.DROP;
            if (Integer.valueOf(tick).equals(startedTick.get(sound))) return Admit.HOLD;
            voices.addLast(seconds);
            startedTick.put(sound, tick);
            if (flier != 0L) lastByFlier.put(key, seconds);
            return Admit.START;
        }

        /** Voices of {@code sound} sounding as of its last start, that one included. */
        int sounding(Sound sound) {
            ArrayDeque<Float> voices = sounding.get(sound);
            return voices == null ? 0 : voices.size();
        }

        /** What the fliers make this tick, once per tick; empty on a tick already heard. */
        public List<Play> hear(BattleSimulation sim, float listenerX, float listenerY) {
            int now = sim.getSimTickIndex();
            List<Play> out = new ArrayList<>();
            if (now == tick) return out;
            if (now < tick) reset();
            tick = now;
            SwarmFliers fliers = sim.swarmFliers();
            List<Held> waiting = new ArrayList<>(held);
            held.clear();
            for (Held h : waiting) {
                if (h.atTick() <= now) emit(out, h.sound(), 0L, h.x(), h.y(), h.heldFor());
                else held.add(h);
            }
            List<Beat> beats = new ArrayList<>();
            for (HarrierPack pack : fliers.packs()) {
                for (HarrierFlight m : pack.members) {
                    float x = m.body.x, y = m.body.y;
                    float beat = HarrierWingbeat.phase(m, pack.phase());
                    float priorBeat = swap(wing, m.id, beat);
                    if (downstroke(priorBeat, beat)) {
                        float dx = x - listenerX, dy = y - listenerY;
                        float d2 = dx * dx + dy * dy;
                        if (d2 <= WINGBEAT_RANGE_CELLS * WINGBEAT_RANGE_CELLS) beats.add(new Beat(d2, x, y, m.id));
                    }
                    if (wentIn(swap(fold, m.id, m.fold), m.fold)) emit(out, Sound.SCREECH, m.id, x, y, 0);
                    float hp = sim.world().hp(m.id);
                    if (hurt(swap(health, m.id, hp), hp)) emit(out, Sound.PAIN, m.id, x, y, 0);
                }
            }
            // Nearest first, so when the voices run out it is the far beats that go unheard.
            beats.sort(Comparator.comparingDouble(Beat::distanceSquared));
            for (Beat b : beats) emit(out, Sound.WINGBEAT, b.flier(), b.x(), b.y(), 0);
            List<SwarmFliers.Event> log = fliers.events();
            for (; events < log.size(); events++) {
                SwarmFliers.Event e = log.get(events);
                if (e.kind() != SwarmFliers.Event.Kind.STRIKE) continue;
                HarrierFlight m = fliers.flight(e.harrier());
                float x = m != null ? m.body.x : sim.world().x(e.target());
                float y = m != null ? m.body.y : sim.world().y(e.target());
                emit(out, Sound.STRIKE, e.harrier(), x, y, 0);
            }
            lifts(sim, fliers, out);
            LongList deaths = sim.getDeathsThisFrame();
            for (int i = 0; i < deaths.size(); i++) {
                long body = deaths.getLong(i);
                if (sim.identity().type(body) != UnitType.SWARM_HARRIER) continue;
                emit(out, Sound.DEATH, body, sim.world().renderX(body), sim.world().renderY(body), 0);
            }
            falls(fliers, out);
            return out;
        }

        /** A snatch when a throw starts, and the landing when it ends with the marine still alive. */
        private void lifts(BattleSimulation sim, SwarmFliers fliers, List<Play> out) {
            for (HarrierLift l : fliers.lifts()) {
                if (!lifts.containsKey(l.body)) emit(out, Sound.LIFT, l.harrier, l.x(), l.y(), 0);
                lifts.put(l.body, new float[]{l.x(), l.y()});
            }
            var gone = lifts.long2ObjectEntrySet().iterator();
            while (gone.hasNext()) {
                var entry = gone.next();
                if (fliers.lift(entry.getLongKey()) != null) continue;
                if (sim.getRoster().isAliveById(entry.getLongKey())) {
                    emit(out, Sound.THROW_LANDING, 0L, entry.getValue()[0], entry.getValue()[1], 0);
                }
                gone.remove();
            }
        }

        /** A thump where each fall that was in the air last tick has come down. */
        private void falls(SwarmFliers fliers, List<Play> out) {
            var gone = falls.long2ObjectEntrySet().iterator();
            while (gone.hasNext()) {
                var entry = gone.next();
                if (fliers.falling(entry.getLongKey())) continue;
                emit(out, Sound.FALL_THUMP, 0L, entry.getValue()[0], entry.getValue()[1], 0);
                gone.remove();
            }
            for (HarrierFall f : fliers.falls()) falls.put(f.id, new float[]{f.x(), f.y()});
        }

        /** Starts, holds or drops one voice; {@code heldFor} is how many ticks it has already waited. */
        private void emit(List<Play> out, Sound sound, long flier, float x, float y, int heldFor) {
            switch (admit(sound, flier, tick)) {
                case START -> {
                    float pitch = sound.pitch * (1f + jitter(tick, played++));
                    out.add(new Play(sound, x, y, pitch, sound.volume * layerGain(sounding(sound))));
                }
                case HOLD -> {
                    int wait = 1 + (played++ & 1);
                    if (heldFor + wait <= HOLD_TICKS) held.add(new Held(sound, x, y, tick + wait, heldFor + wait));
                }
                case DROP -> { }
            }
        }

        private void reset() {
            sounding.clear();
            startedTick.clear();
            lastByFlier.clear();
            held.clear();
            wing.clear();
            fold.clear();
            health.clear();
            lifts.clear();
            falls.clear();
            events = 0;
        }

        private static float swap(Long2IntOpenHashMap readings, long id, float value) {
            return Float.intBitsToFloat(readings.put(id, Float.floatToRawIntBits(value)));
        }
    }

    /** What {@link Mix#admit} makes of a voice. */
    enum Admit { START, HOLD, DROP }

    private HarrierAudio() { }

    /**
     * The level of the {@code n}th voice of a sound sounding at once, against one alone: {@code n^-1/4}, so {@code n}
     * layered voices carry about {@code sqrt(n)} times one's power. A pack grows louder with its size, but slowly.
     */
    static float layerGain(int n) {
        return n <= 1 ? 1f : (float) Math.pow(n, -0.25);
    }

    /** Called every rendered frame with the listener's cell; plays each tick's sounds once, on seen ground. */
    public static void play(BattleSimulation sim, float listenerX, float listenerY) {
        if (sim.swarmFliers().packs().isEmpty() && sim.swarmFliers().falls().isEmpty()
                && sim.swarmFliers().lifts().isEmpty() && !MIXES.containsKey(sim)) return;
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        for (Play p : mix.hear(sim, listenerX, listenerY)) {
            if (!sim.getFogOfWar().isCellRevealed((int) Math.floor(p.x()), (int) Math.floor(p.y()))) continue;
            Global.getSoundPlayer().playSound(p.soundId(), p.pitch(), p.volume(),
                    new Vector2f(p.x() * BattleShotAudio.WORLD_UNITS_PER_CELL, p.y() * BattleShotAudio.WORLD_UNITS_PER_CELL),
                    new Vector2f(0f, 0f));
        }
    }

    /** Whether a wingbeat drove down since the last reading: the stroke crossed from the upstroke into the downstroke. */
    static boolean downstroke(float prior, float phase) {
        return prior >= 0f && prior < 0.5f && phase >= 0.5f;
    }

    /** Whether a flier just went into its dive: its wings, spread last reading, have started to fold. */
    static boolean wentIn(float priorFold, float fold) {
        return priorFold <= 0f && fold > 0f;
    }

    /** Whether a flier was hit and lived: more than a graze lost since the last reading, and health left. */
    static boolean hurt(float priorHp, float hp) {
        return hp > 0f && priorHp - hp >= PAIN_THRESHOLD;
    }

    /** Seeded pitch jitter for the {@code n}th sound heard, at {@code tick}: within {@link #PITCH_JITTER}. */
    static float jitter(int tick, int n) {
        long h = ((long) tick * 31L + n) * 0x9E3779B97F4A7C15L;
        float share = (float) ((h >>> 40) % 2001) / 1000f - 1f;
        return share * PITCH_JITTER;
    }
}
