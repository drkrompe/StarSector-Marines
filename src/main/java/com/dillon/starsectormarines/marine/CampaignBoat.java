package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.io.Serializable;

/**
 * One persistent, company-owned ship's boat: a stable id, a tail name, the
 * pattern it is built on, and one fitting in each of its slots.
 *
 * <p>The campaign authority. It freezes a {@link FittedBoat} for a battle the
 * way a {@link CampaignMech} freezes a deployment spec, and the sortie flying
 * that frame never reaches back here — a boat shot down over a compound is a
 * campaign consequence somebody has to write, not a field this object quietly
 * loses.
 *
 * <p>Fittings are held by id rather than by reference because this is
 * persisted: a catalog entry that is renamed or retired then reads as the
 * standard fitting through {@link BoatFitting#resolve}, which is a plainer boat
 * rather than a save that will not load.
 */
public final class CampaignBoat implements Serializable {

    private final String id;
    private String displayName;
    private final ShuttleType pattern;
    private String platingId;
    private String driveId;

    public CampaignBoat(String id, String displayName, ShuttleType pattern,
                        String platingId, String driveId) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Boat id is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Boat name is required");
        }
        if (pattern == null) throw new IllegalArgumentException("Boat pattern is required");
        this.id = id;
        this.displayName = displayName;
        this.pattern = pattern;
        this.platingId = BoatFitting.resolve(platingId, BoatFittingSlot.PLATING).id();
        this.driveId = BoatFitting.resolve(driveId, BoatFittingSlot.DRIVE).id();
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    public ShuttleType pattern() { return pattern; }

    public BoatFitting plating() {
        return BoatFitting.resolve(platingId, BoatFittingSlot.PLATING);
    }

    public BoatFitting drive() {
        return BoatFitting.resolve(driveId, BoatFittingSlot.DRIVE);
    }

    /** What is installed in one slot. Never null — an empty slot is the standard fit. */
    public BoatFitting fittingIn(BoatFittingSlot slot) {
        return slot == BoatFittingSlot.DRIVE ? drive() : plating();
    }

    /** The frame this boat flies as, with its fittings applied. */
    public FittedBoat freezeForDeployment() {
        return new FittedBoat(pattern, plating(), drive());
    }

    /**
     * Installs a fitting into its own slot. Package-private: the atomic
     * authority is {@link BoatWorkshop}, and a caller that reached past it
     * would be a refit nobody paid for.
     */
    void install(BoatFitting fitting) {
        if (fitting == null) return;
        if (fitting.slot() == BoatFittingSlot.DRIVE) driveId = fitting.id();
        else platingId = fitting.id();
    }
}
