package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The signs that a colony built the storm bank and keeps it ({@code mapgen-nouns.md}, Climate; {@link StormBank}), laid
 * from existing art once the bank is raised and the streets are finished, so the bank reads as works and not as a
 * ridge:
 * <ul>
 *   <li>sand drifted to its windward foot and over the windward half of its crest, the lee half clean;</li>
 *   <li>a windbreak fence along the crest's windward edge, in panels with breaks between;</li>
 *   <li>a worn maintenance track along its lee foot, running on through the passes;</li>
 *   <li>a gate post either side of every pass, and a hauler or loader parked beside it on the lee side;</li>
 *   <li>an older bank line farther out to windward, half buried: a sand-drowned strip with sandbag remnants.</li>
 * </ul>
 * Pure where it decides ({@link #plan}): cells in, placements out, so its rules are proved without a map. Nothing it
 * places stands on a road or in a pass, nothing blocks the town's side of the bank, and every prop takes only open
 * ground, so the ground stays as connected as the bank left it.
 */
public final class StormBankDressing {

    /** Sand banked on the windward ground before the bank's face, cells deep. */
    static final int APRON = 3;
    /** A fence panel's run along the crest before a break, cells. */
    static final int FENCE_RUN = 7;
    /** Cells from the windward edge to the old, buried bank line. */
    static final int OLD_LINE = 5;
    /** The old line's sandbags: a run this long, then a gap as long as {@link #OLD_GAP}. */
    static final int OLD_RUN = 3, OLD_GAP = 3;
    /** The shortest gap in the bank that is a pass worth a gate. */
    static final int MIN_PASS = 3;

    static final String SAND = "floors.sand";
    static final String TRACK = "pavement.dirt.worn";
    static final String POST = "doodad.boom-gate-post";
    static final String[] MACHINES = {"doodad.parked-flatbed-truck", "doodad.stores-cargo-loader",
            "doodad.parked-utility-truck"};

    private StormBankDressing() { }

    /** A prop to stand: its def id, its cell, and its quarter turns. */
    public record Prop(String id, int x, int y, int turns) { }

    /** Everything the bank is dressed with: surface cells by surface id, and props. */
    public record Plan(List<int[]> sand, List<int[]> track, List<int[]> oldLine, List<Prop> props,
                       List<int[]> passes) { }

    /**
     * The dressing for a bank on the {@code wind} side of a {@code width} x {@code height} map.
     *
     * @param top   a walkable cell on the bank's crest
     * @param face  a cliff face cell of the bank
     * @param ramp  a ramp cell
     * @param open  open ground a prop or a track may take (walkable, no fixture, no building, not a ramp)
     * @param road  a road cell: never covered by a prop
     */
    public static Plan plan(int width, int height, StormWind.Side wind, IntPredicate top, IntPredicate face,
                            IntPredicate ramp, IntPredicate open, IntPredicate road) {
        int length = wind.alongX() ? width : height;
        int lo = StormBank.OFFSET - 3, hi = StormBank.OFFSET + StormBank.THICKNESS + 2;
        int trackAt = StormBank.OFFSET + StormBank.THICKNESS + StormBank.RAMP + 2;
        List<int[]> sand = new ArrayList<>(), track = new ArrayList<>(), old = new ArrayList<>(), passes = new ArrayList<>();
        List<Prop> props = new ArrayList<>();
        boolean[] banked = new boolean[length];
        int first = -1, last = -1;
        for (int a = 0; a < length; a++) {
            int windward = -1, crest = -1;
            for (int d = lo; d <= hi; d++) {
                int i = index(wind, width, height, a, d);
                boolean bank = top.test(i) || face.test(i);
                if (bank && windward < 0) windward = d;
                if (crest < 0 && top.test(i) && !face.test(i) && !ramp.test(i)) crest = d;
            }
            if (windward < 0) continue;
            banked[a] = true;
            if (first < 0) first = a;
            last = a;
            // Sand up the windward face and over the windward half of the crest; the lee half is swept.
            for (int d = windward; d < StormBank.OFFSET + StormBank.THICKNESS / 2; d++) {
                int i = index(wind, width, height, a, d);
                if (top.test(i) && !face.test(i)) sand.add(cell(wind, width, height, a, d));
            }
            for (int d = windward - APRON; d < windward; d++) {
                if (d >= 0 && open.test(index(wind, width, height, a, d))) sand.add(cell(wind, width, height, a, d));
            }
            // The windbreak along the crest's windward edge, in panels.
            if (crest >= 0 && a % (FENCE_RUN + 1) != FENCE_RUN) {
                int i = index(wind, width, height, a, crest);
                if (open.test(i) && !road.test(i)) {
                    int[] c = cell(wind, width, height, a, crest);
                    props.add(new Prop(wind.alongX() ? "doodad.industrial-fence-straight-h"
                            : "doodad.industrial-fence-straight-v", c[0], c[1], 0));
                }
            }
        }
        if (first < 0) return new Plan(sand, track, old, props, passes);
        // The maintenance track along the lee foot, through the passes, a cell past each end.
        for (int a = Math.max(0, first - 2); a <= Math.min(length - 1, last + 2); a++) {
            for (int d = trackAt; d <= trackAt + 1; d++) {
                int i = index(wind, width, height, a, d);
                if (open.test(i) && !road.test(i)) track.add(cell(wind, width, height, a, d));
            }
        }
        // The old line, half buried, out to windward: broken where the passes run out to the edge.
        for (int a = first; a <= last; a++) {
            if (!banked[a]) continue;
            for (int d = OLD_LINE - 1; d <= OLD_LINE + 1; d++) {
                if (open.test(index(wind, width, height, a, d))) old.add(cell(wind, width, height, a, d));
            }
            if ((a - first) % (OLD_RUN + OLD_GAP) < OLD_RUN && open.test(index(wind, width, height, a, OLD_LINE))) {
                int[] c = cell(wind, width, height, a, OLD_LINE);
                props.add(new Prop("doodad.sandbag-straight-" + Character.toLowerCase(wind.name().charAt(0)),
                        c[0], c[1], 0));
            }
        }
        // The passes: a gate post either side on the lee foot, and a machine parked beside each.
        int machine = 0;
        for (int a = first; a <= last; a++) {
            if (banked[a]) continue;
            int start = a;
            while (a <= last && !banked[a]) a++;
            int end = a - 1;
            if (end - start + 1 < MIN_PASS) continue;
            passes.add(new int[]{start, end});
            for (int side : new int[]{start - 1, end + 1}) {
                int d = trackAt - 1, i = index(wind, width, height, side, d);
                if (open.test(i) && !road.test(i)) {
                    int[] c = cell(wind, width, height, side, d);
                    props.add(new Prop(POST, c[0], c[1], 0));
                }
            }
            int along = start - 5, d = trackAt + 3;
            if (along >= 0) {
                int[] c = cell(wind, width, height, along, d);
                props.add(new Prop(MACHINES[machine++ % MACHINES.length], c[0], c[1], wind.alongX() ? 0 : 1));
            }
        }
        return new Plan(sand, track, old, props, passes);
    }

    /** Dresses a raised bank on this map, if one stands. Props that do not fit open ground are left out. */
    public static void dress(GenContext c, StormWind.Side wind) {
        ElevationMap elevation = c.grid.elevation();
        TileRegistry registry = TileRegistry.installed();
        if (elevation == null || registry == null) return;
        int w = c.width;
        IntPredicate face = i -> elevation.isFaceAt(i);
        IntPredicate ramp = i -> elevation.isRampAt(i);
        IntPredicate top = i -> elevation.tier(i % w, i / w) > 0 && !elevation.isFaceAt(i);
        IntPredicate road = i -> c.topology.getGroundKind(i % w, i / w) == GroundKind.STREET
                || c.topology.roadSurface() != null && c.topology.roadSurface().underlay(i % w, i / w) != null;
        IntPredicate open = i -> open(c, i % w, i / w);
        Plan plan = plan(w, c.height, wind, top, face, ramp, open, road);
        paint(c.topology, plan.sand(), SAND, true);
        paint(c.topology, plan.oldLine(), SAND, false);
        if (registry.hasBlock(TRACK)) paint(c.topology, plan.track(), TRACK, false);
        for (Prop p : plan.props()) place(c, registry, p);
    }

    private static void paint(CellTopology topology, List<int[]> cells, String surface, boolean over) {
        int index = topology.surfaceIndex(surface);
        for (int[] cell : cells) {
            if (!over && topology.getSurface(cell[0], cell[1]) != 0) continue;
            topology.setSurface(cell[0], cell[1], index);
        }
    }

    private static void place(GenContext c, TileRegistry registry, Prop p) {
        DoodadDef def = registry.doodad(p.id());
        if (def == null) return;
        Doodad prop = new Doodad(p.x(), p.y(), def, p.turns());
        for (int y = p.y(); y < p.y() + prop.footprintCellsY; y++) {
            for (int x = p.x(); x < p.x() + prop.footprintCellsX; x++) if (!open(c, x, y)) return;
        }
        for (int y = p.y(); y < p.y() + prop.footprintCellsY; y++) {
            for (int x = p.x(); x < p.x() + prop.footprintCellsX; x++) {
                c.grid.setWalkable(x, y, false);
                c.grid.setSeeThrough(x, y, true);
                c.topology.setFixture(x, y, true);
            }
        }
        c.doodads.add(prop);
    }

    /** Open ground a dressing may take: walkable, no fixture, building, door or ramp, and no road. */
    private static boolean open(GenContext c, int x, int y) {
        if (!c.grid.inBounds(x, y) || !c.grid.isWalkable(x, y) || c.topology.isFixture(x, y)
                || c.topology.getBuildingId(x, y) != 0 || c.topology.hasBuildingKindHint(x, y)) return false;
        if (c.grid.elevation() != null && c.grid.elevation().isRamp(x, y)) return false;
        for (int ty = y - 1; ty <= y + 1; ty++) {
            for (int tx = x - 1; tx <= x + 1; tx++) if (c.grid.inBounds(tx, ty) && c.grid.isDoorway(tx, ty)) return false;
        }
        GroundKind kind = c.topology.getGroundKind(x, y);
        return kind != GroundKind.STREET && kind != GroundKind.INDOOR && kind != GroundKind.WATER;
    }

    static int index(StormWind.Side wind, int width, int height, int a, int d) {
        int[] c = cell(wind, width, height, a, d);
        return c[1] * width + c[0];
    }

    /** The cell {@code a} along the windward edge and {@code d} in from it. */
    static int[] cell(StormWind.Side wind, int width, int height, int a, int d) {
        if (wind.alongX()) return new int[]{a, wind.low() ? d : height - 1 - d};
        return new int[]{wind.low() ? d : width - 1 - d, a};
    }
}
