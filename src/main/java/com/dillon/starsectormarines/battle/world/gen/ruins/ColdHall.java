package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A cryoarithmetic plant's cold hall ({@link WorksHall#COLD}). Its product is the cryo rack unit, a computing engine
 * that only runs frozen, so the hall is built round its cold:
 * <ul>
 *   <li><b>Feed.</b> Unit chassis come up the west rail from the intake to the lattice benches at the head.</li>
 *   <li><b>Cold plant.</b> Two cold heads either side of the line's head, a manifold between them; their coolant
 *   headers run overhead down both sides of the line.</li>
 *   <li><b>Charge.</b> Down the line, each bay has a charge cell either side, open to the line, where a unit was
 *   flooded with coolant from the headers.</li>
 *   <li><b>Burn-in.</b> Behind each cell, banks of cryo racks where the charged units ran their first load.</li>
 *   <li><b>Sealing and dispatch.</b> At the line's foot, seal presses close the units for the store.</li>
 *   <li><b>Return.</b> Spent coolant runs back up the east lane's return trench.</li>
 * </ul>
 * The cold leaked when the plant died: ice spreads from the cold heads and the charge cells and thins toward
 * dispatch, rime is banked on every wall. The collapse: a header burst and came down by the second bay, its coolant
 * frozen in a ridge across the aisle; a carrier lies derailed off the line; one cell's unit froze half-charged and
 * racks stand empty where units were pulled.
 */
final class ColdHall {

    static final String ICE = "floors.snow";

    private ColdHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.COLD, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("lattice").station("charge").station("burn-in").station("sealing")
                .station("dispatch");
        int top = length - 2, sw = f.stationWidth();

        // The floor first: the line's conveyor and the return trench are lanes.
        for (int y = HallFrame.DISPATCH + 1; y + 1 <= f.headY - 1; y += 2) {
            p.floor("doodad.ruins-unit-conveyor", "doodad.ruins-mag-rail", f.cx, y, 2, 2, "charge");
        }
        p.zone(f.cx, HallFrame.DISPATCH + 1, f.cx + 1, f.headY - 1, WorksHallPlan.Zone.LANE);
        f.feedRail("feed", top - 1);
        for (int y = HallFrame.DISPATCH + 2; y + 1 <= f.headY + 4; y += 2) {
            p.floor("doodad.ruins-coolant-return", "doodad.ruins-trench-cover", f.laneX, y, 2, 2, "charge");
        }
        p.zone(f.laneX, HallFrame.DISPATCH + 2, f.laneX + 1, f.headY + 5, WorksHallPlan.Zone.LANE);

        // The head: lattice benches at the rail's stop, the cold heads either side of the line's head, the manifold
        // and the pulpit between them.
        f.stand("doodad.ruins-lattice-bench", "doodad.industrial-machine-tool", f.westL, top - 2, 3, 2, "lattice");
        f.stand("doodad.ruins-lattice-bench", "doodad.industrial-machine-tool", f.westL, top - 6, 3, 2, "lattice");
        int head = Math.min(6, sw);
        f.stand("doodad.ruins-cold-head", "factory-machines.coolant", f.westR - head + 1, top - 12, head, 6, "charge");
        f.stand("doodad.ruins-cold-head", "factory-machines.coolant", f.eastL, top - 12, head, 6, "charge");
        f.stand("doodad.ruins-cold-manifold", "factory-machines.coolant", f.cx - 1, top - 5, 4, 3, "charge");
        f.stand("doodad.ruins-line-pulpit", "doodad.industrial-control-console", f.eastL, top - 3, 3, 3, "charge");
        p.zone(f.westL, top - 12, f.eastR, top, WorksHallPlan.Zone.PLATE);

