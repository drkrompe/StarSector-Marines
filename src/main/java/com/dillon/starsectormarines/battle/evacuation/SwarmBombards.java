package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The Bombards on the field (`bombard.md`): each one's stance, and the serial half of what they do.
 *
 * <p>A Bombard's behaviour runs in the parallel unit update and writes only its own {@link Stance}. The glob it
 * flings is drawn as an ordinary payloadless projectile, and the haze it bursts into is posted here with its
 * arrival tick: {@link #tick} bursts it in the serial pass through {@link SporeHazes#burst}, which detonates, so
 * it may not run on a worker. The same pass bursts a dead Bombard's sac into its last small haze, and frees its
 * bore to draw another.
 */
public final class SwarmBombards {

    /** Seconds to plant on creep, and to uproot off it. */
    static final float PLANT_SECONDS = 3f;
    /** Seconds the tail is cocked back before the glob is flung. */
    static final float WINDUP_SECONDS = 3f;
    /** The death burst's haze against a glob's. */
    static final float DEATH_HAZE_SCALE = 0.5f;

    /** What a Bombard is doing. */
    enum Phase { WALKING, PLANTING, PLANTED, WINDING_UP, UPROOTING }

    /** One Bombard's state, written only by its own update. */
    static final class Stance {
        Phase phase = Phase.WALKING;
        float timer;
        long aim;
        float lastX, lastY;
        float scale = 1f;
        /** The creep cell it is walking back to, or -1 for none chosen. */
        int creepX = -1, creepY = -1;
        /** The bore it came from, or {@code null} for one placed on the field. */
        SwarmBore bore;
    }

    /** A glob in the air that bursts at ({@code x}, {@code y}) once the sim reaches {@code arrivalTick}. */
    record Glob(long shooter, float x, float y, float scale, int arrivalTick) {}

    private static final Comparator<Glob> GLOB_ORDER = Comparator
            .comparingInt(Glob::arrivalTick).thenComparingLong(Glob::shooter);

    private final Map<Long, Stance> stances = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Glob> posted = new ConcurrentLinkedQueue<>();
    private final List<Glob> inFlight = new ArrayList<>();
    private final List<Long> dead = new ArrayList<>();
    private int globsLanded;
    private int deathBursts;

    /** A Bombard's stance, made on first sight. Thread-safe. */
    Stance stance(long body) {
        return stances.computeIfAbsent(body, id -> new Stance());
    }

    /** The stance of a Bombard on the field, or {@code null}. */
    Stance stanceIfPresent(long body) {
        return stances.get(body);
    }

    /** Serial: records that {@code body}, just released, is {@code bore}'s Bombard. */
    void released(long body, SwarmBore bore) {
        stance(body).bore = bore;
        bore.releasedBombard(body);
    }

    /** Thread-safe; called from a Bombard's update as it flings. */
    void postGlob(long shooter, float x, float y, float scale, int arrivalTick) {
        posted.add(new Glob(shooter, x, y, scale, arrivalTick));
    }

    /** Serial: bursts every glob that has landed, and every dead Bombard's sac. */
    public void tick(BattleSimulation sim) {
        if (stances.isEmpty() && inFlight.isEmpty() && posted.isEmpty()) return;
        int before = inFlight.size();
        for (Glob glob; (glob = posted.poll()) != null; ) inFlight.add(glob);
        if (inFlight.size() > before) inFlight.sort(GLOB_ORDER);
        int now = sim.getSimTickIndex();
        for (int i = 0; i < inFlight.size(); ) {
            Glob glob = inFlight.get(i);
            if (glob.arrivalTick() > now) {
                i++;
                continue;
            }
            inFlight.remove(i);
            sim.sporeHazes().burst(sim, glob.shooter(), glob.x(), glob.y(), glob.scale());
            globsLanded++;
        }
        dead.clear();
        for (Map.Entry<Long, Stance> entry : stances.entrySet()) {
            if (!sim.world().isAlive(entry.getKey())) dead.add(entry.getKey());
        }
        dead.sort(null);
        for (long body : dead) {
            Stance stance = stances.remove(body);
            sim.sporeHazes().burst(sim, body, stance.lastX, stance.lastY, stance.scale * DEATH_HAZE_SCALE);
            deathBursts++;
            if (stance.bore != null) stance.bore.lostBombard(body);
        }
    }

    /** Globs that have landed and burst, for evidence. */
    public int globsLanded() { return globsLanded; }

    /** Dead Bombards whose sac has burst, for evidence. */
    public int deathBursts() { return deathBursts; }

    /** Whether a Bombard is planted (winding up included), for evidence and the look. */
    public boolean planted(long body) {
        Stance stance = stances.get(body);
        return stance != null && (stance.phase == Phase.PLANTED || stance.phase == Phase.WINDING_UP);
    }

    /** Whether a Bombard has its tail cocked back for a lob, for evidence and the look. */
    public boolean windingUp(long body) {
        Stance stance = stances.get(body);
        return stance != null && stance.phase == Phase.WINDING_UP;
    }
}
