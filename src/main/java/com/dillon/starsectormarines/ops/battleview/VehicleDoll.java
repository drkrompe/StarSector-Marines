package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.i18n.Strings;

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
        if (type.hasTurretWeapon()) sockets.add(card(SocketId.ARMS, SocketType.BALLISTIC, 2, "@vehicleDollTurret"));
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

    /** The short mark a sealed card's bricks carry. */
    static String mark(SocketId card) {
        return switch (card) {
            case CORE -> "HULL";
            case ARMS -> "GUN";
            case LEFT_SHOULDER -> "BAY";
            case RIGHT_SHOULDER -> "SYS";
            default -> "";
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
