package com.dillon.starsectormarines.battle.squad;

/**
 * <b>The squad's remembered flank aim.</b> One answer, shared by every member,
 * to the question {@code AttackMove.maneuverAim} asks: given this contact and
 * this raw bearing off the fixing squad's axis, which cell should the squad
 * actually walk to?
 *
 * <p>The question is per-squad but the call is per-member per-tick, and
 * answering it costs an A* fan-out — {@code ReinforceContact.snapToReachable}
 * pathfinds to every candidate over a radius-5 square, up to a hundred and
 * twenty-one routes. Six members of a maneuvering squad therefore paid for the
 * same search six times a tick, thirty times a second, and a profile of the
 * conquest matrix put two thirds of the whole tick in pathfinding with almost
 * all of {@code GridPathfinder.findPath} reached this way.
 *
 * <p>The answer only moves when the contact does. It is keyed on the contact's
 * id and the raw flank cell — both integers — and refreshed every
 * {@link #REFRESH_TICKS} ticks so a squad that has walked away from where it
 * asked still gets a route computed from where it now stands. Nothing here
 * reads a clock or a hash, so a replay stays byte-stable.
 *
 * <p>Mutable, allocation-free, and owned by exactly one {@link Squad}; it is
 * read and written on the sim thread inside the squad's own behavior tick.
 */
public final class FlankAimMemo {

    /**
     * Ticks a stored aim is trusted for — one second at 30 ticks/s. Short
     * enough that the squad's flank never lags its contact by more than a
     * stride, long enough that the search runs once per squad per second
     * instead of once per member per tick.
     */
    public static final int REFRESH_TICKS = 30;

    /** Contact the stored aim answers for. 0 while nothing has been stored. */
    private long contactId;
    private int rawX;
    private int rawY;
    /** Tick the stored aim was computed on. Meaningless while {@link #stored} is false. */
    private int computedTick;
    private int aimX;
    private int aimY;
    /**
     * True when the search refused — the flank came back on the squad's own
     * ground, which is a "the building will not support this" answer rather
     * than a destination. Memoised alongside the aim so the refusal costs the
     * fan-out once too.
     */
    private boolean refused;
    /** False until the first {@link #store}, so nothing is ever fresh by accident. */
    private boolean stored;

    /**
     * Whether the stored aim still answers this question. Same contact, same
     * raw bearing cell, and computed within {@link #REFRESH_TICKS}.
     */
    public boolean isFresh(long contactId, int rawX, int rawY, int tick) {
        return stored
                && this.contactId == contactId
                && this.rawX == rawX
                && this.rawY == rawY
                && tick - this.computedTick < REFRESH_TICKS;
    }

    /** Records the answer to {@code (contactId, rawX, rawY)} as computed on {@code tick}. */
    public void store(long contactId, int rawX, int rawY, int tick,
                      int aimX, int aimY, boolean refused) {
        this.contactId = contactId;
        this.rawX = rawX;
        this.rawY = rawY;
        this.computedTick = tick;
        this.aimX = aimX;
        this.aimY = aimY;
        this.refused = refused;
        this.stored = true;
    }

    public int aimX() { return aimX; }

    public int aimY() { return aimY; }

    public boolean refused() { return refused; }

    /** Tick the stored aim was computed on; {@code -1} while nothing is stored. */
    public int computedTick() { return stored ? computedTick : -1; }
}
