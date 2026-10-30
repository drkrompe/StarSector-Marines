package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.MechInternal;

import java.util.List;

/**
 * The doll's package-private geometry and vehicle cards, for the {@code machine-dolls} snapshot suite: the cards a
 * hull shows, their captions, and where the lab lays them out on its canvas.
 */
public final class MachineDollExport {

    private MachineDollExport() { }

    /** {@link VehicleDoll#sockets}. */
    public static List<SocketDef> vehicleSockets(VehicleType type) {
        return VehicleDoll.sockets(type);
    }

    /** {@link VehicleDoll#caption}. */
    public static String vehicleCaption(VehicleType type, SocketId card, List<VehicleKit> kits, MechInternal plating,
                                        MechInternal engine, MechWeaponComponent gun) {
        return VehicleDoll.caption(type, card, kits, plating, engine, gun);
    }

    /** {@link MechLabDollCanvas#mountFor}: the weapon mount a mech socket holds, null for an internal one. */
    public static MechMountSlot mountFor(SocketId socket) {
        return MechLabDollCanvas.mountFor(socket);
    }

    /**
     * The union of the cards' boxes, {left, top, right, bottom} in canvas pixels, as the lab lays {@code sockets}
     * out on a doll canvas of this size.
     */
    public static float[] cardBounds(List<SocketDef> sockets, float canvasWidth, float canvasHeight) {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (MechLabDollCanvas.SocketDropTarget target : MechLabDollCanvas.schematicTargets(sockets,
                canvasWidth * MechLabDollCanvas.SCHEMATIC_SHARE, canvasHeight)) {
            box[0] = Math.min(box[0], target.left());
            box[1] = Math.min(box[1], target.top());
            box[2] = Math.max(box[2], target.right());
            box[3] = Math.max(box[3], target.bottom());
        }
        return box;
    }
}
