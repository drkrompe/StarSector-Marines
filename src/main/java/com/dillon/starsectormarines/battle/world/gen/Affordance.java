package com.dillon.starsectormarines.battle.world.gen;

/**
 * What a fixture invites a crew member to do at it.
 *
 * <p>This is the vocabulary that turns a furnished room into an inhabited one.
 * A route author never lists coordinates: it reads the affordances a
 * compartment placed and emits stops from them, so a bay that gained a bench
 * gains somewhere to work without anybody editing a route.
 *
 * <p>An affordance is a property of the <b>placement</b>, not of the art. The
 * same crate is stores in a hold and clutter in the gap between two bays, and
 * only one of those is somewhere a technician has business. Fittings decide
 * which; the registry only knows what a crate looks like.
 */
public enum Affordance {

    /** A bunk. Somewhere off watch is spent. */
    REST,

    /** A table. Somewhere a watch eats, in sittings. */
    MESS,

    /**
     * The galley: a range, a prep bench, a servery, the scullery behind it.
     * Somewhere the ship's food is made rather than eaten.
     *
     * <p>Distinct from {@link #MESS}, and the distinction is the whole reason
     * this exists. Eating is not work — it is what a watch does between shifts,
     * and a room affording it is somewhere people visit. Cooking is a trade,
     * done continuously, by hands posted to it. Folding the two together gave a
     * ship whose entire complement ate three meals a day that nobody made.
     *
     * <p>Nothing else aboard has this shape: the mess hall is the one
     * compartment that is a workplace and an amenity at once, for two different
     * populations, at the same moment.
     */
    COOK,

    /**
     * Stores handling: breaking down a pallet, restowing a rack, walking a part
     * from one stack to another. Alone among the affordances the work is
     * <em>between</em> points rather than at one, so a compartment with a single
     * stow point has nowhere to carry anything to.
     */
    STOW,

    /**
     * Work on the machine standing in a berth — welding, panels off, a fitter
     * underneath it. Bound to a berth rather than to a cell, because the work
     * only exists while something is parked there.
     */
    SERVICE,

    /** A terminal. Diagnostics read off a machine rather than hands laid on it. */
    READOUT,

    /** A bench or machine tool. Somewhere a part is made rather than drawn. */
    FABRICATE,

    /** A firing lane. Somewhere marines who would otherwise be idle shoot. */
    PRACTICE,

    /**
     * The heads. Somewhere everybody aboard goes, several times a watch, and
     * never far from where they sleep.
     *
     * <p>The most ordinary job on a ship and, for that reason, the one that does
     * most for how a deck reads: a berthing block's washroom is a few cells
     * away, so it fills the gap between the jobs that are a hundred cells away
     * and keeps a watch in the part of the ship it actually lives in.
     */
    WASH,

    /**
     * A treatment station or a ward bed. Somewhere a sick berth is kept, whether
     * or not there is anybody in it.
     *
     * <p>Distinct from {@link #REST}, which is a bunk. A bed in a ward is
     * somebody's <em>work</em> — it is checked, made up, and its readings taken
     * — and a ship keeps her sick berth ready rather than staffing it only when
     * a casualty arrives.
     */
    TREAT,

    /**
     * A console kept manned. The bridge watch, and the control room that watches
     * the machinery.
     *
     * <p>Distinct from {@link #READOUT}, which is a reading taken and acted on.
     * A watch station is occupied because it must be occupied; the point is the
     * continuous presence rather than the individual look.
     */
    WATCH,

    /**
     * The armoury counter. Weapons drawn and handed back, and the paperwork that
     * goes with them.
     */
    ISSUE,

    /**
     * Machinery kept running: the plant, the drive, the pumps and the pipework.
     *
     * <p>Distinct from {@link #SERVICE}, which is work on a machine standing in
     * a berth and exists only while something is parked there. This is the ship
     * herself, and it is there whether or not anything is embarked.
     */
    TEND,

    /**
     * A seat in the lounge. Somewhere to be that is not a bunk, a mess table or
     * a work station.
     *
     * <p>The one job whose content is that there is none, and the reason it is a
     * job at all: doing nothing has to have somewhere to be done, or the only
     * shape it can take is standing in a passage. Idleness a crew <em>chooses</em>
     * is worth having and reads as a ship people live on; idleness imposed on
     * them by a rotation with nowhere to go is the defect the whole model exists
     * to avoid, and the two look identical from outside unless one of them has a
     * room.
     */
    UNWIND,

    /**
     * A mat or a piece of gear in the gymnasium. Off watch, and the other half
     * of what a complement does when it is not working or asleep.
     */
    EXERCISE,

    /**
     * A compartment as it appears on somebody's rounds: looked into, checked,
     * and left.
     *
     * <p>Unlike every other job this is not <em>at</em> anything. What a fixture
     * affords is a reason to stand somewhere; what rounds afford is a reason to
     * be somewhere else next, and a ship is full of places that are worth
     * looking into and hold no work of their own.
     */
    ROUNDS,

    /**
     * A defect standing against a fixture: something aboard that is not right
     * and has not been got to yet.
     *
     * <p>Distinct from {@link #SERVICE} and {@link #TEND}, which are the work a
     * machine needs when nothing is wrong with it. A ship of any size always has
     * a list, and the list is what keeps a trade moving around the hull rather
     * than circling one compartment.
     */
    REPAIR;

    /**
     * Whether doing this is work, as opposed to what somebody does when they
     * are not working.
     *
     * <p>The distinction decides where a watch is <em>posted</em>, not what it
     * does. A watch is posted where its work is; eating, sleeping and keeping
     * one's shooting in are things people go and do between shifts, and a room
     * that affords them is somewhere they visit rather than somewhere they are
     * stationed. Without this, a compartment offering any job a role does reads
     * as a billet for that role, and a ship acquires a watch of marines who
     * live in the galley.
     */
    public boolean duty() {
        return switch (this) {
            case SERVICE, FABRICATE, STOW, READOUT, TREAT, WATCH, ISSUE, TEND,
                    ROUNDS, REPAIR, COOK -> true;
            case REST, MESS, PRACTICE, WASH, UNWIND, EXERCISE -> false;
        };
    }
}
