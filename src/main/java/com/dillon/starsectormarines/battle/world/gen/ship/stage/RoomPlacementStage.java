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
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayouts;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

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
                // Here rather than in the fill, because this is the moment the
                // hull is known and the room's position in it is settled. A
                // fitting sees a floor and a pose; which of its bulkheads has
                // vacuum behind it is the placer's doing.
                // A flank, not any hull contact. The two contacts mean different
                // things: a flank is where a bay launches something, and a
                // transom is where an engine room has to sit to be driving
                // anything. Giving the engine room a door would publish an
                // opening onto space at the back of every ship in the fleet.
                if (recipe.contact() == HullContact.FLANK) {
                    BayAperture door = doorOnto(ctx, hull, compartment);
                    if (door != null) apertures.add(door);
                }
            }
        }
        fillPockets(profile, packer, placed);
        // Last, because it is a judgement about the finished network. Every
        // passage above reached the nearest thing already connected, which is
        // right each time and leaves a tree overall.
        packer.openLoops(DECK_LOOPS);
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
                edgeContact(recipe.contact()));
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

    /**
     * How wide a bay's door is, at most.
     *
     * <p>Enough for the largest thing kept in one to go through it, and no more
     * than the outboard run the room actually has. It is a bound rather than a
     * size: a gig bay's door is as wide as the gig bay, and widening it to a
     * number written here would put a door through the compartment next along.
     */
    private static final int DOOR_CELLS = 7;

    /**
     * The door onto the outside for a room the placer pushed against the hull,
     * or null for one that somehow ended up amidships.
     *
     * <p>Found by asking which of the room's own bulkhead cells have vacuum
     * behind them and taking the longest unbroken run of them, which is the same
     * question the packer asked to place the room at all — {@code meetsEdge}
     * accepts a room one of whose ring cells lies outside the hull, and this
     * recovers <em>which</em> ones did.
     *
     * <p>Returning null rather than throwing: the recipe demands hull contact
     * and the packer honours it, so a bay amidships is a defect somewhere
     * upstream. It shows up as a bay with no way out, which the deck can report,
     * and not as a generation that stops.
     */
    private static BayAperture doorOnto(GenContext ctx, boolean[][] hull,
                                        DeckGraph.Compartment room) {
        int left = room.originX();
        int top = room.originY();
        int right = left + room.shape().width() - 1;
        int bottom = top + room.shape().height() - 1;

        BayAperture best = null;
        int bestRun = 0;
        for (int[] side : new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            boolean vertical = side[0] != 0;
            int from = vertical ? top : left;
            int to = vertical ? bottom : right;
            int fixed = side[0] < 0 ? left - 1 : side[0] > 0 ? right + 1
                    : side[1] < 0 ? top - 1 : bottom + 1;

            int run = 0;
            int runFrom = from;
            for (int along = from; along <= to + 1; along++) {
                int x = vertical ? fixed : along;
                int y = vertical ? along : fixed;
                boolean open = along <= to && !insideHull(ctx, hull, x, y);
                if (open) {
                    if (run == 0) runFrom = along;
                    run++;
                    continue;
                }
                if (run > bestRun) {
                    bestRun = run;
                    int width = Math.min(DOOR_CELLS, run);
                    float middle = runFrom + (run - 1) / 2f;
                    float doorX = vertical ? fixed : middle;
                    float doorY = vertical ? middle : fixed;
                    best = new BayAperture(room.id(), doorX, doorY,
                            side[0], side[1], width);
                }
                run = 0;
            }
        }
        return best;
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
