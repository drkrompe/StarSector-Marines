package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Crossing;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.CrossingKind;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Drop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where a drop needs a bridge because a road meets it: each run of road cells under the drop, along its length, is
 * one crossing at the run's middle, as wide as the run and three cells more, so a road meeting it aslant is spanned whole. Grid-only, so it is proved on a small grid.
 */
public final class RoadCrossings {

    /** Road cells this far apart along a drop are two roads, not one. */
    static final float GAP = 3f;

    private RoadCrossings() { }

    /** {@code drop}'s crossings with a bridge added for every road run no crossing already covers. */
    public static List<Crossing> add(Drop drop, boolean[] road, int w, int h) {
        boolean[] band = SketchRaster.band(w, h, drop.path(), drop.width());
        float[] along = SketchRaster.along(w, h, drop.path());
        float length = SketchRaster.length(drop.path());
        float[] hits = new float[band.length];
        int n = 0;
        for (int i = 0; i < band.length; i++) if (band[i] && road[i]) hits[n++] = along[i];
        hits = Arrays.copyOf(hits, n);
        Arrays.sort(hits);
        List<Crossing> out = new ArrayList<>(drop.crossings());
        int start = 0;
        for (int k = 1; k <= n; k++) {
            if (k < n && hits[k] - hits[k - 1] <= GAP) continue;
            if (start < n) {
                float from = hits[start], to = hits[k - 1], mid = (from + to) / 2f;
                if (!covered(out, from, to, length)) {
                    int width = Math.max(2, Math.min(24, Math.round(to - from) + 3));
                    out.add(new Crossing(Math.max(0f, Math.min(1f, mid / length)), width, CrossingKind.BRIDGE));
                }
            }
            start = k;
        }
        return out;
    }

    /** Whether one crossing already spans the whole run {@code from}..{@code to} cells along the drop. */
    private static boolean covered(List<Crossing> crossings, float from, float to, float length) {
        for (Crossing c : crossings) {
            float centre = c.at() * length, half = c.width() / 2f;
            if (from >= centre - half && to <= centre + half) return true;
        }
        return false;
    }
}
