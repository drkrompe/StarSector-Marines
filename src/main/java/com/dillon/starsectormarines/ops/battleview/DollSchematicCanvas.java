package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

/**
 * A chassis's doll as the Mech Lab shows it empty: the drafting panel, the sockets laid out and wired
 * to the engine core, each card as it stands on a bare frame. It is drawn by the lab's own code
 * ({@link MechLabDollCanvas#drawBareChassis} and {@link MechLabDollCanvas#drawBareVehicle}), which reads the
 * doll definitions ({@link com.dillon.starsectormarines.battle.mech.MechFittingLayout} for a mech,
 * {@link VehicleDoll} for a vehicle), so a change to a doll reaches the lab and the store together.
 */
public final class DollSchematicCanvas implements CanvasProducer {

    private final MechVariant variant;
    private final VehicleType vehicle;

    private DollSchematicCanvas(MechVariant variant, VehicleType vehicle) {
        this.variant = variant;
        this.vehicle = vehicle;
    }

    /** A mech chassis's doll. */
    public static DollSchematicCanvas ofChassis(MechVariant variant) {
        return new DollSchematicCanvas(variant, null);
    }

    /** A vehicle hull's doll. */
    public static DollSchematicCanvas ofVehicle(VehicleType type) {
        return new DollSchematicCanvas(null, type);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        // A pattern for sale is not a mech that cannot deploy: its empty sockets carry no warning.
        if (variant != null) MechLabDollCanvas.drawBareChassis(context, variant, width, height, null, 0d, false);
        else MechLabDollCanvas.drawBareVehicle(context, vehicle, width, height, 0d, false);
    }
}
