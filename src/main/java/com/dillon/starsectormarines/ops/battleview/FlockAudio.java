package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A derelict flock's sound (`derelict-drone-flocks.md`, slice 7): the swarm whine, one placed loop on each flock
 * within earshot, read off the flocks rather than posted by them, so the simulation stays silent.
 *
 * <p>A flock is heard as one body, not fourteen machines: the loop sits on its members' middle, rises in pitch with
 * the flock's speed (a flock pouring down a street or wrapping a target whines higher than one holding at its
 * cradle), and swells with its numbers, so a flock shot down to a few thins out. The nearest {@link #LOOPS} flocks are
 * heard, each further one quieter by the layering rule ({@link VoicePool#layerGain}), on ground the player has seen.
 */
public final class FlockAudio {

    public static final String SOUND_ID = "marines_flock_whine";
    /** Flocks heard at once: each is one placed loop, nearest the listener first. */
    static final int LOOPS = 3;
    /** Flocks further from the listener than this are not heard. */
    static final float RANGE_CELLS = 40f;
    /**
     * One whole flock's level. The take sits near -20 LUFS; at this level a flock of fourteen holds its own against a
     * firefight's gunfire rather than sinking under it: it is the alarm the flock raises.
     */
    static final float VOLUME = 0.9f;
    /** The members at which a flock is heard at its full level. */
    static final int FULL_MEMBERS = 14;
    /** The pitch at rest and at {@link #FULL_SPEED} cells a second, the flight's top speed. */
    static final float PITCH_REST = 0.88f, PITCH_FULL = 1.18f, FULL_SPEED = 6f;

    private static final Object[] LOOP_OWNERS = {new Object(), new Object(), new Object()};
    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    /** A flock heard this tick: which, where its members' middle is, their mean speed, and how many fly. */
    record Swarm(int id, float x, float y, float speed, int members) { }

    /** The loop to hold this tick, where, and at what pitch and level; {@code loop} is its slot. */
    public record Play(float x, float y, float pitch, float volume, int loop) {
        public String soundId() { return SOUND_ID; }
    }

    /** Per battle: the last tick heard, and which flock each loop slot is held on. */
    public static final class Mix {
        private int tick = -1;
        /** The flock each slot is held on, as its id + 1; 0 for a free slot. */
        private final long[] slots = new long[LOOPS];
        private List<Play> loops = List.of();

        /** The loops held this tick, once per tick; a tick already heard returns the same loops. */
        public List<Play> hear(BattleSimulation sim, float listenerX, float listenerY) {
            int now = sim.getSimTickIndex();
            if (now == tick) return loops;
            if (now < tick) Arrays.fill(slots, 0L);
            tick = now;
            World world = sim.world();
            List<Swarm> swarms = new ArrayList<>();
            for (Flock flock : sim.flocks().flocks()) {
                float sx = 0f, sy = 0f, speed = 0f;
                int n = 0;
                for (long m : flock.memberIds()) {
                    if (!world.isAlive(m)) continue;
                    sx += world.renderX(m);
                    sy += world.renderY(m);
                    AirBody body = world.kinematics(m);
                    if (body != null) speed += (float) Math.hypot(body.vx, body.vy);
                    n++;
                }
                if (n == 0) continue;
                float x = sx / n, y = sy / n;
                if (!sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y))) continue;
                swarms.add(new Swarm(flock.id(), x, y, speed / n, n));
            }
            loops = loops(swarms, listenerX, listenerY, slots);
            return loops;
        }
    }

    private FlockAudio() { }

    /** Called every rendered frame with the listener's cell; a loop must be refreshed each frame or it stops. */
    public static void play(BattleSimulation sim, float listenerX, float listenerY) {
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        Vector2f still = new Vector2f(0f, 0f);
        for (Play p : mix.hear(sim, listenerX, listenerY)) {
            Global.getSoundPlayer().playLoop(SOUND_ID, LOOP_OWNERS[p.loop()], p.pitch(), p.volume(),
                    new Vector2f(p.x() * BattleShotAudio.WORLD_UNITS_PER_CELL,
                            p.y() * BattleShotAudio.WORLD_UNITS_PER_CELL), still);
        }
    }

    /**
     * The loops among the flocks: on the nearest {@link #LOOPS} within range, each later one quieter by the layering
     * rule. A flock keeps the slot it was heard on ({@code slots}, updated here) while it stays among the nearest, so a
     * loop never jumps from one flock to another; a newly heard one takes a free slot.
     */
    static List<Play> loops(List<Swarm> swarms, float listenerX, float listenerY, long[] slots) {
        List<Swarm> near = new ArrayList<>();
        for (Swarm s : swarms) {
            float dx = s.x() - listenerX, dy = s.y() - listenerY;
            if (dx * dx + dy * dy <= RANGE_CELLS * RANGE_CELLS) near.add(s);
        }
        near.sort(Comparator.comparingDouble(s -> (s.x() - listenerX) * (s.x() - listenerX)
                + (s.y() - listenerY) * (s.y() - listenerY)));
        List<Swarm> heard = near.subList(0, Math.min(LOOPS, near.size()));
        for (int i = 0; i < slots.length; i++) {
            long held = slots[i];
            if (held != 0L && heard.stream().noneMatch(s -> s.id() + 1L == held)) slots[i] = 0L;
        }
        List<Play> out = new ArrayList<>();
        for (int i = 0; i < heard.size(); i++) {
            Swarm s = heard.get(i);
            int slot = slotOf(slots, s.id() + 1L);
            if (slot < 0) {
                slot = slotOf(slots, 0L);
                slots[slot] = s.id() + 1L;
            }
            out.add(new Play(s.x(), s.y(), pitch(s.speed()), volume(s.members()) * VoicePool.layerGain(i + 1),
                    slot));
        }
        return out;
    }

    /** The whine's pitch at a flock's mean speed, cells a second: rising from rest to the flight's top speed. */
    static float pitch(float speed) {
        float t = Math.max(0f, Math.min(1f, speed / FULL_SPEED));
        return PITCH_REST + (PITCH_FULL - PITCH_REST) * t;
    }

    /** A flock's level by how many of it fly: thinning as it is shot down, full at {@link #FULL_MEMBERS}. */
    static float volume(int members) {
        return VOLUME * (float) Math.sqrt(Math.min(members, FULL_MEMBERS) / (float) FULL_MEMBERS);
    }

    private static int slotOf(long[] slots, long held) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == held) return i;
        return -1;
    }
}
