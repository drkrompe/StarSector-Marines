package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A biofactory's culture hall ({@link WorksHall#CULTURE}), where soil nanites and biofactory embryos were grown into
 * vat units:
 * <ul>
 *   <li><b>Feed.</b> Empty vat shells come up the west rail to the head.</li>
 *   <li><b>Tank farm.</b> Nutrient tanks at the head, pumps feeding the manifolds that run overhead down the line.</li>
 *   <li><b>Culture.</b> Down the line each bay has great culture vats either side, standing in wet basins that drain
 *   into the channel down the middle.</li>
 *   <li><b>Harvest.</b> Beside the vats, decanters drew the culture off; proving beds behind them grew the samples.</li>
 *   <li><b>Sealing and dispatch.</b> At the line's foot, fillers sealed the culture into vat units.</li>
 *   <li><b>Drain and waste.</b> The channel runs to the sump at the dispatch end; sludge went down the east lane.</li>
 * </ul>
 * The hall is wet: the channel and the basins are shallow water to wade, and the sump overflowed across the dispatch
 * end. The collapse: two vats burst and their culture grew out over the floor, a manifold came down across an aisle.
 * Growth is banked on the walls.
 */
final class CultureHall {

    static final String GROWTH = "floors.grass", EARTH = "floors.dirt";

    private CultureHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.CULTURE, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("tank farm").station("culture").station("harvest").station("sealing")
                .station("dispatch");
        int top = length - 2, sw = f.stationWidth();

        // The drain down the middle, a grate over it at every cross aisle, the sump at its foot.
        p.water(f.cx, HallFrame.DISPATCH - 1, f.cx + 1, f.headY - 1);
        for (int y = f.l / 2 - 2; y <= f.l / 2 + 2; y++) {
            p.water(f.cx, y, false);
            p.water(f.cx + 1, y, false);
        }
        f.feedRail("feed", top - 1);
        f.wasteLane("culture", "doodad.ruins-waste-skid", "doodad.ruins-slag-skid");

        // The tank farm: nutrient tanks either side of the line's head, pumps between them and the line.
        int tank = Math.min(4, sw);
        f.stand("doodad.ruins-nutrient-tank", "doodad.industrial-fluid-tank", f.westR - tank + 1, top - 4, tank, 4,
                "tank farm");
        f.stand("doodad.ruins-nutrient-tank", "doodad.industrial-fluid-tank", f.westR - tank + 1, top - 10, tank, 4,
                "tank farm");
        f.stand("doodad.ruins-nutrient-tank", "doodad.industrial-fluid-tank", f.eastL, top - 4, tank, 4, "tank farm");
        f.stand("doodad.ruins-nutrient-tank", "doodad.industrial-fluid-tank", f.eastL, top - 10, tank, 4, "tank farm");
        f.stand("doodad.ruins-feed-pumps", "doodad.industrial-pump-set", f.cx - 1, top - 4, 4, 2, "tank farm");
        f.stand("doodad.ruins-line-pulpit", "doodad.industrial-control-console", f.westL, top - 15, 3, 3, "tank farm");
        p.zone(f.westL, top - 10, f.eastR, top, WorksHallPlan.Zone.PLATE);