        // Down the line: a charge cell either side of every bay, burn-in racks behind it.
        List<int[]> cold = new ArrayList<>();
        cold.add(new int[]{f.westR - 2, top - 9});
        cold.add(new int[]{f.eastL + 2, top - 9});
        List<Integer> bays = f.bays();
        int cell = Math.min(5, sw);
        for (int b = 0; b < bays.size(); b++) {
            int y0 = bays.get(b);
            boolean frozen = b == 1;
            String cellId = frozen ? "doodad.ruins-charge-cell-open" : "doodad.ruins-charge-cell";
            if (f.stand(cellId, "doodad.ruins-cryo-unit-half", f.westR - cell + 1, y0 + 1, cell, 6, "charge")) {
                cold.add(new int[]{f.westR - 2, y0 + 4});
                p.zone(f.westR - cell, y0, f.westR + 1, y0 + 7, WorksHallPlan.Zone.BAY);
            }
            if (f.stand("doodad.ruins-charge-cell", "doodad.ruins-cryo-unit-half", f.eastL, y0 + 1, cell, 6, "charge")) {
                cold.add(new int[]{f.eastL + 2, y0 + 4});
                p.zone(f.eastL - 1, y0, f.eastL + cell, y0 + 7, WorksHallPlan.Zone.BAY);
            }
            // Burn-in: columns of racks behind each cell; units were pulled from some, so the bank has gaps.
            for (int side = 0; side < 2; side++) {
                int from = side == 0 ? f.westL : f.eastL + cell + 1, to = side == 0 ? f.westR - cell - 1 : f.eastR;
                for (int x = from; x <= to; x += 2) {
                    for (int y = y0 + 1; y + 1 <= y0 + 6; y += 2) {
                        if (f.hash(x, y, 0xB1) % 5 == 0) continue;
                        f.stand("doodad.ruins-cryo-rack", null, x, y, 1, 2, "burn-in");
                    }
                }
            }
            // Wear by the cells: coolant spilt, a floor drain in the cross aisle, frost where it pooled.
            p.wear("doodad.ruins-coolant-stain", f.cx - 3, y0 + 2, 2, 2);
            p.wear("doodad.ruins-floor-drain", f.cx + 3, y0 - 2, 1, 1);
            p.wear(f.hash(y0, b, 0xF7) % 2 == 0 ? "doodad.ruins-frost-patch-a" : "doodad.ruins-frost-patch-b",
                    f.westL + 1, y0 - 3, 2, 2);
            // Carriers stopped on the line, one derailed beside it.
            if (b == 0) f.stand("doodad.ruins-unit-carrier", "doodad.ruins-feed-pod", f.cx, y0 + 2, 2, 3, "charge");
            if (b == 2) {
                f.stand("doodad.ruins-unit-carrier-derailed", "doodad.ruins-feed-pod-derailed", f.cx + 2, y0 + 1, 2,
                        3, WorksHallPlan.COLLAPSE);
            }
        }

        // The collapse: a header burst over the second bay, its length down in the line aisle and its coolant frozen
        // in a ridge across the cross aisle beside it.
        int burst = bays.size() > 1 ? bays.get(1) : bays.isEmpty() ? f.headY - 6 : bays.get(0);
        f.tray("doodad.ruins-coolant-header", "doodad.ruins-coolant-header-fallen", f.cx - 3, HallFrame.DISPATCH + 2,
                f.headY - 1, burst + 2);
        f.tray("doodad.ruins-coolant-header", "doodad.ruins-coolant-header-fallen", f.cx + 2, HallFrame.DISPATCH + 2,
                f.headY - 1, -1);
        f.stand("doodad.ruins-ice-ridge", "doodad.ruins-frost-heatsink", f.westL + 2, burst - 3, 3, 2,
                WorksHallPlan.COLLAPSE);
        f.stand("doodad.ruins-fallen-girder", null, f.eastL - 1, burst - 2, 7, 1, WorksHallPlan.COLLAPSE);

        // Sealing at the line's foot, either side of it.
        f.stand("doodad.ruins-seal-press", "doodad.ruins-line-end", f.cx - 5, HallFrame.DISPATCH - 1, 3, 3, "sealing");
        f.stand("doodad.ruins-seal-press", "doodad.ruins-line-end", f.cx + 4, HallFrame.DISPATCH - 1, 3, 3, "sealing");
        f.dispatch("dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-coolant-drop", "barracks-bunks.locker-worn", "charge");

        // The environment: ice from every source of cold, rime banked on the walls.
        f.spread(ICE, cold, 7, 0x1CE);
        f.bank(ICE, 0, 3, 0x1C1);
        return p;
    }
}
