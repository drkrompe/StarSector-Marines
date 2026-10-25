package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.AisleFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.AisleFitting.FixtureGroup;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Packs a fortress's program into its envelope and cuts the roadways that reach
 * it.
 *
 * <p>This runs <b>before</b> any wall is drawn. A fortress whose wall is stamped
 * first can only ever enclose whatever the ordinary fill left inside it; packed
 * first, the wall has something to be drawn around, its gates can land where the
 * roadways already run out to meet them, and its kill zone is measured from real
 * structure. See {@code compound-programs.md}.
 *
 * <p>Roadways are not laid out in advance. The packer cuts a two-wide way to
 * every building that cannot otherwise be reached, through whatever the packing
 * left over, so the fortress ends up with a road network shaped by where its
 * buildings actually went. A grid ruled first would put the buildings in the
 * bays between the roads, which is the parcel-first mistake wearing a different
 * hat.
 */
public final class FortressInterior {

    /**
     * Fortress ground: buildings stand on paved interiors, roadways are stone,
     * and thresholds are marked so a doorway reads as one from outside.
     */
    private static final RoomPacker.Palette PALETTE = new RoomPacker.Palette(
            GroundKind.INDOOR, GroundKind.STONE, GroundKind.STRIPED);

    /** The open ground between the buildings: parade square, vehicle park, verge. */
    private static final GroundKind YARD = GroundKind.DIRT;

    /**
     * How much of a building's wall the ward lets it share with its neighbours.
     *
     * <p>A hull wedges every compartment against the next, because open space
     * inside a ship is wasted displacement. A garrison stands on open ground,
     * and there the same wedging chains the buildings into one continuous wall:
     * measured at production proportions the packing put unbroken runs of
     * seventy-five cells across the ward and twenty-seven through its
     * twenty-eight-cell depth, and left ground fifty-odd cells further to walk
     * to than to look at.
     *
     * <p>It also says how much floor earns another way in. One door is enough
     * to make a room reachable, and reachability is all the packing checks; it
     * is not enough to make a building worth fighting over. A vehicle shed five
     * hundred cells across with one entrance — widened to two cells, which
     * reads as two doors on the same wall — is cleared by holding that doorway,
     * so an attacker never chooses an approach and a defender never covers more
     * than one. Ninety cells is set against the program: a magazine keeps its
     * single door, a barracks block earns a second, and a shed takes every face
     * the packing leaves it. A hull keeps one hatch per compartment, because
     * how a deck is cut is the ship family's decision and not this one's.
     *
     * <p>The allowance is a short seam rather than none. Buildings that corner
     * into each other and share a few cells read as a compound; buildings held
     * apart on all sides read as sheds dropped on a field, which is the look
     * the packing was adopted to get away from. What is bought is a way
     * through: a run stops growing before it becomes a wall nobody can cross.
     */
    private static final RoomPacker.Massing MASSING = new RoomPacker.Massing(6, 4, 90);

    /**
     * Rooms a garrison lives and works in, which are the ones given windows.
     *
     * <p>The distinction an ordinary building shell already draws, kept rather
     * than reinvented: a magazine, a parts cage and a machinery space have no
     * reason to open onto the yard, and a keep's inner chamber is the last
     * place a defender wants a hole in the wall. What is left is where people
     * are — and a window is what they fight from, so this decides how
     * defensible each building is as much as how it looks. A residential hall
     * is one: the civil-defence compound's shelter hall and a field redoubt's
     * bunk dugout are each the last building their people hold, and a blank
     * box is one they could fight from only through its door.
     */
    private static final Set<RoomPurpose> WINDOWED = EnumSet.of(
            RoomPurpose.BARRACKS, RoomPurpose.MESS_HALL, RoomPurpose.CONTROL_ROOM,
            RoomPurpose.KEEP_ENTRY, RoomPurpose.VEHICLE_BAY, RoomPurpose.CIVIC_OFFICE,
            RoomPurpose.CONFERENCE_ROOM, RoomPurpose.PATIENT_WARD, RoomPurpose.CREW_QUARTERS,
            RoomPurpose.RESIDENTIAL_HALL);

    /**
     * Windowed rooms whose windows are laid out before they are furnished, so
     * the fitting stands its fixtures round them rather than against the wall
     * behind them. A hall's bunks otherwise line both long walls end to end,
     * and a window is cut only where the cell inside it can be stood on: the
     * civil-defence shelter came out with one window on a short wall and none
     * on the long ones. Other rooms keep the windows their furnishing leaves
     * room for.
     */
    private static final Set<RoomPurpose> WINDOWS_FIRST = EnumSet.of(RoomPurpose.RESIDENTIAL_HALL);

