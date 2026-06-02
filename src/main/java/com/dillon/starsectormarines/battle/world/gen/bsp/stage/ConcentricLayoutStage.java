package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.StationGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * The "onion" station layout — concentric defensive rings wrapping a central
 * control core. Where the BSP station scatters uniform rooms, this lays nested
 * rectangular rings around the map center: the player breaches the outer ring
 * and fights <em>inward</em> through gated ring walls toward the core (the
 * defender's last stand / the objective).
 *
 * <p>Geometry: nested centered rectangles inset by {@link #RING_THICKNESS} from
 * a 1-cell hull margin, until the innermost rect is the {@link #coreInset core}.
 * Each ring <em>band</em> (the frame between two consecutive rects) is split into
 * 8 rooms — 4 corner strongpoints + 4 edge galleries — carved inset by 1 so
 * 1-cell walls separate every room and every ring. Connectivity is then carved:
 * <ul>
 *   <li><b>Intra-ring doors</b> — the 8 rooms of a band are linked in a cycle
 *       (corner↔gallery↔…), so each ring is a loop (flanking space).</li>
 *   <li><b>Inter-ring gates</b> — same-side galleries of consecutive rings are
 *       breached: {@value #OUTER_GATES} gates on the outermost boundary (a
 *       flanking loop near the breach), {@value #INNER_GATES} on every inner
 *       boundary (each a hard must-pass <em>bridge</em> — the besieged core).
 *       Gate sides rotate per ring so the inward path spirals.</li>
 * </ul>
 *
 * <p>Publishes the result as a {@link StationGraph} (rooms = vertices, doors +
 * gates = corridors) under {@link BspKeys#STATION_GRAPH}, tagged with the ring
 * structure ({@link StationGraph#setRings}). Everything downstream —
 * {@link StationTopologyStage}, the scans, the previews — consumes it unchanged;
 * the inter-ring gates become the topology's bridges and depth-from-entry the
 * radial assault gradient. Deterministic, rng-free geometry.
 */
public final class ConcentricLayoutStage implements GenStage {

    /** Solid hull cells reserved at the map perimeter (the outer ring sits just inside). */
    private static final int HULL_MARGIN = 1;
    /** Radial thickness of each ring band, in cells. */
    private static final int RING_THICKNESS = 10;
    /** Stop adding rings once the remaining center would be smaller than this — that center becomes the core. */
    private static final int CORE_MIN = 12;
    /** Gates on the outermost ring boundary — a flanking loop near the breach. */
    private static final int OUTER_GATES = 2;
    /** Gates on every inner ring boundary — each a hard must-pass bridge toward the core. */
    private static final int INNER_GATES = 1;

    @Override
    public void run(GenContext ctx) {
        int w = ctx.width;
        int h = ctx.height;

        // Nested rectangle insets: HULL_MARGIN outward-most, each +RING_THICKNESS,
        // stopping while the next-inner rect is still ≥ CORE_MIN on its short side.
        List<Integer> insets = new ArrayList<>();
        insets.add(HULL_MARGIN);
        int d = HULL_MARGIN;
        while (Math.min(w, h) - 2 * (d + RING_THICKNESS) >= CORE_MIN) {
            d += RING_THICKNESS;
            insets.add(d);
        }
        int bands = insets.size() - 1;          // ring bands between consecutive insets
        int coreInset = insets.get(insets.size() - 1);

        // Per-seed spiral rotation: rotates which gallery side every boundary gates
        // through, so each station's inward path (and radial depth gradient) differs
        // while staying deterministic from the seed.
        int gateRot = ctx.rng.nextInt(4);

        List<StationGraph.Room> rooms = new ArrayList<>();
        List<Integer> ringList = new ArrayList<>();
        // Per band: the cyclic-8 rooms (for the ring loop) + the 4 galleries by side (for gates).
        List<StationGraph.Room[]> bandCycle = new ArrayList<>();
        List<StationGraph.Room[]> bandGallery = new ArrayList<>();

        for (int i = 0; i < bands; i++) {
            int ring = bands - i;               // core = 0, innermost band = 1, outermost = bands
            int dOuter = insets.get(i);
            int dInner = insets.get(i + 1);
            int oL = dOuter, oT = dOuter, oR = w - 1 - dOuter, oB = h - 1 - dOuter;
            int iL = dInner, iT = dInner, iR = w - 1 - dInner, iB = h - 1 - dInner;

            StationGraph.Room tl = addRoom(rooms, ringList, ring, oL,     oT,     iL - 1, iT - 1);
            StationGraph.Room top = addRoom(rooms, ringList, ring, iL,    oT,     iR,     iT - 1);
            StationGraph.Room tr = addRoom(rooms, ringList, ring, iR + 1, oT,     oR,     iT - 1);
            StationGraph.Room right = addRoom(rooms, ringList, ring, iR + 1, iT,   oR,     iB);
            StationGraph.Room br = addRoom(rooms, ringList, ring, iR + 1, iB + 1, oR,     oB);
            StationGraph.Room bottom = addRoom(rooms, ringList, ring, iL,   iB + 1, iR,     oB);
            StationGraph.Room bl = addRoom(rooms, ringList, ring, oL,     iB + 1, iL - 1, oB);
            StationGraph.Room left = addRoom(rooms, ringList, ring, oL,    iT,     iL - 1, iB);

            bandCycle.add(new StationGraph.Room[]{ tl, top, tr, right, br, bottom, bl, left });
            bandGallery.add(new StationGraph.Room[]{ top, right, bottom, left });
        }

        // Core room (ring 0).
        int cI = coreInset;
        StationGraph.Room core = addRoom(rooms, ringList, 0, cI, cI, w - 1 - cI, h - 1 - cI);

        // Build the graph + ring metadata.
        int[] ringOf = new int[ringList.size()];
        for (int k = 0; k < ringOf.length; k++) ringOf[k] = ringList.get(k);
        StationGraph graph = new StationGraph(rooms);
        graph.setRings(ringOf, core.id);

        // Carve every room interior (inset by 1 → 1-cell walls between rooms/rings).
        for (StationGraph.Room r : rooms) {
            StationCarve.carveRoomRect(ctx, r.left + 1, r.top + 1, r.right - 1, r.bottom - 1);
        }

        // Intra-ring doors — link each band's 8 rooms into a loop.
        for (StationGraph.Room[] cyc : bandCycle) {
            for (int j = 0; j < cyc.length; j++) {
                connect(ctx, graph, cyc[j], cyc[(j + 1) % cyc.length]);
            }
        }

        // Inter-ring gates — breach same-side galleries between consecutive rings;
        // the innermost band gates into the core. Outermost boundary gets 2 gates,
        // every inner boundary 1 (a bridge). Sides rotate per ring → inward spiral.
        for (int i = 0; i < bands; i++) {
            int ring = bands - i;               // outer band of this boundary
            StationGraph.Room[] outerGal = bandGallery.get(i);
            boolean toCore = (i == bands - 1);
            StationGraph.Room[] innerGal = toCore ? null : bandGallery.get(i + 1);

            int gates = (ring >= bands) ? OUTER_GATES : INNER_GATES;
            for (int side : gateSides(ring + gateRot, gates)) {
                StationGraph.Room outer = outerGal[side];
                StationGraph.Room inner = toCore ? core : innerGal[side];
                connect(ctx, graph, outer, inner);
            }
        }

        ctx.put(BspKeys.STATION_GRAPH, graph);
    }

    /** Which gallery sides to gate for a boundary: 1 gate → one rotating side; 2 gates → two opposite sides. */
    private static int[] gateSides(int ring, int gates) {
        int base = ring % 4;
        return gates >= 2 ? new int[]{ base, (base + 2) % 4 } : new int[]{ base };
    }

    private static StationGraph.Room addRoom(List<StationGraph.Room> rooms, List<Integer> ringList,
                                             int ring, int left, int top, int right, int bottom) {
        StationGraph.Room r = new StationGraph.Room(rooms.size(), left, top, right, bottom);
        rooms.add(r);
        ringList.add(ring);
        return r;
    }

    private static void connect(GenContext ctx, StationGraph graph,
                                StationGraph.Room a, StationGraph.Room b) {
        StationCarve.carveDoorBetween(ctx, a, b);
        graph.addCorridor(a.id, b.id);
    }
}
