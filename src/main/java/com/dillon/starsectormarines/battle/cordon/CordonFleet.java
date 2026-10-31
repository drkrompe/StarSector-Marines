package com.dillon.starsectormarines.battle.cordon;

/**
 * The Road's truck fleet ({@code cordon-nouns.md}, Convoys): a fixed number of trucks that cycle, the same trucks
 * driving trip after trip. A truck is put on the road only while the fleet is short of its size: at the start, and
 * {@link #replaceTicks} after one is destroyed, so a lost truck is made good once and the fleet never grows past its
 * size. Pure bookkeeping; {@code CordonConvoys} spawns the hulls it allows.
 */
final class CordonFleet {

    private final int size;
    private final int replaceTicks;
    /** Trucks on the map (on the road, loading, unloading or parked at the camp), and every one ever put on it. */
    private int live, spawned;
    /** The tick before which no replacement is put on the road. */
    private int replaceFrom;

    CordonFleet(int size, int replaceTicks) {
        this.size = size;
        this.replaceTicks = replaceTicks;
    }

    /** The fleet's size. */
    int size() { return size; }

    /** Trucks on the map. */
    int live() { return live; }

    /** Trucks ever put on the map: the fleet and its replacements. */
    int spawned() { return spawned; }

    /** How many trucks may be put on the road at {@code tick}: the fleet's shortfall, once any replacement is due. */
    int spawnable(int tick) {
        return tick < replaceFrom ? 0 : Math.max(0, size - live);
    }

    /** A truck put on the road. */
    void spawn(int tick) {
        if (spawnable(tick) <= 0) throw new IllegalStateException("the fleet of " + size + " is full or waiting");
        live++;
        spawned++;
    }

    /** A truck destroyed (or taken off the road) at {@code tick}: its replacement may come on {@link #replaceTicks} later. */
    void lose(int tick) {
        if (live <= 0) return;
        live--;
        replaceFrom = tick + replaceTicks;
    }

    /** Trucks a trip may set out with at {@code tick}: those waiting at the camp and those that may be put on. */
    int ready(int waiting, int tick) {
        return waiting + spawnable(tick);
    }

    /** The round trips {@code people} take in convoys of {@code trucks} trucks of {@code seats} seats. */
    static int tripsFor(int people, int trucks, int seats) {
        int per = Math.max(1, trucks * seats);
        return (Math.max(0, people) + per - 1) / per;
    }
}
