package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** Authored working yards: equipment has real footprints; paint leaves circulation open. */
public final class StoryServiceYard {
    private final GenContext c;

    public StoryServiceYard(GenContext context) { c = context; }

    /** Distinct gravel equipment bed, with no authority to repaint a road or building. */
    public void gravel(int l, int b, int r, int t) {
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

    /**
     * A site perimeter traced over existing ground, as a closed axis-aligned polygon of
     * {@code x0, y0, x1, y1, ...} corners. Unlike {@link #fence}, it yields rather than refuses:
     * roads, buildings, scenery, thresholds and berths it meets become openings in the run.
     * A side with no opening of its own gets a five-cell gate at its middle, so a perimeter
     * never seals the ground it encloses.
     */
    public void perimeter(int... corners) { perimeter(new int[0], corners); }

    /** As {@link #perimeter(int...)}, with a five-cell gate centred on each {@code x, y} pair of {@code gates}. */
    public void perimeter(int[] gates, int... corners) {
        if (corners.length < 8 || corners.length % 2 != 0) throw new IllegalArgumentException("Perimeter needs four or more corners");
        int n = corners.length / 2;
        for (int i = 0; i < n; i++) {
            int ax = corners[2 * i], ay = corners[2 * i + 1];
            int bx = corners[2 * ((i + 1) % n)], by = corners[2 * ((i + 1) % n) + 1];
            if (ax != bx && ay != by) throw new IllegalArgumentException("Perimeter sides must be axis-aligned");
            int px = corners[2 * ((i + n - 1) % n)], py = corners[2 * ((i + n - 1) % n) + 1];
            int length = Math.abs(bx - ax) + Math.abs(by - ay), dx = Integer.signum(bx - ax), dy = Integer.signum(by - ay);
            boolean[] open = new boolean[length];
            boolean hasOpening = false;
            for (int s = 0; s < length; s++) {
                int x = ax + dx * s, y = ay + dy * s;
                open[s] = canFence(x, y) && !inGate(gates, x, y);
                hasOpening |= !open[s];
            }
            if (!hasOpening) for (int s = length / 2 - 2; s <= length / 2 + 2; s++) if (s > 0 && s < length) open[s] = false;
            for (int s = 0; s < length; s++) {
                if (!open[s]) continue;
                int x = ax + dx * s, y = ay + dy * s;
                prop(x, y, "industrial-fence-" + (s == 0 ? corner(px, py, ax, ay, bx, by) : dx != 0 ? "straight-h" : "straight-v"), 0);
            }
        }
    }

    /** The corner piece joining the side arriving from {@code p} with the side leaving toward {@code b}. */
    private static String corner(int px, int py, int x, int y, int bx, int by) {
        boolean north = py > y || by > y, east = px > x || bx > x;
        return "corner-" + (north ? "s" : "n") + (east ? "w" : "e");
    }

    private static boolean inGate(int[] gates, int x, int y) {
        for (int i = 0; i + 1 < gates.length; i += 2)
            if (Math.abs(gates[i] - x) <= 2 && Math.abs(gates[i + 1] - y) <= 2) return true;
        return false;
    }

    private boolean canFence(int x, int y) {
        if (!c.grid.inBounds(x, y) || !c.grid.isWalkable(x, y) || c.topology.isFixture(x, y)
                || c.topology.hasBuildingKindHint(x, y)
                || c.topology.getGroundKind(x, y) == GroundKind.STREET
                || c.topology.getGroundKind(x, y) == GroundKind.STRIPED) return false;
        for (int yy = y - 2; yy <= y + 2; yy++) for (int xx = x - 2; xx <= x + 2; xx++)
            if (c.grid.inBounds(xx, yy) && c.grid.isDoorway(xx, yy)) return false;
        for (LandingPad pad : c.landingPads)
            if (x >= pad.left() - 2 && x <= pad.right() + 2 && y >= pad.bottom() - 2 && y <= pad.top() + 2) return false;
        return true;
    }

    /** Four hazard corner marks define a working bay without carpeting it in warning paint. */
    public void bay(int l, int b, int r, int t) {
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            if ((x != l && x != r) || (y != b && y != t)) continue;
            requireOpen(x, y);
            c.doodads.add(new Doodad(x, y, TileRegistry.installed().doodad("doodad.fl-striped-yellow"),
                    y == t ? 0 : 1));
        }
    }

    public void cargo(int x, int y) {
        for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 3; dx++)
            prop(x + dx, y + dy, "industrial-crate-stack", 0);
        prop(x + 4, y, "industrial-pallet-stack", 0);
    }

    public void prop(int x, int y, String id, int turns) {
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

    /**
     * Loose goods on a room floor or beside a seeded building, filling around whatever its own fitting
     * already put there: a load whose footprint is taken, or that would crowd a threshold or {@code keepClearX, keepClearY}
     * (the room's anchor, which must stay reachable), is skipped rather than refused. Yard props are refused
     * inside a building; stored goods are allowed there. Returns whether the load was placed.
     */
    public boolean stock(int x, int y, String id, int turns, int keepClearX, int keepClearY) {
        var def = TileRegistry.installed().doodad("doodad." + id);
        if (def == null) throw new IllegalStateException("Missing service-yard asset: " + id);
        Doodad prop = new Doodad(x, y, def, turns);
        for (int yy = y; yy < y + prop.footprintCellsY; yy++)
            for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
                if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.topology.isFixture(xx, yy)
                        || Math.abs(xx - keepClearX) <= 1 && Math.abs(yy - keepClearY) <= 1) return false;
                for (int ty = yy - 2; ty <= yy + 2; ty++) for (int tx = xx - 2; tx <= xx + 2; tx++)
                    if (c.grid.inBounds(tx, ty) && c.grid.isDoorway(tx, ty)) return false;
            }
        for (int yy = y; yy < y + prop.footprintCellsY; yy++)
            for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
                c.grid.setWalkable(xx, yy, false);
                c.grid.setSeeThrough(xx, yy, true);
                c.topology.setFixture(xx, yy, true);
            }
        c.doodads.add(prop);
        return true;
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
