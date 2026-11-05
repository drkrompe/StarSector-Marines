package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Persistent identity and installed kit for one player-owned support mech. */
public final class CampaignMech implements CampaignMachine, Serializable {

    public static final String EMPTY_COMPONENT_ID = "empty";
    public static final int CALLSIGN_MAX_LENGTH = 48;

    private final String id;
    private String displayName;
    private final MechVariant variant;
    private MechRole role;
    /** Migration input only: the loader a save from before the compartment carried. */
    private String missileReplenisherId;
    private String armsComponentId;
    private String leftArmComponentId;
    private String rightArmComponentId;
    private String leftShoulderComponentId;
    private String rightShoulderComponentId;
    // Absent in saves from before internals: null resolves to the stock part, so an existing
    // mech migrates onto the stock frame without a rewrite. The vent array's stock is empty.
    private String engineId;
    private String platingId;
    // Migration input only, like missileReplenisherId: the compartment holds these now.
    private String capacitorBankId;
    private String ventArrayId;
    /**
     * The auxiliary compartment's parts by {@link MechInternal#id}, in the order fitted. Null in
     * saves from before the compartment, which {@link #compartmentIds} migrates on first read.
     */
    private List<String> auxiliaryIds;
    /** The compartment's parts and where each sits; null in saves from before places, built from {@link #auxiliaryIds}. */
    private CompartmentFit compartmentFit;
    // Absent in saves from before section damage: null damage and a zero clock read as a whole,
    // ready mech, so an existing save needs no rewrite.
    /**
     * What the last battle left needing repair, as it stood when the clock started; null when whole.
     * The clock wears it away ({@link #damageOn}). A save from before free repairs may hold damage
     * with no clock yet, which {@link MechBay#startPendingRepairs} starts.
     */
    private MechDamage damage;
    /** Campaign day the repair clock finishes; the damage is gone then. */
    private float repairUntilDay;
    /** Campaign day the repair clock started; zero in saves from before free repairs. */
    private float repairStartDay;

