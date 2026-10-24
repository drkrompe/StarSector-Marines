package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * What swarm bodies ask the presentation to sound: a cry when a body takes a
 * new quarry, a strike, a blow on a wall and a wall giving way, and the acts
 * that belong to stories still landing: each phase of a Brute's charge (the
 * tell, the run, what the run hits, the Brute stunned by a heavy wall or a
 * tank) and a Runner's impale and drag. Deaths, pain and footfalls are not
 * cues: the presentation reads them off the battle's deaths this frame, each
 * body's health and its gait phase.
 *
 * <p>Strain behaviours run in the parallel unit update, so posting is
 * thread-safe. The simulation only records here and never reads back, so the
 * order posts arrive in reaches nothing but the speakers, a headless run stays
 * silent, and nothing presented reaches the simulation. Requests last one
 * advance ({@link #beginFrame()}), as {@link BoreCues} do.
 */
public final class SwarmCues {

    /** What happened. */
    public enum Kind {
        ALERT, STRIKE, WALL_BLOW, WALL_BREAK,
        CHARGE_TELL, CHARGE_RUN, CHARGE_HIT_BODY, CHARGE_HIT_VEHICLE, CHARGE_HIT_WALL, STUNNED,
        IMPALE, DRAG,
        /** A pack leader died and its pack broke: one scatter shriek for the pack, posted by the leader. */
        PACK_BROKEN,
        /** The Matriarch stepped up a growth stage: her plates split and darken, and she roars. */
        MOULT,
        /** The Matriarch out of the mother bore: her first roar. */
        ROAR,
        /** A Matriarch's scythe begins its sweep; the whoosh leads the cut. */
        REAP,
        /**
         * A thrown body hit the ground: one a Brute trampled, a Matriarch's reap threw or a Harrier dropped. Posted by
         * the body that landed, not by what threw it, so the thud is the body's.
         */
        THROWN
    }

    /**
     * One act by {@code body}, of {@code strain} at {@code stage}, at a world position; the stage pitches its voice,
     * and the body keeps one body from repeating itself.
     */
    public record Cue(Kind kind, long body, UnitType strain, SwarmStage stage, float x, float y) { }

    private final ConcurrentLinkedQueue<Cue> frame = new ConcurrentLinkedQueue<>();
    private final Collection<Cue> view = Collections.unmodifiableCollection(frame);

    /** Sound requests since the last {@link #beginFrame()}. */
    public Collection<Cue> cuesThisFrame() { return view; }

    /** Forgets last advance's requests. */
    public void beginFrame() { frame.clear(); }

    /** Thread-safe: {@code body} did {@code kind} where it stands. */
    public void post(Kind kind, long body, BattleSimulation sim) {
        frame.add(new Cue(kind, body, sim.identity().type(body), sim.identity().swarmStage(body),
                sim.world().x(body), sim.world().y(body)));
    }

    /** Thread-safe: {@code body} did {@code kind} to the cell at {@code cellX, cellY}. */
    public void postAt(Kind kind, long body, int cellX, int cellY, BattleSimulation sim) {
        frame.add(new Cue(kind, body, sim.identity().type(body), sim.identity().swarmStage(body),
                cellX + 0.5f, cellY + 0.5f));
    }
}
