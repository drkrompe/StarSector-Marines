package com.dillon.starsectormarines.battle.story;

/** One authored outer post and its honest, irreversible withdrawal history. */
public final class ColdStartPicket {
    public enum State { HOLDING, WITHDRAWING, INNER_HOLD, LOST }
    public record Report(String key, int squadId, int outerX, int outerY, int innerX, int innerY,
                         int initialMembers, int aliveMembers, int innerMembers, State state,
                         boolean contacted, boolean overrun, long withdrawalTick) {
        public int lostMembers() { return initialMembers - aliveMembers; }
        public boolean withdrawing() { return state == State.WITHDRAWING || state == State.INNER_HOLD; }
    }
    private final String key;
    private final int squadId, outerX, outerY, innerX, innerY, initialMembers;
    private int aliveMembers, innerMembers;
    private State state = State.HOLDING;
    private boolean contacted, overrun;
    private long withdrawalTick = -1;

    public ColdStartPicket(String key, int squadId, int outerX, int outerY, int innerX, int innerY, int initialMembers) {
        if (key == null || key.isBlank() || initialMembers < 1) throw new IllegalArgumentException("Invalid picket");
        this.key = key; this.squadId = squadId; this.outerX = outerX; this.outerY = outerY;
        this.innerX = innerX; this.innerY = innerY; this.initialMembers = initialMembers;
        aliveMembers = initialMembers;
    }
    /** A fragile watch retires after its first casualty or actual breach, never merely after contact or elapsed time. */
    public void observe(long tick, int alive, int insideInner, boolean contact, boolean breached) {
        if (alive < 0 || alive > initialMembers || insideInner < 0 || insideInner > alive)
            throw new IllegalArgumentException("Invalid picket strength");
        aliveMembers = alive; innerMembers = insideInner;
        contacted |= contact;
        overrun |= breached || alive == 0;
        if (alive == 0) state = State.LOST;
        else if (state == State.HOLDING && (alive < initialMembers || breached)) {
            state = State.WITHDRAWING; withdrawalTick = tick;
        }
        if (state == State.WITHDRAWING && insideInner == alive) state = State.INNER_HOLD;
        else if (state == State.INNER_HOLD && insideInner < alive) state = State.WITHDRAWING;
    }
    public Report report() {
        return new Report(key, squadId, outerX, outerY, innerX, innerY, initialMembers,
                aliveMembers, innerMembers, state, contacted, overrun, withdrawalTick);
    }
}
