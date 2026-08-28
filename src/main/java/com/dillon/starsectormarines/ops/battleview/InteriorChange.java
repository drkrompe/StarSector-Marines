package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What moving the company from one hull to another would do to their home.
 *
 * <p>The trade, made legible before it is committed. A screen that showed only
 * what a candidate gains would be a worse screen than none: the interesting
 * half is what a bigger ship <em>costs</em>, and hull collection is only a
 * decision at all because a larger hull can be a worse home. A mauled capital
 * may out-berth a sound frigate and still have no range and half its bays out,
 * and the player is entitled to see that before they move.
 *
 * <p>Absence and shrinkage are separate answers. Losing a firing range is a
 * different kind of loss from keeping one that berths fewer, and flattening
 * both into a single number would let a ship that has nowhere to shoot read as
 * merely a bit smaller.
 */
public record InteriorChange(ShipInterior from, ShipInterior to) {

    public InteriorChange {
        if (from == null || to == null) {
            throw new IllegalArgumentException("a change is between two ships");
        }
    }

    /** Places the company would have that they do not have now. */
    public List<RoomPurpose> gained() {
        return difference(to, from);
    }

    /** Places the company would no longer have anywhere aboard. */
    public List<RoomPurpose> lost() {
        return difference(from, to);
    }

    /** Places both ships owe, whether or not they hold the same. */
    public List<RoomPurpose> kept() {
        List<RoomPurpose> both = new ArrayList<>();
        for (RoomPurpose purpose : from.programmed()) {
            if (to.facility(purpose).programmed()) both.add(purpose);
        }
        return both;
    }

    /**
     * Every kind of place either ship has, so a comparison can be read as one
     * list with holes in it rather than three lists to reconcile.
     */
    public List<RoomPurpose> purposes() {
        Set<RoomPurpose> either = new LinkedHashSet<>(from.programmed());
        either.addAll(to.programmed());
        List<RoomPurpose> all = new ArrayList<>(either);
        all.sort(Comparator.comparingInt(RoomPurpose::ordinal));
        return all;
    }

    /** How much more of one kind of place the company would have, or less. */
    public int capacityChange(RoomPurpose purpose) {
        return to.facility(purpose).capacity() - from.facility(purpose).capacity();
    }

    /** How many more separate compartments of one kind, or fewer. */
    public int roomChange(RoomPurpose purpose) {
        return to.facility(purpose).rooms() - from.facility(purpose).rooms();
    }

    /** Whether the move would leave the company without somewhere they rely on. */
    public boolean costsSomething() {
        return !lost().isEmpty();
    }

    /**
     * Compared on what each ship <em>owes</em>, not on every room aboard. The
     * packer scatters lockers and cable trunks into whatever corners a deck
     * leaves over, and two hulls whose leftovers fell differently have not
     * traded a facility — reporting that as a loss would put noise in the one
     * list the player reads most carefully.
     */
    private static List<RoomPurpose> difference(ShipInterior has, ShipInterior lacks) {
        List<RoomPurpose> only = new ArrayList<>();
        for (RoomPurpose purpose : has.programmed()) {
            if (!lacks.facility(purpose).programmed()) only.add(purpose);
        }
        return only;
    }
}
