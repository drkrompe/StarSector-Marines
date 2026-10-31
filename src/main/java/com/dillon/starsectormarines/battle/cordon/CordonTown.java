package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.List;

/**
 * The Road's cut-off town, Ain Tafrant, as the battle keeps it ({@code cordon-nouns.md}, Cut-off town): the ground
 * inside its compound's wall, the staging area inside its loading gate, the defenders who hold it (the town's militia
 * and guard, allied, under garrison authority, never the player's), the guns in its gate towers, and how the staging
 * area stands. Holding the staging area is the Road's first objective; the compound's breach and its defenders' losses
 * are radio, as a redoubt's are, so the company hears the town call without seeing it.
 */
public final class CordonTown {

    /**
     * How the staging area stands. {@code PRESSED}: the swarm is in it and someone still holds it. {@code OVERRUN}: the
     * swarm has been in it with nobody holding it for {@link #OVERRUN_SECONDS}; final.
     */
    public enum State { HOLDING, PRESSED, OVERRUN }

    /** Seconds the swarm must stand in the staging area with nobody holding it before it is overrun. UNTUNED. */
    public static final int OVERRUN_SECONDS = 10;
    static final int OVERRUN_TICKS = Math.round(OVERRUN_SECONDS / BattleSimulation.TICK_DT);
    /** Cells round the staging area a defender or Marine holds it from: they stand round it, not on it. */
    public static final int HOLD_MARGIN = 4;

    private final int left, top, right, bottom;
    private final int gateX, gateY;
    private final CordonStagingArea staging;
    private final List<Integer> teams;
    private final List<Long> guns;
    private final int garrison;
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
    /** The staging area inside the loading gate. */
    public CordonStagingArea staging() { return staging; }
    /** The defenders' squads. */
    public List<Integer> teams() { return teams; }
    /** The gate towers' emplacements. */
    public List<Long> guns() { return guns; }
    /** How many the defenders stood up with. */
    public int garrison() { return garrison; }
    public State state() { return state; }
    /** Whether the staging area is still held: not overrun. */
    public boolean holds() { return state != State.OVERRUN; }
    /** Swarm bodies inside the compound's wall at the last look. */
    public int swarmInside() { return swarmInside; }
    /** Swarm bodies in the staging area at the last look. */
    public int swarmStaging() { return swarmStaging; }
    /** Whether a defender or a Marine stood in or round the staging area at the last look. */
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

    /** Whether a defender or Marine standing on this cell holds the staging area. */
    public boolean holdsFrom(int x, int y) {
        return staging.near(x, y, HOLD_MARGIN);
    }

    /**
     * The sequence's look this tick: swarm bodies inside the wall and in the staging area, and whether anyone holds it.
     * Returns the state it was in before, so the caller can tell a change.
     */
    State observe(int inside, int inStaging, boolean holders, int tick) {
        swarmInside = inside;
        swarmStaging = inStaging;
        held = holders;
        if (inside > 0) lastBreachTick = tick;
        if (inStaging > 0 && !holders) {
            if (unheldSince < 0) unheldSince = tick;
        } else {
            unheldSince = -1;
        }
        State before = state;
        state = next(state, inStaging > 0, holders, unheldSince < 0 ? 0 : tick - unheldSince);
        return before;
    }

    /**
     * The staging area's next state ({@code cordon-nouns.md}, Cut-off town): holding while no swarm body is in it,
     * pressed while one is and a defender or Marine still holds it (or has not been gone for long), overrun once the
     * swarm has stood in it with nobody holding it for {@link #OVERRUN_TICKS}. Overrun is final.
     *
     * @param unheldTicks ticks the swarm has been in it with nobody holding it
     */
    static State next(State now, boolean swarmIn, boolean held, int unheldTicks) {
        if (now == State.OVERRUN) return now;
        if (!swarmIn) return State.HOLDING;
        if (!held && unheldTicks >= OVERRUN_TICKS) return State.OVERRUN;
        return State.PRESSED;
    }
}
