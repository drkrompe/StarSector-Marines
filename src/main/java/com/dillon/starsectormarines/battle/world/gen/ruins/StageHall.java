package com.dillon.starsectormarines.battle.world.gen.ruins;

/**
 * A holosuite works' stage hall ({@link WorksHall#STAGE}), where projector stacks were built and calibrated:
 * <ul>
 *   <li><b>Feed.</b> Emitter blanks come up the west rail to the clean room at the head.</li>
 *   <li><b>Emitters.</b> Emitter benches and the coating chamber in the clean room across the head.</li>
 *   <li><b>Assembly.</b> Down the line's upper half each bay has an assembly stand either side, a stack half-built on
 *   it.</li>
 *   <li><b>Calibration.</b> The lower half is the calibration stage: a round floor ringed by finished stacks, every
 *   one aimed at its middle, the director's booth outside it.</li>
 *   <li><b>Dispatch.</b> Crated stacks at the dispatch end.</li>
 * </ul>
 * The collapse: a stack toppled into the stage and the booth's glass is broken. The hall is clean, with dust in it.
 */
final class StageHall {

    static final String DUST = "floors.sand";

    private StageHall() {}

    static WorksHallPlan plan(int width, int length, long seed) {
        HallFrame f = new HallFrame(WorksHall.STAGE, width, length, seed);
        WorksHallPlan p = f.plan;
        p.station("feed").station("emitters").station("assembly").station("calibration").station("dispatch");
        int top = length - 2, sw = f.stationWidth();
        f.feedRail("feed", top - 1);

        // The clean room across the head.
        p.zone(f.westL, top - 14, f.eastR, top, WorksHallPlan.Zone.SPECIAL);
        for (int row = 0; row < 3; row++) {
            for (int x = f.westL + 2 * (row % 2); x + 1 <= f.eastR - 5; x += 5) {
                f.stand("doodad.ruins-emitter-bench", "doodad.civic-workstation-bank", x, top - 3 - 5 * row, 2, 2,
                        "emitters");
            }
        }
        f.stand("doodad.ruins-coating-chamber", "doodad.industrial-reactor-housing", f.eastR - 3, top - 4, 4, 4,
                "emitters");
        f.stand("doodad.ruins-coating-chamber", "doodad.industrial-reactor-housing", f.eastR - 3, top - 12, 4, 4,
                "emitters");

        // The stage: a round floor in the lower half, centred below the hall's middle, ringed by stacks.
        int sx = f.w / 2, sy = Math.max(HallFrame.DISPATCH + 4 + sw, f.l / 4 + 2);
        int r = Math.min(f.w / 2 - 6, f.l / 2 - sy + HallFrame.DISPATCH);
        r = Math.max(6, Math.min(r, 12));
        for (int y = sy - r; y <= sy + r; y++) {
            for (int x = sx - r; x <= sx + r; x++) {
                if (Math.hypot(x - sx + .5, y - sy + .5) <= r) p.zone(x, y, x, y, WorksHallPlan.Zone.SPECIAL);
            }
        }
        int stacks = 8;
        for (int k = 0; k < stacks; k++) {
            double a = 2 * Math.PI * k / stacks + Math.PI / 8;
            int px = (int) Math.round(sx + (r - 1) * Math.cos(a)) - 1, py = (int) Math.round(sy + (r - 1) * Math.sin(a)) - 1;
            boolean toppled = k == 2;
            if (toppled) {
                int ix = (int) Math.round(sx + (r - 5) * Math.cos(a)) - 1, iy = (int) Math.round(sy + (r - 5) * Math.sin(a)) - 1;
                f.stand("doodad.ruins-projector-stack-wrecked", null, ix, iy, 3, 3, WorksHallPlan.COLLAPSE);
            } else {
                f.stand(k % 3 == 0 ? "doodad.ruins-projector-stack-whole" : "doodad.ruins-projector-stack-half", null,
                        px, py, 3, 3, "calibration");
            }
        }
        // Pedestals inside the stage on a ring halfway in, where the calibration targets stood.
        for (int k = 0; k < 6; k++) {
            double a = 2 * Math.PI * k / 6;
            f.stand("doodad.ruins-holo-pedestal", "doodad.medical-monitor-stack",
                    (int) Math.round(sx + r / 2.0 * Math.cos(a)) - 1, (int) Math.round(sy + r / 2.0 * Math.sin(a)) - 1, 2,
                    2, "calibration");
        }
        f.stand("doodad.ruins-director-booth", "console-bank.straight-damaged", 1 + f.railX + 2, sy - 2, 2, 5,
                WorksHallPlan.COLLAPSE);

        // Assembly down the upper half, above the stage.
        for (int y0 : f.bays(sy + r + 2)) {
            for (int side = 0; side < 2; side++) {
                int ax = side == 0 ? f.westR - 2 : f.eastL;
                p.zone(ax - 1, y0, ax + 3, y0 + 7, WorksHallPlan.Zone.BAY);
                f.stand(f.hash(ax, y0, 0x57) % 4 == 0 ? "doodad.ruins-projector-stack-wrecked"
                        : "doodad.ruins-projector-stack-half", null, ax, y0 + 2, 3, 3, "assembly");
                int bx = side == 0 ? f.westL : ax + 4;
                f.stand("doodad.ruins-emitter-bench", "doodad.civic-workstation-bank", bx, y0 + 1, 2, 2, "assembly");
                f.stand("doodad.ruins-emitter-bench", "doodad.civic-workstation-bank", bx, y0 + 5, 2, 2, "assembly");
            }
            for (int y = y0; y + 1 <= y0 + 6; y += 2) {
                p.floor("doodad.ruins-stack-conveyor", "doodad.ruins-mag-rail", f.cx, y, 2, 2, "assembly");
            }
        }

        f.stand("doodad.ruins-stack-crate", "doodad.industrial-crate-stack", f.westL, 1, 3, 2, "dispatch");
        f.stand("doodad.ruins-stack-crate", "doodad.industrial-crate-stack", f.eastR - 2, 1, 3, 2, "dispatch");
        f.dispatch("dispatch");
        f.runways();
        f.serviceDrops("doodad.ruins-power-drop", "barracks-bunks.locker-worn", "assembly");
        f.bank(DUST, 0, 2, 0x5D);
        return p;
    }
}