    public CampaignMech(String id, String displayName, MechVariant variant,
                        MechRole role, String missileReplenisherId) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Mech id is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Mech name is required");
        }
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        this.id = id;
        this.displayName = displayName;
        this.variant = variant;
        this.role = role != null ? role : variant.defaultRole;
        auxiliaryIds = new ArrayList<>();
        auxiliaryIds.add(MechInternal.FACTORY_CAPACITOR_BANK.id);
        MechInternal loader = MechInternal.loaderFor(MissileReplenisherComponent.resolve(missileReplenisherId).id());
        if (loader != null) auxiliaryIds.add(loader.id);
        armsComponentId = idOf(variant.arms);
        leftArmComponentId = idOf(variant.leftArm);
        rightArmComponentId = idOf(variant.rightArm);
        leftShoulderComponentId = idOf(variant.leftShoulder);
        rightShoulderComponentId = idOf(variant.rightShoulder);
    }

    public String id() { return id; }

    /**
     * A chassis as it is bought: a bare frame, only its plating and factory-locked hardware
     * fitted. Pictures of a pattern draw one, so nothing shows a gun the purchase does not bring.
     */
    public static CampaignMech frame(String id, MechVariant variant) {
        CampaignMech mech = new CampaignMech(id, variant.displayName(), variant, variant.defaultRole,
                MissileReplenisherComponent.STANDARD.id());
        mech.stripToFrame();
        return mech;
    }

    /** Takes off every weapon that is not factory-locked, the engine, the plating and the whole compartment. */
    void stripToFrame() {
        for (MechMountSlot slot : MechMountSlot.values()) {
            if (weaponAt(slot) != null && !MechFrameBudget.integral(variant, slot)) installWeapon(slot, null);
        }
        installInternal(MechInternal.Role.ENGINE, null);
        installInternal(MechInternal.Role.PLATING, null);
        compartmentFit().clear();
    }

    /** Acquisition preserves the recovered fit, including deliberately empty mounts. */
    public static CampaignMech fromDeployment(String id, MechDeploymentSpec fit) {
        CampaignMech mech = new CampaignMech(id, fit.displayName(), fit.variant(),
                fit.role(), fit.missileReplenisher().id());
        for (MechMountSlot slot : MechMountSlot.values()) {
            if (fit.variant().hasMount(slot)) mech.installWeapon(slot, fit.weaponAt(slot));
        }
        return mech;
    }
    public String displayName() { return displayName; }
    /** Changes only this mech's player-facing name; its identity and fit stay intact. */
    public boolean rename(String value) {
        String candidate = normalizeCallSign(value);
        if (candidate == null || candidate.isEmpty()
                || candidate.length() > CALLSIGN_MAX_LENGTH) return false;
        displayName = candidate;
        return true;
    }

    /** Trims Unicode whitespace and space separators from a player-authored callsign. */
    public static String normalizeCallSign(String value) {
        if (value == null) return null;
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isCallSignWhitespace(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isCallSignWhitespace(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isCallSignWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0x0085;
    }

    public MechVariant variant() { return variant; }
    public MechRole role() { return role != null ? role : variant.defaultRole; }
    public String missileReplenisherId() {
        return missileReplenisher().id();
    }

    /** The fitted loader's cadence, or {@link MissileReplenisherComponent#NONE} without one. */
    public MissileReplenisherComponent missileReplenisher() {
        MechInternal loader = internal(MechInternal.Role.LOADER);
        return loader != null ? loader.replenisher() : MissileReplenisherComponent.NONE;
    }

    @Override
    public MachineCapabilities capabilities() {
        return MachineCapabilities.of(this);
    }

    @Override
    public List<SocketDef> compartmentGrids() {
        return MechCompartment.of(variant);
    }

    /** What the auxiliary compartment holds, in the order fitted. */
    @Override
    public List<MechInternal> compartment() {
        List<MechInternal> parts = new ArrayList<>();
        parts.addAll(compartmentFit().parts());
        return List.copyOf(parts);
    }

    @Override
    public List<MechCompartment.Placement> compartmentPlacements() {
        List<MechCompartment.Placement> placed = compartmentFit().placements(compartmentGrids());
        return placed != null ? placed : List.of();
    }

    /** The compartment's record, built on first read from the ids a save kept before places, its arrangement kept. */
    private CompartmentFit compartmentFit() {
        if (compartmentFit == null) {
            compartmentFit = CompartmentFit.of(compartmentIds());
            compartmentFit.settle(compartmentGrids());
        }
        return compartmentFit;
    }

    /**
     * The compartment's ids, migrated on first read from a save that kept a capacitor bank, a vent
     * array and a replenisher apart: the bank (a missing id is the factory bank, an emptied one
     * stays empty), its loader, then the vent. What no longer packs leaves the vent first, then the
     * loader; the bay still owns them, so they are spares.
     */
    private List<String> compartmentIds() {
        if (auxiliaryIds != null) return auxiliaryIds;
        List<MechInternal> parts = new ArrayList<>();
        if (!EMPTY_COMPONENT_ID.equals(capacitorBankId)) {
            MechInternal bank = MechInternal.findById(capacitorBankId);
            parts.add(bank != null && bank.role == MechInternal.Role.CAPACITOR_BANK
                    ? bank : MechInternal.FACTORY_CAPACITOR_BANK);
        }
        MechInternal loader = MechInternal.loaderFor(MissileReplenisherComponent.resolve(missileReplenisherId).id());
        if (loader != null) parts.add(loader);
        MechInternal vent = MechInternal.findById(ventArrayId);
        if (vent != null && vent.role == MechInternal.Role.VENT_ARRAY) parts.add(vent);
        for (MechInternal.Role dropFirst : List.of(MechInternal.Role.VENT_ARRAY, MechInternal.Role.LOADER)) {
            if (MechCompartment.fits(variant, parts)) break;
            parts.removeIf(part -> part.role == dropFirst);
        }
        auxiliaryIds = new ArrayList<>();
        for (MechInternal part : parts) auxiliaryIds.add(part.id);
        return auxiliaryIds;
    }

    public MechWeaponComponent arms() {
        if (EMPTY_COMPONENT_ID.equals(armsComponentId)) return null;
        return MechWeaponComponent.resolve(armsComponentId, variant.arms);
    }

    public MechWeaponComponent leftArm() {
        if (EMPTY_COMPONENT_ID.equals(leftArmComponentId)) return null;
        return MechWeaponComponent.resolve(leftArmComponentId, variant.leftArm);
    }

    public MechWeaponComponent rightArm() {
        if (EMPTY_COMPONENT_ID.equals(rightArmComponentId)) return null;
        return MechWeaponComponent.resolve(rightArmComponentId, variant.rightArm);
    }

    public MechWeaponComponent leftShoulder() {
        if (EMPTY_COMPONENT_ID.equals(leftShoulderComponentId)) return null;
        return MechWeaponComponent.resolve(leftShoulderComponentId, variant.leftShoulder);
    }

    public MechWeaponComponent rightShoulder() {
        if (EMPTY_COMPONENT_ID.equals(rightShoulderComponentId)) return null;
        return MechWeaponComponent.resolve(rightShoulderComponentId, variant.rightShoulder);
    }

    /** Whether no mount carries a weapon: every chassis has mounts, so a mech with none is unarmed. */
    @Override
    public boolean unarmed() {
        for (MechMountSlot slot : MechMountSlot.values()) {
            if (weaponAt(slot) != null) return false;
        }
        return true;
    }

    public MechWeaponComponent weaponAt(MechMountSlot slot) {
        return switch (slot) {
            case ARMS -> arms();
            case LEFT_ARM -> leftArm();
            case RIGHT_ARM -> rightArm();
            case LEFT_SHOULDER -> leftShoulder();
            case RIGHT_SHOULDER -> rightShoulder();
        };
    }

    /**
     * The installed part in {@code role}: the stock part when none was ever fitted, and null
     * when it is empty (an engine stripped back to stores, or no such part in the compartment).
     * Vent arrays stack; this is the first.
     */
    @Override
    public MechInternal internal(MechInternal.Role role) {
        if (role.compartment()) {
            for (MechInternal part : compartment()) if (part.role == role) return part;
            return null;
        }
        String installed = internalId(role);
        if (EMPTY_COMPONENT_ID.equals(installed)) return null;
        MechInternal internal = MechInternal.findById(installed);
        return internal != null && internal.role == role ? internal : role.stock();
    }

    /** The engine budget and frame values of what is installed now. */
    public MechFrameBudget.Fit fit() {
        return MechFrameBudget.Fit.of(this);
    }

    public MechDeploymentSpec freezeForDeployment() {
        return new MechDeploymentSpec(variant, role(), missileReplenisher(),
                arms(), leftArm(), rightArm(), leftShoulder(), rightShoulder(),
                displayName, deployedFrame(), id);
    }

    /** What the fit lands with: the budget's stand-in for an empty armor point is no armor on the field. */
    private MechFrame deployedFrame() {
        MechFrame frame = fit().frame();
        return internal(MechInternal.Role.PLATING) != null ? frame : frame.withNoArmor();
    }

    /** The deployment on {@code day}, carrying whatever wear its repair has not yet taken off. */
    public MechDeploymentSpec freezeForDeployment(float day) {
        MechDamage left = damageOn(day);
        MechDeploymentSpec spec = freezeForDeployment();
        return left == null ? spec : spec.withWear(left.toCarriedWear());
    }

    /**
     * The battle damage still on the mech on {@code day}: the damage the clock started with, less
     * the share of the clock that has run. Null once it is repaired or when it was never hit.
     */
    public MechDamage damageOn(float day) {
        if (damage == null) return null;
        float span = repairUntilDay - repairStartDay;
        if (!(span > 0f)) return damage;
        return damage.scaled((repairUntilDay - day) / span);
    }

    /** Whether the mech carries damage whose repair clock has not been started. */
    boolean repairPending() {
        return damage != null && !(repairUntilDay - repairStartDay > 0f);
    }

    public float repairUntilDay() {
        return repairUntilDay;
    }

    /** Days until the mech is whole; zero once it is, or when it was never hit. */
    public float repairDaysRemaining(float day) {
        if (damageOn(day) == null) return 0f;
        return Math.max(0f, repairUntilDay - day);
    }

    /**
     * Whether the mech can go into an armor drop on {@code day}, and in what state: missing an
     * engine or capacitor bank it cannot; under repair it drops carrying the wear still left.
     */
    public Readiness readiness(float day) {
        if (!missingInternals().isEmpty()) return Readiness.INCOMPLETE;
        return damageOn(day) != null ? Readiness.UNDER_REPAIR : Readiness.READY;
    }

    /**
     * Restarts the repair clock on {@code repairing}, the damage now on the mech, from
     * {@code day} to {@code untilDay}. The gantry crew works between missions for nothing but time.
     */
    void startRepair(MechDamage repairing, float day, float untilDay) {
        if (repairing == null || repairing.isEmpty() || !Float.isFinite(day) || !(untilDay > day)) {
            damage = null;
            repairStartDay = 0f;
            repairUntilDay = 0f;
            return;
        }
        damage = repairing;
        repairStartDay = day;
        repairUntilDay = untilDay;
    }

    /** Whether a mech can deploy, and in what state. */
    public enum Readiness {
        READY,
        /** Repairing on the campaign clock; it can drop, carrying the wear left. */
        UNDER_REPAIR,
        /** An engine or capacitor bank was stripped and nothing fitted in its place. */
        INCOMPLETE
    }

    private String internalId(MechInternal.Role role) {
        return switch (role) {
            case ENGINE -> engineId;
            case PLATING -> platingId;
            default -> null;
        };
    }

    /**
     * Fits {@code internal} in {@code role}, or empties it on null. An empty engine is stored as
     * {@link #EMPTY_COMPONENT_ID}, because a missing id still reads as the stock part for frames
     * from before internals existed. In the compartment a capacitor bank or loader replaces the
     * one there, a vent array goes in beside the others, and null takes out every part of the
     * role; every other part stays where it sits. The caller has checked that the result packs ({@link MechCompartment}).
     */
    void installInternal(MechInternal.Role role, MechInternal internal) {
        installInternal(role, internal, null);
    }

    /** As above, a compartment part going in at {@code at} (a bay, or a cell; null for anywhere) where it will. */
    void installInternal(MechInternal.Role role, MechInternal internal, MechCompartment.Spot at) {
        if (role.compartment()) {
            if (internal == null) compartmentFit().removeRole(compartmentGrids(), role);
            else compartmentFit().add(compartmentGrids(), internal, at);
            return;
        }
        String internalId = internal != null ? internal.id
                : role == MechInternal.Role.ENGINE || role == MechInternal.Role.PLATING ? EMPTY_COMPONENT_ID : null;
        switch (role) {
            case ENGINE -> engineId = internalId;
            case PLATING -> platingId = internalId;
            default -> { }
        }
    }

    /** Takes one {@code part} out of the compartment; false when none is there. */
    boolean removePart(MechInternal part) {
        return removePart(part, null);
    }

    /** Takes out the {@code part} at {@code from} (where it was lifted), else the first fitted; nothing else moves. */
    boolean removePart(MechInternal part, MechCompartment.Spot from) {
        return part != null && compartmentFit().remove(compartmentGrids(), part, from);
    }

    void installWeapon(MechMountSlot slot, MechWeaponComponent component) {
        String componentId = component != null ? component.id : EMPTY_COMPONENT_ID;
        switch (slot) {
            case ARMS -> armsComponentId = componentId;
            case LEFT_ARM -> leftArmComponentId = componentId;
            case RIGHT_ARM -> rightArmComponentId = componentId;
            case LEFT_SHOULDER -> leftShoulderComponentId = componentId;
            case RIGHT_SHOULDER -> rightShoulderComponentId = componentId;
        }
    }

    private static String idOf(MechWeaponComponent component) {
        return component != null ? component.id : null;
    }
}
