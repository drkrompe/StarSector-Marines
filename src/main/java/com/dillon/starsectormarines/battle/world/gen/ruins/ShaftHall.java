package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A mantle bore works' shaft hall ({@link WorksHall#SHAFT}). Its product, the bore head, went down the plant's own
 * shaft, and the spoil came up it, so the hall's flow runs both ways:
 * <ul>
 *   <li><b>Feed.</b> Cutters and segments come up the west rail to the cutter racks.</li>
 *   <li><b>Assembly.</b> Down the line each bay has a bore stand either side, a head half-built on it, its cutters
 *   racked behind.</li>
 *   <li><b>Crane.</b> The gantry crane's runways run down both sides of the line: it carried finished heads north to
 *   the shaft.</li>
 *   <li><b>Shaft.</b> The shaft at the head, open to the mantle, its headframe overhead and a hoist winder either
 *   side; the heads went down it.</li>
 *   <li><b>Spoil.</b> What the bore cut came up in skips and down the east lane's conveyor to the spoil heaps at the
 *   dispatch end.</li>
 * </ul>
 * The collapse: a head fell off the crane short of the shaft and cratered the floor; a spoil heap slid across the
 * dispatch end; a crane girder lies across the west aisle. Spoil is everywhere, banked deep on the walls.
 */
final class ShaftHall {

    static final String SPOIL = "floors.dirt", STONE = "floors.stone";
    /** The shaft's width and depth along the hall. */
    static final int SHAFT = 10;

    private ShaftHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.SHAFT, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("assembly").station("crane").station("shaft");
        int top = length - 2;

        // The shaft at the head, the line's way north; its headframe overhead, standing on the collar a cell wide
        // round the shaft, a winder either side.
        int sl = f.cx - SHAFT / 2 + 1, sb = top - SHAFT;
        p.drop(WorksHallPlan.Drop.SHAFT, sl + 1, sb + 1, sl + SHAFT - 2, top - 2);
        p.overhead("doodad.ruins-headframe", null, sl - 1, sb - 1, SHAFT + 2, SHAFT + 2);
        f.stand("doodad.ruins-hoist-winder", "doodad.industrial-cable-reel", sl - 6, top - 6, 4, 4, "shaft");
        f.stand("doodad.ruins-hoist-winder", "doodad.industrial-cable-reel", sl + SHAFT + 2, top - 6, 4, 4, "shaft");
        f.stand("doodad.ruins-line-pulpit", "doodad.industrial-control-console", sl - 6, sb - 2, 3, 3, "shaft");
        p.zone(sl - 2, sb - 2, sl + SHAFT + 1, top, WorksHallPlan.Zone.PLATE);
        List<int[]> spoil = new ArrayList<>();
        spoil.add(new int[]{sl + SHAFT / 2, sb});

        f.feedRail("feed", top - 1);
        // The spoil conveyor from the shaft's lip down the east lane to the heaps.
        for (int y = HallFrame.DISPATCH + 2; y + 1 <= f.headY + 6; y += 2) {
            p.floor("doodad.ruins-spoil-conveyor", "doodad.ruins-mag-rail", f.laneX, y, 2, 2, "shaft");
        }
        p.zone(f.laneX, HallFrame.DISPATCH + 2, f.laneX + 1, f.headY + 7, WorksHallPlan.Zone.LANE);

        // Down the line: bore stands either side of each bay, cutter racks behind.
        List<Integer> bays = f.bays();
        int stand = Math.min(5, f.stationWidth());
        for (int b = 0; b < bays.size(); b++) {
            int y0 = bays.get(b);
            for (int side = 0; side < 2; side++) {
                int sx = side == 0 ? f.westR - stand + 1 : f.eastL;
                p.floor("doodad.ruins-bore-stand", "doodad.ruins-pallet-bay", sx, y0 + 1, stand, 6, "assembly");
                p.zone(sx, y0, sx + stand - 1, y0 + 7, WorksHallPlan.Zone.BAY);
                String head = f.hash(sx, y0, 0xB0) % 4 == 0 ? "doodad.ruins-bore-head-wrecked"
                        : "doodad.ruins-bore-head-half";
                f.stand(head, null, sx + (stand - 3) / 2, y0 + 2, 3, 3, "assembly");
                int rx = side == 0 ? f.westL : sx + stand + 1;
                if (rx + 1 <= (side == 0 ? sx - 2 : f.eastR)) {
                    f.stand("doodad.ruins-cutter-rack", "doodad.ruins-cooling-rack-castings", rx, y0 + 2, 2, 4,
                            "assembly");
                }
            }
            p.wear("doodad.ruins-oil-stain", f.cx - 3, y0 - 3, 2, 2);
        }
        // The crane's runways either side of the line; the head it dropped short of the shaft.
        for (int y = HallFrame.DISPATCH + 2; y + 3 <= sb - 3; y += 4) {
            p.overhead("doodad.ruins-crane-runway", null, f.cx - 3, y, 1, 4);
            p.overhead("doodad.ruins-crane-runway", null, f.cx + 2, y, 1, 4);
        }
        if (f.stand("doodad.ruins-bore-head-wrecked", null, f.cx - 1, f.headY - 6, 3, 3, WorksHallPlan.COLLAPSE)) {
            p.wear("doodad.ruins-scorch-arc", f.cx - 3, f.headY - 5, 2, 2);
            spoil.add(new int[]{f.cx, f.headY - 5});
        }
        f.stand("doodad.ruins-fallen-girder", null, 1, f.l / 3, 7, 1, WorksHallPlan.COLLAPSE);

        // The spoil heaps at the dispatch end, one slid across it.
        int hx = f.eastR - 7;
        f.stand("doodad.ruins-spoil-heap", "doodad.ruins-slag-heap", hx, 3, 4, 3, "shaft");
        f.stand("doodad.ruins-spoil-heap", "doodad.ruins-slag-heap", hx + 4, 3, 4, 3, "shaft");
        f.stand("doodad.ruins-spoil-slide", "doodad.ruins-slag-heap", f.cx + 1, 1, 4, 2, WorksHallPlan.COLLAPSE);
        spoil.add(new int[]{hx + 4, 4});
        spoil.add(new int[]{f.cx + 3, 2});
        f.runways();
        f.serviceDrops("doodad.ruins-power-drop", "barracks-bunks.locker-worn", "assembly");

        f.spread(SPOIL, spoil, 10, 0x50);
        f.spread(STONE, spoil, 5, 0x51);
        f.bank(SPOIL, 0, 3, 0x52);
        return p;
    }
}
