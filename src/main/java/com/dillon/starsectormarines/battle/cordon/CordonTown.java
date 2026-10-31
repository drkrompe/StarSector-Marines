package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.List;

/**
 * The Road's cut-off town, Ain Tafrant, as the battle keeps it ({@code cordon-nouns.md}, Cut-off town): the ground
 * inside its compound's wall, the staging area beside its truck yard and the rooms its people wait in, the defenders
 * who hold it (the town's militia and guard, allied, under garrison authority, never the player's), the guns in its
 * gate towers, and how the compound stands. Holding the compound is the Road's first objective; its breach and its
 * defenders' losses are radio, as a redoubt's are, so the company hears the town call without seeing it.
 *
 * <p>With its people spread over several rooms, no one room is the town: it is held while anyone holds the ground
 * inside the wall, and overrun only once the swarm is inside with no defender or Marine left in it.
 */
public final class CordonTown {

    /**
     * How the compound stands. {@code PRESSED}: the swarm is inside its wall and someone still holds it.
     * {@code OVERRUN}: the swarm has been inside with no defender or Marine in it for {@link #OVERRUN_SECONDS}; final.
     */
    public enum State { HOLDING, PRESSED, OVERRUN }

    /** Seconds the swarm must be inside the wall with nobody holding the compound before it is overrun. UNTUNED. */
    public static final int OVERRUN_SECONDS = 10;
    static final int OVERRUN_TICKS = Math.round(OVERRUN_SECONDS / BattleSimulation.TICK_DT);
    /** Cells outside the wall a defender or Marine still holds the compound from: at a gate, on its glacis. */
    public static final int HOLD_MARGIN = 2;

    private final int left, top, right, bottom;
    private final int gateX, gateY;
    private final CordonStagingArea staging;
    private final List<Integer> teams;
    private final List<Long> guns;
    private final int garrison;
    /** The rooms the trapped wait in, largest first. */
    private List<List<int[]>> rooms = List.of();
    private int[] waiting = new int[0];
    private State state = State.HOLDING;
    private int swarmInside, swarmStaging;
    private boolean held = true;
    private int lastBreachTick = Integer.MIN_VALUE / 2;
    /** The tick the swarm was first in the staging area with nobody holding it, or -1. */
    private int unheldSince = -1;

    /**
     * @param left     the ground inside the wall, inclusive
     * @param gateX    the gate the convoys load at
     * @param staging  the staging area inside that gate
     * @param teams    the defenders' squads
     * @param guns     the gate towers' emplacements
     * @param garrison how many the defenders stood up with
     */
    public CordonTown(int left, int top, int right, int bottom, int gateX, int gateY, CordonStagingArea staging,
                      List<Integer> teams, List<Long> guns, int garrison) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.gateX = gateX;
        this.gateY = gateY;
        this.staging = staging;
        this.teams = List.copyOf(teams);
        this.guns = List.copyOf(guns);
        this.garrison = garrison;
    }

    public int left() { return left; }
    public int top() { return top; }
    public int right() { return right; }
    public int bottom() { return bottom; }
    public int centreX() { return (left + right) / 2; }
    public int centreY() { return (top + bottom) / 2; }
    public int gateX() { return gateX; }
    public int gateY() { return gateY; }
    /** The staging area beside the truck yard. */
    public CordonStagingArea staging() { return staging; }
    /** The rooms the trapped wait in, largest first; empty where none was found. */
    public List<List<int[]>> rooms() { return rooms; }

    /** How many of the trapped were set to wait in each: the staging area first, then each room. */
    public int[] waiting() { return waiting.clone(); }

    /** Gives the town the rooms its people wait in and how many wait where; at setup. */
    void attachRooms(List<List<int[]>> rooms, int[] waiting) {
        List<List<int[]>> copy = new ArrayList<>();
        for (List<int[]> room : rooms) copy.add(List.copyOf(room));
        this.rooms = List.copyOf(copy);
        this.waiting = waiting.clone();
    }
    /** The defenders' squads. */
    public List<Integer> teams() { return teams; }
    /** The gate towers' emplacements. */
    public List<Long> guns() { return guns; }
    /** How many the defenders stood up with. */
    public int garrison() { return garrison; }
    public State state() { return state; }
    /** Whether the compound is still held: not overrun. */
    public boolean holds() { return state != State.OVERRUN; }
    /** Swarm bodies inside the compound's wall at the last look. */
    public int swarmInside() { return swarmInside; }
    /** Swarm bodies in the staging area at the last look. */
    public int swarmStaging() { return swarmStaging; }
    /** Whether a defender or a Marine stood inside the compound at the last look. */
    public boolean held() { return held; }
    /** Ticks at {@code tick} since a swarm body was last inside the wall; very large for never. */
    public int quietTicks(int tick) { return tick - lastBreachTick; }

    /**
     * Whether the town is calling over the radio with {@code standing} of its defenders up: the swarm is inside its wall
     * or half of them are down.
     */
    public boolean calling(int standing) {
        return holds() && (swarmInside > 0 || garrison > 0 && standing * 2 <= garrison);
    }

    /** Whether a cell is inside the wall, widened by {@code margin}. */
    public boolean contains(int x, int y, int margin) {
        return x >= left - margin && x <= right + margin && y >= top - margin && y <= bottom + margin;
    }

    /** Whether a defender or Marine standing on this cell holds the compound: inside its wall, or at it. */
    public boolean holdsFrom(int x, int y) {
        return contains(x, y, HOLD_MARGIN);
    }

    /**
     * The sequence's look this tick: swarm bodies inside the wall and in the staging area, and whether anyone holds the
     * compound. Returns the state it was in before, so the caller can tell a change.
     */
    State observe(int inside, int inStaging, boolean holders, int tick) {
        swarmInside = inside;
        swarmStaging = inStaging;
        held = holders;
        if (inside > 0) lastBreachTick = tick;
        if (inside > 0 && !holders) {
            if (unheldSince < 0) unheldSince = tick;
        } else {
            unheldSince = -1;
        }
        State before = state;
        state = next(state, inside > 0, holders, unheldSince < 0 ? 0 : tick - unheldSince);
        return before;
    }

    /**
     * The compound's next state ({@code cordon-nouns.md}, Cut-off town): holding while no swarm body is inside its
     * wall, pressed while one is and a defender or Marine still holds it (or has not been gone for long), overrun once
     * the swarm has been inside with nobody holding it for {@link #OVERRUN_TICKS}. Overrun is final.
     *
     * @param unheldTicks ticks the swarm has been inside with nobody holding the compound
     */
    static State next(State now, boolean swarmIn, boolean held, int unheldTicks) {
        if (now == State.OVERRUN) return now;
        if (!swarmIn) return State.HOLDING;
        if (!held && unheldTicks >= OVERRUN_TICKS) return State.OVERRUN;
        return State.PRESSED;
    }
}
