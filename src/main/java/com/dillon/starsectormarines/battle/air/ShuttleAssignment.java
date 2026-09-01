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
    /**
     * The frame this craft actually flies as — the pattern itself for an
     * employer's boat or a padded fallback, and a {@link FittedBoat} for one of
     * the company's own with a yard's work on it.
     *
     * <p>Carried beside {@link #type} rather than instead of it because the two
     * answer different questions. What a craft <em>is</em> decides seats,
     * arrival policy and the delivery-craft substitution, all of which are
     * facts about the pattern; how much hull and speed it brings is the fit,
     * and only the sim reads that. Never null: a plain assignment's airframe is
     * its own type.
     */
    public final Airframe airframe;
    public final int cycles;
    /** Marines actually embarked per sortie; never exceeds the hull's physical capacity. */
    public final int seatsPerSortie;
    /** Exact personnel carried across every sortie; the final sortie may be partial. */
    public final int embarkedPersonnel;

    public ShuttleAssignment(ShuttleType type, int cycles) {
        this(type, cycles, capacityOf(type));
    }

    public ShuttleAssignment(ShuttleType type, int cycles, int seatsPerSortie) {
        this(type, cycles, seatsPerSortie,
                Math.max(1, cycles) * seatsPerSortie);
    }

    /** One of the company's own boats, flying with whatever is fitted to it. */
    public ShuttleAssignment(FittedBoat boat, int cycles, int seatsPerSortie) {
        this(Objects.requireNonNull(boat, "boat").pattern(), boat, cycles, seatsPerSortie,
                Math.max(1, cycles) * seatsPerSortie);
    }

    public ShuttleAssignment(ShuttleType type, int cycles, int seatsPerSortie,
                             int embarkedPersonnel) {
        this(type, type, cycles, seatsPerSortie, embarkedPersonnel);
    }

    public ShuttleAssignment(ShuttleType type, Airframe airframe, int cycles,
                             int seatsPerSortie, int embarkedPersonnel) {
        this.type = Objects.requireNonNull(type, "type");
        this.airframe = airframe != null ? airframe : type;
        this.cycles = Math.max(1, cycles);
        if (seatsPerSortie < 1 || seatsPerSortie > type.capacity) {
            throw new IllegalArgumentException("seatsPerSortie must be between 1 and "
                    + type.capacity + " for " + type + ": " + seatsPerSortie);
        }
        this.seatsPerSortie = seatsPerSortie;
        int fullBeforeFinal = (this.cycles - 1) * seatsPerSortie;
        int maximum = this.cycles * seatsPerSortie;
        if (embarkedPersonnel <= fullBeforeFinal || embarkedPersonnel > maximum) {
            throw new IllegalArgumentException("embarkedPersonnel must fill every sortie "
                    + "before a possibly partial final sortie: " + embarkedPersonnel
                    + " not in " + (fullBeforeFinal + 1) + ".." + maximum);
        }
        this.embarkedPersonnel = embarkedPersonnel;
    }

    public int seatsForCycle(int cycle) {
        if (cycle < 0 || cycle >= cycles) return 0;
        return Math.min(seatsPerSortie,
                embarkedPersonnel - cycle * seatsPerSortie);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ShuttleAssignment assignment)) return false;
        return type == assignment.type && airframe.equals(assignment.airframe)
                && cycles == assignment.cycles
                && seatsPerSortie == assignment.seatsPerSortie
                && embarkedPersonnel == assignment.embarkedPersonnel;
    }

    @Override
    public int hashCode() {
        int result = 31 * type.hashCode() + airframe.hashCode();
        result = 31 * result + cycles;
        result = 31 * result + seatsPerSortie;
        return 31 * result + embarkedPersonnel;
    }

    private static int capacityOf(ShuttleType type) {
        return Objects.requireNonNull(type, "type").capacity;
    }
}
