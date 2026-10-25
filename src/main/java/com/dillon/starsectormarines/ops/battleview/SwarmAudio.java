package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.evacuation.SwarmCues;
import com.dillon.starsectormarines.battle.evacuation.SwarmHarvest;
import com.dillon.starsectormarines.battle.evacuation.SwarmWorkers;
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
import java.util.SplittableRandom;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

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
 * <p>The skitterer, the nests' worker, has its own small sounds out of borrowed takes: a quick chitin patter on
 * every stride (the Harrier's wingbeat, which is the Runner's clatter cut short, played higher), a thin squeak when it
 * is hurt or scatters (the Runner's yelp, higher still), the Runner's death and the body drag, all a few dB under a
 * Runner, and while it stands at a mend, a burn, a tree or an outpost a soft wet working bed (the fissure's scratching
 * ambience, raised): a placed loop on the nearest workers at it, at most {@link #WORK_LOOPS} at once, so a busy nest
 * murmurs and does not drone. It never cries or strikes.
 *
 * <p>A body fed to a bore ({@link SwarmHarvest#feeding}) is swallowed as it reaches the mouth: a wet gulp (the
 * Spitter's lob, slowed) and, a beat later, the thud of it landing down inside (the body thud, deeper), pitched by the
 * fed body's size like everything else.
 *
 * <p>The Spitter has no voice. The Matriarch has her own roar (out of the bore
 * and at each moult) and reap; her bellow, hoof, crown, wall-break, pain and
 * death are the Brute's takes played deeper and slower through the pitch
 * argument, not baked copies. The Harrier, which flies with its pack rather than walking, is
 * {@link HarrierAudio}'s, and the brood spire it rises from {@link SpireAudio}'s, both played from here, as are the
 * marines' barks in the fight ({@link MarineBarks}).
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
    /** Ticks after a fed body is let go that it reaches the mouth and is swallowed. */
    static final int GULP_TICKS = Math.round(SwarmHarvest.FEED_SLIDE_SECONDS / BattleSimulation.TICK_DT);
    /** Ticks after the gulp that the body lands down inside. */
    static final int GULP_THUD_TICKS = Math.round(0.3f / BattleSimulation.TICK_DT);
    /** Skitterers heard at their work at once: each is one placed loop, nearest the listener first. */
    static final int WORK_LOOPS = 2;
    private static final Object[] WORK_LOOP_OWNERS = {new Object(), new Object()};

    // Presentation-only: per battle, the last tick played, gait and health last seen, and the voices.
    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /**
     * One sound a strain makes: its take, the level one voice plays at alone, how many voices of it may sound at once
     * and how long each counts as sounding, the shortest gap before one body repeats it (0 for none), and for a
     * borrowed take the pitch it plays at.
     *
     * <p>Levels are set from each take's measured loudness, as the Harrier's are: the generated takes run hot and
     * differ by some 30 dB, and at their own level a Runner pack's cries and deaths sat 10 to 15 dB over the rifles
     * killing it. One adult voice now lands near the gunfire's -24 to -28 LUFS: a Runner's cry, bite, pain and death
     * near -25, a Brute's a few dB heavier, its charge tell over the guns so the charge is heard coming, and the
     * Matriarch, the one boss, -15 to -21. Footfalls sit well under, since many bodies make them.
     */
    enum Sound {
        RUNNER_CRY("runner_chitter", 0.4f, 4, 1.2f, 2f),
        RUNNER_GALLOP("runner_gallop", 0.5f, 6, 0.5f, 0f),
        RUNNER_CLATTER("runner_clatter", 0.68f, 3, 0.6f, 0f),
        RUNNER_STRIKE("runner_attack", 0.22f, 5, 0.8f, 0.4f),
        RUNNER_IMPALE("runner_impale", 0.32f, 3, 1.4f, 1f),
        RUNNER_PAIN("runner_pain", 0.11f, 4, 0.6f, 0.4f),
        RUNNER_DEATH("runner_death", 0.125f, 6, 1.2f, 0f),
        BODY_LATCH("body_latch", 0.5f, 3, 0.6f, 0.6f),
        BODY_DRAG("body_drag", 0.48f, 3, 1.9f, 1.9f),
        BODY_THUD("body_thud", 0.55f, 5, 0.8f, 0.5f),
        BRUTE_BELLOW("brute_bellow", 0.23f, 2, 2.5f, 4f),
        BRUTE_FOOTFALL("brute_footfall", 0.41f, 4, 0.6f, 0f),
        BRUTE_IMPACT("brute_impact", 0.25f, 3, 1.2f, 0.4f),
        BRUTE_WALL_BREAK("brute_wall_break", 0.27f, 2, 2.5f, 0f),
        BRUTE_CHARGE_TELL("brute_charge_tell", 0.4f, 2, 0.9f, 0f),
        BRUTE_CHARGE_RUN("brute_charge", 0.27f, 2, 2.5f, 0f),
        BRUTE_CHARGE_HIT_BODY("brute_charge_hit_body", 0.56f, 3, 1.2f, 0f),
        BRUTE_CHARGE_HIT_VEHICLE("brute_charge_hit_vehicle", 0.22f, 2, 1.5f, 0f),
        BRUTE_CHARGE_HIT_WALL("brute_charge_hit_wall", 0.28f, 2, 1.5f, 0f),
        BRUTE_STUN("brute_stun", 0.2f, 2, 1.8f, 0f),
        BRUTE_PAIN("brute_pain", 0.15f, 3, 0.9f, 0.6f),
        BRUTE_DEATH("brute_death", 0.21f, 3, 2.5f, 0f),
        // The Matriarch: her own roar and reap; everything else is a Brute take played a size deeper at
        // runtime, so a variant costs no file (colony-breach-nouns.md, Sound). Her death is the Brute's
        // slowed and deepened by the same pitch argument. There is one of her, so her voices hardly layer.
        MATRIARCH_ROAR("matriarch_roar", 0.58f, 1, 4f, 0f),
        MATRIARCH_REAP("matriarch_reap", 0.76f, 2, 1.3f, 0f),
        MATRIARCH_DEATH("brute_death", 0.41f, 1, 3f, 0f, 0.62f),
        MATRIARCH_BELLOW("brute_bellow", 0.35f, 1, 3.5f, 0f, 0.72f),
        // The tell before her run: the Brute's charge tell a size deeper, -16 to -19 LUFS across its takes.
        MATRIARCH_RUN_TELL("brute_charge_tell", 0.45f, 1, 1.5f, 0f, 0.7f),
        MATRIARCH_FOOTFALL("brute_footfall", 0.78f, 4, 1f, 0f, 0.65f),
        MATRIARCH_CROWN("brute_impact", 0.35f, 2, 2f, 0f, 0.75f),
        MATRIARCH_WALL_BREAK("brute_wall_break", 0.35f, 2, 3f, 0f, 0.8f),
        MATRIARCH_PAIN("brute_pain", 0.2f, 1, 1.3f, 1.2f, 0.72f),
        // The skitterer, on top of its size's pitch (1/0.68), a few dB under a Runner: patter near -39 LUFS a voice,
        // squeak near -30, death -28, drag -35, and each work loop -36. The squeak is its hurt and its scatter both,
        // so one body's gap keeps a hit that sends it running to one squeak.
        WORKER_PATTER("harrier_wingbeat", 0.3f, 4, 0.18f, 0f, 1.15f),
        WORKER_SQUEAK("runner_pain", 0.08f, 4, 0.45f, 1.2f, 1.3f),
        WORKER_DEATH("runner_death", 0.07f, 4, 0.9f, 0f),
        WORKER_DRAG("body_drag", 0.33f, 2, 1.5f, 1.5f),
        // A body going down a bore's mouth: a gulp near -29 LUFS and its landing inside near -33, under a Runner.
        BORE_GULP("spitter_lob", 0.13f, 3, 1.6f, 0f, 0.6f),
        BORE_GULP_THUD("body_thud", 0.16f, 3, 1.1f, 0f, 0.7f),
        /** A placed loop, refreshed every frame while a skitterer is at its work ({@link #workLoops}); never layered. */
        WORKER_WORK("bore_ambience_fissure", 0.35f, WORK_LOOPS, 0f, 0f);

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

    /**
     * A sound to start, where, and at what pitch and level (stage, layering and jitter included); {@code loop} is the
     * slot of a placed loop held this tick ({@code -1} for a sound played once).
     */
    public record Play(Sound sound, float x, float y, float pitch, float volume, int loop) {
        public String soundId() { return sound.id; }
    }

    /** Per battle: the last tick heard, gait and health last seen, and the voices. */
    public static final class Mix {
        private int tick = -1;
        /** The pitch jitter's source: the thread's own in a battle; seeded where a review needs the same mix twice. */
        private RandomGenerator jitter;
        final VoicePool<Sound, Spot> voices = new VoicePool<>(Sound.class, sound -> sound.layering);
        // Readings by body, as float bits (fastutil-core has no float maps); absent reads NaN.
        private final Long2IntOpenHashMap gait = readings();
        private final Long2IntOpenHashMap health = readings();
        private final Long2IntOpenHashMap seenGait = readings();
        private final Long2IntOpenHashMap seenHealth = readings();
        // A skitterer's job by body, as its ordinal; absent reads -1.
        private final Long2IntOpenHashMap job = jobs();
        private final Long2IntOpenHashMap seenJob = jobs();
        private List<Play> loops = List.of();
        private int strides;

        private static Long2IntOpenHashMap jobs() {
            Long2IntOpenHashMap map = new Long2IntOpenHashMap();
            map.defaultReturnValue(-1);
            return map;
        }

        /** This mix with its pitch jitter drawn from {@code seed}, so the same battle starts the same sounds. */
        public Mix seeded(long seed) {
            jitter = new SplittableRandom(seed);
            return this;
        }

        float jitter() {
            RandomGenerator source = jitter != null ? jitter : ThreadLocalRandom.current();
            return source.nextFloat(-PITCH_JITTER, PITCH_JITTER);
        }

        private static Long2IntOpenHashMap readings() {
            Long2IntOpenHashMap map = new Long2IntOpenHashMap();
            map.defaultReturnValue(Float.floatToRawIntBits(Float.NaN));
            return map;
        }

        /**
         * What the swarm bodies make this tick, once per tick (empty on a tick already heard), on ground the player
         * has seen; {@link SwarmAudio#play} plays it, and a review mixes its soundtrack from it. The placed loops held
         * this tick are in it too, and stay in {@link #loops} until the next.
         */
        public List<Play> hear(BattleSimulation sim, float listenerX, float listenerY) {
            List<Play> out = new ArrayList<>();
            int prior = tick;
            if (!frame(sim.getSimTickIndex())) return out;
            int now = sim.getSimTickIndex();
            if (prior >= now) prior = now - 1;
            for (VoicePool.Held<Sound, Spot> h : voices.due(now)) {
                Spot at = h.payload();
                emit(out, sim, this, h.sound(), 0L, at.x(), at.y(), now, at.strain(), at.stage(), h.heldFor());
            }
            for (SwarmCues.Cue cue : sim.swarmCues().cuesThisFrame())
                emit(out, sim, this, sound(cue.kind(), cue.strain()), cue.body(), cue.x(), cue.y(), now, cue.strain(),
                        cue.stage(), 0);
            for (SwarmHarvest.Feed feed : sim.swarmHarvest().feeding()) {
                Sound gulp = gulp(feed.tick(), prior, now);
                if (gulp == null) continue;
                emit(out, sim, this, gulp, feed.body(), feed.mouthX(), feed.mouthY(), now,
                        sim.identity().type(feed.body()), sim.identity().swarmStage(feed.body()), 0);
            }
            LongList deaths = sim.getDeathsThisFrame();
            for (int i = 0; i < deaths.size(); i++) {
                long body = deaths.getLong(i);
                UnitType type = sim.identity().type(body);
                if (!type.isSwarm()) continue;
                emit(out, sim, this, death(type), body, sim.world().renderX(body), sim.world().renderY(body), now,
                        type, sim.identity().swarmStage(body), 0);
            }
            bodies(out, sim, this, listenerX, listenerY, now);
            loops = workLoops(sim, listenerX, listenerY);
            out.addAll(loops);
            return out;
        }

        /** The placed loops of the last tick heard, to refresh every rendered frame. */
        public List<Play> loops() { return loops; }

        boolean frame(int current) {
            if (current == tick) return false;
            if (current < tick) {
                voices.clear();
                gait.clear();
                health.clear();
                job.clear();
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
            job.clear();
            job.putAll(seenJob);
            seenJob.clear();
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
        SpireAudio.play(sim, listenerX, listenerY);
        MarineBarks.play(sim);
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        for (Play p : mix.hear(sim, listenerX, listenerY)) {
            if (p.loop() >= 0) continue;
            Global.getSoundPlayer().playSound(p.soundId(), p.pitch(), p.volume(), at(p), new Vector2f(0f, 0f));
        }
        // a loop must be refreshed each frame or it stops
        for (Play p : mix.loops()) {
            Global.getSoundPlayer().playLoop(p.soundId(), WORK_LOOP_OWNERS[p.loop()], p.pitch(), p.volume(), at(p),
                    new Vector2f(0f, 0f));
        }
    }

    private static Vector2f at(Play p) {
        return new Vector2f(p.x() * BattleShotAudio.WORLD_UNITS_PER_CELL, p.y() * BattleShotAudio.WORLD_UNITS_PER_CELL);
    }

    /** Footfalls and pain off every living swarm body's gait and health. */
    private static void bodies(List<Play> out, BattleSimulation sim, Mix mix, float listenerX, float listenerY,
                               int now) {
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
            if (hp > 0f && priorHp - hp >= PAIN_THRESHOLD) emit(out, sim, mix, pain(type), body, x, y, now, type, stage,
                    0);
            if (type == UnitType.SWARM_WORKER) {
                SwarmWorkers.Worker worker = sim.swarmWorkers().of(body);
                if (worker != null) {
                    mix.seenJob.put(body, worker.job().ordinal());
                    if (scattered(mix.job.get(body), worker.job()))
                        emit(out, sim, mix, Sound.WORKER_SQUEAK, body, x, y, now, type, stage, 0);
                }
            }
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
            emit(out, sim, mix, step.sound(), step.body(), step.x(), step.y(), now, step.strain(), step.stage(), 0);
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
        // a skitterer's patter is on every stride, small and quick; a Runner's falls on every other
        if (strain == UnitType.SWARM_WORKER) return carrying ? Sound.WORKER_DRAG : Sound.WORKER_PATTER;
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
                case CHARGE_TELL -> Sound.MATRIARCH_RUN_TELL;
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

    /**
     * What a body let go into a mouth at {@code fedTick} makes in the ticks after {@code prior} up to {@code now}: the
     * gulp as it reaches the mouth, then its landing inside, or nothing.
     */
    static Sound gulp(int fedTick, int prior, int now) {
        int at = fedTick + GULP_TICKS;
        if (at > prior && at <= now) return Sound.BORE_GULP;
        at += GULP_THUD_TICKS;
        if (at > prior && at <= now) return Sound.BORE_GULP_THUD;
        return null;
    }

    /**
     * Whether a skitterer has just scattered: it is fleeing and was last heard doing something else. One first heard
     * already fleeing ({@code priorJob} {@code -1}) is not a new scatter.
     */
    static boolean scattered(int priorJob, SwarmWorkers.Job job) {
        return job == SwarmWorkers.Job.FLEE && priorJob >= 0 && priorJob != SwarmWorkers.Job.FLEE.ordinal();
    }

    /** The work loops this tick: the seen skitterers head-down at their work nearest the listener, in range. */
    private static List<Play> workLoops(BattleSimulation sim, float listenerX, float listenerY) {
        List<float[]> at = new ArrayList<>();
        for (SwarmWorkers.Worker w : sim.swarmWorkers().workers()) {
            if (sim.swarmWorkers().show(w.id) != SwarmWorkers.Show.WORKING) continue;
            float x = sim.world().renderX(w.id);
            float y = sim.world().renderY(w.id);
            if (sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y))) at.add(new float[]{x, y});
        }
        return workLoops(at, listenerX, listenerY);
    }

    /**
     * Where the work loops sit among the skitterers at work ({@code {x, y}} each): on the nearest
     * {@link #WORK_LOOPS} within footfall range, each later loop quieter by the layering rule, so more workers are a
     * thicker murmur than one and never a drone.
     */
    static List<Play> workLoops(List<float[]> working, float listenerX, float listenerY) {
        List<float[]> near = new ArrayList<>();
        for (float[] p : working) {
            float dx = p[0] - listenerX, dy = p[1] - listenerY;
            if (dx * dx + dy * dy <= FOOTFALL_RANGE_CELLS * FOOTFALL_RANGE_CELLS) near.add(p);
        }
        near.sort(Comparator.comparingDouble(p -> (p[0] - listenerX) * (p[0] - listenerX)
                + (p[1] - listenerY) * (p[1] - listenerY)));
        Sound work = Sound.WORKER_WORK;
        List<Play> out = new ArrayList<>();
        for (int i = 0; i < Math.min(WORK_LOOPS, near.size()); i++) {
            float[] p = near.get(i);
            out.add(new Play(work, p[0], p[1], work.pitch * pitch(UnitType.SWARM_WORKER, SwarmStage.ADULT),
                    work.volume * VoicePool.layerGain(i + 1), i));
        }
        return out;
    }

    static Sound pain(UnitType strain) {
        if (strain == UnitType.SWARM_WORKER) return Sound.WORKER_SQUEAK;
        if (strain == UnitType.SWARM_RUNNER) return Sound.RUNNER_PAIN;
        if (strain == UnitType.SWARM_MATRIARCH) return Sound.MATRIARCH_PAIN;
        if (heavy(strain)) return Sound.BRUTE_PAIN;
        return null;
    }

    static Sound death(UnitType strain) {
        if (strain == UnitType.SWARM_WORKER) return Sound.WORKER_DEATH;
        if (strain == UnitType.SWARM_RUNNER) return Sound.RUNNER_DEATH;
        if (strain == UnitType.SWARM_MATRIARCH) return Sound.MATRIARCH_DEATH;
        if (heavy(strain)) return Sound.BRUTE_DEATH;
        return null;
    }

    /**
     * A body's pitch by its stage: the inverse of its drawn size, so a whelp is about four semitones up; a skitterer,
     * smaller than any whelp, higher still.
     */
    static float pitch(UnitType strain, SwarmStage stage) {
        return 1f / (SwarmStage.of(stage).scale(strain) * (strain == UnitType.SWARM_WORKER ? strain.renderScale : 1f));
    }

    /** A body's loudness by its stage, against the adult's. */
    static float volume(SwarmStage stage) {
        return switch (SwarmStage.of(stage)) {
            case WHELP -> 0.85f;
            case ADULT -> 1f;
            case ELDER -> 1.15f;
        };
    }

    /** Offers one voice to the battle's pool and adds it to {@code out} if it starts; {@code heldFor} is ticks it has waited. */
    private static void emit(List<Play> out, BattleSimulation sim, Mix mix, Sound sound, long body, float x, float y,
                             int tick, UnitType strain, SwarmStage stage, int heldFor) {
        if (sound == null || !sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y))) return;
        if (mix.voices.offer(sound, body, tick, new Spot(x, y, strain, stage), heldFor) != VoicePool.Admit.START) return;
        float pitch = sound.pitch * pitch(strain, stage) * (1f + mix.jitter());
        out.add(new Play(sound, x, y, pitch, sound.volume * volume(stage) * mix.voices.gain(sound), -1));
    }
}
