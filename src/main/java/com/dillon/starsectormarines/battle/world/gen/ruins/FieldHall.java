package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A field-housing works' field hall ({@link WorksHall#FIELD}), where fusion lamps, plasma dynamos and hypershunt taps
 * had their field housings wound, built and proved:
 * <ul>
 *   <li><b>Feed.</b> Coil stock comes up the west rail to the winders at the head.</li>
 *   <li><b>Winding.</b> Coil winders by the rail's stop.</li>
 *   <li><b>Proving.</b> The proving torus fills the head in its shielded pit: every housing's field was first lit
 *   against it.</li>
 *   <li><b>Assembly.</b> Housings rode the line south to the test cells.</li>
 *   <li><b>Test.</b> Down the line each bay has a shielded test cell either side, open to the line: a housing on its
 *   stand inside three walls of shield block.</li>
 *   <li><b>Coolant.</b> The coolant loop runs down the east lane, a heat exchanger at each bay.</li>
 *   <li><b>Dispatch.</b> Housings were crated at the line's foot.</li>
 * </ul>
 * The collapse: one test cell flashed and blew its shield walls out across the aisle, scorching the floor round it;
 * the loop burst and coolant pooled at its junctions.
 */
final class FieldHall {

    static final String SCORCH = "volcanic.ash";

    private FieldHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.FIELD, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("winding").station("proving").station("assembly").station("test")
                .station("dispatch");
        int top = length - 2, sw = f.stationWidth();

        for (int y = HallFrame.DISPATCH + 1; y + 1 <= f.headY - 1; y += 2) {
            p.floor("doodad.ruins-housing-conveyor", "doodad.ruins-mag-rail", f.cx, y, 2, 2, "assembly");
        }
        p.zone(f.cx, HallFrame.DISPATCH + 1, f.cx + 1, f.headY - 1, WorksHallPlan.Zone.LANE);
        f.feedRail("feed", top - 1);
        for (int y = HallFrame.DISPATCH + 2; y + 1 <= f.headY + 4; y += 2) {
            p.floor("doodad.ruins-coolant-loop", "doodad.ruins-trench-cover", f.laneX, y, 2, 2, "test");
        }
        p.zone(f.laneX, HallFrame.DISPATCH + 2, f.laneX + 1, f.headY + 5, WorksHallPlan.Zone.LANE);

        // The head: winders at the rail's stop, the proving torus in its shield ring across the line's head.
        f.stand("doodad.ruins-coil-winder", "doodad.industrial-cable-reel", f.westL, top - 3, 3, 3, "winding");
        f.stand("doodad.ruins-coil-winder", "doodad.industrial-cable-reel", f.westL, top - 8, 3, 3, "winding");
        int tl = f.westR - 2, tr = f.eastL + 2, tb = top - 12, tt = top - 1;
        f.stand("doodad.ruins-proving-torus", "doodad.industrial-reactor-housing", tl + 2, tb + 2, tr - tl - 3,
                tt - tb - 3, "proving");
        for (int x = tl; x <= tr; x++) {
            if (Math.abs(x - f.cx - .5f) <= 1.5f) continue;
            f.stand("doodad.ruins-shield-block", "doodad.industrial-crate-stack", x, tb, 1, 1, "proving");
        }
        for (int y = tb + 1; y <= tt - 1; y++) {
            f.stand("doodad.ruins-shield-block", "doodad.industrial-crate-stack", tl, y, 1, 1, "proving");
            f.stand("doodad.ruins-shield-block", "doodad.industrial-crate-stack", tr, y, 1, 1, "proving");
        }
        p.zone(tl, tb, tr, tt, WorksHallPlan.Zone.PLATE);

        // Down the line: a shielded test cell either side of each bay, open to the line.
        List<int[]> flash = new ArrayList<>();
        List<Integer> bays = f.bays();
        for (int b = 0; b < bays.size(); b++) {
            int y0 = bays.get(b);
            for (int side = 0; side < 2; side++) {
                int l = side == 0 ? f.westL : f.eastL, r = side == 0 ? f.westR : f.eastR;
                int back = side == 0 ? l : r;
                boolean blown = b == 1 && side == 1;
                p.zone(l, y0, r, y0 + 7, WorksHallPlan.Zone.BAY);
                if (blown) {
                    // The walls blown out into the aisle, the housing wrecked on its stand.
                    for (int k = 0; k < 3; k++) {
                        f.stand("doodad.ruins-shield-rubble", "doodad.industrial-scrap-pile", r + 1 + (k % 2),
                                y0 + 1 + 3 * k, 1, 1, WorksHallPlan.COLLAPSE);
                    }
                    f.stand("doodad.ruins-field-housing-wrecked", null, l + (sw - 3) / 2, y0 + 3, 3, 3,
                            WorksHallPlan.COLLAPSE);
                    flash.add(new int[]{(l + r) / 2, y0 + 4});
                    continue;
                }
                for (int x = l; x <= r; x++) {
                    f.stand("doodad.ruins-shield-block", "doodad.industrial-crate-stack", x, y0, 1, 1, "test");
                    f.stand("doodad.ruins-shield-block", "doodad.industrial-crate-stack", x, y0 + 7, 1, 1, "test");
                }
                for (int y = y0 + 1; y <= y0 + 6; y++) {
                    f.stand("doodad.ruins-shield-block", "doodad.industrial-crate-stack", back, y, 1, 1, "test");
                }
                String housing = f.hash(l, y0, 0xF1) % 3 == 0 ? "doodad.ruins-field-housing-wrecked"
                        : "doodad.ruins-field-housing-half";
                f.stand(housing, null, l + (sw - 3) / 2, y0 + 2, 3, 3, "test");
            }
            // The heat exchanger at this bay's junction on the loop, coolant pooled round it.
            f.stand("doodad.ruins-heat-exchanger", "factory-machines.coolant", f.laneX - 1, y0 + 2, 3, 3, "test");
            if (f.hash(y0, 1, 0xC1) % 2 == 0) p.water(f.laneX - 2, y0 - 2, f.laneX + 1, y0 + 1);
        }
        f.tray("doodad.ruins-cable-tray", "doodad.ruins-cable-tray-fallen", f.cx - 3, HallFrame.DISPATCH + 2,
                f.headY - 1, -1);
        f.tray("doodad.ruins-cable-tray", "doodad.ruins-cable-tray-fallen", f.cx + 2, HallFrame.DISPATCH + 2,
                f.headY - 1, bays.size() > 2 ? bays.get(2) + 2 : -1);
        for (int[] s : flash) {
            p.wear("doodad.ruins-scorch-arc", s[0] - 1, s[1] - 1, 2, 2);
            p.wear("doodad.ruins-scorch-mark", s[0] + 3, s[1] + 2, 2, 2);
        }

        f.stand("doodad.ruins-housing-crate", "doodad.industrial-crate-stack", f.cx - 5, HallFrame.DISPATCH - 1, 3, 2,
                "dispatch");
        f.stand("doodad.ruins-housing-crate", "doodad.industrial-crate-stack", f.cx + 4, HallFrame.DISPATCH - 1, 3, 2,
                "dispatch");
        f.dispatch("dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-coolant-drop", "barracks-bunks.locker-worn", "test");
        f.spread(SCORCH, flash, 9, 0xF5);
        return p;
    }
}
