package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Conquest fortress band holds a packed ward, not ordinary city.
 *
 * <p>Counts room purposes rather than inspecting geometry, because what makes
 * the band a fortress is that the buildings in it are the ones a garrison needs.
 * A band full of shops and apartments passes every connectivity check and is
 * still the defect this work exists to fix.
 */
class FortressWardTest {

    private static final int W = 240;
    private static final int H = 160;

    /** Purposes only the fortress program places. */
    private static final RoomPurpose[] FORTRESS_PURPOSES = {
            RoomPurpose.VEHICLE_BAY, RoomPurpose.ARMORY, RoomPurpose.BARRACKS,
            RoomPurpose.ENGINE_ROOM, RoomPurpose.PARTS_CAGE, RoomPurpose.STOCKROOM,
            RoomPurpose.MESS_HALL, RoomPurpose.KEEP_ENTRY, RoomPurpose.CONTROL_ROOM };

    /** The ward's buildings are furnished, and its vehicle sheds hold real berths. */
    @Test
    void thePackedWardIsFurnished() {
        for (long seed : new long[] { 1L, 9L, 777L }) {
            MapResult map = new BspCityGenerator().generate(
                    W, H, seed, TraversalAxis.SOUTH_TO_NORTH);
            int inWard = 0;
            for (Doodad doodad : map.doodads) {
                if (map.topology.getRoomPurpose(doodad.cellX, doodad.cellY) != null) inWard++;
            }
            System.out.println("FURNISH seed=" + seed
                    + " doodads=" + map.doodads.size()
                    + " gantries=" + map.gantries.size());
            assertTrue(map.gantries.size() > 0,
                    "a fortress with vehicle sheds must publish machine berths: " + seed);
        }
    }

    @Test
    void everyCanonicalConquestMapPacksItsFortressWard() {
        for (TraversalAxis axis : TraversalAxis.values()) {
            for (long seed : new long[] { 1L, 5L, 9L, 42L, 777L }) {
                Map<RoomPurpose, Integer> counts = wardPurposes(seed, axis);
                int total = counts.values().stream().mapToInt(Integer::intValue).sum();
                System.out.println("WARD seed=" + seed + " axis=" + axis
                        + " cells=" + total + " " + counts);
                assertTrue(total > 0,
                        "conquest map " + seed + "/" + axis + " has no fortress ward");
                assertTrue(counts.containsKey(RoomPurpose.VEHICLE_BAY),
                        "a fortress without a vehicle shed is a walled district: "
                                + seed + "/" + axis);
            }
        }
    }

    private static Map<RoomPurpose, Integer> wardPurposes(long seed, TraversalAxis axis) {
        MapResult map = new BspCityGenerator().generate(W, H, seed, axis);
        Map<RoomPurpose, Integer> counts = new EnumMap<>(RoomPurpose.class);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                RoomPurpose purpose = map.topology.getRoomPurpose(x, y);
                for (RoomPurpose wanted : FORTRESS_PURPOSES) {
                    if (purpose != wanted) continue;
                    counts.merge(purpose, 1, Integer::sum);
                    break;
                }
            }
        }
        return counts;
    }
}
