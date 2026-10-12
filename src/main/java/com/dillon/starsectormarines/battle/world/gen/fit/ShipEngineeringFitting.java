package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.List;

/** Complete piped machinery skids, service alleys and a separate control/workshop apron. */
public final class ShipEngineeringFitting implements RoomFitting {
    private static final String KIT = "ship-engineering.";
    private final RoomPurpose purpose;

    public ShipEngineeringFitting(RoomPurpose purpose) {
        if (purpose != RoomPurpose.ENGINE_ROOM && purpose != RoomPurpose.PRODUCTION_FLOOR)
            throw new IllegalArgumentException("Not an engineering space: " + purpose);
        this.purpose = purpose;
    }
    @Override public RoomPurpose purpose() { return purpose; }

    @Override public void fit(RoomFloor floor) {
        boolean drive = purpose == RoomPurpose.ENGINE_ROOM;
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < (drive ? 24 : 20) || h < (drive ? 22 : 14)
                || TileRegistry.installed().doodad(KIT + "drive-train") == null) {
            (drive ? MachinerySpaceFitting.driveRoom() : MachinerySpaceFitting.auxiliaryPlant()).fit(floor);
            return;
        }
        reserve(floor, 0, 0, w, 1); reserve(floor, 0, h-1, w, 1);
        reserve(floor, 0, 0, 1, h); reserve(floor, w-1, 0, 1, h);
        for (Doorway door : floor.localDoors())
            floor.reserveLane(door.x()-1, door.y()-1, 3, 3);
        paint(floor, 0, 0, w, h, "engineering");
        if (drive) drive(floor); else auxiliary(floor);
    }

    private void drive(RoomFloor floor) {
        paint(floor, 1, 2, 22, 7, "service-pad");
        paint(floor, 1, 11, 22, 5, "service-pad");
        paint(floor, 1, 9, 22, 2, "service-aisle");
        paint(floor, 1, 18, 22, 3, "service-aisle");
        reserve(floor, 0, 9, 24, 3);
        reserve(floor, 0, 16, 24, 2);
        if (put(floor, 3, 3, "drive-train")) {
            task(floor, 6, 9, 6, 8, Affordance.TEND);
            task(floor, 16, 9, 16, 8, Affordance.REPAIR);
        }
        machine(floor, 2, 12, "cooling", 4, 11, Affordance.TEND);
        machine(floor, 9, 12, "generator", 11, 11, Affordance.TEND);
        machine(floor, 16, 12, "life-support", 18, 11, Affordance.TEND);
        control(floor, 2, 19);
        machine(floor, 7, 18, "switchgear", 8, 17, Affordance.READOUT);
        machine(floor, 12, 18, "bench", 13, 17, Affordance.REPAIR);
        machine(floor, 19, 18, "spares", 20, 17, Affordance.STOW);
    }

    private void auxiliary(RoomFloor floor) {
        paint(floor, 1, 1, 7, 5, "service-pad");
        paint(floor, 11, 1, 7, 5, "service-pad");
        paint(floor, 1, 6, 18, 2, "service-aisle");
        paint(floor, 1, 9, 18, 4, "service-aisle");
        reserve(floor, 0, 6, 20, 2);
        reserve(floor, 9, 0, 2, 8);
        if (put(floor, 2, 2, "cooling")) task(floor, 4, 6, 4, 5, Affordance.REPAIR);
        if (put(floor, 12, 2, "life-support")) task(floor, 14, 6, 14, 5, Affordance.TEND);
        machine(floor, 2, 8, "spares", 3, 7, Affordance.STOW);
        control(floor, 2, 11);
        machine(floor, 8, 9, "bench", 9, 8, Affordance.FABRICATE);
        machine(floor, 14, 9, "switchgear", 15, 8, Affordance.READOUT);
    }

    private void control(RoomFloor floor, int x, int y) {
        if (put(floor, x, y, "control")) {
            task(floor, x, y-1, x, y, Affordance.WATCH);
            task(floor, x+2, y-1, x+2, y, Affordance.READOUT);
        }
    }
    private void machine(RoomFloor floor, int x, int y, String id, int sx, int sy, Affordance work) {
        if (put(floor, x, y, id)) task(floor, sx, sy, sx, y, work);
    }
    private boolean put(RoomFloor floor, int x, int y, String id) {
        // Pipes never cross assembly boundaries: the entire connected circuit is one fixture.
        return floor.placeAssembly(x, y, KIT + id, List.of());
    }
    private void task(RoomFloor floor, int x, int y, int fx, int fy, Affordance work) {
        int[] stand = floor.toLocal(x, y), fixture = floor.toLocal(fx, fy);
        floor.fixtureTask(stand[0], stand[1], work, fixture[0], fixture[1]);
    }
    private void paint(RoomFloor floor, int x, int y, int w, int h, String material) {
        int[] r = floor.toLocalRect(x, y, w, h);
        floor.markFloorSurface(r[0], r[1], r[2], r[3], "ship-floor-trim." + material);
    }

    private void reserve(RoomFloor floor, int x, int y, int w, int h) {
        int[] rect = floor.toLocalRect(x, y, w, h);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
