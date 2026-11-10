package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A catalytic core works' reactor train ({@link WorksHall#CRACKER}), where catalyst beds were proved in the plant's own
 * reactor columns:
 * <ul>
 *   <li><b>Feed.</b> Fresh beds come up the west rail to the tank farm at the head.</li>
 *   <li><b>Feedstock.</b> Feed tanks and a preheat furnace at the head send the charge down the train.</li>
 *   <li><b>Train.</b> Down the middle of the hall a row of catalytic columns, one a bay, joined by transfer pipes;
 *   the hall's middle is a crossing between two of them.</li>
 *   <li><b>Bed change.</b> East of each column a bed-change bay: a bed being proved, the bed changer's rail down the
 *   east lane carrying them.</li>
 *   <li><b>Exchange.</b> West of each column its heat exchanger.</li>
 *   <li><b>Dispatch.</b> Proved beds were crated at the train's foot; the product drawn off to its tanks there.</li>
 * </ul>
 * The collapse: one column split and fell across the west aisle, its sludge pooled where it stood; the bed changer
 * lies derailed. Sulphur stains the floor round every column.
 */
final class CrackerHall {

    static final String SULPHUR = "toxic-ground.sulphur", SLUDGE = "toxic-ground.sludge";

    private CrackerHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.CRACKER, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("feedstock").station("train").station("bed change").station("dispatch");
        int top = length - 2, sw = f.stationWidth();
        f.feedRail("feed", top - 1);
        f.wasteLane("bed change", "doodad.ruins-bed-changer-derailed", "doodad.ruins-slag-skid-crashed");

        // The head: feed tanks and the preheat furnace.
        int tank = Math.min(4, sw);
        f.stand("doodad.ruins-feed-tank", "doodad.industrial-fluid-tank", f.westR - tank + 1, top - 4, tank, 4,
                "feedstock");
        f.stand("doodad.ruins-feed-tank", "doodad.industrial-fluid-tank", f.westR - tank + 1, top - 9, tank, 4,
                "feedstock");
        f.stand("doodad.ruins-preheat-furnace", "doodad.industrial-generator", f.eastL, top - 5, Math.min(5, sw), 4,
                "feedstock");
        f.stand("doodad.ruins-line-pulpit", "doodad.industrial-control-console", f.eastL, top - 10, 3, 3, "feedstock");
        p.zone(f.westL, top - 10, f.eastR, top, WorksHallPlan.Zone.PLATE);

        // The train down the middle: a column a bay, the transfer pipe between, bays either side.
        List<int[]> stain = new ArrayList<>();
        List<Integer> bays = f.bays();
        int col = 5;
        for (int b = 0; b < bays.size(); b++) {
            int y0 = bays.get(b);
            int cl = f.cx - 2;
            boolean split = b == 1;
            if (split) {
                // The split column lies across the west aisle; its sludge pooled where it stood.
                f.stand("doodad.ruins-reactor-column-fallen", "doodad.ruins-fallen-girder", HallFrame.TRUNK, y0 + 2, 9, 3,
                        WorksHallPlan.COLLAPSE);
                p.water(cl - 1, y0, cl + col, y0 + 6);
                for (int y = y0; y <= y0 + 6; y++) for (int x = cl - 1; x <= cl + col; x++) p.skin(x, y, SLUDGE);
            } else if (f.stand("doodad.ruins-reactor-column", "doodad.industrial-reactor-housing", cl, y0 + 1, col, col,
                    "train")) {
                stain.add(new int[]{f.cx, y0 + 3});
            }
            p.floor("doodad.ruins-transfer-pipe", "doodad.ruins-trench-cover", f.cx, y0 - 3, 2, 2, "train");
            // The exchanger west of the column, the bed-change bay east of it.
            f.stand("doodad.ruins-heat-exchanger", "factory-machines.coolant", f.westR - 3, y0 + 2, 3, 3, "train");
            int bx = f.eastR - 3;
            p.zone(f.eastL, y0, f.eastR, y0 + 7, WorksHallPlan.Zone.BAY);
            String bed = f.hash(bx, y0, 0xCA) % 3 == 0 ? "doodad.ruins-catalyst-bed-wrecked"
                    : "doodad.ruins-catalyst-bed-half";
            f.stand(bed, null, bx, y0 + 1, 3, 3, "bed change");
            f.stand("doodad.ruins-pipe-rack", "doodad.industrial-pipe-bundle", f.westL, y0 + 1, 2, 6, "train");
        }
        f.tray("doodad.ruins-pipe-run", "doodad.ruins-pipe-run-fallen", f.cx - 3, HallFrame.DISPATCH + 2, f.headY - 1,
                -1);
        f.tray("doodad.ruins-pipe-run", "doodad.ruins-pipe-run-fallen", f.cx + 2, HallFrame.DISPATCH + 2, f.headY - 1,
                bays.size() > 2 ? bays.get(2) + 2 : -1);

        // Dispatch: product tanks and crated beds.
        f.stand("doodad.ruins-product-tank", "doodad.industrial-fluid-tank", f.cx - 6, HallFrame.DISPATCH - 2, 3, 3,
                "dispatch");
        f.stand("doodad.ruins-catalyst-bed-whole", null, f.cx + 4, HallFrame.DISPATCH - 2, 3, 3, "dispatch");
        f.dispatch("dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-steam-drop", "barracks-bunks.locker-worn", "train");
        f.spread(SULPHUR, stain, 6, 0xCB);
        return p;
    }
}
