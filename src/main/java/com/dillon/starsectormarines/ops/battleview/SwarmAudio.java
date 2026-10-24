package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.evacuation.SwarmCues;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.fs.starfarer.api.Global;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The swarm bodies' sound (`colony-breach-nouns.md`, Sound), keyed to what each
 * body does rather than to the beat table:
 * <ul>
 *   <li><b>Acts</b> from {@link SwarmCues}: a Runner's snarl and a Brute's
 *       bellow on taking new quarry, strikes, rams and walls giving way, and
 *       each phase of the Brute's charge and the Runner's impale, and the
 *       latch of a body being taken up.</li>
 *   <li><b>Footfalls</b> off each body's gait phase, which advances with the
 *       ground it covers: a gallop on every other Runner stride (and the
 *       chitin clattering on some of them), a Brute's hoof every half cell, so a
 *       walking Brute plods and a charging one thunders. A Runner hauling a
 *       body drags it instead: its strides scrape the load over the ground,
 *       repeated no faster than one drag clip plays.</li>
 *   <li><b>Pain</b> when a body loses health and lives, <b>death</b> off the
 *       battle's deaths this frame.</li>
 * </ul>
 * Played positionally with pitch jitter, heard only on ground the player has
 * seen. A body's growth stage pitches everything it makes by the inverse of its
 * size and sets its loudness ({@link #pitch}, {@link #volume}): one take heard
 * three ways, so a whelp pack chitters high and light and an elder is deeper
 * and louder, and still the same creature. A pack's break is one scatter
 * shriek, the leader's own cry at the leader's stage. A swarm is many bodies doing the same thing at once, so each sound is
 * admitted under its own wall-time gap and footfalls are drawn from the few
 * bodies nearest the listener: a pack reads as a pack, not as forty copies of
 * one clip. Presentation only; nothing here reaches the simulation.
 *
 * <p>The Spitter has no voice. The Matriarch has her own roar (out of the bore
 * and at each moult) and reap; her bellow, hoof, crown, wall-break, pain and
 * death are the Brute's takes played deeper and slower through the pitch
 * argument, not baked copies. The Harrier, which flies with its pack rather than walking, is
 * {@link HarrierAudio}'s, played from here.
 */
public final class SwarmAudio {

    static final String PREFIX = "marines_breach_";
    private static final float PITCH_JITTER = 0.08f;
    /** Footfalls further from the listener than this are not drawn. */
    private static final float FOOTFALL_RANGE_CELLS = 22f;
    /** At most this many footfalls start in one frame, nearest first. */
    private static final int FOOTFALLS_PER_FRAME = 3;
    /** Health lost below this is a graze and draws no pain sound. */
    private static final float PAIN_THRESHOLD = 1f;

    // Presentation-only: per battle, the last tick played, gait and health last seen, and gaps.
    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /** One sound a strain makes, with its mix level and the shortest gap between two plays. */
    enum Sound {
        RUNNER_CRY("runner_chitter", 0.6f, 350),
        RUNNER_GALLOP("runner_gallop", 0.5f, 110),
        RUNNER_CLATTER("runner_clatter", 0.45f, 260),
        RUNNER_STRIKE("runner_attack", 0.7f, 90),
        RUNNER_IMPALE("runner_impale", 0.9f, 250),
        RUNNER_PAIN("runner_pain", 0.6f, 120),
        RUNNER_DEATH("runner_death", 0.8f, 60),
        BODY_LATCH("body_latch", 0.8f, 400),
        BODY_DRAG("body_drag", 0.6f, 1900),
        BRUTE_BELLOW("brute_bellow", 1f, 1500),
        BRUTE_FOOTFALL("brute_footfall", 0.75f, 90),
        BRUTE_IMPACT("brute_impact", 0.9f, 200),
        BRUTE_WALL_BREAK("brute_wall_break", 1f, 300),
        BRUTE_CHARGE_TELL("brute_charge_tell", 1f, 400),
        BRUTE_CHARGE_RUN("brute_charge", 1f, 600),
        BRUTE_CHARGE_HIT_BODY("brute_charge_hit_body", 1f, 250),
        BRUTE_CHARGE_HIT_VEHICLE("brute_charge_hit_vehicle", 1f, 300),
        BRUTE_CHARGE_HIT_WALL("brute_charge_hit_wall", 1f, 300),
        BRUTE_STUN("brute_stun", 0.9f, 800),
        BRUTE_PAIN("brute_pain", 0.8f, 300),
        BRUTE_DEATH("brute_death", 1f, 150),
        // The Matriarch: her own roar and reap; everything else is a Brute take played a size deeper at
        // runtime, so a variant costs no file (colony-breach-nouns.md, Sound). Her death is the Brute's
        // slowed and deepened by the same pitch argument.
        MATRIARCH_ROAR("matriarch_roar", 1f, 4000),
        MATRIARCH_REAP("matriarch_reap", 1f, 400),
        MATRIARCH_DEATH("brute_death", 1f, 500, 0.62f),
        MATRIARCH_BELLOW("brute_bellow", 1f, 3000, 0.72f),
        MATRIARCH_FOOTFALL("brute_footfall", 1f, 250, 0.65f),
        MATRIARCH_CROWN("brute_impact", 1f, 400, 0.75f),
        MATRIARCH_WALL_BREAK("brute_wall_break", 1f, 600, 0.8f),
        MATRIARCH_PAIN("brute_pain", 0.9f, 1200, 0.72f);

        final String id;
        final float volume;
        final long gapNanos;
        /** A borrowed take played this much lower: a bigger body. */
        final float pitch;

        Sound(String clip, float volume, long gapMillis) {
            this(clip, volume, gapMillis, 1f);
        }

        Sound(String clip, float volume, long gapMillis, float pitch) {
            id = PREFIX + clip;
            this.volume = volume;
            gapNanos = gapMillis * 1_000_000L;
            this.pitch = pitch;
        }
    }

    static final class Mix {
        private int tick = -1;
        private final EnumMap<Sound, Long> last = new EnumMap<>(Sound.class);
        // Readings by body, as float bits (fastutil-core has no float maps); absent reads NaN.
        private final Long2IntOpenHashMap gait = readings();
        private final Long2IntOpenHashMap health = readings();
        private final Long2IntOpenHashMap seenGait = readings();
        private final Long2IntOpenHashMap seenHealth = readings();
        private int strides;

        private static Long2IntOpenHashMap readings() {
            Long2IntOpenHashMap map = new Long2IntOpenHashMap();
            map.defaultReturnValue(Float.floatToRawIntBits(Float.NaN));
            return map;
        }

        boolean frame(int current) {
            if (current == tick) return false;
            if (current < tick) {
                last.clear();
                gait.clear();
                health.clear();
            }
            tick = current;
            return true;
        }

        boolean admit(Sound sound, long now) {
            Long prior = last.get(sound);
            if (prior != null && now - prior < sound.gapNanos) return false;
            last.put(sound, now);
            return true;
        }

        /** Swaps in this frame's readings; bodies not seen this frame are forgotten. */
        void settle() {
            gait.clear();
            gait.putAll(seenGait);
            seenGait.clear();
            health.clear();
            health.putAll(seenHealth);
            seenHealth.clear();
        }
    }

    /** A footfall waiting for the per-frame budget. */
    private record Step(Sound sound, float x, float y, float distanceSquared, UnitType strain, SwarmStage stage) { }

    private SwarmAudio() { }

    /**
     * Called every rendered frame with the listener's cell; plays each tick's
     * sounds once.
     */
    public static void play(BattleSimulation sim, float listenerX, float listenerY) {
        HarrierAudio.play(sim, listenerX, listenerY);
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        if (!mix.frame(sim.getSimTickIndex())) return;
        long now = System.nanoTime();
        for (SwarmCues.Cue cue : sim.swarmCues().cuesThisFrame())
            emit(sim, mix, sound(cue.kind(), cue.strain()), cue.x(), cue.y(), now, cue.strain(), cue.stage());
        LongList deaths = sim.getDeathsThisFrame();
        for (int i = 0; i < deaths.size(); i++) {
            long body = deaths.getLong(i);
            UnitType type = sim.identity().type(body);
            if (!type.isSwarm()) continue;
            emit(sim, mix, death(type), sim.world().renderX(body), sim.world().renderY(body), now,
                    type, sim.identity().swarmStage(body));
        }
        bodies(sim, mix, listenerX, listenerY, now);
    }

    /** Footfalls and pain off every living swarm body's gait and health. */
    private static void bodies(BattleSimulation sim, Mix mix, float listenerX, float listenerY, long now) {
        BattleComponents components = sim.getRoster().components();
        EntityWorld world = sim.getRoster().entityWorld();
        List<Step> steps = new ArrayList<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long body = sim.liveUnitAt(i);
            UnitType type = sim.identity().type(body);
            if (!type.isSwarm()) continue;
            float x = sim.world().renderX(body);
            float y = sim.world().renderY(body);
            SwarmStage stage = sim.identity().swarmStage(body);
            float hp = sim.world().hp(body);
            float priorHp = Float.intBitsToFloat(mix.health.get(body));
            mix.seenHealth.put(body, Float.floatToRawIntBits(hp));
            if (hp > 0f && priorHp - hp >= PAIN_THRESHOLD) emit(sim, mix, pain(type), x, y, now, type, stage);
            if (!world.has(body, components.MOVEMENT)) continue;
            float phase = world.getFloat(body, components.MOVEMENT, BattleComponents.MOVEMENT_GAIT_PHASE);
            float priorPhase = Float.intBitsToFloat(mix.gait.get(body));
            mix.seenGait.put(body, Float.floatToRawIntBits(phase));
            if (Float.isNaN(priorPhase) || !stepped(type, priorPhase, phase)) continue;
            float dx = x - listenerX;
            float dy = y - listenerY;
            float d2 = dx * dx + dy * dy;
            if (!(d2 <= FOOTFALL_RANGE_CELLS * FOOTFALL_RANGE_CELLS)) continue;
            Sound step = footfall(type, ++mix.strides, sim.swarmHarvest().isCarrier(body));
            if (step != null) steps.add(new Step(step, x, y, d2, type, stage));
        }
        mix.settle();
        steps.sort(Comparator.comparingDouble(Step::distanceSquared));
        for (int i = 0; i < Math.min(FOOTFALLS_PER_FRAME, steps.size()); i++) {
            Step step = steps.get(i);
            emit(sim, mix, step.sound(), step.x(), step.y(), now, step.strain(), step.stage());
        }
    }

    /**
     * Whether a body's gait crossed a footfall since the last reading. The gait
     * phase is the ground covered, modulo one cell: a Brute's hoof falls at each
     * half and a Runner's stride at each wrap ({@link #footfall} thins the
     * pack's strides), so a stopped body is silent and a fast one is busy.
     */
    static boolean stepped(UnitType type, float prior, float phase) {
        if (phase == prior) return false;
        boolean wrapped = phase < prior;
        if (heavy(type)) return wrapped || (prior < 0.5f && phase >= 0.5f);
        return wrapped;
    }

    static Sound footfall(UnitType strain, int stride, boolean carrying) {
        if (strain == UnitType.SWARM_MATRIARCH) return Sound.MATRIARCH_FOOTFALL;
        if (heavy(strain)) return Sound.BRUTE_FOOTFALL;
        if (strain == UnitType.SWARM_RUNNER) {
            if (carrying) return Sound.BODY_DRAG;
            if (stride % 2 != 0) return null;
            return stride % 6 == 0 ? Sound.RUNNER_CLATTER : Sound.RUNNER_GALLOP;
        }
        return null;
    }

    static Sound sound(SwarmCues.Kind kind, UnitType strain) {
        if (strain == UnitType.SWARM_RUNNER) {
            return switch (kind) {
                case ALERT, PACK_BROKEN -> Sound.RUNNER_CRY;
                case STRIKE -> Sound.RUNNER_STRIKE;
                case IMPALE -> Sound.RUNNER_IMPALE;
                case DRAG -> Sound.BODY_LATCH;
                default -> null;
            };
        }
        if (strain == UnitType.SWARM_MATRIARCH) {
            return switch (kind) {
                case ROAR, MOULT -> Sound.MATRIARCH_ROAR;
                case ALERT -> Sound.MATRIARCH_BELLOW;
                case REAP -> Sound.MATRIARCH_REAP;
                case STRIKE, WALL_BLOW -> Sound.MATRIARCH_CROWN;
                case WALL_BREAK -> Sound.MATRIARCH_WALL_BREAK;
                default -> null;
            };
        }
        if (heavy(strain)) {
            return switch (kind) {
                case ALERT, PACK_BROKEN, MOULT -> Sound.BRUTE_BELLOW;
                case STRIKE, WALL_BLOW -> Sound.BRUTE_IMPACT;
                case WALL_BREAK -> Sound.BRUTE_WALL_BREAK;
                case CHARGE_TELL -> Sound.BRUTE_CHARGE_TELL;
                case CHARGE_RUN -> Sound.BRUTE_CHARGE_RUN;
                case CHARGE_HIT_BODY -> Sound.BRUTE_CHARGE_HIT_BODY;
                case CHARGE_HIT_VEHICLE -> Sound.BRUTE_CHARGE_HIT_VEHICLE;
                case CHARGE_HIT_WALL -> Sound.BRUTE_CHARGE_HIT_WALL;
                case STUNNED -> Sound.BRUTE_STUN;
                case DRAG -> Sound.BODY_LATCH;
                default -> null;
            };
        }
        return null;
    }

    /** The Brute and the Matriarch: a hoof every half cell. */
    private static boolean heavy(UnitType strain) {
        return strain == UnitType.SWARM_BRUTE || strain == UnitType.SWARM_MATRIARCH;
    }

    static Sound pain(UnitType strain) {
        if (strain == UnitType.SWARM_RUNNER) return Sound.RUNNER_PAIN;
        if (strain == UnitType.SWARM_MATRIARCH) return Sound.MATRIARCH_PAIN;
        if (heavy(strain)) return Sound.BRUTE_PAIN;
        return null;
    }

    static Sound death(UnitType strain) {
        if (strain == UnitType.SWARM_RUNNER) return Sound.RUNNER_DEATH;
        if (strain == UnitType.SWARM_MATRIARCH) return Sound.MATRIARCH_DEATH;
        if (heavy(strain)) return Sound.BRUTE_DEATH;
        return null;
    }

    /** A body's pitch by its stage: the inverse of its drawn size, so a whelp is about four semitones up. */
    static float pitch(UnitType strain, SwarmStage stage) {
        return 1f / SwarmStage.of(stage).scale(strain);
    }

    /** A body's loudness by its stage, against the adult's. */
    static float volume(SwarmStage stage) {
        return switch (SwarmStage.of(stage)) {
            case WHELP -> 0.85f;
            case ADULT -> 1f;
            case ELDER -> 1.15f;
        };
    }

    private static void emit(BattleSimulation sim, Mix mix, Sound sound, float x, float y, long now,
                             UnitType strain, SwarmStage stage) {
        if (sound == null || !sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y))) return;
        if (!mix.admit(sound, now)) return;
        float pitch = sound.pitch * pitch(strain, stage) * (1f + ThreadLocalRandom.current().nextFloat(-PITCH_JITTER, PITCH_JITTER));
        Global.getSoundPlayer().playSound(sound.id, pitch, sound.volume * volume(stage),
                new Vector2f(x * BattleShotAudio.WORLD_UNITS_PER_CELL, y * BattleShotAudio.WORLD_UNITS_PER_CELL),
                new Vector2f(0f, 0f));
    }
}
