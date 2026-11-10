package com.dillon.starsectormarines.battle.world.gen.ruins;

/**
 * The great hall a themed plant made its product in ({@code ruins-expedition.md}, A hall is laid out from its process):
 * one mega hall in the outer interior band, in place of the line halls, laid from its process head to dispatch. Each
 * kind names what the plant made and how it plans its hall ({@link #plan(int, int, long)}); {@link #FORGE} is laid by
 * {@link RuinsDelveStage}'s own mega forge, and {@link #NONE} keeps the line halls.
 */
public enum WorksHall {
    /** No works hall: the plant keeps its line halls. */
    NONE,
    /** A nanoforge works' mega forge (owner pick {@code ruins-forge-process} b1), laid by the delve itself. */
    FORGE,
    /** A cryoarithmetic plant's cold hall: cold heads, charge cells, burn-in racks, all iced over. */
    COLD,
    /** A biofactory's culture hall: a tank farm, vat rows in wet bays, a drain down the middle to the sump. */
    CULTURE,
    /** A drone replicator's print hall: feedstock silos, print gantries, assembly, a flight-test cage. */
    PRINT,
    /** An AI core plant's core hall: lattice clean rooms, rack pods where the cores burned in, sealing. */
    CORE,
    /** A field-housing works' field hall: a proving torus, the housing line, shielded test cells, coolant loops. */
    FIELD,
    /** A synchrotron works' ring hall: a storage ring of magnet segments round the hall, the winding shop at its head. */
    RING,
    /** A mantle bore works' shaft hall: the bore shaft at the head, bore heads built down the line, spoil out south. */
    SHAFT,
    /** A catalytic core works' reactor train: tank farm, a row of catalytic columns, a bed changer's rail. */
    CRACKER,
    /** A fullerene spool works' draw line: furnace and draw tower, a tension line the hall's length, winders. */
    DRAW,
    /** A holosuite works' stage hall: emitter benches, stack assembly, a calibration stage ringed by stacks. */
    STAGE,
    /** A depot's high bay: a sorter at the head, rack rows with stacker cranes, dispatch docks. */
    HIGH_BAY,
    /** An armory's filling hall: a filling line, a proof range down one side, blast-walled magazines. */
    FILLING;

    /** Whether the plant lays a works hall. */
    public boolean any() { return this != NONE; }

    /**
     * This hall's plan at {@code width} by {@code length} cells, head to the north; the same seed plans the same hall.
     * {@link #FORGE} and {@link #NONE} have none.
     */
    public WorksHallPlan plan(int width, int length, long seed) {
        return switch (this) {
            case NONE, FORGE -> null;
            case COLD -> ColdHall.plan(width, length, seed);
            case CULTURE -> CultureHall.plan(width, length, seed);
            case PRINT -> PrintHall.plan(width, length, seed);
            case CORE -> CoreHall.plan(width, length, seed);
            case FIELD -> FieldHall.plan(width, length, seed);
            case RING -> RingHall.plan(width, length, seed);
            case SHAFT -> ShaftHall.plan(width, length, seed);
            case CRACKER -> CrackerHall.plan(width, length, seed);
            case DRAW -> DrawHall.plan(width, length, seed);
            case STAGE -> StageHall.plan(width, length, seed);
            case HIGH_BAY -> HighBayHall.plan(width, length, seed);
            case FILLING -> FillingHall.plan(width, length, seed);
        };
    }
}
