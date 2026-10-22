package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * A frigate's sick bay: a short ward and its clinic across a stretcher aisle,
 * and a rehab pool at the quiet end.
 *
 * <p>Three real-length beds stand against one bulkhead with bedside cells between them; the
 * nurse station, the medical cabinet and the scrub sink face them across the
 * aisle. The pool is a narrow basin inside a walkable coping ring, water to
 * the renderer and closed, see-through deck to navigation, the same treatment
 * the gymnasium's pool has. Hydrotherapy is somebody's work, so the pool's two
 * places are treatment alongside the beds and the station: this is the
 * medic's room, not a recreation space. The pool goes to whichever end of the
 * compartment has fewer hatches.
 *
 * <p>Drawn for Second Wind's 10-by-6 reserve. Another size falls back to the
 * ordinary shipboard sick bay.
 */
public final class RehabSickBayFitting implements RoomFitting {
    private static final String KIT = "sickbay-clinic.";
    private static final String POOL = "ship-gym.";
    /** Rows a ward bed takes, head at the bulkhead: a real-length bed is three cells (2.2 m). */
    private static final int BED = 3;

    @Override public RoomPurpose purpose() { return RoomPurpose.PATIENT_WARD; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 10 || h < 6 || !available()) { SickBayFitting.shipboard().fit(floor); return; }
        // Authored with the pool aft (high x); turned round when the hatches are there.
        int far = EndFloor.doorsAtFarEnd(floor), near = EndFloor.canonicalDoors(floor).size() - far;
        EndFloor room = new EndFloor(floor, far > near, false);
        int pool = w - 4;

        // The stretcher aisle, joined to the coping round the pool.
        room.reserve(0, BED, pool, 2);
        room.reserve(pool, 0, 1, h); room.reserve(w - 1, 0, 1, h);
        room.reserve(pool, 0, 4, 1); room.reserve(pool, h - 1, 4, 1);
        room.clearHatches();

        // The basin: water to look at, closed and see-through to walk.
        room.surface(pool, 0, 4, h, POOL + "coping");
        room.closeOff(pool + 1, 1, 2, h - 2);
        room.ground(pool + 1, 1, 2, h - 2, GroundKind.WATER);
        room.surface(pool + 1, 1, 2, h - 2, POOL + "water");
        room.task(pool, 2, Affordance.TREAT, pool + 1, 2);
        room.task(w - 1, h - 3, Affordance.TREAT, w - 2, h - 3);

        // Beds head to the bulkhead with a bedside cell between them, packed
        // into whatever run of it the hatches leave; a monitor at each head.
        room.surface(0, BED, pool, 2, "ship-floor-trim.clinical-aisle");
        for (int x = 0, beds = 0; x < pool && beds < 3; x++) {
            if (!room.free(x, 0, 1, BED)) continue;
            if (!room.put(x, 0, KIT + "bed", KIT + (room.flippedAcross() ? "linen-high" : "linen-low"))) continue;
            beds++;
            // Worked from the aisle at the bed's foot.
            room.task(x, BED, Affordance.TREAT, x, BED - 1);
            if (x + 1 < pool && room.free(x + 1, 0, 1, 1) && room.put(x + 1, 0, KIT + "monitor"))
                room.reserve(x + 1, 1, 1, BED - 1);
            x++;
        }
        // The clinic bank across the aisle, worked from the row in front of it.
        if (room.put(0, h - 1, KIT + "station")) room.task(1, h - 2, Affordance.TREAT, 1, h - 1);
        if (room.put(2, h - 1, KIT + "cabinet")) room.task(2, h - 2, Affordance.STOW, 2, h - 1);
        if (room.put(4, h - 1, KIT + "sink")) room.task(4, h - 2, Affordance.WASH, 4, h - 1);
        room.put(pool - 1, h - 1, KIT + "trolley");
    }

    private static boolean available() {
        var tiles = TileRegistry.installed();
        for (String id : new String[]{"bed", "linen-low", "monitor", "station", "cabinet", "sink", "trolley"})
            if (tiles.doodad(KIT + id) == null) return false;
        return tiles.block(POOL + "coping") != null && tiles.block(POOL + "water") != null;
    }
}
