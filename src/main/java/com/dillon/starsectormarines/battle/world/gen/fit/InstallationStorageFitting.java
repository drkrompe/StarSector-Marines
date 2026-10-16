package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.LogisticsKit.Module;
import com.dillon.starsectormarines.battle.world.gen.fit.LogisticsKit.Stock;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.ArrayList;
import java.util.List;

/** Ground stores: a receiving apron and stocked bays joined by a handling spine. */
public final class InstallationStorageFitting implements RoomFitting {
    private final RoomPurpose purpose;
    public InstallationStorageFitting(RoomPurpose purpose) {
        if (purpose != RoomPurpose.STOCKROOM && purpose != RoomPurpose.LOADING_BAY)
            throw new IllegalArgumentException("Not a logistics room: " + purpose);
        this.purpose = purpose;
    }
    @Override public RoomPurpose purpose() { return purpose; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 4 || h < 4) return;
        int spine = (w - 2) / 2;
        int front = Math.min(2, h - 2);
        InstallationArmoryFitting.reserve(floor, spine, 0, 2, h);
        List<Integer> aisles = new ArrayList<>();
        aisles.add(front); aisles.add(front + 1);
        InstallationArmoryFitting.reserve(floor, 0, front, w, 2);
        // Each six-cell rack band ends in a two-cell cross-aisle.
        for (int y = front + 2; y < h; y += 8) {
            int cross = Math.min(y + 6, h - 2);
            InstallationArmoryFitting.reserve(floor, 0, cross, w, 2);
            aisles.add(cross); aisles.add(cross + 1);
        }
        InstallationArmoryFitting.doors(floor, w, h, aisles.stream().mapToInt(Integer::intValue).toArray());
        if (w < 6) {
            for (int x : new int[]{0, w - 1}) {
                if (floor.placeAssembly(x, 1, "colony-furnishings.shelf", List.of()))
                    InstallationWorkshopFitting.task(floor, x, 2, x, 1, Affordance.STOW);
            }
            return;
        }
        // A packing bench or dispatch desk is a complete station, never a tiny loose terminal.
        Module desk = LogisticsKit.desk(purpose == RoomPurpose.LOADING_BAY);
        if (desk.place(floor, 0, 1))
            InstallationWorkshopFitting.task(floor, 1, 2, 1, 1, Affordance.READOUT);
        for (int x = 0; x + 2 <= w; x += 4) {
            placeStock(floor, LogisticsKit.pallet(Stock.CARTONS), x, 0, x, 2, x, 1);
        }
        for (int y = front + 2; y + 2 <= h; y += 8) {
            int depth = Math.min(6, h - 2 - y);
            if (depth < 2) continue;
            for (int x = 0; x + 4 <= w; x += 4) {
                // Side handling aisle is shared by every stock slot in this bay.
                InstallationArmoryFitting.reserve(floor, x + 2, y, 2, depth);
                if (purpose == RoomPurpose.STOCKROOM && depth == 6) {
                    if (LogisticsKit.rack(Stock.CASES).place(floor, x, y))
                        for (int slot = 0; slot < 3; slot++)
                            InstallationWorkshopFitting.task(floor, x + 2, y + slot * 2,
                                    x + 1, y + slot * 2, Affordance.STOW);
                } else {
                    // Receiving floors use separated staging bays; shallow stores use the same complete pallets.
                    for (int row = y; row + 2 <= y + depth; row += 4)
                        placeStock(floor, LogisticsKit.pallet(Stock.CASES), x, row, x + 2, row, x + 1, row);
                }
            }
        }
    }

    private static void placeStock(RoomFloor floor, Module module, int x, int y, int sx, int sy, int fx, int fy) {
        if (module.place(floor, x, y)) InstallationWorkshopFitting.task(floor, sx, sy, fx, fy, Affordance.STOW);
    }
}
