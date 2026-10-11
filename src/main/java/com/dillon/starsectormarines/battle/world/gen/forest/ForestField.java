package com.dillon.starsectormarines.battle.world.gen.forest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Deterministic crown packing inside painted ground. The union of actual canopies is the blocked grid. */
public final class ForestField {
    public record Tree(int x, int y, TreeCanopy canopy, int turns) {
        public int width() { return canopy.width(turns); }
        public int height() { return canopy.height(turns); }
        public boolean blocks(int x, int y) { return canopy.blocks(x - this.x, y - this.y, turns); }
    }
    private record Candidate(int x, int y, long key) {}
    private final int width, height;
    private final boolean[] blocked;
    private final List<Tree> trees;
    private ForestField(int width, int height, boolean[] blocked, List<Tree> trees) {
        this.width = width; this.height = height; this.blocked = blocked; this.trees = List.copyOf(trees);
    }
    public boolean blocked(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height && blocked[y * width + x]; }
    public List<Tree> trees() { return trees; }

    public static ForestField build(ForestPlan plan) {
        int w = plan.width(), h = plan.height();
        boolean[] paint = paint(plan), blocked = new boolean[w * h];
        List<Tree> trees = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        // A fixed jittered lattice keeps the seed stable when strokes are edited.
        for (int y = 0; y < h; y += 2) for (int x = 0; x < w; x += 2) {
            long key = mix(plan.seed() ^ ((long)x << 32) ^ y);
            int cx = Math.min(w - 1, x + (int)(key & 1)), cy = Math.min(h - 1, y + (int)((key >>> 1) & 1));
            if (paint[cy * w + cx]) candidates.add(new Candidate(cx, cy, key));
        }
        candidates.sort(Comparator.comparingLong(Candidate::key).thenComparingInt(Candidate::y).thenComparingInt(Candidate::x));
        TreeCanopy[] pool = Arrays.stream(TreeCanopy.values()).filter(plan.woodland()::includes).toArray(TreeCanopy[]::new);
        for (Candidate candidate : candidates) {
            int first = (int)Math.floorMod(candidate.key() >>> 3, pool.length);
            int turns = (int)((candidate.key() >>> 12) & 3);
            for (int attempt = 0; attempt < pool.length; attempt++) {
                TreeCanopy canopy = pool[(first + attempt) % pool.length];
                Tree tree = new Tree(candidate.x() - canopy.width(turns) / 2,
                        candidate.y() - canopy.height(turns) / 2, canopy, turns);
                if (!fits(tree, paint, blocked, w, h)) continue;
                trees.add(tree);
                for (int y = tree.y(); y < tree.y() + tree.height(); y++)
                    for (int x = tree.x(); x < tree.x() + tree.width(); x++)
                        if (tree.blocks(x, y)) blocked[y * w + x] = true;
                break;
            }
        }
        // Consistent spatial drawing order keeps overlapping crowns legible.
        trees.sort(Comparator.comparingInt(Tree::y).reversed().thenComparingInt(Tree::x));
        return new ForestField(w, h, blocked, trees);
    }

    private static boolean fits(Tree tree, boolean[] paint, boolean[] blocked, int w, int h) {
        if (tree.x() < 0 || tree.y() < 0 || tree.x() + tree.width() > w || tree.y() + tree.height() > h) return false;
        int occupied = 0, fresh = 0;
        for (int y = tree.y(); y < tree.y() + tree.height(); y++) for (int x = tree.x(); x < tree.x() + tree.width(); x++) {
            if (!tree.blocks(x, y)) continue;
            if (!paint[y * w + x]) return false;
            occupied++;
            if (!blocked[y * w + x]) fresh++;
        }
        return fresh >= Math.max(3, occupied * .38);
    }

    /** Ordered swept capsules; a sparse drag paints the whole segment and erasing excludes entire canopy cells. */
    static boolean[] paint(ForestPlan plan) {
        int w = plan.width(), h = plan.height(); boolean[] paint = new boolean[w * h];
        for (var stroke : plan.strokes()) for (int i = 0; i < stroke.points().size(); i++) {
            var a = stroke.points().get(Math.max(0, i - 1)); var b = stroke.points().get(i);
            double r = stroke.radius(), dx = b.x() - a.x(), dy = b.y() - a.y(), length = dx * dx + dy * dy;
            int x0 = Math.max(0, (int)Math.floor(Math.min(a.x(), b.x()) - r));
            int x1 = Math.min(w - 1, (int)Math.ceil(Math.max(a.x(), b.x()) + r));
            int y0 = Math.max(0, (int)Math.floor(Math.min(a.y(), b.y()) - r));
            int y1 = Math.min(h - 1, (int)Math.ceil(Math.max(a.y(), b.y()) + r));
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                double px = x - a.x(), py = y - a.y(), t = length == 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy) / length));
                if (Math.hypot(px - t * dx, py - t * dy) <= r) paint[y * w + x] = stroke.mode() == ForestPlan.Mode.PAINT;
            }
        }
        return paint;
    }
    private static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
}
