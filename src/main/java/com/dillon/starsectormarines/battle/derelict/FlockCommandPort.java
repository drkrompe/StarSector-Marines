package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.FlockTask;
import java.util.List;

/**
 * The seam between the security network director and the flock model (`air-nouns.md`, Flocks;
 * `derelict-security-network.md`, slice 2). The director reads what each flock reports and gives each a task with an
 * anchor; members, leash flying, re-forming and returning under a third of strength stay the flock model's. A battle
 * with no flocks uses {@link #NONE}.
 */
public interface FlockCommandPort {

    /** A contact a flock's members hold, where and when they last saw it. */
    record Contact(long unitId, int cellX, int cellY, int observedTick) { }

    /**
     * One flock as its members report it: its home zone, where its anchor is, its strength as a share of full,
     * whether it is docked in its cradle, what it sees, and the unit it is striking ({@code pinnedUnitId}, -1 none).
     */
    record Report(int flockId, int homeZoneId, int cellX, int cellY, float strength, boolean docked,
                  List<Contact> seen, long pinnedUnitId) {

        public Report {
            seen = seen == null ? List.of() : List.copyOf(seen);
        }

        /** Where the pinned unit was last seen by this flock, or null when it pins nothing or no longer sees it. */
        public Contact pinned() {
            if (pinnedUnitId < 0) return null;
            for (Contact contact : seen) if (contact.unitId() == pinnedUnitId) return contact;
            return null;
        }
    }

    /** Every flock of the network this pulse, in flock id order. */
    List<Report> reports();

    /** Gives a flock its task and anchor cell; {@code targetUnitId} is the strike or pursuit target, -1 for none. */
    void order(int flockId, FlockTask task, int anchorCellX, int anchorCellY, long targetUnitId);

    /** The cradles of a zone's flocks, for its repair drone to tend. */
    default List<Long> cradles(int zoneId) {
        return List.of();
    }

    /** Whether a zone's node stands, which its flocks' cradles need to rebuild drones. */
    default void nodeStands(int zoneId, boolean stands) {
    }

    /** A network with no flocks: it reports nothing and takes no orders. */
    FlockCommandPort NONE = new FlockCommandPort() {
        @Override
        public List<Report> reports() {
            return List.of();
        }

        @Override
        public void order(int flockId, FlockTask task, int anchorCellX, int anchorCellY, long targetUnitId) {
        }
    };
}
