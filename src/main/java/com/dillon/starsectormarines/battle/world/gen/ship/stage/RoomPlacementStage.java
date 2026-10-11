package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.fit.CirculationLoops;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.HullContact;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayouts;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Step 3 (ship) — pack the deck's room program into the hull.
 *
 * <p>The packing itself is {@link RoomPacker} and is not a shipboard idea. What
 * belongs here is everything about <em>this</em> place: that the hull profile is
 * the buildable envelope and the spine is circulation that already exists, that
 * a room belongs in the longitudinal zone its purpose is at home in, that a boat
 * bay must reach a flank and an engine room the transom, and that a deck is laid
 * out on indoor deck plate.
 *
 * <p>Program order is also policy rather than mechanism. Rooms are laid largest
 * first, so the compartments that cannot go just anywhere get their pick before
 * the small ones fill in around them, and a room the hull could not hold is
 * published as unplaced rather than quietly dropped — a deck short of its
 * program is a sizing defect, and hiding it would turn a measurable shortfall
 * into a deck that merely looks a little empty.
 */
public final class RoomPlacementStage implements GenStage {

    /** A deck is laid on indoor plate, with its thresholds marked. */
    private static final RoomPacker.Palette DECK_PALETTE = new RoomPacker.Palette(
            GroundKind.INDOOR, GroundKind.INDOOR, GroundKind.STRIPED);

    /**
     * When a deck is worth a second way round.
     *
     * <p><b>Double the walk, and a dozen cells saved.</b> Doubling is a low bar
     * on the face of it and is not one in practice: a deck offers very few
     * places where it can be cleared at all, because a passage may never come
     * within a cell of a compartment and the rooms are packed against one
     * another. Measured across five vanilla hulls at two seeds it takes nothing
     * on a frigate, which needs nothing, and three to six links on a capital.
     * Raising it to four times took two links instead of six and left detours
     * standing that a twenty-cell cut would have halved.
     *
     * <p>The budget bounds the search rather than expressing taste; sixty-four
     * and a hundred and twenty produce the same decks, because what actually
     * refuses a link is the detour test and not the length.
     */
    private static final CirculationLoops.Policy DECK_LOOPS =
            new CirculationLoops.Policy(64, 2, 12);

    private final RoomFit fit;

    /** A deck packed at the level its rooms will be furnished at. */
    public RoomPlacementStage(RoomFit fit) {
        this.fit = fit == null ? RoomFit.STANDARD : fit;
    }

    /**
     * The footprint to pack this room at: whatever an authored layout draws for
     * it, and otherwise the recipe's own.
     *
     * <p>A bigger authored room is not guaranteed a berth. The deck was sized
     * from the program's recipes, so a room drawn larger than the one it
     * replaces may simply fail to fit and land among the rooms the deck could
     * not take — which is the honest outcome and already a case the graph
     * carries.
     */
    private static RoomShape footprint(RoomRecipe recipe, RoomFit fit) {
        RoomShape authored = RoomLayouts.installed().footprintFor(recipe.purpose(), fit);
        return authored != null ? authored : recipe.shape();
    }

