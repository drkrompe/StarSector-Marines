package com.dillon.starsectormarines.battle.objective.optional;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An optional objective played on a set piece under its own pressure ({@code set-piece-blocks.md}, Lanes and
 * pressure): a {@link Task} that says how it stands and where it is in its beats, and the
 * {@link ObjectivePressure} it brings against itself. The mission brings both and what they are worth; nothing
 * here knows which mission it serves. A battle that ends with the task still going lapses it.
 */
public final class PressuredObjective implements ObjectiveRuntime {

    /** The work an objective is about: installed at setup, advanced on the board's clock. */
    public interface Task {
        /** One step: {@link Status#ACTIVE} while it goes on, else how it ended. */
        Status advance(BattleSimulation sim, int tick);

        /** Where the task stands in its beats, read once it has advanced this tick. */
        ObjectivePressure.Beat beat();
    }

    private final Task task;
    private final ObjectivePressure pressure;
    private boolean ended;

    PressuredObjective(Task task, ObjectivePressure pressure) {
        this.task = task;
        this.pressure = pressure;
    }

    /**
     * The pool entry. {@code task} is asked for the battle's task when the board starts; one that returns null
     * (the set piece could not host it) lapses the objective at once. {@code pressure} is asked once the task
     * is installed.
     */
    public static <T extends Task> OptionalObjective entry(String key, String title, String briefing, int weight,
                                                           Reward reward, Cost cost, Supplier<T> task,
                                                           Function<T, ObjectivePressure> pressure) {
        return new OptionalObjective(key, title, briefing, Set.of(), Set.of(), weight, reward, cost,
                ctx -> {
                    T installed = task.get();
                    return installed == null ? (c, tick) -> Status.LAPSED
                            : new PressuredObjective(installed, pressure.apply(installed));
                });
    }

    @Override
    public Status advance(ObjectiveContext ctx, int tick) {
        Status status = task.advance(ctx.sim(), tick);
        pressure.advance(ctx.sim(), task.beat(), tick);
        if (status.isFinal()) endPressure(ctx);
        return status;
    }

    @Override
    public Status end(ObjectiveContext ctx) {
        endPressure(ctx);
        return Status.LAPSED;
    }

    private void endPressure(ObjectiveContext ctx) {
        if (ended) return;
        ended = true;
        pressure.end(ctx.sim());
    }

    /** The work this objective watches. */
    public Task task() { return task; }

    /** What the task brought against itself. */
    public ObjectivePressure pressure() { return pressure; }
}
