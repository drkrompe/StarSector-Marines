package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A drone replicator works' print hall ({@link WorksHall#PRINT}), where drone frames were printed, armed and flown:
 * <ul>
 *   <li><b>Feed.</b> Feedstock pods come up the west rail to the silos at the head.</li>
 *   <li><b>Silos.</b> Two feedstock silos either side of the line's head; powder lines run overhead down the line.</li>
 *   <li><b>Print.</b> Down the line each bay has a print cell either side: a frame on its bed under the gantry that
 *   printed it, a powder hopper beside it.</li>
 *   <li><b>Arm.</b> Behind the cells, benches where frames were armed and wired.</li>
 *   <li><b>Flight test.</b> Above the dispatch end the frames flew in a fenced cage across the hall.</li>
 *   <li><b>Dispatch.</b> Launch racks at the dispatch end; rejects went back down the east lane to be reprinted.</li>
 * </ul>
 * The collapse: a gantry came down across its bed, a silo split and its powder spilled over the head, and the cage is
 * strewn with frames that crashed on their last flights. Scorch marks the cage floor.
 */
final class PrintHall {

    static final String POWDER = "floors.sand", SCORCH = "volcanic.ash";

    private PrintHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.PRINT, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("silos").station("print").station("arm").station("flight test")
                .station("dispatch");
        int top = length - 2, sw = f.stationWidth();
        // The flight-test cage takes about a sixth of the hall's length above the dispatch end.
        int cageB = HallFrame.DISPATCH + 1, cageT = cageB + Math.max(7, Math.min(11, length / 6)) - 1;

        // The frame conveyor down the line, from the head to the cage.
        for (int y = cageT + 2; y + 1 <= f.headY - 1; y += 2) {
            if (f.middle(f.cx, y) || f.middle(f.cx, y + 1)) continue;
            p.floor("doodad.ruins-frame-conveyor", "doodad.ruins-mag-rail", f.cx, y, 2, 2, "print");
        }
        p.zone(f.cx, cageT + 2, f.cx + 1, f.headY - 1, WorksHallPlan.Zone.LANE);
        f.feedRail("feed", top - 1);
        f.wasteLane("print", "doodad.ruins-reject-skid", "doodad.ruins-slag-skid");

        // The head: two silos, one split; the pulpit between.
        int silo = Math.min(5, sw);
        f.stand("doodad.ruins-feedstock-silo", "doodad.industrial-fluid-tank", f.westR - silo + 1, top - 6, silo, 5,
                "silos");
        f.stand("doodad.ruins-feedstock-silo-split", "doodad.industrial-fluid-tank-damaged", f.eastL, top - 6, silo, 5,
                WorksHallPlan.COLLAPSE);
        f.stand("doodad.ruins-line-pulpit", "doodad.industrial-control-console", f.cx - 1, top - 4, 3, 3, "silos");
        p.zone(f.westL, top - 7, f.eastR, top, WorksHallPlan.Zone.PLATE);
        List<int[]> spill = new ArrayList<>();
        spill.add(new int[]{f.eastL + silo / 2, top - 7});