    @Override
    public void run(GenContext ctx) {
        // Published before anything is packed, because a room's authored doors
        // are a constraint on where it may go and an authored layout is what
        // carries them. Placing first and asking afterwards is exactly the
        // ordering law 14 exists to forbid.
        ctx.put(RoomFittings.ROOM_FIT, fit);
        DeckProfile profile = ctx.get(ShipKeys.DECK_PROFILE);
        List<RoomRecipe> program = ctx.get(ShipKeys.ROOM_PROGRAM);
        if (profile == null || program == null) {
            throw new IllegalStateException(
                    "RoomPlacementStage requires a deck profile and a room program");
        }

        boolean[][] hull = hullMask(ctx, profile);
        RoomPacker packer = new RoomPacker(ctx, hull,
                spineMask(ctx, profile), DECK_PALETTE);

        List<RoomRecipe> ordered = new ArrayList<>(program);
        ordered.sort(Comparator.comparingInt(RoomRecipe::area).reversed()
                .thenComparing(recipe -> recipe.purpose().name()));

        List<DeckGraph.Compartment> placed = new ArrayList<>();
        List<RoomRecipe> unplaced = new ArrayList<>();
        List<BayAperture> apertures = new ArrayList<>();
        for (RoomRecipe recipe : ordered) {
            RoomPacker.Placed room = packer.place(request(profile, recipe, fit), true);
            if (room == null) {
                unplaced.add(recipe);
            } else {
                DeckGraph.Compartment compartment = describe(profile, room, placed.size());
                placed.add(compartment);
            }
        }
        fillPockets(profile, packer, placed);
        // Last, because it is a judgement about the finished network. Every
        // passage above reached the nearest thing already connected, which is
        // right each time and leaves a tree overall.
        packer.openLoops(DECK_LOOPS);
        for (DeckGraph.Compartment room : placed) {
            if (room.purpose() != RoomPurpose.HANGAR) continue;
            BayAperture door = doorOnto(ctx, hull, room, placed);
            if (door != null) apertures.add(door);
        }
        ctx.put(ShipKeys.DECK_GRAPH,
                new DeckGraph(placed, unplaced, apertures, ctx.get(ShipKeys.SHIPS_BOATS)));
    }

