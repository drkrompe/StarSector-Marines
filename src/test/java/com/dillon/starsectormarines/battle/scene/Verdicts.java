package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.scene.OrderTrace.Sample;

import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * The questions a scene keeps asking of its {@link OrderTrace}, each turned into
 * a {@link Verdict} whose detail quotes the measurement it was read off.
 *
 * <p>A FAIL that only says "held the order: FAIL" sends the next reader back to
 * re-play the scene. A FAIL that says which run it actually recorded is often
 * the whole diagnosis, so every factory here writes the number into the detail
 * on the failing side and on the passing side alike.
 *
 * <p>Deliberately ignorant of the domain: no goal name, assignment kind or
 * doctrine appears here. What a scene is asking about goes in the predicate it
 * passes, so a new behaviour needs a new scene rather than a new factory.
 */
public final class Verdicts {

    private Verdicts() {}

    /** The squad held a plan on all but at most {@code allowance} of the ticks in the window. */
    public static Verdict planlessAtMost(String name, OrderTrace trace, int squadId,
                                         int from, int to, int allowance) {
        int measured = trace.planlessTicks(squadId, from, to);
        String detail = "plan-less on " + measured + " tick(s) in [" + from + ".." + to
                + "], allowance " + allowance;
        return Verdict.of(name, measured <= allowance, detail);
    }

    /**
     * Every sample in the window satisfied {@code test}. A window the recording
     * never reached fails, because a verdict that passes by measuring nothing is
     * worse than no verdict.
     */
    public static Verdict held(String name, OrderTrace trace, int squadId,
                               int from, int to, Predicate<Sample> test, String what) {
        boolean pass = trace.holds(squadId, from, to, test);
        String detail = pass
                ? "held " + what + " across ticks [" + from + ".." + to + "]"
                : "did not hold " + what + " across ticks [" + from + ".." + to + "]; goals were "
                        + trace.runs(squadId, Sample::goal);
        return Verdict.of(name, pass, detail);
    }

    /** The first tick satisfying {@code test} fell inside {@code [notBefore, notAfter]}. */
    public static Verdict firstTickWithin(String name, OrderTrace trace, int squadId,
                                          Predicate<Sample> test, int notBefore, int notAfter,
                                          String what) {
        OptionalInt found = trace.firstTick(squadId, test);
        String window = " (wanted within [" + notBefore + ".." + notAfter + "])";
        if (found.isEmpty()) {
            return Verdict.fail(name, what + " never happened" + window);
        }
        int tick = found.getAsInt();
        boolean pass = tick >= notBefore && tick <= notAfter;
        return Verdict.of(name, pass, what + " first at tick " + tick + window);
    }

    /**
     * {@code test} came true within {@code maxTicks} of {@code fromTick} — the
     * "handed back within a tick of arriving" shape, where the bar is the delay
     * rather than the absolute tick.
     */
    public static Verdict latency(String name, OrderTrace trace, int squadId, int fromTick,
                                  Predicate<Sample> test, int maxTicks, String what) {
        OptionalInt found = trace.firstTick(squadId, s -> s.tick() >= fromTick && test.test(s));
        String bar = " (wanted within " + maxTicks + " tick(s) of " + fromTick + ")";
        if (found.isEmpty()) {
            return Verdict.fail(name, what + " never happened after tick " + fromTick + bar);
        }
        int delay = found.getAsInt() - fromTick;
        return Verdict.of(name, delay <= maxTicks,
                what + " after " + delay + " tick(s), at tick " + found.getAsInt() + bar);
    }

    /** The squad still had at least {@code atLeast} members on its feet at {@code atTick}. */
    public static Verdict alive(String name, OrderTrace trace, int squadId, int atTick, int atLeast) {
        Sample sample = trace.at(squadId, atTick);
        if (sample == null) {
            return Verdict.fail(name, "no sample at tick " + atTick + " (wanted at least "
                    + atLeast + " alive)");
        }
        return Verdict.of(name, sample.alive() >= atLeast,
                sample.alive() + " alive at tick " + atTick + " (wanted at least " + atLeast + ")");
    }
}
