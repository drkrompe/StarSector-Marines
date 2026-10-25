package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.BoreCues;
import com.dillon.starsectormarines.battle.evacuation.BoreTier;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.fs.starfarer.api.Global;
import it.unimi.dsi.fastutil.longs.LongList;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The bores' sound (`colony-breach-nouns.md`, Bore): an eruption, a tier
 * reached, a collapse and a surge leaving the mouth, played positionally from
 * {@link BoreCues}, and a breathing ambience loop under every living bore whose
 * clip follows its tier. A bore is the swarm's, so it is heard only where the
 * map has been seen. Spore-colony bursts need nothing here: a burst is a shot and
 * the vent weapon names its own fire sound.
 *
 * <p>Each clutch a bore lets go is heard clawing up out of the hole (`colony-breach-nouns.md`, Emergence): chitin
 * on rock and wet earth tearing, one short take per clutch, read off {@code SwarmEmergence#begunAt} so the
 * simulation stays silent. A surge's clutches overlap, so they layer by the crowd rule ({@link VoicePool}) under a
 * small voice budget and sit well under the surge cue: a churn under the roar, not a wall.
 */
public final class BoreAudio {

    static final String ERUPTION = "marines_breach_bore_eruption";
    static final String COLLAPSE = "marines_breach_bore_collapse";
    static final String SURGE = "marines_breach_bore_surge";
    static final String GROWTH = "marines_breach_bore_growth";
    static final String CLAW_UP = "marines_breach_bore_clawup";
    static final float PITCH_JITTER = 0.06f;
    /**
     * One body's claw-up alone. The take is levelled to -20 LUFS, so a Runner's lands near -33, under the
     * footfalls' crowd and far under the surge cue and the gunfire's -24 to -28.
     */
    static final float CLAW_UP_VOLUME = 0.22f;
    /** Ticks of missed emergence a frame looks back over, when the screen draws slower than the battle runs. */
    static final int CATCH_UP_TICKS = 30;
    private static final float AMBIENCE_VOLUME = 0.45f;

    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /** The bores' one layered sound: claw-ups of overlapping clutches, four at once at most, each a 0.65 s take. */
    enum Sound {
        CLAW_UP(4, 0.65f);

        final VoicePool.Layering layering;

        Sound(int voices, float voiceSeconds) {
            this.layering = new VoicePool.Layering(voices, voiceSeconds, 0f);
        }
    }

    /** A sound to start, where, and at what pitch and level (jitter included). */
    public record Play(String soundId, float x, float y, float pitch, float volume) { }

    /** How one clutch is heard: the pitch its heaviest body sets and its level against one body alone. */
    record Claw(float pitch, float volume) { }

    /** Per battle: the last tick heard, and the claw-ups' voices. */
    public static final class Mix {
        private int tick = -1;
        private int played;
        private final VoicePool<Sound, float[]> voices = new VoicePool<>(Sound.class, sound -> sound.layering);

        /** What the bores make this tick, once per tick; empty on a tick already heard. */
        public List<Play> hear(BattleSimulation sim) {
            int now = sim.getSimTickIndex();
            List<Play> out = new ArrayList<>();
            if (now == tick) return out;
            if (now < tick) reset();
            int from = tick < 0 ? now : Math.max(tick + 1, now - CATCH_UP_TICKS);
            tick = now;
            for (BoreCues.Cue cue : sim.swarmBores().cues().cuesThisFrame()) {
                float pitch = 1f + jitter(now, played++);
                out.add(new Play(soundId(cue.kind()), cue.x(), cue.y(), pitch, volume(cue.kind())));
            }
            for (VoicePool.Held<Sound, float[]> h : voices.due(now)) {
                float[] p = h.payload();
                claw(out, p[0], p[1], new Claw(p[2], p[3]), h.heldFor());
            }
            for (int t = from; t <= now; t++) clutches(sim, sim.swarmEmergence().begunAt(t), out);
            return out;
        }

        /** One claw-up per bore whose clutch began: its bodies grouped by the bore they came up out of. */
        private void clutches(BattleSimulation sim, LongList begun, List<Play> out) {
            if (begun.isEmpty()) return;
            Map<SwarmBore, List<UnitType>> byBore = new LinkedHashMap<>();
            for (int i = 0; i < begun.size(); i++) {
                long body = begun.getLong(i);
                if (!sim.world().isAlive(body)) continue;
                SwarmBore bore = nearest(sim, sim.world().x(body), sim.world().y(body));
                if (bore != null) byBore.computeIfAbsent(bore, ignored -> new ArrayList<>()).add(sim.identity().type(body));
            }
            byBore.forEach((bore, bodies) -> claw(out, bore.x() + 0.5f, bore.y() + 0.5f, clawFor(bodies), 0));
        }

        /** Offers one claw-up to the voices; {@code heldFor} is how many ticks it has already waited. */
        void claw(List<Play> out, float x, float y, Claw claw, int heldFor) {
            float[] payload = {x, y, claw.pitch(), claw.volume()};
            if (voices.offer(Sound.CLAW_UP, 0L, tick, payload, heldFor) != VoicePool.Admit.START) return;
            float pitch = claw.pitch() * (1f + jitter(tick, played++));
            out.add(new Play(CLAW_UP, x, y, pitch, CLAW_UP_VOLUME * claw.volume() * voices.gain(Sound.CLAW_UP)));
        }

        /** The tick heard, for a test driving {@link #claw} without a battle. */
        void at(int tick) {
            this.tick = tick;
        }

        private void reset() {
            voices.clear();
            played = 0;
        }
    }

    private BoreAudio() { }

    /** Called every rendered frame: the loops must be refreshed each frame, the one-shots once per tick. */
    public static void play(BattleSimulation sim) {
        Vector2f still = new Vector2f(0f, 0f);
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.collapsed() || !heard(sim, bore.x(), bore.y())) continue;
            Global.getSoundPlayer().playLoop(ambience(bore.tier()), bore, 1f, AMBIENCE_VOLUME,
                    where(bore.x() + 0.5f, bore.y() + 0.5f), still);
        }
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        for (Play p : mix.hear(sim)) {
            if (!heard(sim, (int) Math.floor(p.x()), (int) Math.floor(p.y()))) continue;
            Global.getSoundPlayer().playSound(p.soundId(), p.pitch(), p.volume(), where(p.x(), p.y()), still);
        }
    }

    /** The one-shot clip for a cue; a tier reached tears the ground wider before its ambience changes. */
    static String soundId(BoreCues.Kind kind) {
        return switch (kind) {
            case ERUPTED -> ERUPTION;
            case COLLAPSED -> COLLAPSE;
            case SURGE -> SURGE;
            case GREW -> GROWTH;
        };
    }

    static float volume(BoreCues.Kind kind) {
        return switch (kind) {
            case ERUPTED -> 1f;
            case COLLAPSED -> 0.95f;
            case SURGE -> 0.7f;
            case GREW -> 0.9f;
        };
    }

    static String ambience(BoreTier tier) {
        return "marines_breach_bore_ambience_" + tier.beatName;
    }

    /**
     * How a clutch claws up: pitched by its heaviest body (the Matriarch deep and slow, a Brute lower, a skitterer
     * light and quick) and a little louder the more bodies break out together ({@code n^1/4}), the Matriarch louder
     * still.
     */
    static Claw clawFor(List<UnitType> bodies) {
        float pitch = 2f;
        for (UnitType type : bodies) pitch = Math.min(pitch, pitch(type));
        if (bodies.isEmpty()) pitch = 1f;
        float volume = (float) Math.pow(Math.max(1, bodies.size()), 0.25);
        if (bodies.contains(UnitType.SWARM_MATRIARCH)) volume *= 1.6f;
        return new Claw(pitch, volume);
    }

    static float pitch(UnitType type) {
        return switch (type) {
            case SWARM_MATRIARCH -> 0.7f;
            case SWARM_BRUTE -> 0.84f;
            case SWARM_SPITTER -> 0.94f;
            case SWARM_WORKER -> 1.25f;
            default -> 1f;
        };
    }

    /** Seeded pitch jitter for the {@code n}th sound heard, at {@code tick}: within {@link #PITCH_JITTER}. */
    static float jitter(int tick, int n) {
        return HarrierAudio.jitter(tick, n) * (PITCH_JITTER / HarrierAudio.PITCH_JITTER);
    }

    /** The living bore whose hole is nearest ({@code x}, {@code y}), or null for none. */
    private static SwarmBore nearest(BattleSimulation sim, float x, float y) {
        SwarmBore best = null;
        float bestD2 = Float.MAX_VALUE;
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.collapsed()) continue;
            float dx = bore.x() + 0.5f - x, dy = bore.y() + 0.5f - y, d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                best = bore;
                bestD2 = d2;
            }
        }
        return best;
    }

    private static Vector2f where(float x, float y) {
        return new Vector2f(x * BattleShotAudio.WORLD_UNITS_PER_CELL, y * BattleShotAudio.WORLD_UNITS_PER_CELL);
    }

    private static boolean heard(BattleSimulation sim, int x, int y) {
        return sim.getFogOfWar().isCellRevealed(x, y);
    }
}
