package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ops.battleview.MechLabDollCanvas.SocketDropTarget;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A chassis's doll as a small picture of its sockets: the Mech Lab's own schematic
 * ({@link MechLabDollCanvas#schematicTargets} lays the cards out, {@link MechLabDollCanvas#drawWires}
 * wires them to the engine core) shrunk to fit a box, each card its footprint of solid bricks
 * ({@link HardpointBricks#drawFilled}) in the tone the lab gives it. The sockets come from the doll
 * definitions the lab reads ({@link MechFittingLayout} for a mech, {@link VehicleDoll} for a vehicle),
 * so a change to a chassis's doll reaches this picture with nothing redrawn.
 */
public final class DollSchematicCanvas implements CanvasProducer {

    /** A panel far larger than any doll, so the layout is the doll's natural one before it is scaled. */
    private static final float LAYOUT_SIZE = 2000f;

    private final List<SocketDef> sockets;
    private final Function<SocketId, Color> tone;

    private DollSchematicCanvas(List<SocketDef> sockets, Function<SocketId, Color> tone) {
        this.sockets = sockets;
        this.tone = tone;
    }

    /** A mech chassis's doll: the sockets its variant is built with, each in its socket type's tone. */
    public static DollSchematicCanvas ofChassis(MechVariant variant) {
        MechFittingLayout layout = MechFittingLayout.forVariant(variant);
        return new DollSchematicCanvas(layout.sockets(), id -> HardpointBricks.tone(layout.socket(id).type()));
    }

    /** A vehicle hull's doll: the sealed cards its type shows, each in the lab's colour for it. */
    public static DollSchematicCanvas ofVehicle(VehicleType type) {
        return new DollSchematicCanvas(VehicleDoll.sockets(type), id -> VehicleDoll.tone(type, id));
    }

    @Override
    public void draw(CanvasContext context) {
        List<SocketDropTarget> natural = MechLabDollCanvas.schematicTargets(sockets, LAYOUT_SIZE, LAYOUT_SIZE);
        if (natural.isEmpty()) return;
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (SocketDropTarget target : natural) {
            left = Math.min(left, target.left());
            top = Math.min(top, target.top());
            right = Math.max(right, target.right());
            bottom = Math.max(bottom, target.bottom());
        }
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float scale = Math.min(width / (right - left), height / (bottom - top));
        float originX = (width - (right - left) * scale) * 0.5f - left * scale;
        float originY = (height - (bottom - top) * scale) * 0.5f - top * scale;
        List<SocketDropTarget> targets = new ArrayList<>();
        for (SocketDropTarget t : natural) {
            targets.add(new SocketDropTarget(t.id(), t.gridColumns(), t.gridRows(),
                    t.anchorX() * scale + originX, t.anchorY() * scale + originY,
                    t.centerX() * scale + originX, t.centerY() * scale + originY, t.width() * scale, t.height() * scale));
        }
        MechLabDollCanvas.drawWires(context, targets, tone, Math.max(0.3f, scale * 1.5f));
        for (SocketDropTarget t : targets) {
            HardpointBricks.Layout bricks = HardpointBricks.fit(t.left(), t.top(), t.width(), t.height(),
                    t.gridColumns(), t.gridRows(), HardpointBricks.BRICK_GAP * Math.min(1f, scale * 3f));
            HardpointBricks.drawFilled(context, bricks, 0, 0, t.gridColumns(), t.gridRows(), tone.apply(t.id()));
        }
    }
}
