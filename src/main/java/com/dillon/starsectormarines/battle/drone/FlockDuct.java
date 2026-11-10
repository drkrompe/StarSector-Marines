package com.dillon.starsectormarines.battle.drone;

/**
 * A flock's way through a service duct (`derelict-watch.md`, Movement): in at a vent, along the duct off the map, and up
 * at another vent. A pure clock; {@link FlockSystem} takes the members off the field and puts them back up.
 *
 * <ul>
 *   <li>{@link Phase#IN}: the anchor sits on the entry vent and each member that comes within {@link #ENTER_CELLS} goes
 *       in. Once none is left outside, or after {@link #IN_SECONDS}, the flock is in the duct; stragglers fly on.</li>
 *   <li>{@link Phase#DUCT}: the drones inside are out of play, neither seen nor seeing nor shot at, for the transit.</li>
 *   <li>{@link Phase#OUT}: they come up at the exit vent one every {@link FlockDock#LAUNCH_SECONDS}, the first at once.</li>
 *   <li>{@link Phase#DONE}: all are up; the flock's leash is held from the exit from now on.</li>
 * </ul>
 */
public final class FlockDuct {

    public enum Phase { IN, DUCT, OUT, DONE }

    /** A member this near the entry vent goes in, cells: a flock wheels wider round its anchor than it lands. */
    public static final float ENTER_CELLS = 3f;
    /** The longest the flock waits at the entry for its stragglers, seconds. */
    public static final float IN_SECONDS = 8f;

    public final float entryX;
    public final float entryY;
    public final float exitX;
    public final float exitY;
    private final float transitSeconds;
    private Phase phase = Phase.IN;
    private int inside;
    private int surfaced;
    private float clock;

    public FlockDuct(float entryX, float entryY, float exitX, float exitY, float transitSeconds) {
        this.entryX = entryX;
        this.entryY = entryY;
        this.exitX = exitX;
        this.exitY = exitY;
        this.transitSeconds = Math.max(0f, transitSeconds);
    }

    public Phase phase() {
        return phase;
    }

    /** Drones that have come up at the exit. */
    public int surfaced() {
        return surfaced;
    }

    /** Drones in the duct now. */
    public int inside() {
        return inside;
    }

    /** Whether a member {@code distance} cells from the entry goes in this tick. */
    public boolean enters(float distance) {
        return phase == Phase.IN && distance <= ENTER_CELLS;
    }

    /** One member went in. */
    public void entered() {
        inside++;
    }

    /** Where the flock's anchor is held: the entry while it goes in, the exit after. */
    public float anchorX() {
        return phase == Phase.IN ? entryX : exitX;
    }

    public float anchorY() {
        return phase == Phase.IN ? entryY : exitY;
    }

    /**
     * Advances by {@code dt}.
     *
     * @param outside members of the flock still on the field
     * @return how many drones come up at the exit this tick
     */
    public int tick(float dt, int outside) {
        switch (phase) {
            case IN -> {
                clock += dt;
                if (outside == 0 || clock >= IN_SECONDS) {
                    phase = inside > 0 ? Phase.DUCT : Phase.DONE;
                    clock = transitSeconds;
                }
                return 0;
            }
            case DUCT -> {
                clock -= dt;
                if (clock > 0f) return 0;
                phase = Phase.OUT;
                clock = FlockDock.LAUNCH_SECONDS;
                return up(0f);
            }
            case OUT -> {
                return up(dt);
            }
            default -> {
                return 0;
            }
        }
    }

    private int up(float dt) {
        clock += dt;
        int out = 0;
        while (clock >= FlockDock.LAUNCH_SECONDS && inside > 0) {
            clock -= FlockDock.LAUNCH_SECONDS;
            inside--;
            surfaced++;
            out++;
        }
        if (inside == 0) phase = Phase.DONE;
        return out;
    }
}
