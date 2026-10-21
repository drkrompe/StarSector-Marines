package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The {@link PlaceDirective}s one party's commander holds, and where each stands (`garrisonables.md`, place
 * directives): dormant, active or released. The commander {@link #evaluate evaluates} the book once a pulse over its own
 * {@link PlaceSignals}; then it reads {@link #active(PlaceDirective.Kind) the active ones} of the kind it acts on, and
 * for a locked position {@link #lockFor which lock a detachment holds}.
 *
 * <p>The lifecycle: every directive is evaluated once a pulse, in declaration order, on the same signals. An active one
 * whose {@code until} holds is released; a dormant one whose {@code when} holds becomes active unless its {@code until}
 * already holds too; never both on one pulse. A released directive that {@link PlaceDirective#rearms re-arms} is dormant again from the next pulse. The order
 * directives became active in is kept, ties in declaration order. Pure and deterministic; one book per commander, so
 * per simulation.
 */
public final class PlaceDirectives {

    /** Where a directive stands. */
    public enum State { DORMANT, ACTIVE, RELEASED }

    /** One directive changing state on one pulse, and the trigger that moved it; for traces and evidence. */
    public record Transition(int tick, String id, PlaceDirective.Kind kind, State to, String trigger) {}

    /** The lock a detachment holds this pulse and the post it stands on ({@code null} when the lock names none). */
    public record Lock(PlaceDirective directive, PlaceDirective.Post post) {}

    private final List<PlaceDirective> directives;
    private final State[] states;
    private final int[] activeSince;
    private final int[] releasedAt;
    private final boolean[] everOwn;
    /** Indexes of the active directives, in the order they became active. */
    private final List<Integer> activeOrder = new ArrayList<>();
    private final List<Transition> log = new ArrayList<>();
    private int activations;
    private int releases;

    public PlaceDirectives(List<PlaceDirective> directives) {
        this.directives = List.copyOf(directives);
        Set<String> ids = new HashSet<>();
        for (PlaceDirective directive : this.directives) {
            if (!ids.add(directive.id())) throw new IllegalArgumentException("Duplicate place directive " + directive.id());
        }
        int n = this.directives.size();
        states = new State[n];
        activeSince = new int[n];
        releasedAt = new int[n];
        everOwn = new boolean[n];
        for (int i = 0; i < n; i++) {
            states[i] = State.DORMANT;
            activeSince[i] = -1;
            releasedAt[i] = -1;
        }
    }

    /** A book with nothing in it. */
    public static PlaceDirectives none() {
        return new PlaceDirectives(List.of());
    }

    /** A fresh book of this mission's directives for one party's commander. */
    public static PlaceDirectives forParty(List<PlaceDirective> mission, Faction party) {
        List<PlaceDirective> own = new ArrayList<>();
        for (PlaceDirective directive : mission) if (directive.party() == party) own.add(directive);
        return new PlaceDirectives(own);
    }

    public boolean isEmpty() { return directives.isEmpty(); }

    public List<PlaceDirective> directives() { return directives; }

    /**
     * One pulse: releases, activates and re-arms by the lifecycle above, and returns what changed on it (also kept in
     * {@link #log}). Call once per command pulse, before reading the book.
     */
    public List<Transition> evaluate(PlaceSignals signals) {
        List<Transition> changed = new ArrayList<>();
        int tick = signals.tick();
        for (int i = 0; i < directives.size(); i++) {
            PlaceDirective directive = directives.get(i);
            if (states[i] == State.RELEASED && directive.rearms() && releasedAt[i] < tick) {
                states[i] = State.DORMANT;
                activeSince[i] = -1;
            }
            Context context = new Context(signals, directive.target(), states[i] == State.ACTIVE ? activeSince[i] : -1,
                    everOwn[i]);
            if (states[i] == State.ACTIVE) {
                if (directive.until().test(context)) {
                    states[i] = State.RELEASED;
                    releasedAt[i] = tick;
                    activeOrder.remove(Integer.valueOf(i));
                    releases++;
                    changed.add(new Transition(tick, directive.id(), directive.kind(), State.RELEASED,
                            String.valueOf(directive.until())));
                }
            } else if (states[i] == State.DORMANT && directive.when().test(context) && !directive.until().test(context)) {
                states[i] = State.ACTIVE;
                activeSince[i] = tick;
                activeOrder.add(i);
                activations++;
                changed.add(new Transition(tick, directive.id(), directive.kind(), State.ACTIVE,
                        String.valueOf(directive.when())));
            }
            // Held own on this pulse counts for "lost" from the next one.
            if (!everOwn[i] && signals.hold(directive.target()) == PlaceHold.OWN) everOwn[i] = true;
        }
        log.addAll(changed);
        return changed;
    }

    /** The active directives of a kind, in the order they became active. */
    public List<PlaceDirective> active(PlaceDirective.Kind kind) {
        List<PlaceDirective> out = new ArrayList<>();
        for (int index : activeOrder) if (directives.get(index).kind() == kind) out.add(directives.get(index));
        return out;
    }

    /** Where a directive stands, or {@code null} for an id the book does not hold. */
    public State state(String id) {
        for (int i = 0; i < directives.size(); i++) if (directives.get(i).id().equals(id)) return states[i];
        return null;
    }

    /**
     * The lock detachment {@code detachment} holds this pulse, or {@code null} when no lock is active: detachments fill
     * the active locks in the order they became active, one each, oldest first, and detachments beyond them reinforce
     * the newest. The post cycles through the lock's posts by detachment number.
     */
    public Lock lockFor(int detachment) {
        if (detachment < 0) throw new IllegalArgumentException("Negative detachment " + detachment);
        List<PlaceDirective> locks = active(PlaceDirective.Kind.LOCK);
        if (locks.isEmpty()) return null;
        PlaceDirective lock = locks.get(Math.min(detachment, locks.size() - 1));
        PlaceDirective.Post post = lock.posts().isEmpty() ? null
                : lock.posts().get(Math.floorMod(detachment, lock.posts().size()));
        return new Lock(lock, post);
    }

    /** Every transition so far, oldest first. */
    public List<Transition> log() { return Collections.unmodifiableList(log); }

    public int activations() { return activations; }

    public int releases() { return releases; }

    /** One directive's view of one pulse. */
    private record Context(PlaceSignals signals, PlaceTarget self, int activeSince, boolean everOwn)
            implements PlaceTrigger.Context {
        @Override public int tick() { return signals.tick(); }

        @Override public int stage(String name) { return signals.stage(name); }

        @Override public boolean flag(String name) { return signals.flag(name); }

        @Override public PlaceHold hold(PlaceTarget target) { return signals.hold(target); }

        @Override public float pressure(PlaceTarget target) { return signals.pressure(target); }
    }
}
