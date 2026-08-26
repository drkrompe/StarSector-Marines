package com.dillon.starsectormarines.battle.world.gen.ship.fit;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * How one kind of compartment is furnished.
 *
 * <p>A fitting owns the arrangement a room of its purpose takes — bunk rows
 * either side of an aisle, benches along a bulkhead, gantries with service
 * access — and produces that arrangement at whatever {@link RoomFit} it is
 * handed. It never decides how large the room is: the footprint arrives already
 * placed, and a refit changes what fits inside it.
 *
 * <p>Fittings are selected by {@link #purpose()} alone. A compartment says what
 * it is for, and that is the whole of the dispatch — no coordinate table, no
 * per-deck special case.
 */
public interface RoomFitting {

    /** The compartment purpose this fitting furnishes. */
    RoomPurpose purpose();

    /**
     * Furnish one compartment. Reserve circulation before placing anything, and
     * place fixtures in groups rather than as isolated points.
     */
    void fit(CompartmentFloor floor);
}
