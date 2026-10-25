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
 * shriek, the leader's own cry at the leader's stage. A swarm is many bodies doing the same thing at once, so a
 * sound layers ({@link VoicePool}): several of its voices at once, up to its voice count, each quieter the more are
 * sounding, and one body never repeats inside its own gap; footfalls are drawn from the bodies nearest the
 * listener. A pack reads as a pack, thicker and somewhat louder than one body, not as forty copies of one clip.
 * A thrown body's landing is one thud whatever threw it. Presentation only; nothing here reaches the simulation.
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
    /** At most this many footfalls are offered in one frame, nearest first; the voices cap what starts. */
    private static final int FOOTFALLS_PER_FRAME = 8;
    /** Health lost below this is a graze and draws no pain sound. */
    private static final float PAIN_THRESHOLD = 1f;

    // Presentation-only: per battle, the last tick played, gait and health last seen, and the voices.
    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /**
     * One sound a strain makes: its take, the level one voice plays at alone, how many voices of it may sound at once
     * and how long each counts as sounding, the shortest gap before one body repeats it (0 for none), and for a
     * borrowed take the pitch it plays at.
     */
    enum Sound {
        RUNNER_CRY("runner_chitter", 0.6f, 4, 1.2f, 2f),
        RUNNER_GALLOP("runner_gallop", 0.5f, 6, 0.5f, 0f),
        RUNNER_CLATTER("runner_clatter", 0.45f, 3, 0.6f, 0f),
        RUNNER_STRIKE("runner_attack", 0.7f, 5, 0.8f, 0.4f),
        RUNNER_IMPALE("runner_impale", 0.9f, 3, 1.4f, 1f),
        RUNNER_PAIN("runner_pain", 0.6f, 4, 0.6f, 0.4f),
        RUNNER_DEATH("runner_death", 0.8f, 6, 1.2f, 0f),
        BODY_LATCH("body_latch", 0.8f, 3, 0.6f, 0.6f),
        BODY_DRAG("body_drag", 0.6f, 3, 1.9f, 1.9f),
        BODY_THUD("body_thud", 0.8f, 5, 0.8f, 0.5f),
        BRUTE_BELLOW("brute_bellow", 1f, 2, 2.5f, 4f),
        BRUTE_FOOTFALL("brute_footfall", 0.75f, 6, 0.6f, 0f),
        BRUTE_IMPACT("brute_impact", 0.9f, 3, 1.2f, 0.4f),
        BRUTE_WALL_BREAK("brute_wall_break", 1f, 2, 2.5f, 0f),
        BRUTE_CHARGE_TELL("brute_charge_tell", 1f, 2, 0.9f, 0f),
        BRUTE_CHARGE_RUN("brute_charge", 1f, 2, 2.5f, 0f),
        BRUTE_CHARGE_HIT_BODY("brute_charge_hit_body", 1f, 3, 1.2f, 0f),
        BRUTE_CHARGE_HIT_VEHICLE("brute_charge_hit_vehicle", 1f, 2, 1.5f, 0f),
        BRUTE_CHARGE_HIT_WALL("brute_charge_hit_wall", 1f, 2, 1.5f, 0f),
        BRUTE_STUN("brute_stun", 0.9f, 2, 1.8f, 0f),
        BRUTE_PAIN("brute_pain", 0.8f, 3, 0.9f, 0.6f),
        BRUTE_DEATH("brute_death", 1f, 3, 2.5f, 0f),
        // The Matriarch: her own roar and reap; everything else is a Brute take played a size deeper at
        // runtime, so a variant costs no file (colony-breach-nouns.md, Sound). Her death is the Brute's
        // slowed and deepened by the same pitch argument. There is one of her, so her voices hardly layer.
        MATRIARCH_ROAR("matriarch_roar", 1f, 1, 4f, 0f),
        MATRIARCH_REAP("matriarch_reap", 1f, 2, 1.3f, 0f),
        MATRIARCH_DEATH("brute_death", 1f, 1, 3f, 0f, 0.62f),
        MATRIARCH_BELLOW("brute_bellow", 1f, 1, 3.5f, 0f, 0.72f),
        MATRIARCH_FOOTFALL("brute_footfall", 1f, 4, 1f, 0f, 0.65f),
        MATRIARCH_CROWN("brute_impact", 1f, 2, 2f, 0f, 0.75f),
        MATRIARCH_WALL_BREAK("brute_wall_break", 1f, 2, 3f, 0f, 0.8f),
        MATRIARCH_PAIN("brute_pain", 0.9f, 1, 1.3f, 1.2f, 0.72f);

        final String id;
        final float volume;
        final VoicePool.Layering layering;
        /** A borrowed take played this much lower: a bigger body. */
        final float pitch;

        Sound(String clip, float volume, int voices, float voiceSeconds, float bodyGapSeconds) {
            this(clip, volume, voices, voiceSeconds, bodyGapSeconds, 1f);
        }

        Sound(String clip, float volume, int voices, float voiceSeconds, float bodyGapSeconds, float pitch) {
            id = PREFIX + clip;
            this.volume = volume;
            layering = new VoicePool.Layering(voices, voiceSeconds, bodyGapSeconds);
            this.pitch = pitch;
        }
    }

    /** Where a held voice starts, and whose voice it is. */
    record Spot(float x, float y, UnitType strain, SwarmStage stage) { }

    static final class Mix {
        private int tick = -1;
        final VoicePool<Sound, Spot> voices = new VoicePool<>(Sound.class, sound -> sound.layering);
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
                voices.clear();
                gait.clear();
                health.clear();
            }
            tick = current;
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
    private record Step(Sound sound, long body, float x, float y, float distanceSquared, UnitType strain,
                        SwarmStage stage) { }

    private SwarmAudio() { }

    /**
     * Called every rendered frame with the listener's cell; plays each tick's
     * sounds once.
     */
    public static void play(BattleSimulation sim, float listenerX, float listenerY) {
        HarrierAudio.play(sim, listenerX, listenerY);
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        if (!mix.frame(sim.getSimTickIndex())) return;
        int now = sim.getSimTickIndex();
        for (VoicePool.Held<Sound, Spot> h : mix.voices.due(now)) {
            Spot at = h.payload();
            emit(sim, mix, h.sound(), 0L, at.x(), at.y(), now, at.strain(), at.stage(), h.heldFor());
        }
        for (SwarmCues.Cue cue : sim.swarmCues().cuesThisFrame())
            emit(sim, mix, sound(cue.kind(), cue.strain()), cue.body(), cue.x(), cue.y(), now, cue.strain(),
                    cue.stage(), 0);
        LongList deaths = sim.getDeathsThisFrame();
        for (int i = 0; i < deaths.size(); i++) {
            long body = deaths.getLong(i);
            UnitType type = sim.identity().type(body);
            if (!type.isSwarm()) continue;
            emit(sim, mix, death(type), body, sim.world().renderX(body), sim.world().renderY(body), now,
                    type, sim.identity().swarmStage(body), 0);
        }
        bodies(sim, mix, listenerX, listenerY, now);
    }

    /** Footfalls and pain off every living swarm body's gait and health. */
    private static void bodies(BattleSimulation sim, Mix mix, float listenerX, float listenerY, int now) {
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
            if (hp > 0f && priorHp - hp >= PAIN_THRESHOLD) emit(sim, mix, pain(type), body, x, y, now, type, stage, 0);
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
            if (step != null) steps.add(new Step(step, body, x, y, d2, type, stage));
        }
        mix.settle();
        steps.sort(Comparator.comparingDouble(Step::distanceSquared));
        for (int i = 0; i < Math.min(FOOTFALLS_PER_FRAME, steps.size()); i++) {
            Step step = steps.get(i);
            emit(sim, mix, step.sound(), step.body(), step.x(), step.y(), now, step.strain(), step.stage(), 0);
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
        // A thrown body's landing is the body's own, whatever it is and whatever threw it.
        if (kind == SwarmCues.Kind.THROWN) return Sound.BODY_THUD;
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

    /** Offers one voice to the battle's pool and plays it if it starts; {@code heldFor} is ticks it has waited. */
    private static void emit(BattleSimulation sim, Mix mix, Sound sound, long body, float x, float y, int tick,
                             UnitType strain, SwarmStage stage, int heldFor) {
        if (sound == null || !sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y))) return;
        if (mix.voices.offer(sound, body, tick, new Spot(x, y, strain, stage), heldFor) != VoicePool.Admit.START) return;
        float pitch = sound.pitch * pitch(strain, stage) * (1f + ThreadLocalRandom.current().nextFloat(-PITCH_JITTER, PITCH_JITTER));
        Global.getSoundPlayer().playSound(sound.id, pitch, sound.volume * volume(stage) * mix.voices.gain(sound),
                new Vector2f(x * BattleShotAudio.WORLD_UNITS_PER_CELL, y * BattleShotAudio.WORLD_UNITS_PER_CELL),
                new Vector2f(0f, 0f));
    }
}
