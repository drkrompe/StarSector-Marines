package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
 *
 * <p><b>Any socket can stand empty.</b> Every part comes off into stores, the
 * way a mech's engine does. A boat missing one of her {@link #REQUIRED_SLOTS}
 * cannot fly ({@link #missingParts}) and stays off the lift until it is fitted;
 * empty plating flies her as a weak bare hull, an empty lift is the bare hold,
 * an empty emitter flies her unshielded, and a bare hardpoint carries no gun.
 */
public final class CampaignBoat implements Serializable {

    private final String id;
    private String displayName;
    private final ShuttleType pattern;
    /**
     * The fitting in each slot, by id. A slot with no entry reads as its standard
     * fitting, which is what every boat saved before parts came off is carrying;
     * {@link #EMPTY} is a socket stripped bare.
     */
    private Map<BoatFittingSlot, String> fittingIds = new EnumMap<>(BoatFittingSlot.class);
    /**
     * The door gun on each hardpoint, by {@link BoatGun} id. Null, or a null or
     * unknown entry, reads as the hardpoint's yard-standard gun — which is what
     * every boat saved before hardpoints could be refitted is carrying;
     * {@link #EMPTY} is a hardpoint stripped bare. This is each hardpoint's
     * {@linkplain BoatHardpoint#TOP top} position, which a 2x2 gun fills whole.
     */
    private String[] gunIds;
    /**
     * The 2x1 gun in each hardpoint's {@linkplain BoatHardpoint#BOTTOM bottom} position,
     * by {@link BoatGun} id; a null or unknown entry is none. Null on every save from
     * before two guns could share a hardpoint, which reads as each bottom position empty,
     * so those boats load carrying the one gun a mount they always had.
     */
    private String[] podGunIds;
    private float repairUntilDay;

    /** A socket stripped bare, as persisted. */
    static final String EMPTY = "";

    /**
     * The slots a boat cannot fly without: her engines, her cabin and her capacitors
     * (owner pick). Plating, lift, emitter and guns are options, not gaps.
     */
    public static final List<BoatFittingSlot> REQUIRED_SLOTS = List.of(
            BoatFittingSlot.DRIVE, BoatFittingSlot.CABIN, BoatFittingSlot.POWER);

    /** Zero on legacy saves. Recovery is read from campaign time, not screen visits. */
    public float repairUntilDay() { return repairUntilDay; }
    public float repairDaysRemaining(float day) { return Math.max(0f, repairUntilDay - day); }
    public boolean airworthy(float day) { return repairDaysRemaining(day) == 0f; }

    /** The required slots this boat has left empty, in slot order; empty when she is whole. */
    public List<BoatFittingSlot> missingParts() {
        List<BoatFittingSlot> missing = new ArrayList<>();
        for (BoatFittingSlot slot : REQUIRED_SLOTS) {
            if (fittingIn(slot) == null) missing.add(slot);
        }
        return missing;
    }

    /** Whether she can go on the lift on {@code day}: every required part fitted, and repaired. */
    public boolean flightReady(float day) { return missingParts().isEmpty() && airworthy(day); }
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
        fittingIds.put(BoatFittingSlot.PLATING, BoatFitting.resolve(platingId, BoatFittingSlot.PLATING).id());
        fittingIds.put(BoatFittingSlot.DRIVE, BoatFitting.resolve(driveId, BoatFittingSlot.DRIVE).id());
    }

    public CampaignBoat(String id, String displayName, ShuttleType pattern,
                        String platingId, String driveId, String liftId) {
        this(id, displayName, pattern, platingId, driveId);
        BoatFitting lift = BoatFitting.resolve(liftId, BoatFittingSlot.LIFT);
        if (lift.supports(pattern)) fittingIds.put(BoatFittingSlot.LIFT, lift.id());
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    public ShuttleType pattern() { return pattern; }

    public BoatFitting plating() { return fittingIn(BoatFittingSlot.PLATING); }

    public BoatFitting drive() { return fittingIn(BoatFittingSlot.DRIVE); }

    public BoatFitting lift() { return fittingIn(BoatFittingSlot.LIFT); }

    /** What is installed in one slot, or null for a socket stripped bare. */
    public BoatFitting fittingIn(BoatFittingSlot slot) {
        String id = fittingIds.get(slot);
        return EMPTY.equals(id) ? null : BoatFitting.resolve(id, slot);
    }

    /**
     * The frame this boat flies as, with its fittings applied.
     *
     * <p>Carries the boat's id so a shoot-down can be traced back here at
     * resolution. Nothing in the battle reads it — an air loss simply remembers
     * what it was flying as, and the campaign works out afterwards whose it was.
     *
     * <p>Only a {@linkplain #flightReady flight-ready} boat is flown; one missing a
     * required part still freezes, for the hangar to draw, with that slot's
     * standard part standing in. Empty plating freezes as a bare hull, an empty
     * emitter as no shield and a bare hardpoint as no gun ({@link FittedBoat}). A
     * hardpoint with two 2x1 guns freezes the bottom one as its pod's second gun.
     */
    public FittedBoat freezeForDeployment() {
        String[] guns = new String[pattern.hardpoints()];
        String[] pods = new String[guns.length];
        for (int index = 0; index < guns.length; index++) {
            BoatGun gun = gunOn(index);
            guns[index] = gun != null ? gun.structureId() : FittedBoat.BARE;
            BoatGun pod = gunsAt(index)[BoatHardpoint.BOTTOM];
            pods[index] = pod != null ? pod.structureId() : null;
        }
        Map<BoatFittingSlot, BoatFitting> fit = new EnumMap<>(BoatFittingSlot.class);
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting fitted = fittingIn(slot);
            if (fitted != null) fit.put(slot, fitted);
            else if (FittedBoat.optional(slot)) fit.put(slot, null);
        }
        return new FittedBoat(pattern, fit, id, guns, pods);
    }

    /** Whether the pattern has a hardpoint {@code index}, bare or armed. */
    public boolean hasHardpoint(int index) {
        return index >= 0 && index < pattern.hardpoints();
    }

    /**
     * The gun in the top position of hardpoint {@code index}, which is the whole
     * hardpoint for a 2x2 gun, or null for an empty top or a hardpoint the
     * pattern does not have ({@link #hasHardpoint} tells them apart). A
     * hardpoint nobody refitted carries the yard's own gun, which on some
     * hardpoints is none.
     */
    public BoatGun gunOn(int index) {
        if (index < 0 || index >= pattern.hardpoints()) return null;
        String id = gunIds != null && index < gunIds.length ? gunIds[index] : null;
        if (EMPTY.equals(id)) return null;
        BoatGun fitted = BoatGun.findById(id);
        return fitted != null ? fitted : BoatGun.standardFor(pattern, index);
    }

    /**
     * Both positions of hardpoint {@code index}, top then bottom, null where empty
     * ({@link BoatHardpoint}); two nulls for a hardpoint the pattern does not have.
     */
    public BoatGun[] gunsAt(int index) {
        BoatGun[] pair = new BoatGun[BoatHardpoint.POSITIONS];
        if (!hasHardpoint(index)) return pair;
        pair[BoatHardpoint.TOP] = gunOn(index);
        if (pair[BoatHardpoint.TOP] == null || pair[BoatHardpoint.TOP].half()) {
            pair[BoatHardpoint.BOTTOM] = BoatGun.findById(podGunIds != null && index < podGunIds.length
                    ? podGunIds[index] : null);
        }
        return pair;
    }

    /** Every gun on hardpoint {@code index}, top first: none, one, or a pod's two. */
    public List<BoatGun> gunsOn(int index) {
        return BoatHardpoint.guns(gunsAt(index));
    }

    /**
     * Sets both positions of one hardpoint, top then bottom, null for empty.
     * Package-private for the reason {@link #install} is.
     */
    void installGuns(int index, BoatGun[] pair) {
        if (!hasHardpoint(index) || pair == null || pair.length != BoatHardpoint.POSITIONS) return;
        gunIds = sized(gunIds);
        podGunIds = sized(podGunIds);
        BoatGun top = pair[BoatHardpoint.TOP];
        BoatGun bottom = top != null && !top.half() ? null : pair[BoatHardpoint.BOTTOM];
        gunIds[index] = top == null ? EMPTY : top.id();
        podGunIds[index] = bottom == null ? null : bottom.id();
    }

    private String[] sized(String[] ids) {
        if (ids != null && ids.length == pattern.hardpoints()) return ids;
        String[] grown = new String[pattern.hardpoints()];
        if (ids != null) System.arraycopy(ids, 0, grown, 0, Math.min(ids.length, grown.length));
        return grown;
    }

    /**
     * Installs a fitting into its own slot. Package-private: the atomic
     * authority is {@link BoatWorkshop}, and a caller that reached past it
     * would be a refit nobody paid for.
     */
    void install(BoatFitting fitting) {
        if (fitting == null) return;
        fittingIds.put(fitting.slot(), fitting.id());
    }

    /** Leaves one slot empty. Package-private for the reason {@link #install} is. */
    void clear(BoatFittingSlot slot) {
        if (slot != null) fittingIds.put(slot, EMPTY);
    }
}
