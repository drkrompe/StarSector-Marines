package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lays the plan's {@link MissionLayout} over the finished places, roads and
 * lanes, before the landscape grows round them ({@link Phase#WORKS}), and
 * binds what it made under {@link BspKeys#MISSION_LAYOUT_LAID}.
 *
 * <p>Only ever in a recipe whose plan states a layout, so a map without one is
 * generated exactly as before. Every placement yields to what is there: a cell
 * that is not open ground, or is road, is skipped, so a line through a town is
 * broken by its buildings and reported {@link Laid.Status#PARTIAL}. A line is
 * never allowed to seal the map: wire and nests that would cut the walkable
 * ground apart are not laid, and the line says so.
 */
public final class MissionLayoutStage implements GenStage {

    /** Share of a line's planned works it must lay to count as laid whole. */
    static final float WHOLE = 0.9f;
    /** Walkable cells the closed works may cut off from the main ground beyond their own (pockets behind wire). */
    static final int SEAL_SLACK = 48;
    private static final String NEST_STRUCTURE = "structure.turret-heavy-mg";

    @Override
    public void run(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        MissionLayout layout = plan == null ? MissionLayout.NONE : plan.layout();
        if (layout.isEmpty()) return;
        LayoutGround ground = new LayoutGround(ctx);
        boolean[][] held = layout.keepClear(ctx.width, ctx.height);
        List<Piece> pieces = new ArrayList<>();
        List<Laid> out = new ArrayList<>();
        for (Piece piece : layout.pieces()) {
            if (piece instanceof Deferred deferred) {
                if (deferred.phase() != Phase.WORKS) {
                    out.add(Laid.refused(deferred.name(), Laid.Kind.SITE, deferred.required(),
                            "phase " + deferred.phase() + " is not run yet"));
                    continue;
                }
                List<Piece> resolved = deferred.resolve().apply(ground);
                if (resolved != null) pieces.addAll(resolved);
            } else {
                pieces.add(piece);
            }
        }
        Set<String> names = new HashSet<>();
        boolean closed = false;
        for (Piece piece : pieces) {
            if (!names.add(piece.name())) {
                out.add(Laid.refused(piece.name(), Laid.Kind.SITE, false, "named twice"));
                continue;
            }
            if (piece instanceof Line line) {
                Laid laid = lay(ctx, ground, line, held);
                out.add(laid);
                for (int[] c : laid.cells()) closed |= !ctx.grid.isWalkable(c[0], c[1]);
                closed |= !laid.nests().isEmpty();
            } else if (piece instanceof Site site) {
                boolean inside = site.x0() >= 0 && site.y0() >= 0 && site.x1() < ctx.width && site.y1() < ctx.height;
                Laid.Kind kind = site.purpose() == SitePurpose.GROUND ? Laid.Kind.GROUND : Laid.Kind.SITE;
                out.add(inside
                        ? new Laid(site.name(), kind, Laid.Status.LAID, "", site.required(),
                                List.of(new int[]{site.x0(), site.y0()}, new int[]{site.x1(), site.y1()}), List.of(), List.of())
                        : Laid.refused(site.name(), kind, site.required(), "off the map"));
            } else {
                out.add(Laid.refused(piece.name(), Laid.Kind.SITE, piece.required(), "a deferred piece resolved to another"));
            }
        }
        // Wire and nests close cells after the regions were published: "non-walkable => region -1" must hold.
        if (closed && ctx.get(BspKeys.TACTICAL_REGIONS) != null) {
            ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid, ctx.topology, ctx.get(BspKeys.AXIS)));
        }
        ctx.put(BspKeys.MISSION_LAYOUT_LAID, new LaidLayout(out));
    }

    private static Laid lay(GenContext ctx, LayoutGround ground, Line line, boolean[][] held) {
        LineCourse.Course course = LineCourse.plan(line.path(), ground::isRoad, line.roadGap(), line.frontward(),
                line.style() == LineStyle.TRENCH_WIRE ? line.funnelEvery() : 0);
        int planned = 0;
        List<int[]> works = new ArrayList<>();
        for (List<int[]> run : course.runs()) {
            planned += run.size();
            for (int[] c : run) if (takes(ctx, ground, held, c)) works.add(c);
        }
        if (planned == 0) return Laid.refused(line.name(), Laid.Kind.LINE, line.required(), "no course between roads");
        if (works.isEmpty()) return Laid.refused(line.name(), Laid.Kind.LINE, line.required(), "no open ground on its course");

        Set<Long> worksKeys = keys(works);
        List<int[]> closing = new ArrayList<>();
        if (line.style() == LineStyle.TRENCH_WIRE) {
            for (int[] c : course.wire()) {
                if (!worksKeys.contains(PreparedGround.key(c[0], c[1])) && takes(ctx, ground, held, c)) closing.add(c);
            }
        }
        List<int[]> nests = new ArrayList<>();
        if (line.nestsAtEnds()) for (int[] c : course.nests()) if (takes(ctx, ground, held, c)) nests.add(c);
        List<int[]> shut = new ArrayList<>(closing);
        shut.addAll(nests);
        String reason = "";
        if (!shut.isEmpty() && seals(ctx, shut)) {
            closing.clear();
            nests.clear();
            reason = "wire and nests would seal the ground; works only";
        }

        Set<Long> taken = keys(works);
        taken.addAll(keys(closing));
        taken.addAll(keys(nests));
        ctx.doodads.removeIf(d -> taken.contains(PreparedGround.key(d.cellX, d.cellY)));

        List<int[]> laidCells = new ArrayList<>();
        if (line.style() == LineStyle.SANDBAG) {
            for (int[] c : works) if (PreparedGround.sandbag(ctx, c[0], c[1], line.frontward(), null)) laidCells.add(c);
        } else {
            PreparedGround.trenchLine(ctx, works);
            laidCells.addAll(works);
        }
        boolean horizontal = line.frontward()[1] != 0;
        for (int[] c : closing) if (PreparedGround.fence(ctx, c[0], c[1], horizontal)) laidCells.add(c);
        for (int[] c : nests) {
            ctx.grid.setWalkable(c[0], c[1], false);
            ctx.defensePosts.add(new DefensePost(DefensePostKind.LIGHT, c[0], c[1],
                    List.of(new DefensePost.TurretSpec(NEST_STRUCTURE, c[0], c[1]))));
        }

        List<int[]> gaps = new ArrayList<>();
        for (List<int[]> gap : course.gaps()) {
            int[] middle = null;
            for (int[] c : gap) {
                if (ground.isRoad(c[0], c[1]) && ctx.grid.inBounds(c[0], c[1]) && ctx.grid.isWalkable(c[0], c[1])) {
                    middle = c;
                    if (gap.indexOf(c) >= gap.size() / 2) break;
                }
            }
            if (middle != null) gaps.add(middle);
        }
        if (!closing.isEmpty()) {
            for (int[] c : course.funnels()) {
                if (ctx.grid.inBounds(c[0], c[1]) && ctx.grid.isWalkable(c[0], c[1])) gaps.add(c);
            }
        }
        int laidWorks = line.style() == LineStyle.SANDBAG ? laidCells.size() - closing.size() : works.size();
        Laid.Status status = laidWorks >= WHOLE * planned && reason.isEmpty() ? Laid.Status.LAID : Laid.Status.PARTIAL;
        if (reason.isEmpty() && status == Laid.Status.PARTIAL) reason = "laid " + laidWorks + " of " + planned + " cells";
        return new Laid(line.name(), Laid.Kind.LINE, status, reason, line.required(), laidCells, gaps, nests);
    }

    /** Whether the cell may take a work: open ground that is not road, nor held by a site the layout states up front. */
    private static boolean takes(GenContext ctx, LayoutGround ground, boolean[][] held, int[] c) {
        return PreparedGround.open(ctx, c[0], c[1]) && !ground.isRoad(c[0], c[1])
                && (held == null || !held[c[0]][c[1]]);
    }

    /**
     * Whether closing {@code cells} would cut the walkable ground apart: the main (largest) 4-connected body of
     * walkable ground shrinks by more than the cells themselves and a little slack for pockets behind the wire.
     */
    static boolean seals(GenContext ctx, List<int[]> cells) {
        int before = largestBody(ctx);
        for (int[] c : cells) ctx.grid.setWalkable(c[0], c[1], false);
        int after = largestBody(ctx);
        for (int[] c : cells) ctx.grid.setWalkable(c[0], c[1], true);
        return before - after > cells.size() + SEAL_SLACK;
    }

    private static int largestBody(GenContext ctx) {
        int w = ctx.width, h = ctx.height;
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int best = 0;
        for (int start = 0; start < w * h; start++) {
            if (seen[start] || !ctx.grid.isWalkable(start % w, start / w)) continue;
            int size = 0;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                size++;
                int x = i % w, y = i / w;
                if (x > 0) visit(ctx, seen, queue, i - 1, x - 1, y);
                if (x < w - 1) visit(ctx, seen, queue, i + 1, x + 1, y);
                if (y > 0) visit(ctx, seen, queue, i - w, x, y - 1);
                if (y < h - 1) visit(ctx, seen, queue, i + w, x, y + 1);
            }
            best = Math.max(best, size);
        }
        return best;
    }

    private static void visit(GenContext ctx, boolean[] seen, ArrayDeque<Integer> queue, int i, int x, int y) {
        if (seen[i] || !ctx.grid.isWalkable(x, y)) return;
        seen[i] = true;
        queue.add(i);
    }

    private static Set<Long> keys(List<int[]> cells) {
        Set<Long> out = new HashSet<>();
        for (int[] c : cells) out.add(PreparedGround.key(c[0], c[1]));
        return out;
    }
}
