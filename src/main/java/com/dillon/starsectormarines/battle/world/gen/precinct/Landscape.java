package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;

import java.util.ArrayList;
import java.util.List;

/**
 * What a plan says about the country between its places: how much of the wild
 * ground is wood, and which straight tracks are cut through it.
 *
 * <p>The stock answer is what every precinct map had before a mission could
 * say anything: groves where the landscape pass's noise puts them and no tracks.
 * A mission states more only when its battle is about the country, as the
 * heart's is (`bloom-mission-arc.md`): its forest is the fuel The Burn sets
 * alight and the cover Broad Front's lanes advance through.
 *
 * @param woodland share of the wild ground the groves may cover, 0 to 1; the
 *                 stock pass is {@link #STOCK_WOODLAND}
 * @param tracks   straight strips cleared to bare earth before the woods are
 *                 grown, so no tree stands on them: a firebreak ride across an
 *                 approach, or a track along one
 * @param brooks   streams run across the country before the woods are grown:
 *                 water nobody walks through, crossed at fords
 * @param raggedMargin cells, at most, that a seeded ragged line of rough grass
 *                 and scrub wanders either side of a field's straight edge,
 *                 over the bare earth beside it or into the parcel; 0 for none. A burn then stops on an organic line
 *                 rather than a field's ruler edge.
 * @param ridges   high ground the mission names: each band raised as hill
 *                 segments, broken into passes wherever roads, buildings or
 *                 the landing cross it ({@link RidgePlanner})
 * @param held     ridges whose ground is held open before the places grow, so
 *                 the town, its roads and its fields grow round them: high
 *                 ground that has to stand among built places, such as a rim
 *                 round a settlement ({@link RidgePlanner#heldGround})
 * @param heldFromGrowth whether the places' growth and claims keep off the held
 *                 ground as well as their fields; false holds it from the
 *                 fields alone, so the places grow exactly as they would have
 *                 and only ploughing yields to the ridge
 * @param kept     boxes held open round places that will stand on high ground
 *                 (a terrace, {@code elevation-nouns.md}): kept from the other
 *                 places' growth, claims, road arms and fields whatever
 *                 {@code heldFromGrowth} says, so the cliff round the place has
 *                 open country to stand in. Never raised themselves.
 * @param sealed   ridges whose ground is held as {@code held} is, from growth whatever {@code heldFromGrowth}
 *                 says, and is also gone round by everything generation routes: the welds between road networks,
 *                 a place's road off the map, the layout's roads, the tracks, a landing's approach and the
 *                 landscape's access routes. Its bands are crossed
 *                 only at the gaps between them, the passes the mission left, so a ring of them stands as one: a
 *                 hollow's rim ({@code elevation-nouns.md}, Sealed ground)
 * @param knolls   places stood on a knoll ({@link Knoll}): grown inside a flat top round their seed, with a ring of
 *                 open country kept round it for the cliff and roads that leave it only through gates
 */
