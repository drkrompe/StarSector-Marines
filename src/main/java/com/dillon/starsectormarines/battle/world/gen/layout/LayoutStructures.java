package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;

/**
 * Raises a layout's {@link Structure}s in the structures phase, before the map
 * is finished: buildings through their authored plans, rings and stores from
 * the fort's own props. A structure whose footprint is not all ground it may
 * stand on is refused, never squeezed.
 */
final class LayoutStructures {

    /** The square's marker: there is no bell art yet, so a mast stands for it (placeholder). */
    static final String MARKER = "doodad.military-radar-dish";
    private static final String CRATES = "doodad.industrial-crate-stack";
    private static final String PAVING = "pavement.concrete.new";

    private LayoutStructures() {}

    static Laid raise(GenContext ctx, LayoutGround ground, Structure s, boolean[][] held) {
        int w = s.width(), h = s.height();
        if (s.x() < 1 || s.y() < 1 || s.x() + w >= ctx.width || s.y() + h >= ctx.height) {
            return Laid.refused(s.name(), Laid.Kind.STRUCTURE, s.required(), "off the map");
        }
        LineCourse.CellTest standable = (x, y) -> (held == null || !held[x][y])
                && (ground.buildable(x, y)
                || s.kind().takesRoad() && ground.isRoad(x, y) && PreparedGround.open(ctx, x, y));
        if (!StructureFit.fits(standable, s.x(), s.y(), w, h)) {
            return Laid.refused(s.name(), Laid.Kind.STRUCTURE, s.required(), "its ground is not open");
        }
        int x0 = s.x(), y0 = s.y(), x1 = x0 + w - 1, y1 = y0 + h - 1;
        ctx.doodads.removeIf(d -> d.cellX >= x0 && d.cellX <= x1 && d.cellY >= y0 && d.cellY <= y1);
        switch (s.kind()) {
            case FARMSTEAD -> {
                yard(ctx, x0, y0, x1, y1);
                int x = x0 + StructureFit.YARD, y = y0 + StructureFit.YARD;
                BuildingStamp.FARMHOUSE.place(ctx, x, y, s.turns());
                if ((s.turns() & 1) == 0) {
                    BuildingStamp.BARN.place(ctx, x + StructureFit.HOUSE_ALONG + StructureFit.BETWEEN, y, s.turns());
                } else {
                    BuildingStamp.BARN.place(ctx, x, y + StructureFit.HOUSE_ALONG + StructureFit.BETWEEN, s.turns());
                }
            }
            case GRANARY -> {
                yard(ctx, x0, y0, x1, y1);
                BuildingStamp.BARN.place(ctx, x0 + StructureFit.YARD, y0 + StructureFit.YARD, s.turns());
            }
            case AMMO_DUMP -> {
                ring(ctx, x0, y0, x1, y1, s.turns());
                // Stacks in rows with a walk between them, so the crew can reach every one.
                for (int x = x0 + 2; x <= x1 - 2; x += 2) {
                    for (int y = y0 + 2; y <= y1 - 2; y++) PreparedGround.obstacle(ctx, x, y, CRATES);
                }
            }
            case OBSERVATION_POST -> ring(ctx, x0, y0, x1, y1, s.turns());
            case BELL_SQUARE -> square(ctx, x0, y0, x1, y1);
            default -> s.kind().stamp.place(ctx, x0, y0, s.turns());
        }
        return new Laid(s.name(), Laid.Kind.STRUCTURE, Laid.Status.LAID, "", s.required(),
                List.of(new int[]{x0, y0}, new int[]{x1, y1}), List.of(), List.of());
    }

    /** Worked earth over the footprint: a farm's or a store's yard. */
    private static void yard(GenContext ctx, int x0, int y0, int x1, int y1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) PreparedGround.workedGround(ctx, x, y);
    }

    /** Paving over the footprint and a marker in its middle. */
    private static void square(GenContext ctx, int x0, int y0, int x1, int y1) {
        int paving = ctx.topology.surfaceIndex(PAVING);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                ctx.topology.setGroundKind(x, y, GroundKind.SIDEWALK);
                if (paving >= 0) ctx.topology.setSurface(x, y, paving);
                ctx.topology.setNatureOverlayIndex(x, y, -1);
                ctx.markMadeGround(x, y);
            }
        }
        int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        DoodadDef marker = TileRegistry.installed().doodad(MARKER);
        if (marker != null && marker.footprintCellsX == 1 && marker.footprintCellsY == 1) {
            ctx.grid.setWalkable(cx, cy, false);
            ctx.topology.setFixture(cx, cy, true);
            ctx.doodads.add(new Doodad(cx, cy, marker));
        } else {
            PreparedGround.obstacle(ctx, cx, cy, CRATES);
        }
    }

    /**
     * A sandbag ring on the footprint's edge, bags facing out and corners turned, open in the middle of one side
     * (the side {@code turns} names: 0 south, 1 west, 2 north, 3 east) so it can be walked into.
     */
    private static void ring(GenContext ctx, int x0, int y0, int x1, int y1, int turns) {
        int mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            for (int y = y0 + 1; y <= y1 - 1; y++) PreparedGround.workedGround(ctx, x, y);
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                boolean west = x == x0, east = x == x1, south = y == y0, north = y == y1;
                if (!west && !east && !south && !north) continue;
                boolean entrance = switch (turns) {
                    case 0 -> south && x == mx;
                    case 1 -> west && y == my;
                    case 2 -> north && x == mx;
                    default -> east && y == my;
                };
                if (entrance) continue;
                int[] ns = north ? new int[]{0, 1} : south ? new int[]{0, -1} : null;
                int[] ew = east ? new int[]{1, 0} : west ? new int[]{-1, 0} : null;
                if (ns != null && ew != null) PreparedGround.sandbag(ctx, x, y, ns, ew);
                else PreparedGround.sandbag(ctx, x, y, ns != null ? ns : ew, null);
            }
        }
    }
}
