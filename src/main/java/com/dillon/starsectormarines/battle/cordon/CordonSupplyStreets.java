package com.dillon.starsectormarines.battle.cordon;

import java.util.ArrayList;
import java.util.List;

/**
 * A city's supply road ({@code cordon-nouns.md}, Logistics base): where no supply road could be laid across open
 * country, the city's own streets are the supply road. The way is the one a truck drives: the vehicle router's route
 * for a truck's footprint from the base's dispatch cell to a redoubt's rear gate, over the map's road network as the
 * router prices it ({@code TerrainCostField}: a street costs least, a yard more, open ground most). A truck sets out
 * from its base end, the first street cell on it outside the base's yard from which it can drive the rest, as a rural
 * truck sets out from the base's end of its supply road.
 *
 * <p>Not the map generator's {@code RoadGraph}: in a city that is the block partition's whole skeleton, every lot line
 * as well as every street, most of it narrower than a truck and much of it across yards. Ways found over it ran round
 * redoubts through back lots, and missed a gate a straight street led to.
 */
public final class CordonSupplyStreets {

    private CordonSupplyStreets() { }

    /** Whether a cell is street. */
    interface Street {
        boolean at(int x, int y);
    }

    /** A router's corner points ({@code xs}, {@code ys}) as the cells between them, in order, each a step from the last. */
    public static List<int[]> cells(float[] xs, float[] ys) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            int x1 = (int) Math.floor(xs[i]), y1 = (int) Math.floor(ys[i]);
            if (out.isEmpty()) {
                out.add(new int[]{x1, y1});
                continue;
            }
            int[] last = out.get(out.size() - 1);
            int x0 = last[0], y0 = last[1];
            int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            for (int k = 1; k <= steps; k++) {
                out.add(new int[]{x0 + Math.round((x1 - x0) * (float) k / steps),
                        y0 + Math.round((y1 - y0) * (float) k / steps)});
            }
        }
        return out;
    }

    /**
     * Where along a way a truck may set out, in the order to try them: its first {@code tries} street cells at least
     * {@code pitch} apart (the base end first: outside the base's yard, as a rural truck sets out at the base's end of
     * its road), then the way's first cell, the base's own door.
     */
    static List<int[]> starts(List<int[]> way, Street street, int tries, int pitch) {
        List<int[]> out = new ArrayList<>();
        int last = -pitch;
        for (int i = 0; i < way.size() && out.size() < tries; i++) {
            int[] c = way.get(i);
            if (i - last < pitch || !street.at(c[0], c[1])) continue;
            out.add(c);
            last = i;
        }
        if (out.isEmpty() || out.get(0) != way.get(0)) out.add(way.get(0));
        return out;
    }
}
