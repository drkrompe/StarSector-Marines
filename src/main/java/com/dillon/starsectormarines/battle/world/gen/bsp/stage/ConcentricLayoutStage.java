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
 * <p>Geometry comes from {@link RingGeometry}: nested centered rectangles down to
 * a central core, each ring <em>band</em> split into 8 rooms — 4 corner
 * strongpoints + 4 edge galleries — carved inset by 1 so 1-cell walls separate
 * every room and every ring. Connectivity is then carved:
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

    /** Gates on the outermost ring boundary — a flanking loop near the breach. */
    private static final int OUTER_GATES = 2;
    /** Gates on every inner ring boundary — each a hard must-pass bridge toward the core. */
    private static final int INNER_GATES = 1;

    @Override
    public void run(GenContext ctx) {
        int w = ctx.width;
        int h = ctx.height;

        List<Integer> insets = RingGeometry.insets(w, h);
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
            int[][] rects = RingGeometry.bandRects(w, h, insets.get(i), insets.get(i + 1));

            StationGraph.Room[] cyc = new StationGraph.Room[rects.length];
            for (int s = 0; s < rects.length; s++) {
                cyc[s] = addRoom(rooms, ringList, ring, rects[s]);
            }
            bandCycle.add(cyc);
            // Galleries by side (0=top,1=right,2=bottom,3=left) — odd cyclic slots.
            bandGallery.add(new StationGraph.Room[]{
                    cyc[RingGeometry.galleryIndex(0)], cyc[RingGeometry.galleryIndex(1)],
                    cyc[RingGeometry.galleryIndex(2)], cyc[RingGeometry.galleryIndex(3)] });
        }

        // Core room (ring 0).
        StationGraph.Room core = addRoom(rooms, ringList, 0, RingGeometry.rectAt(w, h, coreInset));

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
                StationCarve.connect(ctx, graph, cyc[j], cyc[(j + 1) % cyc.length]);
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
                StationCarve.connect(ctx, graph, outer, inner);
            }
        }

        ctx.put(BspKeys.STATION_GRAPH, graph);
    }

    /** Which gallery sides (0=top,1=right,2=bottom,3=left) to gate: 1 gate → one rotating side; 2 gates → two opposite sides. {@code sideBase} is the pre-rotated index. */
    private static int[] gateSides(int sideBase, int gates) {
        int base = Math.floorMod(sideBase, 4);
        return gates >= 2 ? new int[]{ base, (base + 2) % 4 } : new int[]{ base };
    }

    private static StationGraph.Room addRoom(List<StationGraph.Room> rooms, List<Integer> ringList,
                                             int ring, int[] rect) {
        StationGraph.Room r = new StationGraph.Room(rooms.size(), rect[0], rect[1], rect[2], rect[3]);
        rooms.add(r);
        ringList.add(ring);
        return r;
    }
}
