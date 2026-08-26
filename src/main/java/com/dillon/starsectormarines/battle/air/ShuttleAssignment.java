package com.dillon.starsectormarines.battle.air;

import java.util.Objects;

/**
 * One transport's commitment to a mission: which {@link ShuttleType} it is,
 * how many sorties it will fly, and how many of the hull's physical seats are
 * embarked on each sortie. The two-argument constructor preserves full-load
 * legacy behavior; a mission arrival policy may deliberately author a partial
 * load such as six marines aboard a twelve-seat Valkyrie. The sim cycles the
 * shuttle through the state machine the appropriate number of times via the
 * {@link ShuttleMission#totalCycles} field set from this.
 */
public final class ShuttleAssignment {

    public final ShuttleType type;
    public final int cycles;
    /** Marines actually embarked per sortie; never exceeds the hull's physical capacity. */
    public final int seatsPerSortie;

    public ShuttleAssignment(ShuttleType type, int cycles) {
        this(type, cycles, capacityOf(type));
    }

    public ShuttleAssignment(ShuttleType type, int cycles, int seatsPerSortie) {
        this.type = Objects.requireNonNull(type, "type");
        this.cycles = Math.max(1, cycles);
        if (seatsPerSortie < 1 || seatsPerSortie > type.capacity) {
            throw new IllegalArgumentException("seatsPerSortie must be between 1 and "
                    + type.capacity + " for " + type + ": " + seatsPerSortie);
        }
        this.seatsPerSortie = seatsPerSortie;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ShuttleAssignment assignment)) return false;
        return type == assignment.type && cycles == assignment.cycles
                && seatsPerSortie == assignment.seatsPerSortie;
    }

    @Override
    public int hashCode() {
        int result = 31 * type.hashCode() + cycles;
        return 31 * result + seatsPerSortie;
    }

    private static int capacityOf(ShuttleType type) {
        return Objects.requireNonNull(type, "type").capacity;
    }
}
