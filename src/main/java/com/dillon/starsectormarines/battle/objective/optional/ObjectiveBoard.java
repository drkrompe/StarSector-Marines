package com.dillon.starsectormarines.battle.objective.optional;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A battle's rolled optional objectives, run beside its story. The mission
 * starts it at setup and advances it from its own story sequence; the campaign
 * hand-off ends it and reads {@link #results()}.
 *
 * <p>Each objective publishes {@code <prefix>objective_<key>_started} when it
 * starts and one of {@code _succeeded}, {@code _failed} or {@code _lapsed} when
 * it goes final, through the mission's beat log. Serial: one thread drives it.
 */
public final class ObjectiveBoard {

    private static final class Entry {
        final OptionalObjective objective;
        ObjectiveRuntime runtime;
        Status status = Status.ACTIVE;

        Entry(OptionalObjective objective) { this.objective = objective; }
    }

    private final ObjectiveContext ctx;
    private final List<Entry> entries = new ArrayList<>();
    private boolean started;
    private boolean ended;

    public ObjectiveBoard(List<OptionalObjective> rolled, ObjectiveContext ctx) {
        if (ctx == null) throw new IllegalArgumentException("An objective board needs a context");
        this.ctx = ctx;
        for (OptionalObjective objective : rolled) entries.add(new Entry(objective));
    }

    public ObjectiveContext context() { return ctx; }

    /** Builds every runtime and publishes its started beat. Once. */
    public void start(int tick) {
        if (started) throw new IllegalStateException("Start an objective board once");
        started = true;
        for (Entry entry : entries) {
            entry.runtime = entry.objective.start().apply(ctx);
            ctx.publish(tick, beat(entry.objective, "started"));
        }
    }

    /** Advances every still-active objective, in roll order, and finishes those that go final. */
    public void advance(int tick) {
        if (!started || ended) return;
        for (Entry entry : entries) {
            if (entry.status != Status.ACTIVE) continue;
            Status next = entry.runtime.advance(ctx, tick);
            if (next != null && next.isFinal()) finish(entry, next, tick);
        }
    }

    /**
     * The battle is over: every still-active objective is asked how it ends
     * ({@link ObjectiveRuntime#end}, lapsed by default). Idempotent.
     */
    public void end(int tick) {
        if (ended) return;
        ended = true;
        if (!started) return;
        for (Entry entry : entries) {
            if (entry.status != Status.ACTIVE) continue;
            Status last = entry.runtime.end(ctx);
            finish(entry, last == null || !last.isFinal() ? Status.LAPSED : last, tick);
        }
    }

    /** One result per rolled objective, in roll order; an unfinished one reads {@link Status#ACTIVE}. */
    public List<ObjectiveResult> results() {
        List<ObjectiveResult> out = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            OptionalObjective o = entry.objective;
            out.add(new ObjectiveResult(o.key(), o.title(), entry.status, o.reward(), o.cost()));
        }
        return List.copyOf(out);
    }

    /** The started runtime of the rolled objective {@code key}; null when it was not rolled or not started. */
    public ObjectiveRuntime runtime(String key) {
        for (Entry entry : entries) if (entry.objective.key().equals(key)) return entry.runtime;
        return null;
    }

    /** The beat key, without the mission prefix, for an objective's event. */
    public static String beat(OptionalObjective objective, String event) {
        return "objective_" + objective.key() + "_" + event;
    }

    private void finish(Entry entry, Status status, int tick) {
        entry.status = status;
        List<StoryBeatLog.Event> events = ctx.beats().events();
        int at = events.isEmpty() ? tick : Math.max(tick, events.get(events.size() - 1).tick());
        ctx.publish(at, beat(entry.objective, status.name().toLowerCase(Locale.ROOT)));
    }
}
