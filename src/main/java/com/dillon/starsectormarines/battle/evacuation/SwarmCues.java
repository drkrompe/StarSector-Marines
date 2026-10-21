package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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
        IMPALE, DRAG
    }

    /** One act by a body of {@code strain} at a world position. */
    public record Cue(Kind kind, UnitType strain, float x, float y) { }

    private final ConcurrentLinkedQueue<Cue> frame = new ConcurrentLinkedQueue<>();
    private final Collection<Cue> view = Collections.unmodifiableCollection(frame);

    /** Sound requests since the last {@link #beginFrame()}. */
    public Collection<Cue> cuesThisFrame() { return view; }

    /** Forgets last advance's requests. */
    public void beginFrame() { frame.clear(); }

    /** Thread-safe: {@code body} did {@code kind} where it stands. */
    public void post(Kind kind, long body, BattleSimulation sim) {
        frame.add(new Cue(kind, sim.identity().type(body), sim.world().x(body), sim.world().y(body)));
    }

    /** Thread-safe: {@code body} did {@code kind} to the cell at {@code cellX, cellY}. */
    public void postAt(Kind kind, long body, int cellX, int cellY, BattleSimulation sim) {
        frame.add(new Cue(kind, sim.identity().type(body), cellX + 0.5f, cellY + 0.5f));
    }
}
