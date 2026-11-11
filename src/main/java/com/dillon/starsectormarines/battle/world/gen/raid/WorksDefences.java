package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBoundary;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
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
     * the largest open yard inside. Each is an inner position named with {@link RaidSiteTemplate#WORKS_MACHINES}, which
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
                post(c, mouth[0] - mouth[2] * in, mouth[1] - mouth[3] * in, GATE_MACHINES);
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
        if (yard != null) post(c, (yard[0] + yard[2]) / 2, (yard[1] + yard[3]) / 2, RESERVE_MACHINES);
    }

    /** A defender machine post at {@code (x, y)} holding {@code machines}. */
    private static void post(GenContext c, int x, int y, int machines) {
        if (!c.grid.inBounds(x, y)) return;
        TacticalNode node = new TacticalNode(TacticalNode.Kind.INNER_POSITION, x, y, x, y, x, y, Faction.DEFENDER,
                MACHINE_POST_PRIORITY, machines);
        node.setPlace(RaidSiteTemplate.WORKS_MACHINES);
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
