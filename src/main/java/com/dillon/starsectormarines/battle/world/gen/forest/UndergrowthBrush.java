package com.dillon.starsectormarines.battle.world.gen.forest;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * Generation-time undergrowth. Choosing where it grows is the caller's; this closes exactly the cells each
 * {@link Undergrowth} silhouette touches, all or nothing, on open natural ground.
 */
public final class UndergrowthBrush {
    private UndergrowthBrush() {}

    public record Placement(int x, int y, Undergrowth kind, int turns) {
        public int width() { return kind.width(turns); }
        public int height() { return kind.height(turns); }
        public boolean blocks(int x, int y) { return kind.blocks(x - this.x, y - this.y, turns); }
    }

    /** Whether every cell this placement would close is open natural ground nothing else has claimed. */
    public static boolean fits(Placement p, NavigationGrid grid, CellTopology topology, boolean[] reserved) {
        if (p.x() < 0 || p.y() < 0 || p.x() + p.width() > grid.getWidth() || p.y() + p.height() > grid.getHeight())
            return false;
        for (int y = p.y(); y < p.y() + p.height(); y++) for (int x = p.x(); x < p.x() + p.width(); x++)
            if (p.blocks(x, y) && !eligible(grid, topology, reserved, x, y)) return false;
        return true;
    }

    public static void stamp(List<Placement> placements, NavigationGrid grid, CellTopology topology,
                             List<Doodad> props, boolean[] reserved) {
        int w = grid.getWidth(), h = grid.getHeight();
        if (topology.getWidth() != w || topology.getHeight() != h || (reserved != null && reserved.length != w * h))
            throw new IllegalArgumentException("Undergrowth and map dimensions differ");
        if (placements.isEmpty()) return;
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) throw new IllegalStateException("Undergrowth tilesets have not been installed");
        List<Doodad> additions = new ArrayList<>();
        // 0 open, 1 closed by see-through growth only, 2 closed by dense growth.
        byte[] closed = new byte[w * h];
        for (Placement p : placements) {
            if (!fits(p, grid, topology, reserved))
                throw new IllegalArgumentException("Undergrowth overlaps protected ground at " + p.x() + "," + p.y());
            var def = registry.doodad(p.kind().id);
            if (def == null || def.footprintCellsX != p.kind().width || def.footprintCellsY != p.kind().height)
                throw new IllegalStateException("Missing or mismatched undergrowth " + p.kind().id);
            additions.add(new Doodad(p.x(), p.y(), def, p.turns()));
            byte level = (byte) (p.kind().seeThrough ? 1 : 2);
            for (int y = p.y(); y < p.y() + p.height(); y++) for (int x = p.x(); x < p.x() + p.width(); x++)
                if (p.blocks(x, y)) closed[y * w + x] = (byte) Math.max(closed[y * w + x], level);
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            byte level = closed[y * w + x];
            if (level == 0) continue;
            grid.setWalkable(x, y, false); grid.setSeeThrough(x, y, level == 1);
            grid.setEdgeCoverSuppressed(x, y, level == 1);
            grid.setTag(x, y, CellTag.NATURAL_SOLID, true); grid.setWallHp(x, y, 0);
            topology.setFixture(x, y, true); topology.setNatureOverlayIndex(x, y, -1);
        }
        props.addAll(additions);
    }

    private static boolean eligible(NavigationGrid grid, CellTopology topology, boolean[] reserved, int x, int y) {
        boolean natural = switch (topology.getGroundKind(x, y)) { case GRASS, DIRT, STONE, SAND, SNOW -> true; default -> false; };
        return natural && grid.isWalkable(x, y) && !grid.isDoorway(x, y) && !topology.isWall(x, y)
                && !topology.isFixture(x, y) && topology.getBuildingId(x, y) == 0 && topology.getSurface(x, y) == 0
                && (reserved == null || !reserved[y * grid.getWidth() + x])
                && (topology.roadSurface() == null || topology.roadSurface().underlay(x, y) == null);
    }
}
