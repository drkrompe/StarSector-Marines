package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.BroodSpires;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.fs.starfarer.api.Global;
import it.unimi.dsi.fastutil.longs.LongList;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The brood spire's sound (`colony-breach-nouns.md`, Sound), read off {@link BroodSpires} rather than posted by it:
 * <ul>
 *   <li>A <b>launch</b> on each pack that rises ({@link BroodSpires#launches}): a wet split as the pods open, the
 *       brood's chitter, then a few wingbeats as it takes up. A Matriarch's brood rises with the same cue, quieter.
 *       The pack's own wingbeats after that are {@link HarrierAudio}'s.</li>
 *   <li>A <b>collapse</b> when a spire dies, shot down or withered with its bore: its chitin creaking, the horn
 *       cracking, then the fall.</li>
 * </ul>
 * Every layer is an existing Rav take at another pitch, so the spire ships no audio of its own; a cue's layers start
 * a few ticks apart on simulation time, and pitch jitter is seeded, so the same battle sounds the same.
 */
public final class SpireAudio {

    static final String PREFIX = SwarmAudio.PREFIX;
    /** A Matriarch's brood is the spire's launch played this much quieter: an escort rising, not a nest emptying. */
    static final float BROOD_GAIN = 0.6f;

    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /**
     * One layer of a cue: its take, its level, its pitch, and how far into the cue it starts.
     *
     * <p>Levels are set from each take's measured loudness, as the Harrier's are. The launch sums near -23 LUFS, by a
     * Harrier screech against the gunfire's -24 to -28: the split near -26, the chitter -27, each wingbeat -28. The
     * collapse is a big one-off and sums near -19: the creaks -27, the crack -20, the fall -22.
     */
    enum Layer {
        SPLIT("spore_vent_burst", 0.2f, 0.85f, 0f),
        CHITTER("runner_chitter", 0.34f, 1.35f, 0.15f),
        TAKE_UP_1("harrier_wingbeat", 1f, 0.9f, 0.35f),
        TAKE_UP_2("harrier_wingbeat", 1f, 0.9f, 0.65f),
        TAKE_UP_3("harrier_wingbeat", 1f, 0.9f, 0.95f),
        CREAK_1("harrier_wingbeat", 1f, 0.55f, 0f),
        CREAK_2("harrier_wingbeat", 1f, 0.5f, 0.2f),
        CRACK("brute_impact", 0.28f, 0.75f, 0.3f),
        FALL("brute_wall_break", 0.2f, 0.7f, 0.6f);

        final String id;
        final float volume;
        final float pitch;
        /** Ticks into the cue this layer starts. */
        final int offset;

        Layer(String clip, float volume, float pitch, float seconds) {
            id = PREFIX + clip;
            this.volume = volume;
            this.pitch = pitch;
            offset = Math.round(seconds / BattleSimulation.TICK_DT);
        }
    }

    /** What the spire is heard doing. */
    enum Cue {
        LAUNCH(Layer.SPLIT, Layer.CHITTER, Layer.TAKE_UP_1, Layer.TAKE_UP_2, Layer.TAKE_UP_3),
        COLLAPSE(Layer.CREAK_1, Layer.CREAK_2, Layer.CRACK, Layer.FALL);

        final Layer[] layers;

        Cue(Layer... layers) {
            this.layers = layers;
        }
    }

    /** A sound to start, where, and at what pitch and level (jitter included). */
    public record Play(Layer layer, float x, float y, float pitch, float volume) {
        public String soundId() { return layer.id; }
    }

    /** A layer waiting for its tick. */
    private record Pending(int tick, Play play) { }

    /** Per battle: launches already heard, and layers still to start. */
    public static final class Mix {
        private int tick = -1;
        private int launches;
        private int played;
        private final List<Pending> pending = new ArrayList<>();

        /** What the spires make this tick, once per tick; empty on a tick already heard. */
        public List<Play> hear(BattleSimulation sim, float listenerX, float listenerY) {
            int now = sim.getSimTickIndex();
            List<Play> out = new ArrayList<>();
            if (now == tick) return out;
            if (now < tick) pending.clear();
            tick = now;
            BroodSpires spires = sim.broodSpires();
            List<BroodSpires.Launch> log = spires.launches();
            for (; launches < log.size(); launches++) {
                BroodSpires.Launch l = log.get(launches);
                float[] at = where(sim, l);
                if (at != null) schedule(Cue.LAUNCH, at[0], at[1], gain(l), now);
            }
            LongList deaths = sim.getDeathsThisFrame();
            for (int i = 0; i < deaths.size(); i++) {
                BroodSpires.Spire s = spires.byEntity(deaths.getLong(i));
                if (s != null) schedule(Cue.COLLAPSE, s.x + 0.5f, s.y + 0.5f, 1f, now);
            }
            for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                if (p.tick() > now) continue;
                out.add(p.play());
                it.remove();
            }
            return out;
        }

        private void schedule(Cue cue, float x, float y, float gain, int now) {
            for (Play p : plays(cue, x, y, gain, now, played)) {
                pending.add(new Pending(now + p.layer().offset, p));
            }
            played += cue.layers.length;
        }
    }

    private SpireAudio() { }

    /** Called every rendered frame with the listener's cell; plays each tick's sounds once, on seen ground. */
    public static void play(BattleSimulation sim, float listenerX, float listenerY) {
        if (sim.broodSpires().spires().isEmpty() && sim.broodSpires().launches().isEmpty()
                && !MIXES.containsKey(sim)) return;
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        for (Play p : mix.hear(sim, listenerX, listenerY)) {
            if (!sim.getFogOfWar().isCellRevealed((int) Math.floor(p.x()), (int) Math.floor(p.y()))) continue;
            Global.getSoundPlayer().playSound(p.soundId(), p.pitch(), p.volume(),
                    new Vector2f(p.x() * BattleShotAudio.WORLD_UNITS_PER_CELL, p.y() * BattleShotAudio.WORLD_UNITS_PER_CELL),
                    new Vector2f(0f, 0f));
        }
    }

    /**
     * Every layer of {@code cue} at {@code (x, y)}, each at its own level times {@code gain} and its own pitch with
     * seeded jitter ({@code n} counts the layers heard before it), in the order they start.
     */
    static List<Play> plays(Cue cue, float x, float y, float gain, int tick, int n) {
        List<Play> out = new ArrayList<>(cue.layers.length);
        for (Layer layer : cue.layers) {
            float pitch = layer.pitch * (1f + HarrierAudio.jitter(tick, n++));
            out.add(new Play(layer, x, y, pitch, layer.volume * gain));
        }
        return out;
    }

    /** A spire's pack launches at full level; a Matriarch's brood ({@code spire} 0) at {@link #BROOD_GAIN}. */
    static float gain(BroodSpires.Launch launch) {
        return launch.spire() == 0L ? BROOD_GAIN : 1f;
    }

    /** Where a pack rose: its spire's cell centre, or its Matriarch; {@code null} when neither is left. */
    private static float[] where(BattleSimulation sim, BroodSpires.Launch launch) {
        if (launch.spire() != 0L) {
            BroodSpires.Spire s = sim.broodSpires().byEntity(launch.spire());
            return s == null ? null : new float[]{s.x + 0.5f, s.y + 0.5f};
        }
        if (sim.resolveUnit(launch.matriarch()) == 0L) return null;
        return new float[]{sim.world().renderX(launch.matriarch()), sim.world().renderY(launch.matriarch())};
    }
}
