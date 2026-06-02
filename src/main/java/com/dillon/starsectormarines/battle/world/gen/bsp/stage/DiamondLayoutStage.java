package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.StationGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * The "diamond" defense-station layout — cardinal ports converging inward toward
 * a besieged core. Shares {@link RingGeometry}'s nested rectangles with the
 * concentric layout, but makes one decisive change: <b>outer rings omit their
 * corner rooms</b>. That single move does two things at once —
 * <ul>
 *   <li>the 4 map corners become dead space → a diamond/cruciform footprint, and</li>
 *   <li>the corners were what linked a ring's galleries laterally, so the 4 edge
 *       galleries become <em>isolated</em> cardinal <b>ports</b> (landing zones),
 *       each leading inward only.</li>
 * </ul>
 *
 * <p>Connectivity:
 * <ul>
 *   <li><b>Radial spokes</b> — same-side galleries of consecutive rings are
 *       linked on all 4 sides (the cardinal corridors), from each outer port down
 *       to the connective ring. With no lateral links, every outer spoke corridor
 *       is a must-pass <em>bridge</em> — the forced-inward gauntlet.</li>
 *   <li><b>Connective ring</b> — the innermost {@value #CONNECTIVE_RINGS} ring(s)
 *       keep their corners + the intra-ring loop, so the 4 sections finally
 *       interconnect here (the hub).</li>
 *   <li><b>Core gate</b> — one gate from the connective ring into the core, so the
 *       objective stays a degree-1 besieged room.</li>
 * </ul>
 *
 * <p>Publishes the {@link StationGraph} + ring metadata + the {@link
 * StationGraph#setPorts port list} (for a future multi-spawn insertion). The
 * topology roles narrate it for free: the outer shell is all bridges, the
 * connective ring the only loop, depth-from-entry the radial assault gradient
 * from a port to the core. With a single spawn at one port, the other 3 spokes
 * sit off-path — the believable "untraversed far side."
 */
public final class DiamondLayoutStage implements GenStage {

    /** Innermost ring(s) (by ring index, 1 = just outside the core) that keep corners + a loop — where the isolated cardinal spokes interconnect. */
    private static final int CONNECTIVE_RINGS = 1;

    @Override
    public void run(GenContext ctx) {
        int w = ctx.width;
        int h = ctx.height;

        List<Integer> insets = RingGeometry.insets(w, h);
        int bands = insets.size() - 1;
        int coreInset = insets.get(insets.size() - 1);

        int coreSide = ctx.rng.nextInt(4);      // which connective-ring gallery gates into the core

        List<StationGraph.Room> rooms = new ArrayList<>();
        List<Integer> ringList = new ArrayList<>();
        // Per band, outer..inner (index 0..bands-1): the 4 galleries by side, plus
        // the cyclic-8 for connective bands (null for isolated outer bands).
        List<StationGraph.Room[]> bandGallery = new ArrayList<>();
        List<StationGraph.Room[]> bandCycle = new ArrayList<>();

        for (int i = 0; i < bands; i++) {
            int ring = bands - i;               // outermost = bands, innermost band = 1
            boolean connective = ring <= CONNECTIVE_RINGS;
            int[][] rects = RingGeometry.bandRects(w, h, insets.get(i), insets.get(i + 1));

            StationGraph.Room[] gal = new StationGraph.Room[4];
            StationGraph.Room[] cyc = null;
            if (connective) {
                // Full 8-room ring (corners + galleries) → loop.
                cyc = new StationGraph.Room[rects.length];
                for (int s = 0; s < rects.length; s++) cyc[s] = addRoom(rooms, ringList, ring, rects[s]);
                for (int side = 0; side < 4; side++) gal[side] = cyc[RingGeometry.galleryIndex(side)];
            } else {
                // Outer ring: only the 4 edge galleries — corners omitted (dead, isolated).
                for (int side = 0; side < 4; side++) {
                    gal[side] = addRoom(rooms, ringList, ring, rects[RingGeometry.galleryIndex(side)]);
                }
            }
            bandGallery.add(gal);
            bandCycle.add(cyc);
        }

        StationGraph.Room core = addRoom(rooms, ringList, 0, RingGeometry.rectAt(w, h, coreInset));

        int[] ringOf = new int[ringList.size()];
        for (int k = 0; k < ringOf.length; k++) ringOf[k] = ringList.get(k);
        StationGraph graph = new StationGraph(rooms);
        graph.setRings(ringOf, core.id);

        // Carve every room interior (inset by 1 → 1-cell walls).
        for (StationGraph.Room r : rooms) {
            StationCarve.carveRoomRect(ctx, r.left + 1, r.top + 1, r.right - 1, r.bottom - 1);
        }

        // Intra-ring loops — connective bands only.
        for (StationGraph.Room[] cyc : bandCycle) {
            if (cyc == null) continue;
            for (int j = 0; j < cyc.length; j++) {
                StationCarve.connect(ctx, graph, cyc[j], cyc[(j + 1) % cyc.length]);
            }
        }

        // Radial cardinal spokes — every side, each consecutive ring pair (outer → inner).
        for (int side = 0; side < 4; side++) {
            for (int i = 0; i + 1 < bands; i++) {
                StationCarve.connect(ctx, graph, bandGallery.get(i)[side], bandGallery.get(i + 1)[side]);
            }
        }

        // Core gate — exactly one, from the innermost (connective) ring.
        if (bands >= 1) {
            StationCarve.connect(ctx, graph, bandGallery.get(bands - 1)[coreSide], core);
        }

        // Ports — the outermost band's 4 cardinal galleries (the landing zones).
        if (bands >= 1) {
            StationGraph.Room[] outer = bandGallery.get(0);
            int[] ports = new int[4];
            for (int side = 0; side < 4; side++) ports[side] = outer[side].id;
            graph.setPorts(ports);
        }

        ctx.put(BspKeys.STATION_GRAPH, graph);
    }

    private static StationGraph.Room addRoom(List<StationGraph.Room> rooms, List<Integer> ringList,
                                             int ring, int[] rect) {
        StationGraph.Room r = new StationGraph.Room(rooms.size(), rect[0], rect[1], rect[2], rect[3]);
        rooms.add(r);
        ringList.add(ring);
        return r;
    }
}