    /**
     * Offer every pocket the authored rooms did not want to the utility set,
     * largest first, until a whole pass places nothing. There is no count to cap
     * here: the deck has a finite number of cells, every placement consumes
     * some, and how many there were to begin with is exactly what the density
     * dial decides. Utility rooms take a direct door only — a locker is worth a
     * pocket that already touches a passage, never worth tunnelling to.
     */
    private void fillPockets(DeckProfile profile, RoomPacker packer,
                             List<DeckGraph.Compartment> placed) {
        List<RoomRecipe> utility = new ArrayList<>(RoomRecipe.UTILITY);
        utility.sort(Comparator.comparingInt(RoomRecipe::area).reversed());
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (RoomRecipe recipe : utility) {
                RoomPacker.Placed room = packer.place(request(profile, recipe, fit), false);
                if (room == null) continue;
                placed.add(describe(profile, room, placed.size()));
                progressed = true;
                break;
            }
        }
    }

    /**
     * A recipe as the packer reads it: the zone becomes an opinion about
     * position, and the hull contact becomes an edge the room has to reach.
     */
    private static RoomPacker.Request request(DeckProfile profile, RoomRecipe recipe,
                                              RoomFit fit) {
        RoomPacker.Affinity affinity = recipe.zone() == null
                ? RoomPacker.Affinity.ANYWHERE
                : (centreX, centreY) -> profile.zone(clampFrame(profile, centreX)) == recipe.zone();
        return new RoomPacker.Request(recipe.purpose(), footprint(recipe, fit), affinity,
                edgeContact(recipe.contact()), recipe.contact() == HullContact.FLANK ? DOOR_CELLS : 0);
    }

    private static RoomPacker.EdgeContact edgeContact(HullContact contact) {
        return switch (contact) {
            case NONE -> RoomPacker.EdgeContact.NONE;
            case FLANK -> RoomPacker.EdgeContact.ANY;
            case STERN -> RoomPacker.EdgeContact.TRAILING;
        };
    }

    /** A packed room as the deck records it: which side of the spine, and which zone. */
    private static DeckGraph.Compartment describe(DeckProfile profile,
                                                  RoomPacker.Placed room, int id) {
        int left = room.originX();
        int top = room.originY();
        int right = left + room.shape().width() - 1;
        int bottom = top + room.shape().height() - 1;
        int spineCentre = (profile.spineTop() + profile.spineBottom()) / 2;
        DeckSide side = (top + bottom) / 2 < spineCentre ? DeckSide.PORT : DeckSide.STARBOARD;
        DeckZone zone = profile.zone(clampFrame(profile, (left + right) / 2));
        return new DeckGraph.Compartment(id, room.shape(), left, top,
                room.pose(), side, zone, room.purpose(), room.doors());
    }

    /** The central taxi aisle and the pressure opening share one physical width. */
    private static final int DOOR_CELLS = 8;

    /**
     * A centered opening on the fitting's outboard wall. The complete finished
     * deck is inspected before publishing it, so a tapered hull may contribute
     * an uncarved structural lip but a neighboring room or passage is never cut.
     * Coordinates use continuous world space: cell centres lie at index + 0.5.
     */
    static BayAperture doorOnto(GenContext ctx, boolean[][] hull,
                               DeckGraph.Compartment room,
                               List<DeckGraph.Compartment> rooms) {
        RoomFitting fitting = RoomFittings.forShipRoom(room.purpose(), room.shape(), RoomFittings.levelOf(ctx));
        int[] authored = fitting == null ? null : fitting.outboard();
        int[] named = authored == null ? null
                : room.pose().mapDirection(authored[0], authored[1]);
        for (int[] side : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            if (named != null && (side[0] != named[0] || side[1] != named[1])) continue;
            boolean vertical = side[0] != 0;
            int extent = vertical ? room.depth() : room.width();
            int width = Math.min(DOOR_CELLS, extent);
            // Match parity so both jambs land on cell boundaries around the aisle.
            if ((width & 1) != (extent & 1)) width--;
            if (width <= 0) continue;
            int alongStart = (vertical ? room.top() : room.left()) + (extent - width) / 2;
            int fixed = side[0] < 0 ? room.left() - 1 : side[0] > 0 ? room.right() + 1
                    : side[1] < 0 ? room.top() - 1 : room.bottom() + 1;
            boolean exposed = false;
            for (int i = 0; i < extent; i++) {
                int x = vertical ? fixed : room.left() + i;
                int y = vertical ? room.top() + i : fixed;
                exposed |= !insideHull(ctx, hull, x, y);
            }
            boolean clear = true;
            for (int i = 0; i < width; i++) {
                int x = vertical ? fixed : alongStart + i;
                int y = vertical ? alongStart + i : fixed;
                if (!room.contains(x - side[0], y - side[1])) { clear = false; break; }
                exposed |= !insideHull(ctx, hull, x, y);
                for (int rx = x, ry = y; ctx.grid.inBounds(rx, ry);
                     rx += side[0], ry += side[1]) {
                    if (ctx.grid.isWalkable(rx, ry) || ctx.topology.isFixture(rx, ry)) {
                        clear = false;
                        break;
                    }
                    for (DeckGraph.Compartment other : rooms) {
                        if (other.id() != room.id() && other.contains(rx, ry)) clear = false;
                    }
                    if (!clear) break;
                }
                if (!clear) break;
            }
            if (clear && exposed) {
                float middle = alongStart + width * 0.5f;
                return new BayAperture(room.id(), vertical ? fixed + 0.5f : middle,
                        vertical ? middle : fixed + 0.5f, side[0], side[1], width);
            }
        }
        return null;
    }
    /** Whether this cell is deck the ship actually has, rather than the space around her. */
    private static boolean insideHull(GenContext ctx, boolean[][] hull, int x, int y) {
        if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return false;
        return hull[x][y];
    }

    /** Everything inside the hull profile is buildable; everything else is sea. */
    private static boolean[][] hullMask(GenContext ctx, DeckProfile profile) {
        boolean[][] mask = new boolean[ctx.width][ctx.height];
        for (int x = 0; x < profile.frames() && x < ctx.width; x++) {
            for (int y = profile.top(x); y <= profile.bottom(x); y++) {
                if (y < 0 || y >= ctx.height) continue;
                mask[x][y] = true;
            }
        }
        return mask;
    }

    /**
     * The spine, which was cut before this stage ran. It is circulation that
     * already exists rather than space to be packed, so the packer takes it as
     * floor and no room may eat into it.
     */
    private static boolean[][] spineMask(GenContext ctx, DeckProfile profile) {
        boolean[][] mask = new boolean[ctx.width][ctx.height];
        for (int x = 0; x < profile.frames() && x < ctx.width; x++) {
            for (int y = profile.spineTop(); y <= profile.spineBottom(); y++) {
                if (y < 0 || y >= ctx.height) continue;
                mask[x][y] = true;
            }
        }
        return mask;
    }

    private static int clampFrame(DeckProfile profile, int frame) {
        return Math.max(0, Math.min(profile.frames() - 1, frame));
    }
}
