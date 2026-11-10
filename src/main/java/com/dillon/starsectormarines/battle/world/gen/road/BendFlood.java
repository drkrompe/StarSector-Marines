package com.dillon.starsectormarines.battle.world.gen.road;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A flood over the cell grid that pays for every bend, so the way it finds back to its sources is a planner's road:
 * straight runs joined by a few deliberate bends, not the staircase a breadth-first flood walks back down wherever
 * its target lies off both axes ({@code mapgen-nouns.md}, Authored roads). Every step is along a grid axis; a step
 * that changes direction costs {@code bend} on top of the ground's own step cost, so of two ways the same length the
 * one with fewer bends wins, and a way a few cells longer wins when it saves a bend.
 *
 * <p>The flood starts from every source at once, each carrying its label, and runs to the whole grid: each cell
 * learns its cheapest cost, the label of the source that reached it, and the way back ({@link #back}). Ties are
 * broken by the order states were reached, so the flood is a pure function of its inputs.
 */
public final class BendFlood {

    /** What a planner's road gives up to save a bend, in cells of length. */
    public static final int BEND = 6;

    /** The ground's price for a step onto a cell: at least one, or negative where no road may go. */
    @FunctionalInterface
    public interface StepCost {
        int at(int x, int y);
    }

    private static final int[] DX = {1, -1, 0, 0}, DY = {0, 0, 1, -1};

    private final int width, height;
    private final int[] cost, parent;
    private final int[] label;

    private BendFlood(int width, int height) {
        this.width = width;
        this.height = height;
        cost = new int[width * height * 4];
        parent = new int[width * height * 4];
        label = new int[width * height * 4];
        Arrays.fill(cost, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);
        Arrays.fill(label, -1);
    }

    /**
     * Floods from every cell whose {@code source} label is zero or more, over the cells {@code step} prices, paying
     * {@code bend} for each change of direction. A source cell is never entered from another.
     */
    public static BendFlood from(int[][] source, StepCost step, int bend) {
        int w = source.length, h = source[0].length;
        BendFlood f = new BendFlood(w, h);
        LongHeap open = new LongHeap();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (source[x][y] < 0) continue;
            for (int d = 0; d < 4; d++) {
                int s = (y * w + x) * 4 + d;
                f.cost[s] = 0;
                f.label[s] = source[x][y];
                open.push(((long) 0 << 32) | s);
            }
        }
        while (!open.isEmpty()) {
            long top = open.pop();
            int s = (int) top, c = (int) (top >>> 32);
            if (c > f.cost[s]) continue;
            int cell = s >> 2, d = s & 3, x = cell % w, y = cell / w;
            for (int nd = 0; nd < 4; nd++) {
                if (nd == (d ^ 1)) continue;
                int nx = x + DX[nd], ny = y + DY[nd];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || source[nx][ny] >= 0) continue;
                int price = step.at(nx, ny);
                if (price < 0) continue;
                int next = c + price + (nd == d ? 0 : bend);
                int ns = (ny * w + nx) * 4 + nd;
                if (next >= f.cost[ns]) continue;
                f.cost[ns] = next;
                f.parent[ns] = s;
                f.label[ns] = f.label[s];
                open.push(((long) next << 32) | ns);
            }
        }
        return f;
    }

    private int best(int x, int y) {
        int base = (y * width + x) * 4, at = -1;
        for (int d = 0; d < 4; d++) if (cost[base + d] != Integer.MAX_VALUE && (at < 0 || cost[base + d] < cost[at])) at = base + d;
        return at;
    }

    /** The cheapest cost to {@code (x, y)} from any source, or {@link Integer#MAX_VALUE} where none reaches. */
    public int cost(int x, int y) {
        int s = best(x, y);
        return s < 0 ? Integer.MAX_VALUE : cost[s];
    }

    /** The label of the source whose flood reached {@code (x, y)} cheapest, or -1 where none did. */
    public int label(int x, int y) {
        int s = best(x, y);
        return s < 0 ? -1 : label[s];
    }

    /** The cells from {@code (x, y)} back to the source that reached it, both included; empty where none did. */
    public List<int[]> back(int x, int y) {
        List<int[]> out = new ArrayList<>();
        for (int s = best(x, y); s >= 0; s = parent[s]) {
            int cell = s >> 2;
            out.add(new int[]{cell % width, cell / width});
        }
        return out;
    }

    /** A binary min-heap of longs, so the flood boxes nothing. */
    private static final class LongHeap {
        private long[] heap = new long[1024];
        private int size;

        boolean isEmpty() { return size == 0; }

        void push(long v) {
            if (size == heap.length) heap = Arrays.copyOf(heap, size * 2);
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (heap[p] <= v) break;
                heap[i] = heap[p];
                i = p;
            }
            heap[i] = v;
        }

        long pop() {
            long top = heap[0], last = heap[--size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size) break;
                int r = l + 1, m = r < size && heap[r] < heap[l] ? r : l;
                if (heap[m] >= last) break;
                heap[i] = heap[m];
                i = m;
            }
            heap[i] = last;
            return top;
        }
    }
}