        // Down the line: vats in their wet basins, a decanter beside them, proving beds behind.
        List<int[]> burst = new ArrayList<>();
        List<Integer> bays = f.bays();
        int vat = Math.min(4, sw - 1);
        for (int b = 0; b < bays.size(); b++) {
            int y0 = bays.get(b);
            for (int side = 0; side < 2; side++) {
                int vx = side == 0 ? f.westR - vat + 1 : f.eastL;
                int basinL = side == 0 ? vx - 1 : vx, basinR = side == 0 ? f.westR : vx + vat;
                boolean broken = (b == 1 && side == 0) || (b == 2 && side == 1);
                for (int k = 0; k < 2; k++) {
                    int vy = y0 + 4 * k;
                    String id = broken && k == 0 ? "doodad.ruins-culture-vat-burst" : "doodad.ruins-culture-vat-large";
                    String standIn = broken && k == 0 ? "doodad.ruins-vat-unit-wrecked" : "doodad.ruins-culture-vat";
                    f.stand(id, standIn, vx, vy, vat, 4, broken && k == 0 ? WorksHallPlan.COLLAPSE : "culture");
                }
                // The basin: a ring of wet floor on the line side of the vats, draining to the channel.
                int wetX = side == 0 ? f.westR + 1 : f.eastL - 1;
                p.water(wetX, y0, wetX, y0 + 7);
                p.wear("doodad.ruins-drain-gutter", side == 0 ? wetX + 1 : wetX - 1, y0 + 3, 1, 2);
                p.zone(basinL, y0, basinR, y0 + 7, WorksHallPlan.Zone.BAY);
                if (broken) burst.add(new int[]{vx + vat / 2, y0 + 2});
                // The decanter beside the vats and the proving beds behind them.
                int dx = side == 0 ? vx - 2 : vx + vat;
                f.stand("doodad.ruins-decanter", "doodad.industrial-pump-set", dx, y0 + 3, 2, 2, "harvest");
                int bedFrom = side == 0 ? f.westL : dx + 2, bedTo = side == 0 ? dx - 2 : f.eastR - 1;
                for (int bx = bedFrom; bx <= bedTo; bx += 3) {
                    for (int by = y0 + 1; by <= y0 + 6; by += 2) {
                        if (f.hash(bx, by, 0x6B) % 4 == 0) continue;
                        f.stand("doodad.ruins-grow-bed", null, bx, by, 2, 1, "harvest");
                    }
                }
            }
            p.wear(f.hash(y0, b, 0x9A) % 2 == 0 ? "doodad.ruins-growth-patch-a" : "doodad.ruins-growth-patch-b",
                    f.cx - 4, y0 - 3, 2, 2);
            p.floor("doodad.ruins-grate-catwalk", null, f.cx, y0 - 3, 2, 2, "culture");
        }

        // A manifold down across the third bay's cross aisle; the rest overhead down both sides of the line.
        int fall = bays.size() > 2 ? bays.get(2) + 2 : -1;
        f.tray("doodad.ruins-feed-manifold", "doodad.ruins-feed-manifold-fallen", f.cx - 3, HallFrame.DISPATCH + 2,
                f.headY - 1, fall);
        f.tray("doodad.ruins-feed-manifold", "doodad.ruins-feed-manifold-fallen", f.cx + 2, HallFrame.DISPATCH + 2,
                f.headY - 1, -1);

        // Fillers either side of the sump, sealing the culture into vat units; the sump overflowed the dispatch end.
        f.stand("doodad.ruins-vat-filler", "doodad.ruins-vat-unit-half", f.cx - 6, HallFrame.DISPATCH - 1, 3, 3,
                "sealing");
        f.stand("doodad.ruins-vat-filler", "doodad.ruins-vat-unit-half", f.cx + 5, HallFrame.DISPATCH - 1, 3, 3,
                "sealing");
        f.dispatch("dispatch");
        for (int y = 1; y <= HallFrame.DISPATCH; y++) {
            for (int x = f.cx - 8; x <= f.cx + 9; x++) {
                if (Math.abs(x - f.cx - .5f) / 9f + (y - 1) / 6f < .55f + .45f * f.noise(x, y, 3, 0x5B)) {
                    p.water(x, y, true);
                }
            }
        }
        f.runways();
        f.serviceDrops("doodad.ruins-nutrient-drop", "barracks-bunks.locker-worn", "culture");

        // The environment: growth out of every burst vat, earth under it, growth banked on the walls.
        f.spread(EARTH, burst, 13, 0x6E);
        f.spread(GROWTH, burst, 8, 0x6F);
        f.bank(GROWTH, 0, 3, 0x61);
        return p;
    }
}
