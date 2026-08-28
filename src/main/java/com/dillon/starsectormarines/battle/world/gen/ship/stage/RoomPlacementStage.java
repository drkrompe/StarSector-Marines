package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.gen.ship.HullContact;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;
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

    @Override
    public void run(GenContext ctx) {
        DeckProfile profile = ctx.get(ShipKeys.DECK_PROFILE);
        List<RoomRecipe> program = ctx.get(ShipKeys.ROOM_PROGRAM);
        if (profile == null || program == null) {
            throw new IllegalStateException(
                    "RoomPlacementStage requires a deck profile and a room program");
        }

        RoomPacker packer = new RoomPacker(ctx, hullMask(ctx, profile),
                spineMask(ctx, profile), DECK_PALETTE);

        List<RoomRecipe> ordered = new ArrayList<>(program);
        ordered.sort(Comparator.comparingInt(RoomRecipe::area).reversed()
                .thenComparing(recipe -> recipe.purpose().name()));

        List<DeckGraph.Compartment> placed = new ArrayList<>();
        List<RoomRecipe> unplaced = new ArrayList<>();
        for (RoomRecipe recipe : ordered) {
            RoomPacker.Placed room = packer.place(request(profile, recipe), true);
            if (room == null) {
                unplaced.add(recipe);
            } else {
                placed.add(describe(profile, room, placed.size()));
            }
        }
        fillPockets(profile, packer, placed);
        ctx.put(ShipKeys.DECK_GRAPH, new DeckGraph(placed, unplaced));
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
                RoomPacker.Placed room = packer.place(request(profile, recipe), false);
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
    private static RoomPacker.Request request(DeckProfile profile, RoomRecipe recipe) {
        RoomPacker.Affinity affinity = recipe.zone() == null
                ? RoomPacker.Affinity.ANYWHERE
                : (centreX, centreY) -> profile.zone(clampFrame(profile, centreX)) == recipe.zone();
        return new RoomPacker.Request(recipe.purpose(), recipe.shape(), affinity,
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
