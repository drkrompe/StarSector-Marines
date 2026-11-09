package com.dillon.starsectormarines.battle.derelict;

/**
 * One security zone of a derelict network (`derelict-security-network.md`, slice 1): a control node, the ground it
 * keeps and its state. It knows only what it is told: a contact its sensors or flocks report, gunfire inside its
 * perimeter, damage to one of its units, a relay from a linked zone. Which zones are linked, and the relay itself,
 * are the {@link SecurityNetwork}'s.
 *
 * <p><b>States.</b> A zone starts {@link State#DORMANT}. A contact, gunfire inside the perimeter or damage wakes it
 * ({@link State#AWAKE}); a relay from a linked zone makes a dormant zone {@link State#ALERT} where it stands. An awake
 * zone that hears nothing for {@link #SETTLE_TICKS} settles to alert, never back to dormant. A zone whose node is dead
 * is isolated: it still wakes on its own senses, but neither relays nor hears a relay.
 */
public final class SecurityZone {

    public enum State { DORMANT, ALERT, AWAKE }

    /** Ticks an awake zone holds with no new contact, gunfire or damage before it settles to alert. UNTUNED (20 s). */
    public static final int SETTLE_TICKS = 20 * 60;

    public final int id;
    public final int nodeCellX;
    public final int nodeCellY;
    /** Chebyshev radius, in cells, of the ground the zone keeps around its node. */
    public final int perimeterCells;

    private State state = State.DORMANT;
    private boolean nodeAlive = true;
    private int lastStirTick = Integer.MIN_VALUE;

    public SecurityZone(int id, int nodeCellX, int nodeCellY, int perimeterCells) {
        if (perimeterCells < 1) throw new IllegalArgumentException("A zone keeps at least one cell");
        this.id = id;
        this.nodeCellX = nodeCellX;
        this.nodeCellY = nodeCellY;
        this.perimeterCells = perimeterCells;
    }

    public State state() {
        return state;
    }

    public boolean nodeAlive() {
        return nodeAlive;
    }

    /** Whether a cell is inside the ground this zone keeps. */
    public boolean inside(int cellX, int cellY) {
        return Math.max(Math.abs(cellX - nodeCellX), Math.abs(cellY - nodeCellY)) <= perimeterCells;
    }

    /**
     * A contact its sensors or flocks hold, gunfire inside the perimeter or damage to one of its units: the zone wakes.
     *
     * @return whether the zone was not awake before, so the network relays the alarm
     */
    public boolean stir(int tick) {
        lastStirTick = tick;
        boolean woke = state != State.AWAKE;
        state = State.AWAKE;
        return woke;
    }

    /** Gunfire heard at a cell: it wakes the zone only inside its perimeter. */
    public boolean hearGunfire(int cellX, int cellY, int tick) {
        return inside(cellX, cellY) && stir(tick);
    }

    /** A linked zone's alarm: a dormant zone with a live node goes alert where it stands; anything else is unchanged. */
    public boolean hearRelay() {
        if (!nodeAlive || state != State.DORMANT) return false;
        state = State.ALERT;
        return true;
    }

    /** The contingency's call: a zone with a live node wakes, wherever it stood. A dead node is out of the call. */
    public boolean call(int tick) {
        return nodeAlive && stir(tick);
    }

    /** Settles an awake zone that has heard nothing for {@link #SETTLE_TICKS} to alert. */
    public void settle(int tick) {
        if (state == State.AWAKE && (long) tick - lastStirTick >= SETTLE_TICKS) state = State.ALERT;
    }

    /** The node is destroyed: the zone stops relaying and hearing relays (`derelict-security-network.md`, slice 5). */
    public void loseNode() {
        nodeAlive = false;
    }
}
