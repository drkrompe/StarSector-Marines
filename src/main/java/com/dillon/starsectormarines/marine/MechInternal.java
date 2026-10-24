package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.i18n.Strings;

/**
 * A non-weapon fitting that changes the frame: engine, plating, capacitor bank, vent array or
 * missile loader. Each internal replaces or adds one part and none is a multiplier on the
 * chassis; the chassis's own band ({@link MechFrameBudget}) bounds what an engine or plating
 * can buy back.
 *
 * <p>Higher tiers take more room: a larger footprint, or more mass against the engine's output.
 * Plating is frame-wide and has no socket, the engine has the core, and the rest share the one
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
            EquipmentAccessTier.COMMON, 0, 0, 0f, 0f, 0f, 0f, 0f, 0f),
    COMPOSITE_PLATING("internal.plating-composite", "@gearInternalCompositePlating", Role.PLATING,
            EquipmentAccessTier.ADVANCED, 0, 0, 1.0f, 0f, 0f, 0.5f, 0f, 0f),
    HEAVY_PLATING("internal.plating-heavy", "@gearInternalHeavyPlating", Role.PLATING,
            EquipmentAccessTier.PRESTIGE, 0, 0, 2.5f, 0f, 0f, 1.0f, 0f, 0f),

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
            EquipmentAccessTier.ADVANCED, 2, 1, 1f, 0f, 0f, 0f, 0f, 0f);

    /** What part of the frame an internal replaces. */
    public enum Role {
        ENGINE(SocketId.CORE),
        PLATING(null),
        CAPACITOR_BANK(SocketId.AUXILIARY),
        /** Optional, and they stack: the stock frame fits none. */
        VENT_ARRAY(SocketId.AUXILIARY),
        /** Optional: a mech without one never refills its racks. */
        LOADER(SocketId.AUXILIARY);

        private final SocketId socket;

        Role(SocketId socket) { this.socket = socket; }

        /** Null for plating, which covers the whole frame. */
        public SocketId socket() { return socket; }

        /** Whether this role's parts share the auxiliary compartment. */
        public boolean compartment() { return socket == SocketId.AUXILIARY; }

        /** The stock part for this role; null where the stock frame fits nothing. */
        public MechInternal stock() {
            return switch (this) {
                case ENGINE -> INDUSTRIAL_ENGINE;
                case PLATING -> FACTORY_PLATING;
                case CAPACITOR_BANK -> FACTORY_CAPACITOR_BANK;
                case VENT_ARRAY -> null;
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

    /** A stock part: known by every company and fitted to every new or migrated frame. */
    public boolean stock() { return role.stock() == this; }

    public static MechInternal findById(String id) {
        if (id == null) return null;
        for (MechInternal internal : values()) {
            if (internal.id.equals(id)) return internal;
        }
        return null;
    }
}
