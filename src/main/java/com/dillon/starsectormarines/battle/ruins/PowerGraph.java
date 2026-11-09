package com.dillon.starsectormarines.battle.ruins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A ruins site's power ({@code ruins-works.md}, The power graph): one power hall with a capacity, junctions that each
 * feed one of two branches, and the consumers hung on those branches (seal doors, the vault's seal). Pure state: the
 * battle asks it what is powered and tells it what the company did.
 *
 * <p>A consumer is <b>fed</b> when its junction is thrown to its branch, and <b>powered</b> when it is fed, the hall
 * is running and the hall is not overloaded: the consumers fed together may not outnumber the capacity. An overloaded
 * hall powers nothing until a junction is thrown to shed load; it needs no second restart. Throwing a junction
 * re-feeds at once.
 */
public final class PowerGraph {

    /** The two ways a junction can feed. */
    public enum Branch { STORE, ONWARD }

    /** One thing that draws power, on {@code junction}'s {@code branch}. */
    public record Consumer(String id, int junction, Branch branch) {
        public Consumer {
            if (id == null || id.isBlank() || junction < 0 || branch == null) {
                throw new IllegalArgumentException("a consumer needs an id, a junction and a branch");
            }
        }
    }

    private final int capacity;
    private final Branch[] junctions;
    private final List<Consumer> consumers;
    private boolean running;

    /**
     * @param capacity  how many consumers the hall runs at once
     * @param initial   each junction's position at the start, by junction index
     * @param consumers every consumer, each on a junction that exists
     */
    public PowerGraph(int capacity, List<Branch> initial, List<Consumer> consumers) {
        if (capacity < 1 || initial.isEmpty()) throw new IllegalArgumentException("a hall with capacity and a junction");
        this.capacity = capacity;
        this.junctions = initial.toArray(new Branch[0]);
        for (Consumer consumer : consumers) {
            if (consumer.junction() >= junctions.length) {
                throw new IllegalArgumentException("consumer " + consumer.id() + " hangs on a missing junction");
            }
        }
        if (consumers.stream().map(Consumer::id).distinct().count() != consumers.size()) {
            throw new IllegalArgumentException("consumer ids are unique");
        }
        this.consumers = List.copyOf(consumers);
    }

    /** Starts the hall. It runs for the rest of the battle. */
    public void restart() { running = true; }

    /** Throws a junction to a branch; throwing it to where it already is changes nothing. */
    public void throwJunction(int junction, Branch branch) { junctions[junction] = branch; }

    public boolean running() { return running; }
    public int capacity() { return capacity; }
    public int junctionCount() { return junctions.length; }
    public Branch position(int junction) { return junctions[junction]; }
    public List<Consumer> consumers() { return consumers; }

    /** Whether the consumer's junction feeds its branch, the hall running or not. */
    public boolean fed(Consumer consumer) { return junctions[consumer.junction()] == consumer.branch(); }

    /** How many consumers the junctions feed now. */
    public int load() {
        int load = 0;
        for (Consumer consumer : consumers) if (fed(consumer)) load++;
        return load;
    }

    public boolean overloaded() { return load() > capacity; }

    public boolean powered(Consumer consumer) { return running && !overloaded() && fed(consumer); }

    /** The consumer with this id. */
    public Consumer consumer(String id) {
        for (Consumer consumer : consumers) if (consumer.id().equals(id)) return consumer;
        throw new IllegalArgumentException("no consumer " + id);
    }

    /** The consumers a junction's branch feeds. */
    public List<Consumer> on(int junction, Branch branch) {
        List<Consumer> out = new ArrayList<>();
        for (Consumer consumer : consumers) {
            if (consumer.junction() == junction && consumer.branch() == branch) out.add(consumer);
        }
        return out;
    }

    @Override public String toString() {
        return "PowerGraph[running=" + running + ", load=" + load() + "/" + capacity + ", junctions="
                + Arrays.toString(junctions) + "]";
    }
}
