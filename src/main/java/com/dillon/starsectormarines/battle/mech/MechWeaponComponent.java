package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.weapon.RotaryFeedDef;
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

    // One gun per arm: a two-armed chassis carries a pair as two of these, one on each arm mount. On the
    // centreline mount the same gun is drawn as the nose chaingun (appearanceSelector(MechMountSlot)).
    CHAINGUN("component.mech-chaingun", "@gearMechWeaponComponentCHAINGUN", MountFamily.ARMS,
            HardpointType.BALLISTIC, 2, 2, "weapon.mech-chaingun",
            6, -1, LayeredMechAppearance.ARMS_CHAINGUN),
    // The better chaingun: the same gun on a faster drive and a bigger feed (mechs-nouns.md, Rotary guns).
    CHAINGUN_HF("component.mech-chaingun-hf", "@gearMechWeaponComponentCHAINGUNHF", MountFamily.ARMS,
            HardpointType.BALLISTIC, 2, 2, WeaponRegistry.MECH_CHAINGUN_HF_ID,
            6, -1, LayeredMechAppearance.ARMS_CHAINGUN),
    LINEAR_CANNON("component.mech-linear-cannon", "@gearMechWeaponComponentLINEARCANNON",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2, "weapon.mech-linear-cannon",
            1, -1, LayeredMechAppearance.ARMS_LINEAR_CANNON),
    SINGLE_HEAVY_CANNON("component.mech-heavy-cannon", "@gearMechWeaponComponentSINGLEHEAVYCANNON", MountFamily.ARMS,
            HardpointType.BALLISTIC, 2, 3, "weapon.mech-heavy-cannon",
            1, -1, LayeredMechAppearance.ARMS_HEAVY_CANNON),
    PULSE_LASER("component.mech-pulse-laser", "@gearMechWeaponComponentPULSELASER",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 2, "weapon.mech-pulse-laser",
            4, -1, LayeredMechAppearance.ARMS_PULSE_LASER),
    BASTION_AUTOCANNON("component.mech-bastion-autocannon",
            "@gearMechWeaponComponentBASTIONAUTOCANNON", MountFamily.ARMS,
            HardpointType.BALLISTIC, 2, 2, "weapon.mech-bastion-autocannon", 3, -1,
            LayeredMechAppearance.ARMS_BASTION_AUTOCANNON),
    DEMOLITION_CANNON("component.mech-demolition-cannon", "@gearMechWeaponComponentDEMOLITIONCANNON",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-demolition-cannon", 1, 5,
            LayeredMechAppearance.ARMS_DEMOLITION_CANNON),
    MUSTER_AUTOGUN("component.mech-muster-autogun", "@gearMechWeaponComponentMUSTERAUTOGUN",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-muster-autogun", 3, -1,
            LayeredMechAppearance.ARMS_MUSTER_AUTOGUN),

    QUARRY_BREAKER_CANNON("component.mech-quarry-breaker", "@gearMechWeaponComponentQUARRYBREAKERCANNON",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-quarry-breaker", 1, 4,
            LayeredMechAppearance.ARMS_QUARRY_BREAKER),
    // The light weapons: one-row parts that stock a small vehicle gun card (vehicle-card-sizes.md).
    LIGHT_MACHINE_GUN("component.mech-light-mg", "@gearMechWeaponComponentLIGHTMACHINEGUN",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 1,
            WeaponRegistry.MECH_LIGHT_MG_ID, 5, -1,
            LayeredMechAppearance.ARMS_LIGHT_MG),
    LIGHT_PULSE_LASER("component.mech-light-pulse-laser", "@gearMechWeaponComponentLIGHTPULSELASER",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 1,
            WeaponRegistry.MECH_LIGHT_PULSE_LASER_ID, 4, -1,
            LayeredMechAppearance.ARMS_LIGHT_PULSE_LASER),

    // The laser ladder (vehicle-card-sizes.md): a PD family (steady lances that run cool, a slight red motif) and a burst
    // family (short heavy volleys that vent, clean white and blue), each in three sizes. Named by their size in the
    // family: the light PD laser is LIGHT_PULSE_LASER above, the heavy burst laser BURST_LASER here.
    LIGHT_BURST_LASER("component.mech-light-burst-laser", "@gearMechWeaponComponentLIGHTBURSTLASER",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 1,
            WeaponRegistry.MECH_LIGHT_BURST_LASER_ID, 2, -1,
            LayeredMechAppearance.ARMS_LIGHT_BURST_LASER),
    MEDIUM_BURST_LASER("component.mech-medium-burst-laser", "@gearMechWeaponComponentMEDIUMBURSTLASER",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 2,
            WeaponRegistry.MECH_MEDIUM_BURST_LASER_ID, 3, -1,
            LayeredMechAppearance.ARMS_MEDIUM_BURST_LASER),
    BURST_LASER("component.mech-burst-laser", "@gearMechWeaponComponentBURSTLASER",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 3,
            WeaponRegistry.MECH_BURST_LASER_ID, 4, -1,
            LayeredMechAppearance.ARMS_BURST_LASER),
    PD_LASER("component.mech-pd-laser", "@gearMechWeaponComponentPDLASER",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 2,
            WeaponRegistry.MECH_PD_LASER_ID, 10, -1,
            LayeredMechAppearance.ARMS_PD_LASER),
    HEAVY_PD_LASER("component.mech-heavy-pd-laser", "@gearMechWeaponComponentHEAVYPDLASER",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 3,
            WeaponRegistry.MECH_HEAVY_PD_LASER_ID, 14, -1,
            LayeredMechAppearance.ARMS_HEAVY_PD_LASER),

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
            MountFamily.SHOULDER, HardpointType.ENERGY, 2, 3,
            "weapon.mech-shoulder-laser", 1, -1,
            LayeredMechAppearance.POD_SHOULDER_LASER),
    THERMAL_LANCE("component.mech-thermal-lance", "@gearMechWeaponComponentTHERMALLANCE",
            MountFamily.SHOULDER, HardpointType.ENERGY, 2, 1,
            "weapon.mech-thermal-lance", 1, -1,
            LayeredMechAppearance.POD_THERMAL_LANCE),
    SRM_3_QS("component.mech-srm-3-qs", "@gearMechWeaponComponentSRM3QS",
            MountFamily.SHOULDER, HardpointType.MISSILE, 2, 1,
            WeaponRegistry.MECH_SRM_3_QS_ID, 3, 3,
            LayeredMechAppearance.POD_SRM_3_QS);

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

    /**
     * A missile rack holding more than one salvo: a held trigger chains its salvos (`mechs-nouns.md`,
     * {@link com.dillon.starsectormarines.battle.combat.SalvoChain}).
     */
    public boolean chainsSalvos() {
        return hardpointType == HardpointType.MISSILE && ammoCapacity > 1;
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

    /**
     * A gun whose art is one barrel on the chassis's centreline (the heavy cannon, the breaching
     * cannons, the heavy lasers): it fits the centreline arms mount only, never a side arm. Every
     * other arm gun fits either; the chainguns and the Muster autogun change their drawing with the
     * mount instead ({@link #appearanceSelector(MechMountSlot)}).
     */
    public boolean centrelineOnly() {
        return switch (this) {
            case SINGLE_HEAVY_CANNON, DEMOLITION_CANNON, QUARRY_BREAKER_CANNON, BURST_LASER, HEAVY_PD_LASER -> true;
            default -> false;
        };
    }

    /**
     * The turret head a gun tank draws for a weapon of this family in its gun card, instead of the weapon's own drawing:
     * the laser tank's white and red laser heads, and the Onager's heads, its turret housing with the family's gun.
     */
    public enum TankHead { NONE, WHITE, RED, HEAVY_CANNON, AUTOCANNON, ROTARY, RAIL }

    /**
     * The turret head this weapon wears when fitted to a hull that carries one ({@code VehicleType.headFrames}): the
     * heavy (three-row) burst laser the white head the laser tank has always carried, the heavy PD laser the red head the
     * PD laser tank carried, the heavy cannon the Onager's single-cannon turret; the autocannons and breaker cannons
     * the Onager's autocannon head, the chainguns and the light machine gun its rotary head, the linear cannon its rail
     * head. A weapon whose head its hull does not carry, and every other weapon, is drawn as its own drawing on the
     * mount ring.
     */
    public TankHead tankHead() {
        return switch (this) {
            case BURST_LASER -> TankHead.WHITE;
            case HEAVY_PD_LASER -> TankHead.RED;
            case SINGLE_HEAVY_CANNON -> TankHead.HEAVY_CANNON;
            case BASTION_AUTOCANNON, MUSTER_AUTOGUN, DEMOLITION_CANNON, QUARRY_BREAKER_CANNON -> TankHead.AUTOCANNON;
            case CHAINGUN, CHAINGUN_HF, LIGHT_MACHINE_GUN -> TankHead.ROTARY;
            case LINEAR_CANNON -> TankHead.RAIL;
            default -> TankHead.NONE;
        };
    }

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
    /**
     * Representative projectiles emitted per trigger, not literal launcher tubes. A rotary gun has no
     * trigger pull; for it this is only the unit its rated cycle is quoted in.
     */
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

    /**
     * The selector the layered compositor draws this weapon with on {@code slot}. A chaingun or Muster autogun on the
     * centreline mount is drawn as the nose gun under the chassis's nose; on a side arm, a vehicle's turret
     * ({@code slot} null) or in stores it is drawn as the arm gun ({@link #appearanceSelector}). Every other weapon draws
     * the same on any mount.
     */
    public int appearanceSelector(MechMountSlot slot) {
        if (slot != MechMountSlot.ARMS) return appearanceSelector;
        return switch (this) {
            case CHAINGUN, CHAINGUN_HF -> LayeredMechAppearance.ARMS_NOSE_CHAINGUN;
            case MUSTER_AUTOGUN -> LayeredMechAppearance.ARMS_NOSE_MUSTER_AUTOGUN;
            default -> appearanceSelector;
        };
    }

    /** Whether this weapon wears its nose drawing on {@code slot} ({@link #appearanceSelector(MechMountSlot)}). */
    public boolean noseDrawingOn(MechMountSlot slot) {
        return appearanceSelector(slot) != appearanceSelector;
    }

    /**
     * The rotary feed of a held-trigger gun (the chainguns, the light machine gun and the autocannons), or null for a
     * gun fired a pull at a time.
     */
    public RotaryFeedDef rotary() { return weaponDef().rotary(); }

    /**
     * Mechanical trigger-to-trigger time, independent of flux headroom and pack supply. A rotary gun has no
     * trigger cycle; its figure is {@link #projectilesPerTrigger} rounds at its rated rate, so estimates that
     * read rounds over cycle read what the gun delivers over an engagement.
     */
    public float cycleSeconds() {
        return tuned("cycle", authoredCycleSeconds());
    }

    private float authoredCycleSeconds() {
        if (rotary() != null) return projectilesPerTrigger / rotary().ratedRoundsPerSecond();
        return switch (this) {
            // Unused: a rotary gun's cycle is read from its feed above.
            case CHAINGUN, CHAINGUN_HF, BASTION_AUTOCANNON, MUSTER_AUTOGUN, LIGHT_MACHINE_GUN -> 0f;
            case SRM_5, SRM_5_FRAG -> 1.8f;
            case SRM_15, SRM_15_FRAG -> 2.6f;
            case LRM_5, LRM_15, LRM_5_FRAG, LRM_15_FRAG -> 9f;
            case LINEAR_CANNON -> 2.8f;
            case SINGLE_HEAVY_CANNON -> 2.5f;
            case PULSE_LASER -> 2.1f;
            case DEMOLITION_CANNON -> 5.8f;
            case QUARRY_BREAKER_CANNON -> 5.5f;
            case SHOULDER_LASER_CANNON, SRM_3_QS -> 7f;
            case THERMAL_LANCE -> 4.8f;
            case LIGHT_PULSE_LASER -> 1.8f;
            case BURST_LASER -> 1.8f;
            case PD_LASER -> 1.6f;
            case HEAVY_PD_LASER -> 1.8f;
            case MEDIUM_BURST_LASER -> 1.6f;
            case LIGHT_BURST_LASER -> 1.4f;
        };
    }

    /**
     * Flux-hungry enough that the cannon competes with its carrier's launchers for the reservoir
     * (heavy-equipment-progression.md); {@code -Dbattle.mech.heavyCannonFlux=26} is the control.
     */
    private static final float HEAVY_CANNON_FLUX = Float.parseFloat(
            System.getProperty("battle.mech.heavyCannonFlux", "34"));

    /**
     * Whole-packet flux reservation: scheduled rounds cannot stall halfway through a salvo. A rotary gun
     * commits {@link #fluxPerRound} a round instead; this is that over {@link #projectilesPerTrigger} rounds.
     */
    public float fluxPerTrigger() {
        return rotary() != null ? fluxPerRound() * projectilesPerTrigger : tuned("flux", authoredFlux());
    }

    /** What one round of a rotary gun commits to the reservoir; {@link #fluxPerTrigger} for any other gun. */
    public float fluxPerRound() {
        RotaryFeedDef rotary = rotary();
        return rotary != null ? tuned("fluxPerRound", rotary.fluxPerRound()) : fluxPerTrigger();
    }

    /**
     * A balance control: {@code -Dbattle.mech.<axis>.<short id>=<value>} replaces one authored value, where the
     * short id is the component id without {@code component.mech-} (for example
     * {@code -Dbattle.mech.flux.bastion-autocannon=12}). Read once per component and axis.
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
            // Unused: a rotary gun's flux is authored per round on its weapon (fluxPerRound).
            case CHAINGUN, CHAINGUN_HF, BASTION_AUTOCANNON, MUSTER_AUTOGUN, LIGHT_MACHINE_GUN -> 0f;
            case SRM_5, SRM_5_FRAG -> 28f;
            case SRM_15, SRM_15_FRAG -> 42f;
            case LRM_5, LRM_5_FRAG -> 22f;
            case LRM_15, LRM_15_FRAG -> 40f;
            case LINEAR_CANNON -> 12f;

            case SINGLE_HEAVY_CANNON -> HEAVY_CANNON_FLUX;
            case DEMOLITION_CANNON, QUARRY_BREAKER_CANNON -> 26f;
            // The Prestige arm pays in mass; at 38 it lost to both Advanced arms on the same frame.
            case PULSE_LASER -> 13f;

            case SHOULDER_LASER_CANNON -> 35f;
            case THERMAL_LANCE -> 32f;
            case SRM_3_QS -> 24f;
            case LIGHT_PULSE_LASER -> 7f;
            // The burst laser is a flux hog, as on the tank; the PD laser runs cool.
            case BURST_LASER -> 38f;
            case PD_LASER -> 16f;
            case HEAVY_PD_LASER -> 28f;
            case MEDIUM_BURST_LASER -> 26f;
            case LIGHT_BURST_LASER -> 14f;
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
        if (slot == null) return false;
        if (mountFamily == MountFamily.SHOULDER) return slot.isShoulder();
        return slot == MechMountSlot.ARMS || (slot.isSideArm() && !centrelineOnly());
    }
}
