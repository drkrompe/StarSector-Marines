package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.Random;

/**
 * An airbase as a lot: a runway, an apron with berths on it, hangars behind
 * them, and a fence round the whole thing.
 *
 * <p>Deliberately map-agnostic. It knows how to lay an airbase into a rectangle
 * and nothing about where that rectangle came from, so a fortress ward, a city
 * compound, or a future installation map can each reserve one and hand it over.
 * The only thing a host owes it is a rectangle of {@link #WIDTH} by
 * {@link #DEPTH} and the axis the attacker approaches along.
 *
 * <p><b>The lot is reserved before packing, not found afterwards.</b> An
 * airfield built out of whatever a packer left over gets whatever shape the
 * leftovers have, and leftovers are shallow: measured on a packed ward,
 * unclaimed yard runs eleven to forty-six cells wide at a depth of eight and
 * all but vanishes at ten. That is enough for a marked apron and nowhere near
 * enough for a facility. A lot is a claim.
 *
 * <p>Front to back, the layout is the order an aircraft moves through it:
 * runway along the approach edge, apron and berths behind it, hangars at the
 * back. The berths are on the apron rather than inside the sheds — a hangar is
 * where an aircraft is worked on, not where it waits to fly, and a berth inside
 * a building is a berth whose crew has to walk indoors to reach it.
 */
public final class AirbaseLot {

    /** Cells across the lot, fence to fence. */
    public static final int WIDTH = 44;
    /** Cells front to back, fence to fence. */
    public static final int DEPTH = 24;

    /** Ground the lot needs, for a host sizing an envelope that has to contain one. */
    public static int area() {
        return WIDTH * DEPTH;
    }

    /** Rows of runway along the approach edge. Wide enough to read as a strip rather than a path. */
    private static final int RUNWAY_DEPTH = 4;
    /** Rows between the runway and the apron, so the two read as separate surfaces. */
    private static final int RUNWAY_MARGIN = 1;
    /** A berth is five cells square, which is what {@link LandingPad} authors. */
    private static final int PAD = 5;
    /** Wingtip clearance between neighbouring berths. */
    private static final int PAD_GAP = 3;
    /** Berths on the apron. */
    private static final int PADS = 3;
    /** The strip between the berths and the hangar frontage. */
    private static final int TAXIWAY = 4;
    /** Hangar footprint, outer wall to outer wall. */
    private static final int HANGAR_WIDTH = 11;
    private static final int HANGAR_DEPTH = 8;
    /** Sheds on the lot. */
    private static final int HANGARS = 2;
    /** Cells of a hangar's frontage that stand open. Aircraft-sized, not a door. */
    private static final int HANGAR_OPENING = 5;
    /** Cells across a gate in the fence. */
    private static final int GATE_WIDTH = 3;

    /** Paving. */
    private static final GroundKind APRON = GroundKind.STONE;
    /** Runway and hardstand marking, the same hazard treatment a vehicle bay uses. */
    private static final GroundKind MARKED = GroundKind.STRIPED;

    private final int left;
    private final int bottom;
    private final int right;
    private final int top;
    private final boolean alongY;
    private final LandingPad.Approach approach;

    public AirbaseLot(int left, int bottom, int right, int top, TraversalAxis axis) {
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.top = top;
        this.alongY = axis == TraversalAxis.SOUTH_TO_NORTH;
        this.approach = alongY ? LandingPad.Approach.SOUTH : LandingPad.Approach.WEST;
    }

    /** Cells across the lot on the map's x axis, for the given approach. */
    public static int spanX(TraversalAxis axis) {
        return axis == TraversalAxis.SOUTH_TO_NORTH ? WIDTH : DEPTH;
    }

    /** Cells across the lot on the map's y axis, for the given approach. */
    public static int spanY(TraversalAxis axis) {
        return axis == TraversalAxis.SOUTH_TO_NORTH ? DEPTH : WIDTH;
    }

