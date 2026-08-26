package com.dillon.starsectormarines.battle.world.gen.ship;

import java.util.ArrayList;
import java.util.List;

/**
 * How big a ship's deck is.
 *
 * <p>A deck is <b>as large as the rooms it must contain</b>, shaped like the
 * hull containing them. Nothing here picks a size and then fills it: the ship's
 * complement, hold, and class decide which rooms it owes, each
 * {@link RoomRecipe} declares its own footprint, and the deck comes out big
 * enough to pack that program at the hull's own proportions. A ship with a mech
 * bay is necessarily longer than one without, because the bay is forty frames
 * whatever else is true of the hull.
 *
 * <p>Crew alone is a poor proxy for size. A vanilla Atlas carries about the same
 * crew as a Hammerhead and is an enormously larger ship; the difference is two
 * thousand units of hold against one hundred. Complement wants berthing and the
 * spaces serving it, hold wants stowage, and both are walkable during a boarding
 * action, so both count.
 *
 * <p>{@link #DENSITY} is the dial. It scales how many cells a given hull is
 * divided into without touching the program or the hull's shape — turn it up and
 * the packing has slack for passages and pockets, turn it down and the ship
 * tightens until rooms stop fitting. It is the first thing to reach for when
 * decks feel cramped or bloated.
 */
public final class DeckSizing {

    /**
     * Cell-density multiplier applied to every deck. Purely a feel dial: 1.0
     * sizes a deck to its program exactly, above that gives the packer slack.
     */
    public static final float DENSITY = 1.15f;

    /** Of a deck's bounding rectangle, the share a tapered hull actually encloses. */
    private static final float HULL_FILL = 0.62f;
    /**
     * Multiplier on raw room area covering the spine, the passages branching off
     * it, every room's bulkheads, and the packing waste a shape-fitter always
     * leaves. Higher than a partitioner would need, because a packer cannot use
     * every cell it is given and a deck with nowhere to run a passage is worse
     * than a slightly loose one.
     */
    private static final float CIRCULATION = 1.75f;

    private DeckSizing() {}

    /** One ship's deck: its dimensions and the rooms it owes. */
    public record DeckPlan(int frames, int height, List<RoomRecipe> rooms) {

        public DeckPlan {
            rooms = List.copyOf(rooms);
        }

        public int deckArea() {
            return frames * height;
        }

        /** Total floor the rooms themselves claim, before bulkheads and circulation. */
        public int roomArea() {
            int total = 0;
            for (RoomRecipe room : rooms) total += room.area();
            return total;
        }
    }

    /**
     * Size a ship's deck from its class, complement, hold, and hull shape.
     *
     * @param hullClass the hull's size class
     * @param maxCrew maximum crew complement; a vanilla hull's {@code max crew}
     * @param cargo hold capacity; a vanilla hull's {@code cargo}
     * @param aspect the hull's beam over its length; drives the deck's proportions
     */
    public static DeckPlan planFor(HullClass hullClass, int maxCrew, int cargo, float aspect) {
        if (!hullClass.boardable()) return new DeckPlan(0, 0, List.of());

        List<RoomRecipe> rooms = programFor(hullClass, maxCrew, cargo);

        int roomArea = 0;
        for (RoomRecipe room : rooms) roomArea += room.area();
        float needed = roomArea * CIRCULATION * DENSITY * DENSITY;

        // Solve frames x (frames * aspect) * fill = needed, so the deck keeps the
        // hull's drawn proportions while holding everything the ship owes.
        float shape = Math.max(0.08f, aspect);
        int frames = Math.round((float) Math.sqrt(needed / (shape * HULL_FILL)));
        int height = Math.round(frames * shape);

        return new DeckPlan(Math.max(frames, minimumFrames(rooms)),
                Math.max(height, minimumHeight(rooms)), rooms);
    }

    /**
     * The rooms a ship owes. Berthing and stowage scale with what the hull
     * carries; command and engineering are owed once because a ship needs them
     * at all; a bay appears only on hulls large enough to service heavy assets.
     */
    public static List<RoomRecipe> programFor(HullClass hullClass, int maxCrew, int cargo) {
        List<RoomRecipe> rooms = new ArrayList<>();
        rooms.add(RoomRecipe.COMMAND);
        add(rooms, RoomRecipe.BERTHING, RoomRecipe.BERTHING.countFor(maxCrew));
        add(rooms, RoomRecipe.ARMORY, RoomRecipe.ARMORY.countFor(maxCrew));
        if (cargo > 0) add(rooms, RoomRecipe.HOLD, RoomRecipe.HOLD.countFor(cargo));
        add(rooms, RoomRecipe.ENGINEERING,
                hullClass.ordinal() >= HullClass.CRUISER.ordinal() ? 2 : 1);
        if (hullClass.carriesHeavyAssets()) rooms.add(RoomRecipe.VEHICLE_BAY);
        return rooms;
    }

    /** A deck must at least fit its longest room lengthwise, with hull taper either side. */
    private static int minimumFrames(List<RoomRecipe> rooms) {
        int longest = 0;
        for (RoomRecipe room : rooms) {
            longest = Math.max(longest, Math.max(room.shape().width(), room.shape().height()));
        }
        return longest + 12;
    }

    /**
     * A deck must at least fit its deepest room either side of the spine. A room
     * may be turned, so what has to fit is the shorter side of its footprint.
     */
    private static int minimumHeight(List<RoomRecipe> rooms) {
        int deepest = 0;
        for (RoomRecipe room : rooms) deepest = Math.max(deepest, room.shape().minExtent());
        return deepest * 2 + 10;
    }

    private static void add(List<RoomRecipe> rooms, RoomRecipe recipe, int count) {
        for (int i = 0; i < count; i++) rooms.add(recipe);
    }
}
