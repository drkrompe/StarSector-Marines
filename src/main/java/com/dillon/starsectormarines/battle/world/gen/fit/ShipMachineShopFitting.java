package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;

/** Machining faces two service benches across an aisle; parts occupy one layered picking rack. */
public final class ShipMachineShopFitting implements RoomFitting {
    private static final String KIT = "machine-shop.";
    private static final String[] LOADS = {"bar-stock", "bearings", "electrical"};
    @Override public RoomPurpose purpose() { return RoomPurpose.PARTS_CAGE; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 14 || h < 10 || TileRegistry.installed().doodad(KIT + "lathe") == null
                || TileRegistry.installed().doodad("main-stores.rack") == null) {
            new PartsCageFitting().fit(floor);
            return;
        }
        reserve(floor, 0, 0, w, 1); reserve(floor, 0, h-1, w, 1);
        reserve(floor, 0, 0, 1, h); reserve(floor, w-1, 0, 1, h);
        reserve(floor, 0, 4, 11, 2);
        reserve(floor, 10, 0, 1, h);
        for (Doorway door : floor.localDoors())
            floor.reserveLane(door.x()-1, door.y()-1, 3, 3);

        station(floor, "lathe", 1, 1, 2, 3, 2, 2, Affordance.FABRICATE);
        station(floor, "mill", 6, 1, 7, 3, 7, 2, Affordance.FABRICATE);
        station(floor, "assembly-bench", 1, 7, 2, 6, 2, 7, Affordance.REPAIR);
        station(floor, "inspection-bench", 6, 7, 7, 6, 7, 7, Affordance.READOUT);

        List<LayoutOp.Layer> layers = new ArrayList<>();
        for (int slot=0;slot<LOADS.length;slot++)
            layers.add(new LayoutOp.Layer(0, slot*2, KIT + LOADS[slot]));
        layers.add(new LayoutOp.Layer(0, 0, "main-stores.frame"));
        // Bed, contents and retaining frame share one physical footprint and pose.
        if (floor.placeAssembly(11, 2, "main-stores.rack", layers))
            for (int slot=0;slot<LOADS.length;slot++)
                task(floor, 10, 2+slot*2, 11, 2+slot*2, Affordance.STOW);
    }

    private void station(RoomFloor floor, String id, int x, int y,
                         int sx, int sy, int fx, int fy, Affordance work) {
        if (floor.placeAssembly(x, y, KIT + id, List.of())) task(floor, sx, sy, fx, fy, work);
    }
    private void task(RoomFloor floor, int x, int y, int fx, int fy, Affordance work) {
        int[] stand = floor.toLocal(x, y), fixture = floor.toLocal(fx, fy);
        floor.fixtureTask(stand[0], stand[1], work, fixture[0], fixture[1]);
    }
    private void reserve(RoomFloor floor, int x, int y, int w, int h) {
        int[] rect = floor.toLocalRect(x, y, w, h);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
