package com.dillon.starsectormarines.battle.command;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * When a {@link PlaceDirective} begins or ends (`garrisonables.md`, place directives): a generic predicate over one
 * party's pulse, never a mission's own code. Belief predicates read the party's own picture; stages and flags are what
 * the mission discloses to that party. Every trigger prints as what it reads, so a trace says why a directive moved.
 */
public sealed interface PlaceTrigger {

    /** Whether the trigger holds on this pulse. */
    boolean test(Context context);

    /** One pulse of one party, as one directive sees it. */
    interface Context {
        /** The simulation tick of the pulse. */
        int tick();

        /** The tick the directive under test became active, or -1 when it is not active. */
        int activeSince();

        /** The directive's own target. */
        PlaceTarget self();

        /** A mission stage the mission discloses (a phase's ordinal, a wave's number), or {@link Integer#MIN_VALUE}. */
        int stage(String name);

        /** A mission flag the mission discloses (a beat fired, a public fact). */
        boolean flag(String name);

        /** How the party believes the region stands. */
        PlaceHold hold(PlaceTarget target);

        /** Whether the party has believed its own target {@link PlaceHold#OWN} on an earlier pulse. */
        boolean everOwn();

        /** The party's believed hostile pressure on the region: the most on any influence block it covers. */
        float pressure(PlaceTarget target);
    }

    static PlaceTrigger always() { return Always.INSTANCE; }

    static PlaceTrigger never() { return Never.INSTANCE; }

    /** From this tick on. */
    static PlaceTrigger tickAtLeast(int tick) { return new TickAtLeast(tick); }

    /** Once the directive has been active this many ticks; never while it is not active. */
    static PlaceTrigger activeFor(int ticks) { return new ActiveFor(ticks); }

    /** While the named stage is at least this value. */
    static PlaceTrigger stageAtLeast(String name, int value) { return new StageAtLeast(name, value); }

    /** While the named flag is raised. */
    static PlaceTrigger flag(String name) { return new Flag(name); }

    /** While the directive's own target is believed to stand in one of these ways. */
    static PlaceTrigger held(PlaceHold first, PlaceHold... rest) { return new Held(null, EnumSet.of(first, rest)); }

    /** While another region is believed to stand in one of these ways. */
    static PlaceTrigger heldAt(PlaceTarget target, PlaceHold first, PlaceHold... rest) {
        return new Held(target, EnumSet.of(first, rest));
    }

    /** Once the party has held its own target and now believes hostiles in it. */
    static PlaceTrigger lost() { return Lost.INSTANCE; }

    /** While believed hostile pressure on the directive's own target is at least this. */
    static PlaceTrigger pressureAtLeast(float level) { return new PressureAtLeast(null, level); }

    /** While believed hostile pressure on a region is at least this. */
    static PlaceTrigger pressureAtLeast(PlaceTarget target, float level) { return new PressureAtLeast(target, level); }

    static PlaceTrigger anyOf(PlaceTrigger... triggers) { return new AnyOf(List.of(triggers)); }

    static PlaceTrigger allOf(PlaceTrigger... triggers) { return new AllOf(List.of(triggers)); }

    static PlaceTrigger not(PlaceTrigger trigger) { return new Not(trigger); }

    enum Always implements PlaceTrigger {
        INSTANCE;

        @Override public boolean test(Context context) { return true; }

        @Override public String toString() { return "always"; }
    }

    enum Never implements PlaceTrigger {
        INSTANCE;

        @Override public boolean test(Context context) { return false; }

        @Override public String toString() { return "never"; }
    }

    enum Lost implements PlaceTrigger {
        INSTANCE;

        @Override public boolean test(Context context) {
            if (!context.everOwn()) return false;
            PlaceHold hold = context.hold(context.self());
            return hold == PlaceHold.HOSTILE || hold == PlaceHold.CONTESTED;
        }

        @Override public String toString() { return "lost"; }
    }

    record TickAtLeast(int tick) implements PlaceTrigger {
        @Override public boolean test(Context context) { return context.tick() >= tick; }

        @Override public String toString() { return "tick>=" + tick; }
    }

    record ActiveFor(int ticks) implements PlaceTrigger {
        @Override public boolean test(Context context) {
            return context.activeSince() >= 0 && context.tick() - context.activeSince() >= ticks;
        }

        @Override public String toString() { return "active>=" + ticks; }
    }

    record StageAtLeast(String name, int value) implements PlaceTrigger {
        @Override public boolean test(Context context) {
            int stage = context.stage(name);
            return stage != Integer.MIN_VALUE && stage >= value;
        }

        @Override public String toString() { return name + ">=" + value; }
    }

    record Flag(String name) implements PlaceTrigger {
        @Override public boolean test(Context context) { return context.flag(name); }

        @Override public String toString() { return name; }
    }

    record Held(PlaceTarget target, Set<PlaceHold> holds) implements PlaceTrigger {
        public Held {
            holds = Set.copyOf(holds);
        }

        @Override public boolean test(Context context) {
            return holds.contains(context.hold(target == null ? context.self() : target));
        }

        @Override public String toString() {
            return "held" + (target == null ? "" : "@" + target) + " in " + EnumSet.copyOf(holds);
        }
    }

    record PressureAtLeast(PlaceTarget target, float level) implements PlaceTrigger {
        @Override public boolean test(Context context) {
            return context.pressure(target == null ? context.self() : target) >= level;
        }

        @Override public String toString() {
            return "pressure" + (target == null ? "" : "@" + target) + ">=" + level;
        }
    }

    record AnyOf(List<PlaceTrigger> triggers) implements PlaceTrigger {
        public AnyOf {
            triggers = List.copyOf(triggers);
        }

        @Override public boolean test(Context context) {
            for (PlaceTrigger trigger : triggers) if (trigger.test(context)) return true;
            return false;
        }

        @Override public String toString() { return "any" + triggers; }
    }

    record AllOf(List<PlaceTrigger> triggers) implements PlaceTrigger {
        public AllOf {
            triggers = List.copyOf(triggers);
        }

        @Override public boolean test(Context context) {
            for (PlaceTrigger trigger : triggers) if (!trigger.test(context)) return false;
            return true;
        }

        @Override public String toString() { return "all" + triggers; }
    }

    record Not(PlaceTrigger trigger) implements PlaceTrigger {
        @Override public boolean test(Context context) { return !trigger.test(context); }

        @Override public String toString() { return "not(" + trigger + ")"; }
    }
}
