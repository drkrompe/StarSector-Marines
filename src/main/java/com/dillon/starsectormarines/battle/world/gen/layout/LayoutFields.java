package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralGround;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Plot;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays a {@link Fields} piece: its plots painted as the colony's fields ({@link RuralGround#paint}), its spill as
 * grain-coloured sand, its water as pond. Each only on open ground no road, building, held site or made ground has
 * taken.
 */
final class LayoutFields {

    private LayoutFields() { }

    static Laid lay(GenContext ctx, Fields fields, boolean[][] held) {
        int sand = RuralGround.surface(ctx, GroundKind.SAND);
        List<int[]> done = new ArrayList<>();
        int painted = 0;
        for (Plot plot : fields.plots()) {
            painted += RuralGround.paint(ctx, plot, (x, y) -> free(ctx, held, x, y));
            done.add(new int[]{plot.x0(), plot.y0()});
        }
        int spilt = 0;
        for (int[] c : fields.spill()) {
            // Grain lies on the hardstand it was spilt on as much as on the ground beside it.
            if (!ctx.grid.inBounds(c[0], c[1]) || !PreparedGround.open(ctx, c[0], c[1])
                    || ctx.topology.getBuildingId(c[0], c[1]) != 0) continue;
            GroundKind under = ctx.topology.getGroundKind(c[0], c[1]);
            if (under == GroundKind.INDOOR || under == GroundKind.WATER || under == GroundKind.STREET) continue;
            ctx.topology.setGroundKind(c[0], c[1], GroundKind.SAND);
            ctx.topology.setSurface(c[0], c[1], sand);
            ctx.topology.setNatureOverlayIndex(c[0], c[1], -1);
            spilt++;
        }
        int ponds = 0;
        for (int[] c : fields.water()) {
            if (!free(ctx, held, c[0], c[1]) && !ctx.isMadeGround(c[0], c[1])) continue;
            if (!PreparedGround.open(ctx, c[0], c[1])) continue;
            ctx.topology.setSurface(c[0], c[1], 0);
            ctx.topology.setGroundKind(c[0], c[1], GroundKind.WATER);
            ctx.topology.setNatureOverlayIndex(c[0], c[1], -1);
            ctx.topology.setTag(c[0], c[1], CellTopology.Tag.SHALLOW_WATER, false);
            ctx.grid.setTag(c[0], c[1], NavigationGrid.CellTag.SHALLOW_WATER, false);
            ctx.grid.setWalkable(c[0], c[1], false);
            ctx.grid.setSeeThrough(c[0], c[1], true);
            ctx.grid.setEdgeCoverSuppressed(c[0], c[1], true);
            ctx.markMadeGround(c[0], c[1]);
            ponds++;
        }
        if (painted + spilt + ponds == 0) {
            return Laid.refused(fields.name(), Laid.Kind.GROUND, fields.required(), "no open ground");
        }
        return new Laid(fields.name(), Laid.Kind.GROUND, Laid.Status.LAID,
                painted + " field cells, " + spilt + " spilt, " + ponds + " water", fields.required(), done,
                List.of(), List.of());
    }

    /** Open natural ground: no road, building, prop, wall or held site, and nothing made on it yet. */
    private static boolean free(GenContext ctx, boolean[][] held, int x, int y) {
        if (!ctx.grid.inBounds(x, y) || held != null && held[x][y]) return false;
        return PreparedGround.open(ctx, x, y) && ctx.topology.getBuildingId(x, y) == 0 && !ctx.isMadeGround(x, y)
                && ctx.topology.getGroundKind(x, y) != GroundKind.STREET
                && ctx.topology.getGroundKind(x, y) != GroundKind.INDOOR;
    }
}
