package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * Where one particular hull keeps her rooms: an authored stretch of hull and a
 * side for each compartment, read by the packing as where the room belongs.
 *
 * <p>A zone is the generic answer: berths forward, volume amidships, plant
 * aft. A hull that has been drawn has a better one. Second Wind's Blender model
 * cuts her two boat bays through the waist, one to each flank, and a deck that
 * packs both bays into her aft port corner shows the player a ship that is not
 * the one in her renders. An arrangement says where the drawn ship has them.
 *
 * <p><b>The packing is not replaced.</b> A site is only an opinion about
 * position, at the same weight a zone carries: the room is still fitted by
 * {@link RoomPacker}, its doors and passages still cut the way every deck's
 * are, and a room its site cannot hold is laid wherever it fits rather than
 * left off the deck. Positions are fractions of the deck's length, so the
 * arrangement survives a change to the program that resizes the deck.
 *
 * <p>Sites are matched to rooms by purpose: the first site named for a purpose
 * goes to the first room of that purpose to be placed, and a purpose with more
 * rooms than sites falls back to its zone for the rest. Rooms are laid in the
 * order their purposes first appear here rather than largest first, so the
 * rooms the drawing fixes are down before the rest look for space.
 *
 * <p><b>Reserves</b> are compartments the hull keeps sealed for refits she has
 * not had yet. They are no part of the room program: the deck is not sized for
 * them, nothing aboard is owed them, and they are laid after every programmed
 * room and before the pocket fill, so reserving one never moves a room, a door
 * or a berth the program placed. Paying for a refit later opens a reserve where
 * it already stands rather than finding it a place.
 */
public record DeckArrangement(String name, List<Site> sites, List<Fairing> fairings, List<Reserve> reserves) {

    public DeckArrangement {
        sites = List.copyOf(sites);
        fairings = List.copyOf(fairings);
        reserves = List.copyOf(reserves);
    }

    public DeckArrangement(String name, List<Site> sites, List<Fairing> fairings) {
        this(name, sites, fairings, List.of());
    }

    /**
     * A compartment reserved for a future refit: the room it will become, and
     * where on the deck it is kept. A reserve its site cannot hold falls back to
     * its recipe's zone the way a programmed room does.
     */
    public record Reserve(RoomRecipe recipe, Site site) {
        public Reserve {
            if (site.purpose() != recipe.purpose()) {
                throw new IllegalArgumentException("a reserve's site names its own purpose: " + site + " for " + recipe);
            }
        }
    }

    /**
     * A stretch of flank the drawn hull runs straight, between {@code fore} and
     * {@code aft} as fractions of the deck's length from the bow.
     *
     * <p>A collision polygon is a handful of corners, and between two of them
     * it runs a diagonal the hull itself does not have. Where that diagonal
     * crosses the stretch a bay is cut through, no bay can stand flush with the
     * plating: its opening needs the whole of its outboard bulkhead to face
     * space, and a slanted flank leaves one end of it inboard. A fairing lays
     * the flank straight at its narrowest across the stretch, which takes deck
     * away rather than inventing hull that is not there.
     */
    public record Fairing(float fore, float aft, DeckSide side) {
        public Fairing {
            if (fore < 0f || aft > 1f || aft < fore) {
                throw new IllegalArgumentException("a fairing spans the deck bow to stern: " + fore + ".." + aft);
            }
        }

        /** First frame of the stretch on a deck {@code frames} long. */
        public int first(int frames) {
            return Math.round(fore * (frames - 1));
        }

        /** Last frame of the stretch on a deck {@code frames} long. */
        public int last(int frames) {
            return Math.round(aft * (frames - 1));
        }
    }

    /**
     * One room's place: its centre lies between {@code fore} and {@code aft},
     * as fractions of the deck's length from the bow, on {@code side} of the
     * spine. {@code fore == aft} pins the centre to one frame, which is how two
     * rooms meant to stand abreast are made to.
     */
    public record Site(RoomPurpose purpose, float fore, float aft, DeckSide side) {
        public Site {
            if (fore < 0f || aft > 1f || aft < fore) {
                throw new IllegalArgumentException("a site spans the deck bow to stern: " + fore + ".." + aft);
            }
        }

        /** Whether a room centred on this cell stands in this site. */
        public boolean holds(DeckProfile profile, int centreX, int centreY) {
            int frames = profile.frames();
            int first = Math.round(fore * (frames - 1));
            int last = Math.round(aft * (frames - 1));
            if (centreX < first || centreX > last) return false;
            int spineCentre = (profile.spineTop() + profile.spineBottom()) / 2;
            DeckSide at = centreY < spineCentre ? DeckSide.PORT : DeckSide.STARBOARD;
            return at == side;
        }
    }

    /**
     * Where rooms of this purpose come in the arrangement's laying order: the
     * index of the first site named for it, or after every sited purpose for a
     * purpose the arrangement does not place.
     */
    public int rank(RoomPurpose purpose) {
        for (int i = 0; i < sites.size(); i++) {
            if (sites.get(i).purpose() == purpose) return i;
        }
        return sites.size();
    }

    /**
     * Hands sites out as rooms are placed. One per deck generation, because
     * which site a room gets depends on how many of its purpose went before it.
     */
    public Allocation allocate() {
        return new Allocation(new ArrayList<>(sites));
    }

    public static final class Allocation {
        private final List<Site> open;

        private Allocation(List<Site> open) {
            this.open = open;
        }

        /** The next site named for this purpose, or null when it has none left. */
        public Site next(RoomPurpose purpose) {
            for (int i = 0; i < open.size(); i++) {
                if (open.get(i).purpose() == purpose) return open.remove(i);
            }
            return null;
        }
    }

