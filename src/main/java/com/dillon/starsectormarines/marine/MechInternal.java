package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.i18n.Strings;

/**
 * A non-weapon fitting that changes the frame: engine, plating, heat sink or cooler. Each
 * internal replaces one stock part and none is a multiplier on the chassis; the chassis's
 * own band ({@link MechFrameBudget}) bounds what an engine or plating can buy back.
 *
 * <p>Higher tiers take more room: a larger footprint in their socket, or more mass against
 * the engine's output. Plating is frame-wide and has no socket; every other role has one.
 * The missile replenisher is the loader and stays its own component, but a non-standard
 * loader competes with a cooler for the one {@link SocketId#MINI_FAB} socket.</p>
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

    FACTORY_HEAT_SINK("internal.heat-sink-factory", "@gearInternalFactoryHeatSink", Role.HEAT_SINK,
            EquipmentAccessTier.COMMON, 1, 1, 0f, 0f, 0f, 0f, 100f, 6f),
    DOUBLE_HEAT_SINK("internal.heat-sink-double", "@gearInternalDoubleHeatSink", Role.HEAT_SINK,
            EquipmentAccessTier.ADVANCED, 2, 1, 3f, 0f, 0f, 0f, 120f, 7.5f),
    MILITARY_HEAT_SINK("internal.heat-sink-military", "@gearInternalMilitaryHeatSink", Role.HEAT_SINK,
            EquipmentAccessTier.PRESTIGE, 3, 1, 5f, 0f, 0f, 0f, 140f, 8f),

    AUXILIARY_COOLER("internal.cooler-auxiliary", "@gearInternalAuxiliaryCooler", Role.COOLER,
            EquipmentAccessTier.ADVANCED, 1, 1, 2f, 0f, 0f, 0f, 0f, 1f);

    /** What part of the frame an internal replaces. */
    public enum Role {
        ENGINE(SocketId.CORE),
        PLATING(null),
        HEAT_SINK(SocketId.AMMO_RESERVE),
        /** Optional: the stock frame leaves this socket empty. */
        COOLER(SocketId.MINI_FAB);

        private final SocketId socket;

        Role(SocketId socket) { this.socket = socket; }

        /** Null for plating, which covers the whole frame. */
        public SocketId socket() { return socket; }

        /** The stock part for this role; null where the stock frame fits nothing. */
        public MechInternal stock() {
            return switch (this) {
                case ENGINE -> INDUSTRIAL_ENGINE;
                case PLATING -> FACTORY_PLATING;
                case HEAT_SINK -> FACTORY_HEAT_SINK;
                case COOLER -> null;
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
    /** Heat sink only: the reservoir it provides. */
    public final float heatCapacity;
    /**
     * Cooling contribution in heat per second: a heat sink's base cooling, a cooler's
     * addition, or an engine's (negative) running-heat penalty.
     */
    public final float cooling;

    MechInternal(String id, String displayName, Role role, EquipmentAccessTier tier,
                 int footprintColumns, int footprintRows, float mass, float outputFactor,
                 float speedFraction, float armorFraction, float heatCapacity, float cooling) {
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
        this.heatCapacity = heatCapacity;
        this.cooling = cooling;
    }

    public String displayName() { return Strings.resolve(displayName); }

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
