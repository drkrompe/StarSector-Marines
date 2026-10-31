package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.drone.DroneRole;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.List;
import java.util.Map;

/**
 * A non-weapon fitting that changes the machine: engine, plating, capacitor bank, vent array, missile loader, spall
 * liner or ready rack. One machine, one part catalog: a mech and a vehicle fit the same internals, and whether one goes
 * on a machine is its own requirements ({@link #unmet}) against what the chassis offers ({@link MachineCapabilities}),
 * never which kind of machine it is. The chassis's own band ({@link MechFrameBudget}) bounds what an engine or plating
 * can buy back.
 *
 * <p>Higher tiers take more room: a larger footprint, or more mass against the engine's output.
 * Plating covers the whole frame but has its own point on the doll ({@link SocketId#PLATING}), the
 * engine has the core, and the rest share the one
 * {@link SocketId#AUXILIARY} compartment, whose rules are {@link MechCompartment}'s.</p>
 */
public enum MechInternal {

    // Every chassis's CORE socket holds a 2x2 engine, so an engine's footprint never costs room;
    // its mass does. Without it the top engine cost nothing over the uprated one, and the best of
    // everything fitted on the Hound and the Bulwark.
    INDUSTRIAL_ENGINE("internal.engine-industrial", "@gearInternalIndustrialEngine", Role.ENGINE,
            EquipmentAccessTier.COMMON, 2, 1, 0f, 1.00f, 0f, 0f, 0f, 0f),
    UPRATED_ENGINE("internal.engine-uprated", "@gearInternalUpratedEngine", Role.ENGINE,
            EquipmentAccessTier.ADVANCED, 2, 2, 1f, 1.25f, 0.5f, 0f, 0f, 0f),
    MILITARY_ENGINE("internal.engine-military", "@gearInternalMilitaryEngine", Role.ENGINE,
            EquipmentAccessTier.PRESTIGE, 2, 2, 3f, 1.50f, 1.0f, 0f, 0f, -0.5f),

    FACTORY_PLATING("internal.plating-factory", "@gearInternalFactoryPlating", Role.PLATING,
            EquipmentAccessTier.COMMON, 2, 1, 0f, 0f, 0f, 0f, 0f, 0f),
    COMPOSITE_PLATING("internal.plating-composite", "@gearInternalCompositePlating", Role.PLATING,
            EquipmentAccessTier.ADVANCED, 2, 1, 1.0f, 0f, 0f, 0.5f, 0f, 0f),
    HEAVY_PLATING("internal.plating-heavy", "@gearInternalHeavyPlating", Role.PLATING,
            EquipmentAccessTier.PRESTIGE, 2, 1, 2.5f, 0f, 0f, 1.0f, 0f, 0f),

    FACTORY_CAPACITOR_BANK("internal.capacitor-bank-factory", "@gearInternalFactoryCapacitorBank", Role.CAPACITOR_BANK,
            EquipmentAccessTier.COMMON, 2, 1, 0f, 0f, 0f, 0f, 100f, 6f),
    DOUBLE_CAPACITOR_BANK("internal.capacitor-bank-double", "@gearInternalDoubleCapacitorBank", Role.CAPACITOR_BANK,
            EquipmentAccessTier.ADVANCED, 2, 1, 3f, 0f, 0f, 0f, 120f, 7.5f),
    // The military bank keeps the double's reservoir and adds dissipation. Above about 126 a bigger
    // reservoir changes which weapon gets the flux, not how much flux there is. When a vented
    // reservoir of size C releases, the two arm chainguns (15 flux each) fire first. A 42-flux SRM-15
    // then clears the 0.9C gate before the chainguns cycle again only if
    // C > 2 * (72 - 1.05 * dissipation). That is about 126 on the refit Bulwark (8.5/s). Past it the
    // refit fires mostly SRMs and out-guns its pre-cut frame by a further ~4%. 120 stays below
    // the threshold for any dissipation up to 11.4/s.
    // That reasoning dates from burst-fed arm chainguns. They are rotary guns now and commit flux a round at
    // a time (mechs-nouns.md, Rotary guns), so re-measure with armorMatchup before resizing a bank on it.
    MILITARY_CAPACITOR_BANK("internal.capacitor-bank-military", "@gearInternalMilitaryCapacitorBank", Role.CAPACITOR_BANK,
            EquipmentAccessTier.PRESTIGE, 2, 2, 5f, 0f, 0f, 0f, 120f, 8f),

