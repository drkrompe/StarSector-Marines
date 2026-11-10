package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * The refining Raid site ({@code raid-site-templates.md}, The planet's chokepoint; {@code raid-mission.md}, Site
 * templates): the planet's one cracking and distillation plant, laid in the order its stock passes through it.
 *
 * <p>Drawn, as every Raid site is, with the company landing in the west and the works at the deep end, north of the
 * garrison. Crude and volatiles arrive from the planet's other fields by pipeline: the works' feeders, off the deep
 * and outer edges, carry pipe racks in to the <b>receiving manifold</b> in the works' rear. Inside the wall stand the
 * <b>units</b>, one per machine the raid wrecks: each a heater and pump house (the charge's place) with its
 * distillation and cracking columns on an open plot beside it. The product goes out by a rack over the haul road to the
 * <b>tank farm</b>, held ground between the works and the industrial quarter on whichever side of the haul road has
 * the room, where the tanks stand in their bunds; at its haul-road edge is the <b>loading rack</b> the tankers fill at
 * for the town. A flare stack burns alone off the works' deep corner. {@code RefineryGround} dresses all of it once the
 * places have grown.
 *
 * <p>The tank farm is the raid's approach: the company comes through the industrial quarter to it, and its bunds and
 * tanks are the cover and the broken sight lines before the works' wall.
 */
public final class RefinerySite {

    /** The tank farm's held ground, a {@link SitePurpose#GROUND} site on the plan's layout. */
    public static final String TANK_FARM = "tank-farm";

    /**
     * The works stands deeper than heavy industry's, so the tank farm fits between it and its industrial quarter, and
     * nearer the middle, so the outer side of the haul road has the room for a planet's tankage. UNTUNED.
     */
    static final MapPlacement WORKS_AT = new MapPlacement(0.74f, 0.56f, 0.86f, 0.66f);
    /** The industrial quarter stands back toward the homes, leaving the tank farm's ground before the works. UNTUNED. */
    static final MapPlacement INDUSTRY_AT = new MapPlacement(0.34f, 0.50f, 0.44f, 0.64f);
    /** Share of the map the works may claim: its units' plots need open ground round their houses. UNTUNED. */
    static final float WORKS_FIT = 0.07f;

    /** Cells between the works' largest claim and the tank farm's deep edge: the glacis and the product rack's run. UNTUNED. */
    static final int FARM_GAP = 6;
    /** Cells between the industrial quarter's seed and the tank farm's near edge: its last yard and the loading lane. UNTUNED. */
    static final int FARM_INDUSTRY_GAP = 12;
    /** Cells the tank farm keeps off the haul road's rows (the rows between the works' and the quarter's seeds). UNTUNED. */
    static final int FARM_ROAD_GAP = 7;
    /** Longest the tank farm runs along the map, as a share of its width. UNTUNED. */
    static final float FARM_LENGTH = 0.28f;
    /**
     * The farm never comes nearer the middle than this share of the height on its inner side: the ground between the
     * works and the garrison carries the works' roads (the pit's lesson, {@code RaidSiteTemplate.PIT_INNER}). UNTUNED.
     */
    static final float FARM_INNER = 0.40f;
    /** Cells the farm stops short of the map's edges. */
    static final int FARM_MARGIN = 3;

    // The works' buildings. A unit's house is the heater and charge pumps of one crude or cracking unit; its columns
    // stand on the plot beside it, dressed after growth.
    private static final RoomShape RECEIVING = RoomShape.rectangle(12, 6);
    private static final RoomShape UNIT_HOUSE = RoomShape.rectangle(8, 6);
    private static final RoomShape PUMP_HOUSE = RoomShape.rectangle(9, 6);
    private static final RoomShape CONTROL_HOUSE = RoomShape.rectangle(7, 6);
    private static final RoomShape GUARD_POST = RoomShape.rectangle(4, 4);

    /** Open ground a works keeps per unit: the plot its columns stand on and the pipe alleys round it. UNTUNED. */
    static final int YARD_PER_UNIT = 420;

    /** The room each step of the plant is, in the order the stock passes through them. */
    public static final RoomPurpose RECEIVING_ROOM = RoomPurpose.LOADING_BAY;
    public static final RoomPurpose UNIT_ROOM = RoomPurpose.PARTS_CAGE;
    public static final RoomPurpose PUMP_ROOM = RoomPurpose.ENGINE_ROOM;

    private RefinerySite() { }

    /**
     * The refinery's works: the receiving manifold in the rear, where the pipelines from the deep edge land; one unit
     * house per machine in the yard; the product pump house and the control house toward the front, whence the
     * product rack leaves for the tank farm; and the posts on its gates. No loadout inside: the tankers fill at the
     * tank farm's rack.
     *
     * @param units units in the plant, one per machine the raid wrecks
     */
    public static FortressProgram program(int units) {
        if (units < 1) throw new IllegalArgumentException("a refinery has at least one unit");
        return new FortressProgram(List.of(
                new FortressBuilding(RECEIVING_ROOM, RECEIVING, Ward.REAR, 1),
                new FortressBuilding(UNIT_ROOM, UNIT_HOUSE, Ward.YARD, units),
                new FortressBuilding(PUMP_ROOM, PUMP_HOUSE, Ward.FRONTAGE, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, CONTROL_HOUSE, Ward.YARD, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 2)),
                0, units * YARD_PER_UNIT, true);
    }

    /**
     * The tank farm's ground: along the map from {@link #FARM_GAP} short of the works' reach back toward the
     * industrial quarter (no nearer its seed than {@link #FARM_INDUSTRY_GAP}, no longer than {@link #FARM_LENGTH} of
     * the map), and across it on the side of the haul road with the more room: the outer side, toward the map's edge,
     * or the inner, toward the middle but never past {@link #FARM_INNER}. Kept {@link #FARM_ROAD_GAP} off the rows
     * the haul road runs between the two seeds.
     */
    static Site tankFarm(int worksX, int worksY, int industryX, int industryY, int reach,
                         boolean mirrorX, boolean mirrorY, int width, int height) {
        // Worked in the canonical frame (landing west, works north), then mirrored back.
        int wx = mirrorX ? width - 1 - worksX : worksX, ix = mirrorX ? width - 1 - industryX : industryX;
        int wy = mirrorY ? height - 1 - worksY : worksY, iy = mirrorY ? height - 1 - industryY : industryY;
        int x1 = wx - reach - FARM_GAP;
        int x0 = Math.max(ix + FARM_INDUSTRY_GAP, x1 - Math.round(FARM_LENGTH * width));
        x0 = Math.max(FARM_MARGIN, x0);
        if (x1 < x0) x1 = x0;
        int outerY0 = Math.max(wy, iy) + FARM_ROAD_GAP, outerY1 = height - 1 - FARM_MARGIN;
        int innerY0 = Math.round(FARM_INNER * height), innerY1 = Math.min(wy, iy) - FARM_ROAD_GAP;
        int y0, y1;
        if (outerY1 - outerY0 >= innerY1 - innerY0) {
            y0 = outerY0;
            y1 = outerY1;
        } else {
            y0 = innerY0;
            y1 = innerY1;
        }
        if (y1 < y0) y1 = y0;
        int cx0 = mirrorX ? width - 1 - x1 : x0, cx1 = mirrorX ? width - 1 - x0 : x1;
        int cy0 = mirrorY ? height - 1 - y1 : y0, cy1 = mirrorY ? height - 1 - y0 : y1;
        return new Site(TANK_FARM, cx0, cy0, cx1, cy1, SitePurpose.GROUND, true);
    }
}
