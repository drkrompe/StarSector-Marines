package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.List;

/** Shallow bulkhead banks around a service aisle, sized for the ship's utility pockets. */
public final class ShipUtilityFitting implements RoomFitting {
    private static final String KIT = "ship-utility.";
    private final RoomPurpose purpose;

    public ShipUtilityFitting(RoomPurpose purpose) {
        if (purpose != RoomPurpose.SERVER_ROOM && purpose != RoomPurpose.PARTS_CAGE
                && purpose != RoomPurpose.GENERIC)
            throw new IllegalArgumentException("Not a ship utility purpose: " + purpose);
        this.purpose = purpose;
    }

    @Override public RoomPurpose purpose() { return purpose; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 3 || h < 3) return;
        int[] entire = floor.toLocalRect(0, 0, w, h);
        floor.markFloorSurface(entire[0], entire[1], entire[2], entire[3], "auto-shop.concrete");
        reserve(floor, 0, 1, w, h - 2);
        // Preserve every cell of a wide hatch, including a hatch arriving through a fixture bank.
        for (Doorway door : floor.localDoors()) {
            int[] d = floor.toCanonical(door.x(), door.y());
            int x = Math.max(0, Math.min(w - 1, d[0]));
            if (d[1] <= 0) reserve(floor, x, 0, 1, 2);
            if (d[1] >= h - 1) reserve(floor, x, h - 2, 1, 2);
        }
        if (purpose == RoomPurpose.SERVER_ROOM) {
            first(floor, "console", Affordance.READOUT, true);
            first(floor, "electrical", null, true);
            first(floor, "pump", null, true);
            for (int y : new int[]{0, h - 1}) for (int x = 0; x < w; x++)
                station(floor, x, y, "server", List.of(), null);
        } else if (purpose == RoomPurpose.PARTS_CAGE) {
            first(floor, "counter", Affordance.REPAIR, true);
            first(floor, "console", Affordance.READOUT, true);
            List<LayoutOp.Layer> stock = List.of(new LayoutOp.Layer(0, 0, KIT + "parts"),
                    new LayoutOp.Layer(0, 0, KIT + "frame"));
            for (int y : new int[]{0, h - 1}) {
                for (int x = 0; x + 2 <= w; x++) station(floor, x, y, "shelf", stock, Affordance.STOW);
                // A hatch can leave one cell beside a shelf: a closed supply locker fits it honestly.
                for (int x = 0; x < w; x++) station(floor, x, y, "supply-locker", List.of(), Affordance.STOW);
            }
        } else {
            // These closets are equipment, not new watch postings. Rounds are added by the fill stage.
            first(floor, "pump", null, false);
            first(floor, "electrical", null, false);
            first(floor, "supply-locker", null, true);
        }
    }

    private void first(RoomFloor floor, String id, Affordance work, boolean bottomFirst) {
        int last = floor.canonicalHeight() - 1;
        for (int y : bottomFirst ? new int[]{last, 0} : new int[]{0, last})
            for (int x = 0; x < floor.canonicalWidth(); x++)
                if (station(floor, x, y, id, List.of(), work)) return;
    }

    private boolean station(RoomFloor floor, int x, int y, String id,
                            List<LayoutOp.Layer> layers, Affordance work) {
        if (!floor.placeAssembly(x, y, KIT + id, layers)) return false;
        if (work != null) {
            int[] stand = floor.toLocal(x, y == 0 ? 1 : y - 1);
            int[] fixture = floor.toLocal(x, y);
            floor.fixtureTask(stand[0], stand[1], work, fixture[0], fixture[1]);
        }
        return true;
    }

    private void reserve(RoomFloor floor, int x, int y, int w, int h) {
        int[] rect = floor.toLocalRect(x, y, w, h);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
