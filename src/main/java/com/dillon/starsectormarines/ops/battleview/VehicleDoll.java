package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.i18n.Strings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * A vehicle's doll in the Mech Lab (mechs-nouns.md, vehicle kit): the mech schematic's shape with
 * the hull in the middle, the turret ahead, the bay and the systems either side and the kit rack
 * behind. Every card but the rack is sealed; it is derived from the hull's type, never authored, so
 * a new vehicle gets a doll by having a turret, seats or systems.
 *
 * <p>The cards reuse the mech socket ids for their places on the schematic: {@code CORE} is the
 * hull, {@code ARMS} the turret, the shoulders the bay and the systems, {@code AUXILIARY} the rack.</p>
 */
final class VehicleDoll {

    private VehicleDoll() { }

    /** The cards a hull of {@code type} shows, the kit rack last. */
    static List<SocketDef> sockets(VehicleType type) {
        List<SocketDef> sockets = new ArrayList<>();
        sockets.add(card(SocketId.CORE, SocketType.ENGINE, 2, "@vehicleDollHull"));
        if (type.hasTurretWeapon()) {
            Gun gun = Gun.of(type.turretStructureId);
            sockets.add(card(SocketId.ARMS, gun.hardpoint == HardpointType.MISSILE ? SocketType.MISSILE
                    : SocketType.BALLISTIC, gun.rows, "@vehicleDollTurret"));
        }
        if (bay(type) != null) sockets.add(card(SocketId.LEFT_SHOULDER, SocketType.AUXILIARY, 2, "@vehicleDollBay"));
        if (!systems(type).isEmpty()) {
            sockets.add(card(SocketId.RIGHT_SHOULDER, SocketType.OMNI, 2, "@vehicleDollSystems"));
        }
        sockets.add(card(SocketId.AUXILIARY, SocketType.AUXILIARY, Math.max(1, VehicleKit.slots(type)),
                "@vehicleDollKitRack"));
        return List.copyOf(sockets);
    }

    private static SocketDef card(SocketId id, SocketType type, int rows, String label) {
        return new SocketDef(id, type, 2, rows, 0f, 0f, 0f, 1f, 1f, 1f, false, label);
    }

    /** What a sealed card holds, as its caption; the rack's caption is its fill. */
    static String caption(VehicleType type, SocketId card, List<VehicleKit> kits) {
        VehicleKit.Effect effect = VehicleKit.Effect.of(kits);
        return switch (card) {
            case CORE -> Strings.format("vehicleDollHullCaption", Math.round(type.maxArmor * effect.armorFactor()),
                    Math.round(type.armorRating + effect.armorRatingBonus()));
            case ARMS -> turretName(type);
            case LEFT_SHOULDER -> bay(type);
            case RIGHT_SHOULDER -> String.join(Strings.get("separatorDot"), systems(type));
            case AUXILIARY -> Strings.format("vehicleDollRackCaption", kits.size(), VehicleKit.slots(type));
            default -> "";
        };
    }

    /**
     * A turret's gun as the lab already draws it: the mech or door gun of the same kind, its footprint
     * that drawing's, its colour its hardpoint's. Only the mortar has no such gun, so it has its own.
     */
    private enum Gun {
        CANNON("heavy-cannon", 3, HardpointType.BALLISTIC),
        HEAVY_MG("boat-door-heavy-mg", 1, HardpointType.BALLISTIC),
        MORTAR("vehicle-turret-mortar", 2, HardpointType.BALLISTIC),
        LASER("shoulder-laser", 3, HardpointType.ENERGY),
        PULSE("pulse-laser", 2, HardpointType.ENERGY),
        LRM("lrm-5", 2, HardpointType.MISSILE),
        SRM("srm-5", 2, HardpointType.MISSILE);

        final String art;
        final int rows;
        final HardpointType hardpoint;

        Gun(String art, int rows, HardpointType hardpoint) {
            this.art = art;
            this.rows = rows;
            this.hardpoint = hardpoint;
        }

        static Gun of(String structureId) {
            if (structureId == null) return HEAVY_MG;
            return switch (structureId) {
                case TurretCatalogRegistry.TANK_CANNON_STRUCTURE_ID -> CANNON;
                case TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID -> MORTAR;
                case TurretCatalogRegistry.TANK_BURST_LASER_STRUCTURE_ID -> LASER;
                case TurretCatalogRegistry.TANK_PD_LASER_STRUCTURE_ID, TurretCatalogRegistry.HOVER_PULSE_STRUCTURE_ID -> PULSE;
                case TurretCatalogRegistry.VEHICLE_LRM_STRUCTURE_ID -> LRM;
                case TurretCatalogRegistry.VEHICLE_SRM_STRUCTURE_ID -> SRM;
                default -> HEAVY_MG;
            };
        }
    }

