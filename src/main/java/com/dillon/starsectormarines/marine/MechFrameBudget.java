package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;

/**
 * The engine budget and the frame values one fit resolves to. Engine output is a mass
 * budget against everything fitted; engine and plating move the chassis within its own
 * speed and armor bands, so a refit Hound is a better Hound and never a cheap Bulwark.
 *
 * <p>Each band runs from the stock frame (the chassis's authored values) to its refit
 * ceiling, roughly where every chassis stood before the stock-frame cut.</p>
 */
public final class MechFrameBudget {

    private MechFrameBudget() {
    }

    /** Engine output an industrial engine gives this chassis; better engines multiply it. */
    public static float ratedOutput(MechVariant variant) {
        return switch (variant) {
            case HOUND -> 20f;
            case SIROCCO -> 36f;
            case BULWARK -> 46f;
        };
    }

    /** Mass of one plating unit on this chassis: more frame takes more plate. */
    public static float platingUnit(MechVariant variant) {
        return switch (variant) {
            case HOUND -> 3f;
            case SIROCCO -> 4f;
            case BULWARK -> 6f;
        };
    }

    /** Armor capacity at the top of the plating band. */
    public static float refitArmorCapacity(MechVariant variant) {
        return switch (variant) {
            case HOUND -> 500f;
            case SIROCCO -> 400f;
            case BULWARK -> 950f;
        };
    }

    /** Move speed at the top of the engine band. */
    public static float refitMoveSpeed(MechVariant variant) {
        return switch (variant) {
            case HOUND -> 1.70f;
            case SIROCCO -> 1.45f;
            case BULWARK -> 1.15f;
        };
    }

    /** A weapon's mass: its footprint, plus the weight its access tier carries. */
    public static float mass(MechWeaponComponent component) {
        if (component == null) return 0f;
        EquipmentAccessTier tier = MechPattern.of(component).accessTier();
        return component.slotCost * 2f + tierMass(tier);
    }

    public static float mass(MechInternal internal, MechVariant variant) {
        if (internal == null) return 0f;
        return internal.role == MechInternal.Role.PLATING
                ? internal.mass * platingUnit(variant) : internal.mass;
    }

    private static float tierMass(EquipmentAccessTier tier) {
        if (tier == null) return 0f;
        return switch (tier) {
            case COMMON -> 0f;
            case ADVANCED -> 2f;
            case PRESTIGE, RESTRICTED -> 4f;
        };
    }

    /** One fit's budget and frame, computed from what is installed; nothing is stored. */
    public record Fit(MechVariant variant,
                      MechWeaponComponent arms, MechWeaponComponent leftShoulder,
                      MechWeaponComponent rightShoulder,
                      MechInternal engine, MechInternal plating,
                      MechInternal heatSink, MechInternal cooler) {

        public Fit {
            if (variant == null) throw new IllegalArgumentException("Mech variant is required");
            if (engine == null) engine = MechInternal.INDUSTRIAL_ENGINE;
            if (plating == null) plating = MechInternal.FACTORY_PLATING;
            if (heatSink == null) heatSink = MechInternal.FACTORY_HEAT_SINK;
        }

        public static Fit of(CampaignMech mech) {
            return new Fit(mech.variant(), mech.arms(), mech.leftShoulder(), mech.rightShoulder(),
                    mech.internal(MechInternal.Role.ENGINE), mech.internal(MechInternal.Role.PLATING),
                    mech.internal(MechInternal.Role.HEAT_SINK), mech.internal(MechInternal.Role.COOLER));
        }

        public MechWeaponComponent weaponAt(MechMountSlot slot) {
            return switch (slot) {
                case ARMS -> arms;
                case LEFT_SHOULDER -> leftShoulder;
                case RIGHT_SHOULDER -> rightShoulder;
            };
        }

        public Fit withWeapon(MechMountSlot slot, MechWeaponComponent component) {
            return new Fit(variant,
                    slot == MechMountSlot.ARMS ? component : arms,
                    slot == MechMountSlot.LEFT_SHOULDER ? component : leftShoulder,
                    slot == MechMountSlot.RIGHT_SHOULDER ? component : rightShoulder,
                    engine, plating, heatSink, cooler);
        }

        public MechInternal internal(MechInternal.Role role) {
            return switch (role) {
                case ENGINE -> engine;
                case PLATING -> plating;
                case HEAT_SINK -> heatSink;
                case COOLER -> cooler;
            };
        }

        /** Replaces the part in {@code role}; a null cooler empties the utility socket. */
        public Fit withInternal(MechInternal.Role role, MechInternal internal) {
            return new Fit(variant, arms, leftShoulder, rightShoulder,
                    role == MechInternal.Role.ENGINE ? internal : engine,
                    role == MechInternal.Role.PLATING ? internal : plating,
                    role == MechInternal.Role.HEAT_SINK ? internal : heatSink,
                    role == MechInternal.Role.COOLER ? internal : cooler);
        }

        public float output() {
            return ratedOutput(variant) * engine.outputFactor;
        }

        public float fittedMass() {
            float mass = MechFrameBudget.mass(arms) + MechFrameBudget.mass(leftShoulder)
                    + MechFrameBudget.mass(rightShoulder);
            for (MechInternal.Role role : MechInternal.Role.values()) {
                mass += MechFrameBudget.mass(internal(role), variant);
            }
            return mass;
        }

        public boolean overBudget() {
            return fittedMass() > output() + 1e-4f;
        }

        public float armorCapacity() {
            return band(variant.armorCapacity, refitArmorCapacity(variant), plating.armorFraction);
        }

        public float moveSpeed() {
            return band(variant.moveSpeed, refitMoveSpeed(variant), engine.speedFraction);
        }

        public float heatCapacity() {
            return heatSink.heatCapacity;
        }

        public float coolingPerSecond() {
            float cooling = heatSink.cooling + engine.cooling
                    + (cooler != null ? cooler.cooling : 0f);
            return Math.max(0f, cooling);
        }

        public MechFrame frame() {
            return new MechFrame(armorCapacity(), moveSpeed(), heatCapacity(), coolingPerSecond());
        }

        private static float band(float stock, float refit, float fraction) {
            return fraction <= 0f ? stock : stock + (refit - stock) * Math.min(1f, fraction);
        }
    }

    /** True when a loader other than the standard one occupies the utility socket. */
    static boolean loaderOccupiesUtility(MissileReplenisherComponent loader) {
        return loader != null && !MissileReplenisherComponent.STANDARD.id().equals(loader.id());
    }
}