    /** One window a room's facade will take: the wall cell and the way it faces. */
    private record WindowCell(int x, int y, Direction outward) { }

    /**
     * Minimum pitch between windows or window bays along a facade.
     *
     * <p>Occupied support rooms get wider bays where the whole inward approach
     * is clear; defensive rooms keep spaced single apertures. The spacing
     * also decides the fight, because every window is a
     * firing position: a facade of them is a defended building, and a blank one
     * is a box the garrison can only shoot out of through its door.
     */
    private static final int WINDOW_PITCH = 5;

    /** Shortest facade run worth an aperture, so a stub of wall stays solid. */
    private static final int MIN_FACADE_RUN = 3;

    /**
     * How well a garrison keeps its own buildings. A working arrangement rather
     * than a cramped one: this is a defended installation, not a prize hull
     * being run on a shoestring.
     */
    private static final RoomFit FIT = RoomFit.STANDARD;

    /** Where a fortress's buildings ended up, and what it could not find room for. */
    public record Result(List<RoomPacker.Placed> placed, List<FortressBuilding> unplaced) {

        public Result {
            placed = List.copyOf(placed);
            unplaced = List.copyOf(unplaced);
        }
    }

    private FortressInterior() {}

    /** Finish explicitly arranged buildings through the same shell and fitting rules as packed rooms. */
    public static Result author(GenContext ctx, List<RoomPacker.Placed> rooms) {
        return author(ctx, rooms, List.of());
    }

    /**
     * As {@link #author(GenContext, List)}, except that the rooms in {@code bare} are shelled and dressed but left
     * unfurnished. A fort's aspect buildings are bare because their one fixture, the core structure, is placed by
     * the host and must stand on floor no fitting has already taken.
     */
    public static Result author(GenContext ctx, List<RoomPacker.Placed> rooms, List<RoomPacker.Placed> bare) {
        return author(ctx, rooms, bare, () -> { });
    }

    /**
     * As {@link #author(GenContext, List, List)}, with {@code hostFill} run once the floors, walls and fittings stand and
     * before the windows are cut. The host furnishes its bare rooms there (a fort stands each room's core and fits its
     * authored layout), because a window is cut only through a wall whose inward cell is still standable: a fill that came
     * after the windows would put a console bank behind one, an opening onto nothing.
     */
    public static Result author(GenContext ctx, List<RoomPacker.Placed> rooms, List<RoomPacker.Placed> bare,
                                Runnable hostFill) {
        return author(ctx, rooms, bare, hostFill, null);
    }

    /**
     * A host's own rule for its buildings' windows, in place of the compound's ({@link #WINDOWED} rooms, one aperture a
     * {@link #WINDOW_PITCH}): every room not {@code blind} takes a window every {@code pitch} cells along each straight
     * facade that looks out over open ground, shifted a cell either way where a fixture stands inside the wall, so a
     * fitted room is still windowed round its fittings. A window is never cut at a corner nor beside a door (the facade
     * rule already holds both: a cell beside a doorway or round a corner is not a straight facade cell).
     */
    public record FacadeWindows(Set<RoomPurpose> blind, int pitch) { }

