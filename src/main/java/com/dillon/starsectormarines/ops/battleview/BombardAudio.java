package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.evacuation.SwarmBombards;
import com.dillon.starsectormarines.battle.evacuation.SporeHazes;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.fs.starfarer.api.Global;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Bombard's sound (`bombard.md`), read off what the battle shows rather than posted by it, so the simulation stays
 * silent:
 * <ul>
 *   <li>The <b>wind-up</b> as its tail starts to curl ({@link SwarmBombards#windingUp}): a slow swelling groan of strain
 *       (the bore's growth, which tears and stretches for the three seconds the wind-up takes) and over it a chitin
 *       creak at each quarter of the curl, deeper than a Runner's clatter and tightening as it goes, so the throw is
 *       heard coming.</li>
 *   <li>The <b>whip</b> as the glob is flung (its shot leaving): the tail's whoosh, the sling's hinge cracking over and
 *       the glob's wet slap, one short take of its own cut from a Matriarch reap, a Runner clatter and a Spitter lob.</li>
 *   <li>The <b>burst</b> where the glob lands (its shot arriving): a soft heavy wet pop, a spore vent's puff played low
 *       over a body thud. A Bombard killed bursts its sac the same way, quieter and lower.</li>
 *   <li>The <b>haze</b> while one stands ({@link SmokeFieldService#activeHazes}): a low spore hiss, a placed loop of its
 *       own cut from the vent's hissing cloud, on the hazes nearest the listener, at most {@link #HAZE_LOOPS} at once,
 *       swelling briefly at each sting ({@link SporeHazes#PULSE_SECONDS}) and fading as it thins.</li>
 * </ul>
 * It is the heaviest Rav after the Matriarch, so every borrowed take plays deeper than the Spitter's (its claw-up
 * {@link BoreAudio#pitch}), and its growth stage pitches it as it does every Rav ({@link SwarmAudio#pitch}). Heard only
 * where the map has been seen; the whip also where its own launch gives it away ({@link SwarmBombards#launchReveals}).
 *
 * <p>Levels are set from each take's measured loudness against the gunfire's -24 to -28 LUFS a voice: the wind-up, a
 * tell, near -27 with its creaks a little under, the whip and the burst near -27, a death burst near -32, and one
 * standing haze well under the fight at -38, -33 at the top of a sting.
 */
public final class BombardAudio {

    static final String PREFIX = SwarmAudio.PREFIX;
    static final String GLOB_WEAPON = "weapon.swarm-bombard";
    /** The Bombard's own pitch, below the Spitter's: every borrowed take plays this much deeper. */
    static final float PITCH = BoreAudio.pitch(UnitType.SWARM_BOMBARD);
    static final float PITCH_JITTER = 0.05f;
    /** Hazes heard at once: each is one placed loop, nearest the listener first. */
    static final int HAZE_LOOPS = 2;
    /** Hazes further from the listener than this are not heard. */
    static final float HAZE_RANGE_CELLS = 24f;
    /** How much a sting lifts a haze's hiss at its top, and how fast the lift dies away (seconds). */
    static final float STING_SWELL = 0.8f, STING_DECAY_SECONDS = 0.45f;
    /** Shares of the wind-up at which the tail creaks, and the pitch each plays at against the Bombard's. */
    static final float[] CREAKS = {0.05f, 0.3f, 0.55f, 0.8f};
    private static final float[] CREAK_PITCH = {0.7f, 0.76f, 0.82f, 0.9f};
    private static final float[] CREAK_VOLUME = {0.85f, 0.9f, 0.95f, 1f};
    private static final Object[] HAZE_LOOP_OWNERS = {new Object(), new Object()};

    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /**
     * One sound: its take, the level one voice plays at alone, how many may sound at once and for how long each counts,
     * one Bombard's own gap, and the pitch its take plays at against the Bombard's.
     */
    enum Sound {
        /** The bore's growth: -26 to -27 LUFS. Played a little higher than the Bombard so it lasts the wind-up. */
        WINDUP("bore_growth", 0.9f, 3, 3f, 2f, 1.15f),
        /** A Runner's clatter, -32 to -38 LUFS rising across the curl; its quietest take lower still. */
        CREAK("runner_clatter", 1f, 4, 0.4f, 0f, 1f),
        /** Its own take, levelled to the guns: about -27 LUFS. */
        WHIP("bombard_whip", 1f, 3, 0.5f, 0f, 1f / BoreAudio.pitch(UnitType.SWARM_BOMBARD)),
        /** A spore vent's puff, about -27 LUFS, over a body thud near -33. */
        BURST("spore_vent_burst", 0.16f, 3, 1.5f, 0f, 1f),
        BURST_THUD("body_thud", 0.2f, 3, 0.8f, 0f, 0.85f),
        /** The same burst, quieter and lower, as a dead Bombard's sac goes: about -32 LUFS. */
        DEATH_BURST("spore_vent_burst", 0.09f, 2, 1.5f, 0f, 0.85f),
        DEATH_THUD("body_thud", 0.14f, 2, 0.8f, 0f, 0.75f),
        /** A placed loop, refreshed every frame while a haze stands ({@link #hazeLoops}); its take sits at -20 LUFS. */
        HAZE("bombard_haze", 0.13f, HAZE_LOOPS, 0f, 0f, 1f / BoreAudio.pitch(UnitType.SWARM_BOMBARD));

        final String id;
        final float volume;
        final VoicePool.Layering layering;
        final float pitch;

        Sound(String clip, float volume, int voices, float voiceSeconds, float bodyGapSeconds, float pitch) {
            this.id = PREFIX + clip;
            this.volume = volume;
            this.layering = new VoicePool.Layering(voices, voiceSeconds, bodyGapSeconds);
            this.pitch = pitch;
        }
    }

    /**
     * A sound to start, where, and at what pitch and level (stage, layering and jitter included); {@code loop} is the
     * slot of a placed loop held this tick ({@code -1} for a sound played once).
     */
    public record Play(Sound sound, float x, float y, float pitch, float volume, int loop) {
        public String soundId() { return sound.id; }
    }

    /** A haze heard this tick: which it is, where it stands, its age, and how much of it is left. */
    record Haze(long id, float x, float y, float age, float left) { }

    /** Per battle: the last tick heard, how far into its wind-up each Bombard was, and the voices. */
    public static final class Mix {
        private int tick = -1;
        private int played;
        private final VoicePool<Sound, float[]> voices = new VoicePool<>(Sound.class, sound -> sound.layering);
        // Each winding Bombard's share of its wind-up last tick, as float bits; absent reads NaN.
        private final Long2IntOpenHashMap windup = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap seenWindup = new Long2IntOpenHashMap();
        private List<Play> loops = List.of();
        /** The haze each loop slot is held on, 0 for a free slot. */
        private final long[] slots = new long[HAZE_LOOPS];

        public Mix() {
            windup.defaultReturnValue(Float.floatToRawIntBits(Float.NaN));
        }

        /**
         * What the Bombards make this tick, once per tick (empty on a tick already heard), on ground the player has
         * seen; {@link BombardAudio#play} plays it, and a review mixes its soundtrack from it. The haze loops held
         * this tick are in it too, and stay in {@link #loops} until the next.
         */
        public List<Play> hear(BattleSimulation sim, float listenerX, float listenerY) {
            int now = sim.getSimTickIndex();
            List<Play> out = new ArrayList<>();
            if (now == tick) return out;
            if (now < tick) reset();
            tick = now;
            for (VoicePool.Held<Sound, float[]> h : voices.due(now)) {
                float[] p = h.payload();
                emit(out, sim, h.sound(), 0L, p[0], p[1], p[2], h.heldFor(), true);
            }
            SwarmBombards bombards = sim.swarmBombards();
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long body = sim.liveUnitAt(i);
                if (sim.identity().type(body) != UnitType.SWARM_BOMBARD || !bombards.windingUp(body)) continue;
                float share = bombards.windupShare(body);
                float prior = Float.intBitsToFloat(windup.get(body));
                seenWindup.put(body, Float.floatToRawIntBits(share));
                float x = sim.world().renderX(body), y = sim.world().renderY(body);
                float pitch = stagePitch(sim, body);
                if (Float.isNaN(prior)) emit(out, sim, Sound.WINDUP, body, x, y, pitch, 0, false);
                int creak = creak(Float.isNaN(prior) ? -1f : prior, share);
                if (creak >= 0) emitCreak(out, sim, body, x, y, pitch, creak);
            }
            windup.clear();
            windup.putAll(seenWindup);
            seenWindup.clear();
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (!glob(shot)) continue;
                emit(out, sim, Sound.WHIP, shot.shooterId, shot.fromX, shot.fromY, stagePitch(sim, shot.shooterId), 0,
                        revealed(bombards, shot.fromX, shot.fromY));
            }
            for (ShotEvent shot : sim.getShotsExpiredThisFrame()) {
                if (!glob(shot)) continue;
                float pitch = stagePitch(sim, shot.shooterId);
                emit(out, sim, Sound.BURST, 0L, shot.toX, shot.toY, pitch, 0, false);
                emit(out, sim, Sound.BURST_THUD, 0L, shot.toX, shot.toY, pitch, 0, false);
            }
            LongList deaths = sim.getDeathsThisFrame();
            for (int i = 0; i < deaths.size(); i++) {
                long body = deaths.getLong(i);
                if (sim.identity().type(body) != UnitType.SWARM_BOMBARD) continue;
                float x = sim.world().renderX(body), y = sim.world().renderY(body);
                float pitch = stagePitch(sim, body);
                emit(out, sim, Sound.DEATH_BURST, body, x, y, pitch, 0, false);
                emit(out, sim, Sound.DEATH_THUD, body, x, y, pitch, 0, false);
            }
            List<Haze> hazes = new ArrayList<>();
            for (SmokeFieldService.SmokeFieldView v : sim.smokeFields().activeHazes()) {
                if (!sim.getFogOfWar().isCellRevealed((int) Math.floor(v.x()), (int) Math.floor(v.y()))) continue;
                hazes.add(new Haze(v.id(), v.x(), v.y(), v.totalDuration() - v.remaining(),
                        v.fullRadius() > 0f ? v.radius() / v.fullRadius() : 0f));
            }
            loops = hazeLoops(hazes, listenerX, listenerY, slots);
            out.addAll(loops);
            return out;
        }

        /** The haze loops of the last tick heard, to refresh every rendered frame. */
        public List<Play> loops() { return loops; }

        private void emitCreak(List<Play> out, BattleSimulation sim, long body, float x, float y, float pitch, int n) {
            if (!heard(sim, x, y) || voices.offer(Sound.CREAK, body, tick, new float[]{x, y, pitch}, 0)
                    != VoicePool.Admit.START) return;
            float p = Sound.CREAK.pitch * PITCH * CREAK_PITCH[n] * pitch * (1f + jitter(tick, played++));
            out.add(new Play(Sound.CREAK, x, y, p, Sound.CREAK.volume * CREAK_VOLUME[n] * voices.gain(Sound.CREAK), -1));
        }

        /**
         * Offers one voice to the pool and adds it to {@code out} if it starts; {@code pitch} is the body's stage
         * pitch, {@code given} true where the sound is heard whatever the fog shows (a launch giving itself away).
         */
        private void emit(List<Play> out, BattleSimulation sim, Sound sound, long body, float x, float y, float pitch,
                          int heldFor, boolean given) {
            if (!given && !heard(sim, x, y)) return;
            if (voices.offer(sound, body, tick, new float[]{x, y, pitch}, heldFor) != VoicePool.Admit.START) return;
            float p = sound.pitch * PITCH * pitch * (1f + jitter(tick, played++));
            out.add(new Play(sound, x, y, p, sound.volume * SwarmAudio.volume(stage(sim, body))
                    * voices.gain(sound), -1));
        }

        private void reset() {
            voices.clear();
            windup.clear();
            Arrays.fill(slots, 0L);
            played = 0;
        }
    }

    private BombardAudio() { }

    /** Called every rendered frame with the listener's cell: the one-shots once per tick, the haze loops every frame. */
    public static void play(BattleSimulation sim, float listenerX, float listenerY) {
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        Vector2f still = new Vector2f(0f, 0f);
        for (Play p : mix.hear(sim, listenerX, listenerY)) {
            if (p.loop() >= 0) continue;
            Global.getSoundPlayer().playSound(p.soundId(), p.pitch(), p.volume(), at(p), still);
        }
        // a loop must be refreshed each frame or it stops
        for (Play p : mix.loops()) {
            Global.getSoundPlayer().playLoop(p.soundId(), HAZE_LOOP_OWNERS[p.loop()], p.pitch(), p.volume(), at(p),
                    still);
        }
    }

    /**
     * Which creak of the wind-up its share crossed since the last reading ({@code prior} {@code -1} on the first),
     * or {@code -1} for none; several crossed in one reading are heard as the last of them.
     */
    static int creak(float prior, float share) {
        int crossed = -1;
        for (int i = 0; i < CREAKS.length; i++) if (prior < CREAKS[i] && share >= CREAKS[i]) crossed = i;
        return crossed;
    }

    /**
     * Where the haze loops sit among the standing hazes: on the nearest {@link #HAZE_LOOPS} within range, each later
     * one quieter by the layering rule, each at its sting's swell ({@link #swell}) and fading as it thins. A haze keeps
     * the slot it was heard on ({@code slots}, by haze id, updated here) while it stays among the nearest, so a loop
     * never jumps from one haze to another; a newly heard one takes a free slot.
     */
    static List<Play> hazeLoops(List<Haze> hazes, float listenerX, float listenerY, long[] slots) {
        List<Haze> near = new ArrayList<>();
        for (Haze h : hazes) {
            float dx = h.x() - listenerX, dy = h.y() - listenerY;
            if (dx * dx + dy * dy <= HAZE_RANGE_CELLS * HAZE_RANGE_CELLS) near.add(h);
        }
        near.sort(Comparator.comparingDouble(h -> (h.x() - listenerX) * (h.x() - listenerX)
                + (h.y() - listenerY) * (h.y() - listenerY)));
        List<Haze> heard = near.subList(0, Math.min(HAZE_LOOPS, near.size()));
        for (int s = 0; s < slots.length; s++) {
            long held = slots[s];
            if (held != 0L && heard.stream().noneMatch(h -> h.id() == held)) slots[s] = 0L;
        }
        Sound haze = Sound.HAZE;
        List<Play> out = new ArrayList<>();
        for (int i = 0; i < heard.size(); i++) {
            Haze h = heard.get(i);
            int slot = slotOf(slots, h.id());
            if (slot < 0) {
                slot = slotOf(slots, 0L);
                slots[slot] = h.id();
            }
            out.add(new Play(haze, h.x(), h.y(), haze.pitch * PITCH,
                    haze.volume * h.left() * swell(h.age()) * VoicePool.layerGain(i + 1), slot));
        }
        return out;
    }

    /**
     * A haze's hiss against its level between stings, {@code age} seconds after it burst: lifted by
     * {@link #STING_SWELL} at each sting (a whole {@link SporeHazes#PULSE_SECONDS}), dying away over
     * {@link #STING_DECAY_SECONDS}. The burst itself has its own sound, so the first lift is at the first pulse.
     */
    static float swell(float age) {
        if (age < SporeHazes.PULSE_SECONDS) return 1f;
        float since = age % SporeHazes.PULSE_SECONDS;
        return 1f + STING_SWELL * (float) Math.exp(-since / STING_DECAY_SECONDS);
    }

    private static int slotOf(long[] slots, long id) {
        for (int s = 0; s < slots.length; s++) if (slots[s] == id) return s;
        return -1;
    }

    private static boolean glob(ShotEvent shot) {
        return shot.mechWeaponDef != null && GLOB_WEAPON.equals(shot.mechWeaponDef.id);
    }

    /** Whether a launch from ({@code x}, {@code y}) is giving its cell away. */
    private static boolean revealed(SwarmBombards bombards, float x, float y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        for (SwarmBombards.Reveal r : bombards.launchReveals()) if (r.cellX() == cx && r.cellY() == cy) return true;
        return false;
    }

    private static boolean heard(BattleSimulation sim, float x, float y) {
        return sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y));
    }

    private static SwarmStage stage(BattleSimulation sim, long body) {
        return body != 0L ? sim.identity().swarmStage(body) : SwarmStage.ADULT;
    }

    /** A Bombard's pitch by its stage (an elder deeper, a whelp higher), or the adult's for one gone. */
    private static float stagePitch(BattleSimulation sim, long body) {
        return SwarmAudio.pitch(UnitType.SWARM_BOMBARD, stage(sim, body));
    }

    /** Seeded pitch jitter for the {@code n}th sound heard, at {@code tick}: within {@link #PITCH_JITTER}. */
    static float jitter(int tick, int n) {
        return HarrierAudio.jitter(tick, n) * (PITCH_JITTER / HarrierAudio.PITCH_JITTER);
    }

    private static Vector2f at(Play p) {
        return new Vector2f(p.x() * BattleShotAudio.WORLD_UNITS_PER_CELL, p.y() * BattleShotAudio.WORLD_UNITS_PER_CELL);
    }
}
