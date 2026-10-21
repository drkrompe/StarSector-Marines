package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.fit.BrigFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.ChapelLoungeFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.FiringRangeFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RehabSickBayFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.GrandGymFitting;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * One of Second Wind's sealed refit compartments: the facility it becomes once
 * a refit opens it, at the footprint her deck can spare in every layout.
 *
 * <p>The deck lays and furnishes every reserve the same way whether it is open
 * or not, so opening one never moves a room, a door or a berth; what differs is
 * only whether {@code ReservedFacilityStage} strips and seals it. Which ones are
 * open is a saved fact about the hull, carried in as
 * {@link ShipRestoration#openReserves()}. See {@code second-wind-refit-tree.md}.
 *
 * <p>Each reserve's purpose is unique among the hull's reserves, which is how a
 * compartment the deck marks reserved is read back as its facility.
 */
public enum ReservedFacility {
    /** Three short lanes along the spine; practice at the firing line, shut deck downrange. */
    FIRING_RANGE(RoomRecipe.SECOND_WIND_RANGE),
    /** A running track round the bulkheads, the equipment floor inside it, and a pool. */
    GYMNASIUM(RoomRecipe.SECOND_WIND_GYM),
    /** A short ward and its clinic beside a rehab pool. */
    SICK_BAY(RoomRecipe.SECOND_WIND_SICK_BAY),
    /** Off-watch seating and a card table, with a chapel at the quiet end. */
    REC_LOUNGE(RoomRecipe.SECOND_WIND_LOUNGE),
    /** Two holding cells and the guard post outside them. */
    BRIG(RoomRecipe.BRIG);

    private final RoomRecipe recipe;

    ReservedFacility(RoomRecipe recipe) {
        this.recipe = recipe;
    }

    /** The compartment this facility is reserved as. */
    public RoomRecipe recipe() {
        return recipe;
    }

    public RoomPurpose purpose() {
        return recipe.purpose();
    }

    /**
     * How this facility is furnished at its reserved footprint. Used for the
     * fill only: where the compartment goes and where its doors are cut was
     * decided by the room's ordinary fitting, and stays that way.
     */
    public RoomFitting fitting() {
        return switch (this) {
            case FIRING_RANGE -> FiringRangeFitting.shipboard();
            case GYMNASIUM -> new GrandGymFitting();
            case SICK_BAY -> new RehabSickBayFitting();
            case REC_LOUNGE -> new ChapelLoungeFitting();
            case BRIG -> new BrigFitting();
        };
    }

    /** The reserve kept as a compartment of this purpose, or null where none is. */
    public static ReservedFacility of(RoomPurpose purpose) {
        for (ReservedFacility facility : values()) if (facility.purpose() == purpose) return facility;
        return null;
    }
}
