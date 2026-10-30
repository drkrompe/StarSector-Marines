package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.vehicle.VehicleGunCard;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.VehicleRack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * A vehicle's doll in the Mech Lab (mechs-nouns.md, vehicle kit): the mech schematic's shape with
 * the engine in the middle, as on every machine, the turret ahead, the bay and the systems either
 * side and the kit rack behind. Every card but the rack, the engine core, the armor point and the gun card is sealed; it is derived
 * from the hull's type, never authored, so a new vehicle gets a doll by having a turret, seats or systems. The engine
 * core, the armor point and the gun card are live, as on a mech: they take the same engine, plating and weapon parts,
 * the gun card by the type and size of the gun the hull was built around ({@link VehicleGunCard}).
 *
 * <p>The cards reuse the mech socket ids for their places on the schematic: {@code CORE} is the
 * engine, {@code ARMS} the turret, the shoulders the bay and the systems, {@code AUXILIARY} the rack.</p>
 */
final class VehicleDoll {

    private VehicleDoll() { }

    /** The cards a hull of {@code type} shows: the kit rack's bay or bays ({@link VehicleRack#grids}) near last. */
    static List<SocketDef> sockets(VehicleType type) {
        List<SocketDef> sockets = new ArrayList<>();
        sockets.add(card(SocketId.CORE, SocketType.ENGINE, type.weight() == VehicleType.Weight.LIGHT ? 1 : 2,
                "@gearSocketEngine"));
        if (type.hasTurretWeapon()) {
            VehicleGunCard gun = VehicleGunCard.of(type);
            sockets.add(card(SocketId.ARMS, gun.hardpoint == null ? SocketType.OMNI : switch (gun.hardpoint) {
                case MISSILE -> SocketType.MISSILE;
                case ENERGY -> SocketType.ENERGY;
                case BALLISTIC -> SocketType.BALLISTIC;
            }, gun.rows, "@vehicleDollTurret"));
        }
        if (bay(type) != null) sockets.add(card(SocketId.LEFT_SHOULDER, SocketType.AUXILIARY, 2, "@vehicleDollBay"));
        if (!systems(type).isEmpty()) {
            sockets.add(card(SocketId.RIGHT_SHOULDER, SocketType.OMNI, 2, "@vehicleDollSystems"));
        }
        sockets.addAll(VehicleRack.grids(type));
        sockets.add(card(SocketId.PLATING, SocketType.PLATING, 1, "@gearSocketPlating"));
        return List.copyOf(sockets);
    }

    private static SocketDef card(SocketId id, SocketType type, int rows, String label) {
        return new SocketDef(id, type, 2, rows, 0f, 0f, 0f, 1f, 1f, 1f, false, label);
    }

    /**
     * What a card holds, as its caption; the rack's caption is its fill, {@code rackRows} of its slots taken. The
     * engine core and the armor point are live: they name the part fitted, or the hull's own. The gun card names the
     * weapon fitted, or the hull's turret.
     */
    static String caption(VehicleType type, SocketId card, int rackRows, MechInternal plating,
                          MechInternal engine, MechWeaponComponent gun) {
        return switch (card) {
            case CORE -> engine != null ? engine.displayName()
                    : Strings.get(type.isHover() ? "vehicleDollHover" : type.isTank() ? "vehicleDollTracked"
                    : "vehicleDollWheeled");
            case ARMS -> gun != null ? Strings.upper(gun.displayName())
                    : type.ownGun() != null ? Strings.upper(type.ownGun().displayName()) : turretName(type);
            case LEFT_SHOULDER -> bay(type);
            case RIGHT_SHOULDER -> String.join(Strings.get("separatorDot"), systems(type));
            case AUXILIARY, AUXILIARY_2 -> Strings.format("vehicleDollRackCaption", rackRows, VehicleRack.slots(type));
            case PLATING -> plating != null ? plating.displayName() : Strings.get("vehicleDollHullPlate");
            default -> "";
        };
    }

