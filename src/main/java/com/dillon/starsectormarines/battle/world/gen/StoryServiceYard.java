package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** Authored working yards: equipment has real footprints; paint leaves circulation open. */
final class StoryServiceYard {
    private final GenContext c;

    StoryServiceYard(GenContext context) { c = context; }

    /** Distinct gravel equipment bed, with no authority to repaint a road or building. */
    void gravel(int l, int b, int r, int t) {
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) requireOpen(x, y);
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            c.topology.setGroundKind(x, y, GroundKind.STONE);
            c.topology.setSurface(x, y, c.topology.surfaceIndex("floors.stone"));
            c.markMadeGround(x, y);
        }
    }

    /** Four generous openings retain cross-yard access rather than making a new choke point. */
    void fence(int l, int b, int r, int t) {
        int mx = (l + r) / 2, my = (b + t) / 2;
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            if (x != l && x != r && y != b && y != t) continue;
            if ((y == b || y == t) && Math.abs(x - mx) <= 2) continue;
            if ((x == l || x == r) && Math.abs(y - my) <= 2) continue;
            String piece = x == l && y == b ? "corner-sw" : x == r && y == b ? "corner-se"
                    : x == l && y == t ? "corner-nw" : x == r && y == t ? "corner-ne"
                    : y == b || y == t ? "straight-h" : "straight-v";
            prop(x, y, "industrial-fence-" + piece, 0);
        }
    }

    /** Four hazard corner marks define a working bay without carpeting it in warning paint. */
    void bay(int l, int b, int r, int t) {
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            if ((x != l && x != r) || (y != b && y != t)) continue;
            requireOpen(x, y);
            c.doodads.add(new Doodad(x, y, TileRegistry.installed().doodad("doodad.fl-striped-yellow"),
                    y == t ? 0 : 1));
        }
    }

    void cargo(int x, int y) {
        for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 3; dx++)
            prop(x + dx, y + dy, "industrial-crate-stack", 0);
        prop(x + 4, y, "industrial-pallet-stack", 0);
    }

    void prop(int x, int y, String id, int turns) {
        var def = TileRegistry.installed().doodad("doodad." + id);
        if (def == null) throw new IllegalStateException("Missing service-yard asset: " + id);
        Doodad prop = new Doodad(x, y, def, turns);
        for (int yy = y; yy < y + prop.footprintCellsY; yy++)
            for (int xx = x; xx < x + prop.footprintCellsX; xx++) requireOpen(xx, yy);
        for (int yy = y; yy < y + prop.footprintCellsY; yy++)
            for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
                c.grid.setWalkable(xx, yy, false);
                c.grid.setSeeThrough(xx, yy, true);
                c.topology.setFixture(xx, yy, true);
            }
        c.doodads.add(prop);
    }

    private void requireOpen(int x, int y) {
        if (!c.grid.inBounds(x, y) || !c.grid.isWalkable(x, y)
                || c.topology.hasBuildingKindHint(x, y)
                || c.topology.getGroundKind(x, y) == GroundKind.STREET
                || c.topology.getGroundKind(x, y) == GroundKind.STRIPED)
            throw new IllegalStateException("Service yard overlaps reserved ground at " + x + "," + y);
        for (int yy = y - 2; yy <= y + 2; yy++) for (int xx = x - 2; xx <= x + 2; xx++)
            if (c.grid.inBounds(xx, yy) && c.grid.isDoorway(xx, yy))
                throw new IllegalStateException("Service yard obstructs a threshold at " + x + "," + y);
        for (LandingPad pad : c.landingPads)
            if (x >= pad.left() - 2 && x <= pad.right() + 2 && y >= pad.bottom() - 2 && y <= pad.top() + 2)
                throw new IllegalStateException("Service yard overlaps a berth");
    }
}
