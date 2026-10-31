package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lays a layout's {@link Paving} in the structures phase: street ground on every open cell of it. */
final class LayoutPaving {

    private LayoutPaving() {}

    static Laid lay(GenContext ctx, Paving p) {
        Set<Long> cells = new HashSet<>();
        List<int[]> laid = new ArrayList<>();
        for (int[] c : p.cells()) {
            int x = c[0], y = c[1];
            if (x < 1 || y < 1 || x >= ctx.width - 1 || y >= ctx.height - 1) continue;
            if (!PreparedGround.open(ctx, x, y) || ctx.topology.getBuildingId(x, y) != 0
                    || ctx.topology.getGroundKind(x, y) == GroundKind.INDOOR) continue;
            if (!cells.add((long) x << 32 | (y & 0xffffffffL))) continue;
            laid.add(c);
        }
        ctx.doodads.removeIf(d -> cells.contains((long) d.cellX << 32 | (d.cellY & 0xffffffffL)));
        for (int[] c : laid) {
            ctx.topology.setGroundKind(c[0], c[1], GroundKind.STREET);
            ctx.topology.setSurface(c[0], c[1], 0);
            ctx.topology.setNatureOverlayIndex(c[0], c[1], -1);
            ctx.markMadeGround(c[0], c[1]);
        }
        if (laid.isEmpty()) return Laid.refused(p.name(), Laid.Kind.ROAD, p.required(), "no open ground on its course");
        boolean whole = laid.size() >= MissionLayoutStage.WHOLE * p.cells().size();
        return new Laid(p.name(), Laid.Kind.ROAD, whole ? Laid.Status.LAID : Laid.Status.PARTIAL,
                whole ? "" : "paved " + laid.size() + " of " + p.cells().size() + " cells", p.required(), laid,
                List.of(), List.of());
    }
}