    /**
     * Lay the base into its rectangle: pave it, run the strip, mark the berths,
     * wall the sheds, work their interiors, and fence the lot.
     */
    public void author(GenContext ctx, Random rng) {
        pave(ctx);
        runway(ctx);
        berths(ctx);
        hangars(ctx, rng);
        groundSupport(ctx);
        fence(ctx);
    }

    /** Everything inside the fence is made surface, and claimed so nothing else takes it. */
    private void pave(GenContext ctx) {
        for (int x = left; x <= right; x++) {
            for (int y = bottom; y <= top; y++) {
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, APRON);
                ctx.topology.setRoomPurpose(x, y, RoomPurpose.HANGAR);
            }
        }
    }

    /**
     * The strip, along the approach edge and the full length of the lot.
     *
     * <p>At the front on purpose. It is the longest thing on the base and the
     * one piece that has to be unobstructed end to end, so it takes the edge
     * where nothing else wants to be, and everything that needs depth stacks
     * behind it.
     */
    private void runway(GenContext ctx) {
        int depth = depthStart();
        for (int step = 0; step < RUNWAY_DEPTH; step++) {
            for (int along = alongLo() + 1; along <= alongHi() - 1; along++) {
                int x = alongY ? along : depth + depthSign() * step;
                int y = alongY ? depth + depthSign() * step : along;
                ctx.topology.setGroundKind(x, y, MARKED);
            }
        }
    }

    /** Berths in a row on the apron, behind the runway and in front of the sheds. */
    private void berths(GenContext ctx) {
        int row = depthStart() + depthSign() * (RUNWAY_DEPTH + RUNWAY_MARGIN + PAD / 2 + 1);
        int span = PADS * PAD + (PADS - 1) * PAD_GAP;
        int start = (alongLo() + alongHi() - span) / 2 + PAD / 2;
        for (int i = 0; i < PADS; i++) {
            int along = start + i * (PAD + PAD_GAP);
            int cx = alongY ? along : row;
            int cy = alongY ? row : along;
            markBerth(ctx, cx, cy);
            ctx.landingPads.add(LandingPad.garrison(cx, cy, approach));
        }
    }

    /**
     * The vehicles that live on an apron, one in front of each berth.
     *
     * <p>Paint alone makes helipads. What tells a reader this is a working
     * airfield is everything standing around the aircraft that is not the
     * aircraft — the bowser it is fuelled from, the crew truck, the flatbed
     * with what is going aboard. None of it closes a cell: the apron has to
     * stay ground people cross under fire, which is the whole tactical point of
     * an airfield, and the berths stay bare because something has to land on
     * them.
     */
    private void groundSupport(GenContext ctx) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        int row = depthStart() + depthSign() * (RUNWAY_DEPTH + RUNWAY_MARGIN + PAD + 1);
        int span = PADS * PAD + (PADS - 1) * PAD_GAP;
        int start = (alongLo() + alongHi() - span) / 2 + PAD / 2;
        for (int i = 0; i < PADS; i++) {
            int along = start + i * (PAD + PAD_GAP);
            int x = alongY ? along - 1 : row;
            int y = alongY ? row : along - 1;
            DoodadDef kit = registry.doodad(GROUND_SUPPORT[i % GROUND_SUPPORT.length]);
            if (kit == null) continue;
            if (x < left + 1 || y < bottom + 1) continue;
            if (x + kit.footprintCellsX - 1 > right - 1) continue;
            if (y + kit.footprintCellsY - 1 > top - 1) continue;
            ctx.doodads.add(new Doodad(x, y, kit));
        }
    }

    /** What stands beside each berth, cycled so neighbouring stands differ. */
    private static final String[] GROUND_SUPPORT = {
            "doodad.parked-tanker-truck",
            "doodad.parked-utility-truck",
            "doodad.parked-flatbed-truck",
    };

    /** Paint one berth so a stand reads as a stand from across the lot. */
    private void markBerth(GenContext ctx, int centreX, int centreY) {
        for (int x = centreX - PAD / 2; x <= centreX + PAD / 2; x++) {
            for (int y = centreY - PAD / 2; y <= centreY + PAD / 2; y++) {
                if (x < left || x > right || y < bottom || y > top) continue;
                ctx.topology.setGroundKind(x, y, MARKED);
            }
        }
    }

    /**
     * The sheds at the back, walled, open-fronted, and worked.
     *
     * <p>Their frontage faces the apron, so an aircraft rolls out of a shed
     * straight onto the taxiway and out to a berth. The opening is five cells
     * because what goes through it has wings; its cells are ordinary floor with
     * their edges opened rather than doorway-flagged, since a doorway is a
     * one-cell routing node and a row of them would carve the mouth of the shed
     * into that many separate rooms.
     */
    private void hangars(GenContext ctx, Random rng) {
        // The stack behind the runway, exactly: strip, margin, berths, taxiway.
        // One row more and the sheds' back wall lands on the fence row, where
        // the fence overwrites it — two hangars with three walls each, which
        // looks almost right and is open at the back.
        int frontDepth = depthStart()
                + depthSign() * (RUNWAY_DEPTH + RUNWAY_MARGIN + PAD + TAXIWAY);
        int span = HANGARS * HANGAR_WIDTH + (HANGARS - 1) * (HANGAR_WIDTH / 2);
        int start = (alongLo() + alongHi() - span) / 2;
        for (int i = 0; i < HANGARS; i++) {
            int alongLo = start + i * (HANGAR_WIDTH + HANGAR_WIDTH / 2);
            stampHangar(ctx, rng, frontDepth, alongLo, alongLo + HANGAR_WIDTH - 1);
        }
    }

    private void stampHangar(GenContext ctx, Random rng,
                             int frontDepth, int alongLo, int alongHi) {
        int backDepth = frontDepth + depthSign() * (HANGAR_DEPTH - 1);
        int hLeft = alongY ? alongLo : Math.min(frontDepth, backDepth);
        int hRight = alongY ? alongHi : Math.max(frontDepth, backDepth);
        int hBottom = alongY ? Math.min(frontDepth, backDepth) : alongLo;
        int hTop = alongY ? Math.max(frontDepth, backDepth) : alongHi;
        int openCentre = (alongLo + alongHi) / 2;

        for (int x = hLeft; x <= hRight; x++) {
            for (int y = hBottom; y <= hTop; y++) {
                boolean ring = x == hLeft || x == hRight || y == hBottom || y == hTop;
                if (!ring) continue;
                boolean onFront = alongY ? y == frontDepth : x == frontDepth;
                int along = alongY ? x : y;
                if (onFront && Math.abs(along - openCentre) <= HANGAR_OPENING / 2) {
                    ctx.grid.setWalkableFloor(x, y);
                    ctx.grid.openAllEdges(x, y);
                    ctx.topology.setGroundKind(x, y, MARKED);
                    continue;
                }
                ctx.grid.setWalkable(x, y, false);
                int mask = 0;
                if (y + 1 > hTop) mask |= CellTopology.WALL_DIR_N;
                if (y - 1 < hBottom) mask |= CellTopology.WALL_DIR_S;
                if (x + 1 > hRight) mask |= CellTopology.WALL_DIR_E;
                if (x - 1 < hLeft) mask |= CellTopology.WALL_DIR_W;
                ctx.topology.setWall(x, y, true);
                ctx.topology.orWallDirMask(x, y, mask);
                ctx.topology.setGroundKind(x, y, APRON);
            }
        }
        workInterior(ctx, rng, hLeft + 1, hBottom + 1, hRight - 1, hTop - 1);
    }

    /**
     * Dress a shed's interior as somewhere people work.
     *
     * <p>An empty shed is a box. What makes it read as a maintenance hangar is
     * the tooling in it, and what makes that tooling worth generating is that
     * the people who work here are units on the map: a bay with a bench and a
     * parts stack against the wall gives them somewhere to be, and gives
     * whoever fights through it something to fight around.
     *
     * <p>The middle stays clear. A hangar's floor is where the aircraft goes,
     * so the kit lines the walls, which is both how a real one is arranged and
     * what keeps the shed crossable.
     */
    private void workInterior(GenContext ctx, Random rng,
                              int inLeft, int inBottom, int inRight, int inTop) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        int slot = 0;
        for (int x = inLeft; x <= inRight; x++) {
            for (int y = inBottom; y <= inTop; y++) {
                boolean againstWall = x == inLeft || x == inRight
                        || y == inBottom || y == inTop;
                if (!againstWall) continue;
                if (rng.nextFloat() > WORKSHOP_DENSITY) continue;
                DoodadDef kit = registry.doodad(WORKSHOP_KIT[slot++ % WORKSHOP_KIT.length]);
                if (kit == null) continue;
                if (x + kit.footprintCellsX - 1 > inRight) continue;
                if (y + kit.footprintCellsY - 1 > inTop) continue;
                ctx.doodads.add(new Doodad(x, y, kit));
            }
        }
    }

    /** Share of wall-adjacent interior cells that carry something. Enough to read as worked, not packed. */
    private static final float WORKSHOP_DENSITY = 0.35f;

    /**
     * What an aircraft shed has in it. Cycled rather than rolled so one shed
     * never comes out as six of the same crate.
     */
    private static final String[] WORKSHOP_KIT = {
            "doodad.industrial-crate-stack",
            "doodad.industrial-cable-reel",
            "doodad.industrial-pallet-stack",
            "doodad.industrial-drum-cluster",
            "doodad.industrial-fluid-tank",
            "doodad.industrial-generator",
    };

    /**
     * The perimeter, with a gate front and back.
     *
     * <p>Two gates is a safety property rather than a flourish: a ring with one
     * badly-placed gate walks the ground crew the long way round their own base
     * or seals the lot off from the map entirely.
     */
    private void fence(GenContext ctx) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        int gateCentre = (alongLo() + alongHi()) / 2;
        for (int x = left; x <= right; x++) {
            for (int y = bottom; y <= top; y++) {
                boolean ring = x == left || x == right || y == bottom || y == top;
                if (!ring) continue;
                boolean gateSide = alongY ? (y == bottom || y == top)
                        : (x == left || x == right);
                int along = alongY ? x : y;
                if (gateSide && Math.abs(along - gateCentre) <= GATE_WIDTH / 2) {
                    ctx.grid.setWalkableFloor(x, y);
                    ctx.grid.openAllEdges(x, y);
                    ctx.topology.setGroundKind(x, y, MARKED);
                    continue;
                }
                DoodadDef post = registry.doodad(fenceId(x, y));
                if (post == null) continue;
                ctx.grid.setWalkable(x, y, false);
                ctx.grid.setSeeThrough(x, y, true);
                ctx.topology.setWall(x, y, false);
                ctx.topology.setFixture(x, y, true);
                ctx.doodads.add(new Doodad(x, y, post));
            }
        }
    }

    private String fenceId(int x, int y) {
        if (x == left && y == top) return "doodad.industrial-fence-corner-nw";
        if (x == right && y == top) return "doodad.industrial-fence-corner-ne";
        if (x == left && y == bottom) return "doodad.industrial-fence-corner-sw";
        if (x == right && y == bottom) return "doodad.industrial-fence-corner-se";
        return y == top || y == bottom
                ? "doodad.industrial-fence-straight-h"
                : "doodad.industrial-fence-straight-v";
    }

    /** The lot's front edge on the depth axis — the side the attacker approaches from. */
    private int depthStart() {
        return alongY ? bottom + 1 : left + 1;
    }

    /** Which way "deeper into the lot" runs on the depth axis. */
    private int depthSign() {
        return 1;
    }

    /** Low end of the lot's long axis. */
    private int alongLo() {
        return alongY ? left : bottom;
    }

    /** High end of the lot's long axis. */
    private int alongHi() {
        return alongY ? right : top;
    }
}