    /** Frame centred at {@code frame} of a deck {@code frames} long, as a site fraction. */
    private static float at(int frame, int frames) {
        return frame / (float) (frames - 1);
    }

    private static Site span(RoomPurpose purpose, float fore, float aft, DeckSide side) {
        return new Site(purpose, fore, aft, side);
    }

    private static Reserve reserve(RoomRecipe recipe, float fore, float aft, DeckSide side) {
        return new Reserve(recipe, span(recipe.purpose(), fore, aft, side));
    }

    /**
     * Second Wind, the company's original Cerberus, laid out to agree with her
     * model.
     *
     * <p>The bays come first because the model fixes them: both lateral
     * apertures are cut through the waist, centred about 43% of her length
     * from the bow, the open one to port and the sealed one to starboard. Both
     * bays are pinned to that one frame so they stand abreast, which also keeps
     * the port bay first in fore-to-aft order — it is the original, working
     * bay, and berth order has always put it first.
     *
     * <p>The rest follows from the bays and from what the model shows around
     * them. The forebody is where people live: the bridge in the bow, the
     * company's troop berths to port under the grapple sponson with the cargo
     * the grapple handles, and the ship's own hands to starboard. The waist is
     * where the company goes ashore from: the armory inboard of the working
     * bay and the mess across the spine from it. The broad aft block is
     * machinery, with the Mech Lab hard abaft the working bay so a machine
     * walks from its gantry to its boat, the machine shop and stores behind
     * it, and the plant and the drive to starboard on the transom, under the
     * reactor hatch.
     *
     * <p>Five reserves follow the program, largest first, which is the order
     * that leaves each its best ground: the range along the spine in the
     * starboard waist, the gymnasium in the aft port corner, the sick bay, the
     * lounge and the brig in the starboard aft block. Their footprints are what
     * the deck can give up in every layout rather than the destroyer recipes:
     * measured over two hundred layout seeds, a 30-frame range fits one deck
     * in five and a 14-by-10 room more than once only by luck, while this set
     * places on every one. See {@code second-wind-refit-tree.md}.
     */
    public static final DeckArrangement SECOND_WIND = new DeckArrangement("second-wind", List.of(
            span(RoomPurpose.HANGAR, at(47, 106), at(47, 106), DeckSide.PORT),
            span(RoomPurpose.HANGAR, at(47, 106), at(47, 106), DeckSide.STARBOARD),
            span(RoomPurpose.ARMORY, 0.38f, 0.52f, DeckSide.PORT),
            span(RoomPurpose.STOCKROOM, 0.38f, 0.52f, DeckSide.STARBOARD),
            span(RoomPurpose.ENGINE_ROOM, 0.80f, 1f, DeckSide.STARBOARD),
            span(RoomPurpose.VEHICLE_BAY, 0.64f, 0.75f, DeckSide.PORT),
            span(RoomPurpose.PRODUCTION_FLOOR, 0.60f, 0.72f, DeckSide.STARBOARD),
            span(RoomPurpose.PARTS_CAGE, 0.60f, 0.80f, DeckSide.STARBOARD),
            span(RoomPurpose.MESS_HALL, 0.62f, 0.82f, DeckSide.PORT),
            span(RoomPurpose.LOADING_BAY, 0.80f, 0.97f, DeckSide.PORT),
            span(RoomPurpose.BRIDGE, 0.03f, 0.20f, DeckSide.STARBOARD),
            span(RoomPurpose.BARRACKS, 0.04f, 0.32f, DeckSide.PORT),
            span(RoomPurpose.BARRACKS, 0.04f, 0.32f, DeckSide.PORT),
            span(RoomPurpose.BARRACKS, 0.04f, 0.32f, DeckSide.PORT),
            span(RoomPurpose.BARRACKS, 0.04f, 0.32f, DeckSide.PORT),
            span(RoomPurpose.BARRACKS, 0.04f, 0.32f, DeckSide.PORT),
            span(RoomPurpose.BARRACKS, 0.04f, 0.32f, DeckSide.PORT),
            span(RoomPurpose.CREW_QUARTERS, 0.12f, 0.32f, DeckSide.STARBOARD),
            span(RoomPurpose.CREW_QUARTERS, 0.12f, 0.32f, DeckSide.STARBOARD),
            span(RoomPurpose.CREW_QUARTERS, 0.12f, 0.32f, DeckSide.STARBOARD),
            span(RoomPurpose.WASHROOM, 0.04f, 0.32f, DeckSide.PORT),
            span(RoomPurpose.WASHROOM, 0.04f, 0.32f, DeckSide.STARBOARD)),
            // The waist, x 0 to 27.5 on the model, is straight-sided at ±22.5
            // where both apertures are cut; the collision polygon runs it as
            // a diagonal from the pods to the engine block.
            List.of(new Fairing(0.31f, 0.59f, DeckSide.PORT),
                    new Fairing(0.31f, 0.59f, DeckSide.STARBOARD)),
            List.of(reserve(RoomRecipe.SECOND_WIND_RANGE, 0.44f, 0.66f, DeckSide.STARBOARD),
                    reserve(RoomRecipe.SECOND_WIND_GYM, 0.76f, 1f, DeckSide.PORT),
                    reserve(RoomRecipe.SECOND_WIND_SICK_BAY, 0.66f, 1f, DeckSide.STARBOARD),
                    reserve(RoomRecipe.SECOND_WIND_LOUNGE, 0.66f, 1f, DeckSide.STARBOARD),
                    reserve(RoomRecipe.BRIG, 0.44f, 0.80f, DeckSide.STARBOARD)));
}
