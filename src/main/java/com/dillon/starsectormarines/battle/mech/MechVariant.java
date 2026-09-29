package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;

/** Stable chassis profiles built from swappable {@link MechWeaponComponent}s. */
public enum MechVariant {

    BULWARK("bulwark", "Bulwark", 550f, 950f, 18f, 1.15f, 0.40f, 55f,
            1.60f, 1.50f, 0.60f, 0.80f, 1.8f,
            LayeredMechAppearance.CHASSIS_CLEAN,
            MechWeaponComponent.DUAL_CHAINGUNS,
            MechWeaponComponent.SRM_15,
            MechWeaponComponent.SRM_15,
            MechRole.ARMORED_SUPPORT),

    HOUND("hound", "Hound", 350f, 500f, 14f, 1.70f, 0.42f, 50f,
            1.35f, 1.20f, 0.50f, 0.67f, 0.8f,
            LayeredMechAppearance.CHASSIS_HOUND,
            MechWeaponComponent.NOSE_CHAINGUN,
            MechWeaponComponent.SRM_5,
            null,
            MechRole.ASSAULT),

    SIROCCO("sirocco", "Sirocco", 250f, 400f, 10f, 1.45f, 0.45f, 55f,
            1.35f, 1.20f, 0.48f, 0.65f, 1.2f,
            LayeredMechAppearance.CHASSIS_SIROCCO,
            MechWeaponComponent.SINGLE_HEAVY_CANNON,
            MechWeaponComponent.LRM_5,
            MechWeaponComponent.LRM_5,
            MechRole.LR_SUPPORT);

    public final String id;
    public final String displayName;
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
}
