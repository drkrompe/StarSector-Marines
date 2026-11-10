package com.dillon.starsectormarines.battle.derelict;

import java.util.List;
import java.util.function.Predicate;

/**
 * The fallen hull's autoforge (`fallen-hull-nouns.md`, Vocabulary): the network's heart and the vault of the hull's
 * interior. It prints its {@link BuildQueue} on the forge cycle: each {@link #CYCLE_SECONDS}, with feedstock enough
 * for the next item, it draws the item's cost and prints it; short of it, the item waits for a later cycle. An item
 * with nowhere to go (a stake with no plantable lattice point) is passed over at no cost, so a full claim does not
 * stall the walkers behind it. Put out ({@link #putOut}), it prints nothing again.
 *
 * <p>The forge's breach calls the contingency: its plate and structure below {@link #BREACH_SHARE} of whole.
 */
public final class Autoforge {

    /** Seconds between forge cycles. UNTUNED. */
    public static final float CYCLE_SECONDS = 30f;
    /** The share of its plate and structure below which the forge is breached. UNTUNED. */
    public static final float BREACH_SHARE = 0.9f;
    /** The forge's structure and plate: a squad's long work, heavier than a node. UNTUNED. */
    public static final float MAX_STRUCTURE = 1400f;
    public static final float ARMOR_CAPACITY = 900f;

    private final BuildQueue queue;
    private float clock;
    private boolean lit = true;
    private int printed;

    public Autoforge(BuildQueue queue) {
        this.queue = queue;
    }

    public BuildQueue queue() {
        return queue;
    }

    /** Whether the forge still burns. */
    public boolean lit() {
        return lit;
    }

    /** Destroyed or its power cut: nothing more is printed anywhere, and every stake is stranded. */
    public void putOut() {
        lit = false;
    }

    /** Items printed so far. */
    public int printed() {
        return printed;
    }

    /** How far the forge is into its cycle, 0 to 1. */
    public float cycleProgress() {
        return Math.min(1f, clock / CYCLE_SECONDS);
    }

    /**
     * Runs the forge clock {@code dt} seconds.
     *
     * @param stores    the feedstock the forge reaches, the hull's store first
     * @param placeable whether an item has somewhere to go now
     * @return the item printed on a cycle that ends in this step, or null
     */
    public BuildQueue.Item tick(float dt, List<Feedstock> stores, Predicate<BuildQueue.Item> placeable) {
        if (!lit) return null;
        clock += dt;
        if (clock < CYCLE_SECONDS) return null;
        clock -= CYCLE_SECONDS;
        // Items with nowhere to go are passed over, once round the program at most.
        for (int i = 0; i < queue.program().size(); i++) {
            BuildQueue.Item item = queue.peek();
            if (!placeable.test(item)) {
                queue.advance();
                continue;
            }
            if (!Feedstock.draw(stores, item.cost())) return null;
            queue.advance();
            printed++;
            return item;
        }
        return null;
    }

    /** Whether a forge at {@code share} of its plate and structure is breached. */
    public static boolean breached(float share) {
        return share < BREACH_SHARE;
    }
}