    /** As {@link #author(GenContext, List, List, Runnable)}, the windows cut by {@code windows} where it is given. */
    public static Result author(GenContext ctx, List<RoomPacker.Placed> rooms, List<RoomPacker.Placed> bare,
                                Runnable hostFill, FacadeWindows windows) {
        for (var room : rooms) {
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0], y = room.originY() + cell[1];
                ctx.grid.setWalkable(x, y, false);
            }
            for (int[] cell : room.shape().filled()) {
                int x = room.originX() + cell[0], y = room.originY() + cell[1];
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, PALETTE.roomFloor());
                ctx.topology.setRoomPurpose(x, y, room.purpose());
                ctx.topology.setSurface(x, y, 0);
            }
            for (Doorway door : room.doors()) {
                ctx.grid.setWalkableFloor(door.x(), door.y());
                ctx.grid.setDoorway(door.x(), door.y(), true);
                ctx.topology.setGroundKind(door.x(), door.y(), PALETTE.threshold());
                ctx.topology.setRoomPurpose(door.x(), door.y(), RoomPurpose.CORRIDOR);
                ctx.topology.setSurface(door.x(), door.y(), 0);
            }
        }
        stampWalls(ctx, rooms);
        Map<RoomPacker.Placed, List<WindowCell>> planned = planWindowsFirst(ctx, rooms);
        furnish(ctx, rooms.stream().filter(room -> !bare.contains(room)).toList(), true, planned);
        hostFill.run();
        if (windows == null) stampWindows(ctx, rooms, planned);
        else stampFacadeWindows(ctx, rooms, windows);
        InstallationFloors.dress(ctx, rooms);
        InstallationWalls.dress(ctx, rooms);
        return new Result(rooms, List.of());
    }

    /**
     * Lay the program into {@code ground}, approached from {@code axis}.
     *
     * @param ground every cell the ward owns: where its buildings go, and what
     *               becomes open yard wherever none did
     * @param muster the parade ground and approach that already exist, which the
     *               packing must leave alone and may open onto
     */
    public static Result pack(GenContext ctx, boolean[][] ground, boolean[][] muster,
                              TraversalAxis axis, FortressProgram program) {
        Bounds bounds = Bounds.of(ground, ctx.width, ctx.height);
        if (bounds == null) return new Result(List.of(), program.expanded());

        // A shed is placed for the bay it is furnished with: its machine door is the installation's.
        RoomPacker packer = new RoomPacker(
                ctx, footings(ctx, ground, muster), muster, PALETTE, MASSING)
                .withFittings((purpose, shape) -> purpose == RoomPurpose.VEHICLE_BAY
                        ? RoomFittings.forInstallation(purpose)
                        : RoomFittings.forRoom(ctx, purpose, shape));
        List<RoomPacker.Placed> placed = new ArrayList<>();
        List<RoomShape> authored = new ArrayList<>();
        List<FortressBuilding> unplaced = new ArrayList<>();
        for (FortressBuilding building : program.expanded()) {
            RoomPacker.Request request = new RoomPacker.Request(
                    building.purpose(), building.shape(),
                    wardAffinity(bounds, axis, building.ward()), building.perimeter());
            RoomPacker.Placed room = packer.place(request, true);
            if (room == null) {
                unplaced.add(building);
            } else {
                placed.add(room);
                authored.add(building.shape());
            }
        }
        metalYard(ctx, ground, placed);
        openFurtherDoors(ctx, packer, placed, authored);
        stampWalls(ctx, placed);
        Map<RoomPacker.Placed, List<WindowCell>> planned = planWindowsFirst(ctx, placed);
        furnish(ctx, placed, false, planned);
        stampWindows(ctx, placed, planned);
        InstallationFloors.dress(ctx, placed);
        InstallationWalls.dress(ctx, placed);
        // The airbase is not packed here. It is a lot reserved out of the ward
        // before any of this ran (see FortressWardStage), because an airfield
        // built from leftovers gets the shape leftovers have — a shallow strip,
        // never a facility.
        return new Result(placed, unplaced);
    }

    /**
     * Give every building the further ways in its floor earns, now that the
     * yard around it exists.
     *
     * <p>Ordered between the yard and the walls on purpose, and it has to be
     * both. Before the yard there is nothing outside a building for a second
     * door to open onto, so the packer's own massing rule found none and every
     * building in every ward shipped enterable from exactly one face — a slab
     * an attacker never has to choose an approach to and a defender never has
     * to cover more than one side of. After the walls it would be too late in a
     * different way: a wall is stamped from a mask of which of its faces are
     * outside, and the stamp skips walkable ring cells precisely so an opening
     * is not framed shut. A door cut afterwards would be a door with a wall
     * drawn over it.
     *
     * <p>Also before the fill, because a fitting is handed its doors and some of
     * them arrange around them — a bay joins each of its side doors to the
     * service lane, a berth keeps a rack's width by each hatch.
     */
    private static void openFurtherDoors(GenContext ctx, RoomPacker packer,
                                         List<RoomPacker.Placed> placed,
                                         List<RoomShape> authored) {
        boolean[][] openGround = openGround(ctx, placed);
        for (int i = 0; i < placed.size(); i++) {
            placed.set(i, packer.openFurtherWaysIn(
                    placed.get(i), authored.get(i), openGround));
        }
    }

    /**
     * Ground anybody may stand on that is not the inside of a building: the
     * yard, the parade ground, and the passages cut through them.
     *
     * <p>The exclusion is the point. A building's own floor is walkable too, and
     * where two of them share a wall ring a door cut into it would open one
     * straight into the other — no yard crossed, no approach chosen, and two
     * buildings joined into one enfilade. What a further way in is for is
     * another side to come at the place from.
     */
    private static boolean[][] openGround(GenContext ctx, List<RoomPacker.Placed> placed) {
        boolean[][] interior = new boolean[ctx.width][ctx.height];
        for (RoomPacker.Placed room : placed) {
            for (int[] cell : room.shape().filled()) {
                int x = room.originX() + cell[0];
                int y = room.originY() + cell[1];
                if (x >= 0 && y >= 0 && x < ctx.width && y < ctx.height) interior[x][y] = true;
            }
        }
        boolean[][] open = new boolean[ctx.width][ctx.height];
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                open[x][y] = ctx.grid.isWalkable(x, y) && !interior[x][y];
            }
        }
        return open;
    }

    /**
     * Furnish each building with the fitting authored for its purpose.
     *
     * <p>The same fittings that furnish a deck. A magazine holds racks and ready
     * crates whether it is aboard a ship or against a fortress wall, and a
     * vehicle shed holds the same five-by-seven bays — which is the whole reason
     * the fittings stopped being shipboard. A purpose with no authored fitting
     * is left bare rather than scattered with something generic: an empty
     * building is honest about not being authored yet, one full of crates looks
     * finished and is not.
     *
     * <p>Runs after the yard is opened, not before. A fixture may take its cell
     * out of circulation, and the yard pass opens everything that is not
     * walkable and not wall — so furnishing first would have the yard quietly
     * re-open the cells the fill had just occupied.
     *
     * <p>The rollback is the deck's, for the deck's reason: a fill that seals
     * its own building is worse than no fill, because the building still stands
     * and still shows a door and cannot be entered. What is thrown away is
     * everything the fitting published, not merely what can be seen — a
     * rolled-back bay that kept its berths would offer to service machines on
     * empty floor and look entirely correct from outside.
     */
    private static void furnish(GenContext ctx, List<RoomPacker.Placed> placed, boolean authored,
                                Map<RoomPacker.Placed, List<WindowCell>> planned) {
        for (RoomPacker.Placed room : placed) {
            RoomFitting fitting = RoomFittings.forInstallation(room.purpose());
            if (fitting == null && authored) fitting = switch (room.purpose()) {
                case CONTROL_ROOM -> new AisleFitting(room.purpose(), FixtureGroup.of(
                        "doodad.military-command-console", 2, 1));
                default -> null;
            };
            if (fitting == null) continue;

            int doodads = ctx.doodads.size();
            int berths = ctx.gantries.size();
            int work = ctx.fixtureTasks.size();
            RoomFloor floor = new RoomFloor(ctx, room, FIT);
            // The cell inside each planned window stays floor, for whoever fights from it.
            for (WindowCell window : planned.getOrDefault(room, List.of())) {
                floor.reserveLane(window.x() - window.outward().dx - room.originX(),
                        window.y() - window.outward().dy - room.originY(), 1, 1);
            }
            fitting.fit(floor);
            if (floor.circulationSurvives()) {
                floor.seal();
            } else {
                ctx.doodads.subList(doodads, ctx.doodads.size()).clear();
                ctx.gantries.subList(berths, ctx.gantries.size()).clear();
                ctx.fixtureTasks.subList(work, ctx.fixtureTasks.size()).clear();
            }
        }
    }

    /**
     * The ground a building's floor may actually stand on: the ward's own,
     * less the last cell at every edge of it.
     *
     * <p>A packed room is a floor and the ring around it, and the packer carves
     * only the floor — the ring is wall precisely because it is ground the
     * packing was told to leave alone. So the ring needs ground of the ward's
     * own underneath it. Let a floor reach the ward's last row and the ring
     * falls outside, onto a city street the ward does not own and must not
     * close, because the road graph published that street and a convoy is
     * entitled to drive it. Nothing then makes those cells solid, and the
     * building comes out open along its whole flank — walled by a pavement,
     * with its berths and bunks standing in the street.
     *
     * <p>Circulation is not an edge in that sense. A ring may not cross the
     * kept road at all — the packer rejects the placement outright — so a room
     * beside one is already as close as it is allowed to get, and treating the
     * road as an edge here would only push the packing a cell further off it
     * for nothing.
     */
    private static boolean[][] footings(GenContext ctx, boolean[][] ground, boolean[][] muster) {
        boolean[][] footings = new boolean[ctx.width][ctx.height];
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (!ground[x][y]) continue;
                boolean walled = true;
                for (int dx = -1; dx <= 1 && walled; dx++) {
                    for (int dy = -1; dy <= 1 && walled; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        walled = nx >= 0 && ny >= 0 && nx < ctx.width && ny < ctx.height
                                && (ground[nx][ny] || muster[nx][ny]);
                    }
                }
                footings[x][y] = walled;
            }
        }
        return footings;
    }

    /**
     * Author the wall around every building the packer left as negative space.
     *
     * <p>The packer draws no walls. It carves a room out of solid ground, and
     * the ring around it is simply ground that was not carved — which is all a
     * hull needs, because a void inside a ship is structure whether or not
     * anything says so. On a map a wall is a thing in its own right: it is
     * drawn from a per-cell mask naming which of its faces are outside, and a
     * ring carrying no mask falls through to the blank fill reserved for a cell
     * buried inside a wall mass. That is a building with no wall on any face,
     * standing as an invisible obstruction with its furniture apparently out in
     * the open.
     *
     * <p>A face is exterior when the cell beyond it is not part of this
     * building's own footprint. That is the rule
     * {@link com.dillon.starsectormarines.battle.world.model.WallMasks#stampPerimeter}
     * applies to a rectangle, restated so that it also holds for a shape which
     * is not one. Faces are or-ed rather than assigned, so where two buildings
     * wedge tight enough to share a ring cell the shared wall comes out with a
     * face towards each of them.
     *
     * <p>Walkable ring cells are skipped. A doorway, or a cell some other
     * room's passage was cut through, is an opening rather than wall, and the
     * cells beside it keep the faces the footprint gives them: framing the gap
     * would draw the hole instead of the building. This matches what an
     * ordinary building does when its doorway is punched.
     */
    private static void stampWalls(GenContext ctx, List<RoomPacker.Placed> placed) {
        for (RoomPacker.Placed room : placed) {
            Set<Long> footprint = new HashSet<>();
            for (int[] cell : room.shape().filled()) {
                footprint.add(cellKey(room.originX() + cell[0], room.originY() + cell[1]));
            }
            for (int[] cell : room.shape().wall()) {
                footprint.add(cellKey(room.originX() + cell[0], room.originY() + cell[1]));
            }
            for (int[] cell : room.shape().filled()) {
                hint(ctx, room.originX() + cell[0], room.originY() + cell[1]);
            }
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0];
                int y = room.originY() + cell[1];
                if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) continue;
                if (ctx.grid.isWalkable(x, y)) continue;
                int mask = 0;
                if (!footprint.contains(cellKey(x, y + 1))) mask |= CellTopology.WALL_DIR_N;
                if (!footprint.contains(cellKey(x, y - 1))) mask |= CellTopology.WALL_DIR_S;
                if (!footprint.contains(cellKey(x + 1, y))) mask |= CellTopology.WALL_DIR_E;
                if (!footprint.contains(cellKey(x - 1, y))) mask |= CellTopology.WALL_DIR_W;
                ctx.topology.orWallDirMask(x, y, mask);
                // The room's own ground goes under its wall. Nothing draws it
                // while the wall stands, but a wall does not always stand: cut
                // a window through this cell and the ground beneath is what
                // shows in the opening, and breach it and the rubble sits on
                // whatever was there. Leaving the yard under a building's wall
                // puts a patch of mud in the middle of it either way.
                ctx.topology.setGroundKind(x, y, PALETTE.roomFloor());
                hint(ctx, x, y);
            }
        }
    }

    /**
     * Claim a cell for a fortified building, so the pass that votes a kind per
     * flooded room reads a garrison building rather than falling through to the
     * unclassified default.
     */
    private static void hint(GenContext ctx, int x, int y) {
        if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return;
        ctx.topology.setBuildingKindHint(x, y, BuildingKind.FORTIFIED);
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    /**
     * Open firing windows along each building's facades.
     *
     * <p>The same feature an ordinary building shell authors, authored the same
     * way: the cell becomes walkable floor and the window is a
     * {@link com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier} on its
     * outward edge, rather than a see-through wall cell. That is what lets one
     * feature describe itself identically to movement, sight, fire and cover —
     * a defender stands in the opening and shoots through it, and nobody walks
     * through it.
     *
     * <p>Windows face the yard, never the room next door. Two buildings the
     * packing wedged together share a single ring cell, and an aperture there
     * would be a window between a barracks and a magazine, so an outward cell
     * belonging to another building's floor disqualifies it.
     *
     * <p>Runs after the fill rather than before. A fitting may shut the floor
     * behind a stretch of wall, and a window onto a sealed cell is an opening
     * nothing can reach or fire from; requiring the inward cell to still be
     * walkable when the aperture is cut is what keeps the two in step.
     */
    private static void stampWindows(GenContext ctx, List<RoomPacker.Placed> placed,
                                     Map<RoomPacker.Placed, List<WindowCell>> planned) {
        Set<Long> occupied = occupied(placed);
        for (RoomPacker.Placed room : placed) {
            if (!WINDOWED.contains(room.purpose())) continue;
            List<WindowCell> plan = planned.get(room);
            if (plan != null) {
                // Laid out before the furnishing, which stood its fixtures round them.
                for (WindowCell window : plan) {
                    if (ctx.grid.isWalkable(window.x() - window.outward().dx, window.y() - window.outward().dy)) {
                        openWindow(ctx, window.x(), window.y(), window.outward());
                    }
                }
                continue;
            }
            Set<Long> floor = floorOf(room);
            for (Direction outward : FACADES) {
                for (WindowCell window : facadeWindows(ctx, room, floor, occupied, outward)) {
                    openWindow(ctx, window.x(), window.y(), window.outward());
                }
            }
        }
    }

    private static final Direction[] FACADES = { Direction.N, Direction.S, Direction.E, Direction.W };

    /** Cuts every room's windows by a host's {@link FacadeWindows} rule. */
    private static void stampFacadeWindows(GenContext ctx, List<RoomPacker.Placed> placed, FacadeWindows rule) {
        Set<Long> occupied = occupied(placed);
        for (RoomPacker.Placed room : placed) {
            if (rule.blind().contains(room.purpose())) continue;
            Set<Long> floor = floorOf(room);
            for (Direction outward : FACADES) {
                for (int[] cell : facadeCells(ctx, room, floor, occupied, outward, rule.pitch())) {
                    openWindow(ctx, cell[0], cell[1], outward);
                }
            }
        }
    }

    /**
     * The windows of one facade by a {@link FacadeWindows} pitch: each run of straight facade wall is given a window
     * every {@code pitch} cells, centred on the run; where a fixture stands inside the planned cell the window moves a
     * cell along if that keeps a wall cell between it and its neighbour, and is dropped otherwise.
     */
    static List<int[]> facadeCells(GenContext ctx, RoomPacker.Placed room, Set<Long> floor, Set<Long> occupied,
                                   Direction outward, int pitch) {
        int stepX = outward.dy, stepY = outward.dx;
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int[] ring : room.shape().wall()) {
            int x = room.originX() + ring[0], y = room.originY() + ring[1];
            if (seen.contains(cellKey(x, y)) || !facade(ctx, x, y, floor, occupied, outward)) continue;
            if (facade(ctx, x - stepX, y - stepY, floor, occupied, outward)) continue;
            List<int[]> run = new ArrayList<>();
            for (int cx = x, cy = y; facade(ctx, cx, cy, floor, occupied, outward); cx += stepX, cy += stepY) {
                run.add(new int[]{cx, cy});
                seen.add(cellKey(cx, cy));
            }
            int count = 1 + (run.size() - 1) / pitch;
            int first = (run.size() - 1 - (count - 1) * pitch) / 2;
            int last = -2;
            for (int i = 0; i < count; i++) {
                int planned = first + i * pitch;
                for (int at : new int[]{planned, planned + 1, planned - 1}) {
                    if (at < 0 || at >= run.size() || at - last < 2) continue;
                    int[] cell = run.get(at);
                    if (!ctx.grid.isWalkable(cell[0] - outward.dx, cell[1] - outward.dy)) continue;
                    out.add(cell);
                    last = at;
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Whether the cell is straight facade wall of this building looking out over open ground: {@link #eligible} without
     * asking that the floor inside it be clear, since a fixture inside moves a window rather than ending the facade.
     */
    private static boolean facade(GenContext ctx, int x, int y, Set<Long> floor, Set<Long> occupied, Direction outward) {
        if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return false;
        if (ctx.grid.isWalkable(x, y) || ctx.grid.isDoorway(x, y)) return false;
        if (!floor.contains(cellKey(x - outward.dx, y - outward.dy))) return false;
        int outsideX = x + outward.dx, outsideY = y + outward.dy;
        if (!ctx.grid.inBounds(outsideX, outsideY) || !ctx.grid.isWalkable(outsideX, outsideY)) return false;
        if (occupied.contains(cellKey(outsideX, outsideY))) return false;
        for (Direction side : Direction.CARDINALS) {
            if (side == outward) continue;
            int nx = x + side.dx, ny = y + side.dy;
            if (!ctx.grid.inBounds(nx, ny) || floor.contains(cellKey(nx, ny))) continue;
            if (ctx.grid.isWalkable(nx, ny)) return false;
        }
        return true;
    }

    /** The windows of every {@link #WINDOWS_FIRST} room, laid out on its bare floor. */
    private static Map<RoomPacker.Placed, List<WindowCell>> planWindowsFirst(GenContext ctx,
                                                                             List<RoomPacker.Placed> placed) {
        Map<RoomPacker.Placed, List<WindowCell>> out = new HashMap<>();
        Set<Long> occupied = occupied(placed);
        for (RoomPacker.Placed room : placed) {
            if (!WINDOWS_FIRST.contains(room.purpose()) || !WINDOWED.contains(room.purpose())) continue;
            Set<Long> floor = floorOf(room);
            List<WindowCell> windows = new ArrayList<>();
            for (Direction outward : FACADES) windows.addAll(facadeWindows(ctx, room, floor, occupied, outward));
            out.put(room, windows);
        }
        return out;
    }

    private static Set<Long> occupied(List<RoomPacker.Placed> placed) {
        Set<Long> occupied = new HashSet<>();
        for (RoomPacker.Placed room : placed) occupied.addAll(floorOf(room));
        return occupied;
    }

    private static Set<Long> floorOf(RoomPacker.Placed room) {
        Set<Long> floor = new HashSet<>();
        for (int[] cell : room.shape().filled()) {
            floor.add(cellKey(room.originX() + cell[0], room.originY() + cell[1]));
        }
        return floor;
    }

    /**
     * Where one facade's windows go.
     *
     * <p>The facade is walked as runs of eligible wall rather than as the side
     * of a rectangle, because a packed room need not be one and a run may be
     * interrupted by a door, by the building next door, or by the wall turning
     * a corner. Each run is given apertures on a fixed pitch, centred within
     * the run so a wall's windows sit symmetrically on it instead of crowding
     * whichever end the walk happened to start from.
     */
    private static List<WindowCell> facadeWindows(GenContext ctx, RoomPacker.Placed room, Set<Long> floor,
                                                  Set<Long> occupied, Direction outward) {
        List<WindowCell> windows = new ArrayList<>();
        // Step along the facade, which runs at right angles to the way it faces.
        int stepX = outward.dy;
        int stepY = outward.dx;
        List<List<int[]>> runs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int[] cell : room.shape().wall()) {
            int x = room.originX() + cell[0];
            int y = room.originY() + cell[1];
            if (!eligible(ctx, x, y, floor, occupied, outward)) continue;
            if (seen.contains(cellKey(x, y))) continue;
            // Start from a run's first cell, so it is collected once and in
            // order however the ring happened to list its cells.
            if (eligible(ctx, x - stepX, y - stepY, floor, occupied, outward)) continue;
            List<int[]> run = new ArrayList<>();
            for (int cx = x, cy = y; eligible(ctx, cx, cy, floor, occupied, outward);
                    cx += stepX, cy += stepY) {
                run.add(new int[]{ cx, cy });
                seen.add(cellKey(cx, cy));
            }
            runs.add(run);
        }
        for (List<int[]> facade : runs) {
            if (facade.size() < MIN_FACADE_RUN) continue;
            int panes = switch (room.purpose()) {
                case CIVIC_OFFICE, CONFERENCE_ROOM, MESS_HALL -> Math.min(3, facade.size() - 2);
                case PATIENT_WARD, CREW_QUARTERS -> Math.min(2, facade.size() - 2);
                default -> 1;
            };
            int pitch = Math.max(WINDOW_PITCH, panes + 3);
            int count = 1 + (facade.size() - panes) / pitch;
            int first = (facade.size() - panes - (count - 1) * pitch) / 2;
            for (int i = 0; i < count; i++) for (int pane = 0; pane < panes; pane++) {
                int[] cell = facade.get(first + i * pitch + pane);
                windows.add(new WindowCell(cell[0], cell[1], outward));
            }
        }
        return windows;
    }

    /** Whether this cell is wall this building could open onto ground beyond. */
    private static boolean eligible(GenContext ctx, int x, int y, Set<Long> floor,
                                    Set<Long> occupied, Direction outward) {
        if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return false;
        if (ctx.grid.isWalkable(x, y) || ctx.grid.isDoorway(x, y)) return false;
        int insideX = x - outward.dx;
        int insideY = y - outward.dy;
        int outsideX = x + outward.dx;
        int outsideY = y + outward.dy;
        if (!floor.contains(cellKey(insideX, insideY))) return false;
        if (!ctx.grid.inBounds(outsideX, outsideY)) return false;
        if (!ctx.grid.isWalkable(insideX, insideY)) return false;
        if (!ctx.grid.isWalkable(outsideX, outsideY)) return false;
        if (occupied.contains(cellKey(outsideX, outsideY))) return false;
        // Opening a ring cell makes the entire cell standable. A convex or
        // irregular corner can face walkable yard on a second side; placing a
        // barrier only on the requested facade would then leave that side open
        // and join the room's navigation/capture zone to the yard. Windows are
        // therefore cut only through a straight facade cell whose other
        // non-interior cardinal neighbours remain solid.
        for (Direction side : Direction.CARDINALS) {
            if (side == outward) continue;
            int nx = x + side.dx;
            int ny = y + side.dy;
            if (!ctx.grid.inBounds(nx, ny)) continue;
            if (floor.contains(cellKey(nx, ny))) continue;
            if (ctx.grid.isWalkable(nx, ny)) return false;
        }
        return true;
    }

    /**
     * Turn one wall cell into a window onto the ground beyond it. Public for a host that authors its own apertures (a
     * fort's gate towers are slit where they look out of the works, not by the facade rule).
     */
    public static void openWindow(GenContext ctx, int x, int y, Direction outward) {
        int outsideX = x + outward.dx;
        int outsideY = y + outward.dy;
        if (ctx.grid.getEdgeBarrier(x, y, outward) != null) return;
        if (!ctx.grid.isEdgePassable(outsideX, outsideY, outward.opposite())) return;
        ctx.grid.setWalkableFloor(x, y);
        ctx.topology.setWallDirMask(x, y, 0);
        ctx.topology.setWindow(x, y, false);
        ctx.grid.placeEdgeBarrier(x, y, outward, SharedEdgeBarrier.Kind.WINDOW);
    }

    /**
     * Open every scrap of ground the packing did not build on.
     *
     * <p>Where a hull's leftovers stay solid — a void inside a ship is
     * structure, and walking through it would be walking through the vessel —
     * a fortress's leftovers are its yard. Parade ground, vehicle park, the
     * space between a magazine and the wall: all of it is ground people cross,
     * and leaving it closed would produce a fortress that is mostly corridor
     * and cannot be fought through.
     *
     * <p>This is also what makes the roadways read as roadways. The packer cuts
     * them because a building had to be reached; against solid ground they are
     * the only way through, which is a warren. Against open yard they are the
     * made-up routes across it, and a squad can leave one and cross open ground
     * under fire — which is the choice a fortress assault should be about.
     */
    private static void metalYard(GenContext ctx, boolean[][] buildable,
                                  List<RoomPacker.Placed> placed) {
        boolean[][] structure = new boolean[ctx.width][ctx.height];
        for (RoomPacker.Placed room : placed) {
            for (int[] cell : room.shape().wall()) {
                int x = room.originX() + cell[0];
                int y = room.originY() + cell[1];
                if (x >= 0 && y >= 0 && x < ctx.width && y < ctx.height) structure[x][y] = true;
            }
        }
        // A doorway is cut through the ring, so it is not structure however the
        // ring was stamped; opening the yard must not close a building's door.
        for (RoomPacker.Placed room : placed) {
            for (Doorway door : room.doors()) {
                if (door.x() >= 0 && door.y() >= 0
                        && door.x() < ctx.width && door.y() < ctx.height) {
                    structure[door.x()][door.y()] = false;
                }
            }
        }
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (!buildable[x][y] || structure[x][y] || ctx.grid.isWalkable(x, y)) continue;
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, YARD);
                ctx.topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
            }
        }
    }

    /**
     * Whether a position sits at the depth this ward means, measured along the
     * attacker's approach.
     *
     * <p>Thirds rather than a distance falloff. A building is near the wall, in
     * the middle, or at the back, and the packer's affinity is a yes-or-no
     * anyway — grading it would be precision the packing cannot honour once
     * shapes start wedging against each other.
     */
    private static RoomPacker.Affinity wardAffinity(Bounds bounds, TraversalAxis axis, Ward ward) {
        return (centreX, centreY) -> depthBand(bounds, axis, centreX, centreY) == ward;
    }

    /** Which third of the approach this cell falls in, with FRONTAGE nearest the attacker. */
    private static Ward depthBand(Bounds bounds, TraversalAxis axis, int x, int y) {
        // The attacker enters at the low end of the traversal axis, so distance
        // from that end is distance from the wall they will breach.
        int span = axis == TraversalAxis.SOUTH_TO_NORTH ? bounds.height() : bounds.width();
        int from = axis == TraversalAxis.SOUTH_TO_NORTH ? y - bounds.top() : x - bounds.left();
        if (span <= 0) return Ward.YARD;
        int third = from * 3 / span;
        return third <= 0 ? Ward.FRONTAGE : third == 1 ? Ward.YARD : Ward.REAR;
    }

    /** The extent of the buildable envelope, so wards are thirds of the fortress. */
    private record Bounds(int left, int top, int right, int bottom) {

        static Bounds of(boolean[][] mask, int width, int height) {
            int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (!mask[x][y]) continue;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
            return right < left ? null : new Bounds(left, top, right, bottom);
        }

        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }
}
