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

    /**
     * Cells of clear ground kept outside the fence, all the way round.
     *
     * <p>A fence on the boundary of its own reservation is a fence somebody can
     * be packed flush against, and the gap between it and the next wall is then
     * whatever the packing happened to leave — including nothing. A lot that
     * blocks the way past it has made the map worse in exchange for reading
     * well, so the clearance is reserved with the lot rather than hoped for.
     */
    public static final int CLEARANCE = 2;

    /** Ground the lot and its clearance need, for a host sizing an envelope that has to contain one. */
    public static int area() {
        return (WIDTH + CLEARANCE * 2) * (DEPTH + CLEARANCE * 2);
    }

    /** Cells across the reservation a host must set aside, clearance included. */
    public static int reservedSpanX(TraversalAxis axis) {
        return spanX(axis) + CLEARANCE * 2;
    }

    /** Cells down the reservation a host must set aside, clearance included. */
    public static int reservedSpanY(TraversalAxis axis) {
        return spanY(axis) + CLEARANCE * 2;
    }

    /** Rows of runway along the approach edge. Wide enough to read as a strip rather than a path. */
    private static final int RUNWAY_DEPTH = 4;
    /**
     * The control tower's bay at one end of the strip.
     *
     * <p>Beside the runway rather than behind the sheds, because what a tower
     * is for is seeing the strip. It is the one building on the lot that faces
     * outward, and putting it at the end of the runway is what makes the strip
     * read as something being run rather than a painted rectangle.
     */
    private static final int TOWER_WIDTH = 7;
    private static final int TOWER_DEPTH = 5;
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

    /**
     * The four surfaces a base is made of, and they are four because a reader
     * has to be able to tell them apart at map zoom.
     *
     * <p>The apron is asphalt — a made outdoor surface, and light enough to
     * read as one. It was the stone blob, which a re-export of the floor sheet
     * turned dark navy: a whole lot the same colour as a courtyard, with the
     * markings on it invisible. The runway is deliberately a <em>different</em>
     * tarmac from the apron rather than the same surface with paint on it,
     * because a strip is a different piece of civil engineering from the ground
     * beside it and should look like one.
     *
     * <p>Buildings get an indoor floor. A hangar floored in the same tarmac as
     * the apron outside it reads as a roofed bit of apron; what makes a shed a
     * building from above is that the surface changes at its wall.
     *
     * <p>And the ground outside the fence is paved as a verge — the city's own
     * sidewalk, because that is what it is. The clearance is reserved so people
     * can walk round the base, and reserved dirt in the middle of a made
     * facility reads as ground the lot forgot rather than as the way past it.
     * It is deliberately not the polished tile the civic and commercial
     * interiors use: a surface that says "indoors" everywhere else on the map
     * does not stop saying it out here.
     */
    private static final GroundKind APRON = GroundKind.STREET;
    private static final GroundKind RUNWAY = GroundKind.COURTYARD;
    private static final GroundKind INSIDE = GroundKind.INDOOR;
    private static final GroundKind VERGE = GroundKind.SIDEWALK;

    /** Hazard marking, the same treatment a vehicle bay's berth is edged with. */
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
        tower(ctx);
        fence(ctx);
    }

    /**
     * Everything inside the fence is made surface, and claimed so nothing else
     * takes it; everything in the clearance outside it is paved as a verge.
     *
     * <p>The verge is not decoration. It is the ground the lot reserved so
     * people can get past the base, and leaving it as raw dirt in the middle of
     * a made facility reads as ground nobody thought about rather than as the
     * way round.
     */
    private void pave(GenContext ctx) {
        for (int x = left - CLEARANCE; x <= right + CLEARANCE; x++) {
            for (int y = bottom - CLEARANCE; y <= top + CLEARANCE; y++) {
                if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) continue;
                boolean insideFence = x >= left && x <= right && y >= bottom && y <= top;
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, insideFence ? APRON : VERGE);
                if (insideFence) ctx.topology.setRoomPurpose(x, y, RoomPurpose.HANGAR);
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
        TileRegistry registry = TileRegistry.installed();
        int depth = depthStart();
        for (int step = 0; step < RUNWAY_DEPTH; step++) {
            for (int along = alongLo() + 1; along <= alongHi() - 1; along++) {
                int x = alongY ? along : depth + depthSign() * step;
                int y = alongY ? depth + depthSign() * step : along;
                ctx.topology.setGroundKind(x, y, RUNWAY);
                // Edge lines, laid as floor rather than as a ground kind. A
                // marking that is a kind of ground is only visible while it
                // contrasts with the ground beside it, and the ground palette
                // is not this feature's to hold still — a re-export of the
                // floor sheet turned a marked strip and the apron round it into
                // the same colour without touching a line of this. Paint is a
                // thing laid on a surface; it belongs on top of one.
                if (registry == null) continue;
                if (step != 0 && step != RUNWAY_DEPTH - 1) continue;
                DoodadDef line = registry.doodad(BAY_EDGE);
                if (line != null) ctx.doodads.add(new Doodad(x, y, line));
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
        TileRegistry registry = TileRegistry.installed();
        for (int x = centreX - PAD / 2; x <= centreX + PAD / 2; x++) {
            for (int y = centreY - PAD / 2; y <= centreY + PAD / 2; y++) {
                if (x < left || x > right || y < bottom || y > top) continue;
                ctx.topology.setGroundKind(x, y, MARKED);
                // Painted edge, clear middle — the same treatment a machine bay
                // gets, for the same reason: the middle is where the thing
                // stands, and a filled rectangle would be drawn over by it.
                if (registry == null) continue;
                boolean edge = Math.abs(x - centreX) == PAD / 2
                        || Math.abs(y - centreY) == PAD / 2;
                if (!edge) continue;
                DoodadDef paint = registry.doodad(BAY_EDGE);
                if (paint != null) ctx.doodads.add(new Doodad(x, y, paint));
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
                ctx.topology.setGroundKind(x, y, INSIDE);
            }
        }
        for (int x = hLeft + 1; x <= hRight - 1; x++) {
            for (int y = hBottom + 1; y <= hTop - 1; y++) {
                ctx.topology.setGroundKind(x, y, INSIDE);
            }
        }
        workInterior(ctx, rng, hLeft + 1, hBottom + 1, hRight - 1, hTop - 1, frontDepth);
    }

    /**
     * Work a shed's interior around the thing it is for: a marked bay with a
     * shuttle-sized clear middle, a station at its head, and stores behind.
     *
     * <p>Scattering kit along the walls read as a shed somebody had left things
     * in. What a maintenance hangar actually has is a <em>berth</em> — a
     * marked-out rectangle of floor a machine stands in, framed, with the work
     * arranged around it. That is exactly the shape a mech bay already uses on
     * a ship's deck, so this borrows its treatment rather than inventing a
     * second visual language for the same idea: a striped edge round a grated
     * field, which reads as serviceable floor rather than as ground.
     *
     * <p>The middle stays empty on purpose and the arrangement is what makes it
     * legible. The bay is where the aircraft goes; the station at its head is
     * where the work on that aircraft is run from; the stores are behind the
     * station against the back wall, out of the way of both. The flanks are
     * left clear, which is where a technician stands — so when workers arrive
     * they have somewhere to be that is beside the aircraft rather than on top
     * of it.
     */
    private void workInterior(GenContext ctx, Random rng,
                              int inLeft, int inBottom, int inRight, int inTop,
                              int frontDepth) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;

        // The bay: shuttle-sized, pushed to the mouth so the aircraft stands
        // where it can roll straight out, with the head of the bay at the back.
        boolean headAtHigh = alongY ? frontDepth < inBottom : frontDepth < inLeft;
        int bayLeft = alongY ? inLeft + (inRight - inLeft + 1 - PAD) / 2 : inLeft;
        int bayRight = alongY ? bayLeft + PAD - 1 : inRight;
        int bayBottom = alongY ? inBottom : inBottom + (inTop - inBottom + 1 - PAD) / 2;
        int bayTop = alongY ? inTop : bayBottom + PAD - 1;
        if (alongY) {
            if (headAtHigh) bayTop = inTop - 1; else bayBottom = inBottom + 1;
            if (headAtHigh) bayBottom = bayTop - PAD + 1; else bayTop = bayBottom + PAD - 1;
        } else {
            if (headAtHigh) bayRight = inRight - 1; else bayLeft = inLeft + 1;
            if (headAtHigh) bayLeft = bayRight - PAD + 1; else bayRight = bayLeft + PAD - 1;
        }
        paveBay(ctx, registry, bayLeft, bayBottom, bayRight, bayTop);

        // The station at the head of the bay, against the back wall: a tool to
        // make a part at and the terminal its condition is read off.
        int headAlongLo = alongY ? bayLeft : bayBottom;
        for (int i = 0; i < BAY_STATION.length; i++) {
            int along = headAlongLo + 1 + i;
            int across = alongY
                    ? (headAtHigh ? inTop : inBottom)
                    : (headAtHigh ? inRight : inLeft);
            int x = alongY ? along : across;
            int y = alongY ? across : along;
            place(ctx, registry, BAY_STATION[i], x, y, inLeft, inBottom, inRight, inTop);
        }

        // Stores in the corners the bay does not reach, which is where the
        // stock in a shed of this shape actually ends up.
        int slot = 0;
        for (int x = inLeft; x <= inRight; x++) {
            for (int y = inBottom; y <= inTop; y++) {
                if (x >= bayLeft - 1 && x <= bayRight + 1
                        && y >= bayBottom - 1 && y <= bayTop + 1) continue;
                boolean againstWall = x == inLeft || x == inRight
                        || y == inBottom || y == inTop;
                if (!againstWall) continue;
                if (rng.nextFloat() > WORKSHOP_DENSITY) continue;
                place(ctx, registry, WORKSHOP_KIT[slot++ % WORKSHOP_KIT.length],
                        x, y, inLeft, inBottom, inRight, inTop);
            }
        }
    }

    /**
     * Paint a berth: a striped edge round a grated field.
     *
     * <p>Floor doodads rather than a ground kind, which is how a ship's deck
     * marks its machine bays. A hangar floor is a made surface with markings on
     * it, and the ground palette has no word for that.
     */
    private void paveBay(GenContext ctx, TileRegistry registry,
                         int bLeft, int bBottom, int bRight, int bTop) {
        for (int x = bLeft; x <= bRight; x++) {
            for (int y = bBottom; y <= bTop; y++) {
                boolean edge = x == bLeft || x == bRight || y == bBottom || y == bTop;
                String id = edge ? BAY_EDGE : BAY_FIELD[(x + y) & 1];
                DoodadDef tile = registry.doodad(id);
                if (tile != null) ctx.doodads.add(new Doodad(x, y, tile));
            }
        }
    }

    /** Place one piece if it is known and fits inside the shed. */
    private void place(GenContext ctx, TileRegistry registry, String id,
                       int x, int y, int inLeft, int inBottom, int inRight, int inTop) {
        DoodadDef def = registry.doodad(id);
        if (def == null) return;
        if (x < inLeft || y < inBottom) return;
        if (x + def.footprintCellsX - 1 > inRight) return;
        if (y + def.footprintCellsY - 1 > inTop) return;
        ctx.doodads.add(new Doodad(x, y, def));
    }

    /** Marked edge of a berth — the same striped deck a machine bay is edged with. */
    private static final String BAY_EDGE = "doodad.fl-striped-yellow";
    /** The berth's field, checkered so it reads as plate rather than a painted block. */
    private static final String[] BAY_FIELD = { "doodad.fl-grate-1", "doodad.fl-grate-2" };

    /** The station at the head of a bay, where the work on that aircraft is run from. */
    private static final String[] BAY_STATION = {
            "doodad.industrial-machine-tool",
            "doodad.industrial-control-console",
    };

    /** Share of the corners a shed's stock fills. Enough to read as worked, not packed. */
    private static final float WORKSHOP_DENSITY = 0.5f;

    /**
     * What a shed keeps behind the station. Cycled rather than rolled so one
     * hangar never comes out as six of the same crate.
     */
    private static final String[] WORKSHOP_KIT = {
            "doodad.industrial-crate-stack",
            "doodad.industrial-cable-reel",
            "doodad.industrial-pallet-stack",
            "doodad.industrial-drum-cluster",
    };

    /**
     * The control tower, at the end of the strip.
     *
     * <p>The one building on the lot that faces outward. Everything else here is
     * arranged around an aircraft; this is arranged around the runway, which is
     * why it sits beside the strip rather than behind the sheds, and why the
     * runway is laid short of it rather than through it.
     *
     * <p>Walled like the sheds, with its door onto the apron. A tower whose only
     * way in was from the runway would have its crew crossing the strip to get
     * to work.
     */
    private void tower(GenContext ctx) {
        TileRegistry registry = TileRegistry.installed();
        // Behind the strip, not on the end of it. A tower closing off a runway
        // is a building in the one place nothing should be — and it is the
        // first thing an aircraft would meet. Set back into the apron band at
        // the lot's end, it overlooks the whole strip without standing on any
        // part of it, and the runway runs the full length of the lot again.
        int tAlongLo = alongLo() + 1;
        int tAlongHi = tAlongLo + TOWER_WIDTH - 1;
        int tFront = depthStart() + depthSign() * (RUNWAY_DEPTH + RUNWAY_MARGIN);
        int tBack = tFront + depthSign() * (TOWER_DEPTH - 1);
        int tLeft = alongY ? tAlongLo : Math.min(tFront, tBack);
        int tRight = alongY ? tAlongHi : Math.max(tFront, tBack);
        int tBottom = alongY ? Math.min(tFront, tBack) : tAlongLo;
        int tTop = alongY ? Math.max(tFront, tBack) : tAlongHi;
        int doorCentre = (tAlongLo + tAlongHi) / 2;

        for (int x = tLeft; x <= tRight; x++) {
            for (int y = tBottom; y <= tTop; y++) {
                boolean ring = x == tLeft || x == tRight || y == tBottom || y == tTop;
                if (!ring) continue;
                boolean onBack = alongY ? y == tBack : x == tBack;
                int along = alongY ? x : y;
                if (onBack && along == doorCentre) {
                    ctx.grid.setWalkableFloor(x, y);
                    ctx.grid.setDoorway(x, y, true);
                    ctx.grid.openAllEdges(x, y);
                    ctx.topology.setGroundKind(x, y, MARKED);
                    continue;
                }
                ctx.grid.setWalkable(x, y, false);
                int mask = 0;
                if (y + 1 > tTop) mask |= CellTopology.WALL_DIR_N;
                if (y - 1 < tBottom) mask |= CellTopology.WALL_DIR_S;
                if (x + 1 > tRight) mask |= CellTopology.WALL_DIR_E;
                if (x - 1 < tLeft) mask |= CellTopology.WALL_DIR_W;
                ctx.topology.setWall(x, y, true);
                ctx.topology.orWallDirMask(x, y, mask);
                ctx.topology.setGroundKind(x, y, INSIDE);
            }
        }
        for (int x = tLeft + 1; x <= tRight - 1; x++) {
            for (int y = tBottom + 1; y <= tTop - 1; y++) {
                ctx.topology.setGroundKind(x, y, INSIDE);
            }
        }
        if (registry == null) return;
        // What a tower is: somewhere to watch from and somewhere to talk from.
        place(ctx, registry, "doodad.industrial-control-console",
                tLeft + 1, tBottom + 1, tLeft + 1, tBottom + 1, tRight - 1, tTop - 1);
        place(ctx, registry, "doodad.military-radar-dish",
                tRight - 1, tTop - 1, tLeft + 1, tBottom + 1, tRight - 1, tTop - 1);
    }

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
        int gateAlong = (alongLo() + alongHi()) / 2;
        int gateAcross = alongY ? (bottom + top) / 2 : (left + right) / 2;
        for (int x = left; x <= right; x++) {
            for (int y = bottom; y <= top; y++) {
                boolean ring = x == left || x == right || y == bottom || y == top;
                if (!ring) continue;
                // A gate on every side. Front and back are how the base is used;
                // the two ends are how everybody else gets past it. A lot with
                // gates on one axis only is a wall across the map for anything
                // trying to move along the other.
                boolean onLongSide = alongY ? (y == bottom || y == top)
                        : (x == left || x == right);
                int along = alongY ? x : y;
                int across = alongY ? y : x;
                boolean gate = onLongSide
                        ? Math.abs(along - gateAlong) <= GATE_WIDTH / 2
                        : Math.abs(across - gateAcross) <= GATE_WIDTH / 2;
                if (gate) {
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
