package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable hardware installed in one mech hardpoint. The underlying
 * weapon catalog owns projectile behavior; this component owns the rack
 * size, ammunition bin and visual shell. A displayed LRM/SRM number is a
 * MechWarrior-style weight/readability class, while {@link #projectilesPerTrigger}
 * is the smaller representative packet the ground sim actually emits.
 */
public enum MechWeaponComponent {

    DUAL_CHAINGUNS("component.mech-dual-chainguns", "@gearMechWeaponComponentDUALCHAINGUNS", MountFamily.ARMS,
            HardpointType.BALLISTIC, 3, 2, "weapon.mech-chaingun",
            12, -1, LayeredMechAppearance.ARMS_CHAINGUN),
    NOSE_CHAINGUN("component.mech-nose-chaingun", "@gearMechWeaponComponentNOSECHAINGUN", MountFamily.ARMS,
            HardpointType.BALLISTIC, 2, 2, "weapon.mech-chaingun",
            6, -1, LayeredMechAppearance.ARMS_NOSE_CHAINGUN),
    DUAL_LINEAR_CANNONS("component.mech-dual-linear-cannons", "@gearMechWeaponComponentDUALLINEARCANNONS",
            MountFamily.ARMS, HardpointType.BALLISTIC, 3, 2, "weapon.mech-linear-cannon",
            2, -1, LayeredMechAppearance.ARMS_LINEAR_CANNON),
    SINGLE_HEAVY_CANNON("component.mech-heavy-cannon", "@gearMechWeaponComponentSINGLEHEAVYCANNON", MountFamily.ARMS,
            HardpointType.BALLISTIC, 3, 2, "weapon.mech-heavy-cannon",
            1, -1, LayeredMechAppearance.ARMS_HEAVY_CANNON),
    DUAL_PULSE_LASERS("component.mech-dual-pulse-lasers", "@gearMechWeaponComponentDUALPULSELASERS",
            MountFamily.ARMS, HardpointType.ENERGY, 3, 2, "weapon.mech-pulse-laser",
            8, -1, LayeredMechAppearance.ARMS_PULSE_LASER),
    DUAL_BASTION_AUTOCANNONS("component.mech-bastion-autocannons",
            "@gearMechWeaponComponentDUALBASTIONAUTOCANNONS", MountFamily.ARMS,
            HardpointType.BALLISTIC, 3, 2, "weapon.mech-bastion-autocannon", 6, -1,
            LayeredMechAppearance.ARMS_BASTION_AUTOCANNON),
    DEMOLITION_CANNON("component.mech-demolition-cannon", "@gearMechWeaponComponentDEMOLITIONCANNON",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-demolition-cannon", 1, 5,
            LayeredMechAppearance.ARMS_DEMOLITION_CANNON),
    DUAL_MUSTER_AUTOGUNS("component.mech-dual-muster-autoguns", "@gearMechWeaponComponentDUALMUSTERAUTOGUNS",
            MountFamily.ARMS, HardpointType.BALLISTIC, 3, 2,
            "weapon.mech-muster-autogun", 6, -1,
            LayeredMechAppearance.ARMS_MUSTER_AUTOGUN),
    NOSE_MUSTER_AUTOGUN("component.mech-nose-muster-autogun", "@gearMechWeaponComponentNOSEMUSTERAUTOGUN",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-muster-autogun", 3, -1,
            LayeredMechAppearance.ARMS_NOSE_MUSTER_AUTOGUN),
    QUARRY_BREAKER_CANNON("component.mech-quarry-breaker", "@gearMechWeaponComponentQUARRYBREAKERCANNON",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-quarry-breaker", 1, 4,
            LayeredMechAppearance.ARMS_QUARRY_BREAKER),

    // The anti-armor racks: a contact warhead fired only at hardened targets (mechs-nouns.md, Missile warheads).
    SRM_5("component.mech-srm-5", "@gearMechWeaponComponentSRM5", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 2, Variants.srm(),
            2, 6, LayeredMechAppearance.POD_SMALL_SRM),
    SRM_15("component.mech-srm-15", "@gearMechWeaponComponentSRM15", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 3, Variants.srm(),
            4, 6, LayeredMechAppearance.POD_LARGE_SRM),
    LRM_5("component.mech-lrm-5", "@gearMechWeaponComponentLRM5", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 2, Variants.lrm(),
            2, 4, LayeredMechAppearance.POD_SMALL_LRM),
    LRM_15("component.mech-lrm-15", "@gearMechWeaponComponentLRM15", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 3, Variants.lrm(),
            5, 3, LayeredMechAppearance.POD_LARGE_LRM),
    // The fragmentation racks: the same rack sizes with an area warhead at low penetration.
    SRM_5_FRAG("component.mech-srm-5-frag", "@gearMechWeaponComponentSRM5FRAG", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 2, WeaponRegistry.MECH_SRM_FRAG_ID,
            2, 6, LayeredMechAppearance.POD_SMALL_SRM),
    SRM_15_FRAG("component.mech-srm-15-frag", "@gearMechWeaponComponentSRM15FRAG", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 3, WeaponRegistry.MECH_SRM_FRAG_ID,
            4, 6, LayeredMechAppearance.POD_LARGE_SRM),
    LRM_5_FRAG("component.mech-lrm-5-frag", "@gearMechWeaponComponentLRM5FRAG", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 2, WeaponRegistry.MECH_LRM_FRAG_ID,
            2, 4, LayeredMechAppearance.POD_SMALL_LRM),
    LRM_15_FRAG("component.mech-lrm-15-frag", "@gearMechWeaponComponentLRM15FRAG", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 3, WeaponRegistry.MECH_LRM_FRAG_ID,
            5, 3, LayeredMechAppearance.POD_LARGE_LRM),
    SHOULDER_LASER_CANNON("component.mech-shoulder-laser", "@gearMechWeaponComponentSHOULDERLASERCANNON",
            MountFamily.SHOULDER, HardpointType.ENERGY, 3, 2,
            "weapon.mech-shoulder-laser", 1, -1,
            LayeredMechAppearance.POD_SHOULDER_LASER),
    THERMAL_LANCE("component.mech-thermal-lance", "@gearMechWeaponComponentTHERMALLANCE",
            MountFamily.SHOULDER, HardpointType.ENERGY, 1, 2,
            "weapon.mech-thermal-lance", 1, -1,
            LayeredMechAppearance.POD_THERMAL_LANCE),
    PIONEER_ROCKET_CRADLE("component.mech-pioneer-rocket", "@gearMechWeaponComponentPIONEERROCKETCRADLE",
            MountFamily.SHOULDER, HardpointType.MISSILE, 1, 1,
            "weapon.mech-pioneer-rocket", 4, 3,
            LayeredMechAppearance.POD_PIONEER_ROCKET);

    /**
     * Whether the split into anti-armor and fragmentation racks is live.
     * {@code -Dbattle.mech.missileVariants=false} is the control: the anti-armor ids fire the
     * single warhead every rack had before the split, at any target.
     */
    public static boolean missileVariants() { return Variants.ON; }

    /** Read in the constructors, before the enum's own statics exist. */
    private static final class Variants {
        static final boolean ON = Boolean.parseBoolean(
                System.getProperty("battle.mech.missileVariants", "true"));

        static String srm() { return ON ? WeaponRegistry.MECH_SRM_POD_ID : WeaponRegistry.MECH_SRM_LEGACY_ID; }

        static String lrm() { return ON ? WeaponRegistry.MECH_LRM_ARTILLERY_ID : WeaponRegistry.MECH_LRM_LEGACY_ID; }
    }

    /** An anti-armor rack: it holds fire at anything that is not a hardened target. */
    public boolean antiArmorOnly() {
        return Variants.ON && switch (this) {
            case SRM_5, SRM_15, LRM_5, LRM_15 -> true;
            default -> false;
        };
    }

    /** The fragmentation rack of the same size, or null for anything that is not an anti-armor rack. */
    public MechWeaponComponent fragCounterpart() {
        return switch (this) {
            case SRM_5 -> SRM_5_FRAG;
            case SRM_15 -> SRM_15_FRAG;
            case LRM_5 -> LRM_5_FRAG;
            case LRM_15 -> LRM_15_FRAG;
            default -> null;
        };
    }

    public enum MountFamily { ARMS, SHOULDER }
    public enum HardpointType { BALLISTIC, ENERGY, MISSILE }

    public final String id;
    private final String displayName;

    public String displayName() { return Strings.resolve(displayName); }
    public final MountFamily mountFamily;
    public final HardpointType hardpointType;
    public final int footprintColumns;
    public final int footprintRows;
    public final int slotCost;
    /** Stable catalog id for the installed gun or launcher. */
    public final String weaponId;
    /** Representative projectiles emitted per trigger, not literal launcher tubes. */
    public final int projectilesPerTrigger;
    /** Trigger pulls available at full supply; negative means unlimited. */
    public final int ammoCapacity;
    /** Arms or shoulder selector consumed by the layered compositor. */
    public final int appearanceSelector;

    MechWeaponComponent(String id, String displayName, MountFamily mountFamily,
                        HardpointType hardpointType,
                        int footprintColumns, int footprintRows,
                        String weaponId, int projectilesPerTrigger,
                        int ammoCapacity, int appearanceSelector) {
        this.id = id;
        this.displayName = displayName;
        this.mountFamily = mountFamily;
        this.hardpointType = hardpointType;
        if (footprintColumns < 1
                || footprintColumns > MechFittingLayout.MAX_GRID_COLUMNS
                || footprintRows < 1
                || footprintRows > MechFittingLayout.MAX_GRID_ROWS) {
            throw new IllegalArgumentException("weapon footprint must fit the common grid");
        }
        this.footprintColumns = footprintColumns;
        this.footprintRows = footprintRows;
        this.slotCost = footprintColumns * footprintRows;
        this.weaponId = weaponId;
        this.projectilesPerTrigger = projectilesPerTrigger;
        this.ammoCapacity = ammoCapacity;
        this.appearanceSelector = appearanceSelector;
    }

    /** Mechanical trigger-to-trigger time, independent of flux headroom and pack supply. */
    public float cycleSeconds() {
        return tuned("cycle", authoredCycleSeconds());
    }

    private float authoredCycleSeconds() {
        return switch (this) {
            case NOSE_CHAINGUN -> .65f;
            case DUAL_CHAINGUNS -> 1.05f;
            case SRM_5, SRM_5_FRAG -> 1.8f;
            case SRM_15, SRM_15_FRAG -> 2.6f;
            case LRM_5, LRM_15, LRM_5_FRAG, LRM_15_FRAG -> 9f;
            case DUAL_LINEAR_CANNONS -> 2.8f;
            case SINGLE_HEAVY_CANNON -> 2.5f;
            case DUAL_PULSE_LASERS -> 2.1f;
            case DUAL_BASTION_AUTOCANNONS -> 2.6f;
            case DEMOLITION_CANNON -> 5.8f;
            case DUAL_MUSTER_AUTOGUNS, NOSE_MUSTER_AUTOGUN -> 2.4f;
            case QUARRY_BREAKER_CANNON -> 5.5f;
            case SHOULDER_LASER_CANNON, PIONEER_ROCKET_CRADLE -> 7f;
            case THERMAL_LANCE -> 4.8f;
        };
    }

    /**
     * Flux-hungry enough that the cannon competes with its carrier's launchers for the reservoir
     * (heavy-equipment-progression.md); {@code -Dbattle.mech.heavyCannonFlux=26} is the control.
     */
    private static final float HEAVY_CANNON_FLUX = Float.parseFloat(
            System.getProperty("battle.mech.heavyCannonFlux", "34"));

    /** Whole-packet flux reservation: scheduled rounds cannot stall halfway through a salvo. */
    public float fluxPerTrigger() {
        return tuned("flux", authoredFlux());
    }

    /**
     * A balance control: {@code -Dbattle.mech.<axis>.<short id>=<value>} replaces one authored value, where the
     * short id is the component id without {@code component.mech-} (for example
     * {@code -Dbattle.mech.flux.bastion-autocannons=24}). Read once per component and axis.
     */
    public float tuned(String axis, float authored) {
        float override = TUNING.computeIfAbsent(axis + "." + id.substring(id.indexOf("mech-") + 5), key -> {
            String value = System.getProperty("battle.mech." + key);
            return value != null ? Float.parseFloat(value) : Float.NaN;
        });
        return Float.isNaN(override) ? authored : override;
    }

    private static final Map<String, Float> TUNING = new ConcurrentHashMap<>();

    private float authoredFlux() {
        return switch (this) {
            case NOSE_CHAINGUN -> 18f;
            case DUAL_CHAINGUNS -> 30f;
            case SRM_5, SRM_5_FRAG -> 28f;
            case SRM_15, SRM_15_FRAG -> 42f;
            case LRM_5, LRM_5_FRAG -> 22f;
            case LRM_15, LRM_15_FRAG -> 40f;
            case DUAL_LINEAR_CANNONS -> 24f;
            // Flux-hungry enough that the bastion's range costs its carrier reservoir, as the heavy cannon's does.
            case DUAL_BASTION_AUTOCANNONS -> 28f;
            case SINGLE_HEAVY_CANNON -> HEAVY_CANNON_FLUX;
            case DEMOLITION_CANNON, QUARRY_BREAKER_CANNON -> 26f;
            // The Prestige arm pays in mass; at 38 it lost to both Advanced arms on the same frame.
            case DUAL_PULSE_LASERS -> 26f;
            case DUAL_MUSTER_AUTOGUNS -> 22f;
            case NOSE_MUSTER_AUTOGUN -> 14f;
            case SHOULDER_LASER_CANNON -> 35f;
            case THERMAL_LANCE -> 32f;
            case PIONEER_ROCKET_CRADLE -> 24f;
        };
    }

    public String cycleLabel() {
        return switch (hardpointType) {
            case BALLISTIC -> Strings.get("gearCycleCycler");
            case MISSILE -> Strings.get("gearCycleLoader");
            case ENERGY -> Strings.get("gearCycleCapacitor");
        };
    }

    public WeaponDef weaponDef() { return WeaponRegistry.require(weaponId); }

    public static MechWeaponComponent findById(String id) {
        if (id == null) return null;
        for (MechWeaponComponent component : values()) {
            if (component.id.equals(id)) return component;
        }
        return null;
    }

    public static MechWeaponComponent resolve(String id, MechWeaponComponent fallback) {
        MechWeaponComponent component = findById(id);
        return component != null ? component : fallback;
    }

    public boolean accepts(MechMountSlot slot) {
        return mountFamily == MountFamily.ARMS
                ? slot == MechMountSlot.ARMS
                : slot.isShoulder();
    }
}
