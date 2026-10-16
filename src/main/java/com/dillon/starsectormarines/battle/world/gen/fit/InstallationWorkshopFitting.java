package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.List;

/** A ground workshop: machining, a repair bench and stocked parts beside connected working aisles. */
public final class InstallationWorkshopFitting implements RoomFitting {
    @Override public RoomPurpose purpose() { return RoomPurpose.PARTS_CAGE; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 8 || h < 8) {
            compact(floor, w, h);
            return;
        }
        int aisle = h - 4;
        reserve(floor, 0, 0, w, 1);
        reserve(floor, 0, h - 1, w, 1);
        reserve(floor, 0, 0, 1, h);
        reserve(floor, w - 1, 0, 1, h);
        reserve(floor, 0, aisle, w - 3, 2);
        reserve(floor, w - 4, 0, 1, h);
        for (Doorway door : floor.localDoors()) {
            int[] d = floor.toCanonical(door.x(), door.y());
            int x = Math.max(0, Math.min(w - 1, d[0]));
            int from = Math.min(Math.max(0, d[1]), aisle);
            int to = Math.max(Math.min(h - 1, d[1]), aisle + 1);
            reserve(floor, x, from, 1, to - from + 1);
        }
        station(floor, 1, aisle - 3, "factory-machines.fabricator", 2, aisle, 2, aisle - 1, Affordance.FABRICATE);
        station(floor, 1, h - 2, "main-stores.packing", 2, h - 3, 2, h - 2, Affordance.REPAIR);
        if (w >= 12)
            station(floor, 5, aisle - 2, "factory-machines.assembly", 6, aisle, 6, aisle - 1, Affordance.REPAIR);
        rack(floor, w - 3, 1, w - 4);
    }

    private static void compact(RoomFloor floor, int w, int h) {
        if (w < 4 || h < 4) return;
        int aisle = (h - 2) / 2;
        reserve(floor, 0, aisle, w, 2);
        for (Doorway door : floor.localDoors()) {
            int[] d = floor.toCanonical(door.x(), door.y());
            int from = Math.min(Math.max(0, d[1]), aisle);
            int to = Math.max(Math.min(h - 1, d[1]), aisle + 1);
            reserve(floor, Math.max(0, Math.min(w - 1, d[0])), from, 1, to - from + 1);
        }
        station(floor, 0, 0, "main-stores.packing", 1, 1, 1, 0, Affordance.REPAIR);
        for (int x = 0; x < w; x += 2)
            station(floor, x, h - 1, "colony-furnishings.shelf", x, h - 2, x, h - 1, Affordance.STOW);
    }

    static void rack(RoomFloor floor, int x, int y, int standX) {
        if (floor.placeAssembly(x, y, "main-stores.rack", List.of(
                new LayoutOp.Layer(0, 0, "main-stores.cases"),
                new LayoutOp.Layer(0, 2, "main-stores.cases"),
                new LayoutOp.Layer(0, 4, "main-stores.cartons"),
                new LayoutOp.Layer(0, 0, "main-stores.frame")))) {
            int fixtureX = standX < x ? x : x + 1;
            for (int slot = 0; slot < 3; slot++)
                task(floor, standX, y + 2 * slot, fixtureX, y + 2 * slot, Affordance.STOW);
        }
    }

    static void station(RoomFloor floor, int x, int y, String id,
                        int sx, int sy, int fx, int fy, Affordance work) {
        if (floor.placeAssembly(x, y, id, List.of())) task(floor, sx, sy, fx, fy, work);
    }

    static void task(RoomFloor floor, int sx, int sy, int fx, int fy, Affordance work) {
        int[] stand = floor.toLocal(sx, sy), fixture = floor.toLocal(fx, fy);
        floor.fixtureTask(stand[0], stand[1], work, fixture[0], fixture[1]);
    }

    private static void reserve(RoomFloor floor, int x, int y, int w, int h) {
        int[] r = floor.toLocalRect(x, y, w, h);
        floor.reserveLane(r[0], r[1], r[2], r[3]);
    }
}