public record Landscape(float woodland, List<Track> tracks, List<Brook> brooks, int raggedMargin,
                        List<RidgePlanner.Band> ridges, List<RidgePlanner.Band> held, boolean heldFromGrowth,
                        List<Track> kept, List<RidgePlanner.Band> sealed, List<Knoll> knolls) {

    /** The groves' share of wild ground in the stock landscape pass. */
    public static final float STOCK_WOODLAND = 0.58f;

    /** The landscape every plan has unless its mission says otherwise. */
    public static final Landscape STOCK = new Landscape(STOCK_WOODLAND, List.of(), List.of());

    /** A landscape with no ragged field margins. */
    public Landscape(float woodland, List<Track> tracks, List<Brook> brooks) {
        this(woodland, tracks, brooks, 0);
    }

    /** A landscape with no ridges. */
    public Landscape(float woodland, List<Track> tracks, List<Brook> brooks, int raggedMargin) {
        this(woodland, tracks, brooks, raggedMargin, List.of());
    }

    /** A landscape with no held ground. */
    public Landscape(float woodland, List<Track> tracks, List<Brook> brooks, int raggedMargin,
                     List<RidgePlanner.Band> ridges) {
        this(woodland, tracks, brooks, raggedMargin, ridges, List.of(), true, List.of());
    }

    /** A landscape with no sealed ground and no knolls. */
    public Landscape(float woodland, List<Track> tracks, List<Brook> brooks, int raggedMargin,
                     List<RidgePlanner.Band> ridges, List<RidgePlanner.Band> held, boolean heldFromGrowth,
                     List<Track> kept) {
        this(woodland, tracks, brooks, raggedMargin, ridges, held, heldFromGrowth, kept, List.of(), List.of());
    }

    /** This landscape with these ridges held open before the places grow. */
    public Landscape withHeld(List<RidgePlanner.Band> held) {
        return new Landscape(woodland, tracks, brooks, raggedMargin, ridges, held, true, kept, sealed, knolls);
    }

    /**
     * This landscape with these ridges held from the fields alone: the places
     * grow and claim as they would have, and no field is ploughed on the band.
     */
    public Landscape withUnploughed(List<RidgePlanner.Band> held) {
        return new Landscape(woodland, tracks, brooks, raggedMargin, ridges, held, false, kept, sealed, knolls);
    }

    /** This landscape with these boxes kept open round places that will stand high ({@link #kept}). */
    public Landscape withKept(List<Track> kept) {
        return new Landscape(woodland, tracks, brooks, raggedMargin, ridges, held, heldFromGrowth, kept, sealed,
                knolls);
    }

    /** This landscape with these ridges sealed: held from growth and gone round by every route ({@link #sealed}). */
    public Landscape withSealed(List<RidgePlanner.Band> sealed) {
        return new Landscape(woodland, tracks, brooks, raggedMargin, ridges, held, heldFromGrowth, kept, sealed,
                knolls);
    }

    /** This landscape with these places stood on knolls ({@link Knoll}). */
    public Landscape withKnolls(List<Knoll> knolls) {
        return new Landscape(woodland, tracks, brooks, raggedMargin, ridges, held, heldFromGrowth, kept, sealed,
                knolls);
    }

    /** Every band the landscape holds before growth: its held ridges and its sealed ones. */
    public List<RidgePlanner.Band> allHeld() {
        if (sealed.isEmpty()) return held;
        List<RidgePlanner.Band> out = new ArrayList<>(held);
        out.addAll(sealed);
        return out;
    }

    public Landscape {
        if (raggedMargin < 0) throw new IllegalArgumentException("a margin is a width: " + raggedMargin);
        if (!(woodland >= 0f && woodland <= 1f)) throw new IllegalArgumentException("woodland is a share: " + woodland);
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        brooks = brooks == null ? List.of() : List.copyOf(brooks);
        ridges = ridges == null ? List.of() : List.copyOf(ridges);
        held = held == null ? List.of() : List.copyOf(held);
        kept = kept == null ? List.of() : List.copyOf(kept);
        sealed = sealed == null ? List.of() : List.copyOf(sealed);
        knolls = knolls == null ? List.of() : List.copyOf(knolls);
    }

    /**
     * A place stood on a knoll ({@code elevation-nouns.md}, Knolls): a flat top {@code halfWidth} by
     * {@code halfHeight} cells either side of the place's seed (moved in from the map's edge as far as it must be),
     * the place growing only inside it, and {@code ring} cells of open country kept round it that nothing grows,
     * claims, ploughs or welds a road across, for the terrace's cliff and its ramps. The place's roads leave through
     * a gate on each side {@code gates} names: a straight road from its streets out across the ring, which the
     * terrace ramps down. The knoll is raised as a terrace with the map's other elevation and reported under
     * {@code name}; a required one that cannot stand fails its map.
     *
     * @param gates the sides the place's roads leave by, as cardinal directions (north is up the map, +y)
     */
    public record Knoll(String name, String place, int halfWidth, int halfHeight, int ring, List<Direction> gates,
                        boolean required) {
        /** Cells of ring that leave the terrace its margin, its cliff and its apron, and a road's reservation beyond. */
        public static final int STOCK_RING = 14;

        public Knoll {
            if (name == null || name.isEmpty() || place == null || place.isEmpty()) {
                throw new IllegalArgumentException("a knoll names itself and its place");
            }
            if (halfWidth < 6 || halfHeight < 6) throw new IllegalArgumentException("a knoll's top is 13 cells across at least");
            if (ring < 10) throw new IllegalArgumentException("a knoll's ring leaves room for a cliff: 10 cells at least");
            gates = gates == null ? List.of() : List.copyOf(gates);
            if (gates.isEmpty()) throw new IllegalArgumentException("a knoll's place needs a gate to leave by");
            for (Direction d : gates) {
                if (d.dx != 0 && d.dy != 0) throw new IllegalArgumentException("a gate faces a side: " + d);
            }
            if (gates.stream().distinct().count() != gates.size()) throw new IllegalArgumentException("a side has one gate");
        }

        /** A knoll with the stock ring. */
        public Knoll(String name, String place, int halfWidth, int halfHeight, List<Direction> gates, boolean required) {
            this(name, place, halfWidth, halfHeight, STOCK_RING, gates, required);
        }
    }

    /**
     * One straight cleared strip as an inclusive cell rect. It clears only open
     * natural ground: a building, a road or a place it crosses stays what it
     * is, so a track through a town is the town's streets there.
     */
    public record Track(int x0, int y0, int x1, int y1) {
        public Track {
            if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("a track's rect is inclusive and ordered");
        }

        public boolean contains(int x, int y) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }
    }

    /**
     * A stream along a line of cells: water {@code width} cells across, with a
     * strip of sand on each bank, crossed on foot at a ford every
     * {@code fordEvery} cells along it. Like a track it takes only open natural
     * ground, so a road crossing it is its bridge. Nothing walks through water
     * and creep does not climb it, so a brook is a firebreak and a line the
     * creep crosses only at its fords and bridges.
     *
     * @param path cells the stream's centre line passes through, in order, each {@code {x, y}}
     */
    /**
     * A stream: its path, its water's width, and a ford every {@code fordEvery}
     * cells along it, {@code fordWidth} wide. {@code banks} raises its valley's
     * sides: a ridge that many cells through along each bank, broken at every
     * ford and wherever a road crosses, climbed from the country away from the
     * water, so the fords and bridges are the only ways down to it; 0 for none.
     */
    public record Brook(List<int[]> path, int width, int fordEvery, int fordWidth, int banks) {
        public Brook {
            if (path == null || path.size() < 2) throw new IllegalArgumentException("a brook runs between two points at least");
            if (width < 1 || fordEvery < 1 || fordWidth < 1) throw new IllegalArgumentException("a brook's sizes are positive");
            if (banks < 0) throw new IllegalArgumentException("banks are a thickness: " + banks);
            path = List.copyOf(path);
        }

        /** A brook in open country, with no valley sides. */
        public Brook(List<int[]> path, int width, int fordEvery, int fordWidth) {
            this(path, width, fordEvery, fordWidth, 0);
        }

        /** The brook's middle a cell at a time along its path, source to mouth. */
        public List<int[]> line() {
            List<int[]> line = new ArrayList<>();
            for (int i = 0; i + 1 < path.size(); i++) {
                int[] a = path.get(i), b = path.get(i + 1);
                int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
                for (int k = i == 0 ? 0 : 1; k <= steps; k++) {
                    line.add(new int[]{Math.round(a[0] + (b[0] - a[0]) * k / (float) Math.max(1, steps)),
                            Math.round(a[1] + (b[1] - a[1]) * k / (float) Math.max(1, steps))});
                }
            }
            return line;
        }

        /** Whether the {@code i}th cell of {@link #line} is a ford: one every {@code fordEvery}, never at the source. */
        public boolean isFord(int i) {
            return i >= fordWidth && i % fordEvery < fordWidth;
        }
    }
}
