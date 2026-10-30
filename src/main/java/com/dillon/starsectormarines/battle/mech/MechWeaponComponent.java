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
 * size, ammunition bin and visual shell. The number in a missile rack's name is
 * its buffer, the missiles it holds ready (`mechs-nouns.md`, Missile racks):
 * {@link #projectilesPerTrigger} missiles a salvo times {@link #ammoCapacity}
 * salvos, so an LRM-5 is one salvo of five and an LRM-15 three.
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
    // The cannon families are named <Family>-<N>, like the racks (mechs-nouns.md, Weapon names): HC heavy cannons,
    // AC autocannons, DC demolition cannons; (QS) is the QuickSell budget build of a family's gun.
    // The HC-10: a short single-shot armour-piercing cannon that fits a side arm as well as the centreline.
    HC_10("component.mech-hc-10", "@gearMechWeaponComponentHC10",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2, "weapon.mech-hc-10",
            1, -1, LayeredMechAppearance.ARMS_HC_10),
    HC_20("component.mech-hc-20", "@gearMechWeaponComponentHC20", MountFamily.ARMS,
            HardpointType.BALLISTIC, 2, 3, "weapon.mech-hc-20",
            1, -1, LayeredMechAppearance.ARMS_HC_20),
    // The lasers are named <Family>-<N> by how they fire (mechs-nouns.md, Weapon names): BL beam lasers (held), PL pulse
    // lasers (a burst a trigger), LL lance lasers (one lance a trigger); N is the footprint's rows. A PL moniker marks a
    // pulse laser built for one job: (AP) fewer, harder pulses that pierce plate, (Sweep) a long stream of light pulses.
    PL_2_AP("component.mech-pl-2-ap", "@gearMechWeaponComponentPL2AP",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 2, "weapon.mech-pl-2-ap",
            4, -1, LayeredMechAppearance.ARMS_PL_2_AP),
    AC_6("component.mech-ac-6",
            "@gearMechWeaponComponentAC6", MountFamily.ARMS,
            HardpointType.BALLISTIC, 2, 2, "weapon.mech-ac-6", 3, -1,
            LayeredMechAppearance.ARMS_AC_6),
    DC_5("component.mech-dc-5", "@gearMechWeaponComponentDC5",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-dc-5", 1, 5,
            LayeredMechAppearance.ARMS_DC_5),
    AC_6_QS("component.mech-ac-6-qs", "@gearMechWeaponComponentAC6QS",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-ac-6-qs", 3, -1,
            LayeredMechAppearance.ARMS_AC_6_QS),

    DC_4_QS("component.mech-dc-4-qs", "@gearMechWeaponComponentDC4QS",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 2,
            "weapon.mech-dc-4-qs", 1, 4,
            LayeredMechAppearance.ARMS_DC_4_QS),
    // The light weapons: one-row parts that stock a small vehicle gun card (vehicle-card-sizes.md).
    LIGHT_MACHINE_GUN("component.mech-light-mg", "@gearMechWeaponComponentLIGHTMACHINEGUN",
            MountFamily.ARMS, HardpointType.BALLISTIC, 2, 1,
            WeaponRegistry.MECH_LIGHT_MG_ID, 5, -1,
            LayeredMechAppearance.ARMS_LIGHT_MG),
    // The BL-1: a held beam (mechs-nouns.md, Beam weapons), as is the BL-2 below.
    BL_1("component.mech-bl-1", "@gearMechWeaponComponentBL1",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 1,
            WeaponRegistry.MECH_BL_1_ID, 4, -1,
            LayeredMechAppearance.ARMS_BL_1),

    // The laser ladder (vehicle-card-sizes.md): the red-banded lasers (BL-1, BL-2 and PL-3 (Sweep)) and the pulse lasers
    // (short heavy volleys that vent, clean white and blue: PL-1, PL-2, PL-3). The BL-1 and BL-2 are held beams; the
    // PL-3 (Sweep) keeps its long stream of pulses a pull at a time.
    PL_1("component.mech-pl-1", "@gearMechWeaponComponentPL1",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 1,
            WeaponRegistry.MECH_PL_1_ID, 2, -1,
            LayeredMechAppearance.ARMS_PL_1),
    PL_2("component.mech-pl-2", "@gearMechWeaponComponentPL2",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 2,
            WeaponRegistry.MECH_PL_2_ID, 3, -1,
            LayeredMechAppearance.ARMS_PL_2),
    PL_3("component.mech-pl-3", "@gearMechWeaponComponentPL3",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 3,
            WeaponRegistry.MECH_PL_3_ID, 4, -1,
            LayeredMechAppearance.ARMS_PL_3),
    BL_2("component.mech-bl-2", "@gearMechWeaponComponentBL2",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 2,
            WeaponRegistry.MECH_BL_2_ID, 10, -1,
            LayeredMechAppearance.ARMS_BL_2),
    PL_3_SWEEP("component.mech-pl-3-sweep", "@gearMechWeaponComponentPL3SWEEP",
            MountFamily.ARMS, HardpointType.ENERGY, 2, 3,
            WeaponRegistry.MECH_PL_3_SWEEP_ID, 14, -1,
            LayeredMechAppearance.ARMS_PL_3_SWEEP),

    // The anti-armor racks: a contact warhead fired only at hardened targets (mechs-nouns.md, Missile warheads).
    SRM_5("component.mech-srm-5", "@gearMechWeaponComponentSRM5", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 2, WeaponRegistry.MECH_SRM_ID,
            5, 1, LayeredMechAppearance.POD_SMALL_SRM),
    SRM_15("component.mech-srm-15", "@gearMechWeaponComponentSRM15", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 3, WeaponRegistry.MECH_SRM_ID,
            5, 3, LayeredMechAppearance.POD_LARGE_SRM),
    LRM_5("component.mech-lrm-5", "@gearMechWeaponComponentLRM5", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 2, WeaponRegistry.MECH_LRM_ID,
            5, 1, LayeredMechAppearance.POD_SMALL_LRM),
    LRM_15("component.mech-lrm-15", "@gearMechWeaponComponentLRM15", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 3, WeaponRegistry.MECH_LRM_ID,
            5, 3, LayeredMechAppearance.POD_LARGE_LRM),
    // The fragmentation racks: the same rack sizes with an area warhead at low penetration.
    SRM_5_FRAG("component.mech-srm-5-frag", "@gearMechWeaponComponentSRM5FRAG", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 2, WeaponRegistry.MECH_SRM_FRAG_ID,
            5, 1, LayeredMechAppearance.POD_SMALL_SRM),
    SRM_15_FRAG("component.mech-srm-15-frag", "@gearMechWeaponComponentSRM15FRAG", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 3, WeaponRegistry.MECH_SRM_FRAG_ID,
            5, 3, LayeredMechAppearance.POD_LARGE_SRM),
    LRM_5_FRAG("component.mech-lrm-5-frag", "@gearMechWeaponComponentLRM5FRAG", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 2, WeaponRegistry.MECH_LRM_FRAG_ID,
            5, 1, LayeredMechAppearance.POD_SMALL_LRM),
    LRM_15_FRAG("component.mech-lrm-15-frag", "@gearMechWeaponComponentLRM15FRAG", MountFamily.SHOULDER,
            HardpointType.MISSILE, 2, 3, WeaponRegistry.MECH_LRM_FRAG_ID,
            5, 3, LayeredMechAppearance.POD_LARGE_LRM),
    LL_3("component.mech-ll-3", "@gearMechWeaponComponentLL3",
            MountFamily.SHOULDER, HardpointType.ENERGY, 2, 3,
            "weapon.mech-ll-3", 1, -1,
            LayeredMechAppearance.POD_LL_3),
    THERMAL_LANCE("component.mech-thermal-lance", "@gearMechWeaponComponentTHERMALLANCE",
            MountFamily.SHOULDER, HardpointType.ENERGY, 2, 1,
            "weapon.mech-thermal-lance", 1, -1,
            LayeredMechAppearance.POD_THERMAL_LANCE),
    SRM_3_QS("component.mech-srm-3-qs", "@gearMechWeaponComponentSRM3QS",
            MountFamily.SHOULDER, HardpointType.MISSILE, 2, 1,
            WeaponRegistry.MECH_SRM_3_QS_ID, 3, 1,
            LayeredMechAppearance.POD_SRM_3_QS);

    /**
     * A missile rack holding more than one salvo: a held trigger chains its salvos (`mechs-nouns.md`,
     * {@link com.dillon.starsectormarines.battle.combat.SalvoChain}).
     */
    public boolean chainsSalvos() {
        return hardpointType == HardpointType.MISSILE && ammoCapacity > 1;
    }

    /** An anti-armor rack: it holds fire at anything that is not a hardened target. */
    public boolean antiArmorOnly() {
        return switch (this) {
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
     * A gun whose art is one barrel on the chassis's centreline (the HC-20, the DC breaching
     * cannons, the three-row PL lasers): it fits the centreline arms mount only, never a side arm. Every
     * other arm gun fits either; the chainguns and the AC-6 (QS) change their drawing with the
     * mount instead ({@link #appearanceSelector(MechMountSlot)}).
     */
    public boolean centrelineOnly() {
        return switch (this) {
            case HC_20, DC_5, DC_4_QS, PL_3, PL_3_SWEEP -> true;
            default -> false;
        };
    }

    /**
     * The turret head a gun tank draws for a weapon of this family in its gun card, instead of the weapon's own drawing:
     * the laser tank's white and red laser heads, and the Onager's heads, its turret housing with the family's gun.
     */
    public enum TankHead { NONE, WHITE, RED, HEAVY_CANNON, AUTOCANNON, ROTARY }

    /**
     * The turret head this weapon wears when fitted to a hull that carries one ({@code VehicleType.headFrames}): the
     * PL-3 the white head the laser tank has always carried, the PL-3 (Sweep) the red head the retired PD laser tank
     * carried, the HC cannons (the HC-20 and the HC-10, both one large bore) the Onager's single-cannon
     * turret; the AC autocannons and DC demolition cannons the Onager's autocannon head, the chainguns and the light
     * machine gun its rotary head. A weapon whose head its hull does not carry, and every other weapon, is drawn as its own drawing on the
     * mount ring.
     */
    public TankHead tankHead() {
        return switch (this) {
            case PL_3 -> TankHead.WHITE;
            case PL_3_SWEEP -> TankHead.RED;
            case HC_10, HC_20 -> TankHead.HEAVY_CANNON;
            case AC_6, AC_6_QS, DC_5, DC_4_QS -> TankHead.AUTOCANNON;
            case CHAINGUN, CHAINGUN_HF, LIGHT_MACHINE_GUN -> TankHead.ROTARY;
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
     * Projectiles emitted per trigger: a missile rack's salvo. A rotary gun has no trigger pull; for it this is
     * only the unit its rated cycle is quoted in.
     */
    public final int projectilesPerTrigger;
    /**
     * Trigger pulls available at full supply, a missile rack's salvos; negative means unlimited. A rack's
     * buffer, the number in its name, is this times {@link #projectilesPerTrigger} ({@link #readyRounds}).
     */
    public final int ammoCapacity;

    /**
     * The rounds a full mount holds ready: a missile rack's buffer, the number in its name. Zero for a weapon with
     * no limit.
     */
    public int readyRounds() {
        return ammoCapacity < 0 ? 0 : ammoCapacity * projectilesPerTrigger;
    }
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
     * The selector the layered compositor draws this weapon with on {@code slot}. A chaingun or AC-6 (QS) on the
     * centreline mount is drawn as the nose gun under the chassis's nose; on a side arm, a vehicle's turret
     * ({@code slot} null) or in stores it is drawn as the arm gun ({@link #appearanceSelector}). Every other weapon draws
     * the same on any mount.
     */
    public int appearanceSelector(MechMountSlot slot) {
        if (slot != MechMountSlot.ARMS) return appearanceSelector;
        return switch (this) {
            case CHAINGUN, CHAINGUN_HF -> LayeredMechAppearance.ARMS_NOSE_CHAINGUN;
            case AC_6_QS -> LayeredMechAppearance.ARMS_NOSE_AC_6_QS;
            default -> appearanceSelector;
        };
    }

    /** Whether this weapon wears its nose drawing on {@code slot} ({@link #appearanceSelector(MechMountSlot)}). */
    public boolean noseDrawingOn(MechMountSlot slot) {
        return appearanceSelector(slot) != appearanceSelector;
    }

    /**
     * The held-trigger feed of a gun that fires while held: a rotary gun's barrels and belt (the chainguns, the light
     * machine gun and the autocannons) or a beam's emitter (the BL-1 and the BL-2,
     * {@link RotaryFeedDef#beam()}); null for a gun fired a pull at a time.
     */
    public RotaryFeedDef rotary() { return weaponDef().rotary(); }

    /**
     * Mechanical trigger-to-trigger time, independent of flux headroom and pack supply. A rotary gun has no
     * trigger cycle; its figure is {@link #projectilesPerTrigger} rounds at its rated rate, so estimates that
     * read rounds over cycle read what the gun delivers over an engagement.
     */
    public float cycleSeconds() {
        return triggerInterval(tuned("cycle", authoredCycleSeconds()), burstSeconds());
    }

    /**
     * Seconds from a trigger's first round to its last: its {@link #projectilesPerTrigger} rounds at the weapon's
     * burst spacing, so a pulse laser's click is a short stutter of pulses (`mechs-nouns.md`, Pulse lasers). Zero for
     * a rotary gun, which has no trigger pull.
     */
    public float burstSeconds() {
        if (rotary() != null) return 0f;
        return Math.max(0, projectilesPerTrigger - 1) * Math.max(0f, weaponDef().burstSpacing);
    }

    /** A trigger cannot come round again before its own burst has left the gun ({@code MechWeaponMount#triggerReady}). */
    static float triggerInterval(float cycleSeconds, float burstSeconds) {
        return Math.max(cycleSeconds, burstSeconds);
    }

    private float authoredCycleSeconds() {
        if (rotary() != null) return projectilesPerTrigger / rotary().ratedRoundsPerSecond();
        return switch (this) {
            // Unused: a rotary gun's cycle, and a beam's, is read from its feed above.
            case CHAINGUN, CHAINGUN_HF, AC_6, AC_6_QS, LIGHT_MACHINE_GUN, BL_1, BL_2 -> 0f;
            // A rack's salvos are the same five missiles at every size, so a family cycles alike; a single-salvo
            // rack's real wait is its recharge, which the loader owns.
            case SRM_5, SRM_5_FRAG, SRM_15, SRM_15_FRAG -> 3.5f;
            case LRM_5, LRM_15, LRM_5_FRAG, LRM_15_FRAG -> 9f;
            case HC_10 -> 2.8f;
            case HC_20 -> 2.5f;
            case PL_2_AP -> 2.1f;
            case DC_5 -> 5.8f;
            case DC_4_QS -> 5.5f;
            case LL_3, SRM_3_QS -> 7f;
            case THERMAL_LANCE -> 4.8f;
            case PL_3 -> 1.8f;
            case PL_3_SWEEP -> 1.8f;
            case PL_2 -> 1.6f;
            case PL_1 -> 1.4f;
        };
    }

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
     * {@code -Dbattle.mech.flux.ac-6=12}). Read once per component and axis.
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
            // Unused: a rotary gun's flux is authored per round on its weapon (fluxPerRound), a beam's a second.
            case CHAINGUN, CHAINGUN_HF, AC_6, AC_6_QS, LIGHT_MACHINE_GUN, BL_1, BL_2 -> 0f;
            // One salvo of five costs the same from either size of rack; a chained salvo pays half (SalvoChain), so
            // a 15-rack's three salvos cost 80 of the stock 100.
            case SRM_5, SRM_5_FRAG, SRM_15, SRM_15_FRAG, LRM_5, LRM_5_FRAG, LRM_15, LRM_15_FRAG -> 40f;
            case HC_10 -> 18f;
            // Flux-hungry enough that the cannon competes with its carrier's launchers for the reservoir
            // (heavy-equipment-progression.md).
            case HC_20 -> 34f;
            case DC_5, DC_4_QS -> 26f;
            // The Prestige arm pays in mass; at 38 it lost to both Advanced arms on the same frame.
            case PL_2_AP -> 13f;

            case LL_3 -> 35f;
            case THERMAL_LANCE -> 32f;
            case SRM_3_QS -> 24f;
            // The PL-3 is a flux hog, as on the tank.
            case PL_3 -> 38f;
            case PL_3_SWEEP -> 28f;
            case PL_2 -> 26f;
            case PL_1 -> 14f;
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

    /**
     * The ids a save may still hold for a part that was renamed, mapped to the part that succeeds it: the cannons before
     * they took {@code <Family>-<N>} names, the linear cannon, whose slot the HC-10 took, and the lasers before they took
     * BL, PL and LL names.
     */
    private static final Map<String, String> RETIRED_IDS = Map.ofEntries(
            Map.entry("component.mech-muster-autogun", "component.mech-ac-6-qs"),
            Map.entry("component.mech-bastion-autocannon", "component.mech-ac-6"),
            Map.entry("component.mech-linear-cannon", "component.mech-hc-10"),
            Map.entry("component.mech-heavy-cannon", "component.mech-hc-20"),
            Map.entry("component.mech-demolition-cannon", "component.mech-dc-5"),
            Map.entry("component.mech-quarry-breaker", "component.mech-dc-4-qs"),
            Map.entry("component.mech-light-pulse-laser", "component.mech-bl-1"),
            Map.entry("component.mech-pd-laser", "component.mech-bl-2"),
            Map.entry("component.mech-light-burst-laser", "component.mech-pl-1"),
            Map.entry("component.mech-medium-burst-laser", "component.mech-pl-2"),
            Map.entry("component.mech-burst-laser", "component.mech-pl-3"),
            Map.entry("component.mech-pulse-laser", "component.mech-pl-2-ap"),
            Map.entry("component.mech-heavy-pd-laser", "component.mech-pl-3-sweep"),
            Map.entry("component.mech-shoulder-laser", "component.mech-ll-3"));

    /** The part {@code id} names, a retired id resolving to its successor ({@link #RETIRED_IDS}); null for any other. */
    public static MechWeaponComponent findById(String id) {
        if (id == null) return null;
        String current = RETIRED_IDS.getOrDefault(id, id);
        for (MechWeaponComponent component : values()) {
            if (component.id.equals(current)) return component;
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
