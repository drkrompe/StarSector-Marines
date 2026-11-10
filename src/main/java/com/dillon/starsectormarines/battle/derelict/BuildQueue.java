package com.dillon.starsectormarines.battle.derelict;

import java.util.ArrayList;
import java.util.List;

/**
 * The autoforge's build queue (`fallen-hull-nouns.md`, Vocabulary): the fixed order it prints in, from the survey
 * program, never chosen by what the company does. The program runs round and round: after its last item it starts
 * again at its first. It is readable: {@link #peek(int)} says what comes next, for the briefing and the rail.
 */
public final class BuildQueue {

    public enum Kind {
        /** A survey stake, carried out by a train: its hauler with the folded stake and an escort. */
        STAKE,
        /** One walker of the derelict line, posted to the frontier: the outermost linked established stake, else the hull. */
        WALKER
    }

    /**
     * One item of the program.
     *
     * @param frame  the walker printed; for a stake, the train's hauler ({@link DerelictWalkerFrame#HAULER})
     * @param escort for a stake, the walkers printed to walk round its hauler; empty otherwise
     * @param cost   the feedstock the item takes
     */
    public record Item(Kind kind, DerelictWalkerFrame frame, List<DerelictWalkerFrame> escort, float cost) {
        public Item {
            escort = List.copyOf(escort);
            if (cost < 0f) throw new IllegalArgumentException("An item never pays back");
            if (kind == Kind.STAKE && frame != DerelictWalkerFrame.HAULER) {
                throw new IllegalArgumentException("A stake goes out on a hauler");
            }
        }

        public static Item walker(DerelictWalkerFrame frame, float cost) {
            return new Item(Kind.WALKER, frame, List.of(), cost);
        }

        public static Item stake(float cost, DerelictWalkerFrame... escort) {
            return new Item(Kind.STAKE, DerelictWalkerFrame.HAULER, List.of(escort), cost);
        }
    }

    /** What the stake, its hauler and its escort cost together. UNTUNED. */
    public static final float STAKE_COST = 400f;
    /** A stripper's or hauler's cost. UNTUNED. */
    public static final float WORKER_COST = 120f;
    /** A line frame's cost. UNTUNED. */
    public static final float LINE_COST = 320f;

    private final List<Item> program;
    private int next;

    public BuildQueue(List<Item> program) {
        if (program.isEmpty()) throw new IllegalArgumentException("A survey program prints something");
        this.program = List.copyOf(program);
    }

    /**
     * The survey program's stand-in order (UNTUNED): a stripper and a hauler to feed the forge, a stake out with a
     * rounds walker beside it, a line frame for the frontier, a second stake, then another stripper.
     */
    public static BuildQueue survey() {
        return new BuildQueue(List.of(
                Item.walker(DerelictWalkerFrame.STRIPPER, WORKER_COST),
                Item.walker(DerelictWalkerFrame.HAULER, WORKER_COST),
                Item.stake(STAKE_COST, DerelictWalkerFrame.ROUNDS),
                Item.walker(DerelictWalkerFrame.LINE, LINE_COST),
                Item.stake(STAKE_COST, DerelictWalkerFrame.ROUNDS),
                Item.walker(DerelictWalkerFrame.STRIPPER, WORKER_COST)));
    }

    public List<Item> program() {
        return program;
    }

    /** The item the forge prints next. */
    public Item peek() {
        return program.get(next);
    }

    /** The next {@code n} items in order, round the program's end. */
    public List<Item> peek(int n) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(program.get((next + i) % program.size()));
        return out;
    }

    /** Moves past the next item: printed, or passed over for want of a place. */
    public void advance() {
        next = (next + 1) % program.size();
    }

    /** Where the queue stands in its program. */
    public int position() {
        return next;
    }
}
