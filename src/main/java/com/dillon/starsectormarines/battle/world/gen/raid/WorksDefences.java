package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBoundary;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Raid works' defences, layered by security ({@code raid-demolish-the-works.md}, owner pick
 * {@code raid-works-defences} a): what the raid must get through besides the wall, the emplacements and the works'
 * own guard. Read off the raided market's security ({@link PrecinctPlan#raidSecurity()}), which on an owned map is
 * the raided market's, not the reference market the layout is laid against:
 *
 * <ul>
 *   <li><b>open</b>: nothing more;</li>
 *   <li><b>guarded</b>: a hedgehog belt on the glacis outside the wall and a sandbagged checkpoint at each gate;</li>
 *   <li><b>fortified</b>: also a watchtower in each corner of the works and a sandbag
 *       parapet round every machine hall, so a hall is fought for once the wall is passed.</li>
 * </ul>
 *
 * Every piece is a prop that yields to circulation ({@link WorksProps#place}): never on a street, a road, a doorway's
 * neighbourhood or another prop. A military base lays its own belt, checkpoints and towers at every security
 * ({@link RaidIndustry#laysOwnPerimeter()}); it still takes the hall parapets when fortified.
 */
public final class WorksDefences {

    private WorksDefences() { }

    /** How much a works stands behind beyond its wall. */
    public enum Layer { OPEN, GUARDED, FORTIFIED }

    /** The layer a works of a market at {@code security} stands behind; none for a plan with no security. */
    public static Layer layer(RaidSiteTemplate.Security security) {
        if (security == null) return Layer.OPEN;
        return switch (security) {
            case OPEN -> Layer.OPEN;
            case GUARDED -> Layer.GUARDED;
            case FORTIFIED -> Layer.FORTIFIED;
        };
    }

    /** Lays {@code works}' defences by the raided market's security ({@code RaidWorksDressingStage}, after the industry's dressing). */
    public static void lay(GenContext c, PrecinctPlan plan, Precinct works, RaidIndustry industry) {
        Layer layer = layer(plan.raidSecurity());
        if (layer == Layer.OPEN) return;
        Fortification wall = works.fortification();
        if (!industry.laysOwnPerimeter()) {
            glacisBelt(c, plan, works, wall == null ? 0 : wall.glacis());
            checkpoints(c, plan, works);
            if (layer == Layer.FORTIFIED) {
                int[] bounds = claimBounds(c, plan.precincts().indexOf(works));
                if (bounds != null) MilitaryGround.watchtowers(c, bounds);
            }
        }
        machinePosts(c, plan, works, wall == null ? 0 : wall.glacis(), layer == Layer.FORTIFIED);
        approach(c, plan, works, layer == Layer.FORTIFIED);
        outworks(c, plan, works, wall == null ? 0 : wall.glacis(), layer == Layer.FORTIFIED);
        heldBuildings(c, plan, works, layer == Layer.FORTIFIED ? HELD_FORTIFIED : HELD_GUARDED);
        if (layer == Layer.FORTIFIED) {
            for (PointOfInterest b : WorksProps.buildings(c, plan, works)) {
                if (WorksProps.purposeOf(c, b) != RoomPurpose.PARTS_CAGE) continue;
                for (int[] p : parapet(b.left, b.bottom, b.right, b.top)) {
                    WorksProps.place(c, p[0], p[1], "doodad.fort-sandbag-" + "nesw".charAt(p[2]), 0);
                }
            }
        }
    }

    /** Cells in past the wall a gate's machine post stands. UNTUNED. */
    static final int GATE_POST_IN = 4;
    /** Priority of a machine post: after the works' guard, whose seating comes first anyway. UNTUNED. */
    static final int MACHINE_POST_PRIORITY = 75;
    /** Machines at a gate, and in the reserve. UNTUNED; owner pick {@code raid-works-machines} pending. */
    static final int GATE_MACHINES = 1, RESERVE_MACHINES = 2;

    /**
     * The works' machine posts ({@code raid-demolish-the-works.md}, slice 6; owner picks {@code raid-works-machines}
     * and {@code raid-works-machines-source}, (a) recommended): a post just inside each open gate, past the wall
     * ({@code glacis} cells of cleared ground and {@link #GATE_POST_IN} more), and on a fortified works a reserve on
     * the largest open yard inside. Each is an inner position named with {@link RaidSiteTemplate#WORKS_MACHINES} (the
     * reserve with {@link RaidSiteTemplate#WORKS_RESERVE}, which answers the alarm), which
     * the defender allocation seats with the market's machines right after the works' own guard.
     */
    static void machinePosts(GenContext c, PrecinctPlan plan, Precinct works, int glacis, boolean reserve) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM), owner = c.get(BspKeys.PRECINCT_ROAD);
        int index = plan.precincts().indexOf(works);
        if (claim == null || index < 0) return;
        if (owner != null) {
            for (PrecinctBoundary.Gate gate : PrecinctBoundary.openGates(claim, owner, index, c.width, c.height,
                    Integer.MAX_VALUE)) {
                int[] mouth = mouth(c, claim, index, gate);
                if (mouth == null) continue;
                int in = glacis + GATE_POST_IN;
                post(c, mouth[0] - mouth[2] * in, mouth[1] - mouth[3] * in, GATE_MACHINES,
                        RaidSiteTemplate.WORKS_MACHINES);
            }
        }
        if (!reserve) return;
        boolean[][] open = new boolean[c.width][c.height];
        for (int x = 0; x < c.width; x++) {
            for (int y = 0; y < c.height; y++) {
                open[x][y] = claim[x][y] == index && distanceOut(claim, index, x, y, glacis + 1) > glacis + 1
                        && c.grid.isWalkable(x, y) && !c.topology.isWall(x, y) && !c.topology.isFixture(x, y)
                        && c.topology.getBuildingId(x, y) == 0;
            }
        }
        int[] yard = MilitaryGround.largestRectangle(open);
        if (yard != null) post(c, (yard[0] + yard[2]) / 2, (yard[1] + yard[3]) / 2, RESERVE_MACHINES,
                RaidSiteTemplate.WORKS_RESERVE);
    }

    /** Cells out past the works' ground a gate's forward post stands on its approach. UNTUNED. */
    static final int FORWARD_OUT = 6;
    /** Riflemen at a forward post, on top of the market's force. UNTUNED. */
    static final int FORWARD_RIFLES = 4;
    /** Cells a forward post may slide aside off its approach's road to find open ground. UNTUNED. */
    static final int FORWARD_ASIDE = 9;
    /** Cells out past the works' ground the outer belt's two rows run. UNTUNED. */
    static final int OUTER_BELT_NEAR = 4, OUTER_BELT_FAR = 6;
    /** Cells out past the works' ground its approach reaches, for firing positions. UNTUNED. */
    static final int APPROACH_REACH = 8;
    /** Firing positions a fortified works holds on its approach's open ground, and the least side of that ground. UNTUNED. */
    static final int FIRING_POSITIONS = 2, FIRING_GROUND = 3;
    /** Cells either side of a gate's mouth its approach is kept clear of the outer belt. UNTUNED. */
    static final int LANE_CLEAR = 4;

    /**
     * The works' approach ({@code raid-demolish-the-works.md}, slice 7; owner: "all of this space outside of the works
     * that we don't use for defensive layers"; pick {@code raid-works-approach} a, recommended): the raid fights through
     * depth before it reaches the wall. On a guarded works a sandbagged forward post stands on each open gate's
     * approach, {@link #FORWARD_OUT} cells past the works' ground, held by riflemen security adds
     * ({@link RaidSiteTemplate#WORKS_FORWARD}); on a fortified works an outer belt of hedgehogs also runs on the open
     * ground just outside its ground, broken where the gates' approaches run, and riflemen hold sandbagged firing
     * positions on the largest open ground of the approach.
     */
    static void approach(GenContext c, PrecinctPlan plan, Precinct works, boolean outerBelt) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM), owner = c.get(BspKeys.PRECINCT_ROAD);
        int index = plan.precincts().indexOf(works);
        if (claim == null || owner == null || index < 0) return;
        List<int[]> lanes = new ArrayList<>();
        for (PrecinctBoundary.Gate gate : PrecinctBoundary.openGates(claim, owner, index, c.width, c.height,
                Integer.MAX_VALUE)) {
            int[] mouth = mouth(c, claim, index, gate);
            if (mouth == null) continue;
            lanes.add(mouth);
            forwardPost(c, claim, index, mouth);
        }
        if (outerBelt) {
            // The positions take their ground before the belt breaks it up.
            firingPositions(c, claim, index);
            outerBelt(c, claim, index, lanes);
        }
    }

    /**
     * Up to {@link #FIRING_POSITIONS} sandbagged firing positions, one on each of the largest stretches of open ground
     * (at least {@link #FIRING_GROUND} cells a side) within {@link #APPROACH_REACH} cells outside the works' ground,
     * facing away from the works along the axis it lies out on.
     */
    private static void firingPositions(GenContext c, int[][] claim, int index) {
        int[] b = claimBounds(c, index);
        if (b == null) return;
        boolean[][] open = new boolean[c.width][c.height];
        for (int x = Math.max(0, b[0] - APPROACH_REACH); x <= Math.min(c.width - 1, b[2] + APPROACH_REACH); x++) {
            for (int y = Math.max(0, b[1] - APPROACH_REACH); y <= Math.min(c.height - 1, b[3] + APPROACH_REACH); y++) {
                open[x][y] = claim[x][y] != index && WorksProps.open(c, x, y, 1, 1)
                        && distanceTo(claim, index, x, y, APPROACH_REACH) <= APPROACH_REACH;
            }
        }
        float wx = (b[0] + b[2]) / 2f, wy = (b[1] + b[3]) / 2f;
        for (int placed = 0, tries = 0; placed < FIRING_POSITIONS && tries < 2 * FIRING_POSITIONS; tries++) {
            int[] r = MilitaryGround.largestRectangle(open);
            if (r == null || r[2] - r[0] + 1 < FIRING_GROUND || r[3] - r[1] + 1 < FIRING_GROUND) return;
            int x = (r[0] + r[2]) / 2, y = (r[1] + r[3]) / 2;
            float ox = x - wx, oy = y - wy;
            int dx = Math.abs(ox) >= Math.abs(oy) ? (int) Math.signum(ox) : 0;
            int dy = dx == 0 ? (int) Math.signum(oy) : 0;
            if (nest(c, x, y, dx, dy)) placed++;
            for (int xx = r[0]; xx <= r[2]; xx++) for (int yy = r[1]; yy <= r[3]; yy++) open[xx][yy] = false;
        }
    }

    /**
     * A sandbag nest at {@code (x, y)}, its three bags across the cell beyond it along {@code (dx, dy)} and facing
     * that way, held by a forward post of riflemen security adds; whether it stood.
     */
    private static boolean nest(GenContext c, int x, int y, int dx, int dy) {
        if (dx == 0 && dy == 0) return false;
        int px = dy, py = dx;
        if (!frontOpen(c, x + dx, y + dy, px, py) || !WorksProps.open(c, x, y, 1, 1)) return false;
        String facing = "doodad.fort-sandbag-" + side(dx, dy);
        for (int s = -1; s <= 1; s++) WorksProps.place(c, x + dx + px * s, y + dy + py * s, facing, 0);
        post(c, x, y, FORWARD_RIFLES, RaidSiteTemplate.WORKS_FORWARD);
        return true;
    }

    /** A sandbagged forward post on the approach out of the gate at {@code mouth} {x, y, dx, dy}, if open ground is near. */
    private static void forwardPost(GenContext c, int[][] claim, int index, int[] mouth) {
        int dx = mouth[2], dy = mouth[3];
        int edge = 0;
        while (c.grid.inBounds(mouth[0] + dx * edge, mouth[1] + dy * edge)
                && claim[mouth[0] + dx * edge][mouth[1] + dy * edge] == index) edge++;
        int ax = mouth[0] + dx * (edge + FORWARD_OUT), ay = mouth[1] + dy * (edge + FORWARD_OUT);
        int px = dy, py = dx;  // across the approach
        for (int k = 0; k <= FORWARD_ASIDE; k++) {
            for (int sign : k == 0 ? new int[]{1} : new int[]{1, -1}) {
                if (nest(c, ax + px * k * sign, ay + py * k * sign, dx, dy)) return;
            }
        }
    }

    /** Whether the three cells across {@code (x, y)} (across is {@code (px, py)}) are open for a sandbag each. */
    private static boolean frontOpen(GenContext c, int x, int y, int px, int py) {
        for (int s = -1; s <= 1; s++) if (!WorksProps.open(c, x + px * s, y + py * s, 1, 1)) return false;
        return true;
    }

    /** The prop letter for a sandbag facing out along {@code (dx, dy)}. */
    private static String side(int dx, int dy) {
        for (Side s : Side.values()) {
            if (s.dx == dx && s.dy == dy) return s.name().substring(0, 1).toLowerCase(Locale.ROOT);
        }
        return "n";
    }

    /**
     * Two staggered rows of hedgehogs on the open ground {@link #OUTER_BELT_NEAR} and {@link #OUTER_BELT_FAR} cells out
     * from the works' ground, every other cell, clear of each gate's approach ({@code lanes}, mouths {x, y, dx, dy});
     * roads and buildings refuse them.
     */
    private static void outerBelt(GenContext c, int[][] claim, int index, List<int[]> lanes) {
        for (int x = 0; x < c.width; x++) {
            for (int y = 0; y < c.height; y++) {
                if (claim[x][y] == index || inLane(lanes, x, y)) continue;
                int d = distanceTo(claim, index, x, y, OUTER_BELT_FAR);
                // Every other cell along each row, the far row offset by one: a staggered double row.
                boolean row = d == OUTER_BELT_NEAR && (x + y) % 2 == 0 || d == OUTER_BELT_FAR && (x + y) % 2 == 1;
                if (row) WorksProps.place(c, x, y, "doodad.fort-hedgehog", 0);
            }
        }
    }

    /** Whether {@code (x, y)} is on a gate's approach: outward of its mouth and within {@link #LANE_CLEAR} of its line. */
    static boolean inLane(List<int[]> lanes, int x, int y) {
        for (int[] m : lanes) {
            int along = (x - m[0]) * m[2] + (y - m[1]) * m[3];
            int across = Math.abs((x - m[0]) * m[3] - (y - m[1]) * m[2]);
            if (along >= 0 && across <= LANE_CLEAR) return true;
        }
        return false;
    }

    /** Buildings facing each side of the works held as strongpoints, guarded and fortified. UNTUNED. */
    static final int HELD_GUARDED = 1, HELD_FORTIFIED = 2;
    /** Nearest and furthest a held building stands from the works' ground, in cells. UNTUNED. */
    static final int HELD_NEAR = 2, HELD_FAR = 12;
    /** Cells to the side of a gate's approach its emplacement stands, past the approach's clear lane. UNTUNED. */
    static final int GATE_GUN_ASIDE = LANE_CLEAR + 3;

    /**
     * The buildings facing the works held as strongpoints (pick {@code raid-works-approach}, owner: "(a) and (c)"):
     * on each side, the nearest {@code perSide} buildings standing {@link #HELD_NEAR} to {@link #HELD_FAR} cells out
     * from the works' ground and not already a defended place, each a forward post of riflemen security adds at its
     * interior.
     */
    static void heldBuildings(GenContext c, PrecinctPlan plan, Precinct works, int perSide) {
        int index = plan.precincts().indexOf(works);
        int[] b = claimBounds(c, index);
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        if (b == null || claim == null) return;
        List<PointOfInterest> facing = new ArrayList<>();
        List<Integer> sides = new ArrayList<>(), gaps = new ArrayList<>();
        for (PointOfInterest poi : c.pois) {
            int x = poi.interiorAnchorX, y = poi.interiorAnchorY;
            if (!c.grid.inBounds(x, y) || claim[x][y] == index || defended(c, poi)) continue;
            int x0 = Math.min(poi.left, poi.right), x1 = Math.max(poi.left, poi.right);
            int y0 = Math.min(poi.top, poi.bottom), y1 = Math.max(poi.top, poi.bottom);
            int side = heldSide(b, x0, y0, x1, y1);
            if (side < 0) continue;
            int gap = gap(b, x0, y0, x1, y1);
            if (gap < HELD_NEAR || gap > HELD_FAR) continue;
            int at = 0;
            while (at < gaps.size() && gaps.get(at) <= gap) at++;
            facing.add(at, poi);
            sides.add(at, side);
            gaps.add(at, gap);
        }
        int[] held = new int[4];
        for (int i = 0; i < facing.size(); i++) {
            if (held[sides.get(i)]++ >= perSide) continue;
            PointOfInterest poi = facing.get(i);
            post(c, poi.interiorAnchorX, poi.interiorAnchorY, FORWARD_RIFLES, RaidSiteTemplate.WORKS_FORWARD);
        }
    }

    /**
     * Which side of the works' bounds {@code b} {x0, y0, x1, y1} a building's box faces: 0 west, 1 east, 2 low y, 3
     * high y, the side it lies furthest out past; -1 for one overlapping the works' bounds.
     */
    static int heldSide(int[] b, int x0, int y0, int x1, int y1) {
        int west = b[0] - x1, east = x0 - b[2], low = b[1] - y1, high = y0 - b[3];
        int most = Math.max(Math.max(west, east), Math.max(low, high));
        if (most <= 0) return -1;
        return most == west ? 0 : most == east ? 1 : most == low ? 2 : 3;
    }

    /** Cells between a building's box and the works' bounds {@code b}, along the side it lies out past. */
    static int gap(int[] b, int x0, int y0, int x1, int y1) {
        return Math.max(Math.max(b[0] - x1, x0 - b[2]), Math.max(b[1] - y1, y0 - b[3]));
    }

    /** Whether a defender node already stands inside {@code poi}: a garrison's own building is not held twice. */
    private static boolean defended(GenContext c, PointOfInterest poi) {
        int x0 = Math.min(poi.left, poi.right), x1 = Math.max(poi.left, poi.right);
        int y0 = Math.min(poi.top, poi.bottom), y1 = Math.max(poi.top, poi.bottom);
        for (TacticalNode node : c.tactical) {
            if (node.defaultGuard == Faction.DEFENDER && node.anchorX >= x0 && node.anchorX <= x1
                    && node.anchorY >= y0 && node.anchorY <= y1) return true;
        }
        return false;
    }

    /**
     * The works' outworks guns (owner: "why don't they have turrets OUTSIDE of the works? Defense in depth"): crewed
     * emplacements outside the wall, crewed by riflemen security adds ({@link RaidSiteTemplate#WORKS_FORWARD}). On a
     * guarded works a light emplacement stands beside each open gate's approach, on the glacis' outer edge; a fortified
     * works also sets a medium emplacement mid-way along each side of its glacis.
     */
    static void outworks(GenContext c, PrecinctPlan plan, Precinct works, int glacis, boolean fortified) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM), owner = c.get(BspKeys.PRECINCT_ROAD);
        int index = plan.precincts().indexOf(works);
        int[] b = claimBounds(c, index);
        if (claim == null || owner == null || b == null) return;
        // Outside the wall: the glacis band of the works' own ground, or anywhere beyond it.
        DefensePostStamper.Ground outside = (x, y) -> c.grid.inBounds(x, y)
                && (claim[x][y] != index || distanceOut(claim, index, x, y, glacis + 1) <= Math.max(1, glacis));
        boolean[][] clear = c.get(BspKeys.ROAD_RESERVATION);
        int placed = 0;
        for (PrecinctBoundary.Gate gate : PrecinctBoundary.openGates(claim, owner, index, c.width, c.height,
                Integer.MAX_VALUE)) {
            int[] m = mouth(c, claim, index, gate);
            if (m == null) continue;
            int edge = 0;
            while (c.grid.inBounds(m[0] + m[2] * edge, m[1] + m[3] * edge)
                    && claim[m[0] + m[2] * edge][m[1] + m[3] * edge] == index) edge++;
            for (int sign : new int[]{1, -1}) {
                int x = m[0] + m[2] * edge + m[3] * GATE_GUN_ASIDE * sign;
                int y = m[1] + m[3] * edge + m[2] * GATE_GUN_ASIDE * sign;
                int got = gun(c, outside, clear, DefensePostKind.LIGHT, x, y);
                placed += got;
                if (got > 0) break;
            }
        }
        if (fortified) {
            int in = Math.max(1, glacis / 2);
            int mx = (b[0] + b[2]) / 2, my = (b[1] + b[3]) / 2;
            for (int[] site : new int[][]{{mx, b[1] + in}, {mx, b[3] - in}, {b[0] + in, my}, {b[2] - in, my}}) {
                placed += gun(c, outside, clear, DefensePostKind.MEDIUM, site[0], site[1]);
            }
        }
        if (placed > 0) {
            TacticalRegionMap regions = c.get(BspKeys.TACTICAL_REGIONS);
            if (regions != null) c.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(c.grid, c.topology,
                    c.get(BspKeys.AXIS)));
        }
    }

    /** Stamps one {@code tier} emplacement at or slid near {@code (x, y)}, its guard post named a forward post. */
    private static int gun(GenContext c, DefensePostStamper.Ground where, boolean[][] clear, DefensePostKind tier,
                           int x, int y) {
        int before = c.tactical.size();
        int got = DefensePostStamper.stampAtSites(c.grid, c.topology, where, clear, c.doodads, c.tactical,
                c.defensePosts, c.rng, tier, List.of(new int[]{x, y}));
        TacticalMap linked = c.get(BspKeys.TACTICAL_MAP);
        for (int i = before; i < c.tactical.size(); i++) {
            TacticalNode node = c.tactical.get(i);
            node.setPlace(RaidSiteTemplate.WORKS_FORWARD);
            if (linked != null) linked.add(node);
        }
        return got;
    }

    /** A defender post at {@code (x, y)} holding {@code machines} (or riflemen), named with {@code place}. */
    private static void post(GenContext c, int x, int y, int machines, String place) {
        if (!c.grid.inBounds(x, y)) return;
        TacticalNode node = new TacticalNode(TacticalNode.Kind.INNER_POSITION, x, y, x, y, x, y, Faction.DEFENDER,
                MACHINE_POST_PRIORITY, machines);
        node.setPlace(place);
        c.tactical.add(node);
        // The works is dressed after the tactical link: the linked map takes the post as a node found after it.
        TacticalMap linked = c.get(BspKeys.TACTICAL_MAP);
        if (linked != null) linked.add(node);
    }

    /**
     * A gate's mouth: its middle cell and the outward step {x, y, dx, dy}, the side whose cell beyond the checkpoint
     * line is outside the works; null for a gate with no cells or no outward side.
     */
    private static int[] mouth(GenContext c, int[][] claim, int index, PrecinctBoundary.Gate gate) {
        if (gate.cells().isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int[] cell : gate.cells()) {
            minX = Math.min(minX, cell[0]); minY = Math.min(minY, cell[1]);
            maxX = Math.max(maxX, cell[0]); maxY = Math.max(maxY, cell[1]);
        }
        int cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        for (Side out : Side.values()) {
            int ox = cx + out.dx * (CHECKPOINT_OUT + 1), oy = cy + out.dy * (CHECKPOINT_OUT + 1);
            if (c.grid.inBounds(ox, oy) && claim[ox][oy] != index) return new int[]{cx, cy, out.dx, out.dy};
        }
        return null;
    }

    /** Cells out from a hall's wall its parapet stands, and the parapet's way through every this many cells. UNTUNED. */
    static final int PARAPET_OUT = 2, PARAPET_GAP = 6;

    /**
     * A hall's parapet: a sandbag line {@link #PARAPET_OUT} cells out from each side of the building's bounds
     * {@code (left, bottom)} to {@code (right, top)}, facing out, as {x, y, side} with side 0 north, 1 east, 2 south,
     * 3 west; a way through every {@link #PARAPET_GAP}th cell along it, and the corners open.
     */
    static List<int[]> parapet(int left, int bottom, int right, int top) {
        List<int[]> out = new ArrayList<>();
        int x0 = left - PARAPET_OUT - 1, x1 = right + PARAPET_OUT + 1;
        int y0 = Math.min(bottom, top) - PARAPET_OUT - 1, y1 = Math.max(bottom, top) + PARAPET_OUT + 1;
        for (int x = x0 + 1; x < x1; x++) {
            if ((x - x0) % PARAPET_GAP == 0) continue;
            out.add(new int[]{x, y1, 0});
            out.add(new int[]{x, y0, 2});
        }
        for (int y = y0 + 1; y < y1; y++) {
            if ((y - y0) % PARAPET_GAP == 0) continue;
            out.add(new int[]{x1, y, 1});
            out.add(new int[]{x0, y, 3});
        }
        return out;
    }

    /** Cells out from the works' ground the hedgehog belt runs, and cells between two hedgehogs along it. UNTUNED. */
    static final int BELT_OUT = 2, BELT_STEP = 3;
    /** Cells out from a gate's mouth its checkpoint's sandbag positions stand, and to either side of the gate. UNTUNED. */
    static final int CHECKPOINT_OUT = 2, CHECKPOINT_ASIDE = 2, CHECKPOINT_RUN = 3;

    /**
     * A works' perimeter: a hedgehog belt {@link #BELT_OUT} cells outside its ground, every {@link #BELT_STEP}th
     * cell so a man can pass between two (never across a road, which stays open), and at each gate a checkpoint, a
     * run of sandbags either side of the mouth facing out.
     */
    static void perimeter(GenContext c, PrecinctPlan plan, Precinct works) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        int index = plan.precincts().indexOf(works);
        if (claim == null || index < 0) return;
        for (int x = 0; x < c.width; x++) {
            for (int y = 0; y < c.height; y++) {
                if (claim[x][y] != GrownTrunkPlan.UNOWNED || distanceTo(claim, index, x, y, BELT_OUT) != BELT_OUT) continue;
                if ((x + y) % BELT_STEP == 0) WorksProps.place(c, x, y, "doodad.fort-hedgehog", 0);
            }
        }
        checkpoints(c, plan, works);
    }

    /**
     * A works' belt on its glacis, the cleared ground its claim holds outside its wall ({@code glacis} cells deep): a
     * field of hedgehogs every {@link #BELT_STEP}th cell each way, from {@link #BELT_OUT} cells in from the claim's
     * edge to one short of the wall, so the road that rings a works is never blocked.
     */
    static void glacisBelt(GenContext c, PrecinctPlan plan, Precinct works, int glacis) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        int index = plan.precincts().indexOf(works);
        if (claim == null || index < 0 || glacis <= BELT_OUT) return;
        for (int x = 0; x < c.width; x++) {
            for (int y = 0; y < c.height; y++) {
                if (claim[x][y] != index || x % BELT_STEP != 0 || y % BELT_STEP != 0) continue;
                int d = distanceOut(claim, index, x, y, glacis);
                if (d >= BELT_OUT && d < glacis) WorksProps.place(c, x, y, "doodad.fort-hedgehog", 0);
            }
        }
    }

    /** Chebyshev distance from {@code (x, y)}, a cell of place {@code index}, to the nearest cell outside it, up to {@code most + 1}. */
    private static int distanceOut(int[][] claim, int index, int x, int y, int most) {
        for (int r = 1; r <= most; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= claim.length || ny >= claim[0].length || claim[nx][ny] != index) {
                        return r;
                    }
                }
            }
        }
        return most + 1;
    }

    /** A sandbagged checkpoint at each of the works' open gates. */
    static void checkpoints(GenContext c, PrecinctPlan plan, Precinct works) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM), owner = c.get(BspKeys.PRECINCT_ROAD);
        int index = plan.precincts().indexOf(works);
        if (claim == null || owner == null || index < 0) return;
        for (PrecinctBoundary.Gate gate : PrecinctBoundary.openGates(claim, owner, index, c.width, c.height,
                Integer.MAX_VALUE)) {
            checkpoint(c, claim, index, gate);
        }
    }

    /** A sandbag run either side of a gate's mouth, {@link #CHECKPOINT_OUT} cells out, facing away from the works. */
    private static void checkpoint(GenContext c, int[][] claim, int index, PrecinctBoundary.Gate gate) {
        if (gate.cells().isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int[] cell : gate.cells()) {
            minX = Math.min(minX, cell[0]); minY = Math.min(minY, cell[1]);
            maxX = Math.max(maxX, cell[0]); maxY = Math.max(maxY, cell[1]);
        }
        int cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        for (Side out : Side.values()) {
            int ox = cx + out.dx * (CHECKPOINT_OUT + 1), oy = cy + out.dy * (CHECKPOINT_OUT + 1);
            if (!c.grid.inBounds(ox, oy) || claim[ox][oy] == index) continue;
            // The mouth runs across the outward side; its sandbags stand beyond each end of it.
            boolean across = out.dx == 0;
            int lo = across ? minX : minY, hi = across ? maxX : maxY;
            String facing = "doodad.sandbag-straight-" + out.name().substring(0, 1).toLowerCase(Locale.ROOT);
            // Outside the mouth first; where the gate opens straight onto a road, just inside it, still facing out.
            for (int sign : new int[]{1, -1}) {
                int stood = 0;
                for (int k = 0; k < CHECKPOINT_RUN; k++) {
                    int a = lo - CHECKPOINT_ASIDE - 1 - k, b = hi + CHECKPOINT_ASIDE + 1 + k;
                    int line = (across ? cy : cx) + sign * (across ? out.dy : out.dx) * CHECKPOINT_OUT;
                    if (WorksProps.place(c, across ? a : line, across ? line : a, facing, 0)) stood++;
                    if (WorksProps.place(c, across ? b : line, across ? line : b, facing, 0)) stood++;
                }
                if (stood > 0) return;
            }
            return;
        }
    }

    /** Chebyshev distance from {@code (x, y)} to the nearest cell of place {@code index}, up to {@code most + 1}. */
    private static int distanceTo(int[][] claim, int index, int x, int y, int most) {
        for (int r = 1; r <= most; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < claim.length && ny < claim[0].length && claim[nx][ny] == index) {
                        return r;
                    }
                }
            }
        }
        return most + 1;
    }

    /** The inclusive bounds {x0, y0, x1, y1} of place {@code index}'s ground, or null where it has none. */
    static int[] claimBounds(GenContext c, int index) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        if (claim == null || index < 0) return null;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
        for (int x = 0; x < c.width; x++) {
            for (int y = 0; y < c.height; y++) {
                if (claim[x][y] != index) continue;
                x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
            }
        }
        return x1 < 0 ? null : new int[]{x0, y0, x1, y1};
    }
}
