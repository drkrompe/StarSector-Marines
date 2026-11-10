package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * A fullerene spool works' draw line ({@link WorksHall#DRAW}), where tether fibre was drawn the hall's length and wound
 * onto spool drums:
 * <ul>
 *   <li><b>Feed.</b> Precursor comes up the west rail to the furnace at the head.</li>
 *   <li><b>Draw.</b> The precursor furnace and the draw tower at the line's head pulled the fibre.</li>
 *   <li><b>Tension.</b> The fibre ran the hall's length down the middle over guide rollers, a dancer tower at every
 *   bay holding its tension.</li>
 *   <li><b>Winding.</b> Winders either side of each bay took the fibre onto drums; full drums were racked behind.</li>
 *   <li><b>Proof.</b> Above the dispatch end a length of every spool was pulled on the tensile bed against its
 *   anchor.</li>
 *   <li><b>Dispatch.</b> Drums out at the line's foot.</li>
 * </ul>
 * The collapse: the fibre snapped under load and whipped across the hall, scoring the floor and knocking a run of
 * rollers flat; a drum came off its winder and rolled into the aisle. Chemical crust and soot lie round the furnace.
 */
final class DrawHall {

    static final String CRUST = "toxic-ground.crust", SOOT = "volcanic.ash";

    private DrawHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.DRAW, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("draw").station("tension").station("winding").station("proof").station("dispatch");
        int top = length - 2, sw = f.stationWidth();
        f.feedRail("feed", top - 1);

        // The head: the furnace by the rail's stop, the draw tower over the line's head.
        f.stand("doodad.ruins-precursor-furnace", "doodad.industrial-generator", f.westR - Math.min(6, sw) + 1, top - 5,
                Math.min(6, sw), 5, "draw");
        f.stand("doodad.ruins-draw-tower", "doodad.industrial-reactor-housing", f.cx - 1, top - 5, 4, 4, "draw");
        f.stand("doodad.ruins-line-pulpit", "doodad.industrial-control-console", f.eastL, top - 3, 3, 3, "draw");
        f.stand("doodad.ruins-precursor-tank", "doodad.industrial-fluid-tank", f.eastR - 3, top - 3, 4, 4, "draw");
        f.stand("doodad.ruins-precursor-tank", "doodad.industrial-fluid-tank", f.eastR - 3, top - 9, 4, 4, "draw");
        p.zone(f.westL, top - 7, f.eastR, top, WorksHallPlan.Zone.PLATE);
        List<int[]> furnace = new ArrayList<>();
        furnace.add(new int[]{f.westR - 3, top - 3});

        // The tension line: rollers down the line, broken where the fibre snapped and knocked a run flat.
        List<Integer> bays = f.bays();
        int snap = bays.size() > 1 ? bays.get(1) : f.l / 2;
        for (int y = f.headY - 2; y >= HallFrame.DISPATCH + 2; y -= 3) {
            if (y <= snap + 6 && y >= snap - 2) {
                if (y == snap) {
                    f.stand("doodad.ruins-guide-roller-fallen", "doodad.industrial-pipe-bundle-damaged", f.cx + 2, y, 2,
                            1, WorksHallPlan.COLLAPSE);
                }
                continue;
            }
            f.stand("doodad.ruins-guide-roller", "doodad.industrial-pipe-bundle", f.cx, y, 2, 1, "tension");
        }
        p.zone(f.cx, HallFrame.DISPATCH + 2, f.cx + 1, f.headY - 1, WorksHallPlan.Zone.LANE);

        // Down the line: a dancer tower at every bay, winders either side, drum racks behind.
        int wind = Math.min(4, sw);
        for (int b = 0; b < bays.size(); b++) {
            int y0 = bays.get(b);
            f.stand("doodad.ruins-dancer-tower", "doodad.industrial-generator", f.cx - 3, y0 + 3, 1, 2, "tension");
            for (int side = 0; side < 2; side++) {
                int wx = side == 0 ? f.westR - wind + 1 : f.eastL;
                p.zone(wx, y0, wx + wind - 1, y0 + 7, WorksHallPlan.Zone.BAY);
                boolean rolled = b == 1 && side == 1;
                f.stand("doodad.ruins-winder", "doodad.industrial-cable-reel", wx, y0 + 1, wind, 4, "winding");
                if (!rolled) {
                    p.over(f.hash(wx, y0, 0xD7) % 2 == 0 ? "doodad.ruins-spool-drum-half" : "doodad.ruins-spool-drum-whole",
                            wx + (wind - 3) / 2, y0 + 1, 3, 3, "winding");
                }
                int rx = side == 0 ? f.westL : wx + wind + 1, rto = side == 0 ? wx - 2 : f.eastR;
                for (int x = rx; x + 1 <= rto; x += 3) {
                    f.stand("doodad.ruins-drum-rack", "doodad.ruins-cooling-rack-castings", x, y0 + 2, 2, 4, "winding");
                }
            }
        }
        // The rolled drum in the east aisle, the whip scar across the floor where the fibre went.
        f.stand("doodad.ruins-spool-drum-wrecked", null, f.eastR + 1, snap + 2, 3, 3, WorksHallPlan.COLLAPSE);
        for (int k = 0; k < 6; k++) {
            p.wear("doodad.ruins-scorch-arc", f.cx - 2 - 2 * k, snap - 2 + k, 2, 2);
        }

        // The proof: a tensile bed down the east lane, its anchor at the bed's head.
        int bedB = HallFrame.DISPATCH + 2, bedT = Math.min(f.headY - 2, bedB + 13);
        for (int y = bedB; y + 1 <= bedT; y += 2) {
            p.floor("doodad.ruins-tensile-bed", "doodad.ruins-mag-rail", f.laneX, y, 2, 2, "proof");
        }
        p.zone(f.laneX, bedB, f.laneX + 1, bedT, WorksHallPlan.Zone.LANE);
        f.stand("doodad.ruins-tether-anchor", "doodad.industrial-generator", f.laneX - 1, bedT + 1, 3, 3, "proof");

        f.stand("doodad.ruins-spool-drum-whole", null, f.cx - 5, HallFrame.DISPATCH - 2, 3, 3, "dispatch");
        f.stand("doodad.ruins-spool-drum-half", null, f.cx + 4, HallFrame.DISPATCH - 2, 3, 3, "dispatch");
        f.dispatch("dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-power-drop", "barracks-bunks.locker-worn", "winding");
        f.tray("doodad.ruins-cable-tray", "doodad.ruins-cable-tray-fallen", f.cx + 2, HallFrame.DISPATCH + 2, f.headY - 1,
                -1);
        f.spread(CRUST, furnace, 10, 0xD1);
        f.spread(SOOT, furnace, 5, 0xD2);
        return p;
    }
}
