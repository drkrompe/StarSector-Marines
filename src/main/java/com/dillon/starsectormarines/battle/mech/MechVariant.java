package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;

/** Stable chassis profiles built from swappable {@link MechWeaponComponent}s. */
public enum MechVariant {

    BULWARK("bulwark", "@gearMechVariantBULWARK", 550f, 710f, 18f, 1.05f, 0.40f, 55f,
            1.60f, 1.50f, 0.60f, 0.80f, 1.8f,
            LayeredMechAppearance.CHASSIS_CLEAN,
            MechWeaponComponent.DUAL_CHAINGUNS,
            MechWeaponComponent.SRM_15,
            MechWeaponComponent.SRM_15,
            MechRole.ARMORED_SUPPORT),

    HOUND("hound", "@gearMechVariantHOUND", 350f, 375f, 14f, 1.55f, 0.42f, 50f,
            1.35f, 1.20f, 0.50f, 0.67f, 0.8f,
            LayeredMechAppearance.CHASSIS_HOUND,
            MechWeaponComponent.NOSE_CHAINGUN,
            MechWeaponComponent.SRM_5,
            null,
            MechRole.ASSAULT),

    SIROCCO("sirocco", "@gearMechVariantSIROCCO", 250f, 300f, 10f, 1.32f, 0.45f, 55f,
            1.35f, 1.20f, 0.48f, 0.65f, 1.2f,
            LayeredMechAppearance.CHASSIS_SIROCCO,
            MechWeaponComponent.SINGLE_HEAVY_CANNON,
            // Artillery against men; the heavy cannon is the Sirocco's answer to armor.
            MechWeaponComponent.LRM_5_FRAG,
            MechWeaponComponent.LRM_5_FRAG,
            MechRole.LR_SUPPORT);

    public final String id;
    private final String displayName;

    public String displayName() { return Strings.resolve(displayName); }
    public final float maxStructure;
    public final float armorCapacity;
    public final float armorRating;
    public final float moveSpeed;
    public final float accuracy;
    public final float visionRange;
    public final float renderScale;
    public final float moraleImpact;
    public final float radius;
    public final float hitHalfHeight;
    /** Relative inertia for manual drive only; not tonnage or an autonomous speed modifier. */
    public final float relativeMass;
    public final int chassisAppearance;
    public final MechWeaponComponent arms;
    public final MechWeaponComponent leftShoulder;
    public final MechWeaponComponent rightShoulder;
    public final MechRole defaultRole;

    MechVariant(String id, String displayName, float maxStructure,
                float armorCapacity, float armorRating, float moveSpeed,
                float accuracy, float visionRange, float renderScale,
                float moraleImpact, float radius, float hitHalfHeight, float relativeMass,
                int chassisAppearance, MechWeaponComponent arms,
                MechWeaponComponent leftShoulder,
                MechWeaponComponent rightShoulder, MechRole defaultRole) {
        this.id = id;
        this.displayName = displayName;
        this.maxStructure = maxStructure;
        this.armorCapacity = armorCapacity;
        this.armorRating = armorRating;
        this.moveSpeed = moveSpeed;
        this.accuracy = accuracy;
        this.visionRange = visionRange;
        this.renderScale = renderScale;
        this.moraleImpact = moraleImpact;
        this.radius = radius;
        this.hitHalfHeight = hitHalfHeight;
        this.relativeMass = relativeMass;
        this.chassisAppearance = chassisAppearance;
        this.arms = arms;
        this.leftShoulder = leftShoulder;
        this.rightShoulder = rightShoulder;
        this.defaultRole = defaultRole;
    }

    public static MechVariant fromId(String id) {
        for (MechVariant variant : values()) {
            if (variant.id.equals(id)) return variant;
        }
        throw new IllegalArgumentException("Unknown mech variant id '" + id + "'");
    }

    /** Applies the chassis's spawn-time stats and persistent profile identity. */
    public EntitySpec applyTo(EntitySpec spec) {
        spec.mechVariant = this;
        spec.health(maxStructure)
                .armor(armorCapacity, armorRating)
                .moveSpeed(moveSpeed)
                .accuracy(accuracy)
                .attackRange(maxWeaponRange())
                .visionRange(Math.max(visionRange, maxWeaponRange()));
        return spec;
    }

    public MechLoadoutComponent createLoadout(MechRole role) {
        return new MechLoadoutComponent(this, role != null ? role : defaultRole);
    }

    public float maxWeaponRange() {
        float max = arms.weaponDef().range;
        if (leftShoulder != null) {
            max = Math.max(max, leftShoulder.weaponDef().range);
        }
        if (rightShoulder != null) {
            max = Math.max(max, rightShoulder.weaponDef().range);
        }
        return max;
    }

    /** How this chassis is taken apart: its height bands and section pools. */
    public MechBodyPlan bodyPlan() {
        return switch (this) {
            case HOUND -> MechBodyPlan.HOUND;
            case BULWARK -> MechBodyPlan.BULWARK;
            case SIROCCO -> MechBodyPlan.SIROCCO;
        };
    }

    /**
     * What this chassis asks of the aircraft that drops it: the weight class
     * seen from the air. Separate from {@link #relativeMass}, which is inertia.
     */
    public LiftClass liftClass() {
        return switch (this) {
            case HOUND -> LiftClass.LIGHT;
            case SIROCCO -> LiftClass.MEDIUM;
            case BULWARK -> LiftClass.HEAVY;
        };
    }
}