    AUXILIARY_VENT_ARRAY("internal.vent-array-auxiliary", "@gearInternalAuxiliaryVentArray", Role.VENT_ARRAY,
            EquipmentAccessTier.ADVANCED, 2, 1, 2f, 0f, 0f, 0f, 0f, 1f),

    // Loaders are the missile replenishers as parts: replenisherId names the battle cadence.
    STANDARD_LOADER("internal.loader-standard", "@gearInternalStandardLoader", Role.LOADER,
            EquipmentAccessTier.COMMON, 2, 1, 0f, 0f, 0f, 0f, 0f, 0f),
    ACCELERATED_LOADER("internal.loader-accelerated", "@gearInternalAcceleratedLoader", Role.LOADER,
            EquipmentAccessTier.ADVANCED, 2, 1, 1f, 0f, 0f, 0f, 0f, 0f),

    /** A fifth more structure: spall lining behind the plate, on a mech's every section or a hull. Once the vehicle's kit. */
    SPALL_LINER("internal.spall-liner", "@gearInternalSpallLiner", Role.LINER,
            EquipmentAccessTier.COMMON, 2, 1, 1f, 0f, 0f, 0f, 0f, 0f),
    /**
     * Half again the rounds aboard every fed gun: a deeper rack or magazine, a longer belt. Once the vehicle's kit; it
     * needs a fed weapon to deepen.
     */
    READY_RACK("internal.ready-rack", "@gearInternalReadyRack", Role.READY_RACK,
            EquipmentAccessTier.COMMON, 2, 1, 1f, 0f, 0f, 0f, 0f, 0f),
    /**
     * A tethered pair of sensor drones the machine launches and flies ahead of it: sight past cover it cannot see over,
     * which is what an indirect-fire machine otherwise needs a spotter for. A machine with no bay of its own that can
     * tether a wing. Once the vehicle's kit.
     */
    SENSOR_PAIR("internal.sensor-pair", "@gearInternalSensorPair", Role.DRONE_BAY,
            EquipmentAccessTier.ADVANCED, 2, 1, 1f, 0f, 0f, 0f, 0f, 0f),
    /**
     * Active protection: interceptor launchers and their sensor panels, a point-defence mount riding the machine that
     * takes the arcing, top-attack rounds coming down on it, for six hundredths of its speed. An armed machine with
     * the flanks to stand the launchers on. Once the vehicle's kit, the tank line's answer to a missile mech.
     */
    ACTIVE_PROTECTION("internal.active-protection", "@gearInternalActiveProtection", Role.ACTIVE_PROTECTION,
            EquipmentAccessTier.PRESTIGE, 2, 1, 2f, 0f, 0f, 0f, 0f, 0f);

    /** What part of the frame an internal replaces. */
    public enum Role {
        ENGINE(SocketId.CORE),
        PLATING(SocketId.PLATING),
        CAPACITOR_BANK(SocketId.AUXILIARY),
        /** Optional, and they stack: the stock frame fits none. */
        VENT_ARRAY(SocketId.AUXILIARY),
        /** Optional: a mech without one never refills its racks. */
        LOADER(SocketId.AUXILIARY),
        /** Optional: structure behind the plate. */
        LINER(SocketId.AUXILIARY),
        /** Optional: deeper magazines, racks and belts. */
        READY_RACK(SocketId.AUXILIARY),
        /** Optional: a tethered drone wing. */
        DRONE_BAY(SocketId.AUXILIARY),
        /** Optional: a point-defence mount riding the machine. */
        ACTIVE_PROTECTION(SocketId.AUXILIARY);

