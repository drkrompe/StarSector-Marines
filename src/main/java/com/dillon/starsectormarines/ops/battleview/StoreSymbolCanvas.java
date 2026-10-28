package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.marine.BoatParts;
import com.dillon.starsectormarines.marine.EquipmentStore;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A mech or vehicle pattern's picture on the company store's shelf, drawn by the code that
 * draws it in the Mech Lab, so the store shows what the lab shows: a chassis or a vehicle as
 * its damage doll ({@link DamageDollCanvas}), and a weapon, an internal or a kit as the lab draws
 * hardware: its bricks under its painted symbol. A shuttle part is drawn as the Boat Deck
 * draws it ({@link BoatHardware}). A marine loadout is drawn as its faction's flag instead, an image in the
 * page's markup.
 */
public final class StoreSymbolCanvas {

    private StoreSymbolCanvas() { }

    /** Whether an offer has a picture drawn here. */
    public static boolean has(EquipmentStore.Kind kind, String id) {
        return of(kind, id) != null;
    }

    /** The picture for an offer, or null when it is drawn some other way. */
    public static CanvasProducer of(EquipmentStore.Kind kind, String id) {
        if (kind == EquipmentStore.Kind.BOAT_PART) return boatPart(id);
        if (kind != EquipmentStore.Kind.MECH_PATTERN) return null;
        MechPattern pattern = MechPattern.parse(id);
        if (pattern == null) return null;
        switch (pattern.kind()) {
            case CHASSIS -> {
                DamageDollCanvas.View view = new DamageDollCanvas.View();
                view.readChassis(pattern.variant());
                return DamageDollCanvas.bare(view);
            }
            case VEHICLE -> {
                DamageDollCanvas.View view = new DamageDollCanvas.View();
                view.readStock(pattern.vehicleType());
                return DamageDollCanvas.bare(view);
            }
            case WEAPON -> {
                MechWeaponComponent component = pattern.component();
                return new Hardware(component.footprintColumns, component.footprintRows,
                        HardpointBricks.tone(component.hardpointType),
                        (c, b) -> HardwareArt.draw(c, component, b.left(), b.top(), b.width(), b.height(), 255));
            }
            case INTERNAL -> {
                MechInternal internal = pattern.internal();
                return new Hardware(internal.footprintColumns, internal.footprintRows,
                        HardpointBricks.tone(internal.role),
                        (c, b) -> HardwareArt.draw(c, internal, b.left(), b.top(), b.width(), b.height(), 255));
            }
            case KIT -> {
                VehicleKit kit = pattern.kit();
                return new Hardware(2, 1, VehicleDoll.tone(kit),
                        (c, b) -> HardwareArt.draw(c, kit, b.left(), b.top(), b.width(), b.height(), 255));
            }
        }
        return null;
    }

    /** Whether a selected offer has a doll of its sockets to show: a chassis or a vehicle hull. */
    public static boolean hasDoll(EquipmentStore.Kind kind, String id) {
        return doll(kind, id) != null;
    }

    /**
     * The sockets of a chassis or vehicle hull as the lab's doll lays them out ({@link DollSchematicCanvas}),
     * or null for any other offer.
     */
    public static CanvasProducer doll(EquipmentStore.Kind kind, String id) {
        if (kind != EquipmentStore.Kind.MECH_PATTERN) return null;
        MechPattern pattern = MechPattern.parse(id);
        if (pattern == null) return null;
        return switch (pattern.kind()) {
            case CHASSIS -> DollSchematicCanvas.ofChassis(pattern.variant());
            case VEHICLE -> DollSchematicCanvas.ofVehicle(pattern.vehicleType());
            default -> null;
        };
    }

    /**
     * The doll of whichever offer is selected, drawn from one binding that follows the selection; the
     * doll is built once per selected offer, not on every draw.
     */
    public static CanvasProducer selectedDoll(Supplier<EquipmentStore.Offer> selected) {
        return new CanvasProducer() {
            private EquipmentStore.Offer built;
            private CanvasProducer doll;

            @Override
            public void draw(CanvasContext context) {
                EquipmentStore.Offer offer = selected.get();
                if (offer != built) {
                    built = offer;
                    doll = offer == null ? null : doll(offer.kind(), offer.id());
                }
                if (doll != null) doll.draw(context);
            }
        };
    }

    /** A shuttle fitting or door gun as the Boat Deck draws it, fitted to the picture. */
    private static CanvasProducer boatPart(String id) {
        BoatFittingSlot slot = BoatParts.slot(id);
        if (!BoatParts.exists(id)) return null;
        BoatDoll.Kind kind = slot == null ? BoatDoll.Kind.HARDPOINT : switch (slot) {
            case PLATING -> BoatDoll.Kind.PLATING;
            case DRIVE -> BoatDoll.Kind.DRIVE;
            case LIFT -> BoatDoll.Kind.LIFT;
            case CABIN -> BoatDoll.Kind.CABIN;
            case EMITTER -> BoatDoll.Kind.EMITTER;
            case POWER -> BoatDoll.Kind.POWER;
        };
        return context -> {
            BoatHardware.Piece piece = BoatHardware.piece(kind, id);
            HardpointBricks.Layout bricks = HardpointBricks.fit(0f, 0f, context.metrics().surfaceWidth(),
                    context.metrics().surfaceHeight(), piece.columns(), piece.rows(), HardpointBricks.BRICK_GAP);
            BoatHardware.draw(context, bricks, piece, 255);
        };
    }

    /**
     * A piece of hardware as the lab draws it, fitted to the picture: its footprint of bricks in its
     * hardpoint's or role's colour ({@link HardpointBricks#drawFilled}) under its painted symbol
     * ({@link HardwareArt}).
     */
    private record Hardware(int columns, int rows, Color tone, BiConsumer<CanvasContext, HardpointBricks.Layout> art)
            implements CanvasProducer {
        @Override
        public void draw(CanvasContext context) {
            HardpointBricks.Layout bricks = HardpointBricks.fit(0f, 0f, context.metrics().surfaceWidth(),
                    context.metrics().surfaceHeight(), columns, rows, HardpointBricks.BRICK_GAP);
            HardpointBricks.drawFilled(context, bricks, 0, 0, columns, rows, tone);
            art.accept(context, bricks);
        }
    }
}
