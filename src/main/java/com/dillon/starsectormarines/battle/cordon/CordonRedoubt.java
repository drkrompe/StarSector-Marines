package com.dillon.starsectormarines.battle.cordon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One field redoubt of the line as the battle keeps it ({@code cordon-nouns.md},
 * Field redoubt, Redoubt states): its number west to east, the ground inside
 * its wall, the garrison that holds it and the state it is in.
 */
public final class CordonRedoubt {

    /**
     * {@code cordon-nouns.md}, Redoubt states. {@code GIVEN_UP} is a redoubt the allied command ordered its garrison
     * out of (beat L3): empty, never calling, and not fallen, so it does not count against the line.
     */
    public enum State { HOLDING, CALLING, RELIEVED, FALLEN, GIVEN_UP }

    private final CordonConstruction construction;
    private final int number;
    private final int left, top, right, bottom;
    private final List<Integer> teams;
    private final List<Integer> teamsView;
    private final List<Long> guns;
    private int garrison;
    private State state = State.HOLDING;
    private int swarmInside;
    private int lastBreachTick = Integer.MIN_VALUE / 2;

    public CordonRedoubt(int number, int left, int top, int right, int bottom,
                         List<Integer> teams, List<Long> guns, int garrison) {
        this(number, left, top, right, bottom, teams, guns, garrison, CordonConstruction.COMPOUND);
    }

    /** As the compound's constructor, for a redoubt built as {@code construction}. */
    public CordonRedoubt(int number, int left, int top, int right, int bottom,
                         List<Integer> teams, List<Long> guns, int garrison, CordonConstruction construction) {
        this.construction = construction;
        this.number = number;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.teams = new ArrayList<>(teams);
        this.teamsView = Collections.unmodifiableList(this.teams);
        this.guns = List.copyOf(guns);
        this.garrison = garrison;
    }

    /** What it is built from: the allies' compound, or what the country had standing. */
    public CordonConstruction construction() { return construction; }
    /** West to east from 1. */
    public int number() { return number; }
    /** The ground inside the wall, inclusive. */
    public int left() { return left; }
    public int top() { return top; }
    public int right() { return right; }
    public int bottom() { return bottom; }
    public int centreX() { return (left + right) / 2; }
    public int centreY() { return (top + bottom) / 2; }
    /** The garrison's squads: the wall teams, and any column that has joined them. */
    public List<Integer> teams() { return teamsView; }
    /** The gun towers' emplacements. */
    public List<Long> guns() { return guns; }
    /** How many the wall teams stood up with, and every column that has joined them since. */
    public int garrison() { return garrison; }
    public State state() { return state; }
    /** Swarm bodies inside the wall at the last look. */
    public int swarmInside() { return swarmInside; }
    /** Ticks at {@code tick} since a swarm body was last inside the wall; very large for never. */
    public int quietTicks(int tick) { return tick - lastBreachTick; }

    /** The sequence's look inside the wall this tick. */
    void observe(int inside, int tick) {
        swarmInside = inside;
        if (inside > 0) lastBreachTick = tick;
    }

    void setState(State state) { this.state = state; }

    /** Whether the allies still hold it: neither fallen nor given up. Columns, carriers and armour go only to these. */
    public boolean manned() { return state != State.FALLEN && state != State.GIVEN_UP; }

    /** An allied column joins the garrison: its squad is a wall team from here on. */
    void reinforce(int squad, int members) {
        teams.add(squad);
        garrison += members;
    }

    /** Whether a cell is inside the wall, widened by {@code margin}. */
    public boolean contains(int x, int y, int margin) {
        return x >= left - margin && x <= right + margin && y >= top - margin && y <= bottom + margin;
    }
}