        private final SocketId socket;

        Role(SocketId socket) { this.socket = socket; }

        public SocketId socket() { return socket; }

        /** Whether a part of this role is only ever replaced: plating covers the frame and is never left out. */
        public boolean replaceOnly() { return this == PLATING; }

        /** Whether this role's parts share the auxiliary compartment. */
        public boolean compartment() { return socket == SocketId.AUXILIARY; }

        /** The stock part for this role; null where the stock frame fits nothing. */
        public MechInternal stock() {
            return switch (this) {
                case ENGINE -> INDUSTRIAL_ENGINE;
                case PLATING -> FACTORY_PLATING;
                case CAPACITOR_BANK -> FACTORY_CAPACITOR_BANK;
                case VENT_ARRAY, LINER, READY_RACK, DRONE_BAY, ACTIVE_PROTECTION -> null;
                case LOADER -> STANDARD_LOADER;
            };
        }
    }

    public final String id;
    private final String displayName;
    public final Role role;
    public final EquipmentAccessTier tier;
    public final int footprintColumns;
    public final int footprintRows;
    /**
     * Mass against the engine budget. Plating's is in plating units of the chassis it is
     * fitted to ({@link MechFrameBudget#platingUnit}); every other role's is absolute.
     */
    public final float mass;
    /** Engine only: output as a multiple of the chassis's rated output. */
    public final float outputFactor;
    /** Engine only: where in the chassis's speed band this engine puts it, 0 stock to 1 refit. */
    public final float speedFraction;
    /** Plating only: where in the chassis's armor-capacity band this plating puts it. */
    public final float armorFraction;
    /** Capacitor bank only: the reservoir it provides. */
    public final float fluxCapacity;
    /**
     * Dissipation contribution in flux per second: a capacitor bank's base dissipation, a vent array's
     * addition, or an engine's (negative) running-flux penalty.
     */
    public final float dissipation;

    MechInternal(String id, String displayName, Role role, EquipmentAccessTier tier,
                 int footprintColumns, int footprintRows, float mass, float outputFactor,
                 float speedFraction, float armorFraction, float fluxCapacity, float dissipation) {
        this.id = id;
        this.displayName = displayName;
        this.role = role;
        this.tier = tier;
        this.footprintColumns = footprintColumns;
        this.footprintRows = footprintRows;
        this.mass = mass;
        this.outputFactor = outputFactor;
        this.speedFraction = speedFraction;
        this.armorFraction = armorFraction;
        this.fluxCapacity = fluxCapacity;
        this.dissipation = dissipation;
    }

    public String displayName() { return Strings.resolve(displayName); }

    /** A loader's battle cadence; null for every other role. */
    public MissileReplenisherComponent replenisher() {
        return switch (this) {
            case STANDARD_LOADER -> MissileReplenisherComponent.STANDARD;
            case ACCELERATED_LOADER -> MissileReplenisherComponent.ACCELERATED_FEED;
            default -> null;
        };
    }

    /** The loader part a persisted replenisher id names: the save-migration seam. */
    public static MechInternal loaderFor(String replenisherId) {
        MissileReplenisherComponent replenisher = MissileReplenisherComponent.findById(replenisherId);
        if (replenisher == null) return null;
        for (MechInternal internal : values()) {
            if (replenisher.equals(internal.replenisher())) return internal;
        }
        return null;
    }

    /** What this part multiplies the machine's structure by: a spall liner's fifth more. */
    public float structureFactor() {
        return this == SPALL_LINER ? 1.2f : 1f;
    }

    /** What this part multiplies every finite magazine, rack and belt by: a ready rack's half again. */
    public float ammoFactor() {
        return this == READY_RACK ? 1.5f : 1f;
    }

