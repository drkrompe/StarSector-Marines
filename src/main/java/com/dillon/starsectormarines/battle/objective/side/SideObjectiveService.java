package com.dillon.starsectormarines.battle.objective.side;

import com.dillon.starsectormarines.battle.objective.side.SideObjective.State;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import java.util.ArrayList;
import java.util.List;

/**
 * A battle's side objectives while they run ({@code side-objective-toolkit.md}). The battle ticks it once a tick, before
 * its story sequences react, so a job finished on the tick its window closes still counts. Each change of state is
 * kept for presentation ({@link #transitionsAfter}) and speaks the hook's field line, if it has one, through the
 * battle's beat log. Serial: one thread drives it. Nothing here reaches the win check.
 */
public final class SideObjectiveService {
    /** One change of state, in the order it happened; {@code found} marks a hidden job coming to light. */
    public record Transition(int tick, String key, State state, boolean found) {
        public Transition(int tick, String key, State state) { this(tick, key, state, false); }
    }

    private final List<SideObjective> jobs = new ArrayList<>();
    private final List<Transition> transitions = new ArrayList<>();
    private float elapsed;
    private boolean ended;

    /** Offers a job. At setup only, before the first tick. */
    public void install(SideObjective job) {
        if (elapsed > 0f || ended) throw new IllegalStateException("offer side objectives at setup");
        for (SideObjective other : jobs)
            if (other.key().equals(job.key())) throw new IllegalArgumentException("side objective " + job.key() + " is offered twice");
        jobs.add(job);
    }

    public void tick(BattleSimulation sim) {
        if (jobs.isEmpty() || ended || sim.isComplete()) return;
        elapsed += BattleSimulation.TICK_DT;
        SideField field = SideField.of(sim);
        for (SideObjective job : jobs) step(job, field, BattleSimulation.TICK_DT, sim, sim.getSimTickIndex(), sim.getStoryBeatLog());
    }

    /** Advances on a hand-built field; the effect hooks get no battle. For checks of the rules, not for play. */
    void tick(SideField field, float dt, int tick, StoryBeatLog beats) {
        if (ended) return;
        elapsed += dt;
        for (SideObjective job : jobs) step(job, field, dt, null, tick, beats);
    }

    private void step(SideObjective job, SideField field, float dt, BattleSimulation sim, int tick, StoryBeatLog beats) {
        boolean known = job.known();
        boolean changed = job.step(field, dt, elapsed, sim);
        if (!known && job.known()) {
            transitions.add(new Transition(tick, job.key(), State.OFFERED, true));
            if (job.foundLine() != null && beats != null) beats.emitOnce(tick, job.foundLine(), StoryBeatLog.Audience.PLAYER);
        }
        if (changed) changed(job, tick, beats);
    }

    /**
     * Completes a job outright before play, as if it had been done: for evidence that plays a prepared approach on
     * demand. Silent: no line is spoken and no notice is kept.
     */
    public void grant(String key, BattleSimulation sim) { require(key).grant(elapsed, sim); }

    /** The battle is over: every unfinished job lapses. Idempotent. */
    public void end(int tick) {
        if (ended) return;
        ended = true;
        for (SideObjective job : jobs) if (job.end()) transitions.add(new Transition(tick, job.key(), job.state()));
    }

    /** One result per job, in offer order; ends the jobs first. */
    public List<SideObjectiveResult> results(int tick) {
        end(tick);
        List<SideObjectiveResult> out = new ArrayList<>(jobs.size());
        for (SideObjective job : jobs)
            out.add(new SideObjectiveResult(job.key(), job.hook().name(), job.state(), job.hook().debrief(job.state()),
                    job.ret().mark(job.state()), job.ret().credits(job.state())));
        return List.copyOf(out);
    }

    /** Every job offered, the hidden ones included; a presenter shows only {@link SideObjective#known} ones. */
    public List<SideObjective> jobs() { return List.copyOf(jobs); }

    public boolean isEmpty() { return jobs.isEmpty(); }

    /** The job offered under {@code key}, or null. */
    public SideObjective job(String key) {
        for (SideObjective job : jobs) if (job.key().equals(key)) return job;
        return null;
    }

    /** Changes of state from index {@code cursor} on; a presenter keeps its own cursor. */
    public List<Transition> transitionsAfter(int cursor) {
        return cursor >= transitions.size() ? List.of() : List.copyOf(transitions.subList(Math.max(0, cursor), transitions.size()));
    }

    private SideObjective require(String key) {
        SideObjective job = job(key);
        if (job == null) throw new IllegalArgumentException("no side objective " + key);
        return job;
    }

    private void changed(SideObjective job, int tick, StoryBeatLog beats) {
        transitions.add(new Transition(tick, job.key(), job.state()));
        String line = job.hook().lines().get(job.state());
        if (line != null && beats != null) beats.emitOnce(tick, line, StoryBeatLog.Audience.PLAYER);
    }
}
