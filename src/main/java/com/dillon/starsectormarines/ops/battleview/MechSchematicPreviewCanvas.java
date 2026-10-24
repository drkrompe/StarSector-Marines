package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

/**
 * A pattern's fitting doll before it is built: the same socket cards, wires and drafting board
 * the fitting view draws, laid out the same way and filled with the chassis's standard fit, but
 * read-only and drawn small in the pattern card. A vehicle has no sockets, so a vehicle pattern
 * shows its hull instead.
 */
public final class MechSchematicPreviewCanvas implements CanvasProducer {
    private final Supplier<Object> pattern;
    private final Supplier<LayeredMechAssets> assets;
    private VehicleType vehicleType;
    private VehiclePreviewCanvas vehicleArt;

    /** @param pattern a {@link MechVariant}, a {@link VehicleType}, or null for nothing to show */
    public MechSchematicPreviewCanvas(Supplier<Object> pattern, Supplier<LayeredMechAssets> assets) {
        if (pattern == null || assets == null) {
            throw new IllegalArgumentException("pattern and mech assets are required");
        }
        this.pattern = pattern;
        this.assets = assets;
    }

    @Override
    public void draw(CanvasContext c) {
        float width = c.metrics().surfaceWidth();
        float height = c.metrics().surfaceHeight();
        Object shown = pattern.get();
        if (shown instanceof VehicleType type) {
            if (type != vehicleType) {
                vehicleType = type;
                vehicleArt = new VehiclePreviewCanvas(type);
            }
            vehicleArt.draw(c);
            return;
        }
        if (!(shown instanceof MechVariant variant) || assets.get() == null) return;
        MechDeploymentSpec stock = new MechDeploymentSpec(variant, variant.defaultRole,
                MissileReplenisherComponent.STANDARD);
        MechFrameBudget.Fit fit = MechFrameBudget.Fit.stock(variant);
        MechFittingLayout layout = MechFittingLayout.forVariant(variant);

        MechLabDollCanvas.drawSchematicPanel(c, width, height);
        List<MechLabDollCanvas.SocketDropTarget> targets =
                MechLabDollCanvas.schematicTargets(layout.sockets(), width, height);
        MechLabDollCanvas.SocketDropTarget core = null;
        for (MechLabDollCanvas.SocketDropTarget target : targets) if (target.id() == SocketId.CORE) core = target;
        for (MechLabDollCanvas.SocketDropTarget target : targets) {
            if (core == null || target == core) continue;
            Color wire = HardpointBricks.tone(layout.socket(target.id()).type());
            List<float[]> path = MechLabDollCanvas.wireToCore(target, core);
            for (int i = 0; i < path.size() - 1; i++) {
                float[] a = path.get(i), b = path.get(i + 1);
                c.line(a[0], a[1], b[0], b[1], new Color(0, 0, 0, 190), 5f);
                c.line(a[0], a[1], b[0], b[1], new Color(wire.getRed(), wire.getGreen(), wire.getBlue(), 150), 2f);
            }
            float[] end = path.get(path.size() - 1);
            MechLabDollCanvas.drawAnchorMechanicalTick(c, end[0], end[1], wire, false);
        }
        for (MechLabDollCanvas.SocketDropTarget target : targets) {
            SocketDef socket = layout.socket(target.id());
            MechMountSlot mount = MechLabDollCanvas.mountFor(socket.id());
            MechWeaponComponent weapon = mount != null ? stock.weaponAt(mount) : null;
            MechInternal internal = switch (socket.id()) {
                case CORE -> fit.engine();
                case CAPACITORS -> fit.capacitorBank();
                case MINI_FAB -> fit.ventArray();
                default -> null;
            };
            MechSocketConsole.draw(c, target, socket, weapon, null,
                    internal == null ? stock.missileReplenisher() : null, internal, assets.get(),
                    false, false, false, false, false, 0d);
        }
    }
}