    /** What this part multiplies the machine's top speed by: active protection's launchers and their power. */
    public float speedFactor() {
        return this == ACTIVE_PROTECTION ? ACTIVE_PROTECTION_SPEED : 1f;
    }

    /** The retired Parapet's 2.9 cells a second against the Onager's 3.1. */
    private static final float ACTIVE_PROTECTION_SPEED = .94f;

    /** The drones this part adds to what the machine launches, in launch order: a sensor pair's two. */
    public List<DroneRole> droneBay() {
        return this == SENSOR_PAIR ? List.of(DroneRole.SENSOR, DroneRole.SENSOR) : List.of();
    }

    /** Something a part needs of the machine it goes on (mechs-nouns.md, one machine, one part catalog). */
    public enum Requirement {
        /** A gun, so a reservoir: a capacitor bank or vent array has nothing to feed on an unarmed hull. */
        ARMED,
        /** A gun that draws on ammunition: a ready rack has nothing to deepen otherwise. */
        FED_WEAPON,
        /** Room to tether a drone wing, and no bay of its own: a machine already flying drones takes no sensor pair. */
        DRONE_HOST,
        /** Flanks to stand interceptor launchers and their sensor panels on. */
        POINT_DEFENCE_HOST;

        /** The player-facing reason a part will not fit. */
        public String reason() {
            return Strings.get(switch (this) {
                case ARMED -> "gearRequirementArmed";
                case FED_WEAPON -> "gearRequirementFedWeapon";
                case DRONE_HOST -> "gearRequirementDroneHost";
                case POINT_DEFENCE_HOST -> "gearRequirementPointDefenceHost";
            });
        }
    }

    /** What this part needs of a machine, whatever kind of machine it is. */
    public List<Requirement> requirements() {
        return switch (role) {
            case CAPACITOR_BANK, VENT_ARRAY -> List.of(Requirement.ARMED);
            case READY_RACK -> List.of(Requirement.FED_WEAPON);
            case DRONE_BAY -> List.of(Requirement.DRONE_HOST);
            case ACTIVE_PROTECTION -> List.of(Requirement.ARMED, Requirement.POINT_DEFENCE_HOST);
            case ENGINE, PLATING, LOADER, LINER -> List.of();
        };
    }

    /** The first requirement {@code machine} does not meet, or null when the part may go on it. */
    public Requirement unmet(MachineCapabilities machine) {
        for (Requirement requirement : requirements()) {
            boolean met = switch (requirement) {
                case ARMED -> machine.armed();
                case FED_WEAPON -> machine.fedWeapons();
                case DRONE_HOST -> machine.droneHost();
                case POINT_DEFENCE_HOST -> machine.pointDefenceHost();
            };
            if (!met) return requirement;
        }
        return null;
    }

    /** A stock part: known by every company and fitted to every new or migrated frame. */
    public boolean stock() { return role.stock() == this; }

    /**
     * Every internal by id, an old id resolving to the part it became: the vehicle kits are internals now (one machine,
     * one part catalog), so a save, fixture, pattern or ladder line naming a kit reads as its internal.
     */
    public static MechInternal findById(String id) {
        if (id == null) return null;
        for (MechInternal internal : values()) {
            if (internal.id.equals(id)) return internal;
        }
        return RETIRED_KITS.get(id);
    }

    /**
     * The retired vehicle kits' ids and the internal each became: migration input only. A kit id resolves through
     * {@link #findById}; the bay folds kit stock and kit patterns into the internal's ({@code MechBay}).
     */
    public static final Map<String, MechInternal> RETIRED_KITS = Map.of(
            "kit.accelerated-loader", ACCELERATED_LOADER,
            "kit.spall-liner", SPALL_LINER,
            "kit.ready-rack", READY_RACK,
            "kit.sensor-pair", SENSOR_PAIR,
            "kit.active-protection", ACTIVE_PROTECTION);
}
