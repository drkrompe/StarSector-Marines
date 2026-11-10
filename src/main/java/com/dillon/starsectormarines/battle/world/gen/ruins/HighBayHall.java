package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A depot's high bay ({@link WorksHall#HIGH_BAY}), where the colony's goods were sorted, racked and shipped:
 * <ul>
 *   <li><b>Feed.</b> Totes come up the west rail from the intake to the sorter at the head.</li>
 *   <li><b>Sort.</b> Sorters either side of the line's head sent each tote to its rack.</li>
 *   <li><b>Transfer.</b> A transfer rail down the middle carried totes to the rack aisles.</li>
 *   <li><b>Racking.</b> Rack rows across the hall either side, a stacker crane's rail over every aisle between
 *   them.</li>
 *   <li><b>Pick and dispatch.</b> Picked goods were staged in the bays at the dispatch end.</li>
 * </ul>
 * The collapse: a stacker crane toppled into its rack row and spilled its crates across the aisle. Sand drifted in
 * through a breach in the east wall and is banked against every wall.
 */
final class HighBayHall {

    static final String SAND = "floors.sand";

    private HighBayHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.HIGH_BAY, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("sort").station("transfer").station("racking").station("dispatch");
        int top = length - 2, sw = f.stationWidth();
        f.feedRail("feed", top - 1);

        // The sorters at the head.
        f.stand("doodad.ruins-sorter", "doodad.stores-roller-conveyor", f.westR - 5, top - 4, 6, 3, "sort");
        f.stand("doodad.ruins-sorter", "doodad.stores-roller-conveyor", f.eastL, top - 4, 6, 3, "sort");
        f.stand("doodad.ruins-line-pulpit", "doodad.industrial-control-console", f.cx - 1, top - 2, 3, 1, "sort");
        p.zone(f.westL, top - 6, f.eastR, top, WorksHallPlan.Zone.PLATE);

        // The transfer rail down the middle.
        for (int y = HallFrame.DISPATCH + 1; y + 1 <= top - 6; y += 2) {
            p.floor("doodad.ruins-mag-rail", null, f.cx, y, 2, 2, "transfer");
        }
        p.zone(f.cx, HallFrame.DISPATCH + 1, f.cx + 1, top - 6, WorksHallPlan.Zone.LANE);

        // Rack rows across each side every four rows, a stacker's rail over the aisle between.
        int toppleRow = -1, row = 0;
        for (int y = top - 10; y >= HallFrame.DISPATCH + 3; y -= 4, row++) {
            for (int side = 0; side < 2; side++) {
                int l = side == 0 ? f.westL : f.eastL, r = side == 0 ? f.westR : f.eastR;
                if (row == 4 && side == 1) {
                    toppleRow = y;
                    f.stand("doodad.ruins-stacker-toppled", "doodad.ruins-fallen-girder", l, y, Math.min(7, sw), 2,
                            WorksHallPlan.COLLAPSE);
                    continue;
                }
                String id = f.hash(l, y, 0xBA) % 3 == 0 ? "doodad.ruins-rack-crates" : "doodad.ruins-rack-stripped";
                f.stand(id, null, l, y, Math.min(6, sw), 2, "racking");
                if (sw > 7) f.stand("doodad.ruins-rack-stripped", "doodad.stores-rack-run", l + 6, y, sw - 6, 2,
                        "racking");
                p.overhead("doodad.ruins-stacker-rail", "deck-motion.rack-lift-rail", l, y + 2, Math.min(6, sw), 2);
            }
        }
        // The crates the toppled stacker spilled.
        List<int[]> sand = new ArrayList<>();
        if (toppleRow > 0) {
            for (int k = 0; k < 3; k++) {
                f.stand("doodad.industrial-crate-stack-damaged", "doodad.industrial-crate-stack", f.eastL + 2 * k,
                        toppleRow + 2 + (k % 2), 1, 1, WorksHallPlan.COLLAPSE);
            }
        }
        sand.add(new int[]{f.w - 1, f.l * 3 / 5});

        f.dispatch("dispatch");
        f.stand("doodad.industrial-pallet-stack", null, f.westL + 1, 4, 1, 1, "dispatch");
        f.stand("doodad.industrial-pallet-stack", null, f.eastR - 1, 4, 1, 1, "dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-power-drop", "barracks-bunks.locker-worn", "racking");
        f.spread(SAND, sand, 16, 0xA1);
        f.bank(SAND, 1, 3, 0xA2);
        return p;
    }
}
