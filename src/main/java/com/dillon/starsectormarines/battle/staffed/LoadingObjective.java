package com.dillon.starsectormarines.battle.staffed;

import com.dillon.starsectormarines.battle.objective.optional.Cost;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveContext;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.objective.optional.Reward;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * "Secure the loading" as an optional objective ({@code optional-objectives.md}): it succeeds when
 * the truck is away with its load and fails when the truck or the whole crew is lost before then. A
 * battle that ends with the loading still going lapses it: nobody stopped it and nobody finished it.
 *
 * <p>The mission brings the loading, already installed, and what it is worth; nothing here knows
 * which mission it serves.
 */
public final class LoadingObjective implements ObjectiveRuntime {

    /** The pool key. */
    public static final String KEY = "secure_loading";
    /** Beat the runtime publishes once the truck sets off, under the board's prefix. */
    public static final String DEPARTED_BEAT = "objective_" + KEY + "_departed";

    private final Loading loading;
    private final LoadingPressure pressure;
    private boolean departedSaid;
    private boolean ended;

    LoadingObjective(Loading loading, LoadingPressure pressure) {
        this.loading = loading;
        this.pressure = pressure;
    }

    /**
     * The pool entry. {@code loading} is asked for the battle's loading when the board starts; one
     * that returns null (no warehouse, no road out) lapses the objective at once.
     */
    public static OptionalObjective entry(String title, String briefing, int weight, Reward reward, Cost cost,
                                          Supplier<Loading> loading) {
        return entry(title, briefing, weight, reward, cost, loading, l -> LoadingPressure.NONE);
    }

    /** {@link #entry}, with what the loading brings against itself, asked for once the loading is installed. */
    public static OptionalObjective entry(String title, String briefing, int weight, Reward reward, Cost cost,
                                          Supplier<Loading> loading, Function<Loading, LoadingPressure> pressure) {
        return new OptionalObjective(KEY, title, briefing, Set.of(), Set.of(), weight, reward, cost,
                ctx -> {
                    Loading installed = loading.get();
                    return installed == null ? (c, tick) -> Status.LAPSED
                            : new LoadingObjective(installed, pressure.apply(installed));
                });
    }

    @Override
    public Status advance(ObjectiveContext ctx, int tick) {
        Loading.Phase phase = loading.advance(ctx.sim(), tick);
        if (phase == Loading.Phase.DEPARTING && !departedSaid) {
            departedSaid = true;
            ctx.publish(tick, DEPARTED_BEAT);
        }
        pressure.advance(ctx.sim(), loading, tick);
        Status status = switch (phase) {
            case AWAY -> Status.SUCCEEDED;
            case LOST -> Status.FAILED;
            case LOADING, DEPARTING -> Status.ACTIVE;
        };
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

    /** What the loading brought against itself. */
    public LoadingPressure pressure() {
        return pressure;
    }

    /** The loading this objective watches. */
    public Loading loading() {
        return loading;
    }
}
