package com.dillon.starsectormarines.battle.staffed;

import com.dillon.starsectormarines.battle.objective.optional.Cost;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveContext;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.objective.optional.Reward;

import java.util.Set;
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
    private boolean departedSaid;

    LoadingObjective(Loading loading) {
        this.loading = loading;
    }

    /**
     * The pool entry. {@code loading} is asked for the battle's loading when the board starts; one
     * that returns null (no warehouse, no road out) lapses the objective at once.
     */
    public static OptionalObjective entry(String title, String briefing, int weight, Reward reward, Cost cost,
                                          Supplier<Loading> loading) {
        return new OptionalObjective(KEY, title, briefing, Set.of(), Set.of(), weight, reward, cost,
                ctx -> {
                    Loading installed = loading.get();
                    return installed == null ? (c, tick) -> Status.LAPSED : new LoadingObjective(installed);
                });
    }

    @Override
    public Status advance(ObjectiveContext ctx, int tick) {
        Loading.Phase phase = loading.advance(ctx.sim(), tick);
        if (phase == Loading.Phase.DEPARTING && !departedSaid) {
            departedSaid = true;
            ctx.publish(tick, DEPARTED_BEAT);
        }
        return switch (phase) {
            case AWAY -> Status.SUCCEEDED;
            case LOST -> Status.FAILED;
            case LOADING, DEPARTING -> Status.ACTIVE;
        };
    }

    /** The loading this objective watches. */
    public Loading loading() {
        return loading;
    }
}
