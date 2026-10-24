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
 *   <li><b>Wingbeats</b>, quiet, on each downstroke of the few fliers nearest the listener
 *       ({@link HarrierWingbeat#phase}).</li>
 *   <li>A <b>screech</b> as each member goes in: the moment its wings start to fold for the dive.</li>
 *   <li>The <b>bite</b> on each strike the pack lands, the <b>snatch</b> when a strike lifts a marine and the
 *       <b>landing</b> when the throw comes down.</li>
 *   <li><b>Pain</b> when a flier loses health and lives (flak), the <b>death cry</b> when it is killed, and a
 *       <b>thump</b> when its fall reaches the ground.</li>
 * </ul>
 * Every clip is an existing Rav take played a little higher and quicker through the pitch argument (the
 * Matriarch's rule in {@link SwarmAudio}, the other way): a lighter, smaller creature, and no new files. Levels sit
 * under the line's fire (a rifle plays at about 0.4, flak at 0.6): a pack under flak cries and dies often, and its
 * shrieks are long clips where a shot is short.
 *
 * <p>A pack does everything together, so each sound is admitted under two gaps: one per pack, so five members
 * going in at once read as a staggered cry rather than five copies of a clip, and a shorter one across the battle.
 * Gaps and pitch jitter run on simulation time and a seed, so the same battle sounds the same (a review mixes its
 * soundtrack from {@link Mix#hear}) and the rules are testable without a sound player.
 */
public final class HarrierAudio {

    static final String PREFIX = SwarmAudio.PREFIX;
    static final float PITCH_JITTER = 0.06f;
    /** Wingbeats further from the listener than this are not drawn. */
    static final float WINGBEAT_RANGE_CELLS = 18f;
    /** At most this many wingbeats start in one tick, nearest first. */
    static final int WINGBEATS_PER_TICK = 2;
    /** Health lost below this is a graze and draws no pain sound. */
    static final float PAIN_THRESHOLD = 1f;

    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /** One sound, with its level, its gaps across the battle and within a pack, and the pitch its take plays at. */
    enum Sound {
        WINGBEAT("runner_clatter", 0.2f, 0.08f, 0.25f, 1.15f),
        SCREECH("runner_chitter", 0.5f, 0.15f, 0.4f, 1.2f),
        STRIKE("runner_attack", 0.5f, 0.09f, 0.2f, 1.12f),
        LIFT("runner_impale", 0.6f, 0.25f, 0.25f, 1.15f),
        THROW_LANDING("brute_charge_hit_body", 0.5f, 0.25f, 0f, 1.2f),
        PAIN("runner_pain", 0.4f, 0.12f, 0.3f, 1.2f),
        DEATH("spitter_death", 0.45f, 0.15f, 0.3f, 0.9f),
        FALL_THUMP("brute_footfall", 0.35f, 0.12f, 0f, 1.3f);

        final String id;
        final float volume;
        final float gapSeconds;
        /** Shortest gap between two plays by one pack; 0 for sounds no pack owns. */
        final float packGapSeconds;
        final float pitch;

        Sound(String clip, float volume, float gapSeconds, float packGapSeconds, float pitch) {
            this.id = PREFIX + clip;
            this.volume = volume;
            this.gapSeconds = gapSeconds;
            this.packGapSeconds = packGapSeconds;
            this.pitch = pitch;
        }
    }

    /** A sound to start, where, and at what pitch and level (jitter included). */
    public record Play(Sound sound, float x, float y, float pitch, float volume) {
        public String soundId() { return sound.id; }
    }

    /** Per battle: what was last seen of each flier, and when each sound last played. */
    public static final class Mix {
        private int tick = -1;
        private final EnumMap<Sound, Float> last = new EnumMap<>(Sound.class);
        private final Long2DoubleOpenHashMap lastByPack = new Long2DoubleOpenHashMap();
        // Readings by flier, as float bits; absent reads NaN.
        private final Long2IntOpenHashMap wing = readings();
        private final Long2IntOpenHashMap fold = readings();
        private final Long2IntOpenHashMap health = readings();
        private final Long2ObjectOpenHashMap<float[]> lifts = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<float[]> falls = new Long2ObjectOpenHashMap<>();
        private int events;
        private int played;

        private static Long2IntOpenHashMap readings() {
            Long2IntOpenHashMap map = new Long2IntOpenHashMap();
            map.defaultReturnValue(Float.floatToRawIntBits(Float.NaN));
            return map;
        }

        /**
         * Whether {@code sound} may start at {@code seconds} for {@code pack} ({@code 0} for none), and records it
         * if so: no sooner than its gap after the last of it anywhere, nor its pack gap after the pack's last.
         */
        boolean admit(Sound sound, int pack, float seconds) {
            Float prior = last.get(sound);
            if (prior != null && seconds - prior < sound.gapSeconds) return false;
            long key = (long) sound.ordinal() << 32 | (pack & 0xFFFFFFFFL);
            if (pack != 0 && sound.packGapSeconds > 0f && lastByPack.containsKey(key)
                    && seconds - lastByPack.get(key) < sound.packGapSeconds) return false;
            last.put(sound, seconds);
            if (pack != 0) lastByPack.put(key, seconds);
            return true;
        }

        /** What the fliers make this tick, once per tick; empty on a tick already heard. */
        public List<Play> hear(BattleSimulation sim, float listenerX, float listenerY) {
            int now = sim.getSimTickIndex();
            List<Play> out = new ArrayList<>();
            if (now == tick) return out;
            if (now < tick) reset();
            tick = now;
            float seconds = now * BattleSimulation.TICK_DT;
            SwarmFliers fliers = sim.swarmFliers();
            List<float[]> beats = new ArrayList<>();
            for (HarrierPack pack : fliers.packs()) {
                for (HarrierFlight m : pack.members) {
                    float x = m.body.x, y = m.body.y;
                    float beat = HarrierWingbeat.phase(m, pack.phase());
                    float priorBeat = swap(wing, m.id, beat);
                    if (downstroke(priorBeat, beat)) {
                        float dx = x - listenerX, dy = y - listenerY;
                        float d2 = dx * dx + dy * dy;
                        if (d2 <= WINGBEAT_RANGE_CELLS * WINGBEAT_RANGE_CELLS) beats.add(new float[]{d2, x, y, pack.id});
                    }
                    if (wentIn(swap(fold, m.id, m.fold), m.fold)) emit(out, Sound.SCREECH, pack.id, x, y, seconds);
                    float hp = sim.world().hp(m.id);
                    if (hurt(swap(health, m.id, hp), hp)) emit(out, Sound.PAIN, pack.id, x, y, seconds);
                }
            }
            beats.sort(Comparator.comparingDouble(b -> b[0]));
            for (int i = 0; i < Math.min(WINGBEATS_PER_TICK, beats.size()); i++) {
                float[] b = beats.get(i);
                emit(out, Sound.WINGBEAT, (int) b[3], b[1], b[2], seconds);
            }
            List<SwarmFliers.Event> log = fliers.events();
            for (; events < log.size(); events++) {
                SwarmFliers.Event e = log.get(events);
                if (e.kind() != SwarmFliers.Event.Kind.STRIKE) continue;
                HarrierFlight m = fliers.flight(e.harrier());
                float x = m != null ? m.body.x : sim.world().x(e.target());
                float y = m != null ? m.body.y : sim.world().y(e.target());
                emit(out, Sound.STRIKE, e.pack(), x, y, seconds);
            }
            lifts(sim, fliers, out, seconds);
            LongList deaths = sim.getDeathsThisFrame();
            for (int i = 0; i < deaths.size(); i++) {
                long body = deaths.getLong(i);
                if (sim.identity().type(body) != UnitType.SWARM_HARRIER) continue;
                HarrierPack pack = fliers.packOf(body);
                emit(out, Sound.DEATH, pack != null ? pack.id : 0, sim.world().renderX(body), sim.world().renderY(body),
                        seconds);
            }
            falls(fliers, out, seconds);
            return out;
        }

        /** A snatch when a throw starts, and the landing when it ends with the marine still alive. */
        private void lifts(BattleSimulation sim, SwarmFliers fliers, List<Play> out, float seconds) {
            for (HarrierLift l : fliers.lifts()) {
                if (!lifts.containsKey(l.body)) {
                    HarrierPack pack = fliers.packOf(l.harrier);
                    emit(out, Sound.LIFT, pack != null ? pack.id : 0, l.x(), l.y(), seconds);
                }
                lifts.put(l.body, new float[]{l.x(), l.y()});
            }
            var gone = lifts.long2ObjectEntrySet().iterator();
            while (gone.hasNext()) {
                var entry = gone.next();
                if (fliers.lift(entry.getLongKey()) != null) continue;
                if (sim.getRoster().isAliveById(entry.getLongKey())) {
                    emit(out, Sound.THROW_LANDING, 0, entry.getValue()[0], entry.getValue()[1], seconds);
                }
                gone.remove();
            }
        }

        /** A thump where each fall that was in the air last tick has come down. */
        private void falls(SwarmFliers fliers, List<Play> out, float seconds) {
            var gone = falls.long2ObjectEntrySet().iterator();
            while (gone.hasNext()) {
                var entry = gone.next();
                if (fliers.falling(entry.getLongKey())) continue;
                emit(out, Sound.FALL_THUMP, 0, entry.getValue()[0], entry.getValue()[1], seconds);
                gone.remove();
            }
            for (HarrierFall f : fliers.falls()) falls.put(f.id, new float[]{f.x(), f.y()});
        }

        private void emit(List<Play> out, Sound sound, int pack, float x, float y, float seconds) {
            if (!admit(sound, pack, seconds)) return;
            float pitch = sound.pitch * (1f + jitter(tick, played++));
            out.add(new Play(sound, x, y, pitch, sound.volume));
        }

        private void reset() {
            last.clear();
            lastByPack.clear();
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
