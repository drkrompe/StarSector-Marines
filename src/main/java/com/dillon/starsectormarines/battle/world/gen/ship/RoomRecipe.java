package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * The authored recipe for one kind of shipboard room: what it is, what shape it
 * takes, where along the hull it belongs, and what it provides.
 *
 * <p>Rooms are the unit that sizes a ship. A mech bay is large because servicing
 * a walker needs a gantry and the clearance around it; a berth is as small as
 * the bunks and the aisle between them. A deck is therefore as big as the rooms
 * it must contain, rather than a figure chosen up front and then subdivided.
 *
 * <p>A cell reads as roughly a metre, which is what makes these numbers
 * checkable against a real vessel rather than tuned until the picture looks
 * busy. {@link #BERTHING} is twelve bunks in forty-eight square metres — four
 * per hand, which is spartan on purpose.
 *
 * <p>The {@link RoomShape} is a mask, not a rectangle and not an orientation.
 * {@link #COMMAND} is a diamond because a bridge is not a box; a range will be
 * an L. The placer turns a shape freely, so a berth laid across the beam to fill
 * a pocket is the same room as one laid along the hull.
 *
 * <p>{@link #zone} is the stretch of hull the room belongs in, or null for the
 * small rooms that simply take whatever the packing left over.
 *
 * <p>{@link #provides} is the capacity one room of this kind supplies, in
 * whatever unit its purpose implies — bunks for a berth, hold units for a
 * stockroom, serviced hulls for a vehicle bay. Zero means the room earns its
 * place by function rather than by capacity, like a command centre.
 */
public record RoomRecipe(RoomPurpose purpose, RoomShape shape, DeckZone zone,
                        int provides, boolean hullAccess) {

    /** A room that only needs to be somewhere on the deck. */
    public RoomRecipe(RoomPurpose purpose, RoomShape shape, DeckZone zone, int provides) {
        this(purpose, shape, zone, provides, false);
    }

    /**
     * Bunks and living space for a watch of twelve. Two rows of stacked bunks
     * either side of a single aisle, with a locker run at one end and nothing
     * else — a ship berths its crew in many small compartments spread through
     * the hull, not in one dormitory.
     *
     * <p>Berthed forward, which is both the traditional arrangement and the
     * practical one here: berths are the smallest rooms and the most numerous,
     * so they are what can fill a tapering bow that no bay or hold would fit.
     */
    public static final RoomRecipe BERTHING = new RoomRecipe(
            RoomPurpose.BARRACKS, RoomShape.rectangle(8, 6), DeckZone.FORE, 12);

    /** Gantry space for servicing heavy assets; the largest room a ship carries. */
    public static final RoomRecipe VEHICLE_BAY = new RoomRecipe(
            RoomPurpose.VEHICLE_BAY, RoomShape.rectangle(40, 16), DeckZone.MIDSHIPS, 4);

    /**
     * Racked weapons and an issue counter. A ship keeps one, or a second on a
     * very large complement — never one per hundred hands.
     */
    public static final RoomRecipe ARMORY = new RoomRecipe(
            RoomPurpose.ARMORY, RoomShape.rectangle(12, 8), DeckZone.MIDSHIPS, 400);

    /**
     * Bridge or combat information centre, forward, and one per deck at most.
     * Cut as a chamfered diamond so the watch stands around a plot rather than
     * along a wall — and, less romantically, as the standing proof that the
     * packer handles a shape that is not a rectangle.
     */
    public static final RoomRecipe COMMAND = new RoomRecipe(
            RoomPurpose.CONTROL_ROOM, RoomShape.of(
                    ".....###.....",
                    "...#######...",
                    "..#########..",
                    ".###########.",
                    "#############",
                    "#############",
                    "#############",
                    ".###########.",
                    "..#########..",
                    "...#######...",
                    ".....###....."),
            DeckZone.FORE, 0);

    /**
     * Boat bay, and the deck's only way in or out under its own power. Troops
     * embark here for the surface and come back through it, so its capacity is
     * a lift cycle rather than a headcount that lives aboard.
     *
     * <p>The one room that must reach the hull. A bay buried amidships opens
     * onto nothing, so this is where {@link #hullAccess} earns its place: the
     * placer has to find it a wall of the ship, not merely a wall.
     */
    public static final RoomRecipe SHUTTLE_BAY = new RoomRecipe(
            RoomPurpose.HANGAR, RoomShape.rectangle(28, 16), DeckZone.MIDSHIPS, 120, true);

    /**
     * The single boat a small hull carries, and its bay. A frigate has a gig,
     * not a hangar deck; giving her the full bay put a room the size of her
     * machinery spaces amidships and left no ship around it.
     */
    public static final RoomRecipe BOAT_BAY = new RoomRecipe(
            RoomPurpose.HANGAR, RoomShape.rectangle(16, 10), DeckZone.MIDSHIPS, 40, true);

    /** Bulk hold. Large and sparse inside. */
    public static final RoomRecipe HOLD = new RoomRecipe(
            RoomPurpose.STOCKROOM, RoomShape.rectangle(18, 12), DeckZone.MIDSHIPS, 250);

    /**
     * Mess and galley. Sized by sittings rather than by heads: a ship feeds its
     * complement in watches, so the room holds a fraction of the crew at once
     * and does it several times a day.
     */
    public static final RoomRecipe MESS = new RoomRecipe(
            RoomPurpose.MESS_HALL, RoomShape.rectangle(22, 12), DeckZone.MIDSHIPS, 90);

    /**
     * Small-arms range: a bank of lanes with a ready area off one end, which is
     * why it is an L and not a box. Marines who never shoot are marines who
     * cannot, and a transport carrying a landing force wants more than one.
     */
    public static final RoomRecipe RANGE = new RoomRecipe(
            RoomPurpose.FIRING_RANGE, RoomShape.of(
                    "##############################",
                    "##############################",
                    "##############################",
                    "##############################",
                    "##########....................",
                    "##########....................",
                    "##########....................",
                    "##########...................."),
            DeckZone.MIDSHIPS, 200);

    /** Sick bay: treatment and a ward, amidships where it can be reached from either end. */
    public static final RoomRecipe SICK_BAY = new RoomRecipe(
            RoomPurpose.PATIENT_WARD, RoomShape.rectangle(14, 10), DeckZone.MIDSHIPS, 200);

    /** Briefing room, forward by the bridge. Where a patron's job stops being a rumour. */
    public static final RoomRecipe BRIEFING = new RoomRecipe(
            RoomPurpose.CONFERENCE_ROOM, RoomShape.rectangle(14, 10), DeckZone.FORE, 0);

    /**
     * Heads and washroom. Small, numerous, and tied to the berthing they serve,
     * which makes them the authored rooms best suited to the awkward pockets a
     * block of berths leaves behind.
     */
    public static final RoomRecipe WASHROOM = new RoomRecipe(
            RoomPurpose.WASHROOM, RoomShape.rectangle(6, 5), DeckZone.FORE, 60);

    /** Machine shop aft, where a part gets made rather than drawn from a cage. */
    public static final RoomRecipe MACHINE_SHOP = new RoomRecipe(
            RoomPurpose.PARTS_CAGE, RoomShape.rectangle(14, 10), DeckZone.AFT, 0);

    /**
     * Power, drive, and life support machinery, aft against the engines. Scales
     * with the complement rather than sitting at one per ship: keeping four
     * hundred people alive and moving takes more plant than keeping thirty
     * alive, and machinery is most of what a stern is.
     */
    public static final RoomRecipe ENGINEERING = new RoomRecipe(
            RoomPurpose.PRODUCTION_FLOOR, RoomShape.rectangle(20, 14), DeckZone.AFT, 150);

    /**
     * Provisions and consumable stores, struck down aft by the machinery they
     * feed. Distinct from the cargo hold: this is what the ship eats on the way,
     * not what she is carrying for somebody else.
     */
    public static final RoomRecipe PROVISIONS = new RoomRecipe(
            RoomPurpose.LOADING_BAY, RoomShape.rectangle(18, 12), DeckZone.AFT, 200);

    /**
     * The small rooms that exist because the packing left somewhere to put
     * them: lockers, cable trunks, a pump room, a spares cage. They have no
     * zone, are never owed by the program, and are fitted last into whatever
     * pockets the authored rooms did not want — which is what stops a tightly
     * packed deck from reading as rooms plus leftovers.
     */
    public static final List<RoomRecipe> UTILITY = List.of(
            new RoomRecipe(RoomPurpose.PARTS_CAGE, RoomShape.rectangle(6, 4), null, 0),
            new RoomRecipe(RoomPurpose.SERVER_ROOM, RoomShape.rectangle(5, 4), null, 0),
            new RoomRecipe(RoomPurpose.STOCKROOM, RoomShape.rectangle(4, 4), null, 0),
            new RoomRecipe(RoomPurpose.GENERIC, RoomShape.rectangle(3, 3), null, 0));

    /** Cells of floor this room claims, excluding its bulkheads. */
    public int area() {
        return shape.area();
    }

    /** How many rooms of this kind are needed to supply {@code demand}. */
    public int countFor(int demand) {
        if (provides <= 0) return 1;
        return Math.max(1, (int) Math.ceil((double) demand / provides));
    }
}
