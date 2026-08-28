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
    PRACTICE;

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
            case SERVICE, FABRICATE, STOW, READOUT -> true;
            case REST, MESS, PRACTICE -> false;
        };
    }
}