        // Down the line: a print cell either side of each bay, arming benches behind it.
        List<Integer> bays = f.bays(cageT + 3);
        int bed = Math.min(6, sw);
        for (int b = 0; b < bays.size(); b++) {
            int y0 = bays.get(b);
            for (int side = 0; side < 2; side++) {
                int bx = side == 0 ? f.westR - bed + 1 : f.eastL;
                p.floor("doodad.ruins-print-bed", "doodad.ruins-pallet-bay", bx, y0 + 1, bed, 6, "print");
                p.zone(bx, y0, bx + bed - 1, y0 + 7, WorksHallPlan.Zone.BAY);
                boolean fallen = b == 1 && side == 1;
                if (fallen) {
                    f.stand("doodad.ruins-print-gantry-fallen", "doodad.ruins-fallen-girder", bx, y0 + 3,
                            Math.min(7, sw), 2, WorksHallPlan.COLLAPSE);
                } else {
                    p.overhead("doodad.ruins-print-gantry", "doodad.ruins-crane-runway", bx, y0 + 1, bed, 6);
                    String frame = f.hash(bx, y0, 0xD5) % 3 == 0 ? "doodad.ruins-drone-frame-wrecked"
                            : "doodad.ruins-drone-frame-half";
                    f.stand(frame, null, bx + (bed - 3) / 2, y0 + 2, 3, 3, "print");
                }
                int hx = side == 0 ? bx - 2 : bx + bed;
                if (hx >= f.westL && hx + 1 <= f.eastR) {
                    f.stand("doodad.ruins-powder-hopper", "doodad.industrial-drum-cluster", hx, y0 + 5, 2, 2, "print");
                }
                // The arming benches behind the cell.
                int from = side == 0 ? f.westL : bx + bed + 2, to = side == 0 ? bx - 4 : f.eastR - 1;
                for (int ax = from; ax <= to; ax += 3) {
                    f.stand("doodad.ruins-arming-bench", "doodad.industrial-machine-tool", ax, y0 + 1, 2, 2, "arm");
                }
            }
            p.wear("doodad.ruins-oil-stain", f.cx + 3, y0 - 3, 2, 2);
            p.wear("doodad.ruins-floor-drain", f.cx - 3, y0 - 2, 1, 1);
        }
        f.tray("doodad.ruins-powder-line", "doodad.ruins-powder-line-fallen", f.cx - 3, cageT + 2, f.headY - 1, -1);
        f.tray("doodad.ruins-powder-line", "doodad.ruins-powder-line-fallen", f.cx + 2, cageT + 2, f.headY - 1,
                bays.size() > 2 ? bays.get(2) + 2 : -1);

        // The flight-test cage across the hall above dispatch: a fence round it, gaps to walk in by, crashed frames.
        int cl = f.westL - 1, cr = f.eastR + 1;
        for (int x = cl; x <= cr; x++) {
            boolean gap = Math.abs(x - f.cx - .5f) <= 2 || Math.abs(x - (cl + 3)) <= 1 || Math.abs(x - (cr - 3)) <= 1;
            if (gap) continue;
            f.stand("doodad.ruins-cage-fence", "doodad.industrial-fence-straight-h", x, cageT, 1, 1, "flight test");
            f.stand("doodad.ruins-cage-fence", "doodad.industrial-fence-straight-h", x, cageB, 1, 1, "flight test");
        }
        for (int y = cageB + 1; y < cageT; y++) {
            if (Math.abs(y - (cageB + cageT) / 2f) <= 1.5f) continue;
            f.stand("doodad.ruins-cage-fence", "doodad.industrial-fence-straight-v", cl, y, 1, 1, "flight test");
            f.stand("doodad.ruins-cage-fence", "doodad.industrial-fence-straight-v", cr, y, 1, 1, "flight test");
        }
        int mid = (cageB + cageT) / 2;
        f.stand("doodad.ruins-drone-frame-wrecked", null, cl + 4, mid - 2, 3, 3, WorksHallPlan.COLLAPSE);
        f.stand("doodad.ruins-drone-frame-wrecked", null, f.cx + 4, mid, 3, 3, WorksHallPlan.COLLAPSE);
        f.stand("doodad.ruins-test-pylon", "doodad.industrial-generator", f.cx - 5, mid + 1, 2, 2, "flight test");
        f.stand("doodad.ruins-test-pylon", "doodad.industrial-generator", cr - 4, mid - 2, 2, 2, "flight test");
        f.stand("doodad.ruins-drone-frame-wrecked", null, f.cx - 1, cageB + 1, 3, 3, WorksHallPlan.COLLAPSE);
        List<int[]> scorch = new ArrayList<>();
        scorch.add(new int[]{cl + 5, mid});
        scorch.add(new int[]{f.cx + 5, mid + 1});
        scorch.add(new int[]{f.cx, cageB + 2});
        for (int[] s : scorch) p.wear("doodad.ruins-scorch-mark", s[0] - 1, s[1] + 2, 2, 2);

        // Launch racks at dispatch.
        int rack = Math.min(6, sw);
        f.stand("doodad.ruins-launch-rack", "doodad.ruins-rack-stripped", f.westR - rack + 1, 4, rack, 2, "dispatch");
        f.stand("doodad.ruins-launch-rack", "doodad.ruins-rack-crates", f.eastL, 4, rack, 2, "dispatch");
        f.dispatch("dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-powder-drop", "barracks-bunks.locker-worn", "print");

        f.spread(POWDER, spill, 10, 0x9D);
        f.spread(SCORCH, scorch, 6, 0x9E);
        return p;
    }
}
