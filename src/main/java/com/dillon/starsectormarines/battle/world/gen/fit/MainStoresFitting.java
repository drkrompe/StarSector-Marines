package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;

/** Ship stores: parallel picking racks, end cross-aisles and a separate pallet lane. */
public final class MainStoresFitting implements RoomFitting {
    private static final String KIT = "main-stores.";
    private static final String[] STOCK = {"cartons", "cases", "cartons", "drums"};

    @Override public RoomPurpose purpose() { return RoomPurpose.STOCKROOM; }

    @Override public void fit(RoomFloor floor) {
        if (TileRegistry.installed().doodad(KIT + "rack") == null) {
            new StockroomFitting().fit(floor);
            return;
        }
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 10 || h < 10) {
            fitLocker(floor, w, h);
            return;
        }
        int start = (h - 6) / 2;
        int end = start + 6;
        int cargo = w - 2;
        // Picking aisles remain two cells wide; pallet traffic has its own lane.
        reserve(floor, 0, start - 2, cargo, 2);
        reserve(floor, 0, end, cargo, 2);
        reserve(floor, cargo - 2, 0, 2, h);
        for (int x = 0; x + 4 <= cargo; x += 4) reserve(floor, x + 2, start, 2, 6);
        doorApproaches(floor, w, h, start, end, cargo);

        for (int x = 0, bank = 0; x + 2 <= cargo - 2; x += 4, bank++) {
            List<LayoutOp.Layer> layers = new ArrayList<>();
            // Keep each bank a commodity group. One deliberately picked slot exposes the shelf bed.
            for (int slot = 0; slot < 3; slot++) {
                if (bank % 4 == 2 && slot == 1) continue;
                layers.add(new LayoutOp.Layer(0, slot * 2, KIT + STOCK[bank % STOCK.length]));
            }
            layers.add(new LayoutOp.Layer(0, 0, KIT + "frame"));
            if (floor.placeAssembly(x, start, KIT + "rack", layers)) {
                for (int slot = 0; slot < 3; slot++)
                    task(floor, x + 2, start + slot * 2, Affordance.STOW, x + 1, start + slot * 2);
            }
        }
        for (int y = 0, load = 0; y + 2 <= h; y += 4, load++) {
            if (floor.placeAssembly(cargo, y, KIT + "pallet",
                    List.of(new LayoutOp.Layer(0, 0, KIT + STOCK[load % STOCK.length]))))
                task(floor, cargo - 1, y, Affordance.STOW, cargo, y);
        }
        // Desks use the otherwise shallow end margins, never the cross-aisles.
        // Try either end and several columns so an arbitrary hatch need not remove a job.
        placeDesk(floor, "tally", Affordance.READOUT, 0, w, h, start, end);
        placeDesk(floor, "packing", Affordance.REPAIR, 4, w, h, start, end);
    }

    private void placeDesk(RoomFloor floor, String piece, Affordance work, int preferred,
                           int w, int h, int start, int end) {
        for (int y : new int[]{0, h - 1}) {
            if (y == 0 && start < 3 || y == h - 1 && end + 2 >= h) continue;
            for (int offset = 0; offset < w - 4; offset += 4) {
                int x = (preferred + offset) % (w - 4);
                if (x + 3 > w - 4) continue;
                if (floor.placeAssembly(x, y, KIT + piece, List.of())) {
                    task(floor, x + 1, y == 0 ? 1 : h - 2, work, x + 1, y);
                    return;
                }
            }
        }
    }

    /** Small utility stores retain a worked pallet bank beside a two-cell gangway. */
    private void fitLocker(RoomFloor floor, int w, int h) {
        if (w < 2 || h < 4) { new StockroomFitting().fit(floor); return; }
        boolean lowHatch = floor.localDoors().stream()
                .anyMatch(d -> floor.toCanonical(d.x(), d.y())[1] < 0);
        int row = lowHatch ? h - 2 : 0;
        int stand = lowHatch ? h - 3 : 2;
        reserve(floor, 0, lowHatch ? 0 : 2, w, h - 2);
        for (Doorway door : floor.localDoors()) {
            int[] d = floor.toCanonical(door.x(), door.y());
            if (d[1] < 0 && lowHatch || d[1] >= h && !lowHatch) continue;
            if (d[1] >= (lowHatch ? 0 : 2) && d[1] < (lowHatch ? h - 2 : h)) continue;
            int x = Math.max(0, Math.min(w - 2, d[0]));
            reserve(floor, x, 0, 2, h);
        }
        for (int x = 0; x + 2 <= w; x += 2)
            if (floor.placeAssembly(x, row, KIT + "pallet",
                    List.of(new LayoutOp.Layer(0, 0, KIT + "cases"))))
                task(floor, x, stand, Affordance.STOW, x, lowHatch ? row : row + 1);
    }

    /** Join complete two-cell approaches to the nearest end or picking aisle. */
    private void doorApproaches(RoomFloor floor, int w, int h, int start, int end, int cargo) {
        for (Doorway door : floor.localDoors()) {
            int[] d = floor.toCanonical(door.x(), door.y());
            if (d[1] < 0 || d[1] >= h) {
                int x = Math.max(0, Math.min(w - 2, d[0]));
                if (d[1] < 0) reserve(floor, x, 0, 2, start);
                else reserve(floor, x, end, 2, h - end);
            } else {
                int y = Math.max(0, Math.min(h - 2, d[1]));
                if (d[0] < 0) reserve(floor, 0, y, 4, 2);
                else reserve(floor, cargo - 2, y, 4, 2);
            }
        }
    }

    private void reserve(RoomFloor floor, int x, int y, int w, int h) {
        int[] r = floor.toLocalRect(x, y, w, h);
        floor.reserveLane(r[0], r[1], r[2], r[3]);
    }
    private void task(RoomFloor floor, int x, int y, Affordance work, int fx, int fy) {
        int[] s = floor.toLocal(x, y), f = floor.toLocal(fx, fy);
        floor.fixtureTask(s[0], s[1], work, f[0], f[1]);
    }
}
