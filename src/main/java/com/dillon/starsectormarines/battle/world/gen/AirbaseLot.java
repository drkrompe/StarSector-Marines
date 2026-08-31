package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
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

    /**
     * How much airbase this is.
     *
     * <p>The same laws at two scales rather than two designs. Everything that
     * makes a base legible — berths on the apron and never inside a shed, a
     * marked bay in each shed with the work arranged round it, a fence with a
     * way in on every side, clear ground reserved outside it — holds at both,
     * because those are properties of an airbase and not of a big one. What
     * changes is how much of it there is.
     *
     * <p>{@link #STATION} is a base that flies its own aircraft rather than
     * hosting other people's: a longer strip and a third shed. What separates
     * it from {@link #FIELD} is not the extra ground but what the sheds are for
     * — a station keeps aircraft in them and taxis them out, so its shed count
     * is its aircraft count and the apron berths beside them are still a
     * visitor's parking.
     *
     * <p>It grows along the frontage and <b>not</b> backwards, which is a
     * measurement rather than a preference: a fortress ward comes out about
     * 250 cells wide and 29 deep, so depth is the scarce axis and the only one
     * a bigger base can actually be refused for. A station a few rows deeper
     * than a field never fits anywhere; one fourteen cells wider fits with room
     * to spare — and length is what a strip wanted in the first place.
     *
     * <p>{@link #FIELD} is an installation: a runway, three berths, two sheds,
     * a control tower and a vehicle park. It is what a fortress ward builds
     * when it has the ground for it.
     *
     * <p>{@link #PAD} is a landing site: two berths, one shed, and no strip at
     * all. A quarter of the ground, and it fits where the large one cannot —
     * a compound's yard, a claim across a few city blocks, a map that is not a
     * fortress. It is the shape a player's own arrival wants.
     *
     * <p>{@link #STRIP} is one berth and its shed, sized to drop straight into
     * a single city block. Measured on a Conquest map the largest blocks run to
     * about eighteen by sixteen, so this is what an airbase looks like when it
     * has to be a block rather than claim several — and a block is already
     * bounded by streets, which is why it is the one size that reserves no
     * clearance of its own.
     *
     * <p>A site with no runway is not a diminished airfield; it is the thing
     * most airbases actually are. Aircraft that land vertically need somewhere
     * to stand and somewhere to be worked on, and a strip is what you add when
     * something has to roll.
     */
    public enum Size {
        STATION(58, 24, 4, true, 3, 3, 11, 8, 5, 4, 3, 2, 2),
        FIELD(44, 24, 4, true, 3, 2, 11, 8, 5, 4, 3, 2, 2),
        PAD(22, 19, 0, false, 2, 1, 11, 8, 5, 3, 2, 1, 2),
        STRIP(14, 16, 0, false, 1, 1, 9, 7, 5, 2, 1, 1, 0);

        /** Cells across the lot, fence to fence. */
        public final int width;
        /** Cells front to back, fence to fence. */
        public final int depth;
        /** Rows of runway along the approach edge, or zero for a site with no strip. */
        final int runwayDepth;
        /** Whether the lot runs its own air traffic. */
        final boolean tower;
        /** Berths on the apron. */
        final int pads;
        /** Sheds, and their footprint outer wall to outer wall. */
        final int hangars;
        final int hangarWidth;
        final int hangarDepth;
        /** Cells of a shed's frontage that stand open. Aircraft-sized, not a door. */
        final int hangarOpening;
        /** The strip between the berths and the shed frontage. */
        final int taxiway;
        /** Ranks and files of parked vehicles. */
        final int parkRanks;
        final int parkFiles;
        /**
         * Cells of clear ground kept outside the fence.
         *
         * <p>Zero for a site that fills a city block, because a block is
         * already bounded by streets and the way past it exists whether the lot
         * reserves it or not. A lot carved out of a larger reservation has no
         * such guarantee and buys its own.
         */
        final int clearance;

        Size(int width, int depth, int runwayDepth, boolean tower,
             int pads, int hangars, int hangarWidth, int hangarDepth,
             int hangarOpening, int taxiway, int parkRanks, int parkFiles,
             int clearance) {
            this.width = width;
            this.depth = depth;
            this.runwayDepth = runwayDepth;
            this.tower = tower;
            this.pads = pads;
            this.hangars = hangars;
            this.hangarWidth = hangarWidth;
            this.hangarDepth = hangarDepth;
            this.hangarOpening = hangarOpening;
            this.taxiway = taxiway;
            this.parkRanks = parkRanks;
            this.parkFiles = parkFiles;
            this.clearance = clearance;
        }

        /** Cells of clear ground this size keeps outside its fence. */
        public int clearance() { return clearance; }

        /** Berths this size carries. */
        public int pads() { return pads; }

        /** Sheds this size carries, which is also the number of aircraft it shelters. */
        public int hangars() { return hangars; }
    }

    /**
     * Which edge of the lot the approach is on — and therefore which way the
     * whole base is turned.
     *
     * <p>Four, not two. The lot used to take a traversal axis, which gave it
     * the two orientations a fortress ward needs: a base at the bottom of the
     * map facing the attacker, or one at the left facing the same way. Anywhere
     * else on a map, the direction that matters is the one the site actually
     * fronts onto — the road it opens off, the edge it was built along — and
     * that can be any of four.
     *
     * <p>Rotation is safe here because every piece of the lot is already placed
     * in the lot's own frame: <em>along</em> the frontage and <em>into</em> the
     * depth, never in map x and y. Turning the base is therefore a change to
     * two accessors — where the depth starts and which way it runs — and the
     * runway, berths, sheds, park and tower all follow without knowing.
     */
    public enum Facing {
        /** Approach from low y; the strip lies along the lot's south edge. */
        SOUTH(true, 1, LandingPad.Approach.SOUTH, 0, false),
        /** Approach from high y — the half turn, taken as a mirror. */
        NORTH(true, -1, LandingPad.Approach.NORTH, 0, true),
        /** Approach from low x. */
        WEST(false, 1, LandingPad.Approach.WEST, 1, false),
        /** Approach from high x. */
        EAST(false, -1, LandingPad.Approach.EAST, 1, true);

        /** Whether the lot's depth runs along the map's y axis. */
        final boolean alongY;
        /** Which way "deeper into the lot" runs on that axis. */
        final int sign;
        final LandingPad.Approach approach;
        /**
         * Quarter turns anything with a front takes when the lot is turned.
         *
         * <p>The paving, the walls and the markings are all symmetric enough
         * not to care, but a truck is drawn facing somewhere. Six of them
         * pointing the same way on a lot that has been turned is the one thing
         * that gives a rotation away.
         *
         * <p><b>A quarter turn is a rotation; a half turn must be a mirror.</b>
         * The art is drawn lit from one direction, so turning a vehicle through
         * a hundred and eighty degrees lights it from underneath and it reads as
         * upside down. The two facings opposite the baselines therefore keep
         * their neighbour's rotation and mirror it instead.
         */
        final int quarterTurns;
        final boolean mirrored;

        Facing(boolean alongY, int sign, LandingPad.Approach approach,
               int quarterTurns, boolean mirrored) {
            this.alongY = alongY;
            this.sign = sign;
            this.approach = approach;
            this.quarterTurns = quarterTurns;
            this.mirrored = mirrored;
        }

        /** Whether the lot's depth runs along the map's y axis. */
        public boolean alongY() { return alongY; }

        /** Which way "deeper into the lot" runs on that axis. */
        public int sign() { return sign; }

        /** Quarter turns a directional prop takes at this facing. */
        public int quarterTurns() { return quarterTurns; }

        /** Whether a directional prop is mirrored rather than turned further. */
        public boolean mirrored() { return mirrored; }

        /**
         * The facing a mission's traversal axis implies: the base fronts onto
         * the side the attacker arrives from, so an aircraft comes in over the
         * ward rather than over the wall behind it.
         */
        public static Facing of(TraversalAxis axis) {
            return axis == TraversalAxis.SOUTH_TO_NORTH ? SOUTH : WEST;
        }
    }

    /**
     * A berth is five cells square, which is what {@link LandingPad} authors.
     * It does not scale with the lot: an aircraft is the size it is, and a
     * compact base is one with fewer berths rather than smaller ones.
     */
    private static final int PAD = 5;
    /** Wingtip clearance between neighbouring berths. */
    private static final int PAD_GAP = 3;
    /**
     * Rows between the runway and the apron, so the two read as separate
     * surfaces — and nothing at all on a site with no runway.
     *
     * <p>Charged unconditionally it is a row of apron that exists to separate
     * the strip from something, on a lot that has no strip. On the compact
     * sizes that row is the difference between a shed with room in front of it
     * and a shed with none.
     */
    private int runwayMargin() {
        return size.runwayDepth > 0 ? 1 : 0;
    }
    /** The control tower's footprint, on a lot that runs its own traffic. */
    private static final int TOWER_WIDTH = 7;
    private static final int TOWER_DEPTH = 5;
    /** Cells across a gate. Three, so a fire team is channelled rather than filtered one at a time. */
    private static final int GATE_WIDTH = 3;

    /** Cells across one gate — the only opening a fence is allowed. */
    public static int gateWidth() { return GATE_WIDTH; }

    /** Sides a lot is gated on. Every one of them, so the base never walls off a direction. */
    public static int gatedSides() { return 4; }

    /** Ground this size and its clearance need, for a host sizing an envelope that has to contain one. */
    public static int area(Size size) {
        return (size.width + size.clearance * 2) * (size.depth + size.clearance * 2);
    }

    /** Cells across the reservation a host must set aside, clearance included. */
    public static int reservedSpanX(Size size, Facing facing) {
        return reservedSpanX(size, facing, size.clearance);
    }

    /** Cells across a reservation that keeps {@code clearance} of its own. */
    public static int reservedSpanX(Size size, Facing facing, int clearance) {
        return spanX(size, facing) + clearance * 2;
    }

    /** Cells down the reservation a host must set aside, clearance included. */
    public static int reservedSpanY(Size size, Facing facing) {
        return reservedSpanY(size, facing, size.clearance);
    }

    /** Cells down a reservation that keeps {@code clearance} of its own. */
    public static int reservedSpanY(Size size, Facing facing, int clearance) {
        return spanY(size, facing) + clearance * 2;
    }

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
    /**
     * Package-private so {@code AirbaseLotTest} can check that the strip this
     * lot <em>publishes</em> covers the surface it <em>painted</em>. Which
     * ground kind stands in for runway is an art decision and has no meaning
     * outside this class; a test that hard-coded it would drift the moment the
     * alias moved.
     */
    static final GroundKind RUNWAY = GroundKind.COURTYARD;
    private static final GroundKind INSIDE = GroundKind.INDOOR;
    private static final GroundKind VERGE = GroundKind.SIDEWALK;

    /** Hazard marking, the same treatment a vehicle bay's berth is edged with. */
    private static final GroundKind MARKED = GroundKind.STRIPED;

    private final int left;
    private final int bottom;
    private final int right;
    private final int top;
    private final Facing facing;
    private final boolean alongY;
    private final LandingPad.Approach approach;
    private final Size size;
    private final LandingPad.Purpose purpose;
    /**
     * Clear ground this placement keeps outside its fence.
     *
     * <p>The size's own figure by default, and the host's to override, because
     * whether the surround already exists is something only the host knows. A
     * lot carved out of a packed ward has buildings pressed against it and must
     * buy its own; a lot filling a city block or a claim across several is
     * bounded by the streets they front onto, and reserving more is asking for
     * ground that is already there. Insisting on it cost the city its larger
     * base for a while: the claim's usable ground measured exactly the lot and
     * the request measured the lot plus four.
     */
    private final int clearance;

    /**
     * A lot whose berths are a working garrison field.
     */
    public AirbaseLot(int left, int bottom, int right, int top,
                      Facing facing, Size size) {
        this(left, bottom, right, top, facing, size,
                LandingPad.Purpose.GARRISON_AIRFIELD);
    }

    /**
     * A lot whose berths are published for {@code purpose}.
     *
     * <p>Whether a base is <em>operational</em> is the host's call, not the
     * lot's. The geometry is the same either way — the same paving, sheds,
     * markings and fence — and what changes is who picks the berths up: a
     * garrison field supplies an air arm and can be taken to stop it, while a
     * civil pad is somewhere to put an aircraft down and nothing more. Building
     * a second, cosmetic airbase to get the second behaviour would be two
     * things to keep in step for no reason.
     */
    public AirbaseLot(int left, int bottom, int right, int top,
                      Facing facing, Size size, LandingPad.Purpose purpose) {
        this(left, bottom, right, top, facing, size, purpose, size.clearance);
    }

    /** A lot that keeps {@code clearance} cells of its own outside the fence. */
    public AirbaseLot(int left, int bottom, int right, int top,
                      Facing facing, Size size, LandingPad.Purpose purpose,
                      int clearance) {
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.top = top;
        this.facing = facing;
        this.alongY = facing.alongY;
        this.approach = facing.approach;
        this.size = size;
        this.purpose = purpose;
        this.clearance = clearance;
    }

    /** Cells across the lot on the map's x axis, for the given facing. */
    public static int spanX(Size size, Facing facing) {
        return facing.alongY ? size.width : size.depth;
    }

    /** Cells across the lot on the map's y axis, for the given facing. */
    public static int spanY(Size size, Facing facing) {
        return facing.alongY ? size.depth : size.width;
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
        vehiclePark(ctx);
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
     *
     * <p><b>Paving is replacing, so the lot takes out what was there.</b> Not
     * only the cells: a building id, a kind hint, a wall mask, a doorway or an
     * authored edge that outlives the building it belonged to is worse than
     * clutter. A roof is drawn wherever a cell carries a building id, and a
     * hint left under fresh tarmac is re-flooded into one at finalize — which
     * is a brick slab standing in the middle of the apron with no walls under
     * it, because the walls were paved over and the id was not. The same goes
     * for what generation recorded about the ground: a point of interest or a
     * tactical node still naming a demolished building sends a squad to hold a
     * place that is now a taxiway, and puts a capture marker on it.
     */
    private void pave(GenContext ctx) {
        clearPriorGround(ctx);
        for (int x = left - clearance; x <= right + clearance; x++) {
            for (int y = bottom - clearance; y <= top + clearance; y++) {
                if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) continue;
                boolean insideFence = x >= left && x <= right && y >= bottom && y <= top;
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, insideFence ? APRON : VERGE);
                if (!insideFence) continue;
                ctx.topology.setRoomPurpose(x, y, RoomPurpose.HANGAR);
                // Claimed against the terrain passes that run after fill. The
                // beach override repaints outdoor ground as sand, and it took
                // the apron and half of each berth's markings with it — a
                // fenced airbase floored in beach. Only inside the fence: the
                // verge outside is ground the lot reserved rather than ground
                // it made, and a shore reaching the fence line is right.
                ctx.markMadeGround(x, y);
            }
        }
    }

    /**
     * Take out whatever stood on this reservation before the lot claimed it.
     *
     * <p>Run over the whole reservation rather than only inside the fence,
     * because the verge is paved walkable too: a wall cell turned into
     * sidewalk while its building id and kind hint stay behind is the same
     * orphan roof, just outside the wire.
     *
     * <p>A host that hands over occupied ground is normally the thing at fault
     * — a mission objective ought to be refused a site rather than buried under
     * one, which is why the fortress ward declines a lot over its keep. This is
     * what makes that a placement rule rather than a rendering accident: with
     * nothing left behind, ground the lot was given is ground the lot has, and
     * a host's mistake shows up as a missing building instead of as a marker
     * floating on an apron.
     */
    private void clearPriorGround(GenContext ctx) {
        for (int x = left - clearance; x <= right + clearance; x++) {
            for (int y = bottom - clearance; y <= top + clearance; y++) {
                if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) continue;
                // Before the cell is repaved: an authored edge whose structure
                // is about to be tarmac is scenery with nothing to belong to,
                // and an edge the fence cannot then author on.
                ctx.grid.removeEdgeBarrier(x, y, Direction.E);
                ctx.grid.removeEdgeBarrier(x, y, Direction.N);
                ctx.grid.removeEdgeBarrier(x, y, Direction.W);
                ctx.grid.removeEdgeBarrier(x, y, Direction.S);
                ctx.grid.setDoorway(x, y, false);
                ctx.topology.setWallDirMask(x, y, 0);
                ctx.topology.setWindow(x, y, false);
                ctx.topology.setBuildingKindHint(x, y, null);
                ctx.topology.setBuildingId(x, y, 0);
                ctx.topology.setTag(x, y, Tag.WALL, false);
            }
        }
        ctx.doodads.removeIf(doodad -> onReservation(doodad.cellX, doodad.cellY));
        ctx.pois.removeIf(poi -> onReservation(poi.anchorCellX, poi.anchorCellY));
        ctx.tactical.removeIf(node -> onReservation(node.anchorX, node.anchorY));
    }

    /** Whether this cell is anywhere on the ground the lot reserved. */
    private boolean onReservation(int x, int y) {
        return x >= left - clearance && x <= right + clearance
                && y >= bottom - clearance && y <= top + clearance;
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
        if (size.runwayDepth > 0) ctx.runways.add(strip());
        for (int step = 0; step < size.runwayDepth; step++) {
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
                if (step != 0 && step != size.runwayDepth - 1) continue;
                DoodadDef line = registry.doodad(BAY_EDGE);
                if (line != null) ctx.doodads.add(new Doodad(x, y, line));
            }
        }
    }

    /** Berths in a row on the apron, behind the runway and in front of the sheds. */
    private void berths(GenContext ctx) {
        int row = depthStart() + depthSign() * (size.runwayDepth + runwayMargin() + PAD / 2 + 1);
        int span = size.pads * PAD + (size.pads - 1) * PAD_GAP;
        // A rank of berths is centred; a single one is not. Centring one berth
        // on a small lot puts it in the middle of the only open ground there
        // is and leaves a useless margin all the way round it. Set into a
        // corner it leaves one continuous piece of apron instead, which is
        // where the vehicles go and where anyone crossing the lot walks.
        int start = size.pads > 1
                ? (alongLo() + alongHi() - span) / 2 + PAD / 2
                : alongLo() + 2 + PAD / 2;
        for (int i = 0; i < size.pads; i++) {
            int along = start + i * (PAD + PAD_GAP);
            int cx = alongY ? along : row;
            int cy = alongY ? row : along;
            markBerth(ctx, cx, cy);
            ctx.landingPads.add(berth(cx, cy));
        }
    }

    /**
     * The vehicle park, at the end of the apron opposite the tower.
     *
     * <p>These used to stand one in front of each berth, which is where a
     * bowser is while it is working and nowhere a vehicle is parked. It also
     * put them across the taxiway — the strip the crew walks from the sheds to
     * the aircraft — so the base's own ground traffic was blocking the one path
     * everybody on it uses. Ground vehicles are kept somewhere, and that
     * somewhere is a park.
     *
     * <p>Ranked at the far end, so the two ends of the apron are the two things
     * that are not aircraft: the tower at one, the vehicles at the other, and
     * the berths and their taxiway clear between them.
     *
     * <p>It is also the base's one piece of hard cover. A truck stops rifle
     * fire, and six of them in ranks is a fight worth having — an attacker
     * crossing open apron can reach the park and work up it, which is a better
     * problem than a single truck sitting alone in front of a shed.
     */
    private void vehiclePark(GenContext ctx) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        int slot = 0;
        for (int rank = 0; rank < size.parkRanks; rank++) {
            int across = depthStart()
                    + depthSign() * (size.runwayDepth + runwayMargin() + 1 + rank * PARK_RANK_PITCH);
            for (int file = 0; file < size.parkFiles; file++) {
                int along = alongHi() - 1 - (file + 1) * PARK_FILE_PITCH + 1;
                int x = alongY ? along : across;
                int y = alongY ? across : along;
                DoodadDef truck = registry.doodad(
                        GROUND_SUPPORT[slot++ % GROUND_SUPPORT.length]);
                if (truck == null) continue;
                if (x < left + 1 || y < bottom + 1) continue;
                // Turned with the lot, and measured after turning: a truck
                // rotated a quarter turn is two cells across and three deep
                // rather than the other way round, so a park that fitted on one
                // facing would hang over the fence on the next.
                int spanX = turnedSpanX(truck);
                int spanY = turnedSpanY(truck);
                if (x + spanX - 1 > right - 1) continue;
                if (y + spanY - 1 > top - 1) continue;
                // Never on a berth. A compact lot has the park close enough to
                // the stands that "it fits inside the fence" stops being the
                // same question as "it is not on the aircraft".
                if (onABerth(ctx, x, y, spanX, spanY)) continue;
                ctx.doodads.add(new Doodad(x, y, truck, facing.quarterTurns, facing.mirrored));
            }
        }
    }

    /** A prop's span on the map's x axis once the lot's turn is applied. */
    private int turnedSpanX(DoodadDef def) {
        return (facing.quarterTurns & 1) == 0 ? def.footprintCellsX : def.footprintCellsY;
    }

    /** A prop's span on the map's y axis once the lot's turn is applied. */
    private int turnedSpanY(DoodadDef def) {
        return (facing.quarterTurns & 1) == 0 ? def.footprintCellsY : def.footprintCellsX;
    }

    /** Whether this footprint would stand on any berth already marked out. */
    private boolean onABerth(GenContext ctx, int x, int y, int spanX, int spanY) {
        for (LandingPad pad : ctx.landingPads) {
            if (pad.purpose != LandingPad.Purpose.GARRISON_AIRFIELD) continue;
            if (x + spanX - 1 < pad.left() || x > pad.right()) continue;
            if (y + spanY - 1 < pad.bottom() || y > pad.top()) continue;
            return true;
        }
        return false;
    }

    /**
     * The strip this lot laid, as the geometry a system can fly along.
     *
     * <p>Built from the same four numbers the painting loop uses, so the
     * published centreline is the middle of the surface that was actually made
     * rather than a second description of it that can drift.
     *
     * <p>Thresholds sit half a cell inside the ends of the painted surface,
     * because a cell's centre is where a body standing in it is: an endpoint on
     * the boundary would ask an aircraft to roll to a point half a cell past
     * the last piece of runway there is.
     */
    private Runway strip() {
        float lo = alongLo() + 1 + 0.5f;
        float hi = alongHi() - 1 + 0.5f;
        // Depth runs from the first row into the lot; the centre of the strip
        // is half its depth further in than the first row's centre.
        float depthCentre = depthStart() + 0.5f
                + depthSign() * (size.runwayDepth - 1) * 0.5f;
        return alongY
                ? new Runway(lo, depthCentre, hi, depthCentre, size.runwayDepth)
                : new Runway(depthCentre, lo, depthCentre, hi, size.runwayDepth);
    }

    /** Rows of parked vehicles, and files across each row. */
    /** Pitch between ranks and files — a truck's own span plus room to walk between. */
    private static final int PARK_RANK_PITCH = 3;
    private static final int PARK_FILE_PITCH = 4;

    /** What the base runs on the ground. Cycled so a rank is not six of the same truck. */
    private static final String[] GROUND_SUPPORT = {
            "doodad.parked-tanker-truck",
            "doodad.parked-utility-truck",
            "doodad.parked-flatbed-truck",
            "doodad.parked-cargo-truck",
    };

    /**
     * The bay just paved, published as the berth an aircraft is kept in.
     *
     * <p>A {@link Gantry} rather than a {@link LandingPad}, because nothing
     * lands here: an aircraft in a shed arrived under its own power and leaves
     * the same way. The berth faces out of the shed, which is both the way the
     * machine is parked and the first leg of the taxi.
     *
     * <p>Published from every size that has a shed, including the ones whose
     * aircraft are transports on the apron. The bay is on the map either way,
     * and a fact about the map does not become true only when something is
     * ready to use it.
     */
    private Gantry shelter(int bayLeft, int bayBottom, int bayRight, int bayTop) {
        // Out of the shed is against the lot's own depth direction: the sheds
        // are at the back and their mouths face the apron.
        int outX = alongY ? 0 : -depthSign();
        int outY = alongY ? -depthSign() : 0;
        return new Gantry((bayLeft + bayRight) / 2, (bayBottom + bayTop) / 2,
                (bayRight - bayLeft) / 2, (bayTop - bayBottom) / 2,
                Gantry.Facing.of(outX, outY));
    }

    /** One berth, published for whatever this lot is for. */
    private LandingPad berth(int cx, int cy) {
        return purpose == LandingPad.Purpose.GARRISON_AIRFIELD
                ? LandingPad.garrison(cx, cy, approach)
                : LandingPad.civilian(cx, cy, approach);
    }

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
                + depthSign() * (size.runwayDepth + runwayMargin() + PAD + size.taxiway);
        int span = size.hangars * size.hangarWidth + (size.hangars - 1) * (size.hangarWidth / 2);
        int start = (alongLo() + alongHi() - span) / 2;
        for (int i = 0; i < size.hangars; i++) {
            int alongLo = start + i * (size.hangarWidth + size.hangarWidth / 2);
            stampHangar(ctx, rng, frontDepth, alongLo, alongLo + size.hangarWidth - 1);
        }
    }

    private void stampHangar(GenContext ctx, Random rng,
                             int frontDepth, int alongLo, int alongHi) {
        int backDepth = frontDepth + depthSign() * (size.hangarDepth - 1);
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
                if (onFront && Math.abs(along - openCentre) <= size.hangarOpening / 2) {
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
        ctx.shelters.add(shelter(bayLeft, bayBottom, bayRight, bayTop));

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
        if (!size.tower) return;
        TileRegistry registry = TileRegistry.installed();
        // Behind the strip, not on the end of it. A tower closing off a runway
        // is a building in the one place nothing should be — and it is the
        // first thing an aircraft would meet. Set back into the apron band at
        // the lot's end, it overlooks the whole strip without standing on any
        // part of it, and the runway runs the full length of the lot again.
        int tAlongLo = alongLo() + 1;
        int tAlongHi = tAlongLo + TOWER_WIDTH - 1;
        int tFront = depthStart() + depthSign() * (size.runwayDepth + runwayMargin());
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
                // A gate is only a gate if it opens onto something. On a
                // compact lot a shed's back wall can sit against the fence, and
                // a gap cut in the perimeter there is a doorway into masonry —
                // it reads as a way in from outside and is not one. Skipping it
                // leaves the fence solid where the building already closes the
                // line, which is what a real compound looks like.
                if (gate && opensOntoTheLot(ctx, x, y)) {
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

    /** Whether the cell one step inside this perimeter cell can be stood on. */
    private boolean opensOntoTheLot(GenContext ctx, int x, int y) {
        int inX = x == left ? x + 1 : x == right ? x - 1 : x;
        int inY = y == bottom ? y + 1 : y == top ? y - 1 : y;
        if (inX < 0 || inY < 0 || inX >= ctx.width || inY >= ctx.height) return false;
        return ctx.grid.isWalkable(inX, inY) && !ctx.topology.isWall(inX, inY);
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
        if (alongY) return facing.sign > 0 ? bottom + 1 : top - 1;
        return facing.sign > 0 ? left + 1 : right - 1;
    }

    /** Which way "deeper into the lot" runs on the depth axis. */
    private int depthSign() {
        return facing.sign;
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
