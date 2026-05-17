package com.dillon.starsectormarines.battle.air;

/**
 * One transport's commitment to a mission: which {@link ShuttleType} it is and
 * how many sorties it'll fly. A player with one Valkyrie covering a 3-drop
 * mission produces one {@code ShuttleAssignment(VALKYRIE, 3)}; the same mission
 * with three Aeroshuttles produces three {@code ShuttleAssignment(AEROSHUTTLE, 1)}
 * assignments. The sim cycles the shuttle through state machine the appropriate
 * number of times via the {@link Shuttle#totalCycles} field set from this.
 */
public final class ShuttleAssignment {

    public final ShuttleType type;
    public final int cycles;

    public ShuttleAssignment(ShuttleType type, int cycles) {
        this.type = type;
        this.cycles = Math.max(1, cycles);
    }
}
