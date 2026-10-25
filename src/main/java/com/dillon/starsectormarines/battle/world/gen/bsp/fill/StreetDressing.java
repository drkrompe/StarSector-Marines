package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;

/**
 * The street dressing districts lay along their streets ({@code set-piece-districts.md}): lamps and shop awnings on a
 * main street, pipe gantries over an industrial service street, and the barriers of a cordoned facility's gatehouse.
 *
 * <p>Two kinds of piece. A <b>standing</b> piece (a lamp, a gantry column, a barrier, a bollard, a boom-gate post) is
 * a fixture: its cells are not walkable but stay see-through, and it is cover by its tileset's value. An
 * <b>overhead</b> piece (an awning, a pipe rack cell) and the boom-gate arm block nothing and carry no cover: a figure
 * walks under them. The overhead ones draw in the roof pass, above the people under them, through their motion
 * catalog ({@code street-dressing-motions.tileset.json}).
 *
 * <p>Directions are grid steps, {@code (dx, dy)} with exactly one of them non-zero. Draws nothing from any random
 * stream. A piece whose art is not installed is skipped.
 */
public final class StreetDressing {

    public static final String LAMP = "doodad.street-lamp-colonial";
    public static final String LAMP_INDUSTRIAL = "doodad.street-lamp-industrial";
    /** The shop awnings, by colourway. */
    public static final List<String> AWNINGS = List.of(
            "doodad.street-awning-striped", "doodad.street-awning-canvas", "doodad.street-awning-steel");
    /** Cells an awning spans along its wall. */
    public static final int AWNING_SPAN = 3;
    public static final String PIPE_BEAM = "doodad.pipe-rack-beam";
    public static final String PIPE_STRAIGHT = "doodad.pipe-rack-straight";
    public static final String PIPE_STRAIGHT_B = "doodad.pipe-rack-straight-b";
    public static final String GANTRY_COLUMN = "doodad.gantry-column";
    public static final String JERSEY_BARRIER = "doodad.jersey-barrier";
    public static final String BOLLARD = "doodad.security-bollard";
    public static final String BOOM_POST = "doodad.boom-gate-post";
    public static final String BOOM_ARM = "doodad.boom-gate-arm";
    /** Cells a boom-gate arm reaches past its post. */
    public static final int BOOM_REACH = 3;

    private StreetDressing() { }

    /**
     * The quarter turns that point a piece's north (the top of its frame) along {@code (dx, dy)}. A turn takes north
     * to west, so north is {@code +y} unturned.
     */
    static int turnsFacing(int dx, int dy) {
        if (dy > 0) return 0;
        if (dx < 0) return 1;
        if (dy < 0) return 2;
        return 3;
    }

    /** A street lamp standing on {@code (x, y)}, its arm reaching {@code (dx, dy)} over the carriageway. */
    public static void lamp(NavigationGrid grid, CellTopology topology, List<Doodad> doodads,
                            String id, int x, int y, int dx, int dy) {
        standing(grid, topology, doodads, id, x, y, dx, dy);
    }

