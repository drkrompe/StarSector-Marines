package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.List;

/** Ground armory: the courtyard door enters the issue apron, with repair and stock behind it. */
public final class InstallationArmoryFitting implements RoomFitting {
    private static final String KIT = "armory-workshop.";
    @Override public RoomPurpose purpose() { return RoomPurpose.ARMORY; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 12 || h < 8) {
            compact(floor, w, h);
            return;
        }
        int counter = h - 3, staff = h - 4, gate = w - 4;
        reserve(floor, 0, h - 2, w, 2);
        reserve(floor, 0, staff, w, 1);
        reserve(floor, 0, 1, w, 1);
        reserve(floor, gate, 1, 2, h - 1);
        doors(floor, w, h, 1, staff, h - 2);
        int issue = 0;
        for (int x = 0; x + 2 <= w; x += 2) {
            if (floor.placeAssembly(x, counter, KIT + "counter", List.of()) && issue < 2) {
                InstallationWorkshopFitting.task(floor, x, staff, x, counter, Affordance.ISSUE);
                issue++;
            }
        }
        // The bench and lockers are one cell deep and worked on their bottom edge (a sprite's top edge lies at its
        // footprint's highest row), so they stand on the rack alley's far side and are worked from it.
        fixture(floor, 0, 2, "ready-locker", List.of(), 1, 1, 1, 2, Affordance.STOW);
        fixture(floor, 4, 2, "bench", List.of(new LayoutOp.Layer(0, 0, KIT + "bench-tools")),
                5, 1, 5, 2, Affordance.REPAIR);
        fixture(floor, w - 2, staff - 2, "ammo", List.of(), w - 1, staff, w - 1, staff - 1, Affordance.STOW);
        for (int x = 0; x + 3 <= w; x += 3)
            fixture(floor, x, 0, "rack", List.of(new LayoutOp.Layer(0, 0, KIT + "rifles"),
                    new LayoutOp.Layer(0, 0, KIT + "rack-frame")), x + 1, 1, x + 1, 0, Affordance.STOW);
    }

    private static void compact(RoomFloor floor, int w, int h) {
        if (w < 6 || h < 5) return;
        reserve(floor, 0, 2, w, 2);
        doors(floor, w, h, 2, 3);
        fixture(floor, 3, h - 1, "bench", List.of(new LayoutOp.Layer(0, 0, KIT + "bench-tools")),
                4, h - 2, 4, h - 1, Affordance.REPAIR);
        fixture(floor, w - 2, 0, "ammo", List.of(), w - 1, 2, w - 1, 1, Affordance.STOW);
        fixture(floor, 0, h - 1, "rack", List.of(new LayoutOp.Layer(0, 0, KIT + "rifles"),
                new LayoutOp.Layer(0, 0, KIT + "rack-frame")), 1, h - 2, 1, h - 1, Affordance.STOW);
    }

    private static void fixture(RoomFloor f, int x, int y, String id, List<LayoutOp.Layer> layers,
                                int sx, int sy, int fx, int fy, Affordance work) {
        if (f.placeAssembly(x, y, KIT + id, layers)) InstallationWorkshopFitting.task(f, sx, sy, fx, fy, work);
    }

    /** Join each external threshold to its nearest cross-aisle before placing equipment. */
    static void doors(RoomFloor f, int w, int h, int... aisles) {
        for (Doorway door : f.localDoors()) {
            int[] d = f.toCanonical(door.x(), door.y());
            int y = Math.max(0, Math.min(h - 1, d[1])), nearest = aisles[0];
            for (int aisle : aisles) if (Math.abs(aisle - y) < Math.abs(nearest - y)) nearest = aisle;
            reserve(f, Math.max(0, Math.min(w - 1, d[0])), Math.min(y, nearest), 1, Math.abs(y - nearest) + 1);
        }
    }

    static void reserve(RoomFloor f, int x, int y, int w, int h) {
        int[] r = f.toLocalRect(x, y, w, h);
        f.reserveLane(r[0], r[1], r[2], r[3]);
    }
}
