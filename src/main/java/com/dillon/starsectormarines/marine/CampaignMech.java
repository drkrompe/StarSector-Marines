package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Persistent identity and installed kit for one player-owned support mech. */
public final class CampaignMech implements Serializable {

    public static final String EMPTY_COMPONENT_ID = "empty";
    public static final int CALLSIGN_MAX_LENGTH = 48;

    private final String id;
    private String displayName;
    private final MechVariant variant;
    private MechRole role;
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
    private String capacitorBankId;
    private String ventArrayId;
    // Absent in saves from before section damage: null damage and a zero clock read as a whole,
    // ready mech, so an existing save needs no rewrite.
    /** What the last battle left needing repair; null when whole or once a repair is ordered. */
    private MechDamage damage;
    /** Campaign day an ordered repair finishes; the mech is in its gantry until then. */
    private float repairUntilDay;

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
        this.missileReplenisherId = MissileReplenisherComponent.resolve(
                missileReplenisherId).id();
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

    /** Takes off every weapon that is not factory-locked, the engine and the capacitor bank. */
    void stripToFrame() {
        for (MechMountSlot slot : MechMountSlot.values()) {
            if (weaponAt(slot) != null && !MechFrameBudget.integral(variant, slot)) installWeapon(slot, null);
        }
        installInternal(MechInternal.Role.ENGINE, null);
        installInternal(MechInternal.Role.CAPACITOR_BANK, null);
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
        return MissileReplenisherComponent.resolve(missileReplenisherId).id();
    }

    public MissileReplenisherComponent missileReplenisher() {
        return MissileReplenisherComponent.resolve(missileReplenisherId);
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
     * when the socket is empty (an engine or capacitor bank stripped back to stores, or no
     * vent array).
     */
    public MechInternal internal(MechInternal.Role role) {
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
                displayName, fit().frame(), id);
    }

    /** Battle damage awaiting a repair order, or null. */
    public MechDamage damage() {
        return damage;
    }

    public float repairUntilDay() {
        return repairUntilDay;
    }

    public float repairDaysRemaining(float day) {
        return Math.max(0f, repairUntilDay - day);
    }

    /**
     * Whether the mech can go into an armor drop on {@code day}: damaged and awaiting repair, in
     * its gantry under repair, or missing an engine or capacitor bank, it cannot.
     */
    public Readiness readiness(float day) {
        if (damage != null) return Readiness.DAMAGED;
        if (repairDaysRemaining(day) > 0f) return Readiness.UNDER_REPAIR;
        return missingInternals().isEmpty() ? Readiness.READY : Readiness.INCOMPLETE;
    }

    /** Records battle damage; a mech hit twice before repair keeps the worse of each section. */
    void recordDamage(MechDamage taken) {
        if (taken == null || taken.isEmpty()) return;
        damage = MechDamage.worst(damage, taken);
    }

    /** A paid repair order: the damage is in hand and the mech is back on {@code untilDay}. */
    void beginRepair(float untilDay) {
        damage = null;
        repairUntilDay = Float.isFinite(untilDay) ? untilDay : 0f;
    }

    /** Whether a mech can deploy, and if not, why. */
    public enum Readiness {
        READY,
        /** Battle damage recorded and no repair ordered yet. */
        DAMAGED,
        /** A paid repair is still running on the campaign clock. */
        UNDER_REPAIR,
        /** An engine or capacitor bank was stripped and nothing fitted in its place. */
        INCOMPLETE
    }

    private String internalId(MechInternal.Role role) {
        return switch (role) {
            case ENGINE -> engineId;
            case PLATING -> platingId;
            case CAPACITOR_BANK -> capacitorBankId;
            case VENT_ARRAY -> ventArrayId;
        };
    }

    /**
     * The roles this frame must fill before it can drop and has left empty: an engine and a
     * capacitor bank. A missing vent array is an option, not a gap.
     */
    public List<MechInternal.Role> missingInternals() {
        List<MechInternal.Role> missing = new ArrayList<>();
        for (MechInternal.Role role : REQUIRED_INTERNALS) {
            if (EMPTY_COMPONENT_ID.equals(internalId(role))) missing.add(role);
        }
        return missing;
    }

    /** The socketed roles a frame cannot drop without. Plating is frame-wide and never stripped. */
    public static final List<MechInternal.Role> REQUIRED_INTERNALS =
            List.of(MechInternal.Role.ENGINE, MechInternal.Role.CAPACITOR_BANK);

    /**
     * Fits {@code internal} in {@code role}, or empties it on null. An empty engine or capacitor
     * socket is stored as {@link #EMPTY_COMPONENT_ID}, because a missing id still reads as the
     * stock part for frames from before internals existed.
     */
    void installInternal(MechInternal.Role role, MechInternal internal) {
        String internalId = internal != null ? internal.id
                : REQUIRED_INTERNALS.contains(role) ? EMPTY_COMPONENT_ID : null;
        switch (role) {
            case ENGINE -> engineId = internalId;
            case PLATING -> platingId = internalId;
            case CAPACITOR_BANK -> capacitorBankId = internalId;
            case VENT_ARRAY -> ventArrayId = internalId;
        }
    }

    void installMissileReplenisher(String componentId) {
        missileReplenisherId = componentId;
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