    /**
     * A standing piece with its corner at {@code (minX, minY)}, its north turned to {@code (dx, dy)}; every cell of
     * its turned footprint becomes a fixture.
     */
    public static void standing(NavigationGrid grid, CellTopology topology, List<Doodad> doodads,
                                String id, int minX, int minY, int dx, int dy) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        if (def == null) return;
        Doodad piece = new Doodad(minX, minY, def, turnsFacing(dx, dy));
        for (int y = minY; y < minY + piece.footprintCellsY; y++) {
            for (int x = minX; x < minX + piece.footprintCellsX; x++) fixture(grid, topology, x, y);
        }
        doodads.add(piece);
    }

    /**
     * A shop awning over the pavement row in front of a wall, centred on {@code (x, y)} and spanning
     * {@link #AWNING_SPAN} cells along the wall, which lies one step {@code (wallDx, wallDy)} beyond it.
     */
    public static void awning(List<Doodad> doodads, String id, int x, int y, int wallDx, int wallDy) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        if (def == null) return;
        int half = AWNING_SPAN / 2;
        int minX = wallDy != 0 ? x - half : x, minY = wallDx != 0 ? y - half : y;
        doodads.add(new Doodad(minX, minY, def, turnsFacing(wallDx, wallDy)));
    }

    /**
     * An overhead pipe run from {@code (x0, y0)} to {@code (x1, y1)}, along one axis: a gantry column with its beam
     * at both ends and every {@code bay} cells between, straight rack over the rest. Across a street the run is one
     * gantry with a column on each kerb; along one it is a pipe rack on columns. The columns' cells stop being
     * walkable; nothing else on the run does.
     */
    public static void pipeRun(NavigationGrid grid, CellTopology topology, List<Doodad> doodads,
                               int x0, int y0, int x1, int y1, int bay) {
        if (x0 != x1 && y0 != y1) throw new IllegalArgumentException("a pipe run lies along one axis");
        TileRegistry registry = TileRegistry.installed();
        DoodadDef beam = registry.doodad(PIPE_BEAM), column = registry.doodad(GANTRY_COLUMN);
        DoodadDef[] straight = {registry.doodad(PIPE_STRAIGHT), registry.doodad(PIPE_STRAIGHT_B)};
        if (beam == null || column == null || straight[0] == null || straight[1] == null) return;
        int dx = Integer.signum(x1 - x0), dy = Integer.signum(y1 - y0);
        int length = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        // The pipes run east-west unturned.
        int turns = dx != 0 ? 0 : 1;
        for (int i = 0; i <= length; i++) {
            int x = x0 + dx * i, y = y0 + dy * i;
            boolean support = i == 0 || i == length || (bay > 0 && i % bay == 0 && length - i >= 2);
            if (support) {
                fixture(grid, topology, x, y);
                doodads.add(new Doodad(x, y, column));
                doodads.add(new Doodad(x, y, beam, turns));
            } else {
                doodads.add(new Doodad(x, y, straight[i % 2], turns));
            }
        }
    }

    /**
     * A boom gate: its post on {@code (x, y)} and the arm laid {@code (dx, dy)} across the
     * {@link #BOOM_REACH} cells beyond it, down across a gate's opening. The arm stops vehicles, not a figure.
     */
    public static void boomGate(NavigationGrid grid, CellTopology topology, List<Doodad> doodads,
                                int x, int y, int dx, int dy) {
        TileRegistry registry = TileRegistry.installed();
        DoodadDef post = registry.doodad(BOOM_POST), arm = registry.doodad(BOOM_ARM);
        if (post == null || arm == null) return;
        // The arm's root is the west of its frame and the post's pivot its east: east points along (dx, dy).
        int turns = turnsFacing(-dy, dx);
        fixture(grid, topology, x, y);
        doodads.add(new Doodad(x, y, post, turns));
        int ax = dx < 0 ? x - BOOM_REACH : dx > 0 ? x + 1 : x;
        int ay = dy < 0 ? y - BOOM_REACH : dy > 0 ? y + 1 : y;
        doodads.add(new Doodad(ax, ay, arm, turns));
    }

    /** A two-cell jersey barrier from {@code (x, y)}, lying along x when {@code alongX}, else along y. */
    public static void jerseyBarrier(NavigationGrid grid, CellTopology topology, List<Doodad> doodads,
                                     int x, int y, boolean alongX) {
        DoodadDef def = TileRegistry.installed().doodad(JERSEY_BARRIER);
        if (def == null) return;
        fixture(grid, topology, x, y);
        fixture(grid, topology, alongX ? x + 1 : x, alongX ? y : y + 1);
        doodads.add(new Doodad(x, y, def, alongX ? 0 : 1));
    }

    /** A security bollard on {@code (x, y)}. */
    public static void bollard(NavigationGrid grid, CellTopology topology, List<Doodad> doodads, int x, int y) {
        DoodadDef def = TileRegistry.installed().doodad(BOLLARD);
        if (def == null) return;
        fixture(grid, topology, x, y);
        doodads.add(new Doodad(x, y, def));
    }

    private static void fixture(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, true);
        topology.setFixture(x, y, true);
    }
}
