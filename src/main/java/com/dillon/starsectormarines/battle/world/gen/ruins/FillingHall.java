package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * An armory works' filling hall ({@link WorksHall#FILLING}), where munitions and hardware were filled, proved and
 * racked:
 * <ul>
 *   <li><b>Feed.</b> Bodies and charges come up the west rail to the head.</li>
 *   <li><b>Filling.</b> Down the west side of the line each bay has a filling press, its charge hopper beside it.</li>
 *   <li><b>Proof.</b> The east side is the proof range: a lane from the firing point above dispatch to the butts at
 *   the head, behind a baffle wall along the line, with a baffle stub across it every bay.</li>
 *   <li><b>Magazines.</b> Proved hardware went to the blast-walled magazines along the west wall, racked.</li>
 *   <li><b>Dispatch.</b> Crated at the dispatch end.</li>
 * </ul>
 * The collapse: one magazine cooked off and blew its walls out, scorching the floor round it. Sand from the butts
 * lies down the range.
 */
final class FillingHall {

    static final String SAND = "floors.sand", SCORCH = "volcanic.ash";

    private FillingHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.FILLING, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("filling").station("proof").station("magazines").station("dispatch");
        int top = length - 2;
        f.feedRail("feed", top - 1);

        // The butts at the head of the range, sand in front of them.
        List<int[]> sand = new ArrayList<>();
        int rl = f.eastL - 1, rr = f.eastR + 2;
        f.stand("doodad.ruins-proof-butt", "doodad.basin-scrap-plate-stack", rl, top - 5, Math.min(rr - rl + 1, 12), 4,
                "proof");
        sand.add(new int[]{(rl + rr) / 2, top - 6});
        p.zone(rl, HallFrame.DISPATCH + 1, rr, top - 6, WorksHallPlan.Zone.LANE);
        f.stand("doodad.ruins-firing-point", "doodad.armoury-cleaning-bench", (rl + rr) / 2 - 1, HallFrame.DISPATCH + 1, 3,
                1, "proof");
        f.stand("doodad.ruins-line-pulpit", "doodad.industrial-control-console", f.cx - 1, top - 3, 3, 3, "filling");

        // Down the line: a press either side of the conveyor west, the range behind its baffle east.
        List<int[]> blast = new ArrayList<>();
        List<Integer> bays = f.bays();
        for (int b = 0; b < bays.size(); b++) {
            int y0 = bays.get(b);
            f.stand("doodad.ruins-filling-press", "doodad.industrial-machine-tool", f.westR - 2, y0 + 1, 3, 3, "filling");
            f.stand("doodad.ruins-charge-hopper", "doodad.industrial-drum-cluster", f.westR - 2, y0 + 5, 2, 2, "filling");
            p.zone(f.westR - 3, y0, f.westR + 1, y0 + 7, WorksHallPlan.Zone.BAY);
            // The baffle wall along the line's east side, broken at each cross aisle; a stub across the range.
            for (int y = y0; y <= y0 + 7; y++) {
                f.stand("doodad.ruins-blast-baffle", "doodad.industrial-crate-stack", f.cx + 3, y, 1, 1, "proof");
            }
            for (int k = 0; k < 4; k++) {
                f.stand("doodad.ruins-blast-baffle", "doodad.industrial-crate-stack", rl + 2 + (b % 2) * 4 + k, y0 + 4,
                        1, 1, "proof");
            }
            // The magazine behind the press: blast walls round three sides, open to the aisle.
            int ml = f.westL, mr = f.westR - 5;
            boolean cooked = b == 1;
            if (mr - ml < 2) continue;
            if (cooked) {
                for (int k = 0; k < 3; k++) {
                    f.stand("doodad.ruins-blast-rubble", "doodad.industrial-scrap-pile", ml + k * 2, y0 + 2 + k, 1, 1,
                            WorksHallPlan.COLLAPSE);
                }
                blast.add(new int[]{(ml + mr) / 2, y0 + 3});
                continue;
            }
            for (int x = ml; x <= mr; x++) {
                f.stand("doodad.ruins-blast-wall", "doodad.industrial-crate-stack", x, y0, 1, 1, "magazines");
                f.stand("doodad.ruins-blast-wall", "doodad.industrial-crate-stack", x, y0 + 7, 1, 1, "magazines");
            }
            for (int y = y0 + 1; y <= y0 + 6; y++) {
                f.stand("doodad.ruins-blast-wall", "doodad.industrial-crate-stack", mr, y, 1, 1, "magazines");
            }
            f.stand("doodad.armoury-ammo-locker", null, ml + 1, y0 + 2, 2, 2, "magazines");
            f.stand("doodad.armoury-rifle-rack", null, ml, y0 + 5, Math.min(3, mr - ml), 1, "magazines");
        }
        for (int y = HallFrame.DISPATCH + 1; y + 1 <= f.headY - 1; y += 2) {
            p.floor("doodad.ruins-works-conveyor", "doodad.ruins-mag-rail", f.cx, y, 2, 2, "filling");
        }
        for (int[] s : blast) p.wear("doodad.ruins-scorch-arc", s[0], s[1], 2, 2);

        f.stand("doodad.ruins-hardware-crate", "doodad.industrial-crate-stack", f.cx - 5, HallFrame.DISPATCH - 2, 3, 2,
                "dispatch");
        f.dispatch("dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-power-drop", "barracks-bunks.locker-worn", "filling");
        f.spread(SAND, sand, 10, 0xF1);
        f.spread(SCORCH, blast, 7, 0xF2);
        return p;
    }
}
