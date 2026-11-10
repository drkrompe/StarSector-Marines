package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A synchrotron works' ring hall ({@link WorksHall#RING}). Its product is the magnet segment, and every segment was
 * proved on the hall's own storage ring before it shipped, so the ring is the hall:
 * <ul>
 *   <li><b>Feed.</b> Conductor stock comes up the west rail to the winding shop at the head.</li>
 *   <li><b>Winding.</b> Coil winders by the rail's stop.</li>
 *   <li><b>Cold test.</b> Each segment was cooled in a cryostat beside the winders, the cold plant across the head
 *   from it.</li>
 *   <li><b>Ring.</b> The storage ring runs round the hall, segment after segment, crossed by gaps the crews walked
 *   through; the linac injects into it from the head, RF cavities stand on its straights, the beam dump inside its
 *   foot.</li>
 *   <li><b>Dispatch.</b> Proved segments were crated below the ring.</li>
 * </ul>
 * The ring is the fight's ground: a wall of segments round the hall's middle, broken by its crossings. The collapse:
 * a quench blew a segment off the east straight into the aisle and breached the ring there; a crane girder came down.
 * Rime is on the coils, which ran cold.
 */
final class RingHall {

    static final String RIME = "floors.snow";
    /** Each ring segment's size: three cells square. */
    static final int SEG = 3;

    private RingHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.RING, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("winding").station("cold test").station("ring").station("dispatch");
        int top = length - 2;
        f.feedRail("feed", top - 1);

        // The ring: its straights in the station columns, its arcs across the hall below the head and above dispatch.
        int rl = f.westL, rr = f.eastR - SEG + 1, rb = HallFrame.DISPATCH + 2, rt = f.headY - SEG;
        List<int[]> coils = new ArrayList<>();
        int quench = -1, along = 0;
        for (int y = rb; y + SEG - 1 <= rt + SEG - 1; y += SEG) along++;
        int qAt = along / 3;
        int k = 0;
        for (int y = rb + SEG; y + SEG - 1 < rt; y += SEG, k++) {
            boolean crossing = k % 4 == 3;
            if (crossing) continue;
            f.stand("doodad.ruins-ring-segment", "doodad.ruins-magnet-segment-half", rl, y, SEG, SEG, "ring");
            if (k == qAt) {
                // The quench: this segment of the east straight was blown into the aisle beside it.
                quench = y;
                f.stand("doodad.ruins-magnet-segment-wrecked", null, rr + SEG, y + 1, SEG, SEG,
                        WorksHallPlan.COLLAPSE);
            } else {
                f.stand("doodad.ruins-ring-segment", "doodad.ruins-magnet-segment-half", rr, y, SEG, SEG, "ring");
            }
            coils.add(new int[]{rl + 1, y + 1});
            coils.add(new int[]{rr + 1, y + 1});
        }
        int j = 0;
        for (int x = rl; x + SEG - 1 <= rr + SEG - 1; x += SEG, j++) {
            boolean crossing = Math.abs(x + 1 - f.cx - .5f) <= 2;
            if (!crossing) {
                f.stand("doodad.ruins-ring-segment-ew", "doodad.ruins-magnet-segment-half", x, rb, SEG, SEG, "ring");
                coils.add(new int[]{x + 1, rb + 1});
            }
            // The top arc leaves the linac's way in at the line.
            if (!crossing && j % 3 != 1) {
                f.stand("doodad.ruins-ring-segment-ew", "doodad.ruins-magnet-segment-half", x, rt, SEG, SEG, "ring");
                coils.add(new int[]{x + 1, rt + 1});
            }
        }
        // Inside the ring: RF cavities on its straights, the beam dump at its foot.
        for (int y = rb + SEG + 4, n = 0; y + 2 < rt - 2; y += 6, n++) {
            boolean west = n % 2 == 0;
            boolean cavity = n % 3 == 0;
            f.stand(cavity ? "doodad.ruins-rf-cavity" : "doodad.ruins-beam-diagnostics", "doodad.industrial-generator",
                    west ? rl + SEG + 1 : rr - 3, y, 2, cavity ? 3 : 2, "ring");
            if (rr - rl > 24) {
                f.stand("doodad.ruins-beam-diagnostics", "doodad.industrial-generator", west ? rr - 9 : rl + SEG + 7,
                        y + 3, 2, 2, "ring");
            }
        }
        f.stand("doodad.ruins-beam-dump", "doodad.industrial-reactor-housing", f.cx + 3, rb + SEG + 1, 4, 4, "ring");

        // The head: winders and a cryostat west, the cold plant east, the linac down the line into the ring.
        f.stand("doodad.ruins-coil-winder", "doodad.industrial-cable-reel", f.westL, top - 3, 3, 3, "winding");
        f.stand("doodad.ruins-coil-winder", "doodad.industrial-cable-reel", f.westL + 4, top - 3, 3, 3, "winding");
        f.stand("doodad.ruins-cryostat", "doodad.ruins-frost-heatsink", f.westL, top - 9, 4, 3, "cold test");
        f.stand("doodad.ruins-cold-head", "factory-machines.coolant", f.eastR - 5, top - 7, 6, 6, "cold test");
        for (int y = rt + SEG; y + 2 <= top - 1; y += 3) {
            f.stand("doodad.ruins-linac-section", "doodad.ruins-cryo-rack", f.cx, y, 2, 3, "ring");
        }
        p.zone(f.westL, f.headY, f.eastR, top, WorksHallPlan.Zone.PLATE);
        coils.add(new int[]{f.westL + 2, top - 8});
        coils.add(new int[]{f.eastR - 2, top - 4});

        // Spare segments crated below the ring.
        f.stand("doodad.ruins-magnet-segment-half", null, f.westL + 1, 1, 3, 3, "dispatch");
        f.stand("doodad.ruins-magnet-segment-whole", null, f.eastR - 3, 1, 3, 3, "dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-power-drop", "barracks-bunks.locker-worn", "ring");
        f.stand("doodad.ruins-fallen-girder", null, 1, quench > 0 ? quench + 6 : f.l / 3, 7, 1,
                WorksHallPlan.COLLAPSE);
        if (quench > 0) p.wear("doodad.ruins-scorch-arc", rr + SEG, quench - 1, 2, 2);

        f.spread(RIME, coils, 2, 0x41);
        return p;
    }
}