    /**
     * A turret's gun as the lab already draws it: the mech or door gun of the same kind. Only the mortar has no such
     * gun, so it has its own.
     */
    private static String gunArt(VehicleGunCard card) {
        return switch (card) {
            case CANNON -> "heavy-cannon";
            case SMALL -> "light-machine-gun";
            case MORTAR -> "vehicle-turret-mortar";
            case LASER -> "shoulder-laser";
            case LRM -> "lrm-5";
        };
    }

    /**
     * The drawing of the gun a hull's turret actually is. The card sizes the socket, and a small card is shared by
     * several guns, so the SRM pod and the hover pulse emitter each carry their own picture rather than the light
     * machine gun's; any other turret, the machine-gun hulls' light machine gun among them, reads from its card.
     */
    private static String gunArt(VehicleType type) {
        return switch (type.turretStructureId == null ? "" : type.turretStructureId) {
            case TurretCatalogRegistry.VEHICLE_SRM_STRUCTURE_ID -> "vehicle-srm-5";
            case TurretCatalogRegistry.HOVER_PULSE_STRUCTURE_ID -> "light-pulse-laser";
            default -> gunArt(VehicleGunCard.of(type));
        };
    }

    /** Every drawing a sealed card can show, for {@link HardwareArt#preload()}. */
    static final List<String> ART = artStems();

    private static List<String> artStems() {
        List<String> stems = new ArrayList<>(List.of(
                "engine-industrial", "engine-uprated", "engine-military",
                "boat-hold-standard", "vehicle-bay-supply", "vehicle-bay-drones",
                "vehicle-systems-blade", "vehicle-systems-mast", "vehicle-systems-secondary"));
        for (VehicleGunCard gun : VehicleGunCard.values()) if (!stems.contains(gunArt(gun))) stems.add(gunArt(gun));
        stems.addAll(List.of("vehicle-srm-5", "light-pulse-laser"));
        return List.copyOf(stems);
    }

    /**
     * A card's colour in the lab's hardware language ({@link HardpointBricks}): the engine core is
     * engine blue; the turret its gun's hardpoint; the systems utility teal; the bay and the rack the
     * auxiliary compartment's steel, the rack's kits bringing their own colours.
     */
    static Color tone(VehicleType type, SocketId card) {
        return switch (card) {
            case CORE -> HardpointBricks.ENGINE;
            case ARMS -> HardpointBricks.tone(VehicleGunCard.of(type).hardpoint);
            case RIGHT_SHOULDER -> HardpointBricks.UTILITY;
            case PLATING -> HardpointBricks.OMNI;
            default -> HardpointBricks.AUXILIARY;
        };
    }

    /**
     * A kit's colour: the ready rack and the loader feed the gun as a mech's loader does, and active protection is a
     * hull system, as the systems card's are. A bank or vent array on
     * the rack wears its own role's colour, as in a mech's compartment.
     */
    static Color tone(VehicleKit kit) {
        return switch (kit) {
            case READY_RACK, ACCELERATED_LOADER, ACTIVE_PROTECTION -> HardpointBricks.UTILITY;
            default -> HardpointBricks.OMNI;
        };
    }

    /**
     * The drawing a sealed card's bricks carry: the hull by its running gear, the turret by its gun, the
     * bay by what it holds, and the systems card by the most telling system on it; null for the rack.
     */
    static String art(VehicleType type, SocketId card) {
        return switch (card) {
            case CORE -> switch (type.weight()) {
                case LIGHT -> "engine-industrial";
                case MEDIUM -> "engine-uprated";
                case HEAVY -> "engine-military";
            };
            case ARMS -> gunArt(type);
            case LEFT_SHOULDER -> type.capacity > 0 ? "boat-hold-standard"
                    : type.supplyStock() > 0 ? "vehicle-bay-supply" : "vehicle-bay-drones";
            case RIGHT_SHOULDER -> type.hasBlade() ? "vehicle-systems-blade"
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
