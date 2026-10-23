package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lays a layout's {@link Block}s with the works: each cell where the ground is open, never sealing the map. */
final class LayoutBlocks {

    private static final String HEDGEHOG = "doodad.fort-hedgehog";
    private static final String CRATES = "doodad.industrial-crate-stack";
    /** Every this many barricade cells, a crate stack in place of a hedgehog. */
    private static final int CRATE_EVERY = 4;

    private LayoutBlocks() {}

    static Laid lay(GenContext ctx, Block b, boolean[][] held) {
        List<int[]> open = new ArrayList<>();
        for (int[] c : b.cells()) {
            if (PreparedGround.open(ctx, c[0], c[1]) && (held == null || !held[c[0]][c[1]])) open.add(c);
        }
        if (open.isEmpty()) return Laid.refused(b.name(), Laid.Kind.BLOCK, b.required(), "no open ground under it");
        if (b.kind() != BlockKind.SANDBAG && MissionLayoutStage.seals(ctx, open)) {
            return Laid.refused(b.name(), Laid.Kind.BLOCK, b.required(), "it would seal the ground");
        }
        Set<Long> keys = new HashSet<>();
        for (int[] c : open) keys.add(PreparedGround.key(c[0], c[1]));
        ctx.doodads.removeIf(d -> keys.contains(PreparedGround.key(d.cellX, d.cellY)));
        List<int[]> laid = new ArrayList<>();
        for (int i = 0; i < open.size(); i++) {
            int[] c = open.get(i);
            boolean ok = switch (b.kind()) {
                case SANDBAG -> PreparedGround.sandbag(ctx, c[0], c[1], b.facing(), null);
                case WRECK -> PreparedGround.obstacle(ctx, c[0], c[1], CRATES);
                case BARRICADE -> PreparedGround.obstacle(ctx, c[0], c[1],
                        i % CRATE_EVERY == CRATE_EVERY - 1 ? CRATES : HEDGEHOG);
            };
            if (ok) laid.add(c);
        }
        if (laid.isEmpty()) return Laid.refused(b.name(), Laid.Kind.BLOCK, b.required(), "no piece could stand");
        Laid.Status status = laid.size() >= MissionLayoutStage.WHOLE * b.cells().size()
                ? Laid.Status.LAID : Laid.Status.PARTIAL;
        return new Laid(b.name(), Laid.Kind.BLOCK, status,
                status == Laid.Status.LAID ? "" : "laid " + laid.size() + " of " + b.cells().size() + " cells",
                b.required(), laid, List.of(), List.of());
    }
}
