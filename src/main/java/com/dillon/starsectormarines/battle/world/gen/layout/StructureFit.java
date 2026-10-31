package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;

/**
 * Whether, and where, a {@link Structure}'s footprint fits: every cell of it
 * ground it may stand on. Pure: the ground is a cell test.
 */
public final class StructureFit {

    /** The farmhouse's plan and the barn's, door side along (as authored in {@code BuildingStamp}). */
    static final int HOUSE_ALONG = 9, HOUSE_DEEP = 7, BARN_ALONG = 13, BARN_DEEP = 8;
    /** Yard margin round a farm's or a store's buildings, and cells between the two farm buildings. */
    static final int YARD = 2, BETWEEN = 3;
    static final int FARM_ALONG = YARD + HOUSE_ALONG + BETWEEN + BARN_ALONG + YARD;
    static final int FARM_DEEP = YARD + BARN_DEEP + YARD;
    static final int GRANARY_ALONG = YARD + BARN_ALONG + YARD, GRANARY_DEEP = YARD + BARN_DEEP + YARD;
    /** Sides of the ammo dump's ring, the post's ring and the square. */
    static final int DUMP_SIDE = 7, POST_SIDE = 5, SQUARE_SIDE = 9;
    /** An airfield's lot ({@link AirbaseLot.Size#BASE}) and the verge it keeps outside its fence, fence to verge edge. */
    static final AirbaseLot.Size AIRFIELD_SIZE = AirbaseLot.Size.BASE;
    static final int AIRFIELD_VERGE = 2;
    static final int AIRFIELD_ALONG = AIRFIELD_SIZE.width + 2 * AIRFIELD_VERGE;
    static final int AIRFIELD_DEEP = AIRFIELD_SIZE.depth + 2 * AIRFIELD_VERGE;
    /** The prefab bunkhouse's plan, the short module's and the control hut's (as authored in {@code BuildingStamp}). */
    static final int BUNKHOUSE_ALONG = 15, BUNKHOUSE_DEEP = 5, MODULE_ALONG = 9, MODULE_DEEP = 5, HUT_PLAN_ALONG = 7,
            HUT_PLAN_DEEP = 5;
    /** The long prefab module's plan, a hamlet's mess. */
    static final int MESS_PLAN_ALONG = 11, MESS_PLAN_DEEP = 5;
    /**
     * A hamlet building's fenced yard round its building: the fence on the outer ring and two cells inside it, room
     * for its stores, drums, a tank or a parked truck.
     */
    static final int HAMLET_YARD = 3;
    static final int QUARTERS_ALONG = HAMLET_YARD + BUNKHOUSE_ALONG + HAMLET_YARD,
            QUARTERS_DEEP = HAMLET_YARD + BUNKHOUSE_DEEP + HAMLET_YARD;
    static final int SHED_ALONG = HAMLET_YARD + MODULE_ALONG + HAMLET_YARD, SHED_DEEP = HAMLET_YARD + MODULE_DEEP + HAMLET_YARD;
    static final int HUT_ALONG = HAMLET_YARD + HUT_PLAN_ALONG + HAMLET_YARD, HUT_DEEP = HAMLET_YARD + HUT_PLAN_DEEP + HAMLET_YARD;
    static final int MESS_ALONG = HAMLET_YARD + MESS_PLAN_ALONG + HAMLET_YARD,
            MESS_DEEP = HAMLET_YARD + MESS_PLAN_DEEP + HAMLET_YARD;
    static final int HOMESTEAD_ALONG = HAMLET_YARD + HOUSE_ALONG + HAMLET_YARD,
            HOMESTEAD_DEEP = HAMLET_YARD + HOUSE_DEEP + HAMLET_YARD;

    private StructureFit() {}

    /** Whether every cell of the {@code w} by {@code h} footprint at {@code (x, y)} passes {@code ground}. */
    public static boolean fits(LineCourse.CellTest ground, int x, int y, int w, int h) {
        for (int cx = x; cx < x + w; cx++) {
            for (int cy = y; cy < y + h; cy++) if (!ground.test(cx, cy)) return false;
        }
        return true;
    }

    /**
     * The lower-left corner of the {@code w} by {@code h} footprint inside the inclusive region
     * {@code [x0, y0, x1, y1]} that fits {@code ground} and whose middle lies nearest {@code (nx, ny)}, scanning every
     * {@code step} cells; {@code null} when none fits.
     */
    public static int[] nearest(LineCourse.CellTest ground, int[] region, int w, int h, int nx, int ny, int step) {
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int y = region[1]; y + h - 1 <= region[3]; y += step) {
            for (int x = region[0]; x + w - 1 <= region[2]; x += step) {
                long dx = x + w / 2 - nx, dy = y + h / 2 - ny;
                long d = dx * dx + dy * dy;
                if (d >= bestD || !fits(ground, x, y, w, h)) continue;
                best = new int[]{x, y};
                bestD = d;
            }
        }
        return best;
    }
}
