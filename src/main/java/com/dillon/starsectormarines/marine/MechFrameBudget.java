package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
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
            // Rated for its factory heavy cannon, so the build mass costs the Warden nothing.
            case WARDEN -> 36f + HEAVY_CANNON_BUILD_MASS;
            // Two single arm guns weigh what the old twin mount did plus one footprint row (4).
            case BULWARK -> 50f;
        };
    }

    /** Mass of one plating unit on this chassis: more frame takes more plate. */
    public static float platingUnit(MechVariant variant) {
        return switch (variant) {
            case HOUND -> 3f;
            case WARDEN -> 4f;
            case BULWARK -> 6f;
        };
    }

    /** Armor capacity at the top of the plating band. */
    public static float refitArmorCapacity(MechVariant variant) {
        return switch (variant) {
            case HOUND -> 500f;
            case WARDEN -> 400f;
            case BULWARK -> 950f;
        };
    }

    /** Move speed at the top of the engine band. */
    public static float refitMoveSpeed(MechVariant variant) {
        return switch (variant) {
            case HOUND -> 1.70f;
            case WARDEN -> 1.45f;
            case BULWARK -> 1.15f;
        };
    }

    /** A weapon's mass: its footprint, plus the weight its access tier carries, plus its own heavy build. */
    public static float mass(MechWeaponComponent component) {
        if (component == null) return 0f;
        EquipmentAccessTier tier = MechPattern.of(component).accessTier();
        return component.slotCost * 2f + tierMass(tier) + buildMass(component);
    }

    /**
     * Mass a weapon carries beyond its footprint and tier: the heavy cannon's breech and recoil mounting and the
     * bastion autocannons' feed are why a Bulwark carrying either gives up a frame part
     * (heavy-equipment-progression.md). {@code -Dbattle.mech.buildMass.<short id>=0} is the control.
     */
    private static float buildMass(MechWeaponComponent component) {
        float authored = switch (component) {
            case SINGLE_HEAVY_CANNON -> HEAVY_CANNON_BUILD_MASS;
            case BASTION_AUTOCANNON -> 1.5f;
            default -> 0f;
        };
        return component.tuned("buildMass", authored);
    }

    /** {@code -Dbattle.mech.heavyCannonBuildMass=0} is the control. */
    private static final float HEAVY_CANNON_BUILD_MASS = Float.parseFloat(
            System.getProperty("battle.mech.heavyCannonBuildMass", "3"));

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
    /** Whether {@code slot}'s hardware on this chassis is factory-locked: part of the frame. */
    public static boolean integral(MechVariant variant, MechMountSlot slot) {
        MechFittingLayout.SocketId socket = switch (slot) {
            case ARMS -> MechFittingLayout.SocketId.ARMS;
            case LEFT_ARM -> MechFittingLayout.SocketId.LEFT_ARM;
            case RIGHT_ARM -> MechFittingLayout.SocketId.RIGHT_ARM;
            case LEFT_SHOULDER -> MechFittingLayout.SocketId.LEFT_SHOULDER;
            case RIGHT_SHOULDER -> MechFittingLayout.SocketId.RIGHT_SHOULDER;
        };
        MechFittingLayout.SocketDef def = MechFittingLayout.forVariant(variant).socket(socket);
        return def != null && def.factoryLocked();
    }

    public record Fit(MechVariant variant,
                      MechWeaponComponent arms,
                      MechWeaponComponent leftArm, MechWeaponComponent rightArm,
                      MechWeaponComponent leftShoulder,
                      MechWeaponComponent rightShoulder,
                      MechInternal engine, MechInternal plating,
                      MechInternal capacitorBank, MechInternal ventArray) {

        public Fit {
            if (variant == null) throw new IllegalArgumentException("Mech variant is required");
            if (engine == null) engine = MechInternal.INDUSTRIAL_ENGINE;
            if (plating == null) plating = MechInternal.FACTORY_PLATING;
            if (capacitorBank == null) capacitorBank = MechInternal.FACTORY_CAPACITOR_BANK;
        }

        /**
         * A fit with {@code armWeapon} on every arm mount the chassis has: its centreline mount, or
         * one on each arm of a two-armed chassis.
         */
        public Fit(MechVariant variant, MechWeaponComponent armWeapon,
                   MechWeaponComponent leftShoulder, MechWeaponComponent rightShoulder,
                   MechInternal engine, MechInternal plating,
                   MechInternal capacitorBank, MechInternal ventArray) {
            this(variant,
                    variant != null && !variant.twoArmed() ? armWeapon : null,
                    variant != null && variant.twoArmed() ? armWeapon : null,
                    variant != null && variant.twoArmed() ? armWeapon : null,
                    leftShoulder, rightShoulder, engine, plating, capacitorBank, ventArray);
        }

        /** The arm gun this fit carries: the centreline gun, or the left arm's on a two-armed chassis. */
        public MechWeaponComponent armWeapon() {
            return variant.twoArmed() ? leftArm : arms;
        }

        /** This fit with {@code component} on every arm mount the chassis has. */
        public Fit withArmWeapon(MechWeaponComponent component) {
            Fit fit = this;
            for (MechMountSlot slot : variant.armMounts()) fit = fit.withWeapon(slot, component);
            return fit;
        }

        /** This fit's internals around {@code weapons}' guns. */
        public Fit withWeaponsOf(Fit weapons) {
            return new Fit(variant, weapons.arms, weapons.leftArm, weapons.rightArm,
                    weapons.leftShoulder, weapons.rightShoulder, engine, plating, capacitorBank, ventArray);
        }

        /** The chassis's factory fit on industrial parts. */
        /**
         * A bought chassis as it leaves the fabricator: its plating and any factory-locked
         * hardware, which are the frame, and nothing else. Its engine and capacitor bank here are
         * only the budget's stand-ins; the frame itself has none and cannot drop until fitted.
         */
        public static Fit frame(MechVariant variant) {
            return new Fit(variant, integral(variant, MechMountSlot.ARMS) ? variant.arms : null,
                    integral(variant, MechMountSlot.LEFT_ARM) ? variant.leftArm : null,
                    integral(variant, MechMountSlot.RIGHT_ARM) ? variant.rightArm : null,
                    integral(variant, MechMountSlot.LEFT_SHOULDER) ? variant.leftShoulder : null,
                    integral(variant, MechMountSlot.RIGHT_SHOULDER) ? variant.rightShoulder : null,
                    null, null, null, null);
        }

        public static Fit stock(MechVariant variant) {
            return new Fit(variant, variant.arms, variant.leftArm, variant.rightArm,
                    variant.leftShoulder, variant.rightShoulder, null, null, null, null);
        }

        public static Fit of(CampaignMech mech) {
            return new Fit(mech.variant(), mech.arms(), mech.leftArm(), mech.rightArm(),
                    mech.leftShoulder(), mech.rightShoulder(),
                    mech.internal(MechInternal.Role.ENGINE), mech.internal(MechInternal.Role.PLATING),
                    mech.internal(MechInternal.Role.CAPACITOR_BANK), mech.internal(MechInternal.Role.VENT_ARRAY));
        }

        public MechWeaponComponent weaponAt(MechMountSlot slot) {
            return switch (slot) {
                case ARMS -> arms;
                case LEFT_ARM -> leftArm;
                case RIGHT_ARM -> rightArm;
                case LEFT_SHOULDER -> leftShoulder;
                case RIGHT_SHOULDER -> rightShoulder;
            };
        }

        public Fit withWeapon(MechMountSlot slot, MechWeaponComponent component) {
            return new Fit(variant,
                    slot == MechMountSlot.ARMS ? component : arms,
                    slot == MechMountSlot.LEFT_ARM ? component : leftArm,
                    slot == MechMountSlot.RIGHT_ARM ? component : rightArm,
                    slot == MechMountSlot.LEFT_SHOULDER ? component : leftShoulder,
                    slot == MechMountSlot.RIGHT_SHOULDER ? component : rightShoulder,
                    engine, plating, capacitorBank, ventArray);
        }

        public MechInternal internal(MechInternal.Role role) {
            return switch (role) {
                case ENGINE -> engine;
                case PLATING -> plating;
                case CAPACITOR_BANK -> capacitorBank;
                case VENT_ARRAY -> ventArray;
            };
        }

        /** Replaces the part in {@code role}; a null vent array empties the utility socket. */
        public Fit withInternal(MechInternal.Role role, MechInternal internal) {
            return new Fit(variant, arms, leftArm, rightArm, leftShoulder, rightShoulder,
                    role == MechInternal.Role.ENGINE ? internal : engine,
                    role == MechInternal.Role.PLATING ? internal : plating,
                    role == MechInternal.Role.CAPACITOR_BANK ? internal : capacitorBank,
                    role == MechInternal.Role.VENT_ARRAY ? internal : ventArray);
        }

        public float output() {
            return ratedOutput(variant) * engine.outputFactor;
        }

        public float fittedMass() {
            float mass = 0f;
            for (MechMountSlot slot : MechMountSlot.values()) {
                mass += MechFrameBudget.mass(weaponAt(slot));
            }
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

        public float fluxCapacity() {
            return capacitorBank.fluxCapacity;
        }

        public float dissipationPerSecond() {
            float dissipation = capacitorBank.dissipation + engine.dissipation
                    + (ventArray != null ? ventArray.dissipation : 0f);
            return Math.max(0f, dissipation);
        }

        public MechFrame frame() {
            return new MechFrame(armorCapacity(), moveSpeed(), fluxCapacity(), dissipationPerSecond());
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