    /** Every drawing a sealed card can show, for {@link HardwareArt#preload()}. */
    static final List<String> ART = artStems();

    private static List<String> artStems() {
        List<String> stems = new ArrayList<>(List.of(
                "vehicle-hull-tracked", "vehicle-hull-wheeled", "vehicle-hull-hover",
                "boat-hold-standard", "vehicle-bay-supply", "vehicle-bay-drones",
                "vehicle-systems-blade", "vehicle-systems-aps", "vehicle-systems-mast", "vehicle-systems-secondary"));
        for (Gun gun : Gun.values()) if (!stems.contains(gun.art)) stems.add(gun.art);
        return List.copyOf(stems);
    }

    /**
     * A card's colour in the lab's hardware language ({@link HardpointBricks}): the hull is the drive, so
     * engine blue; the turret its gun's hardpoint; the systems utility teal; the bay and the rack the
     * auxiliary compartment's steel, the rack's kits bringing their own colours.
     */
    static Color tone(VehicleType type, SocketId card) {
        return switch (card) {
            case CORE -> HardpointBricks.ENGINE;
            case ARMS -> HardpointBricks.tone(Gun.of(type.turretStructureId).hardpoint);
            case RIGHT_SHOULDER -> HardpointBricks.UTILITY;
            default -> HardpointBricks.AUXILIARY;
        };
    }

    /** A kit's colour: armour is plating's omni grey; the ready rack feeds the gun as a loader does. */
    static Color tone(VehicleKit kit) {
        return kit == VehicleKit.READY_RACK ? HardpointBricks.UTILITY : HardpointBricks.OMNI;
    }

    /**
     * The drawing a sealed card's bricks carry: the hull by its running gear, the turret by its gun, the
     * bay by what it holds, and the systems card by the most telling system on it; null for the rack.
     */
    static String art(VehicleType type, SocketId card) {
        return switch (card) {
            case CORE -> type.isHover() ? "vehicle-hull-hover"
                    : type.isTank() ? "vehicle-hull-tracked" : "vehicle-hull-wheeled";
            case ARMS -> Gun.of(type.turretStructureId).art;
            case LEFT_SHOULDER -> type.capacity > 0 ? "boat-hold-standard"
                    : type.supplyStock() > 0 ? "vehicle-bay-supply" : "vehicle-bay-drones";
            case RIGHT_SHOULDER -> type.hasBlade() ? "vehicle-systems-blade"
                    : type.activeProtectionStructureId() != null ? "vehicle-systems-aps"
                    : type.sightCells() > 0f ? "vehicle-systems-mast" : "vehicle-systems-secondary";
            default -> null;
        };
    }

    /** Seats, a supply stock or a drone bay; null for a hull that carries none. */
    private static String bay(VehicleType type) {
        if (type.capacity > 0) return Strings.format("vehicleDollSeats", type.capacity);
        if (type.supplyStock() > 0) return Strings.format("vehicleDollSupply", type.supplyStock());
        if (type.droneComplement() > 0) return Strings.format("vehicleDollDrones", type.droneComplement());
        return null;
    }

    private static List<String> systems(VehicleType type) {
        List<String> systems = new ArrayList<>();
        if (!type.secondaryMounts().isEmpty()) systems.add(Strings.get("vehicleDollSecondary"));
        if (type.activeProtectionStructureId() != null) systems.add(Strings.get("vehicleDollActiveProtection"));
        if (type.sightCells() > 0f) systems.add(Strings.get("vehicleDollSightMast"));
        if (type.hasBlade()) systems.add(Strings.get("vehicleDollBlade"));
        return systems;
    }

    private static String turretName(VehicleType type) {
        TurretCatalogRegistry registry = TurretCatalogRegistry.installed();
        StructureDef structure = registry != null ? registry.getStructure(type.turretStructureId) : null;
        String name = structure != null ? structure.displayName : Strings.get("openingMechLabVehicleTurretGeneric");
        return Strings.upper(name.replaceFirst("(?i)\\s+emplacement$", ""));
    }
}
