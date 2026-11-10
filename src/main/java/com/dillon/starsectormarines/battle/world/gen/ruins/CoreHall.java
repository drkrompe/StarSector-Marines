package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.List;

/**
 * An AI core plant's core hall ({@link WorksHall#CORE}), where core cabinets were grown, burned in and sealed:
 * <ul>
 *   <li><b>Feed.</b> Lattice substrate comes up the west rail to the clean room at the head.</li>
 *   <li><b>Lattice.</b> The clean room across the head, its lattice tanks in a row; chillers either side of it cooled
 *   the hall, their cold run under the raised floor.</li>
 *   <li><b>Assembly.</b> Cores rode the line south from the clean room to the pods.</li>
 *   <li><b>Burn-in.</b> Down the line each bay has a rack pod either side: two rows of racks round a cold aisle on
 *   the raised floor, a core cabinet burning in at the pod's line end.</li>
 *   <li><b>Sealing and dispatch.</b> At the line's foot the cabinets were sealed for the store.</li>
 * </ul>
 * The collapse: one pod's racks went over like dominoes, a chiller burst and its coolant ran across the floor. Dust
 * lies in the dead hall, deepest against the walls.
 */
final class CoreHall {

    static final String DUST = "floors.sand";

    private CoreHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.CORE, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("lattice").station("assembly").station("burn-in").station("sealing")
                .station("dispatch");
        int top = length - 2, sw = f.stationWidth();

        for (int y = HallFrame.DISPATCH + 1; y + 1 <= f.headY - 1; y += 2) {
            if (f.middle(f.cx, y) || f.middle(f.cx, y + 1)) continue;
            p.floor("doodad.ruins-core-conveyor", "doodad.ruins-mag-rail", f.cx, y, 2, 2, "assembly");
        }
        p.zone(f.cx, HallFrame.DISPATCH + 1, f.cx + 1, f.headY - 1, WorksHallPlan.Zone.LANE);
        f.feedRail("feed", top - 1);

        // The head: the clean room across the line's head, its lattice tanks; a chiller either side.
        p.zone(f.westL, top - 9, f.eastR, top - 3, WorksHallPlan.Zone.SPECIAL);
        for (int x = f.westR - 4; x + 1 <= f.eastL + 5; x += 3) {
            if (x <= f.cx + 1 && x + 1 >= f.cx) continue;
            f.stand("doodad.ruins-lattice-tank", "doodad.industrial-reactor-housing", x, top - 7, 2, 2, "lattice");
        }
        int chill = Math.min(5, sw);
        f.stand("doodad.ruins-chiller", "factory-machines.coolant", f.westL, top - 15, chill, 4, "lattice");
        f.stand("doodad.ruins-chiller-burst", "factory-machines.coolant-damaged", f.eastR - chill + 1, top - 15, chill, 4,
                WorksHallPlan.COLLAPSE);
        p.wear("doodad.ruins-coolant-stain", f.eastR - chill - 1, top - 17, 2, 2);
        p.wear("doodad.ruins-coolant-stain", f.eastR - chill - 2, top - 19, 2, 2);
        f.stand("doodad.ruins-line-pulpit", "doodad.industrial-control-console", f.cx - 1, top - 2, 3, 1, "lattice");

        // Down the line: a rack pod either side of each bay, on the raised floor.
        List<Integer> bays = f.bays();
        for (int b = 0; b < bays.size(); b++) {
            int y0 = bays.get(b);
            for (int side = 0; side < 2; side++) {
                int l = side == 0 ? f.westL : f.eastL, r = side == 0 ? f.westR : f.eastR;
                p.zone(l, y0, r, y0 + 7, WorksHallPlan.Zone.SPECIAL);
                boolean toppled = b == 1 && side == 0;
                int rackL = side == 0 ? l : l + 3, rackR = side == 0 ? r - 3 : r;
                if (toppled) {
                    f.stand("doodad.ruins-core-racks-toppled", "doodad.industrial-scrap-pile", rackL, y0 + 2,
                            rackR - rackL + 1, 3, WorksHallPlan.COLLAPSE);
                } else {
                    f.stand("doodad.ruins-core-rack-row", "doodad.civic-server-rack", rackL, y0 + 1, rackR - rackL + 1,
                            2, "burn-in");
                    f.stand("doodad.ruins-core-rack-row", "doodad.civic-server-rack", rackL, y0 + 5, rackR - rackL + 1,
                            2, "burn-in");
                }
                // The core burning in at the pod's line end.
                int cab = side == 0 ? r - 2 : l;
                String core = f.hash(cab, y0, 0xC0) % 3 == 0 ? "doodad.ruins-core-cabinet-wrecked"
                        : "doodad.ruins-core-cabinet-half";
                f.stand(core, null, cab, y0 + 3, 3, 3, "burn-in");
            }
            p.wear("doodad.ruins-floor-drain", f.cx + 3, y0 - 2, 1, 1);
        }
        f.tray("doodad.ruins-cable-tray", "doodad.ruins-cable-tray-fallen", f.cx - 3, HallFrame.DISPATCH + 2,
                f.headY - 1, bays.size() > 2 ? bays.get(2) + 2 : -1);
        f.tray("doodad.ruins-cable-tray", "doodad.ruins-cable-tray-fallen", f.cx + 2, HallFrame.DISPATCH + 2,
                f.headY - 1, -1);

        f.stand("doodad.ruins-cabinet-sealer", "doodad.ruins-line-end", f.cx - 5, HallFrame.DISPATCH - 1, 3, 3,
                "sealing");
        f.stand("doodad.ruins-cabinet-sealer", "doodad.ruins-line-end", f.cx + 4, HallFrame.DISPATCH - 1, 3, 3,
                "sealing");
        f.dispatch("dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-power-drop", "barracks-bunks.locker-worn", "burn-in");

        f.bank(DUST, 0, 2, 0xD5);
        return p;
    }
}
