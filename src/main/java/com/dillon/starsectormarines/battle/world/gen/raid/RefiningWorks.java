package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import com.dillon.starsectormarines.battle.world.gen.layout.SitePurpose;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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
 * for the town. A flare stack burns alone off the works' deep corner. {@link RefineryGround} dresses all of it once
 * the buildings and roads are final ({@link #dressWorks}).
 *
 * <p>The tank farm is the raid's approach: the company comes through the industrial quarter to it, and its bunds and
 * tanks are the cover and the broken sight lines before the works' wall.
 */
public final class RefiningWorks implements RaidIndustry {

    /** The tank farm's held ground, a {@link SitePurpose#GROUND} site on the plan's layout. */
    public static final String TANK_FARM = "tank-farm";
    /**
     * The process area's held ground beyond the works' outer gate, three {@link SitePurpose#GROUND} sites: its units
     * in blocks either side of its spine ({@link ProcessArea}), and the spine between them, the lane its racks run
     * beside. The spine is held too, so nothing grows in it wherever the feeder road comes out of the works' wall.
     */
    public static final String PROCESS_WEST = "process-west", PROCESS_EAST = "process-east",
            PROCESS_SPINE = "process-spine";

    /**
     * The works stands deeper than heavy industry's, so the tank farm fits between it and its industrial quarter, and
     * nearer the middle, so the outer side of the haul road has the room for a planet's tankage. UNTUNED.
     */
    static final MapPlacement WORKS_AT = new MapPlacement(0.74f, 0.56f, 0.86f, 0.66f);
    /** The industrial quarter stands back toward the homes, leaving the tank farm's ground before the works. UNTUNED. */
    static final MapPlacement INDUSTRY_AT = new MapPlacement(0.34f, 0.50f, 0.44f, 0.64f);
    /** Share of the map the works may claim: its support houses, the units standing in the process area. UNTUNED. */
    static final float WORKS_FIT = 0.05f;

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
    /**
     * Share of the works' reach out from its seed the process area starts at: the reach is the claim's bound with its
     * buffer, wider than the walled compound grows, so the area starts inside it and the held ground stops the
     * compound short of it. UNTUNED.
     */
    static final float PROCESS_START = 0.65f;
    /** Cells either side of the spine's middle line the units' sites keep off: the spine's lane. */
    static final int PROCESS_ROAD_GAP = 3;
    /** Cells each units' site reaches beyond one process area, so ground lost at its edges still leaves one. */
    static final int PROCESS_SLACK = 6;

    // The works' buildings: the plant's support, each told by what stands beside it ({@link RefineryGround}). The units
    // themselves stand in the open in the process area.
    private static final RoomShape RECEIVING = RoomShape.rectangle(12, 6);
    private static final RoomShape PUMP_HOUSE = RoomShape.rectangle(9, 6);
    private static final RoomShape CONTROL_HOUSE = RoomShape.rectangle(7, 6);
    private static final RoomShape BOILER_HOUSE = RoomShape.rectangle(9, 6);
    private static final RoomShape LAB_HOUSE = RoomShape.rectangle(6, 5);

    /** Open ground for the utilities: the boiler's cooling tower and the stacks, masts and skids beside the houses. */
    static final int UTILITY_YARD = 700;

    /** The room each step of the plant is, in the order the stock passes through them. */
    public static final RoomPurpose RECEIVING_ROOM = RoomPurpose.LOADING_BAY;
    public static final RoomPurpose PUMP_ROOM = RoomPurpose.ENGINE_ROOM;
    /** The control room, its comms mast beside it; the boiler house and its cooling tower; the lab and its gases. */
    public static final RoomPurpose CONTROL = RoomPurpose.CONTROL_ROOM;
    public static final RoomPurpose BOILER_ROOM = RoomPurpose.PRODUCTION_FLOOR;
    public static final RoomPurpose LAB_ROOM = RoomPurpose.SERVER_ROOM;

    @Override public String key() { return "refining"; }

    @Override public Set<String> industryIds() { return Set.of("refining", "fuelprod"); }

    @Override public float worksFit() { return WORKS_FIT; }

    @Override public MapPlacement worksAt() { return WORKS_AT; }

    @Override public MapPlacement industryAt() { return INDUSTRY_AT; }

    /** The tank farm is the refinery's yards: no district stands beyond the works, where the pipelines come in. */
    @Override public boolean yards() { return false; }

    @Override public FortressProgram works(int units) { return program(units); }

    /** The tank farm's held ground before the works, stopped short of a storm bank's ground. */
    @Override public void sitePieces(SiteFrame frame, List<Piece> out) {
        Precinct works = frame.works(), industry = frame.place(RaidSiteTemplate.INDUSTRY);
        if (works == null || industry == null) return;
        Site farm = tankFarm(works.seedX(), works.seedY(), industry.seedX(), industry.seedY(),
                RaidSiteTemplate.worksReach(WORKS_FIT, frame.security(), frame.width(), frame.height()),
                frame.mirrorX(), frame.mirrorY(), frame.width(), frame.height());
        out.add(frame.bankWind() != null
                ? RaidSiteTemplate.clearOfBank(farm, frame.bankWind(), frame.width(), frame.height()) : farm);
        for (Site process : processSites(works.seedX(), works.seedY(),
                RaidSiteTemplate.worksReach(WORKS_FIT, frame.security(), frame.width(), frame.height()),
                frame.mirrorX(), frame.mirrorY(), frame.width(), frame.height())) {
            out.add(frame.bankWind() != null
                    ? RaidSiteTemplate.clearOfBank(process, frame.bankWind(), frame.width(), frame.height()) : process);
        }
    }

    /**
     * The process area's sites, from {@link #PROCESS_START} of the works' reach to the map's outer edge: one process
     * area wide ({@link ProcessArea#width}) and {@link #PROCESS_SLACK} more either side of the works' seed's column,
     * and the spine between them. In order across the map, in map cells.
     */
    public static List<Site> processSites(int worksX, int worksY, int reach, boolean mirrorX, boolean mirrorY,
                                          int width, int height) {
        // Worked in the canonical frame (outer to larger y), then mirrored back.
        int wx = mirrorX ? width - 1 - worksX : worksX;
        int wy = mirrorY ? height - 1 - worksY : worksY;
        int y0 = wy + Math.round(reach * PROCESS_START), y1 = height - 1 - FARM_MARGIN;
        if (y1 < y0) y1 = y0;
        int across = ProcessArea.width() + PROCESS_SLACK;
        int[][] xs = {{wx - PROCESS_ROAD_GAP - across + 1, wx - PROCESS_ROAD_GAP},
                {wx + PROCESS_ROAD_GAP, wx + PROCESS_ROAD_GAP + across - 1},
                {wx - PROCESS_ROAD_GAP + 1, wx + PROCESS_ROAD_GAP - 1}};
        String[] names = {PROCESS_WEST, PROCESS_EAST, PROCESS_SPINE};
        List<Site> out = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            int x0 = Math.max(FARM_MARGIN, xs[k][0]), x1 = Math.min(width - 1 - FARM_MARGIN, xs[k][1]);
            if (x1 < x0) x1 = x0;
            int cx0 = mirrorX ? width - 1 - x1 : x0, cx1 = mirrorX ? width - 1 - x0 : x1;
            int cy0 = mirrorY ? height - 1 - y1 : y0, cy1 = mirrorY ? height - 1 - y0 : y1;
            // Mirrored across x, the canonical west site lands east: name each by where it stands.
            String name = mirrorX && k < 2 ? names[1 - k] : names[k];
            out.add(new Site(name, cx0, cy0, cx1, cy1, SitePurpose.GROUND, true));
        }
        out.sort(Comparator.comparingInt(Site::x0));
        return out;
    }

    /** The plant dressed: process area, tank farm, racks, the flare and the spills ({@link RefineryGround}). */
    @Override public void dressWorks(GenContext c, PrecinctPlan plan, Precinct works) {
        RefineryGround.dress(c, plan, works);
    }

    /**
     * The refinery's works, the plant's support: the receiving manifold and the boiler house in the rear, where the
     * pipelines from the deep edge land and the utilities keep their corner; the control room and the lab in the
     * yard; the product pump house toward the front, whence the product rack leaves for the tank farm; and the posts
     * on its gates. The units (the machines the raid wrecks) stand in the open in the process area beyond the outer
     * gate ({@link #processSites}); no loadout inside: the tankers fill at the tank farm's rack.
     *
     * @param units units in the plant, one per machine the raid wrecks (laid in the process area)
     */
    public static FortressProgram program(int units) {
        if (units < 1) throw new IllegalArgumentException("a refinery has at least one unit");
        return new FortressProgram(List.of(
                new FortressBuilding(RECEIVING_ROOM, RECEIVING, Ward.REAR, 1),
                new FortressBuilding(PUMP_ROOM, PUMP_HOUSE, Ward.FRONTAGE, 1),
                new FortressBuilding(CONTROL, CONTROL_HOUSE, Ward.YARD, 1),
                new FortressBuilding(BOILER_ROOM, BOILER_HOUSE, Ward.REAR, 1),
                new FortressBuilding(LAB_ROOM, LAB_HOUSE, Ward.YARD, 1),
                RaidWorksParts.guardPosts(2)),
                0, UTILITY_YARD, true);
    }

    /**
     * The tank farm's ground: along the map from {@link #FARM_GAP} short of the works' reach back toward the
     * industrial quarter (no nearer its seed than {@link #FARM_INDUSTRY_GAP}, no longer than {@link #FARM_LENGTH} of
     * the map), and across it on the side of the haul road with the more room: the outer side, toward the map's edge,
     * or the inner, toward the middle but never past {@link #FARM_INNER}. Kept {@link #FARM_ROAD_GAP} off the rows
     * the haul road runs between the two seeds.
     */
    public static Site tankFarm(int worksX, int worksY, int industryX, int industryY, int reach,
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
