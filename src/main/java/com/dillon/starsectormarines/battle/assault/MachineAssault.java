package com.dillon.starsectormarines.battle.assault;

/**
 * One machine's standing assault on one garrisonable: where it fights from, and what it should
 * fire at this tick. Written only in serial phases (the order systems' command-boundary pass and
 * {@link MachineAssaults#tickVehicles}); a mech's parallel unit update and the turret pass read it.
 *
 * <p>The fire directive is a choice between a believed occupant and the sheltering wall, never
 * both: a wall is named only when no occupant can be shot ({@link MachineAssaults#aim}).
 */
public final class MachineAssault {

    /** No wall named this tick. */
    public static final int NO_WALL = -1;

    private final AssaultTarget target;
    private int standoffX;
    private int standoffY;
    int lastBelievedTick;
    long occupantId;
    int wallX = NO_WALL;
    int wallY = NO_WALL;
    int idleSinceTick = -1;
    int routeFailures;

    public MachineAssault(AssaultTarget target, int standoffX, int standoffY, int tick) {
        this.target = target;
        this.standoffX = standoffX;
        this.standoffY = standoffY;
        this.lastBelievedTick = tick;
    }

    public AssaultTarget target() { return target; }

    public int standoffX() { return standoffX; }

    public int standoffY() { return standoffY; }

    /** A believed occupant to engage this tick, or {@code 0L}. */
    public long occupantId() { return occupantId; }

    /** Whether a wall of the target is named for this tick's fire. */
    public boolean hasWall() { return wallX != NO_WALL; }

    public int wallX() { return wallX; }

    public int wallY() { return wallY; }

    /** The last tick some squad of the side believed a hostile in the target. */
    public int lastBelievedTick() { return lastBelievedTick; }

    /** Moves the stand-off to a cell chosen again from what is believed now. */
    public void moveStandoff(int x, int y) {
        standoffX = x;
        standoffY = y;
        idleSinceTick = -1;
        routeFailures = 0;
    }

    /** Counts a stand-off the machine could not route to; the order gives up after a few. */
    public int noteRouteFailure() {
        return ++routeFailures;
    }
}
