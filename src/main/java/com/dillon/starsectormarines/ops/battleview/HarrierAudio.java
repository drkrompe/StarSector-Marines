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
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
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
 *   <li>The <b>bite</b> on each strike the pack lands and the <b>snatch</b> when a strike lifts a marine; the
 *       thrown marine's landing is the thud every thrown body makes ({@link SwarmAudio}).</li>
 *   <li><b>Pain</b> when a flier loses health and lives (flak), the <b>death cry</b> when it is killed, and a
 *       <b>thud</b> when its fall reaches the ground, the same body thud a thrown marine makes, a little lighter.</li>
 * </ul>
 * Every clip is an existing Rav take played a little higher and quicker through the pitch argument (the
 * Matriarch's rule in {@link SwarmAudio}, the other way): a lighter, smaller creature. The one take of its own,
 * the wingbeat, is cut from one.
 *
 * <p>A pack is heard as a pack by layering ({@link VoicePool}): several voices of one sound at once, each quieter the
 * more are sounding, so ten fliers going in are a thicker cry than one; one flier alone plays at the sound's own
 * level, which is set low for the same reason. Timing and pitch jitter run on simulation time and a seed, so the
 * same battle sounds the same (a review mixes its soundtrack from {@link Mix#hear}).
 */
public final class HarrierAudio {

    static final String PREFIX = SwarmAudio.PREFIX;
    static final float PITCH_JITTER = 0.06f;
    /** Wingbeats further from the listener than this are not drawn. */
    static final float WINGBEAT_RANGE_CELLS = 18f;
    /** Health lost below this is a graze and draws no pain sound. */
    static final float PAIN_THRESHOLD = 1f;

    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /**
     * One sound: the level one voice of it plays at alone, how many of its voices may sound at once and how long each
     * counts as sounding, the shortest gap before one flier repeats it, and the pitch its take plays at.
     *
     * <p>The borrowed takes differ by some 25 dB (a Spitter's death shriek is far hotter than a Runner's cry), so each
     * level is set from its takes' measured loudness: one voice of a cry, a bite or a death lands near -24 LUFS, a
     * snatch a little over, a fall's thud and a wingbeat under. The wingbeat is its own take, the
     * loudest 0.28 s of a Runner's chitin clatter levelled up ({@code harrier_wingbeat}): the whole clatter is too
     * quiet and too long for a stroke a third of a second.
     */
    enum Sound {
        WINGBEAT("harrier_wingbeat", 0.55f, 6, 0.3f, 0.3f, 1.15f),
        SCREECH("runner_chitter", 0.45f, 5, 1.2f, 2f, 1.2f),
        STRIKE("runner_attack", 0.27f, 5, 0.8f, 0.5f, 1.12f),
        LIFT("runner_impale", 0.35f, 2, 1.3f, 1f, 1.15f),
        PAIN("runner_pain", 0.14f, 4, 0.6f, 0.4f, 1.2f),
        DEATH("spitter_death", 0.16f, 5, 1.3f, 0f, 0.9f),
        FALL_THUD("body_thud", 0.4f, 4, 0.8f, 0f, 1.15f);

        final String id;
        final float volume;
        final VoicePool.Layering layering;
        final float pitch;

        Sound(String clip, float volume, int voices, float voiceSeconds, float flierGapSeconds, float pitch) {
            this.id = PREFIX + clip;
            this.volume = volume;
            this.layering = new VoicePool.Layering(voices, voiceSeconds, flierGapSeconds);
            this.pitch = pitch;
        }
    }

    /** A sound to start, where, and at what pitch and level (jitter included). */
    public record Play(Sound sound, float x, float y, float pitch, float volume) {
        public String soundId() { return sound.id; }
    }

    /** Per battle: what was last seen of each flier, and its voices. */
    public static final class Mix {
        private int tick = -1;
        private final VoicePool<Sound, float[]> voices = new VoicePool<>(Sound.class, sound -> sound.layering);
        // Readings by flier, as float bits; absent reads NaN.
        private final Long2IntOpenHashMap wing = readings();
        private final Long2IntOpenHashMap fold = readings();
        private final Long2IntOpenHashMap health = readings();
        private final LongOpenHashSet lifts = new LongOpenHashSet();
        private final Long2ObjectOpenHashMap<float[]> falls = new Long2ObjectOpenHashMap<>();
        private int events;
        private int played;

        /** A downstroke heard this tick, waiting to be sorted nearest first. */
        private record Beat(float distanceSquared, float x, float y, long flier) { }

        private static Long2IntOpenHashMap readings() {
            Long2IntOpenHashMap map = new Long2IntOpenHashMap();
            map.defaultReturnValue(Float.floatToRawIntBits(Float.NaN));
            return map;
        }

        /** What the fliers make this tick, once per tick; empty on a tick already heard. */
        public List<Play> hear(BattleSimulation sim, float listenerX, float listenerY) {
            int now = sim.getSimTickIndex();
            List<Play> out = new ArrayList<>();
            if (now == tick) return out;
            if (now < tick) reset();
            tick = now;
            SwarmFliers fliers = sim.swarmFliers();
            for (VoicePool.Held<Sound, float[]> h : voices.due(now)) {
                emit(out, h.sound(), 0L, h.payload()[0], h.payload()[1], h.heldFor());
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
                    // A flier shot down this tick is a corpse until its pack lets go: no health to read, and the DEATH below voices it.
                    if (!sim.world().isAlive(m.id)) continue;
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
            lifts(fliers, out);
            LongList deaths = sim.getDeathsThisFrame();
            for (int i = 0; i < deaths.size(); i++) {
                long body = deaths.getLong(i);
                if (sim.identity().type(body) != UnitType.SWARM_HARRIER) continue;
                emit(out, Sound.DEATH, body, sim.world().renderX(body), sim.world().renderY(body), 0);
            }
            falls(fliers, out);
            return out;
        }

        /** A snatch when a throw starts; the landing is the toss's own thud. */
        private void lifts(SwarmFliers fliers, List<Play> out) {
            for (HarrierLift l : fliers.lifts()) {
                if (!lifts.contains(l.body)) emit(out, Sound.LIFT, l.harrier, l.x(), l.y(), 0);
            }
            lifts.clear();
            for (HarrierLift l : fliers.lifts()) lifts.add(l.body);
        }

        /** A thud where each fall that was in the air last tick has come down. */
        private void falls(SwarmFliers fliers, List<Play> out) {
            var gone = falls.long2ObjectEntrySet().iterator();
            while (gone.hasNext()) {
                var entry = gone.next();
                if (fliers.falling(entry.getLongKey())) continue;
                emit(out, Sound.FALL_THUD, 0L, entry.getValue()[0], entry.getValue()[1], 0);
                gone.remove();
            }
            for (HarrierFall f : fliers.falls()) falls.put(f.id, new float[]{f.x(), f.y()});
        }

        /** Offers one voice to the pool; {@code heldFor} is how many ticks it has already waited. */
        private void emit(List<Play> out, Sound sound, long flier, float x, float y, int heldFor) {
            if (voices.offer(sound, flier, tick, new float[]{x, y}, heldFor) != VoicePool.Admit.START) return;
            float pitch = sound.pitch * (1f + jitter(tick, played++));
            out.add(new Play(sound, x, y, pitch, sound.volume * voices.gain(sound)));
        }

        private void reset() {
            voices.clear();
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

    private HarrierAudio() { }

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
