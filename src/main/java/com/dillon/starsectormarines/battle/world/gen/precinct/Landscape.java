package com.dillon.starsectormarines.battle.world.gen.precinct;

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
 */
public record Landscape(float woodland, List<Track> tracks, List<Brook> brooks, int raggedMargin) {

    /** The groves' share of wild ground in the stock landscape pass. */
    public static final float STOCK_WOODLAND = 0.58f;

    /** The landscape every plan has unless its mission says otherwise. */
    public static final Landscape STOCK = new Landscape(STOCK_WOODLAND, List.of(), List.of());

    /** A landscape with no ragged field margins. */
    public Landscape(float woodland, List<Track> tracks, List<Brook> brooks) {
        this(woodland, tracks, brooks, 0);
    }

    public Landscape {
        if (raggedMargin < 0) throw new IllegalArgumentException("a margin is a width: " + raggedMargin);
        if (!(woodland >= 0f && woodland <= 1f)) throw new IllegalArgumentException("woodland is a share: " + woodland);
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        brooks = brooks == null ? List.of() : List.copyOf(brooks);
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
    public record Brook(List<int[]> path, int width, int fordEvery, int fordWidth) {
        public Brook {
            if (path == null || path.size() < 2) throw new IllegalArgumentException("a brook runs between two points at least");
            if (width < 1 || fordEvery < 1 || fordWidth < 1) throw new IllegalArgumentException("a brook's sizes are positive");
            path = List.copyOf(path);
        }
    }
}
