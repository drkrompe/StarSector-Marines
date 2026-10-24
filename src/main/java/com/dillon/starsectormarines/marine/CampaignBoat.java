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
    /** Null on saves from before the lift slot: reads as the standard hold. */
    private String liftId;
    /**
     * The door gun on each hardpoint, by {@link BoatGun} id. Null, or a null or
     * unknown entry, reads as the hardpoint's yard-standard gun — which is what
     * every boat saved before hardpoints could be refitted is carrying.
     */
    private String[] gunIds;
    private float repairUntilDay;

    /** Zero on legacy saves. Recovery is read from campaign time, not screen visits. */
    public float repairUntilDay() { return repairUntilDay; }
    public float repairDaysRemaining(float day) { return Math.max(0f, repairUntilDay - day); }
    public boolean airworthy(float day) { return repairDaysRemaining(day) == 0f; }
    public float condition(float day) { return 1f - Math.min(1f, repairDaysRemaining(day) / 2f); }

    void damage(float fraction, float day) {
        if (!Float.isFinite(fraction) || !Float.isFinite(day) || fraction <= 0f) return;
        repairUntilDay = day + 2f * Math.min(1f, fraction);
    }

    void finishRepairs() { repairUntilDay = 0f; }


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
        this.liftId = BoatFitting.STANDARD_LIFT.id();
    }

    public CampaignBoat(String id, String displayName, ShuttleType pattern,
                        String platingId, String driveId, String liftId) {
        this(id, displayName, pattern, platingId, driveId);
        BoatFitting lift = BoatFitting.resolve(liftId, BoatFittingSlot.LIFT);
        this.liftId = lift.supports(pattern) ? lift.id() : BoatFitting.STANDARD_LIFT.id();
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

    /**
     * The hold. A boat saved before the lift slot existed has no id here and
     * reads as the standard hold, which is what it was built with.
     */
    public BoatFitting lift() {
        return BoatFitting.resolve(liftId, BoatFittingSlot.LIFT);
    }

    /** What is installed in one slot. Never null — an empty slot is the standard fit. */
    public BoatFitting fittingIn(BoatFittingSlot slot) {
        return switch (slot) {
            case DRIVE -> drive();
            case LIFT -> lift();
            case PLATING -> plating();
        };
    }

    /**
     * The frame this boat flies as, with its fittings applied.
     *
     * <p>Carries the boat's id so a shoot-down can be traced back here at
     * resolution. Nothing in the battle reads it — an air loss simply remembers
     * what it was flying as, and the campaign works out afterwards whose it was.
     */
    public FittedBoat freezeForDeployment() {
        String[] guns = new String[pattern.hardpoints()];
        for (int index = 0; index < guns.length; index++) {
            BoatGun gun = gunOn(index);
            guns[index] = gun != null ? gun.structureId() : null;
        }
        return new FittedBoat(pattern, plating(), drive(), lift(), id, guns);
    }

    /**
     * The door gun on hardpoint {@code index}, or null for a hardpoint the
     * pattern does not have. A hardpoint nobody refitted carries the yard's
     * own gun.
     */
    public BoatGun gunOn(int index) {
        if (index < 0 || index >= pattern.hardpoints()) return null;
        BoatGun fitted = gunIds != null && index < gunIds.length ? BoatGun.findById(gunIds[index]) : null;
        return fitted != null ? fitted : BoatGun.standardFor(pattern, index);
    }

    /** Puts a gun on one hardpoint. Package-private for the reason {@link #install} is. */
    void installGun(int index, BoatGun gun) {
        if (gun == null || index < 0 || index >= pattern.hardpoints()) return;
        if (gunIds == null || gunIds.length != pattern.hardpoints()) {
            String[] grown = new String[pattern.hardpoints()];
            if (gunIds != null) System.arraycopy(gunIds, 0, grown, 0, Math.min(gunIds.length, grown.length));
            gunIds = grown;
        }
        gunIds[index] = gun.id();
    }

    /**
     * Installs a fitting into its own slot. Package-private: the atomic
     * authority is {@link BoatWorkshop}, and a caller that reached past it
     * would be a refit nobody paid for.
     */
    void install(BoatFitting fitting) {
        if (fitting == null) return;
        switch (fitting.slot()) {
            case DRIVE -> driveId = fitting.id();
            case LIFT -> liftId = fitting.id();
            case PLATING -> platingId = fitting.id();
        }
    }
}
