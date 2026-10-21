package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.SwarmCues;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.fs.starfarer.api.Global;
import it.unimi.dsi.fastutil.longs.LongList;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The swarm bodies' voices (`colony-breach-nouns.md`, Swarm strain): a Runner's
 * snarl and a Brute's bellow on taking new quarry, strikes, blows on walls and
 * walls giving way from {@link SwarmCues}, and each body's death off the
 * battle's deaths this frame. Played positionally with pitch jitter, heard only
 * where the map has been seen. A swarm is many bodies doing the same thing at
 * once, so each strain's sound is admitted under its own wall-time gap: a pack
 * reads as a pack, not as forty copies of one clip.
 *
 * <p>The Spitter is dropped for now and has no voice here; the Matriarch's roar
 * belongs to its own story.
 */
public final class SwarmAudio {

    static final String PREFIX = "marines_breach_";
    private static final float PITCH_JITTER = 0.08f;

    // Presentation-only: per battle, the last tick played and when each sound last started.
    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /** The sound a strain makes for an act, or {@code null} when it has none. */
    enum Sound {
        RUNNER_CRY("runner_chitter", 0.6f, 350),
        RUNNER_STRIKE("runner_attack", 0.7f, 90),
        RUNNER_DEATH("runner_death", 0.8f, 60),
        RUNNER_IMPALE("runner_impale", 0.9f, 250),
        BODY_DRAG("body_drag", 0.7f, 800),
        BRUTE_BELLOW("brute_bellow", 1f, 1500),
        BRUTE_IMPACT("brute_impact", 0.9f, 200),
        BRUTE_WALL_BREAK("brute_wall_break", 1f, 300),
        BRUTE_DEATH("brute_death", 1f, 150),
        BRUTE_CHARGE("brute_charge", 1f, 600);

        final String id;
        final float volume;
        final long gapNanos;

        Sound(String clip, float volume, long gapMillis) {
            id = PREFIX + clip;
            this.volume = volume;
            gapNanos = gapMillis * 1_000_000L;
        }
    }

    static final class Mix {
        private int tick = -1;
        private final Map<Sound, Long> last = new HashMap<>();

        boolean frame(int current) {
            if (current == tick) return false;
            if (current < tick) last.clear();
            tick = current;
            return true;
        }

        boolean admit(Sound sound, long now) {
            Long prior = last.get(sound);
            if (prior != null && now - prior < sound.gapNanos) return false;
            last.put(sound, now);
            return true;
        }
    }

    private SwarmAudio() { }

    /** Called every rendered frame; plays each tick's cues once. */
    public static void play(BattleSimulation sim) {
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        if (!mix.frame(sim.getSimTickIndex())) return;
        long now = System.nanoTime();
        for (SwarmCues.Cue cue : sim.swarmCues().cuesThisFrame())
            emit(sim, mix, sound(cue.kind(), cue.strain()), cue.x(), cue.y(), now);
        LongList deaths = sim.getDeathsThisFrame();
        for (int i = 0; i < deaths.size(); i++) {
            long body = deaths.getLong(i);
            UnitType type = sim.identity().type(body);
            if (!type.isSwarm()) continue;
            emit(sim, mix, death(type), sim.world().renderX(body), sim.world().renderY(body), now);
        }
    }

    static Sound sound(SwarmCues.Kind kind, UnitType strain) {
        if (strain == UnitType.SWARM_RUNNER) {
            return switch (kind) {
                case ALERT -> Sound.RUNNER_CRY;
                case STRIKE -> Sound.RUNNER_STRIKE;
                case IMPALE -> Sound.RUNNER_IMPALE;
                case DRAG -> Sound.BODY_DRAG;
                default -> null;
            };
        }
        if (strain == UnitType.SWARM_BRUTE) {
            return switch (kind) {
                case ALERT -> Sound.BRUTE_BELLOW;
                case STRIKE, WALL_BLOW -> Sound.BRUTE_IMPACT;
                case WALL_BREAK -> Sound.BRUTE_WALL_BREAK;
                case CHARGE -> Sound.BRUTE_CHARGE;
                case DRAG -> Sound.BODY_DRAG;
                default -> null;
            };
        }
        return null;
    }

    static Sound death(UnitType strain) {
        if (strain == UnitType.SWARM_RUNNER) return Sound.RUNNER_DEATH;
        if (strain == UnitType.SWARM_BRUTE) return Sound.BRUTE_DEATH;
        return null;
    }

    private static void emit(BattleSimulation sim, Mix mix, Sound sound, float x, float y, long now) {
        if (sound == null || !sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y))) return;
        if (!mix.admit(sound, now)) return;
        float pitch = 1f + ThreadLocalRandom.current().nextFloat(-PITCH_JITTER, PITCH_JITTER);
        Global.getSoundPlayer().playSound(sound.id, pitch, sound.volume,
                new Vector2f(x * BattleShotAudio.WORLD_UNITS_PER_CELL, y * BattleShotAudio.WORLD_UNITS_PER_CELL),
                new Vector2f(0f, 0f));
    }
}
